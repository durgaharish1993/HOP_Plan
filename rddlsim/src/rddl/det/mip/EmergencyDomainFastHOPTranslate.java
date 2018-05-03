//package rddl.det.mip;
//
//
//
//import java.io.File;
//import java.io.FileFilter;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.ObjectInputStream;
//import java.io.ObjectOutputStream;
//import java.io.Serializable;
//import java.time.LocalDate;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.function.BiConsumer;
//import java.util.function.Consumer;
//
//import org.apache.commons.math3.random.RandomDataGenerator;
//
//import gurobi.GRB;
//import gurobi.GRBConstr;
//import gurobi.GRBException;
//import gurobi.GRBExpr;
//import gurobi.GRBLinExpr;
//import gurobi.GRBModel;
//import gurobi.GRBVar;
//import gurobi.GRB.StringAttr;
//import rddl.EvalException;
//import rddl.RDDL;
//import rddl.RDDL.BOOL_CONST_EXPR;
//import rddl.RDDL.BOOL_EXPR;
//import rddl.RDDL.COMP_EXPR;
//import rddl.RDDL.CPF_DEF;
//import rddl.RDDL.EXPR;
//import rddl.RDDL.INT_CONST_EXPR;
//import rddl.RDDL.LCONST;
//import rddl.RDDL.LVAR;
//import rddl.RDDL.PVARIABLE_DEF;
//import rddl.RDDL.PVAR_EXPR;
//import rddl.RDDL.PVAR_INST_DEF;
//import rddl.RDDL.PVAR_NAME;
//import rddl.RDDL.REAL_CONST_EXPR;
//import rddl.RDDL.TYPE_NAME;
//import rddl.det.mip.HOPTranslate.HINDSIGHT_STRATEGY;
//import rddl.State;
//import rddl.policy.Policy;
//import rddl.viz.StateViz;
//import util.Pair;
//import util.Timer;
//
//
//
//public class EmergencyDomainFastHOPTranslate extends HOPTranslate{
//
//	private static final boolean SHOW_TIMING = false;
//	public static final PVAR_NAME firstResponsePvarName = new PVAR_NAME("firstResponse");
//	public static final PVAR_NAME fullResponsePvarName = new PVAR_NAME("fullResponse");
//	public static final PVAR_NAME overwhelmPvarName = new PVAR_NAME("overwhelm");
//
//
//
//
//	private EmergencyDomainDataReel reel;
//	private FileWriter outFile;
//	private static FileWriter fw;
//	private static FileWriter fw1;
//
//	//Serialization flag
//	private final boolean isSerializable = false;
//
//
//	public EmergencyDomainFastHOPTranslate(List<String> args) throws Exception {
//
//
//
//		super(args.subList(0, args.size()-3));
//		System.out.println("I am here");
//		System.out.println( args );
//		// 1. File Name
//		// 2. Comma seperator
//		// 3.
//		// 4.
//		// 5. Number of Folds
//		// 6. Training Folds
//		// 7. Testing Folds.
//		reel = new EmergencyDomainDataReel( args.get( args.size()-5 ), ",", true,
//				false, Integer.parseInt( args.get( args.size()- 4 ) ), //numfolds
//				Integer.parseInt( args.get( args.size()- 3 ) ), //training fold
//				Integer.parseInt( args.get( args.size()- 2 ) ) ); //testing fold
//		outFile = new FileWriter(new File( args.get( args.size()-1 ) ) );
//
//	}
//
//
//
//
//
//
//	public Pair<Double,Double> evaluatePlanner( final int numRounds,  final StateViz stateViz, final boolean randomize_test ) throws Exception{
//
//		//FileWriter fw = new FileWriter("./files/emergency_domain/results/closest_neighbour_result/State_closest_unFitted_1.txt");
//		//FileWriter fw1 = new FileWriter("./files/emergency_domain/results/closest_neighbour_result/Action_closest_unFitted_1.txt");
//		//fw.flush();
//
//
//
//		ArrayList<Double> rewards = new ArrayList<Double>();
//		for( int round = 0; round < numRounds; ++round ){
//			double cur_discount = 1.0;
//			double accum_reward = 0.0;
//			fw.write("--------Round Number:" + round +'\n');
//			fw1.write("--------Round Number:" + round + '\n');
//
//			//State rddl_state1 = new State();
//			rddl_state.init( rddl_domain._hmObjects, rddl_nonfluents != null ? rddl_nonfluents._hmObjects : null,
//					rddl_instance._hmObjects, rddl_domain._hmTypes, rddl_domain._hmPVariables, rddl_domain._hmCPF,
//					rddl_instance._alInitState, rddl_nonfluents == null ? null : rddl_nonfluents._alNonFluents,
//							rddl_domain._alStateConstraints, rddl_domain._alActionPreconditions, rddl_domain._alStateInvariants,
//							rddl_domain._exprReward, rddl_instance._nNonDefActions);
//
//
//
//			//			final int test_start_idx = rand.nextInt( 0, reel.getNumTestInstances()-1-this.rddl_instance._nHorizon );
//			//			final int test_start_idx = (int)(round * (reel.getNumTestInstances()/numRounds));
//			//			assert( test_start_idx < reel.getNumTestInstances() && test_start_idx + this.rddl_instance._nHorizon < reel.getNumTestInstances() );
//
//			reel.resetTestIndex( 0 ); //test_start_idx );
//
//			EmergencyDomainDataReelElement stored_next_thing = null;
//
//			for( int step = 0; step < this.rddl_instance._nHorizon; ++step ){
//				fw.write("--------Step Number :" + step + '\n');
//				fw1.write("--------Step Number :" + step + '\n');
//
//				if( step == 0 || !randomize_test ){
//					//copy back next state of exogenous vars
//					EmergencyDomainDataReelElement exo_thing = reel.getNextTestingInstance();
//					exo_thing.setInState( rddl_state  );
//				}else{
//					stored_next_thing.setInState( rddl_state );
//				}
//				System.out.println("-------This is the place where its taking nearst Neighbour Action!!");
//				System.out.println( "Next State : " + rddl_state.toString() );
//				fw.write("--------Current/Next State:\n" );
//
//				fw.write(rddl_state.toString() + '\n');
//
//				Timer timer = new Timer();
//				long startTime = System.currentTimeMillis();
//				System.out.println("----Getting Action!!!!");
//				ArrayList<PVAR_INST_DEF> rddl_action = getActions(rddl_state);
//				long endTime = System.currentTimeMillis();
//				double t1 = ((double)endTime-(double)startTime)/1000; //= timer.GetElapsedTimeInSecondsAndReset();
//				System.out.println("OVerall Time for Getting Action = " + t1);
//				try {
//					System.out.println("------------------------------------");
//					System.out.println("State : " + rddl_state );
//					System.out.println("Action : " + rddl_action);
//					System.out.println("------------------------------------");
//					fw.write("\n" + rddl_action.toString()+"\n");
//					fw1.write( rddl_action.toString()+"\n");
//					fw1.flush();
//					fw.flush();
//					if( randomize_test ){
//						EmergencyDomainDataReelElement cur_thing = new EmergencyDomainDataReelElement(rddl_state);
//						ArrayList<Integer> next_indices = reel.getLeads(cur_thing, reel.getTestingFoldIdx() );
//						EmergencyDomainDataReelElement thatElem = reel.getInstance(
//								next_indices.get( rand.nextInt(0, next_indices.size()-1) ), reel.getTestingFoldIdx() );
//
//						//fix date to be not in the past
//						LocalDate newCallDate;
//						if( thatElem.callTime.isBefore(cur_thing.callTime) ){
//							newCallDate = LocalDate.ofYearDay( cur_thing.callDate.getYear(), cur_thing.callDate.getDayOfYear()+1);
//						}else{
//							newCallDate = LocalDate.ofYearDay( cur_thing.callDate.getYear(), cur_thing.callDate.getDayOfYear());
//						}
//						stored_next_thing = new EmergencyDomainDataReelElement( thatElem.callId, thatElem.natureCode,
//								newCallDate, thatElem.callTime, thatElem.callAddress, thatElem.callX, thatElem.callY, false );
//
//						System.out.println( "Current : " + cur_thing );
//						System.out.println( "Candidates : " + next_indices );
//						System.out.println( "Selected : " + stored_next_thing );
//					}
//
//					rddl_state.computeNextState(rddl_action, rand);
//					System.out.println("Interm State : " + rddl_state );
//					System.out.println("------------------------------------");
//					fw.write("----------------Interm State:\n");
//					fw.write(rddl_state.toString() +"\n");
//					fw.flush();
//					outFile.write( round +","+step +","+ 60*getFirstResponse(rddl_state) + "," + 60*getFullResponse(rddl_state) + "," + getOverwhelm(rddl_state) + "," + t1  );
//					outFile.write("\n");
//					outFile.flush();
//					if(stateViz != null){
//						stateViz.display(rddl_state, step);
//					}
//				} catch (Exception ee) {
//					System.out.println("FATAL SERVER EXCEPTION:\n" + ee);
//					throw ee;
//				}
//
//				try {
//					rddl_state.checkStateActionConstraints(rddl_action);
//				} catch (Exception e) {
//					System.out.println("TRIAL ERROR -- STATE-ACTION CONSTRAINT VIOLATION:\n" + e);
//					throw e;
//				}
//
//				if (SHOW_TIMING){
//					System.out.println("**TIME to compute next state: " + timer.GetTimeSoFarAndReset());
//				}
//
//				// Calculate reward / objective and store
//				final double immediate_reward = ((Number)rddl_domain._exprReward.sample(
//						new HashMap<LVAR,LCONST>(),rddl_state, rand)).doubleValue();
//
//
//				accum_reward += cur_discount * immediate_reward;
//				cur_discount *= rddl_instance._dDiscount;
//
//				if (SHOW_TIMING){
//					System.out.println("**TIME to copy observations & update rewards: " + timer.GetTimeSoFarAndReset());
//				}
//
//				rddl_state.advanceNextState();
//
//				if (SHOW_TIMING){
//					System.out.println("**TIME to advance state: " + timer.GetTimeSoFarAndReset());
//				}
//			}
//			outFile.flush();
//			System.out.println("Round reward " + accum_reward);
//			rewards.add( accum_reward );
//
//			if( round != numRounds-1 ){
//				handleOOM(static_grb_model);
//			}
//
//		}
//
//		System.out.println("Round rewards : " + rewards );
//		final double session_mean_reward = rewards.stream().mapToDouble(r->r).average().getAsDouble();
//		final double stdev = Math.sqrt( (1.0/(numRounds-1))*(rewards.stream()
//				.mapToDouble(r->(r-session_mean_reward)*(r-session_mean_reward))
//				.sum()) );
//		return new Pair<Double,Double>(session_mean_reward, stdev);
//	}
//
//
//
//
//
//
//
//
//
//
//	@Override
//	protected void prepareModel( ) throws Exception{
//		translate_time.ResumeTimer();
//		System.out.println("--------------Translating Constraints-------------");
//		boolean temp = isSerializable;
//		String folderName = "./files/emergency_domain/serialize_output/translateConstraints/";
//		if(temp) {
//			long t1 = System.currentTimeMillis();
//			translateConstraints( static_grb_model );
//			long t2 = System.currentTimeMillis();
//			System.out.println("Time taken without Serialization: "+ (double)(t2-t1));
//		}
//		else {
//			long t1 = System.currentTimeMillis();
//			translateConstraintsDeSerialize(static_grb_model,folderName);
//			long t2 = System.currentTimeMillis();
//			System.out.println("Time taken with Serialization: "+ (double)(t2-t1));
//		}
//
//		//translateConstraints( static_grb_model );
//
//		System.out.println("--------Translating CPTs with No History Interactions ----------");
//		translateCPTWithoutHistory(static_grb_model);
//
//		System.out.println("--------------Translating Reward-------------");
//		translateReward( static_grb_model );
//		translate_time.PauseTimer();
//	}
//
//	//This is a new function for translating the state variables which don't interact with historic randomness.
//	protected void translateCPTWithoutHistory(final GRBModel grb_model) throws Exception{
//
//
//		ArrayList<HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>> src
//		= new ArrayList< HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> >();
//		src.add( rddl_state_vars ); src.add( rddl_interm_vars ); src.add( rddl_observ_vars );
//
//		ArrayList<Integer> time_terms_indices = new ArrayList<Integer>( TIME_TERMS.size() );
//		for( int i = 0 ; i < TIME_TERMS.size(); ++i ){
//			time_terms_indices.add( i );
//		}
//
//		ArrayList<Integer> future_terms_indices = new ArrayList<Integer>( future_TERMS.size() );
//		for( int i = 0 ; i < future_TERMS.size(); ++i ){
//			future_terms_indices.add( i );
//		}
//
//		src.stream().forEach( new Consumer< HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST> > > >() {
//
//			@Override
//			public void accept(
//					HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> t) {
//
//				t.entrySet().stream().forEach( new Consumer< Entry<PVAR_NAME, ArrayList< ArrayList<LCONST>> > >() {
//
//
//					@Override
//					public void accept(
//							Entry<PVAR_NAME, ArrayList<ArrayList<LCONST>>> entry ) {
//
//						final String pvarName = entry.getKey()._sPVarName;
//						//these are sampled from data in TranslateCPTs()
//						if( pvarName.equals(EmergencyDomainDataReelElement.currentCallPvarName._sPVarName) ||
//								pvarName.equals(EmergencyDomainDataReelElement.tempUniformCausePvarName._sPVarName) ||
//								pvarName.equals(EmergencyDomainDataReelElement.currentCallCodePvarName._sPVarName) ||
//								pvarName.equals(EmergencyDomainDataReelElement.gapTimePvarName._sPVarName) ||
//								pvarName.equals(EmergencyDomainDataReelElement.currentCallTimePvarName._sPVarName) ){
//							//							pvarName.equals(EmergencyDomainDataReelElement.tempUniformRegionPvarName._sPVarName) ||
//							//							pvarName.equals(EmergencyDomainDataReelElement.currentCallRegionPvarName._sPVarName) ){
//							return;
//						}
//
//						//these are deterministic/known world model
//						entry.getValue().stream().forEach( new Consumer< ArrayList<LCONST> >() {
//							@Override
//							public void accept(ArrayList<LCONST> terms) {
//								PVAR_NAME p = entry.getKey();
//								System.out.println(p + " " + terms);
//
//								CPF_DEF cpf = null;
//								if( rddl_state_vars.containsKey(p) ){
//									cpf = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName + "'" ) );
//								}else {
//									cpf = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName ) );
//								}
//
//								Map<LVAR, LCONST> subs = getSubs( cpf._exprVarName._alTerms, terms );
//								EXPR new_lhs_stationary = cpf._exprVarName.substitute( subs, constants, objects );
//								EXPR new_rhs_stationary = cpf._exprEquals.substitute(subs, constants, objects);
//
//								EXPR lhs_with_tf = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
//										.addTerm(future_PREDICATE, constants, objects);
//								EXPR rhs_with_tf = new_rhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
//										.addTerm(future_PREDICATE, constants, objects);
//
//								time_terms_indices.stream().forEach( new Consumer< Integer >() {
//									@Override
//									public void accept(Integer time_term_index ) {
//										EXPR lhs_with_f_temp = null;
//										if( rddl_state_vars.containsKey(p) ){
//											if( time_term_index == lookahead-1 ){
//												return;
//											}
//											lhs_with_f_temp = lhs_with_tf.substitute(
//													Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index + 1 ) ), constants, objects);
//										}else{
//											lhs_with_f_temp = lhs_with_tf.substitute(
//													Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index ) ), constants, objects);
//										}
//										final EXPR lhs_with_f = lhs_with_f_temp;
//										final EXPR rhs_with_f = rhs_with_tf.substitute(
//												Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get( time_term_index ) ), constants, objects);
//
//										future_terms_indices.stream().forEach( new Consumer<Integer>() {
//
//											public void accept(Integer future_term_index) {
//												EXPR lhs = lhs_with_f.substitute(
//														Collections.singletonMap( future_PREDICATE, future_TERMS.get( future_term_index ) ), constants, objects);
//												EXPR rhs = rhs_with_f.substitute(
//														Collections.singletonMap( future_PREDICATE, future_TERMS.get( future_term_index) ), constants, objects);
//												//PVAR_EXPR
//												EXPR lhs_future = future_gen.getFuture( lhs, rand, objects );
//												//IF_EXPR
//												EXPR rhs_future = future_gen.getFuture( rhs, rand, objects );
//
//
//												synchronized ( static_grb_model ) {
//													try {
//														GRBVar lhs_var = lhs_future.getGRBConstr(
//																GRB.EQUAL, static_grb_model, constants, objects, type_map);
//														GRBVar rhs_var = rhs_future.getGRBConstr(
//																GRB.EQUAL, static_grb_model, constants, objects, type_map);
//
//														System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
//														final String nam = RDDL.EXPR.getGRBName(lhs_future)+"="+RDDL.EXPR.getGRBName(rhs_future);
//														//														System.out.println(nam);;
//
//														GRBConstr this_constr
//														= static_grb_model.addConstr( lhs_var, GRB.EQUAL, rhs_var, nam );
//														saved_constr.add( this_constr );
//														saved_expr.add(lhs_future);
//														saved_expr.add(rhs_future);
//													} catch (GRBException e) {
//														e.printStackTrace();
//														System.exit(1);
//													}
//												}
//											}
//										} );
//									}
//								} );
//							}
//						} );
//					}
//				});
//			}
//		});
//
//
//
//	}
//
//
//
//
//
//	@Override
//	protected void translateConstraints(final GRBModel grb_model) throws Exception {
//
//		GRBExpr old_obj = grb_model.getObjective();
//		//		translateMaxNonDef( );
//
//		System.out.println("--------------Translating Constraints(Overrided) -------------");
//
//		ArrayList<BOOL_EXPR> constraints = new ArrayList<BOOL_EXPR>();
//		//domain constraints
//		constraints.addAll( rddl_state._alActionPreconditions ); constraints.addAll( rddl_state._alStateInvariants );
//
//		constraints.stream().forEach( new Consumer< BOOL_EXPR >() {
//			@Override
//			public void accept(BOOL_EXPR e) {
//				// COMMENT SHOULD BE UNCOMMENTED NOTE CHANGED BY HARISH.
//				//System.out.println( "Translating Constraint " + e );
//				final EXPR non_stationary_e = e.substitute( Collections.EMPTY_MAP, constants, objects)
//						.addTerm(TIME_PREDICATE, constants, objects )
//						.addTerm(future_PREDICATE, constants, objects);
//
//				TIME_TERMS.stream().forEach( new Consumer< LCONST >() {
//					@Override
//					public void accept(LCONST time_term ) {
//						future_TERMS.parallelStream().forEach( new Consumer<LCONST>() {
//							@Override
//							public void accept(LCONST future_term ) {
//								//CONN_EXPR
//								//This has to be serialized
//
//								final EXPR this_tf = non_stationary_e
//										.substitute( Collections.singletonMap( TIME_PREDICATE, time_term ), constants, objects )
//										.substitute( Collections.singletonMap( future_PREDICATE, future_term ), constants, objects );
//								int index_a = constraints.lastIndexOf(e);
//								//Serialization starts
//								String folderName = "./files/emergency_domain/serialize_output/translateConstraints/";
//								exprSerialize(this_tf, index_a +"_" +future_term.toString()+"_"+time_term.toString(), folderName);
//
//								synchronized( grb_model ){
//									try {
//										//I need to store this somehow
//										long s1 = System.currentTimeMillis();
//										//This has to be serialized
//										GRBVar constrained_var = this_tf.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//										//exprSerialize(constrained_var, future_term.toString()+"_"+time_term.toString(), folderName);
//
//
//										//										System.out.println(this_tf);
//										String nam = RDDL.EXPR.getGRBName(this_tf);
//										GRBConstr this_constr = grb_model.addConstr( constrained_var, GRB.EQUAL, 1, nam );
//										long s2 = System.currentTimeMillis();
//										long s = s2-s1;
//										//System.out.println("Time for GetGRBCOnstr:" + s);
//
//										saved_expr.add( this_tf );
//										saved_constr.add(this_constr);
//										//saved_vars.add( constrained_var );
//									} catch (GRBException e) {
//										e.printStackTrace();
//										System.exit(1);
//									}
//								}
//							}
//						});
//					}
//				});
//			}
//		});
//
//		System.out.println("Checking Highsight-Method");
//		//hindishgt constraint
//		translategetHindSightConstraintExpr(grb_model);
//
//		grb_model.setObjective(old_obj);
//		grb_model.update();
//
//	}
//
//	protected void translategetHindSightConstraintExpr(final GRBModel grb_model) throws Exception {
//		GRBExpr old_obj = grb_model.getObjective();
//		getHindSightConstraintExpr(hindsight_method).parallelStream().forEach( new Consumer< BOOL_EXPR >() {
//
//			@Override
//			public void accept( BOOL_EXPR t) {
//				synchronized( grb_model ){
//					GRBVar gvar = t.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//					try {
//						GRBConstr this_constr = grb_model.addConstr( gvar, GRB.EQUAL, 1, RDDL.EXPR.getGRBName(t) );
//						saved_expr.add( t ); // saved_vars.add( gvar );
//						saved_constr.add(this_constr);
//					} catch (GRBException e) {
//						e.printStackTrace();
//						System.exit(1);
//					}
//				}
//			}
//		});
//
//		grb_model.setObjective(old_obj);
//		grb_model.update();
//
//
//	}
//
//	@Override
//	protected void TranslateInit( final String domain_file, final String inst_file,
//			final int lookahead , final double timeout, final StateViz viz ) throws Exception, GRBException {
//		System.out.println("------- This is TranslateInit (Translate.java)");
//		this.viz = viz;
//		TIME_LIMIT_MINS = timeout;
//		//This Function Initializes the RDDL Domain Name and Instance Name.
//		initializeRDDL(domain_file, inst_file);
//
//		this.lookahead = lookahead;
//
//		objects = new HashMap<>( rddl_instance._hmObjects );
//
//		if( rddl_nonfluents != null && rddl_nonfluents._hmObjects != null ){
//			objects.putAll( rddl_nonfluents._hmObjects );
//		}
//
//		getConstants( );
//
//		if(isSerializable) {
//			for( Entry<PVAR_NAME,PVARIABLE_DEF> entry : rddl_state._hmPVariables.entrySet() ){
//
//				final TYPE_NAME rddl_type = entry.getValue()._typeRange;
//				final char grb_type = rddl_type.equals( TYPE_NAME.BOOL_TYPE ) ? GRB.BINARY :
//					rddl_type.equals( TYPE_NAME.INT_TYPE ) ? GRB.INTEGER : GRB.CONTINUOUS;
//				type_map.put( entry.getKey(), grb_type );
//			}
//			exprSerialize(type_map, "type_map.ser" , "./files/emergency_domain/serialize_output/type_map/");
//		}else {
//			String filePath = "./files/emergency_domain/serialize_output/type_map/type_map.ser";
//			type_map = (HashMap<PVAR_NAME, Character>) deserializeObjects(new File(filePath));
//		}
//
//		//This is changed by HARISH.
//		//System.out.println("----------- Types ---------- ");
//		//type_map.forEach( (a,b) -> System.out.println(a + " " + b) );
//		translate_time = new Timer();
//		translate_time.PauseTimer();
//		System.out.println("---------Initializing Translate is completed!!!------------");
//	}
//
//
//	protected Object deserializeObjects(File file) {
//		Object obj = null;
//		try {
//			FileInputStream fileIn = new FileInputStream(file);
//			ObjectInputStream in = new ObjectInputStream(fileIn);
//			obj = (Object) in.readObject();
//			in.close();
//			fileIn.close();
//		} catch (IOException i) {
//			i.printStackTrace();
//
//		} catch (ClassNotFoundException c) {
//			System.out.println("Employee class not found");
//			c.printStackTrace();
//		}
//		//DeSerialization ends
//		return obj;
//	}
//
//
//	@Override
//	protected void addAllVariables( ) {
//		System.out.println("-----This is addAllVaribles (Overrided)----");
//		HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> src = new HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>();
//		src.putAll( rddl_state_vars ); src.putAll( rddl_action_vars ); src.putAll( rddl_interm_vars ); src.putAll( rddl_observ_vars );
//
//		src.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>> >() {
//			@Override
//			public void accept(PVAR_NAME pvar, ArrayList<ArrayList<LCONST>> u) {
//				u.parallelStream().forEach( new Consumer<ArrayList<LCONST>>() {
//					@Override
//					public void accept(ArrayList<LCONST> terms) {
//						EXPR pvar_expr = new PVAR_EXPR(pvar._sPVarName, terms )
//							.addTerm(TIME_PREDICATE, constants, objects)
//							.addTerm( future_PREDICATE, constants, objects );
//
//						TIME_TERMS.parallelStream().forEach( new Consumer<LCONST>() {
//							@Override
//							public void accept(LCONST time_term ) {
//
//								EXPR this_t = pvar_expr.substitute( Collections.singletonMap( TIME_PREDICATE, time_term),
//										constants, objects);
//
//								future_TERMS.parallelStream().forEach( new Consumer< LCONST >() {
//									@Override
//									public void accept(LCONST future_term) {
//										EXPR this_tf =
//												this_t.substitute( Collections.singletonMap( future_PREDICATE, future_term ), constants, objects );
//
//										//Src id, U id , time id
//
//
//										synchronized( static_grb_model ){
//
//											GRBVar gvar = this_tf.getGRBConstr( GRB.EQUAL, static_grb_model, constants, objects, type_map);
//
//
//											//just remember this is commented by HARISH
//
////											try {
////												System.out.println("Adding var " + gvar.get(StringAttr.VarName) + " " + this_tf );
////											} catch (GRBException e) {
////												e.printStackTrace();
////												System.exit(1);
////											}
//											saved_expr.add( this_tf );
//										}
//									}
//								});
//
//							}
//						});
//					}
//				});
//			}
//		});
//	}
//
//
//	@Override
//	protected void translateConstraintsDeSerialize(final GRBModel grb_model, String folderName) throws Exception{
//		//Deserialization begins
//		EXPR this_tf = null;
//		File folder = new File(folderName);
//		File [] listOfFiles = folder.listFiles(new FileFilter() {
//		    @Override
//		    public boolean accept(File file) {
//		        return !file.isHidden();
//		    }
//		});
//		for(File file: listOfFiles) {
//			//DeSerialization starts
//			try {
//				FileInputStream fileIn = new FileInputStream(file);
//				ObjectInputStream in = new ObjectInputStream(fileIn);
//				this_tf = (EXPR) in.readObject();
//				in.close();
//				fileIn.close();
//			} catch (IOException i) {
//				i.printStackTrace();
//
//			} catch (ClassNotFoundException c) {
//				System.out.println("Employee class not found");
//				c.printStackTrace();
//				return;
//			}
//			//DeSerialization ends
//
//			synchronized( grb_model ){
//				try {
//					//I need to store this somehow
//
//					long s1 = System.currentTimeMillis();
//					//This has to be serialized
//					GRBVar constrained_var = this_tf.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//					//					System.out.println(this_tf);
//					String nam = RDDL.EXPR.getGRBName(this_tf);
//					//String nam = "Temp";
//					GRBConstr this_constr = grb_model.addConstr( constrained_var, GRB.EQUAL, 1, nam );
//					long s2 = System.currentTimeMillis();
//					long s = s2-s1;
//					//System.out.println("Time for GetGRBCOnstr:" + s);
//
//					//saved_expr.add( this_tf );
//					//saved_constr.add(this_constr);
//					//saved_vars.add( constrained_var );
//				} catch (GRBException e) {
//					e.printStackTrace();
//					System.exit(1);
//				}
//			}}
//
//
//		translategetHindSightConstraintExpr(grb_model);
//
//
//
//
//	}
//
//	protected void exprSerialize(Object this_tf, String fileName, String folderName) {
//		System.out.println(" --- Serializing to  " + fileName);
//		try {
//			FileOutputStream fos = new FileOutputStream(folderName+fileName);
//			ObjectOutputStream oos = new ObjectOutputStream(fos);
//			oos.writeObject(this_tf);
//			oos.close();
//			fos.close();
//		} catch (FileNotFoundException e1) {
//			e1.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}
//
//
//
//
//
//	protected ArrayList<BOOL_EXPR> getHindSightConstraintExpr( HINDSIGHT_STRATEGY hindsight_method ) {
//		ArrayList<BOOL_EXPR> ret = new ArrayList<BOOL_EXPR>();
//		System.out.println("-----Getting HindSightConstraintExpr (HOPTranslate.java)-------");
//		//the only way to keep this working correctly with expanding enum HINDSIGHT_STRATEGY
//		switch( hindsight_method ){
//		case ALL_ACTIONS :
//			rddl_action_vars.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>> () {
//				public void accept( PVAR_NAME pvar , ArrayList<ArrayList<LCONST>> u) {
//					u.forEach( new Consumer< ArrayList<LCONST>>() {
//						@Override
//						public void accept(ArrayList<LCONST> terms) {
//							PVAR_EXPR pvar_expr = new PVAR_EXPR( pvar._sPVarName, terms );
//							EXPR with_tf = pvar_expr.addTerm(TIME_PREDICATE, constants, objects)
//									.addTerm(future_PREDICATE, constants, objects);
//
//							for( final LCONST time : TIME_TERMS ){
//								EXPR this_t = with_tf.substitute( Collections.singletonMap(TIME_PREDICATE, time ), constants, objects);
//								EXPR ref_expr = this_t.substitute( Collections.singletonMap( future_PREDICATE, future_TERMS.get(0) ), constants, objects);
//
//								for( final LCONST future : future_TERMS ){
//									EXPR addedd = this_t.substitute( Collections.singletonMap( future_PREDICATE, future), constants, objects);
//									ret.add( new COMP_EXPR( ref_expr, addedd, COMP_EXPR.EQUAL ) );
//								}
//
//							}
//						}
//					});
//				};
//			});
//			break;
//		case CONSENSUS :
//			//nothing to add
//			break;
//		case ROOT :
//			System.out.println("-----> This is for ROOT CASE");
//			rddl_action_vars.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>> () {
//				public void accept( PVAR_NAME pvar , ArrayList<ArrayList<LCONST>> u) {
//					u.forEach( new Consumer< ArrayList<LCONST>>() {
//						@Override
//						public void accept(ArrayList<LCONST> terms) {
//							PVAR_EXPR pvar_expr = new PVAR_EXPR( pvar._sPVarName, terms );
//							EXPR with_tf = pvar_expr.addTerm(TIME_PREDICATE, constants, objects)
//									.addTerm(future_PREDICATE, constants, objects);
//
//							EXPR this_t = with_tf.substitute( Collections.singletonMap(TIME_PREDICATE,
//									TIME_TERMS.get(0) ), constants, objects);
//							EXPR ref_expr = this_t.substitute( Collections.singletonMap( future_PREDICATE,
//									future_TERMS.get(0) ), constants, objects);
//
//							for( final LCONST future : future_TERMS ){
//								EXPR addedd = this_t.substitute( Collections.singletonMap( future_PREDICATE, future), constants, objects);
//								ret.add( new COMP_EXPR( ref_expr, addedd, COMP_EXPR.EQUAL ) );
//							}
//
//						}
//					});
//				};
//			});
//			break;
//		default : try{
//			throw new Exception("Unknown hindsight strategy " + hindsight_method );
//		}	catch( Exception exc ){
//			exc.printStackTrace();
//			System.exit(1);
//		}
//		}
//
//		System.out.println("----This is end of HindSightConstraintExpr -------");
//		return ret;
//	}
//
//
//
//
//
//
//
//
//	@Override
//	protected void translateReward( final GRBModel grb_model ) throws Exception {
//		System.out.println("---- This is translate Reward (Overrided) --------");
//		grb_model.setObjective( new GRBLinExpr() );
//		grb_model.update();
//
//		final EXPR stationary = rddl_state._reward;
//		final EXPR stationary_clear = stationary.substitute( Collections.EMPTY_MAP, constants, objects);
//		final EXPR non_stationary = stationary_clear.addTerm( TIME_PREDICATE , constants, objects )
//				.addTerm( future_PREDICATE, constants, objects);
//
//		GRBLinExpr all_sum = new GRBLinExpr();
//		//This piece of code is changed by HARISH
//		System.out.println(non_stationary);
//
//		future_TERMS.parallelStream().forEach( new Consumer<LCONST>() {
//			@Override
//			public void accept(LCONST future_term) {
//				TIME_TERMS.parallelStream().forEach( new Consumer<LCONST>() {
//					@Override
//					public void accept(LCONST time_term) {
//						//OPER_EXPR
//						final EXPR subs_tf = non_stationary.substitute( Collections.singletonMap( TIME_PREDICATE, time_term ),
//								constants, objects)
//								.substitute( Collections.singletonMap( future_PREDICATE, future_term ),constants, objects);
//
//						System.out.println( subs_tf );//"Reward_" + time_term + "_" + future_term );
//
//						synchronized( grb_model ){
//							GRBVar this_future_var = subs_tf.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//							saved_expr.add( subs_tf );
//							//System.out.println(saved_expr);
//							//							saved_vars.add( this_future_var );
//							//IDEA : weightby probability of trajecotry of futures
//							//what is the probability of a determinization
//							all_sum.addTerm( 1.0d/num_futures, this_future_var );
//							//System.out.println(all_sum);
//						}
//					}
//				});
//			}
//		});
//
//		grb_model.setObjective(all_sum);
//		grb_model.update();
//
//	}
//
//
//
//
//
//	@Override
//	protected void translateCPTs(HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> subs,
//			final GRBModel grb_model) throws GRBException {
//		System.out.println("This is TranslateCPT where the futures are generated and adding constraints");
//		GRBExpr old_obj = grb_model.getObjective();
//
//		ArrayList<HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>> src
//		= new ArrayList< HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> >();
//		src.add( rddl_state_vars ); src.add( rddl_interm_vars ); src.add( rddl_observ_vars );
//
//		final RandomDataGenerator rng = this.rand;
//		final int numFutures = this.num_futures;
//		final int length = this.lookahead;
//
//		EmergencyDomainDataReelElement currentElem = new EmergencyDomainDataReelElement(subs);
//		long startTime = System.currentTimeMillis();
//
//		ArrayList<EmergencyDomainDataReelElement>[] futures
//		= reel.getFutures(currentElem, rng, numFutures, length, reel.getTrainingFoldIdx() );
//		long endTime = System.currentTimeMillis();
//		System.out.println("Total execution time for getting Futures: " + (endTime - startTime) );
//		startTime = System.currentTimeMillis();
//		ArrayList<Pair<EXPR, EXPR>> futuresExpressions
//		= reel.to_RDDL_EXPR_constraints_sans_init(futures, future_PREDICATE,
//				future_TERMS, TIME_PREDICATE, TIME_TERMS, constants, objects);
//
//		try {
//			FileWriter fw3 = new FileWriter("./harish_folder/futures.txt");
//			FileWriter fw4 = new FileWriter("./harish_folder/futures_Expressions.txt");
//			fw3.write(futures.toString());
//			fw4.write(futuresExpressions.toString());
//			fw3.flush();
//			fw3.close();
//			fw4.close();
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		System.out.println("checking");
//
//
//		for( Pair<EXPR,EXPR> pairFuture : futuresExpressions ){
//			final EXPR lhs_future = pairFuture._o1;
//			final EXPR rhs_future = pairFuture._o2;
//			synchronized ( grb_model ) {
//				GRBVar lhs_var = lhs_future.getGRBConstr(
//						GRB.EQUAL, grb_model, constants, objects, type_map);
//				GRBVar rhs_var = rhs_future.getGRBConstr(
//						GRB.EQUAL, grb_model, constants, objects, type_map);
//				try {
//
//					//System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
//					final String nam = RDDL.EXPR.getGRBName(lhs_future)+"="+RDDL.EXPR.getGRBName(rhs_future);
//					//					System.out.println(nam);;
//
//					GRBConstr this_constr = grb_model.addConstr( lhs_var, GRB.EQUAL, rhs_var, nam );
//					to_remove_constr.add( this_constr );
//					to_remove_expr.add(lhs_future);
//					to_remove_expr.add(rhs_future);
//					//					System.out.println(this_constr.get(StringAttr.ConstrName));
//				} catch (GRBException e) {
//					System.out.println( e.getErrorCode() + " " +e.getMessage() );
//					e.printStackTrace();
//					System.exit(1);
//				}
//			}
//		}
//
//		endTime = System.currentTimeMillis();
//		System.out.println("Total execution time for translating futures: " + (endTime - startTime) );
//
//		grb_model.setObjective(old_obj);
//		grb_model.update();
//	}
//
//
//
//
//
//	public static double getFirstResponse(State s) throws EvalException {
//		return ((Number) s.getPVariableAssign(firstResponsePvarName , EmergencyDomainDataReelElement.emptySubstitution)).doubleValue();
//	}
//
//	public static double getFullResponse(State s) throws EvalException {
//		return ((Number) s.getPVariableAssign(fullResponsePvarName, EmergencyDomainDataReelElement.emptySubstitution)).doubleValue();
//	}
//
//	public static boolean getOverwhelm(State s) throws EvalException {
//		return (boolean) s.getPVariableAssign(overwhelmPvarName, EmergencyDomainDataReelElement.emptySubstitution);
//	}
//
//
//
//
//
//
//
//	public static void main(String[] args) throws Exception {
//		System.out.println("this is were we are printing arguments");
//		//System.out.println(args.length);
//		//System.out.println( Arrays.toString( args ) );
//
//		fw = new FileWriter(new File( Arrays.asList(args).get( Arrays.asList(args).size()-2 ) ) );
//		fw1 = new FileWriter(new File( Arrays.asList(args).get( Arrays.asList(args).size()-1 ) ) );
//
//
//		System.out.println(Arrays.asList( args ).subList(0, args.length-2));
//		EmergencyDomainFastHOPTranslate planner = new EmergencyDomainFastHOPTranslate(
//				Arrays.asList( args ).subList(0, args.length-2-2) );
//		System.out.println( planner.evaluatePlanner(
//				Integer.parseInt( args[args.length-2-2] ),
//				null, //new EmergencyDomainStateViz(1300,30,1500,80),
//				Boolean.parseBoolean( args[ args.length-1-2 ] ) ) );
//	}
//
//
//
//
//
//}
