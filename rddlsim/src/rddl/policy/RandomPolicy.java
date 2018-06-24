/**
 * RDDL: Implements a random policy for a domain.
 * 
 **/

package rddl.policy;

import java.util.*;

import rddl.*;
import rddl.RDDL.*;

public class RandomPolicy extends Policy {
	
	Random rand_gen;
	final private static int NUM_TRIES = 30;
	
	public RandomPolicy () {
		this.rand_gen = new Random();
	}
	
	public RandomPolicy(String instance_name) {
		super(instance_name);
		this.rand_gen = new Random();
	}

	public ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException {
		final ArrayList<PVAR_INST_DEF> ret = new ArrayList<>();
		
		// Get an action (assuming all actions are enum type)
		ArrayList<PVAR_NAME> pvars = new ArrayList<>(s._alActionNames.size());
		Collections.copy(pvars, s._alActionNames);
		Collections.shuffle(pvars, rand_gen);
		
		for( PVAR_NAME p : pvars ){
			// Get type of action var
			final PVARIABLE_DEF pdef = s._hmPVariables.get(p);
			final TYPE_NAME tdef = pdef._typeRange;
			// Get term instantions for that action
			ArrayList<ArrayList<LCONST>> instantiations = s.generateAtoms(p);
			Collections.shuffle(instantiations, rand_gen);
			
			for( ArrayList<LCONST> inst : instantiations ){
				if( tdef.equals(TYPE_NAME.BOOL_TYPE) ){
					final Boolean val = rand_gen.nextBoolean();
					ret.add(new PVAR_INST_DEF(p._sPVarName, val,inst) );
					try{
						s.checkStateActionConstraints(ret);
					}catch(EvalException exc){
						ret.remove(ret.size()-1);
						ret.add(new PVAR_INST_DEF(p._sPVarName, !val,inst) );
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
						ret.add(new PVAR_INST_DEF(p._sPVarName,  val,inst) );
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
						ret.add(new PVAR_INST_DEF(p._sPVarName, (Object)(enums.get(rand_index)),inst
								));
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
		}
		
		return ret;
	}





}
