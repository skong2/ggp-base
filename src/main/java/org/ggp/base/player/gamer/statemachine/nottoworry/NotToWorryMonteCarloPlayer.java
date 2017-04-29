package org.ggp.base.player.gamer.statemachine.nottoworry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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

public class NotToWorryMonteCarloPlayer extends NotToWorryGamer {
	private long start;
	private int maxTime;
	private boolean foundWin = false;
	private int totalMobility;
	private Random rand = new Random();
	public static final double timeoutBuffer = 0.7;

	//-------------GENERAL HELPER METHODS
	private int totalMobilityHeuristic() throws MoveDefinitionException, TransitionDefinitionException {
		int totalMobility = 1;
		List<MachineState> nextStates = getStateMachine().getNextStates(getCurrentState());
		for(MachineState s: nextStates) {
			if (!getStateMachine().isTerminal(s)) {
				totalMobility += getStateMachine().getNextStates(s).size();
			}
		}
		//System.out.println("total mobility: " + totalMobility);
		return totalMobility;
	}

	private int mobilityHeuristic(Role role, MachineState state) throws MoveDefinitionException, TransitionDefinitionException{
		//number of states reachable from this step
		//go down into each one once, and add up all possible states
		int possibleStates = getStateMachine().getNextStates(state).size();
		//System.out.println((possibleStates*100)/totalMobility + "%");
		return (possibleStates*100)/totalMobility;
	}

