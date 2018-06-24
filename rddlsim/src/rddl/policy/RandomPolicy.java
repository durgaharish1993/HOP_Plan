/**
 * RDDL: Implements a random policy for a domain.
 * 
 **/

package rddl.policy;

import java.util.*;

import rddl.*;
import rddl.RDDL.*;

public class RandomPolicy extends Policy {
	
	final private static int NUM_TRIES = 30;
	protected ArrayList<Pair<PVAR_NAME, ArrayList<LCONST>>> choices = null;
	
	protected void initializeChoices(State s){
		this.choices = new ArrayList<>();
		for(final PVAR_NAME p : s._alActionNames){
			for(final ArrayList<LCONST> inst : s.generateAtoms(p)){
				choices.add(new Pair<>(p, inst));
			}
		}
	}
	
	public RandomPolicy () {
	}
	
	public RandomPolicy(String instance_name) {
		super(instance_name);
	}

	public ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException {
		final ArrayList<PVAR_INST_DEF> ret = new ArrayList<>();
		
		if (this.choices == null){
			initializeChoices(s);
		}
		Collections.shuffle(choices);
		
		for( Pair<PVAR_NAME, ArrayList<LCONST>> choice : choices ){
			final PVAR_NAME p = choice._o1;
			final ArrayList<LCONST> inst = choice._o2;
			
			// Get type of action var
			final PVARIABLE_DEF pdef = s._hmPVariables.get(p);
			final TYPE_NAME tdef = pdef._typeRange;

			if( tdef.equals(TYPE_NAME.BOOL_TYPE) ){
				final Boolean val = rand_gen.nextBoolean();
				ret.add(new PVAR_INST_DEF(p._sPVarName, val, inst) );
				try{
					s.checkStateActionConstraints(ret);
				}catch(EvalException exc){
					ret.remove(ret.size()-1);
					ret.add(new PVAR_INST_DEF(p._sPVarName, !val, inst) );
					try{
						s.checkStateActionConstraints(ret);
					}catch(EvalException exc){
						ret.remove(ret.size()-1);
					}
				}
			}else if( tdef.equals(TYPE_NAME.INT_TYPE) ){
				for( int attempt = 0; attempt < NUM_TRIES; ++attempt ){
					final Integer val = rand_gen.nextInt();
					ret.add(new PVAR_INST_DEF(p._sPVarName, val, inst) );
					try{
						s.checkStateActionConstraints(ret);
						break;
					}catch(EvalException exc){
						ret.remove(ret.size()-1);
					}
				}
			}else if( tdef.equals(TYPE_NAME.REAL_TYPE) ){
				for( int attempt = 0; attempt < NUM_TRIES; ++attempt ){
					final Double val = rand_gen.nextDouble();
					ret.add(new PVAR_INST_DEF(p._sPVarName, val, inst) );
					try{
						s.checkStateActionConstraints(ret);
						break;
					}catch(EvalException exc){
						ret.remove(ret.size()-1);
					}
				}
			}else{
				//enum?
				final ENUM_TYPE_DEF edef = (ENUM_TYPE_DEF)s._hmTypes.get(tdef);
				final ArrayList<ENUM_VAL> enums = new ArrayList<ENUM_VAL>((ArrayList)edef._alPossibleValues);
				for( int attempt = 0; attempt < NUM_TRIES && !enums.isEmpty(); ++attempt ){
					final int rand_index = rand_gen.nextInt(enums.size());
					ret.add(new PVAR_INST_DEF(p._sPVarName, 
							(Object)(enums.get(rand_index)),inst));
					try{
						s.checkStateActionConstraints(ret);
						break;
					}catch(EvalException exc){
						ret.remove(ret.size()-1);
						enums.remove(rand_index);
					}
				}
			}
			System.out.println(ret);
		}
		
		try{
			s.checkStateActionConstraints(ret);
		}catch(EvalException exc){
			//exhaustive : make list of all legal actions
			try{
				ArrayList<ArrayList<PVAR_INST_DEF>> legal_actions 
					= new ArrayList<>();
				getLegalActions(s, new ArrayList<>(), new TreeSet<>, legal_actions);
				ret = legal_actions.get(rand_gen.nextInt(legal_actions.size()));
			}catch(EvalException exc1){
				ret = new ArrayList<PVAR_INST_DEF>();	
			}
		}
		return ret;
	}

	protected void getLegalActions(final State s,
			final ArrayList<PVAR_INST_DEF> cur_action, 
			final TreeSet<Pair<PVAR_NAME, ArrayList<LCONST>>> visited,
			final ArrayList<ArrayList<PVAR_INST_DEF>> legal_actions){
		
		for( final Pair<PVAR_NAME, ArrayList<ArrayList<LCONST>>> choice : choices ){
			final PVAR_NAME p = choice._o1;
			final ArrayList<ArrayList<LCONST>> instantiations = choice._o2;
			
			// Get type of action var
			final PVARIABLE_DEF pdef = s._hmPVariables.get(p);
			final TYPE_NAME tdef = pdef._typeRange;
			
			if (visited.contains(new Pair<>(p, inst))){
				continue;
			}
			
			if( tdef.equals(TYPE_NAME.BOOL_TYPE) ){

				tmp_visited = new TreeSet(visited);
				tmp_visited.add(new Pair<>(p, inst));
				
				tmp_true = new ArrayList<PVAR_INST_DEF>(cur_action);
				tmp_true.add(new PVAR_INST_DEF(p, Boolean.TRUE, inst));
				getLegalActions(s, tmp_true, tmp_visited, legal_actions);
				
				tmp_false = new ArrayList<PVAR_INST_DEF>(cur_action);
				tmp_false.add(new PVAR_INST_DEF(p, Boolean.FALSE, inst));
				getLegalActions(s, tmp_false, tmp_visited, legal_actions);
				
				//noop choice
				getLegalActions(s, cur_action, tmp_visited, legal_actions);
			}
			else if( s._hmTypes.get(tdef) instanceof ENUM_TYPE_DEF ){
				final ENUM_TYPE_DEF edef = (ENUM_TYPE_DEF)s._hmTypes.get(tdef);
				final ArrayList<ENUM_VAL> enums = new ArrayList<ENUM_VAL>((ArrayList)edef._alPossibleValues);
				
				tmp_visited = new TreeSet(visited);
				tmp_visited.add(new Pair<>(p, inst));
				
				for( final ENUM_VAL eval : enums ){
					this_tmp = new ArrayList<PVAR_INST_DEF>(cur_action);
					this_tmp.add(new PVAR_INST_DEF(p, eval , inst));
					getLegalActions(s, this_tmp, tmp_visited, legal_actions);
				}
				//noop choice
				getLegalActions(s, cur_action, tmp_visited, legal_actions);
			}
		}
		//gets here when each choice is assigned a value
		legal_actions.add(cur_action);
	}
}
