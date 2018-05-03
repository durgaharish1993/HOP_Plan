package rddl.det.mip;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

//import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.commons.math3.random.RandomDataGenerator;

import gurobi.GRB;
import gurobi.GRBConstr;
import gurobi.GRBException;
import gurobi.GRBExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import gurobi.GRB.StringAttr;
import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.BOOL_CONST_EXPR;
import rddl.RDDL.CPF_DEF;
import rddl.RDDL.EXPR;
import rddl.RDDL.INT_CONST_EXPR;
import rddl.RDDL.LCONST;
import rddl.RDDL.LVAR;
import rddl.RDDL.PVAR_EXPR;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.RDDL.PVAR_NAME;
import rddl.RDDL.TYPE_NAME;
import rddl.RDDL.OBJECT_VAL;
import rddl.RDDL.ENUM_VAL;
import rddl.RDDL.REAL_CONST_EXPR;
import rddl.State;
import rddl.policy.Policy;
import rddl.viz.StateViz;
//import sun.java2d.cmm.lcms.LcmsServiceProvider;
import util.Pair;
import util.Timer;

public class FittedEmergencyDomainHOPTranslate extends HOPTranslate {

	private static final boolean SHOW_TIMING = false;
	public static final PVAR_NAME firstResponsePvarName = new PVAR_NAME("firstResponse");
	public static final PVAR_NAME fullResponsePvarName = new PVAR_NAME("fullResponse");
	public static final PVAR_NAME overwhelmPvarName = new PVAR_NAME("overwhelm");
	private static final String gumbelNoisePvarName = "gumbelNoise";
	private static final String gapTimePvarName = "gapTime";
	private static final String tempCurrentCallComponentPvarName = "tempCurrentCallComponent";
	private static final String nextCallPvarName = "nextCall";
	private static final String currentCallCode = "currentCallCode";
	private static final String currentCallTime ="currentCallTime";
	private static final String currentCallTimeOfDay="currentCallTimeOfDay";
	private static final String tempUniformCause="tempUniformCause";
	private static final String uniformNumber="uniformNumber";
	private static final String currentCall = "currentCall";
	
	private EmergencyDomainDataReel reel;
	private FileWriter outFile;
	private static FileWriter fw;
	private static FileWriter fw1;
	
	public FittedEmergencyDomainHOPTranslate(List<String> args) throws Exception {
		//The parent class is HOPTranslate --> Translate!!! 
		//Total length of args = 15 , length passed here is 13
		
		super(args.subList(0, args.size()-3));
		//int sub_val = 2 ;
		//System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$The Code ran was initiazed HOPTranslate.java and Translate.java.$$$$$$$$");
		//This piece of code reads the historic data.
		reel = new EmergencyDomainDataReel( args.get( args.size()-5 ), ",", true, 
				false, Integer.parseInt( args.get( args.size()- 4 ) ), //numfolds
				Integer.parseInt( args.get( args.size()- 3 ) ), //training fold
				Integer.parseInt( args.get( args.size()- 2) ) ); //testing fold
		
		outFile = new FileWriter(new File( args.get( args.size()-1 ) ) );
		outFile.write("Round,Step,FirstTimeResponse,FullTimeResponse,OverWhelm,NoDispatch,TimeTaken,Cause,CauseRequirement,ActionTaken,exitCode,Emergency_Region,GreedyChoice,Action_Votes,Vehicles_Available,Number_Available\n");

		System.out.println("-----End of FittedEmergencyDomainHOPTranslate INIT Function------------");
	}
	
