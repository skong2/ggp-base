package org.ggp.base.player.gamer.statemachine.nottoworry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class NotToWorryCompulsiveDeliberater extends NotToWorryGamer {
	HashSet<MachineState> previousStates = new HashSet<MachineState>();

	private class StateUtilityMove {
		public HashSet<MachineState> prev;
		public MachineState state;

		public StateUtilityMove(HashSet<MachineState> prev, MachineState state) {
			this.prev = prev;
			this.state = state;
		}
	}

	private int findStateUtility(ArrayList<StateUtilityMove> queue, Role r) throws TransitionDefinitionException, GoalDefinitionException, MoveDefinitionException {
		StateMachine mach = getStateMachine();
		List<Move> singleMove = new ArrayList<Move>();
		int stateUtility = -1;
		while(queue.size() != 0) { // iterates through queue of moves until there are no longer possible moves
			//System.out.println("queue size: " + queue.size());
			StateUtilityMove curr = queue.remove(0); //pops off the queue
			if(curr.prev.contains(curr.state)) {
				System.out.println("continued");
				continue; //if current state has been visited before, it must be a repeat move
			}
			if(mach.isTerminal(curr.state)) { //if current state is terminal, calculate what it would be
				int finalScore = mach.getGoal(curr.state, r);
				if(finalScore == 100) return 100; //if 100 return immediately
				else if (finalScore > stateUtility) { //else update state utility accordingly
					stateUtility = finalScore;
				}
			} else { //if current state is not terminal, add all possible moves to queue
				curr.prev.add(curr.state); //adds current state to previously viewed states
				List<Move> moves = mach.getLegalMoves(curr.state, r);
				for(Move m: moves) {
					singleMove.add(m);
					MachineState nextState = mach.getNextState(curr.state, singleMove);
					StateUtilityMove sum = new StateUtilityMove(new HashSet<MachineState>(curr.prev), nextState);
					queue.add(sum);
					singleMove.clear();
				}
			}
		}
		System.out.println(stateUtility);
		return stateUtility;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		long start = System.currentTimeMillis();

		/**
		 * We put in memory the list of legal moves from the
		 * current state. The goal of every stateMachineSelectMove()
		 * is to return one of these moves. The choice of which
		 * Move to play is the goal of GGP.
		 */
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move bestMove = moves.get(0); //initialize
		StateMachine mach = getStateMachine();
		int maxUtility = -1;
		System.out.println("prev moves made: " + previousStates.size());
		//for each move, calculate state utility and then return move with highest state utility
		for(Move m: moves) {
			ArrayList<StateUtilityMove> queue = new ArrayList<StateUtilityMove>();
			ArrayList<Move> singleMove = new ArrayList<Move>();
			singleMove.add(m);

			//only make this move if it would not lead to a previous state
			if(!previousStates.contains(mach.getNextState(getCurrentState(), singleMove))) {
				StateUtilityMove initialSUM = new StateUtilityMove(new HashSet<MachineState>(previousStates), getCurrentState());
				queue.add(initialSUM);
				int stateUtility = findStateUtility(queue, getRole());
				if(stateUtility > maxUtility) {
					maxUtility = stateUtility;
					bestMove = m;
				}
			}
		}
		previousStates.add(getCurrentState()); //initialize previous states

		// We get the end time
		// It is mandatory that stop<timeout
		long stop = System.currentTimeMillis();

		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
		return bestMove;
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// Sample gamers do no metagaming at the beginning of the match.
	}

	/** This will currently return "SampleGamer"
	 * If you are working on : public abstract class MyGamer extends SampleGamer
	 * Then this function would return "MyGamer"
	 */
	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	// This is the default State Machine
	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	// This is the defaul Sample Panel
	@Override
	public DetailPanel getDetailPanel() {
		return new SimpleDetailPanel();
	}

	@Override
	public void stateMachineStop() {
		// Sample gamers do no special cleanup when the match ends normally.
		previousStates.clear();
	}

	@Override
	public void stateMachineAbort() {
		// Sample gamers do no special cleanup when the match ends abruptly.
		previousStates.clear();
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// Sample gamers do no game previewing.
	}

}