	private int goalProximityHeuristic(Role role, MachineState state) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
		return getStateMachine().getGoal(state, role);
	}

	private int opponentHeuristic(Role role, MachineState state) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
		//Role opponent = opponents.get(0);
		int opponentMaxScore = 0;
		int totalActions = 0;

		List<Role> opponents = new ArrayList<Role>(getStateMachine().getRoles());
		opponents.remove(getRole());
		for( Role opponent : opponents) {
			opponentMaxScore += getStateMachine().getLegalMoves(state, opponent).size();
			totalActions += getStateMachine().findActions(opponent).size();
		}
		//System.out.println("opponents can make " + opponentMaxScore + " moves out of " + totalActions + " moves");
		opponentMaxScore *= 100;
		opponentMaxScore /= totalActions;
		//System.out.println("opponent mobility: " + opponentMaxScore + "%");
		for(Role opponent: opponents) {
			opponentMaxScore += goalProximityHeuristic(opponent, state);
		}
		return opponentMaxScore;
	}

	//smart gamer selects a method to use depending on number of gamers

	//-------------METHODS FOR USE WITH SPECIFIC GAMERS
	private int findStateUtility(ArrayList<MachineState> queue, Role r) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine mach = getStateMachine();
		int stateUtility = -1;
		List<Move> singleMove = new ArrayList<Move>();
		while(queue.size() != 0) {
			MachineState curr = queue.remove(0);
			//if time clocks down calculate heuristic
			if(System.currentTimeMillis() - start > maxTime*timeoutBuffer) {
				if(foundWin) return 0;
				int goalScore = goalProximityHeuristic(r, curr);
				int mobilityScore = mobilityHeuristic(r, curr);
				return goalScore + mobilityScore;
			}

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

	private int monteCarloDepthCharge(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(state)) {
			return getStateMachine().getGoal(state, role);
		}
		ArrayList<Move> moves = new ArrayList<Move>();
		for (int i = 0; i < getStateMachine().getRoles().size(); i++) {
			List<Move> options = getStateMachine().getLegalMoves(state, getStateMachine().getRoles().get(i));
			int ind = rand.nextInt(options.size());
			moves.add(i, options.get(ind));
		}
		MachineState newState = getStateMachine().getNextState(state, moves);
		return monteCarloDepthCharge(role, newState);
	}

	private int monteCarloUtility(Role role, MachineState state, int count) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		int total = 0;
		for (int i = 0; i < count; i++) {
			total += monteCarloDepthCharge(role, state);
		}
		return total / count;
	}

	public int minScore(Role role, MachineState state, int alpha, int beta, int turn, int depth, int levels, int count)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (getStateMachine().isTerminal(state)) {
			int goal = getStateMachine().getGoal(state, role);
			if(goal == 100) {
				foundWin = true;
			}
			return goal;
		}
		else if(System.currentTimeMillis() - start > maxTime * timeoutBuffer) {
			if(foundWin) {
				return 0;
			}
			int goalScore = goalProximityHeuristic(role, state);
			int mobilityScore = mobilityHeuristic(role, state);
			return goalScore + mobilityScore - opponentHeuristic(role, state);
		}
		List<Move> actions = getStateMachine().getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, actions.get(i));
			for (int j = 0; j < jointActions.size(); j++) {
				List<Move> moves = jointActions.get(j);
				MachineState newState = getStateMachine().getNextState(state, moves);
				int result;
				if (turn == getStateMachine().getRoles().size() - 1) {
					result = maxScore(role, newState, alpha, beta, depth + 1, levels, count);
				} else {
					result = minScore(role, newState, alpha, beta, turn + 1, depth, levels, count);
				}
				beta = Math.min(beta, result);
				if (beta <= alpha) {
					return alpha;
				}
			}
		}
		return beta;
	}

	public int maxScore(Role role, MachineState state, int alpha, int beta, int depth, int levels, int count)
			throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(state)) {
			int goal = getStateMachine().getGoal(state, role);
			if(goal >= 100) {
				foundWin = true;
			}
			return goal;
		}
		if(System.currentTimeMillis() - start > maxTime * timeoutBuffer) {
			if(foundWin) {
				return 0;
			}
			int goalScore = goalProximityHeuristic(role, state);
			int mobilityScore = mobilityHeuristic(role, state);
			return goalScore + mobilityScore - opponentHeuristic(role, state);
		}
		else if (depth == levels) {
			return monteCarloUtility(role, state, count);
		}
		List<Move> actions = getStateMachine().getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, actions.get(i));
			for (int j = 0; j < jointActions.size(); j++) {
				List<Move> moves = jointActions.get(j);
				MachineState newState = getStateMachine().getNextState(state, moves);
				int result= minScore(role, newState, alpha, beta, 1, depth, levels, count);
				alpha = Math.max(alpha, result);
				if (alpha >= beta) {
					return beta;
				}
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
			if(stateUtility == 100) return m;
			else if(stateUtility > maxUtility) {
				maxUtility = stateUtility;
				bestMove = m;
			}
		}
		return bestMove;
	}

	private Move selectMoveAlphabeta(List<Move> actions) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		int score = 0;
		int turn = 1;
		Move selection = actions.get(0);
		for (int i = 0; i < actions.size(); i++) {
			List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(getCurrentState(), getRole(), actions.get(i));
			for (int j = 0; j < jointActions.size(); j++) {
				List<Move> moves = jointActions.get(j);
				MachineState newState = getStateMachine().getNextState(getCurrentState(), moves);
				int levels = 4;
				int count = 4;
				int result = minScore(getRole(), newState, 0, 100, turn, 0, levels, count);
				if (result == 100) {
					return actions.get(i);
				}
				if (result > score) {
					score = result;
					selection = actions.get(i);
				}
			}
		}
		return selection;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// We get the current start time
		start = System.currentTimeMillis();
		foundWin = false;
		maxTime = getMatch().getPlayClock()*1000;

		/**
		 * We put in memory the list of legal moves from the
		 * current state. The goal of every stateMachineSelectMove()
		 * is to return one of these moves. The choice of which
		 * Move to play is the goal of GGP.
		 */

		List<Role> roles = getStateMachine().getRoles();
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		moves = new ArrayList<Move>(moves);
		totalMobility = totalMobilityHeuristic();
		Collections.shuffle(moves);
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
