package org.ggp.base.player.gamer.statemachine.nottoworry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class NotToWorrySmartGamer extends NotToWorryGamer {
	private long start;
	private int maxTime;
	private boolean foundWin = false;
	private int totalMobility;
	private int depthLimit = 2; //hard-coded for now
	private int probeCount = 4; //hard-coded for now
	private Random rand = new Random();
	public static final double timeoutBuffer = 2000;

	// HELPER FUNCTIONS

	public boolean timeout(double buffer) {
		return System.currentTimeMillis() - start > maxTime - buffer;
	}

	// HEURISTIC FUNCTIONS

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

	// MONTE CARLO TREE SEARCH

	private class StateLevel {
		private MachineState state;
		private int depth;

		public StateLevel(MachineState s, int d) {
			this.state = s;
			this.depth = d;
		}
	}

	public class TreeNode {
		public int visits = 0;
		public double utility = 0;
		public MachineState state = null;
		public TreeNode parent = null;
		public Move conception = null;

		public ArrayList<TreeNode> children = null;

		public TreeNode(TreeNode parent, MachineState state, Move move) {
			this.parent = parent;
			this.state = state;
			this.children = new ArrayList<TreeNode>();
			this.conception = move;
		}
	}

	public TreeNode selectNode(TreeNode node){
		if(node.visits == 0) return node;
		double score = 0;
		TreeNode result = node;
		if(node.children.size() == 0) return node;
		for(int i = 0; i < node.children.size(); i++) {
			if(node.children.get(i).visits==0){
				return node.children.get(i);
			}
		}
		for(int i = 0; i < node.children.size(); i++) {
			TreeNode child = node.children.get(i);
			double newScore = child.utility/child.visits+Math.sqrt(2*Math.log(child.parent.visits)/child.visits);
			if(newScore > score) {
				score = newScore;
				result = child;
			}
		}
		return selectNode(result);
	}

	public boolean expandNode(TreeNode node) throws MoveDefinitionException, TransitionDefinitionException{
		for(Move move : getStateMachine().getLegalMoves(node.state, getRole())) {
			List<List<Move>> actions = getStateMachine().getLegalJointMoves(node.state, getRole(), move);
			for(int i = 0; i < actions.size(); i++) {
				if(getStateMachine().isTerminal(node.state)) continue;
				MachineState newState = getStateMachine().getNextState(node.state, actions.get(i));
				TreeNode newNode = new TreeNode(node, newState, move);
				node.children.add(newNode);
			}
		}
		return true;
	}

	public boolean backpropagateNode(TreeNode node, double score) {
		node.visits++;
		node.utility = node.utility + score;
		if(node.parent != null) backpropagateNode(node.parent, score);
		return true;
	}

	public Move selectMCTS(List<Move> moves) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		TreeNode root = new TreeNode(null, getCurrentState(), null);
		Move move = getStateMachine().getLegalMoves(getCurrentState(), getRole()).get(0);
		while(!timeout(timeoutBuffer + 1000)) {
			TreeNode node = selectNode(root);
			expandNode(node);
			double utility = monteCarloUtility(getRole(), node.state, probeCount);
			backpropagateNode(node, utility);
		}
		double maxUtility = Double.MIN_VALUE;
		for (TreeNode node : root.children) {
			if (node.utility > maxUtility) {
				maxUtility = node.utility;
				move = node.conception;
			}
		}
		return move;
	}

	// COMPULSIVE DELIBERATOR

	private int findStateUtility(ArrayList<StateLevel> queue, Role r) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine mach = getStateMachine();
		int stateUtility = -1;
		List<Move> singleMove = new ArrayList<Move>();
		while(queue.size() != 0) {
			StateLevel curr = queue.remove(0);
			//if time clocks down calculate heuristic
			if(timeout(timeoutBuffer)) {
				if(foundWin) return 0;
				int goalScore = goalProximityHeuristic(r, curr.state);
				int mobilityScore = mobilityHeuristic(r, curr.state);
				return goalScore + mobilityScore;
			}

			if(mach.isTerminal(curr.state)) {
				int finalScore = mach.getGoal(curr.state, r);
				if(finalScore == 100) return 100;
				else if (finalScore > stateUtility) stateUtility = finalScore;
			} else {
				List<Move> moves = mach.getLegalMoves(curr.state, r);
				for(Move m: moves) {
					singleMove.add(m);
					MachineState nextState = mach.getNextState(curr.state, singleMove);
					if(curr.depth >= depthLimit) { //if depth is >= limit, then use monte carlo
						int monteCarloMobility = monteCarloUtility(r, nextState, probeCount);
						if(monteCarloMobility > stateUtility) stateUtility = monteCarloMobility;
					} else {
						StateLevel nextStateLevel = new StateLevel(nextState, curr.depth+1); //increment depth by 1, create new state
						queue.add(nextStateLevel);
					}
					singleMove.clear();
				}
			}
		}
		return stateUtility;
	}

	private Move selectMoveCompulsiveDeliberater(List<Move> moves) throws TransitionDefinitionException, GoalDefinitionException, MoveDefinitionException {
		Move bestMove = moves.get(0); //initialize
		StateMachine mach = getStateMachine();
		int maxUtility = -1;
		for(Move m: moves) {
			ArrayList<StateLevel> queue = new ArrayList<StateLevel>();
			ArrayList<Move> singleMove = new ArrayList<Move>();
			singleMove.add(m);
			MachineState nextState = mach.getNextState(getCurrentState(), singleMove);
			StateLevel nextStateLevel = new StateLevel(nextState, 0);
			queue.add(nextStateLevel);
			int stateUtility = findStateUtility(queue, getRole());
			if(stateUtility == 100) return m;
			else if(stateUtility > maxUtility) {
				maxUtility = stateUtility;
				bestMove = m;
			}
		}
		return bestMove;
	}

	// ALPHA-BETA (VANILLA AND WITH MONTE CARLO / ITERATIVE DEEPENING)

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
		else if(timeout(timeoutBuffer) || depth >= levels) {
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
					result = minScore(role, newState, alpha, beta, turn + 1, depth + 1, levels, count);
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
		if(timeout(timeoutBuffer)) {
			if(foundWin) {
				return 0;
			}
			int goalScore = goalProximityHeuristic(role, state);
			int mobilityScore = mobilityHeuristic(role, state);
			return goalScore + mobilityScore - opponentHeuristic(role, state);
		}
		else if (depth == levels) {
			if(levels == depthLimit) return monteCarloUtility(role, state, count);
			else return 0;
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

	private Move selectMoveAlphabeta(List<Move> actions) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		int score = 0;
		int turn = 1;
		int levels = 1;
		Move selection = actions.get(0);
		while(levels <= depthLimit){
			for (int i = 0; i < actions.size(); i++) {
				List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(getCurrentState(), getRole(), actions.get(i));
				for (int j = 0; j < jointActions.size(); j++) {
					List<Move> moves = jointActions.get(j);
					MachineState newState = getStateMachine().getNextState(getCurrentState(), moves);
					//				int levels = 4;
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
			levels++;
		}
		return selection;
	}

	private Move selectIterative(List<Move> actions) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int score = 0;
		int turn = 1;
		int levels = 1;
		Move selection = actions.get(0);
		while(levels <= depthLimit){
			for (int i = 0; i < actions.size(); i++) {
				List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(getCurrentState(), getRole(), actions.get(i));
				for (int j = 0; j < jointActions.size(); j++) {
					List<Move> moves = jointActions.get(j);
					MachineState newState = getStateMachine().getNextState(getCurrentState(), moves);
					//				int levels = 4;
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
			levels++;
		}
		return selection;
	}

	// RUNTIME MOVE SELECTION

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		start = System.currentTimeMillis();
		foundWin = false;
		maxTime = getMatch().getPlayClock()*1000;


		List<Role> roles = getStateMachine().getRoles();
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		moves = new ArrayList<Move>(moves);

		totalMobility = totalMobilityHeuristic();

		Move selection = moves.get(0);

		if (moves.size() > 1) {
			if (roles.size() == 1) {
				selection = selectMoveCompulsiveDeliberater(moves);
			} else {
				selection = selectMCTS(moves);
			}
		}
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
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		return;
	}

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new NotToWorryPropnetStateMachine());
	}

}
