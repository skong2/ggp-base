package org.ggp.base.player.gamer.statemachine.nottoworry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

public class NotToWorryPropnetStateMachine extends SamplePropNetStateMachine {

    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            ordering = getOrdering();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean markBases(MachineState state) {
    	Set<GdlSentence> gdls = state.getContents();
    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
    	for (GdlSentence gdl : gdls) {
    		Proposition prop = bases.get(gdl);
    		if (prop != null) {
        		prop.setValue(true);
    		}
    	}
    	return true;
    }

    public boolean markActions(MachineState state) {
    	//get list of moves possible from current state
    	Set<GdlSentence> gdls = state.getContents();
    	Map<GdlSentence, Proposition> actions = propNet.getInputPropositions();
    	for (GdlSentence gdl : gdls) {
    		Proposition prop = actions.get(gdl);
    		if (prop != null) {
        		prop.setValue(true);
    		}
    	}
		return true;
    }

    public boolean clearPropnet() {
    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
    	for (Proposition prop : bases.values()) {
    		prop.setValue(false);
    	}
    	return true;
    }

//    function propmarkp (p)
//    {if (p.type=='base') {return p.mark};
//     if (p.type=='input') {return p.mark};
//     if (p.type=='view') {return propmarkp(p.source)};
//     if (p.type=='negation') {return propmarknegation(p)};
//     if (p.type=='conjunction') {return propmarkconjunction(p)};
//     if (p.type=='disjunction') {return propmarkdisjunction(p)};
//     return false}

    public boolean propMark(Proposition p) {
    	if (propNet.getBasePropositions().containsKey(p.getName())) {
    		return p.getValue();
    	}
    	if (propNet.getInputPropositions().containsKey(p.getName())) {
    		return p.getValue();
    	}
    	//conjunction
    	if ((Component) p instanceof And) {
    		return propMarkConjunction(p);
    	}
    	//negation
    	if ((Component) p instanceof Not) {
    		return propmarkNegation(p);
    	}
    	//disjunction
    	if ((Component) p instanceof Or) {
    		return propMarkDisjunction(p);
    	}
    	return propMark((Proposition) p.getSingleInput());
    }

    public boolean propmarkNegation (Proposition p) {
    	return !propMark((Proposition)p.getSingleInput());
    }

    public boolean propMarkConjunction (Proposition p) {
    	Set<Component> sources = p.getInputs();
    	for (Component component : sources) {
    		if (!propMark((Proposition)component)) {
    			return false;
    		}
    	}
    	return true;
    }

    public boolean propMarkDisjunction (Proposition p) {
    	Set<Component> sources = p.getInputs();
    	for (Component component : sources) {
    		if (propMark((Proposition)component)) {
    			return true;
    		}
    	}
    	return false;
    }

    public Set<Proposition> propLegals (Role role,MachineState state) {
    	markBases(state);
    	Set<Proposition> legals = new HashSet<Proposition>();
    	Map<Role,Set<Proposition>> propMap = propNet.getLegalPropositions();
    	for (Role r : roles) {
    		if(role == r) {
    			legals = propMap.get(r);
    			break;
    		}
    	}
    	Set<Proposition> actions = new HashSet<Proposition>();
    	for (Proposition p : legals) {
    		if (propMark(p)) {
    			actions.add(p);
    		}
    	}
    	return actions;
    }

    //TODO: Check return type and overall functionality of this function
    public Set<Proposition> propNext(Move move, MachineState state) {
    	markActions(state);
    	markBases(state);
    	Map<GdlSentence,Proposition> bases = propNet.getBasePropositions();
    	Set<Proposition> nexts = new HashSet<Proposition>();
    	for (Proposition p : bases.values()) {
    		if (propMark(p)) {
    			nexts.add(p);
    		}

    	}
    	return nexts;
    }

    public int propReward(MachineState state, Role role) {
    	markBases(state);
    	Map<Role, Set<Proposition>> rewardsMap = propNet.getGoalPropositions();
    	Set<Proposition> rewards = new HashSet<Proposition>();
    	for (Role r :roles) {
    		if (role == r) {
    			rewards = rewardsMap.get(r);
    			break;
    		}
    	}
    	for (Proposition reward : rewards) {
    		if (propMark(reward)) {
    			return getGoalValue(reward);
    		}
    	}
    	return 0;
    }

    public boolean propTerminal (MachineState state) {
    	markBases(state);
    	return propMark(propNet.getTerminalProposition());
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
    	return propTerminal(state);
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
    	return propReward(state, role);
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
    	Proposition initial = propNet.getInitProposition();
    	initial.setValue(true);
    	return getStateFromBase();
    }

    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
        Map<Role, Set<Proposition>> legals = propNet.getLegalPropositions();
        List<Move> moves = new ArrayList<Move>();
        for (Proposition legal : legals.get(role)) {
        	moves.add(getMoveFromProposition(legal));
        }
        return moves;
    }

    /**
     * Computes the legal moves for role in state.
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
        Set<Proposition> legals = propLegals(role,state);
        List<Move> legalMoves = new ArrayList<Move>();
        for (Proposition p : legals) {
        	legalMoves.add(getMoveFromProposition(p));
        }
        return legalMoves;
    }


//function propnext (move,state,propnet)
// {markactions(move,propnet);
//  markbases(state,propnet);
//  var bases = propnet.bases;
//  var nexts = seq();
//  for (var i=0; i<bases.length; i++)
//      {nexts[i] = propmarkp(bases[i].source.source)};
//  return nexts}

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
        markActions(state);
        markBases(state);
    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
    	Set<GdlSentence> contents = new HashSet<GdlSentence>();
    	for (GdlSentence gdl : state.getContents()) {
    		boolean propValue = propMark((Proposition) bases.get(gdl).getSingleInput().getSingleInput());
    	}
    	MachineState newState = new MachineState(contents);
        return null;
    }

    public List<Proposition> order = new LinkedList<Proposition>();

    public Map<Component, Boolean> visitTopological(Component c, Map<Component, Boolean> unvisited, Map<GdlSentence, Proposition> bases, Map<GdlSentence, Proposition> inputs) {
    	if (unvisited.get(c)) {
    		return unvisited;
    	} else {
    		unvisited.put(c, true);
    		Set<Component> neighbors = c.getOutputs();

    		for (Component neighbor : neighbors) {
            	if (bases.values().contains(c) || inputs.values().contains(c)) {
            		continue;
            	}
            	unvisited = visitTopological(neighbor, unvisited, bases, inputs);
    		}
    		unvisited.remove(c);
    		order.add(0, (Proposition) c);
    	}
    	return unvisited;
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    @Override
	public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        order = new LinkedList<Proposition>();

        // All of the components in the PropNet
        List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
        Map<GdlSentence, Proposition> inputs = propNet.getInputPropositions();

        // 0 for unmarked, 1 for temp mark, remove for perm mark
        Map<Component, Boolean> unvisited = new HashMap<Component, Boolean>();
        for (Component c: components) {
        	if (bases.values().contains(c) || inputs.values().contains(c)) {
        		continue;
        	}
        	unvisited.put(c, false);
        }
        while (!unvisited.isEmpty()) {
        	Component selection = (Component) unvisited.keySet().toArray()[0];
        	unvisited = visitTopological(selection, unvisited, bases, inputs);
        }

        return new LinkedList<Proposition>(order);
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    @Override
	public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }

}
