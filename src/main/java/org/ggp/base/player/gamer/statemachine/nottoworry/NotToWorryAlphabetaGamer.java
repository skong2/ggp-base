package org.ggp.base.player.gamer.statemachine.nottoworry;

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

public class NotToWorryAlphabetaGamer extends NotToWorryGamer {

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

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		long start = System.currentTimeMillis();

		List<Move> actions = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move selection = actions.get(0);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			int result = minScore(getRole(), actions.get(i), getCurrentState(),100,0);
			if (result == 100) {
				return actions.get(i);
			}
			if (result > score) {
				score = result;
				selection = actions.get(i);
			}
		}
		long stop = System.currentTimeMillis();

		/**
		 * These are functions used by other parts of the GGP codebase
		 * You shouldn't worry about them, just make sure that you have
		 * moves, selection, stop and start defined in the same way as
		 * this example, and copy-paste these two lines in your player
		 */
		notifyObservers(new GamerSelectedMoveEvent(null, selection, stop - start));
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
	public void preview(Game g, long timeout) throws GamePreviewException {
		// Sample gamers do no game previewing.
	}

}
