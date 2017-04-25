package org.ggp.base.player.gamer.statemachine.nottoworry;

import java.util.ArrayList;
import java.util.Arrays;
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

public class NotToWorrySmartGamer extends NotToWorryGamer {
	private long start;
	private int maxTime;
	//smart gamer selects a method to use depending on number of gamers

	//-------------METHODS FOR USE WITH SPECIFIC GAMERS
	private int findStateUtility(ArrayList<MachineState> queue, Role r) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine mach = getStateMachine();
		int stateUtility = -1;
		List<Move> singleMove = new ArrayList<Move>();
		while(queue.size() != 0) {
			MachineState curr = queue.remove(0);
			if(mach.isTerminal(curr)) {
				int finalScore = mach.getGoal(curr, r);
				if(finalScore == 100) return 100;
				else if (finalScore > stateUtility) stateUtility = finalScore;
			} else {
				List<Move> moves = mach.getLegalMoves(curr, r);
				for(Move m: moves) {
					singleMove.add(m);
					MachineState nextState = mach.getNextState(curr, singleMove);
					queue.add(nextState);
					singleMove.clear();
				}
			}
		}
		return stateUtility;
	}

	public int minScore(Role role, Move action, MachineState state, int alpha, int beta)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		Role opponent = null;
		for (Role r: getStateMachine().getRoles()) {
			if (!r.equals(role)) {
				opponent = r;
			}
		}
		List<Move> actions = getStateMachine().getLegalMoves(state, opponent);
//		int score = 100;
		for (int i = 0; i < actions.size(); i++) {
			List<Move> moves;
			if (role.equals(getStateMachine().getRoles().get(0))) {
				moves = Arrays.asList(action, actions.get(i));
			} else {
				moves = Arrays.asList(actions.get(i), action);
			}
			MachineState newState = getStateMachine().getNextState(state, moves);
			int result = maxScore(role, newState,alpha,beta);
			beta = Math.min(beta, result);
			if (beta <= alpha) {
				return alpha;
			}
//			if (result < score) {
//				score = result;
//			}
		}
//		return score;
		return beta;
	}

	public int maxScore(Role role, MachineState state, int alpha, int beta)
			throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(state)) {
			return getStateMachine().getGoal(state, role);
		}
		if(System.currentTimeMillis() - start > maxTime*0.8) {
			return 0;
		}
		List<Move> actions = getStateMachine().getLegalMoves(state, role);
		//int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			int result = minScore(role, actions.get(i), state,alpha,beta);
//			if (result > score) {
//				score = result;
//			}
			alpha = Math.max(alpha, result);
			if (alpha >= beta) {
				return beta;
			}
		}
		return alpha;
	}

	//------------SELECT MOVE METHODS
	private Move selectMoveCompulsiveDeliberater(List<Move> moves) throws TransitionDefinitionException, GoalDefinitionException, MoveDefinitionException {
		Move bestMove = moves.get(0); //initialize
		StateMachine mach = getStateMachine();
		int maxUtility = -1;
		for(Move m: moves) {
			ArrayList<MachineState> queue = new ArrayList<MachineState>();
			ArrayList<Move> singleMove = new ArrayList<Move>();
			singleMove.add(m);
			MachineState nextState = mach.getNextState(getCurrentState(), singleMove);
			queue.add(nextState);
			int stateUtility = findStateUtility(queue, getRole());
			if(stateUtility > maxUtility) {
				maxUtility = stateUtility;
				bestMove = m;
			}
		}
		return bestMove;
	}

	private Move selectMoveAlphabeta(List<Move> actions) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		Move selection = actions.get(0);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			int result = minScore(getRole(), actions.get(i), getCurrentState(),0,100);
			if (result == 100) {
				return actions.get(i);
			}
			if (result > score) {
				score = result;
				selection = actions.get(i);
			}
		}
		return selection;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		start = System.currentTimeMillis();

		maxTime = getMatch().getPlayClock()*1000;

		/**
		 * We put in memory the list of legal moves from the
		 * current state. The goal of every stateMachineSelectMove()
		 * is to return one of these moves. The choice of which
		 * Move to play is the goal of GGP.
		 */

		List<Role> roles = getStateMachine().getRoles();
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move selection = null;
		if(roles.size() > 1) {
			//use alphabeta gamer
			selection = selectMoveAlphabeta(moves);
		} else if (roles.size() == 1) {
			//use compulsive deliberater
			selection = selectMoveCompulsiveDeliberater(moves);
		}

		// We get the end time
		// It is mandatory that stop<timeout
		long stop = System.currentTimeMillis();

		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// Sample gamers do no metagaming at the beginning of the match.
	}

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
	}

	@Override
	public void stateMachineAbort() {
		// Sample gamers do no special cleanup when the match ends abruptly.
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// Sample gamers do no game previewing.
	}

}
