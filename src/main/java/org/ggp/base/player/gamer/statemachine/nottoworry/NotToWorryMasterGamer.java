package org.ggp.base.player.gamer.statemachine.nottoworry;

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

public class NotToWorryMasterGamer extends NotToWorryGamer {

	private long start;
	private long maxTime;
	private boolean foundWin = false;

	//-------------GENERAL HELPER METHODS
	private int mobilityHeuristic(Role role, MachineState state) throws MoveDefinitionException, TransitionDefinitionException{
		//number of states reachable from this step
		//go down into each one once, and add up all possible states
		int possibleStates = getStateMachine().getNextStates(state).size();
		return possibleStates;
	}

	private int goalProximityHeuristic(Role role, MachineState state) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
		return getStateMachine().getGoal(state, role);
	}

	public int minScore(Role role, MachineState state, int alpha, int beta, int turn)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (getStateMachine().isTerminal(state)) {
			int goal = getStateMachine().getGoal(state, role);
			if(goal >= 100) {
				foundWin = true;
			}
			return goal;
		}
		if(System.currentTimeMillis() - start > maxTime * 0.8) {
			if(foundWin) return 0;
			return goalProximityHeuristic(role, state) + mobilityHeuristic(role, state);
		}
		List<Move> actions = getStateMachine().getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, actions.get(i));
			for (int j = 0; j < jointActions.size(); j++) {
				List<Move> moves = jointActions.get(j);
				MachineState newState = getStateMachine().getNextState(state, moves);
				int result;
				if (turn == getStateMachine().getRoles().size() - 1) {
					result = maxScore(role, newState, alpha, beta);
				} else {
					result = minScore(role, newState, alpha, beta, turn + 1);
				}
				beta = Math.min(beta, result);
				if (beta <= alpha) {
					return alpha;
				}
			}
		}
		return beta;
	}

	public int maxScore(Role role, MachineState state, int alpha, int beta)
			throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(state)) {
			int goal = getStateMachine().getGoal(state, role);
			if(goal >= 100) {
				foundWin = true;
			}
			return goal;
		}
		if(System.currentTimeMillis() - start > maxTime * 0.8) {
			if(foundWin) return 0;
			return goalProximityHeuristic(role, state) + mobilityHeuristic(role, state);
		}
		List<Move> actions = getStateMachine().getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, actions.get(i));
			for (int j = 0; j < jointActions.size(); j++) {
				List<Move> moves = jointActions.get(j);
				MachineState newState = getStateMachine().getNextState(state, moves);
				int result = minScore(role, newState, alpha, beta, 1);
				alpha = Math.max(alpha, result);
				if (alpha >= beta) {
					return beta;
				}
			}
		}
		return alpha;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		start = System.currentTimeMillis();
		maxTime = getMatch().getPlayClock()*1000;

		int turn = 1;
		List<Move> actions = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move selection = actions.get(0);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(getCurrentState(), getRole(), actions.get(i));
			for (int j = 0; j < jointActions.size(); j++) {
				List<Move> moves = jointActions.get(j);
				MachineState newState = getStateMachine().getNextState(getCurrentState(), moves);
				int result = minScore(getRole(), newState, 0, 100, turn);
				if (result == 100) {
					return actions.get(i);
				}
				if (result > score) {
					score = result;
					selection = actions.get(i);
				}
			}
		}
		long stop = System.currentTimeMillis();

		/**
		 * DO NOT REMOVE THESE TWO LINES
		 *
		 * REQUIRED: actions, selection, stop, start
		 *
		 **/
		notifyObservers(new GamerSelectedMoveEvent(actions, selection, stop - start));
		return selection;
	}

	// To be implemented
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{

	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	// To be implemented
	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	// Default
	@Override
	public DetailPanel getDetailPanel() {
		return new SimpleDetailPanel();
	}

	// To be implemented
	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {

	}

}