	@Override
	protected void prepareModel( ) throws Exception{
		translate_time.ResumeTimer();
		System.out.println("This is the first time (FittedEmergencyDomainHOP.java");
		System.out.println("--------------Translating Constraints (FittedEmergencyDomainHOP.java) -------------");
		System.out.println("-----Preparing Model --------");
		//Translating Constraints.............
		translateConstraints( static_grb_model );
		System.out.println("-----------Translating Constraints are done.  Now Working on PrepareModel Code!.-------------");
		
		//[{'PVAR_NAME':[[],[],[],[]]},{'PVAR_NAME':[[],[],[],[]]}]
		ArrayList<HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>> src 
		= new ArrayList< HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> >();
		src.add( rddl_state_vars ); src.add( rddl_interm_vars ); src.add( rddl_observ_vars );
		
		ArrayList<Integer> time_terms_indices = new ArrayList<Integer>( TIME_TERMS.size() );
		for( int i = 0 ; i < TIME_TERMS.size(); ++i ){
			time_terms_indices.add( i );
		}
		
		ArrayList<Integer> future_terms_indices = new ArrayList<Integer>( future_TERMS.size() );
		for( int i = 0 ; i < future_TERMS.size(); ++i ){
			future_terms_indices.add( i );
		}
		
		
		//[{'PVAR_NAME':[[],[],[],[]]},{'PVAR_NAME':[[],[],[],[]]}]
		src.stream().forEach( new Consumer< HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST> > > >() {

			@Override
			public void accept(
					HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> t) {
				
				//{'PVAR_NAME':[[],[],[],[]]}
				t.entrySet().stream().forEach( new Consumer< Entry<PVAR_NAME, ArrayList< ArrayList<LCONST>> > >() {

					
					@Override
					public void accept(
							Entry<PVAR_NAME, ArrayList<ArrayList<LCONST>>> entry ) {

						final String pvarName = entry.getKey()._sPVarName;
						//System.out.println("Dunno What's happening");
						//these are sampled from data in TranslateCPTs()
						if( isStochastic(pvarName) ){
							//This is Changed by HARISH.
							//System.out.println("Skipping " + pvarName);
							return;
						}
							
						//these are deterministic/known world model
						entry.getValue().stream().forEach( new Consumer< ArrayList<LCONST> >() {
							@Override
							public void accept(ArrayList<LCONST> terms) {
								PVAR_NAME p = entry.getKey();
								//This is changed by HARISH
								//System.out.println(p + " " + terms);
								
								CPF_DEF cpf = null;
								if( rddl_state_vars.containsKey(p) ){
									cpf = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName + "'" ) );
								}else {
									cpf = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName ) );
								}
											
								Map<LVAR, LCONST> subs = getSubs( cpf._exprVarName._alTerms, terms );
								EXPR new_lhs_stationary = cpf._exprVarName.substitute( subs, constants, objects );
								EXPR new_rhs_stationary = cpf._exprEquals.substitute(subs, constants, objects);
								//This is Changed by HARISH.
								//System.out.println(new_lhs_stationary + " " + new_rhs_stationary);		
								
								EXPR lhs_with_tf = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
										.addTerm(future_PREDICATE, constants, objects);
								EXPR rhs_with_tf = new_rhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
										.addTerm(future_PREDICATE, constants, objects);
											
								time_terms_indices.stream().forEach( new Consumer< Integer >() {
									@Override
									public void accept(Integer time_term_index ) {
										EXPR lhs_with_f_temp = null;
										if( rddl_state_vars.containsKey(p) ){
											if( time_term_index == lookahead-1 ){
												return;
											}
											lhs_with_f_temp = lhs_with_tf.substitute(
													Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index + 1 ) ), constants, objects);
										}else{
											lhs_with_f_temp = lhs_with_tf.substitute(
													Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index ) ), constants, objects);
										}
										final EXPR lhs_with_f = lhs_with_f_temp;
										final EXPR rhs_with_f = rhs_with_tf.substitute( 
										Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index ) ), constants, objects);
													
										future_terms_indices.stream().forEach( new Consumer<Integer>() {

											public void accept(Integer future_term_index) {
												EXPR lhs = lhs_with_f.substitute(
														Collections.singletonMap( future_PREDICATE, future_TERMS.get( future_term_index ) ), constants, objects);
												EXPR rhs = rhs_with_f.substitute(
														Collections.singletonMap( future_PREDICATE, future_TERMS.get( future_term_index) ), constants, objects);
																	
												EXPR lhs_future = future_gen.getFuture( lhs, rand, objects );
												EXPR rhs_future = future_gen.getFuture( rhs, rand, objects );
																	
												synchronized ( static_grb_model ) {
													try {
														GRBVar lhs_var = lhs_future.getGRBConstr( 
															GRB.EQUAL, static_grb_model, constants, objects, type_map);
														GRBVar rhs_var = rhs_future.getGRBConstr( 
															GRB.EQUAL, static_grb_model, constants, objects, type_map);
													
														//System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
														final String nam = RDDL.EXPR.getGRBName(lhs_future)+"="+RDDL.EXPR.getGRBName(rhs_future);
//														System.out.println(nam);;
														
														GRBConstr this_constr 
															= static_grb_model.addConstr( lhs_var, GRB.EQUAL, rhs_var, nam );
														saved_constr.add( this_constr );
														saved_expr.add(lhs_future);
														saved_expr.add(rhs_future);
													} catch (GRBException e) {
														e.printStackTrace();
														System.exit(1);
													}
												}
											}
										} );
									} 
								} );
							}
						} );
					}

				});
			}
		});
		
		System.out.println("--------------Translating Reward-------------");
		translateReward( static_grb_model );
		translate_time.PauseTimer();
	}

	private boolean isStochastic(String pvarName) {
		return stochasticVars.contains(pvarName);
	}
	//STOCASTIC VARS BEING DEFINED HERE.
	final Set<String> stochasticVars = new HashSet<String>(Arrays.asList( 
		new String[]{
				gumbelNoisePvarName, gapTimePvarName,
				tempCurrentCallComponentPvarName,nextCallPvarName,tempUniformCause,uniformNumber,currentCall }));

	@Override
	protected void translateCPTs(HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> subs,
			final GRBModel grb_model) throws GRBException {
		//There is no use of subs
		System.out.println("-----This is translateCPT(FittedEmergencyDomainHOPTransalate.java)");
		//THIS FUNCTION IS CALLED FOR EVERY NEW STATE. 
		//Timer timer1 = new Timer();
		long startTime1 = System.currentTimeMillis();
		GRBExpr old_obj = grb_model.getObjective();
		
		ArrayList<HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>> src 
		= new ArrayList< HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> >();
		src.add( rddl_state_vars ); src.add( rddl_interm_vars ); src.add( rddl_observ_vars );
		
		ArrayList<Integer> time_terms_indices = new ArrayList<Integer>( TIME_TERMS.size() );
		for( int i = 0 ; i < TIME_TERMS.size(); ++i ){
			time_terms_indices.add( i );
		}
		
		ArrayList<Integer> future_terms_indices = new ArrayList<Integer>( future_TERMS.size() );
		for( int i = 0 ; i < future_TERMS.size(); ++i ){
			future_terms_indices.add( i );
		}
		System.out.println("-------This is were we are sampling the future!!!!--");
		//This function is inherited from HOPTranslate.java.
		src.stream().forEach( new Consumer< HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST> > > >() {

			@Override
			public void accept(
					HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> t) {
				t.entrySet().stream().forEach( new Consumer< Entry<PVAR_NAME, ArrayList< ArrayList<LCONST>> > >() {

					@Override
					public void accept(
							Entry<PVAR_NAME, ArrayList<ArrayList<LCONST>>> entry ) {
						entry.getValue().stream().forEach( new Consumer< ArrayList<LCONST> >() {
							@Override
							public void accept(ArrayList<LCONST> terms) {
								
								PVAR_NAME p = entry.getKey();

								if( !isStochastic(p._sPVarName) ){
									//System.out.println("This is not stochastic Pvar : "+p._sPVarName);
									return;
								}
								
								//System.out.println("These variables are translated : "+ p._sPVarName);
								CPF_DEF cpf = null;
								if( rddl_state_vars.containsKey(p) ){
									cpf = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName + "'" ) );
								}else {
									cpf = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName ) );
								}
								
								Map<LVAR, LCONST> subs = getSubs( cpf._exprVarName._alTerms, terms );
								EXPR new_lhs_stationary = cpf._exprVarName.substitute( subs, constants, objects );
								
								EXPR new_rhs_stationary = cpf._exprEquals.substitute(subs, constants, objects);
								
//								System.out.println(new_lhs_stationary );//.+ " " + new_rhs_stationary );
								//This is commented by HARISH.
								//System.out.println(new_lhs_stationary + " " + new_rhs_stationary );
								
								EXPR lhs_with_tf = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
										.addTerm(future_PREDICATE, constants, objects);
								EXPR rhs_with_tf = new_rhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
										.addTerm(future_PREDICATE, constants, objects);
								System.out.println(lhs_with_tf + " " + rhs_with_tf );
								
								time_terms_indices.stream().forEach( new Consumer< Integer >() {
									@Override
									public void accept(Integer time_term_index ) {
										EXPR lhs_with_f_temp = null;
										if( rddl_state_vars.containsKey(p) ){
											if( time_term_index == lookahead-1 ){
												return;
											}
											
											lhs_with_f_temp = lhs_with_tf.substitute(
													Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index + 1 ) ), constants, objects);
										}else{
											lhs_with_f_temp = lhs_with_tf.substitute(
													Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index ) ), constants, objects);
										}
										final EXPR lhs_with_f = lhs_with_f_temp;
										
										final EXPR rhs_with_f = rhs_with_tf.substitute( 
												Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index ) ), constants, objects);
										
										future_terms_indices.stream().forEach( 
												new Consumer<Integer>() {
													public void accept(Integer future_term_index) {
														EXPR lhs = lhs_with_f.substitute(
																Collections.singletonMap( future_PREDICATE, future_TERMS.get( future_term_index ) ), constants, objects);
														EXPR rhs = rhs_with_f.substitute(
																Collections.singletonMap( future_PREDICATE, future_TERMS.get( future_term_index) ), constants, objects);
														//System.out.println("Something related to future is happening");
														EXPR lhs_future = future_gen.getFuture( lhs, rand, objects );
														EXPR rhs_future = future_gen.getFuture( rhs, rand, objects );
//														System.out.println("lhs_future:"+ lhs_future+ "  rhs_future:"+ rhs_future );
//														synchronized ( lhs_future ) {
//															synchronized ( rhs_future ) {
																synchronized ( grb_model ) {
																	
																	try {
																		GRBVar lhs_var = lhs_future.getGRBConstr( 
																			GRB.EQUAL, grb_model, constants, objects, type_map);

																		GRBVar rhs_var = rhs_future.getGRBConstr( 
																				GRB.EQUAL, grb_model, constants, objects, type_map);
																		
																		//System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
																		final String nam = RDDL.EXPR.getGRBName(lhs_future)+"="+RDDL.EXPR.getGRBName(rhs_future);
//																		System.out.println(nam);;
																		
																		GRBConstr this_constr 
																			= grb_model.addConstr( lhs_var, GRB.EQUAL, rhs_var, nam );
																		to_remove_constr.add( this_constr );
																		to_remove_expr.add( lhs_future );
																		to_remove_expr.add( rhs_future );
																		
																	} catch (GRBException e) {
																		e.printStackTrace();
																		System.exit(1);
																	}

																}
//															}
//														}
														
													}
												} );
										} 
									} );
								}
							} );
					}
				});
			}
		});
		
		grb_model.setObjective(old_obj);
		grb_model.update();
		long endTime1 = System.currentTimeMillis();
		double t3 = ((double)endTime1-(double)startTime1)/1000;
		System.out.println("Time Taken for TranslateCPT Without Emergency Data:" + t3);

		
	}
	
	public Pair<Double,Double> evaluatePlanner( final int numRounds,  final StateViz stateViz, 
			final boolean randomize_test ) throws Exception{
		ArrayList<Double> rewards = new ArrayList<Double>();		
		System.out.println("number of rounds"+numRounds);
		//FileWriter fw = new FileWriter("./files/emergency_domain/results/State_Sequence_result_2_v1.txt");
		//FileWriter fw1 = new FileWriter("./files/emergency_domain/results/Action_Sequence_result_2_v1.txt");
		fw.flush();
		for( int round = 0; round < numRounds; ++round ){
			double cur_discount = 1.0;
			double accum_reward = 0.0;
			fw.write("--------Round Number:" + round +'\n');
			fw1.write("--------Round Number:" + round + '\n');
			
			System.out.println("-----------------------------THIS IS FOR ROUND-----------------------------------------------------");
			System.out.print("--------Round Number :"+ round);
			rddl_state.init( 	rddl_domain._hmObjects,
								rddl_nonfluents != null ? rddl_nonfluents._hmObjects : null,
								rddl_instance._hmObjects,
								rddl_domain._hmTypes,
								rddl_domain._hmPVariables,
								rddl_domain._hmCPF,
								rddl_instance._alInitState,
								rddl_nonfluents == null ? null : rddl_nonfluents._alNonFluents,
								null,
								rddl_domain._alStateConstraints,
								rddl_domain._alActionPreconditions,
								rddl_domain._alStateInvariants,
								rddl_domain._exprReward, rddl_instance._nNonDefActions);
			
			
			
//			final int test_start_idx = rand.nextInt( 0, reel.getNumTestInstances()-1-this.rddl_instance._nHorizon );
//			final int test_start_idx = (int)(round * (reel.getNumTestInstances()/numRounds));
//			assert( test_start_idx < reel.getNumTestInstances() && test_start_idx + this.rddl_instance._nHorizon < reel.getNumTestInstances() );
			
			
			
			
			reel.resetTestIndex( 0 ); //test_start_idx );
			
			EmergencyDomainDataReelElement stored_next_thing = null;
			System.out.println("Horizon of the MDP:" + this.rddl_instance._nHorizon);
			HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> copiedState = new HashMap<>();
			
			for( int step = 0; step < this.rddl_instance._nHorizon; ++step ){
				fw.write("--------Step Number :" + step + '\n');
				fw1.write("--------Step Number :" + step + '\n');
				System.out.println("-----------Step Number : "+ step);
				String callcode = "";
				String causeReq ="";
				String region_data="";
				ArrayList<Object> temp_req      = new ArrayList<Object>();
				HashMap<String, Double> cur_location = new HashMap<String, Double>();
				HashMap<String, Double> distance_emergency = new HashMap<String, Double>();
				HashMap<String, String> cur_availbility = new HashMap<String, String>();
				if( step == 0 || !randomize_test ){
					//copy back next state of exogenous var, Generating Next State from the testing data.

					EmergencyDomainDataReelElement exo_thing = reel.getNextTestingInstance();
					//This is the place, we set the next state. Change when you want to use the real emergency data
					//exo_thing.setInState( rddl_state );
					
					callcode = rddl_state.getCurrentCode();
					temp_req = rddl_state.getCauseRequirment(callcode);
					cur_location = rddl_state.getCurrentCallLoction();
					
					causeReq = temp_req.get(0).toString();
					
					
					
					
					
					
					if(cur_location.get("[$xpos]")>1400) {
						
						region_data = "Region_2";
					}
					else {
						region_data = "Region_1";
					}
					
					
					//causeReq = rddl_state.getCauseRequirment(callcode);
					System.out.println("Current Call Code :" + callcode);
					System.out.println("Current Requirment :"+causeReq );

					
					
				}else{
					stored_next_thing.setInState( rddl_state );
					callcode = rddl_state.getCurrentCode();
					temp_req = rddl_state.getCauseRequirment(callcode);
					causeReq = temp_req.get(0).toString();
					//System.out.println("Current State ID:"+exo_thing.callId);
					System.out.println("Current Call Code :" + callcode);
					System.out.println("Current Requirment :"+causeReq );
				}
				//System.out.println("---->Printing State Information Started!!!");
				System.out.println( "Current/Next State : " );
				System.out.println(rddl_state);

				fw.write("--------Current/Next State:\n" );
		
				fw.write(rddl_state.toString() + '\n');
				
				
				//FileWriter fw = new FileWriter("./files/emergency_domain/results/Policy_result_2.txt");
				//fw.write(rddl_state.toString());
				fw.flush();
				//fw.close();
				
				//System.out.println("--->Printing State Information Ended!!!!");
				//Timer timer = new Timer();
				//This is the place where we get the best Action. 
				System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------");
				Timer timer = new Timer();
				long startTime = System.currentTimeMillis();
				copiedState = deepCopyState(rddl_state);



				//runRandomPolicy(rddl_state,10,3, randint);

				ArrayList<PVAR_INST_DEF> rddl_action = getActions(rddl_state);


				System.out.println("ALL VOTES :"+ all_votes);
				//HashMap<HashMap<EXPR, Object> , Integer >
				StringBuilder sb = new StringBuilder();
				for(HashMap<EXPR, Object> key : all_votes.keySet()) {
					
					sb.append(key.toString() +" : ");
					sb.append(all_votes.get(key).toString()+" : ");
					
					
					
				}
				
				String store_all_votes = sb.toString();
				store_all_votes=store_all_votes.replace(",", ":");
				all_votes.clear();
				System.out.println("Exit Code of the optimizer :"+ exit_code);
				long endTime = System.currentTimeMillis();

				System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------");
				System.out.println("--------GOT ACTION AFTER RUNNING OPTIMIZER!!!!!!!");
				double t1 = ((double)endTime-(double)startTime)/1000; //= timer.GetElapsedTimeInSecondsAndReset();
				
				
				
				
				try {
					//System.out.println("I am here ----> 2");
					System.out.println("------------------------------------");
					//System.out.println("State : " + rddl_state );
					System.out.println("Action From Optimizer : " + rddl_action);
					fw.write('\n'+ rddl_action.toString() + '\n');
					fw1.write(rddl_action.toString()+"\n");
					fw1.flush();
					String action_taken = rddl_action.toString().replaceAll(",", ":");
					System.out.println("------------------------------------");
					boolean novehicle = rddl_action.toString().equals("[]");
					///This is the place where we are defining randomonized tests!!!!
					if( randomize_test ){
						EmergencyDomainDataReelElement cur_thing = new EmergencyDomainDataReelElement(rddl_state);
						ArrayList<Integer> next_indices = reel.getLeads(cur_thing, reel.getTestingFoldIdx() );
						EmergencyDomainDataReelElement thatElem = reel.getInstance( 
								next_indices.get( rand.nextInt(0, next_indices.size()-1) ), reel.getTestingFoldIdx() );

						//fix date to be not in the past
						LocalDate newCallDate;
						if( thatElem.callTime.isBefore(cur_thing.callTime) ){
							newCallDate = LocalDate.ofYearDay( cur_thing.callDate.getYear(), cur_thing.callDate.getDayOfYear()+1);
						}else{
							newCallDate = LocalDate.ofYearDay( cur_thing.callDate.getYear(), cur_thing.callDate.getDayOfYear()); 
						}
						stored_next_thing = new EmergencyDomainDataReelElement( thatElem.callId, thatElem.natureCode, 
								newCallDate, thatElem.callTime, thatElem.callAddress, thatElem.callX, thatElem.callY, false );
						
						System.out.println( "Current : " + cur_thing );
						System.out.println( "Candidates : " + next_indices );
						System.out.println( "Selected : " + stored_next_thing );
					}
					//This is the place where we are getting next action, Need to check the procedure.
					System.out.println("----------Calculating Interm State and Printing it!!!!!");
					//Computing Next State
					
					
					rddl_state.computeNextState(rddl_action, rand);
					distance_emergency =  rddl_state.getDistanceEmergency();
					cur_availbility    =  rddl_state.getVehicleAvailability();
					
					String greedy_choice = getGreedyChoice(distance_emergency,cur_availbility);
					HashMap<String,String> resource_availbility = getResourceAvailbility(cur_availbility);
					
					String Vehicles_available = resource_availbility.get("Vehicles_available");
					String Number_available = resource_availbility.get("Number_available");
					
					
					
					System.out.println("Interm State : "  );
					System.out.println(rddl_state);
					fw.write("----------------Interm State:\n");
					fw.write(rddl_state.toString() +"\n");
					fw.flush();
					System.out.println("----------------------------------------------------------------------------");
					System.out.println("------>Writing FirstResponse, FullResponse, OverWhelm Time to .csv File!!!!.");
					if(hindsight_method.toString()=="ROOT_CONSENSUS"){
						outFile.write( round +","+step +","+ 60*EmergencyDomainHOPTranslate.getFirstResponse(rddl_state) + "," + 60*EmergencyDomainHOPTranslate.getFullResponse(rddl_state)  + "," + EmergencyDomainHOPTranslate.getOverwhelm(rddl_state) + "," +novehicle+","+ t1 + "," + callcode +"," + causeReq + "," + action_taken +","+ exit_code+","+ region_data+","+greedy_choice+","+store_all_votes +","+Vehicles_available+","+Number_available+","+objectiveValues.get(0).toString()+","+objectiveValues.get(1).toString()+","+ret_list.get(0).toString().replaceAll(",", ":")+","+ret_list.get(1).toString().replaceAll(",", ":"));



					}
					else{

						outFile.write( round +","+step +","+ 60*EmergencyDomainHOPTranslate.getFirstResponse(rddl_state) + "," + 60*EmergencyDomainHOPTranslate.getFullResponse(rddl_state)  + "," + EmergencyDomainHOPTranslate.getOverwhelm(rddl_state) + "," +novehicle+","+ t1 + "," + callcode +"," + causeReq + "," + action_taken +","+ exit_code+","+ region_data+","+greedy_choice+","+store_all_votes +","+Vehicles_available+","+Number_available);


					}
					ret_list.clear();
					objectiveValues.clear();






//					outFile.write( 60*EmergencyDomainHOPTranslate.getFirstResponse(rddl_state) 
//							+ "," + 60*EmergencyDomainHOPTranslate.getFullResponse(rddl_state) 
//							+ "," + EmergencyDomainHOPTranslate.getOverwhelm(rddl_state) );
					outFile.write("\n");
					outFile.flush();
					if(stateViz != null){
						stateViz.display(rddl_state, step);			
					}
				} catch (Exception ee) {
					System.out.println("FATAL SERVER EXCEPTION:\n" + ee);
					throw ee;
				}
				
				
				
				
				
				
				try {
					//Need to Understand what's happening here!!!.
					System.out.println("===========> Checking State Action Constraints!!!!!");
					rddl_state.checkStateActionConstraints(rddl_action);
				} catch (Exception e) {
					System.out.println("TRIAL ERROR -- STATE-ACTION CONSTRAINT VIOLATION:\n" + e);
					throw e;
				}

				
				
				if (SHOW_TIMING){
					System.out.println("**TIME to compute next state: " + timer.GetTimeSoFarAndReset());
				}
				
				
				
				
				
				// Calculate reward / objective and store
//				final double immediate_reward = ((Number)rddl_domain._exprReward.sample(
//						new HashMap<LVAR,LCONST>(),rddl_state, rand)).doubleValue();

				
				//accum_reward += cur_discount * immediate_reward;
				cur_discount *= rddl_instance._dDiscount;
				
				if (SHOW_TIMING){
					System.out.println("**TIME to copy observations & update rewards: " + timer.GetTimeSoFarAndReset());
				}
				
				
				
				//Need to Understand This Function 
				System.out.println("===========> Calculating AdvanceNext State !!!!!");
				rddl_state.advanceNextState();				
									
				if (SHOW_TIMING){
					System.out.println("**TIME to advance state: " + timer.GetTimeSoFarAndReset());
				}
			}
			outFile.flush();
			System.out.println("Round reward " + accum_reward);
			rewards.add( accum_reward );
			
			if( round != numRounds-1 ){
				handleOOM(static_grb_model);
			}
			
		}
		
		System.out.println("Round rewards : " + rewards );

		//final double session_mean_reward = rewards.stream().mapToDouble(r->r).average().getAsDouble();
		//final double stdev = Math.sqrt( (1.0/(numRounds-1))*(rewards.stream()
		//						.mapToDouble(r->(r-session_mean_reward)*(r-session_mean_reward))
		//						.sum()) );


		fw.close();
		fw1.close();
		Double session_mean_reward = 1.0;
		Double stdev = 1.0;

		return new Pair<Double,Double>(session_mean_reward, stdev);
	
	
	
	
	
	
	
	}
	
	
	
	public HashMap<String,String> getResourceAvailbility(HashMap<String,String>vehicle_availbility) throws Exception{
		StringBuilder sb = new StringBuilder();
		HashMap<String,String> output_dict = new HashMap<String,String>();
		Integer temp_count = 0;
		for(String vehicle_number : vehicle_availbility.keySet()) {
			if(vehicle_availbility.get(vehicle_number)=="true") {
				
				sb.append(vehicle_number+":");
				temp_count=temp_count+1;
				
				
			}
			
		}
		output_dict.put("Vehicles_available", sb.toString());
		output_dict.put("Number_available",temp_count.toString());
		
		return(output_dict);
		
	}
	
	
	public String getGreedyChoice(HashMap<String, Double> distance_emergency , HashMap<String, String> vehicle_availbility) throws Exception{
		
		
		System.out.print("Here I am to find which one was the greedy Choice");
		
		
		
		String best_choice = "";
		Double best_distance = 10000000.0;
		for (String vehicle_number : distance_emergency.keySet()) {
			
			if(vehicle_availbility.get(vehicle_number)=="true") {
				
				if(distance_emergency.get(vehicle_number)<best_distance) {
					
					best_choice = vehicle_number;
					best_distance = distance_emergency.get(vehicle_number);
				}
				
				
			}
				
			
		}
		
		String out_put = best_choice +" : "+ best_distance.toString();
		
				
				
				
				
		return(out_put);
		
		
	}





	@Override
	protected Map< EXPR, Double > outputAllResults( final GRBModel grb_model ) throws GRBException{


		System.out.println("------This is output results for GRB MODEL -------");
//		DecimalFormat df = new DecimalFormat("#.##########");
//		df.setRoundingMode( RoundingMode.DOWN );


		HashMap<String,HashMap<String,String>> storeResults= new HashMap<String,HashMap<String,String>>();


		if( grb_model.get( GRB.IntAttr.SolCount ) == 0 ){
			return null;
		}

		Map< EXPR, Double > ret = new HashMap< EXPR, Double >();

		HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> src = new HashMap<>();
		//src.putAll( rddl_action_vars );
		//src.putAll( rddl_interm_vars );
		src.putAll( rddl_state_vars );

		src.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST> > >( ) {

			@Override
			public void accept(PVAR_NAME pvar,
							   ArrayList<ArrayList<LCONST>> u) {
				u.forEach( new Consumer< ArrayList<LCONST> >( ) {
					@Override
					public void accept(ArrayList<LCONST> terms ) {
						TIME_TERMS.stream().forEach( new Consumer< LCONST >() {
							public void accept(LCONST time_term ) {

								HashMap<String,String> temp_data =new HashMap<String,String>();

								future_TERMS.forEach( new Consumer<LCONST>() {
									@Override
									public void accept(LCONST future_term) {

										if((!pvar._sPVarName.equals("currentCall"))){
											return;
										}

										if(!terms.get(0).toString().equals("$xpos")){
										    return;
                                        }


										EXPR action_var = new PVAR_EXPR( pvar._sPVarName, terms )
												.addTerm(TIME_PREDICATE, constants, objects)
												.addTerm(future_PREDICATE, constants, objects)
												.substitute( Collections.singletonMap( TIME_PREDICATE, time_term ), constants, objects)
												.substitute( Collections.singletonMap( future_PREDICATE, future_term ) , constants, objects);









										try {



											GRBVar grb_var = EXPR.grb_cache.get( action_var );
											System.out.println(action_var);
											//System.out.println(grb_var);
											assert( grb_var != null );
											Double actual = grb_var.get( GRB.DoubleAttr.X );

											temp_data.put(future_term._sConstValue.split("future*")[1],actual.toString());


											//NOTE : uncomment this part if having issues with constrained actions
											//such as if you get -1E-11 instead of 0,
											//and you are expecting a positive action >= 0
											String interm_val = State._df.format( actual );
											System.out.println( action_var + "Actual Value: "+ actual + " rounded to " + interm_val );

											ret.put( action_var, Double.valueOf(  interm_val ) );
										} catch (GRBException e) {
											e.printStackTrace();
											System.exit(1);
										}

									}




								});

								storeResults.put(time_term._sConstValue.split("time*")[1],temp_data);







							}
						});




					}
				});
			}

		});

		System.out.println( "Maximum (unscaled) bound violation : " +  + grb_model.get( GRB.DoubleAttr.BoundVio	) );
		System.out.println("Sum of (unscaled) constraint violations : " + grb_model.get( GRB.DoubleAttr.ConstrVioSum ) );
		System.out.println("Maximum integrality violation : "+ grb_model.get( GRB.DoubleAttr.IntVio ) );
		System.out.println("Sum of integrality violations : " + grb_model.get( GRB.DoubleAttr.IntVioSum ) );
		System.out.println("Objective value : " + grb_model.get( GRB.DoubleAttr.ObjVal ) );

		return ret;
	}














	public static void main(String[] args) throws Exception {
		System.out.println( Arrays.toString( args ) );
		
		fw = new FileWriter(new File( Arrays.asList(args).get( Arrays.asList(args).size()-2 ) ) );
		fw1 = new FileWriter(new File( Arrays.asList(args).get( Arrays.asList(args).size()-1 ) ) );
		
		FittedEmergencyDomainHOPTranslate planner = new FittedEmergencyDomainHOPTranslate( 
				Arrays.asList( args ).subList(0, args.length-2-2) );
		
		
		System.out.println("-----------Evaluating the planner Started---------------");
		//This piece of code for evaluting the planner!!!. 
		System.out.println( planner.evaluatePlanner(
				Integer.parseInt( args[args.length-2-2] ), 
				null, //new EmergencyDomainStateViz(1300,30,1500,80), 
				Boolean.parseBoolean( args[ args.length-1-2 ] ) ) );
	}

}
