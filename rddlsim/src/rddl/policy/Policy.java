/**
 * RDDL: Implements abstract policy interface.
 * 
 * @author Scott Sanner (ssanner@gmail.com)
 * @version 10/10/10
 *
 **/

package rddl.policy;

import java.util.*;

import gurobi.GRBException;
import org.apache.commons.math3.random.RandomDataGenerator;

import rddl.*;
import rddl.RDDL.*;
import util.Pair;

public abstract class Policy {
	
	public long RAND_SEED = -1;	

	public RandomDataGenerator _random = new RandomDataGenerator();
	public String _sInstanceName;
	public RDDL _rddl;
	public int lookahead;
	public ArrayList<PVAR_INST_DEF> gurobi_initialization = null;
	public double TIME_LIMIT_MINS = 10;
	public Boolean DO_NPWL_PWL = true;
	public Boolean TIME_FUTURE_CACHE_USE = true;



	public Policy() {
		
	}
	
	public Policy(String instance_name) {
		_sInstanceName = instance_name;
	}
	
	public void setInstance(String instance_name) {
		_sInstanceName = instance_name;
	}

	public void setRDDL(RDDL rddl) {
		_rddl = rddl;
	}

	public void setLimitTime(Integer time) {
	}
	
	public int getNumberUpdate() {
		return 0;
	}
	
	public void setRandSeed(long rand_seed) {
		RAND_SEED = rand_seed;
		_random = new RandomDataGenerator();
		_random.reSeed(RAND_SEED);
	}
	
	// Override if needed
	public void roundInit(double time_left, int horizon, int round_number, int total_rounds) {
		System.out.println("\n*********************************************************");
		System.out.println(">>> ROUND INIT " + round_number + "/" + total_rounds + "; time remaining = " + time_left + ", horizon = " + horizon);
		System.out.println("*********************************************************");
	}
	
	// Override if needed
	public void roundEnd(double reward) {
		System.out.println("\n*********************************************************");
		System.out.println(">>> ROUND END, reward = " + reward);
		System.out.println("*********************************************************");
	}
	
	// Override if needed
	public void sessionEnd(double total_reward) {
		System.out.println("\n*********************************************************");
		System.out.println(">>> SESSION END, total reward = " + total_reward);
		System.out.println("*********************************************************");
	}

	// Must override
	public abstract ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException;

	public void initializeState(State s){}

	public State getStateObject(){return  null;}
	
	public String toString() {
		return "Policy for '" + _sInstanceName + "'";
	}


	public void runRandompolicyForState(State s) throws Exception{}



	protected void runRandompolicy(State s, int trajectory_length, int number_trajectories, Random rand1)throws Exception{}

	public void convertNPWLtoPWL(State s) throws Exception{}

	public void dispose_Gurobi() throws GRBException {}



	public Pair<Boolean,Pair<Integer,Integer>> CompetitionExploarationPhase(String rddl_filepath, String instanceName, Integer n_futures, Integer n_lookahead, String gurobi_timeout,
                                                              String future_gen_type,String hindsight_strat, RDDL rddl_object, State s, Double total_explo_time, Double optimization_time_out) throws Exception{
        Pair<Integer,Integer> safe_parameters = new Pair(4,5);
	    return new Pair(true,safe_parameters);
	}



	public Pair<Integer,Integer> CompetitionExploarationPhase(String rddl_filepath, String instanceName, ArrayList<String> parameters) throws Exception{
		return new Pair<>(5,4);
	}


	
}
