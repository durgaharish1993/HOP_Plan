package rddl.det.comMip;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import gurobi.GRB;
import gurobi.GRB.DoubleAttr;
import gurobi.GRB.DoubleParam;
import gurobi.GRB.IntAttr;
import gurobi.GRB.IntParam;
import gurobi.GRB.StringAttr;
import gurobi.GRB.StringParam;
import gurobi.GRBConstr;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBExpr;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.rosuda.JRI.Rengine;
import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.BOOL_CONST_EXPR;
import rddl.RDDL.BOOL_EXPR;
import rddl.RDDL.CPF_DEF;
import rddl.RDDL.DOMAIN;
import rddl.RDDL.EXPR;
import rddl.RDDL.INSTANCE;
import rddl.RDDL.INT_CONST_EXPR;
import rddl.RDDL.LCONST;
import rddl.RDDL.LTERM;
import rddl.RDDL.LVAR;
import rddl.RDDL.NONFLUENTS;
import rddl.RDDL.OBJECTS_DEF;
import rddl.RDDL.OBJECT_VAL;
import rddl.RDDL.PVARIABLE_DEF;
import rddl.RDDL.PVAR_EXPR;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.RDDL.PVAR_NAME;
import rddl.RDDL.REAL_CONST_EXPR;
import rddl.RDDL.TYPE_NAME;
import rddl.State;
import rddl.parser.ParseException;
import rddl.policy.Policy;
import rddl.viz.StateViz;
import util.Pair;
import util.Timer;
import java.util.Random;


public class Translate extends Policy { //  extends rddl.policy.Policy {

	//This is added by Harish
	protected RandomDataGenerator rand;
	protected boolean SHOW_LEVEL_1 = true;
	protected boolean SHOW_LEVEL_2 = false;


	private static final int GRB_INFUNBDINFO = 1;
	private static final int GRB_DUALREDUCTIONS = 0;
	//The Gap between the 
	private static final double GRB_MIPGAP = 0.01;//0.5; //0.01;
	private static final double GRB_HEURISTIC = 0.2;
	private static final int GRB_IISMethod = 1;
	
	protected static final LVAR TIME_PREDICATE = new LVAR( "?time" );
	private static final TYPE_NAME TIME_TYPE = new TYPE_NAME( "time" );
	protected static final boolean OUTPUT_LP_FILE = false;
	private static final boolean GRB_LOGGING_ON = false;
	protected static final boolean RECOVER_INFEASIBLE = true;
	protected double TIME_LIMIT_MINS = 10; 
	
	protected RDDL rddl_obj;
	protected int lookahead;
	protected State rddl_state;
	protected DOMAIN rddl_domain;
	protected INSTANCE rddl_instance;
	protected NONFLUENTS rddl_nonfluents;
	protected String instance_name;
	protected String domain_name;
	private String GRB_log;
	protected HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> rddl_state_vars;
	protected HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> rddl_action_vars;
	protected HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> rddl_observ_vars;
	protected HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> rddl_interm_vars;
	
	protected List<String> string_state_vars;
	protected List<String> string_action_vars;
	protected List<String> string_observ_vars;
	protected List<String> string_interm_vars;
	protected GRBEnv grb_env;
	
	protected GRBModel static_grb_model = null;
	
	private HashMap<PVAR_NAME, TYPE_NAME> pred_type = new HashMap<>();
	protected ArrayList<ArrayList<PVAR_INST_DEF>> ret_list = new ArrayList<ArrayList<PVAR_INST_DEF>>();
	
//	private HashMap<String, GRBVar> grb_string_map  
//		= new HashMap<String, GRBVar>();
//	private HashMap<String, Pair> rddl_string_map 
//		= new HashMap<String,Pair>();
	
	private String OUTPUT_FILE = "model.lp";
	protected HashMap<TYPE_NAME, OBJECTS_DEF> objects;
	protected Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants = new HashMap<>();
	protected ArrayList< LCONST > TIME_TERMS = new ArrayList<>();
	protected HashMap<PVAR_NAME,  Character> type_map = new HashMap<>();
	
	//these are saved between invocations of getActions()
	//these are never removed
	protected List<EXPR> saved_expr = new ArrayList<>();
	protected List<GRBConstr> saved_constr = new ArrayList<>();



	//This is for getting training data CONSENSUS and ROOT ACTION.

	protected List<EXPR> root_policy_expr = new ArrayList<>();
	protected List<GRBConstr> root_policy_constr = new ArrayList<>();








	//These are added by Harish.

	//This is like a memory which stores the states.
	protected ArrayList<ArrayList<HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>>>> buffer_state = new ArrayList<>();


	//This is like a memory which stores the actions.
	protected ArrayList<ArrayList<ArrayList<PVAR_INST_DEF>>> buffer_action = new ArrayList<>();
	protected ArrayList<ArrayList<Double>> buffer_reward = new ArrayList<>();


	//This stores the previous memory of state action, reward.

	protected ArrayList<ArrayList<HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>>>> pre_buffer_state = new ArrayList<>();
	protected ArrayList<ArrayList<ArrayList<PVAR_INST_DEF>>> pre_buffer_action = new ArrayList<>();
	protected ArrayList<ArrayList<Double>> pre_buffer_reward = new ArrayList<>();
	protected Integer number_of_iterations = 30;



	//This is the range of actions to be taken, This will get initalized when the domain gets initalized and will get updated for picking random Actions.
	protected HashMap<PVAR_NAME,ArrayList> value_range = new HashMap<>();
	protected HashMap<PVAR_NAME,ArrayList<TYPE_NAME>> object_type_name  = new HashMap<>();
	protected HashMap<TYPE_NAME,LCONST> object_val_mapping = new HashMap<>();
	protected Double rejection_prob = 0.1;


	//This stores the Expression which are not PWL.

	protected List<EXPR> not_pwl_expr = new ArrayList<>();
	protected HashMap<PVAR_NAME,Object> rddl_state_default = new HashMap<>();


	protected HashMap<PVAR_NAME,Object> variables_names = new HashMap<>();

	protected HashMap<PVAR_NAME,EXPR> replace_cpf_pwl = new HashMap<>();


	//Timers
	protected Double running_R_api = 0.0;


















//	protected List<GRBVar> saved_vars = new ArrayList<GRBVar>();
	//saved vars removed - any saved expr will save the corresponding grbvar
	
	//these are removed between invocations of getActions()
	protected List<EXPR> to_remove_expr = new ArrayList<RDDL.EXPR>();
	protected List<GRBConstr> to_remove_constr = new ArrayList<>();
	protected ArrayList<Double> objectiveValues = new ArrayList<Double>();
	
	//even though extraneous exprs/vars may be in the model
	//these are removed from the MIP 
	//recursively defined vars/exprs would not affect opt
//	protected List<GRBConstr> to_remove_constr = new ArrayList<GRBConstr>();
//	protected List<EXPR> to_remove_expr = new ArrayList<>();
	
	protected Timer translate_time;
	protected StateViz viz;
	
	
	private static final ArrayList<ArrayList<LCONST>> vehicleSubs;
	private static final int NUMVEHICLES=5;
	
	static{
		vehicleSubs = new ArrayList<ArrayList<LCONST>>();
		for( int i = 1; i<= NUMVEHICLES; ++i ){
			vehicleSubs.add( new ArrayList<LCONST>( Arrays.<LCONST>asList( new OBJECT_VAL[]{new OBJECT_VAL("u" + i)} ) ) );
		} 
	}

	
	//pseudoconstructor - only one constructor allowed for rddl client polici interface 
	protected void TranslateInit( final String domain_file, final String inst_file,
			final int lookahead , final double timeout, final StateViz viz, final RDDL rddl_object, State s ) throws Exception, GRBException {
		System.out.println("------- This is TranslateInit (Translate.java)");
		this.viz = viz;
		TIME_LIMIT_MINS = timeout;
		//This Function Initializes the RDDL Domain Name and Instance Name. 
		//initializeRDDL(domain_file, inst_file);
		initializeCompetitionRDDL(rddl_object,inst_file,s);
		this.lookahead = lookahead;
		
		objects = new HashMap<>( rddl_instance._hmObjects );
		
		if( rddl_nonfluents != null && rddl_nonfluents._hmObjects != null ){
			objects.putAll( rddl_nonfluents._hmObjects );
		}
		
		getConstants( );
		
		for( Entry<PVAR_NAME,PVARIABLE_DEF> entry : rddl_state._hmPVariables.entrySet() ){
			
			final TYPE_NAME rddl_type = entry.getValue()._typeRange;
			final char grb_type = rddl_type.equals( TYPE_NAME.BOOL_TYPE ) ? GRB.BINARY :
				rddl_type.equals( TYPE_NAME.INT_TYPE ) ? GRB.INTEGER : GRB.CONTINUOUS;



			//Setting Action Variables ranges.
			//PVAR_NAME temp_pvar = new PVAR_NAME(entry.getKey()._sPVarName);



			object_type_name.put(entry.getKey(), entry.getValue()._alParamTypes);
			//object_val_mapping.put(object_type_name.get(entry.getKey()),  rddl_state._hmObject2Consts.get(object_type_name.get(entry.getKey()))   );




			if(rddl_type.equals(TYPE_NAME.REAL_TYPE)){

				ArrayList<Double> temp_dec_range =new ArrayList<Double>(){{
																				add(0.0);
																				add(50.0); }};
				value_range.put(entry.getKey(),temp_dec_range);
			}



			if(rddl_type.equals(TYPE_NAME.BOOL_TYPE)){

				ArrayList<Boolean> temp_bool_range = new ArrayList<Boolean>(){{  add(new Boolean("true"));
																				 add(new Boolean("false"));	}};



				value_range.put(entry.getKey(),temp_bool_range);


			}

			type_map.put( entry.getKey(), grb_type );
			
		}






		//This is Added by Harish.
		//action_range




		//This is changed by HARISH.
		//System.out.println("----------- Types ---------- ");
		//type_map.forEach( (a,b) -> System.out.println(a + " " + b) );

		translate_time = new Timer();
		translate_time.PauseTimer();
		System.out.println("---------Initializing Translate is completed!!!------------");
		
	
	
	
	}
	
	protected void addExtraPredicates() {
		removeExtraPredicates();
		for( int t = 0 ; t < lookahead; ++t ){
			//[$time0,$time1,$time2,$time3]
			TIME_TERMS.add( new RDDL.OBJECT_VAL( "time" + t ) );
		}
		objects.put( TIME_TYPE,  new OBJECTS_DEF(  TIME_TYPE._STypeName, TIME_TERMS ) );		
	}


	//These are the functions for competition
	@Override
	public void initializeState(State s){


		rddl_state = s;




	}


	@Override
	public State getStateObject(){

		return this.rddl_state;



	}




	protected void addAllVariables() {
		
		BOOL_CONST_EXPR true_expr = new BOOL_CONST_EXPR(true);
		BOOL_CONST_EXPR false_expr = new BOOL_CONST_EXPR(false);
		
		true_expr.getGRBConstr( GRB.EQUAL, static_grb_model, constants, objects, type_map );
		false_expr.getGRBConstr( GRB.EQUAL, static_grb_model, constants, objects, type_map );
		
		saved_expr.add(true_expr);
		saved_expr.add(false_expr);
		
		//canonical vars for pvar exprs
		HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> src = new HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>();
		src.putAll( rddl_state_vars ); src.putAll( rddl_action_vars ); src.putAll( rddl_interm_vars ); src.putAll( rddl_observ_vars );
		
		src.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>> >() {
			@Override
			public void accept(PVAR_NAME pvar, ArrayList<ArrayList<LCONST>> u) {
				u.parallelStream().forEach( new Consumer<ArrayList<LCONST>>() {
					@Override
					public void accept(ArrayList<LCONST> terms) {
						EXPR pvar_expr = new PVAR_EXPR(pvar._sPVarName, terms )
							.addTerm(TIME_PREDICATE, constants, objects);

						TIME_TERMS.parallelStream().forEach( new Consumer<LCONST>() {
							@Override
							public void accept(LCONST time_term ) {
								EXPR this_t = pvar_expr.substitute( Collections.singletonMap( TIME_PREDICATE, time_term), constants, objects);
								synchronized( static_grb_model ){
									GRBVar new_var = this_t.getGRBConstr( GRB.EQUAL, static_grb_model, constants, objects, type_map);
//									saved_vars.add( new_var );

									//Just Remeber Commented this by HARISH.

//									try {
//										//System.out.println("Adding var " + new_var.get(StringAttr.VarName) + " " + this_t );
//									} catch (GRBException e) {
//										e.printStackTrace();
//										////System.exit(1);
//									}
//									
									
									
									saved_expr.add( this_t );
								}
							}
						});
					}
				});
			}
		});
		
	}

	public Pair<ArrayList<Map<EXPR, Double>>,Integer> doPlanInitState( ) throws Exception{
		return doPlan( getSubsWithDefaults(rddl_state), RECOVER_INFEASIBLE ); 
	}

	public Pair<ArrayList<Map< EXPR, Double >>,Integer> doPlan(HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> subs ,
															   final boolean recover ) throws Exception{
		//deterministic : model is already prepared except for initial state
		ArrayList<Map< EXPR, Double >> ret_obj = new ArrayList<Map< EXPR, Double >>();
//		final GRBModel dynamic_grb_model = new GRBModel( static_grb_model );
		translate_time.ResumeTimer();
		System.out.println("--------------Initial State-------------");
		translateInitialState( static_grb_model, subs );
		translate_time.PauseTimer();
		
		int exit_code = -1;
		try{
			exit_code = goOptimize( static_grb_model );
		}catch( GRBException exc ){
			int error_code = exc.getErrorCode();
			System.out.println("Error code : " + error_code );
			if( recover ){ //error_code == GRB.ERROR_OUT_OF_MEMORY && recover ){
				System.out.println("cleaning up and retrying");
				handleOOM( static_grb_model );
				return doPlan( subs, RECOVER_INFEASIBLE );
			}else{
				throw exc;
			}
		}finally {
			System.out.println("Exit code : " + exit_code );
		}

		Map< EXPR, Double > ret = outputResults( static_grb_model );
		ret_obj.add(ret);

		if( OUTPUT_LP_FILE ) {
			outputLPFile( static_grb_model );
		}
		modelSummary( static_grb_model );		
		
		return new Pair<>(ret_obj,exit_code);
	}
	
	protected void handleOOM(GRBModel grb_model) throws GRBException {
		System.out.println("JVM free memory : " + Runtime.getRuntime().freeMemory() + " / " + 
				Runtime.getRuntime().maxMemory() + " = " + ( ((double)Runtime.getRuntime().freeMemory()) / Runtime.getRuntime().maxMemory()) );
		System.out.println("round end / out of memory detected; trying cleanup");
		
		cleanUp(grb_model);
		grb_model.getEnv().dispose();
		grb_model.dispose();

		RDDL.EXPR.cleanUpGRB();
		System.gc();
//		try {
//			Thread.sleep(1*1000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//			////System.exit(1);
//		}//8 second stall
		
		firstTimeModel( );
	}

	protected void removeExtraPredicates() {
		TIME_TERMS.clear();
		objects.remove( TIME_TYPE );
	}

//	private void resetGRB(GRBModel grb_model) throws GRBException {
//		grb_model.dispose();
//				
//	}

//	public Map< EXPR, Double >  doPlan( final ArrayList<PVAR_INST_DEF> initState, 
//			final boolean recover ) throws Exception{
//		if( grb_model == null ){
//			firstTimeModel();
//		}
//
////		System.out.println( "Names : " );
////		RDDL.EXPR.name_map.forEach( (a,b) -> System.out.println( a + " " + b ) );
////		grb_model.set( GRB.IntParam.SolutionLimit, 1 );
////		prepareModel( initState ); model already prepared in constructor
//		
//		translate_time.ResumeTimer();
//		System.out.println("--------------Initial State-------------");
//		translateInitialState( initState );
//		translate_time.PauseTimer();	
//		
//		try{
//			goOptimize();
//		}catch( GRBException exc ){
//			int error_code = exc.getErrorCode();
//			if( error_code == GRB.ERROR_OUT_OF_MEMORY && recover ){
//				handleOOM();
//				return doPlan( initState, false );
//			}else{
//				throw exc;
//			}
//		}
//		
//		Map< EXPR, Double > ret = outputResults();
//		if( OUTPUT_LP_FILE ) {
//			outputLPFile( );
//		}
//		
//		modelSummary();		
//		cleanUp();
//		return ret;
//	}
	
	@Override
	public void sessionEnd(double total_reward) {
		super.sessionEnd(total_reward);
		if( viz != null ){
			viz.close();
		}
		
		try {
			cleanUp(static_grb_model);
			static_grb_model.getEnv().dispose();
			static_grb_model.dispose();
			RDDL.EXPR.cleanUpGRB();
			System.gc();
			static_grb_model = null;
			grb_env = null;
		} catch (GRBException e) {
			e.printStackTrace();
			//////System.exit(1);
		} 
	}

	protected void modelSummary(final GRBModel grb_model) throws GRBException {
		System.out.println( "Status : "+ grb_model.get( IntAttr.Status ) + "(Optimal/Inf/Unb: " + GRB.OPTIMAL + ", " + GRB.INFEASIBLE +", " + GRB.UNBOUNDED + ")" );
		System.out.println( "Number of solutions found : " + grb_model.get( IntAttr.SolCount ) );
		System.out.println( "Number of simplex iterations performed in most recent optimization : " + grb_model.get( DoubleAttr.IterCount ) );	
		System.out.println( "Number of branch-and-cut nodes explored in most recent optimization : " + grb_model.get( DoubleAttr.NodeCount ) );

		//System.out.println("Maximum (unscaled) primal constraint error : " + grb_model.get( DoubleAttr.ConstrResidual ) );	

//		System.out.println("Sum of (unscaled) primal constraint errors : " + grb_model.get( DoubleAttr.ConstrResidualSum ) );
//		System.out.println("Maximum (unscaled) dual constraint error : " + grb_model.get( DoubleAttr.DualResidual ) ) ;
//		System.out.println("Sum of (unscaled) dual constraint errors : " + grb_model.get( DoubleAttr.DualResidualSum ) );

		System.out.println( "#Variables : "+ grb_model.get( IntAttr.NumVars ) );
		System.out.println( "#Integer variables : "+ grb_model.get( IntAttr.NumIntVars ) );
		System.out.println( "#Binary variables : "+ grb_model.get( IntAttr.NumBinVars ) );
		System.out.println( "#Constraints : "+ grb_model.get( IntAttr.NumConstrs ) );
		System.out.println( "#NumPWLObjVars : "+ grb_model.get( IntAttr.NumPWLObjVars ) );
				
		System.out.println("#State Vars : " + string_state_vars.size() );
		System.out.println("#Action Vars : " + string_action_vars.size() );
		System.out.println("Optimization Runtime(mins) : " + grb_model.get( DoubleAttr.Runtime ) );
		System.out.println("Translation time(mins) : " + translate_time.GetElapsedTimeInMinutes() );
	}

	protected void cleanUp(final GRBModel grb_model) throws GRBException {
//		saved_expr.clear(); saved_vars.clear();
		
//		RDDL.EXPR.cleanUpGRB();
		for( final GRBConstr constr : to_remove_constr ){
			if( !saved_constr.contains(constr) ){
//				System.out.println(constr.toString());
				try{
//					System.out.println("Removing constraint " + );
					constr.get(StringAttr.ConstrName); 
					//get can throw an exception
					
				}catch(GRBException exc){
						System.out.println(exc.getErrorCode());
						exc.printStackTrace();
						//////System.exit(1);
				}
				grb_model.remove( constr );		
				grb_model.update();
				//System.out.println(grb_model.get( IntAttr.NumConstrs ));
				//System.out.println(grb_model.toString());
				
			}
		}
		
		grb_model.get( IntAttr.NumConstrs );
		to_remove_constr.clear();
		
		to_remove_expr.clear();
	}
		
//		ArrayList<EXPR> new_list = new ArrayList<>( to_remove_expr );
//		new_list.removeAll( saved_expr );
//		
//		for( final EXPR expr :  new_list ){
//			if( EXPR.grb_cache.containsKey( expr  ) ){
//				GRBVar lookup = EXPR.grb_cache.get( expr ) ;
////				if( !saved_vars.contains(lookup) ){
//					EXPR.grb_cache.remove( expr );	
//					grb_model.remove( lookup );
//					System.out.println("Cache delete pair : " + expr + " " + lookup );
////				}	
//			}else{
//				try{
//					throw new Exception("Cache cannot find/ delete expr : " + expr );
//				}catch( Exception exc ){
//					exc.printStackTrace();
//					////System.exit(1);
//				}
//			}
//		}

		//
//		ArrayList<GRBVar> another_list = new ArrayList<>( to_remove_vars );
//		another_list.removeAll( saved_vars );
//		
//		for( final GRBVar gvar : another_list ){
//			if( !saved_vars.contains( gvar ) ){
//				grb_model.remove( gvar );	
//			}
//		}
		
//		
//		to_remove_expr.clear();
//		to_remove_vars.clear();
		
//		grb_model.dispose();
//		grb_model = null; 
//		grb_env.dispose();
//		grb_env = null;
//		initializeGRB();
//	}
	
//	private void prepareModel( final GRBModel grb_model,
//			HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> subs ) throws Exception {
//		translate_time.ResumeTimer();
//		prepareModel( grb_model );
//		System.out.println("--------------Initial State-------------");
//		translateInitialState( grb_model, subs );
//		translate_time.PauseTimer();		
//	}
		
	
	protected void prepareModel( ) throws Exception{
		System.out.println("--------------Translating CPTs-------------");
		translateCPTs( null, static_grb_model );
		System.out.println("--------------Translating Constraints-------------");
		translateConstraints( static_grb_model );
		
		System.out.println("--------------Translating Reward-------------");
		translateReward( static_grb_model );
	}

	
	protected void translateConstraintsDeSerialize(final GRBModel grb_model, String folderName) throws Exception{
		
	}
	
	protected Map<EXPR, Double> outputResults(final GRBModel grb_model) throws GRBException {
		HashMap<EXPR, Double> ret = new HashMap< EXPR, Double >();

		try{
			
			if( grb_model.get( IntAttr.SolCount ) > 0 ){
				System.out.println("---------- Interm trajectory ----------");
				for( int time = 0; time < lookahead; ++time ){
					ret.putAll( getAssignments( rddl_interm_vars, time ) ); 
				}
				
				System.out.println("---------- Output trajectory ----------");
				for( int time = 0; time < lookahead; ++time ){
					ret.putAll( getAssignments( rddl_state_vars, time ) );
				}
				
				System.out.println("---------- Output action assignments  ----------");
				for( int time = 0; time < lookahead-1; ++time ){
					ret.putAll( getAssignments( rddl_action_vars, time ) );
				}
				
				System.out.println( "Maximum (unscaled) bound violation : " +  + grb_model.get( DoubleAttr.BoundVio	) );
				System.out.println("Sum of (unscaled) constraint violations : " + grb_model.get( DoubleAttr.ConstrVioSum ) );
				System.out.println("Maximum integrality violation : "+ grb_model.get( DoubleAttr.IntVio ) );
				System.out.println("Sum of integrality violations : " + grb_model.get( DoubleAttr.IntVioSum ) );
				System.out.println("Objective value : " + grb_model.get( DoubleAttr.ObjVal ) );
				
			}else{
				try{
					throw new Exception("No solution found, returning noop");	
				}catch( Exception excp){
					excp.printStackTrace();
				}
				
				ret = null;
			}
			
		}catch( Exception exc ){
			exc.printStackTrace();
			dumpAllAssignments(grb_model);
		}		
		return ret;
	}

	protected int goOptimize(final GRBModel grb_model) throws GRBException {

		grb_model.update();
		System.out.println("Optimizing.............");
		grb_model.optimize();
		int constraint_unsatisfied = 0;

//		//return grb_model.get( IntAttr.Status );
//		if( grb_model.get( IntAttr.Status ) == GRB.INFEASIBLE ){
//			System.out.println("===========================INFEASIBLE CONSTRAINTS STARTS HERE====================================");
//			System.out.println("xxxxxxxx-----Solver says infeasible.-------xxxxxxxx");
//			System.out.println("The Solver says infeasible----------");
//			grb_model.computeIIS();
//
//	        System.out.println("\nThe following constraints cannot be satisfied:");
//	        System.out.println("********************************************************");
//
//	        List<String> src = new ArrayList<>( EXPR.reverse_name_map.keySet() );
//			GRBConstr to_remove_constriant = null;
//	        for (GRBConstr c : grb_model.getConstrs()) {
//
//	          if (c.get(GRB.IntAttr.IISConstr) > 0) {
//
//	          		to_remove_constriant = c;
//
//	        	  	//removed.add(c.get(GRB.StringAttr.ConstrName));
////	        	  	grb_model.remove(c);
////	        	  	break;
//
//	        	  	String constr = new String( c.get(GRB.StringAttr.ConstrName) );
//	        	  	System.out.println("The Raw Constraint: "+ constr);
//	        		for( final String sub : src ){
//	        			constr = constr.replace(sub, EXPR.reverse_name_map.get(sub) );
//	        		}
//	        		constraint_unsatisfied = constraint_unsatisfied+1;
//	        		System.out.println(constraint_unsatisfied+". Constraint Not Statisfied : "+constr);
//
//	          }
//
//
//
//	        }
//
//
//			//grb_model.write("checking.ilp");
//	        //System.out.println("Number of Constraints not statisfied :"+ constraint_unsatisfied);
//	        System.out.println("=======================INFEASIBLE CONSTRAINTS ENDS HERE========================================");
//
//	      //throw new GRBException("Infeasible model.");
//
//		}else if( grb_model.get( IntAttr.Status ) == GRB.UNBOUNDED ){
//			System.out.println(  "Unbounded Ray : " + grb_model.get( DoubleAttr.UnbdRay ) );
//		}
		
		return grb_model.get( IntAttr.Status );
	}

	protected void dumpAllAssignments(final GRBModel grb_model) {
		try {
			FileWriter file_write = new FileWriter( new File( OUTPUT_FILE + ".result" ) );
			EXPR.grb_cache.forEach( new BiConsumer<EXPR, GRBVar>() {
		    	public void accept(EXPR t, GRBVar u) {
		    		GRBVar[] u_arr = new GRBVar[]{ u };
		    		try {
		    			char[] type = grb_model.get( GRB.CharAttr.VType , u_arr );
		    			file_write.write( t + " " + type[0] + " " + Arrays.toString( grb_model.get( GRB.DoubleAttr.X, u_arr ) ) );
					} catch (GRBException | IOException e) {
						System.out.println("EXPR : " + t );
						e.printStackTrace();
					}
		    	};
			});		
			file_write.flush();
			file_write.close();
			
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	protected void outputLPFile(final GRBModel grb_model) throws GRBException, IOException {
		grb_model.write( OUTPUT_FILE );
		
		List<String> src = new ArrayList<>( EXPR.reverse_name_map.keySet() );
		Collections.sort( src, new Comparator<String>() {

			@Override
			public int compare(  String o1, String o2) {
				return (new Integer(o1.length()).compareTo( 
							new Integer( o2.length()) ) );
			}
		});
		Collections.reverse( src );
		
		Files.write( Paths.get( OUTPUT_FILE + ".post" ), 
				Files.readAllLines( Paths.get( OUTPUT_FILE ) ).stream().map( new Function<String, String>() {

					@Override
					public String apply(String t) {
						String ret = t;
						for( String entry :  src ){
							ret = ret.replace( entry, EXPR.reverse_name_map.get( entry ) );
						}
						return ret;
					}
					
				}).collect( Collectors.toList() ) );		
	}


	private void outputAssignments( final HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> map, final int time ) {
		map.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>>( ) {
			@Override
			public void accept(PVAR_NAME pvar, ArrayList<ArrayList<LCONST>> u) {
				u.stream().forEach( new Consumer< ArrayList<LCONST> >() {

					   @Override
					   public void accept( ArrayList<LCONST> term ) {
						   EXPR expr = new PVAR_EXPR( pvar._sPVarName, term ).addTerm(TIME_PREDICATE, constants, objects);
						
						   EXPR subs_t = expr.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(time) ), constants, objects);
						   try {
								System.out.println( subs_t + "=" + EXPR.grb_cache.get( subs_t ).get( DoubleAttr.X ) );
						   } catch (GRBException e) {
								e.printStackTrace();
						   }
						}
				});
				}
			});
	}
	
	protected Map<EXPR, Double> getAssignments( final HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> map, final int time ) {
		final HashMap< EXPR, Double > ret = new HashMap< >();
		
	    map.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>>( ) {
			@Override
			public void accept(PVAR_NAME pvar, ArrayList<ArrayList<LCONST>> u) {
				u.stream().forEach( new Consumer< ArrayList<LCONST> >() {

					   @Override
					   public void accept( ArrayList<LCONST> term ) {
						   EXPR expr = new PVAR_EXPR( pvar._sPVarName, term ).addTerm(TIME_PREDICATE, constants, objects);
						
						   EXPR subs_t = expr.substitute( 
								   Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(time) ), constants, objects);
						   
						   try {
							   GRBVar grb_var = EXPR.grb_cache.get( subs_t );
							   assert( grb_var != null );
							   ret.put( subs_t, grb_var.get( DoubleAttr.X ) );
						   } catch (GRBException e) {
								e.printStackTrace();
								////System.exit(1);
						   }
						}
				});
				}
			});
		return ret;
	}

	protected void getConstants() throws EvalException {
		System.out.println("------This is getConstants(Translate.java) ");
		ArrayList<PVAR_INST_DEF> all_consts = new ArrayList<PVAR_INST_DEF>();
		for( final PVAR_NAME p : rddl_state._alNonFluentNames ){
			ArrayList<ArrayList<LCONST>> atoms = rddl_state.generateAtoms( p );
			Object def = rddl_state.getDefaultValue(p);
			for( final ArrayList<LCONST> atom : atoms ){
				all_consts.add( new PVAR_INST_DEF(p._sPVarName, def, atom) );
			}
		}
		if( rddl_nonfluents != null && rddl_nonfluents._alNonFluents != null ){
			all_consts.addAll( rddl_nonfluents._alNonFluents );
		}
		
		constants.putAll( getConsts( all_consts ) );//overwrite default values		
//		constants.putAll( getConsts(  rddl_nonfluents._alNonFluents ) );
		//This is changed by HARISH.
		//System.out.println("Constants: " );
		//System.out.println("---------------------------------------" );
		//constants.forEach( (a,b) -> System.out.println( a + " : " + b ) );
	}

	protected void translateConstraints(final GRBModel grb_model) throws Exception {
		
		GRBExpr old_obj = grb_model.getObjective();
//		translateMaxNonDef( );
		System.out.println("--------------Translating Constraints-------------");
		
		ArrayList<BOOL_EXPR> constraints = new ArrayList<BOOL_EXPR>();
		constraints.addAll( rddl_state._alActionPreconditions ); constraints.addAll( rddl_state._alStateInvariants );
		for( final EXPR e : constraints ){
			System.out.println( "Translating Constraint " + e );
			
//			substitution expands quantifiers
//			better to substitute for time first
			EXPR non_stationary_e = e.substitute( Collections.EMPTY_MAP, constants, objects)
					.addTerm(TIME_PREDICATE, constants, objects );
//			this works. but is expensive
//			QUANT_EXPR all_time = new QUANT_EXPR( QUANT_EXPR.FORALL, 
//					new ArrayList<>( Collections.singletonList( new LTYPED_VAR( TIME_PREDICATE._sVarName,  TIME_TYPE._STypeName ) ) )
//							, non_stationary_e );
			for( int t = 0 ; t < TIME_TERMS.size(); ++t ){
				EXPR this_t = non_stationary_e.substitute( 
						 	Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(t) ), constants, objects);
				GRBVar grb_var = this_t.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
				final String nam = RDDL.EXPR.getGRBName(this_t)+"=1";
				System.out.println(nam);
				
				GRBConstr grb_constr = grb_model.addConstr( grb_var, GRB.EQUAL, 1, nam );
//				System.out.println(this_t+"=1");
//				grb_model.update();
				
				saved_expr.add( this_t ); 
				saved_constr.add( grb_constr );//saved_vars.add( grb_var );
			}
		}
		
		grb_model.setObjective(old_obj);
		grb_model.update();
	}

//	private void translateMaxNonDef() {
//		EXPR sum = new REAL_CONST_EXPR( 0d );
//		for( Entry<PVAR_NAME, ArrayList<ArrayList<LCONST>>> action_var : rddl_action_vars.entrySet() ){
//			for( ArrayList<LCONST> terms : action_var.getValue() ){
//				sum = new OPER_EXPR( sum, new PVAR_EXPR( action_var.getKey()._sPVarName, terms ), OPER_EXPR.PLUS );
//			}
//		}
//		sum = sum.substitute( Collections.EMPTY_MAP, constants, objects).addTerm(TIME_PREDICATE);
//		COMP_EXPR action_constraint_stationary = new COMP_EXPR( sum, 
//				new INT_CONST_EXPR( rddl_instance._nNonDefActions ), COMP_EXPR.LESSEQ );
//		QUANT_EXPR action_constraint_non_stationary = new QUANT_EXPR( QUANT_EXPR.FORALL, 
//				new ArrayList<LTYPED_VAR>(
//						Collections.singletonList( new LTYPED_VAR( TIME_PREDICATE._sVarName, TIME_TYPE._STypeName ) ) ), 
//						action_constraint_stationary );
//		GRBVar constrained_var = action_constraint_non_stationary.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//		grb_model.addConstr( constrained_var, GRB.EQUAL, 1, "maxnondef=1" );
//		grb_model.update();		
//	}

	protected void translateInitialState( final GRBModel grb_model,
			HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> subs ) throws GRBException {
		
		GRBExpr old_obj = grb_model.getObjective();
		
		for( final PVAR_NAME p : rddl_state_vars.keySet() ){
			for( final ArrayList<LCONST> terms : rddl_state_vars.get( p ) ){
				Object rhs = null;
				if( subs.containsKey( p ) && subs.get( p ).containsKey( terms ) ){
					rhs = subs.get(p).get( terms );
				}else{
					rhs = rddl_state.getDefaultValue(p);
				}

				PVAR_EXPR stationary_pvar_expr = new PVAR_EXPR( p._sPVarName, terms );
				EXPR non_stationary_pvar_expr = stationary_pvar_expr
						.addTerm( TIME_PREDICATE, constants, objects )
						.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(0) ) , constants, objects); 
				GRBVar lhs_var = non_stationary_pvar_expr.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
				
				EXPR rhs_expr = null;
				if( rhs instanceof Boolean ){
					rhs_expr = new BOOL_CONST_EXPR( (boolean) rhs );
				}else if( rhs instanceof Double ){
					rhs_expr = new REAL_CONST_EXPR( (double)rhs );
				}else if( rhs instanceof Integer ){
					rhs_expr = new INT_CONST_EXPR( (int)rhs );
				}
				GRBVar rhs_var = rhs_expr.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
				
				final String nam = RDDL.EXPR.getGRBName(non_stationary_pvar_expr)
						+"="+RDDL.EXPR.getGRBName(rhs_expr);
				
				GRBConstr new_constr = grb_model.addConstr( lhs_var, GRB.EQUAL, rhs_var, nam );
//				grb_model.update();
				
				System.out.println( non_stationary_pvar_expr + "=" + rhs_expr );
				System.out.println(nam);
				
//				to_remove_vars.add( lhs_var ); to_remove_vars.add( rhs_var );
				to_remove_expr.add( non_stationary_pvar_expr ); 
				to_remove_expr.add( rhs_expr );
				to_remove_constr.add( new_constr );
				
//				saved_vars.add( lhs_var ); saved_vars.add( rhs_var );
//				saved_expr.add( non_stationary_pvar_expr ); saved_expr.add( rhs_expr );
			}
		}
		
		grb_model.setObjective(old_obj);
//		grb_model.update();
		
	}

	protected void translateCPTs(HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> initState,
			final GRBModel grb_model) throws GRBException { 
		System.out.println("------This is translateCPTs (Original)");
		GRBExpr old_obj = grb_model.getObjective();
		
		ArrayList<HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>> src 
		= new ArrayList< HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> >();
		src.add( rddl_state_vars ); src.add( rddl_interm_vars ); src.add( rddl_observ_vars );
		
		for( final HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> map : src ){
		
			for( final PVAR_NAME p : map.keySet() ){
				for( final ArrayList<LCONST> terms : map.get( p ) ){
					//This piece of code is Changed by HARISH
					//System.out.println( "CPT for " + p.toString() + terms );
					
					CPF_DEF cpf = null;
					if( rddl_state_vars.containsKey(p) ){
						cpf = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName + "'" ) );
					}else {
						cpf = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName ) );
					}
					
					Map<LVAR, LCONST> subs = getSubs( cpf._exprVarName._alTerms, terms );
					EXPR new_lhs_stationary = cpf._exprVarName.substitute( subs, constants, objects );
					EXPR new_rhs_stationary = cpf._exprEquals.substitute(subs, constants, objects);
					for( int t = 0 ; t < lookahead; ++t ){
						
						EXPR new_lhs_non_stationary = null;
						GRBVar lhs_var = null;
						
						if( rddl_state_vars.containsKey(p) ){
							if( t == lookahead - 1 ){
								continue;
							}
							//FIXME : stationarity assumption
							new_lhs_non_stationary = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects )
									.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(t+1) ), constants, objects);
							lhs_var = new_lhs_non_stationary.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
						}else {
							new_lhs_non_stationary = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects )
									.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(t) ), constants, objects);
							lhs_var = new_lhs_non_stationary.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
						}
						
						//FIXME : stationarity assumption
						EXPR new_rhs_non_stationary = new_rhs_stationary.addTerm(TIME_PREDICATE, constants, objects )
								.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(t) ), constants, objects );
						GRBVar rhs_var = new_rhs_non_stationary.getGRBConstr(GRB.EQUAL,  grb_model, constants, objects, type_map);
						
						final String nam = RDDL.EXPR.getGRBName(new_lhs_non_stationary) + "=" + 
												RDDL.EXPR.getGRBName(new_rhs_non_stationary);
						GRBConstr new_constr = grb_model.addConstr( lhs_var, GRB.EQUAL, rhs_var, nam );
//						System.out.println( new_lhs_non_stationary + "=" + new_rhs_non_stationary);
//						System.out.println( nam );
						//						grb_model.update();
						
						saved_constr.add( new_constr );
						saved_expr.add( new_lhs_non_stationary ); 
						saved_expr.add( new_rhs_non_stationary );
//						saved_vars.add( lhs_var ); saved_vars.add( rhs_var );
//						to_remove_vars.add( lhs_var ); to_remove_vars.add( rhs_var );
//						to_remove_expr.add( new_lhs_non_stationary ); to_remove_expr.add( new_rhs_non_stationary );
//						to_remove_constr.add( new_constr );
						
					}
					
				}
			}
			
		}
		
		grb_model.setObjective(old_obj);
		grb_model.update();
		
	}

	protected Map<LVAR,LCONST> getSubs(ArrayList<LTERM> terms, ArrayList<LCONST> consts) {
		Map<LVAR, LCONST> ret = new HashMap<RDDL.LVAR, RDDL.LCONST>();
		for( int i = 0 ; i < terms.size(); ++i ){
			assert( terms.get(i) instanceof LVAR );
			ret.put( (LVAR)terms.get(i), consts.get(i) );
		}
		return ret;
	}

	protected HashMap< PVAR_NAME, HashMap< ArrayList<LCONST>, Object> > getConsts(ArrayList<PVAR_INST_DEF> consts) {
		HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> ret 
			= new HashMap< PVAR_NAME, HashMap< ArrayList<LCONST>, Object> >();
		for( final PVAR_INST_DEF p : consts ){
			if( ret.get(p._sPredName) == null ){
				ret.put( p._sPredName, new HashMap<ArrayList<LCONST>, Object>() );
			}
			HashMap<ArrayList<LCONST>, Object> inner_map = ret.get( p._sPredName );
			inner_map.put( p._alTerms, p._oValue );
			ret.put( p._sPredName, inner_map );//unnecessary
		}
		return ret;
	}

//	private void translateInitialState() throws GRBException {
//		for( final PVAR_INST_DEF p : rddl_instance._alInitState ){
//			PVAR_EXPR pe = new PVAR_EXPR( p._sPredName._sPVarName , pred_type.get( p._sPredName )._STypeName,  p._alTerms );
//			pe.getGRBConstr( GRB.EQUAL, grb_model, 
//						Collections.singletonMap( 
//								p._sPredName, Collections.singletonMap( p._alTerms, p._oValue) ), null );
//			
//			GRBVar var = getGRBVar(p._sPredName, p._alTerms, 0);
//			GRBLinExpr expr = new GRBLinExpr();
//			expr.addConstant( ((Number)p._oValue).doubleValue() );
//			grb_model.addConstr(var, GRB.EQUAL, expr, "C0__" + var.toString() );
//		}
//		//max-nondef does not make sense with real valued actions
//		//TODO discounting
//	}

//	private void translateCPTs() throws EvalException, GRBException {
//		
//		for( int t = 0 ; t < lookahead; ++t ){
//			for( final PVAR_NAME p : rddl_state._alStateNames ){
//				final CPF_DEF cpf_def  = rddl_state._hmCPFs.get( new PVAR_NAME( p._sPVarName + "'" ) );
//				ArrayList<LTERM> param_names = cpf_def._exprVarName._alTerms ; 
//				ArrayList<ArrayList<LCONST>> params = rddl_state.generateAtoms(p);
//				//p'(?x) = p(?x) + a(?x) => p_t(x1) = p_{t-1}(x1) + a_{t-1}(x1)
//				for( final ArrayList<LCONST> param : params ){
//					HashMap<LVAR, LCONST> subs = getSubs( param_names, param );
//					//encode lhs
//					PVAR_EXPR timed_pvar = new PVAR_EXPR( p._sPVarName + "_t" + t , 
//							pred_type.get( p )._STypeName, param );
//					GRBVar timed_gvar = timed_pvar.getGRBConstr( GRB.EQUAL, grb_model, constants, null);
//					//encode rhs
//					EXPR substituted_expr = cpf_def._exprEquals.substitute(subs, constants, null);
//					GRBVar substituted_gvar = substituted_expr.getGRBConstr( GRB.EQUAL, grb_model, constants, null);
//					//lhs = rhs
//					grb_model.addConstr( timed_gvar,  substituted_gvar, expr, name)
//							
//							getGRBVar( p, param, t+1 );//t+1 for next time step (primed)
//					final String grb_varname = CleanFluentName( p.toString() + param );
//					ConstraintLeaf cl = new ConstraintLeaf(grb_var, grb_varname, t );
//					translateLinearExp( cpf_def._exprEquals, subs, 1.0d, cl, t );
//					cl.addConstraint();
//				}
//			}
//		}
//	}

	protected void translateReward(final GRBModel grb_model) throws Exception{
		EXPR stationary = rddl_state._reward;
		//expand quantifier
		//filter constants
		EXPR stationary_clear = stationary.substitute( Collections.EMPTY_MAP, constants, objects);
		//add time 
		EXPR non_stationary = stationary_clear.addTerm( TIME_PREDICATE , constants, objects );
		//expand time
		//this works but expensive
		//just iterate over time 
		//reset objective
		grb_model.setObjective( new GRBLinExpr() );
//		grb_model.update();
				
		for( int time = 0 ; time < lookahead; ++time ){
			EXPR subs_t = non_stationary.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(time)), constants, objects);
			saved_expr.add( subs_t );
			//This piece of Code is changed by HARISH
			//System.out.println(subs_t);
			subs_t.addGRBObjectiveTerm(grb_model, constants, objects, type_map);
			//saved_vars.add( subs_t.addGRBObjectiveTerm(grb_model, constants, objects, type_map) );
		}
		grb_model.update();
	}
	
//	private HashMap<LVAR, LCONST> getSubs( final ArrayList<LTERM> param_names,
//			final ArrayList<LCONST> param ) {
//		HashMap<LVAR, LCONST> subs = new HashMap<RDDL.LVAR, RDDL.LCONST>();
	
//		for( int i = 0 ; i < param_names.size(); ++i ){
//			subs.put( (LVAR)param_names.get(i), param.get(i) );
//		}
//		return subs;
//	}
//	




	protected void initializeCompetitionRDDL(final RDDL rddl_object,String instanceName,State s) throws Exception{


		//I need to think how to remove this.
		this._rddl = rddl_object;
		NONFLUENTS nonFluents = null;
		DOMAIN domain = null;
		INSTANCE instance = rddl_object._tmInstanceNodes.get(instanceName);
		if (instance._sNonFluents != null) {
			nonFluents = rddl_object._tmNonFluentNodes.get(instance._sNonFluents);
		}
		domain = rddl_object._tmDomainNodes.get(instance._sDomain);
		if (nonFluents != null && !instance._sDomain.equals(nonFluents._sDomain)) {
			System.err.println("Domain name of instance and fluents do not match: " +
					instance._sDomain + " vs. " + nonFluents._sDomain);
			////System.exit(1);
		}



		this.rddl_obj = rddl_object;
		this.instance_name = instanceName; //instance_rddl._tmInstanceNodes.keySet().iterator().next() ;
		this.domain_name = instance._sDomain;  //domain_rddl._tmDomainNodes.keySet().iterator().next();



		//This is addition for HOP Planners ( added by Harish).
		rddl_instance = rddl_obj._tmInstanceNodes.get( instance_name );
		if (rddl_instance == null){
			throw new Exception("Instance '" + instance_name +
					"' not found, choices are " + rddl_obj._tmInstanceNodes.keySet());
		}

		rddl_nonfluents = null;
		if (rddl_instance._sNonFluents != null){
			rddl_nonfluents = rddl_obj._tmNonFluentNodes.get(rddl_instance._sNonFluents);
		}

		rddl_domain = rddl_obj._tmDomainNodes.get(rddl_instance._sDomain);
		if ( rddl_domain == null){
			throw new Exception("Could not get domain '" +
					rddl_instance._sDomain + "' for instance '" + instance_name + "'");
		}

		if (rddl_nonfluents != null && !rddl_instance._sDomain.equals(rddl_nonfluents._sDomain)){
			throw new Exception("Domain name of instance and fluents do not match: " +
					rddl_instance._sDomain + " vs. " + rddl_nonfluents._sDomain);
		}

		this.rddl_state = new rddl.State();





		rddl_state = s;
		this.rddl_state_vars = collectGroundings( rddl_state._alStateNames );
		this.rddl_action_vars = collectGroundings( rddl_state._alActionNames );
		this.rddl_observ_vars = collectGroundings( rddl_state._alObservNames );
		this.rddl_interm_vars = collectGroundings( rddl_state._alIntermNames );

		this.string_state_vars = cleanMap( rddl_state_vars );
		this.string_action_vars = cleanMap( rddl_action_vars );
		this.string_observ_vars = cleanMap( rddl_observ_vars );
		this.string_interm_vars = cleanMap( rddl_interm_vars );

		// If necessary, correct the partially observed flag since this flag determines what content will be seen by the Client
		if ((rddl_domain._bPartiallyObserved && rddl_state._alObservNames.size() == 0)
				|| (!rddl_domain._bPartiallyObserved && rddl_state._alObservNames.size() > 0)) {
			boolean observations_present = (rddl_state._alObservNames.size() > 0);
			System.err.println("WARNING: Domain '" + rddl_domain._sDomainName
					+ "' partially observed (PO) flag and presence of observations mismatched.\nSetting PO flag = " + observations_present + ".");
			rddl_domain._bPartiallyObserved = observations_present;
		}




		for(Map.Entry<PVAR_NAME,PVARIABLE_DEF> entry : rddl_state._hmPVariables.entrySet()){


			PVAR_NAME temp_pvar = entry.getKey();

			if(entry.getValue() instanceof RDDL.PVARIABLE_STATE_DEF){

				Object val = ((RDDL.PVARIABLE_STATE_DEF) entry.getValue())._oDefValue;
				rddl_state_default.put(temp_pvar,val);



			}








		}









	}






	//This function initialize the RDDL 
	protected void initializeRDDL(final String domain_file, final String instance_file) throws Exception {
		RDDL domain_rddl = null, instance_rddl = null;
		try {
			domain_rddl = rddl.parser.parser.parse( new File( domain_file) );
		} catch (ParseException e) {
			e.printStackTrace();
			System.out.println("domain file did not parse.");
			////System.exit(1);
		}
		try {
			instance_rddl = rddl.parser.parser.parse( new File( instance_file ) );
		} catch (ParseException e) {
			e.printStackTrace();
			System.out.println("instance file did not parse.");
			////System.exit(1);
		}

		this.rddl_obj = new RDDL();
		this.rddl_obj.addOtherRDDL(domain_rddl,  domain_file);
		this.rddl_obj.addOtherRDDL(instance_rddl, instance_file);

		
		// Set up instance, nonfluent, and domain information
		//I assume that there is only one instance and domain in the respective files
		this.instance_name = instance_rddl._tmInstanceNodes.keySet().iterator().next() ;
		this.domain_name = domain_rddl._tmDomainNodes.keySet().iterator().next();
		
		rddl_instance = rddl_obj._tmInstanceNodes.get( instance_name );
		if (rddl_instance == null){
			throw new Exception("Instance '" + instance_name + 
					"' not found, choices are " + rddl_obj._tmInstanceNodes.keySet());
		}
		
		rddl_nonfluents = null;
		if (rddl_instance._sNonFluents != null){
			rddl_nonfluents = rddl_obj._tmNonFluentNodes.get(rddl_instance._sNonFluents);
		}
		
		rddl_domain = rddl_obj._tmDomainNodes.get(rddl_instance._sDomain);
		if ( rddl_domain == null){
			throw new Exception("Could not get domain '" + 
					rddl_instance._sDomain + "' for instance '" + instance_name + "'");
		}
		
		if (rddl_nonfluents != null && !rddl_instance._sDomain.equals(rddl_nonfluents._sDomain)){
			throw new Exception("Domain name of instance and fluents do not match: " + 
					rddl_instance._sDomain + " vs. " + rddl_nonfluents._sDomain);
		}	
		
		this.rddl_state = new rddl.State();
		rddl_state.init( rddl_domain._hmObjects,
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
						rddl_domain._exprReward,
						rddl_instance._nNonDefActions);
		
		this.rddl_state_vars = collectGroundings( rddl_state._alStateNames );
		this.rddl_action_vars = collectGroundings( rddl_state._alActionNames );
		this.rddl_observ_vars = collectGroundings( rddl_state._alObservNames );
		this.rddl_interm_vars = collectGroundings( rddl_state._alIntermNames );
		
		this.string_state_vars = cleanMap( rddl_state_vars );
		this.string_action_vars = cleanMap( rddl_action_vars );
		this.string_observ_vars = cleanMap( rddl_observ_vars );
		this.string_interm_vars = cleanMap( rddl_interm_vars );





		for(Map.Entry<PVAR_NAME,PVARIABLE_DEF> entry : rddl_state._hmPVariables.entrySet()){


			PVAR_NAME temp_pvar = entry.getKey();

			if(entry.getValue() instanceof RDDL.PVARIABLE_STATE_DEF){

				Object val = ((RDDL.PVARIABLE_STATE_DEF) entry.getValue())._oDefValue;
				rddl_state_default.put(temp_pvar,val);



			}








		}


		
	}
	
	private void initializeGRB( ) throws GRBException {
		this.GRB_log = GRB_LOGGING_ON ? domain_name + "__" + instance_name + ".grb" : "";
		
		this.grb_env = new GRBEnv(GRB_log);
		grb_env.set( GRB.DoubleParam.TimeLimit, TIME_LIMIT_MINS*60 );
		grb_env.set( GRB.DoubleParam.MIPGap, GRB_MIPGAP );
		grb_env.set( DoubleParam.Heuristics, GRB_HEURISTIC );
		grb_env.set( IntParam.InfUnbdInfo , GRB_INFUNBDINFO );
		grb_env.set( IntParam.DualReductions, GRB_DUALREDUCTIONS );
		grb_env.set( IntParam.IISMethod, GRB_IISMethod );
		
		grb_env.set( IntParam.MIPFocus, 1);
		grb_env.set( DoubleParam.FeasibilityTol, 1e-9 );// Math.pow(10,  -(State._df.getMaximumFractionDigits() ) ) ); 1e-6
		grb_env.set( DoubleParam.IntFeasTol,  1e-9);//Math.pow(10,  -(State._df.getMaximumFractionDigits() ) ) ); //Math.pow( 10 , -(1+State._df.getMaximumFractionDigits() ) ) );
		grb_env.set( DoubleParam.FeasRelaxBigM, RDDL.EXPR.M);
		grb_env.set( IntParam.Threads, 1 );
		grb_env.set( IntParam.Quad, 1 );
		grb_env.set( IntParam.Method, 1 );
		grb_env.set( DoubleParam.NodefileStart, 0.5 );
		//grb_env.set(GRB.IntParam.Presolve,0);
		//grb_env.set(DoubleParam.OptimalityTol, 1e-2);
		//grb_env.set(GRB.IntParam.NumericFocus, 3);
//		grb_env.set( IntParam.SolutionLimit, 5);

		System.out.println("current nodefile directly " + grb_env.get( StringParam.NodefileDir ) );
		
		this.static_grb_model = new GRBModel( grb_env );
		//max
		static_grb_model.set( GRB.IntAttr.ModelSense, -1);
		
		//create vars for state, action, interm vars over time
//		translate_time.ResumeTimer();
//		addAllVariables();
		static_grb_model.update();
//		translate_time.PauseTimer();
		
	}

	protected List<String> cleanMap( final HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> map ) {
		List<String> ret = new ArrayList<String>();
		map.forEach( (a,b) -> b.forEach( m -> ret.add( CleanFluentName( a.toString() + m ) ) ) );
		return ret;
	}
	
	public HashMap< PVAR_NAME, ArrayList<ArrayList<LCONST>> > collectGroundings( final ArrayList<PVAR_NAME> preds )
		throws EvalException {
		HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> ret 
			= new  HashMap<RDDL.PVAR_NAME, ArrayList<ArrayList<LCONST>>>();
		
		for( PVAR_NAME p : preds ){
			ArrayList<ArrayList<LCONST>> gfluents = rddl_state.generateAtoms(p);
			ret.put(p, gfluents);
			PVARIABLE_DEF def = rddl_state._hmPVariables.get(p);
			pred_type.put( p, def._typeRange );
		}
		return ret ;
	}
	
	public static String CleanFluentName(String s) {
		s = s.replace("[", "__");
		s = s.replace("]", "");
		s = s.replace(", ", "_");
		s = s.replace(',','_');
		s = s.replace(' ','_');
		s = s.replace('-','_');
		s = s.replace("()","");
		s = s.replace("(", "__");
		s = s.replace(")", "");
		s = s.replace("$", "");
		if (s.endsWith("__"))
			s = s.substring(0, s.length() - 2);
		if (s.endsWith("__'")) {
			s = s.substring(0, s.length() - 3);
			s = s + "\'"; // Really need to escape it?  Don't think so.
		}
		return s;
	}
	
	public static void main(String[] args,RDDL rddl_object,State s) throws Exception {
		System.out.println( Arrays.toString( args ) );
		System.out.println( new Translate( Arrays.asList( args ),rddl_object,s ).doPlanInitState() );
	}
	
	public Translate( List<String> args, RDDL rddl_object, State s) throws Exception {
		System.out.println("------ This is Tranlate (Translate.java)-----");
		System.out.println( args );
		StateViz viz = null;
//		if( args.get(4).equalsIgnoreCase("reservoir") ){
//			viz = new PVarHeatMap( PVarHeatMap.reservoir_tags );
//		}else if( args.get(4).equalsIgnoreCase("inventory") ){
//			viz = new PVarHeatMap( PVarHeatMap.inventory_tags );
//		}else if( args.get(4).equalsIgnoreCase("racetrack") ){
//			viz = new TwoDimensionalTrajectory();
//		}
		
		TranslateInit( args.get(0), args.get(1), Integer.parseInt( args.get(2) ), Double.parseDouble( args.get(3) ),
				viz, rddl_object, s );











//		doPlanInitState();
	}

	@Override
	public ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException {
		HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> subs = getSubsWithDefaults( s );
		
		
		//This is written by harish.
//		for(ArrayList<LCONST> vehicleSub : vehicleSubs) {
//			
//			boolean this_service = (boolean)rddl_state.getPVariableAssign(vehicleInServicePvar, vehicleSub);
//			
//			if(this_service) {
//				System.out.println("This Vehicle " + vehicleSub  + "in Service");
//				
//			}
//			
//			
//			
//			
//		}
		
		
		
		if( static_grb_model == null ){
			//This is for 
			System.out.println("-----------------------------------------This is for First Time----");
			firstTimeModel( );
		}
		
		try {
			Pair<ArrayList<Map<EXPR, Double>>,Integer> out_put = doPlan( subs, RECOVER_INFEASIBLE );
			ArrayList<Map<EXPR, Double>> ret_expr = out_put._o1;




			//This is the exit code.
			Integer exit_code = out_put._o2;
			//ArrayList<ArrayList<PVAR_INST_DEF>> ret_list = new ArrayList<ArrayList<PVAR_INST_DEF>>();



			ret_list.clear();
			for(int i =0;i<ret_expr.size();i++){

				ArrayList<PVAR_INST_DEF> ret = getRootActions(ret_expr.get(i),s,i);
				ret_list.add(ret);


			}

			//System.out.println("####################################################");
			System.out.println("These are Root Actions:" + ret_list.get(0).toString());
			ArrayList<PVAR_INST_DEF> returning_action = ret_list.get(0);


			if(exit_code.equals(2)){
				//Solution is infeasible.
				HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>>  copiedState = deepCopyState(rddl_state);
				ArrayList<PVAR_INST_DEF> act = getRandomActionForSimulation(s,new Random(1));
				Double avg_reward_random = runActionSimulation(s,30, act);
				Double avg_reward_noop   = runActionSimulation(s,30, ret_list.get(0));


				if(avg_reward_random>avg_reward_noop){

					returning_action = act;
					if(SHOW_LEVEL_1)
						System.out.println("Choosing Random Action");


				}
				else {

					returning_action = ret_list.get(0);



				}








			}
















			//System.out.println("These are Consensus Actions:" + ret_list.get(1).toString());







			
//			try{
//				s.computeIntermFluents( ret, new RandomDataGenerator()  );
//				s.checkStateActionConstraints(ret);
//			}catch( EvalException exc ){
//				System.out.println("Violates state-action constraints.");
//				exc.printStackTrace();
//				////System.exit(1);;
////				ret_expr = doPlan( subs , true );
////				ret = getRootActions(ret_expr);
//			}
			
			//fix to prevent numeric errors of the overflow kind
//			int num_digs = State._df.getMaximumFractionDigits();
//			while( num_digs > 0 ){
//				try{
//					s.checkStateActionConstraints(ret);
//					break;
//				}catch( EvalException exc ){
//					//can either return noop 
//					//here i am rounding down one digit at a time
//					num_digs = num_digs-1;
//					System.out.println("Constraint violatation : reducing precision to " + num_digs );
//					ret = reducePrecision( ret , num_digs );
//					System.out.println("Lower precision : " + ret );
//				}
//			}
			
//			if( num_digs == 0 ){
//				System.out.println("Turning into noop");
//				ret = new ArrayList<PVAR_INST_DEF>();
//			}
			
			if( viz != null ){
				viz.display(s, 0);
			}
			//clear interms
//			s.computeIntermFluents( ret, new RandomDataGenerator()  );
//			System.out.println("State : " + s );
//			System.out.println( "Action : " + ret );
//			s.clearIntermFluents();
//			
			cleanUp(static_grb_model);
//			static_grb_model.getEnv().dispose();
//			static_grb_model.dispose();
//			static_grb_model = null;
			
			return returning_action;
		} catch (Exception e) {
			e.printStackTrace();
			//////System.exit(1);
		}
		return null;
	}

	protected HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> getSubsWithDefaults(State state) throws EvalException {
		
		HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> ret 
		= new HashMap< PVAR_NAME, HashMap< ArrayList<LCONST>, Object> >();
		
		for( PVAR_NAME stateVar : state._alStateNames ){
			if( !ret.containsKey(stateVar) ){
				ret.put( stateVar, new HashMap<>() );
			}
			ArrayList<ArrayList<LCONST>> possible_terms = state.generateAtoms(stateVar);
			if( possible_terms.isEmpty() ){
				ret.get(stateVar).put( new ArrayList<LCONST>(), state.getDefaultValue(stateVar) );
			}else{
				for( ArrayList<LCONST> term_assign : possible_terms ){
					if( state._state.containsKey(stateVar) && state._state.get(stateVar).containsKey(term_assign) ){
						ret.get( stateVar ).put( term_assign, state._state.get(stateVar).get(term_assign) );
					}else{
						ret.get(stateVar).put(term_assign, state.getDefaultValue(stateVar) );
					}
				}
			}
		}
		
		
		
		return ret;
	}

	private void firstTimeModel( ) {
		try {
			System.out.println("1. Go to initializeGRB \n2. addExtraPredicates \n3. addAllVariables \n4. prepareModel(FittedEmergencyDomainHOP.java) (a.translateConstraints, b.translateRewards, c. maybe translateCPTS) \n5. doPlan(Translate.java getActions)");
			initializeGRB( );
			addExtraPredicates();
			addAllVariables();
			prepareModel( );
		} catch (Exception e) {
			e.printStackTrace();
			//////System.exit(1);
		}		
		
	}

//	private ArrayList<PVAR_INST_DEF> reducePrecision(
//			ArrayList<PVAR_INST_DEF> ret, int  cur_digs) {
//		DecimalFormat temp_df = new DecimalFormat("#.##"); 
//		temp_df.setMaximumFractionDigits(cur_digs); 
//		temp_df.setRoundingMode( RoundingMode.DOWN );
//		
//		List<PVAR_INST_DEF> ret_lower = ret.stream().map( new Function< PVAR_INST_DEF,  PVAR_INST_DEF >() {
//			public PVAR_INST_DEF apply(PVAR_INST_DEF t) {
//				Object new_val = t._oValue;
//				if( t._oValue instanceof Number ){
//					String new_val_text = temp_df.format( t._oValue );
//					if( t._oValue instanceof Double ){
//						new_val = Double.valueOf( new_val_text );
//					}else if( t._oValue instanceof Integer ){
//						new_val = Integer.valueOf( new_val_text );
//					}
//				}
//				return new PVAR_INST_DEF( t._sPredName._sPVarName, new_val, t._alTerms );
//			}
//		} ).collect( Collectors.toList() );
//		
//		return new ArrayList<PVAR_INST_DEF>( ret_lower );
//	}
	
	protected Object sanitize(PVAR_NAME pName, double value) {
		if( value == -1*value ){
			value = Math.abs( value );
		}
		
		Object ret = null;
		if( type_map.get( pName ).equals( GRB.BINARY ) ){
			if( value > 1.0 ){
				value = 1;
			}else if( value < 0.0 ){
				value = 0;
			}else{
				value = Math.rint( value );
			}
			assert( value == 0d || value == 1d );
			ret = new Boolean( value == 0d ? false : true );
		}
		else 
			if( type_map.get( pName ).equals( GRB.INTEGER ) ){
				value = Math.rint( value );
				ret = new Integer( (int)value );
			}
			else{
				ret = new Double( value );
			}
		return ret;							
	}
	
	protected ArrayList<PVAR_INST_DEF> getRootActions(Map<EXPR, Double> ret_expr, State s, int decision_value) {
		final ArrayList<PVAR_INST_DEF> ret = new ArrayList<>();
		if( ret_expr == null ){
			return ret;
		}
		
		rddl_action_vars.entrySet().parallelStream().forEach( new Consumer< Map.Entry< PVAR_NAME, ArrayList<ArrayList<LCONST>> > >() {
			@Override
			public void accept( Map.Entry< PVAR_NAME , ArrayList<ArrayList<LCONST>> > entry ) {
				final PVAR_NAME pvar = entry.getKey();
				entry.getValue().parallelStream().forEach( new Consumer< ArrayList<LCONST> >() {
					@Override
					public void accept(ArrayList<LCONST> terms ) {
						final EXPR lookup = new PVAR_EXPR( pvar._sPVarName, terms )
							.addTerm(TIME_PREDICATE, constants, objects)
							.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(0) ), constants, objects);
						assert( ret_expr.containsKey( lookup ) );
						
						Object value = sanitize( pvar, ret_expr.get( lookup ) );
						
						if( !value.equals( rddl_state.getDefaultValue(pvar) ) ){
							ret.add( new PVAR_INST_DEF( pvar._sPVarName, value , terms ) );	
						}
					}
				});
			}
		});
		
		return ret;
	}



















	@Override
	public void roundEnd(double reward) {
		super.roundEnd(reward);
		try {
			handleOOM( static_grb_model );
		} catch (GRBException e) {
			e.printStackTrace();
			//////System.exit(1);
		}
	}









	protected HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> deepCopyState(final State rddl_state) {

		HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> temp = rddl_state._state;
		HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> copied_state =  new HashMap<>();

		for(PVAR_NAME pvar : temp.keySet()){

			PVAR_NAME new_pvar = new PVAR_NAME(pvar._sPVarName);
			HashMap<ArrayList<LCONST>,Object> temp_hashmap = temp.get(pvar);
			HashMap<ArrayList<LCONST>,Object> new_hashmap =  new HashMap<>();

			for(ArrayList<LCONST> temp_array : temp_hashmap.keySet()){

				ArrayList<LCONST> new_array = new ArrayList<>();

				for(int i=0; i<temp_array.size(); i++){
					LCONST temp_lconst = temp_array.get(i);
					if(temp_lconst instanceof OBJECT_VAL){
						LCONST new_lconst = new OBJECT_VAL(temp_lconst._sConstValue);
						new_array.add(new_lconst); }
					if(temp_lconst instanceof RDDL.ENUM_VAL){
						LCONST new_lconst = new RDDL.ENUM_VAL(temp_lconst._sConstValue);
						new_array.add(new_lconst); }
				}


				int check = 0;


				//This is for the Object
				if( temp_hashmap.get(temp_array) instanceof Boolean){
					Boolean new_objval = (Boolean) temp_hashmap.get(temp_array);
					new_hashmap.put(new_array,new_objval);
					check=1;}

				if( temp_hashmap.get(temp_array) instanceof Double){
					Double new_objval = (Double) temp_hashmap.get(temp_array);
					new_hashmap.put(new_array,new_objval);
					check=1;}

				//This check if the object is of other instance and not implemented, Please implement this one.
				assert(check==1);
				if(check==0){

					System.out.println("--------------------------------------------------------------------------------------------------------This Instance of an Object is Not Implemented");



				}


			}
			copied_state.put(new_pvar,new_hashmap);



		}




		return copied_state;





	}









	protected HashMap<ArrayList<LCONST>,Object> getActionInstantiations(PVAR_NAME action_var, TYPE_NAME action_type, Random rand){
		//This function gives the intansiations of the parameters.
		//




		HashMap<ArrayList<LCONST>,Object> action_terms_assign = new HashMap<>();

		ArrayList<TYPE_NAME> temp_objects = object_type_name.get(action_var);
		ArrayList<LCONST> action_terms    = new ArrayList<>();



		for(int i = 0;i<temp_objects.size();i++){
			ArrayList<LCONST> temp_array =rddl_state._hmObject2Consts.get(temp_objects.get(i));
			//This is selecting the object value and creating a ArrayList<LCONST> whichs goes as _alTerms
			int j = rand.nextInt(temp_array.size());
			LCONST val = temp_array.get(j);
			if(val instanceof RDDL.OBJECT_VAL){


				RDDL.OBJECT_VAL new_val = new RDDL.OBJECT_VAL(val._sConstValue);

				action_terms.add(new_val);



			}

		}


		//Selecting the value of each object.
		if(action_type.equals(TYPE_NAME.REAL_TYPE)){
			//select_range Has values [min,max]
			ArrayList<Double> select_range = value_range.get(action_var);
			Double take_action_val = select_range.get(0) + ((select_range.get(1)-select_range.get(0)) * rand.nextFloat());
			action_terms_assign.put(action_terms,take_action_val);

		}




		if(action_type.equals(TYPE_NAME.BOOL_TYPE)){

			ArrayList<Boolean>  select_range = value_range.get(action_var);
			int j = rand.nextInt(select_range.size());

			Boolean take_action_val = select_range.get(j);
			action_terms_assign.put(action_terms,take_action_val);


		}





		return action_terms_assign;




	}








	protected ArrayList<PVAR_INST_DEF> getRandomAction(State s, Random randint) throws EvalException{
		//Need to Write Function for Getting a Random Action.







		ArrayList<PVAR_INST_DEF> final_output_actions  = new ArrayList<>();

		//This is  a buffer list to check the instansiations are already exists or not.
		ArrayList<ArrayList<LCONST>> alaction_terms  = new ArrayList<>();




		for(PVAR_NAME action_var : rddl_action_vars.keySet()){

			TYPE_NAME type_val = s._hmPVariables.get(action_var)._typeRange;

			HashMap<ArrayList<LCONST>,Object> final_action_val = new HashMap<>();



			//This function instansiates t
			HashMap<ArrayList<LCONST>,Object> action_terms_val = getActionInstantiations(action_var,type_val,randint);
			for(ArrayList<LCONST> o : action_terms_val.keySet()){
				if(!alaction_terms.contains(o)){

					Double rand_number = randint.nextDouble();

					if(! (rand_number< rejection_prob)){
						alaction_terms.add(o);
						final_action_val.put(o,action_terms_val.get(o));
					}



				}


			}









			ArrayList<PVAR_INST_DEF> output_actions = new ArrayList<>();
			for(ArrayList<LCONST> key : final_action_val.keySet()){


				PVAR_INST_DEF aa  = new PVAR_INST_DEF(action_var._sPVarName,final_action_val.get(key),key);

				final_output_actions.add(aa);


			}

















		}












		return final_output_actions;




	}










	protected  ArrayList<PVAR_INST_DEF>  getRandomActionForSimulation(State s,Random rand1) throws EvalException {
		HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> root_state  = deepCopyState(s);

		boolean check_action_feasible = false;




		check_action_feasible = false;
		ArrayList<PVAR_INST_DEF> random_action =null;
		int cur_while_check = 0;

		while(!check_action_feasible && (cur_while_check<number_of_iterations)){
			//System.out.println("ITERATION.");
			cur_while_check = cur_while_check+1;


			random_action = getRandomAction(s,rand1);
			check_action_feasible = s.checkActionConstraints(random_action);



		}


		//When the actions are infeasible.
		if(! check_action_feasible){


			System.out.println("The actions are not feasible");



		}

		s.copyStateRDDLState(root_state,true);
		return random_action;




	}




	protected Double runActionSimulation(State s, Integer num_rounds, ArrayList<PVAR_INST_DEF> act ) throws EvalException {
		HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> root_state  = deepCopyState(s);
		Double avg_reward = 0.0;


		for(int i=0 ; i<num_rounds; i++){


			s.computeNextState(act, new RandomDataGenerator());



			final double immediate_reward = ((Number)rddl_domain._exprReward.sample(
					new HashMap<LVAR,LCONST>(),rddl_state, rand)).doubleValue();



			avg_reward += immediate_reward;
			s.copyStateRDDLState(root_state,true);






		}


		return avg_reward/num_rounds;















	}












	protected void runRandomPolicy(final State rddl_state , int trajectory_length, int number_trajectories, Random rand1) throws EvalException {


		HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> traj_inital_state  = deepCopyState(rddl_state);

		buffer_state.clear();
		buffer_action.clear();
		buffer_reward.clear();




		for(int j=0;j<number_trajectories; j++){
			//HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> traj_state_values  = deepCopyState(rddl_state);

			rddl_state.copyStateRDDLState(traj_inital_state,true);
			traj_inital_state = deepCopyState(rddl_state);


			ArrayList<HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>>> store_traj_states = new ArrayList<>();
			ArrayList<Double> store_traj_rewards = new ArrayList<>();
			ArrayList<ArrayList<PVAR_INST_DEF>> store_traj_actions = new ArrayList<>();
			boolean check_action_feasible = false;
			boolean all_infeasible        = false;

			for(int i=0;i<trajectory_length;i++){


				check_action_feasible = false;
				ArrayList<PVAR_INST_DEF> traj_action =null;


				int cur_while_check = 0;

				while(!check_action_feasible && (cur_while_check<number_of_iterations)){
					//System.out.println("ITERATION.");
					cur_while_check = cur_while_check+1;


					traj_action = getRandomAction(rddl_state,rand1);
					check_action_feasible = rddl_state.checkActionConstraints(traj_action);



				}


				//When the actions are infeasible.
				if(! check_action_feasible){


					System.out.println("The actions are not feasible");
					all_infeasible = true;
					break;



				}









				//Check the action Constraint





				HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> store_state =  deepCopyState(rddl_state);




				store_traj_states.add(store_state);

				//Advance to Next State
				rddl_state.computeNextState(traj_action, new RandomDataGenerator());

				//Calculate Immediate Reward
				final double immediate_reward = ((Number)rddl_domain._exprReward.sample(
						new HashMap<LVAR,LCONST>(),rddl_state, rand)).doubleValue();


				store_traj_rewards.add(immediate_reward);
				store_traj_actions.add(traj_action);






				//System.out.println("Immediate Reward :"+ immediate_reward);





				rddl_state.advanceNextState();






			}



			//This is checking the trajectory turned out to be bad.
			if(check_action_feasible && all_infeasible){
				// we are overriding the pre_buffer_state values.
				int traj_id = rand1.nextInt(pre_buffer_state.size());
				store_traj_states  = pre_buffer_state.get(traj_id);
				store_traj_actions = pre_buffer_action.get(traj_id);
				store_traj_rewards = pre_buffer_reward.get(traj_id);



			}


			buffer_state.add(store_traj_states);
			buffer_action.add(store_traj_actions);
			buffer_reward.add(store_traj_rewards);



		}

















	}




	protected void checkNonLinearExpressions(final State rddl_state) throws Exception {
		//Clear the things
		not_pwl_expr.clear();



		//This is for Constraints
		ArrayList<BOOL_EXPR> constraints = new ArrayList<>();

		constraints.addAll(rddl_state._alActionPreconditions); constraints.addAll(rddl_state._alStateInvariants);


		//Need to Handle for Action Constraints and State Invariants.
//		for(BOOL_EXPR e : constraints){
//
//			System.out.println("dkfjdkjfkd");
//
//
//
//		}
//



		//This is for State_vars, Interm_vars, Observ_vars.
		ArrayList<HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>> pvar_variables = new ArrayList<>();

		pvar_variables.add(rddl_state_vars); pvar_variables.add(rddl_interm_vars); pvar_variables.add(rddl_observ_vars);



		for( final HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> map : pvar_variables ) {

			for (final PVAR_NAME p : map.keySet()) {

				Map<LVAR, LCONST> subs = new HashMap<>();
				CPF_DEF cpf = null;


				if (rddl_state_vars.containsKey(p)) {
					cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName + "'"));

				} else {
					cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName));
				}

				ArrayList<RDDL.LTERM> raw_terms =cpf._exprVarName._alTerms;
				ArrayList<EXPR> final_pwl_cond = new ArrayList<>();
				ArrayList<EXPR> final_pwl_true = new ArrayList<>();

				//This loop is for $t1, $t2, $t3..........
				for (final ArrayList<LCONST> terms : map.get(p)) {
					//This piece of code is Changed by HARISH
					//System.out.println( "CPT for " + p.toString() + terms );
					Map<LVAR,LCONST> subs1 = getSubs( cpf._exprVarName._alTerms, terms );


					//new COMP_EXPR(raw_terms.get(0),terms.get(0),"==").toString()
					if(!cpf._exprEquals.substitute(subs1,constants,objects).isPiecewiseLinear(constants,objects)){

						if(!not_pwl_expr.contains(cpf._exprEquals)){
							not_pwl_expr.add(cpf._exprEquals);

						}



						EXPR final_expr = generateDataForPWL(cpf._exprEquals.substitute(subs1,constants,objects), raw_terms);



						//This is Getting Condition.

						BOOL_EXPR conditional_state = new BOOL_CONST_EXPR(true);

						for(int i=0;i<terms.size();i++){

							BOOL_EXPR cur_cond_statement = conditional_state;

							RDDL.COMP_EXPR temp_expr = new RDDL.COMP_EXPR(raw_terms.get(i), terms.get(i), "==");

							conditional_state = new RDDL.CONN_EXPR(cur_cond_statement,temp_expr,"^");




						}


						final_pwl_true.add(final_expr);
						final_pwl_cond.add(conditional_state);









						//System.out.println("dkjfkdjfkdj");





					}



				}

				EXPR ifelse_expr = new BOOL_CONST_EXPR(true);
				if(!final_pwl_cond.isEmpty()){

					ifelse_expr = recursiveAdditionIfElse(final_pwl_cond,final_pwl_true,1);

					replace_cpf_pwl.put(p,ifelse_expr); }





			}
		}




	}

	public EXPR recursiveAdditionIfElse(List<EXPR> condition_part,List<EXPR> true_part,Integer check_first){

		EXPR cond_expr=condition_part.get(0);
		EXPR true_expr=true_part.get(0);
		if(condition_part.size()==1){

			return new RDDL.IF_EXPR(cond_expr,true_expr,new REAL_CONST_EXPR(0.0));

		}



		List<EXPR> true_sub_list = new ArrayList<>();
		List<EXPR> cond_sub_list = new ArrayList<>();
		true_sub_list = true_part.subList(1,true_part.size());
		cond_sub_list = condition_part.subList(1,condition_part.size());


		return new RDDL.IF_EXPR(cond_expr,true_expr,recursiveAdditionIfElse(cond_sub_list,true_sub_list,2));


















	}




	//This will generate Data.
	public EXPR generateDataForPWL(EXPR e, ArrayList<LTERM> raw_terms) throws Exception {



		//Getting desired format  as  a String
		ArrayList<PVAR_NAME> input_variables = new ArrayList<>();
		HashMap<Integer,ArrayList<Object>> input_array = new HashMap();

		HashMap<Integer,String> input_R_array = new HashMap<Integer, String>();
		String output_R_array = new String();
		output_R_array = "c(";







		ArrayList<Object> output_array       = new ArrayList<>();
		for(int i=0;i<buffer_state.size();i++){
			ArrayList<HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>>> state_trajectory  = buffer_state.get(i);
			ArrayList<ArrayList<PVAR_INST_DEF>> action_trajectory = buffer_action.get(i);


			for(int j=0;j<buffer_state.get(i).size();j++){

				HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> state_value = state_trajectory.get(j);
				ArrayList<PVAR_INST_DEF> action_value = action_trajectory.get(j);


				//This is a global temp variable which stores the values.m
				variables_names.clear();


				EXPR temp  = recursionSubstitution(e,state_value,action_value);
				Double val = temp.getDoubleValue(constants,objects);


				if(i==0 && j==0 && !variables_names.isEmpty()){

					input_variables.addAll(variables_names.keySet());


				}




				for(int k=0;k<input_variables.size();k++){

					if(input_array.containsKey(k)){
						input_array.get(k).add(variables_names.get(input_variables.get(k)));
						String temp_str =input_R_array.get(k);
						input_R_array.put(k,temp_str + variables_names.get(input_variables.get(k)).toString()+", ");


						//System.out.println("dkjfkdjfkdfkd");


					}
					else{
						ArrayList<Object> temp_array = new ArrayList<>();
						temp_array.add(variables_names.get(input_variables.get(k)));
						input_array.put(k,temp_array);
						String temp_str ="c(";
						input_R_array.put(k,temp_str + variables_names.get(input_variables.get(k)).toString()+", ");

					}





				}
				output_array.add(val);
				String temp_str = output_R_array;
				output_R_array = temp_str + val.toString() + ", ";







			}





		}




		//Making Sure to close the brackets.
		HashMap<PVAR_NAME,String> final_input_R_data = new HashMap<>();

		for( Map.Entry<Integer,String> entry1 : input_R_array.entrySet()){

			input_variables.get(entry1.getKey());

			String temp_str = entry1.getValue();

			temp_str = temp_str.trim();
			temp_str = temp_str.substring(0,temp_str.length()-1) + ")";

			//System.out.println("Dkfjdkfkdfkj");
			final_input_R_data.put(input_variables.get(entry1.getKey()),temp_str);




		}




		String final_output_R_data = output_R_array.trim().substring(0,output_R_array.trim().length()-1) + ")";








		//////////??????#############################################################################
		//Getting R functions.


		//Not thinking about optimizing the code, Just make it work.
		//This is for number of examples

		long start_timer = System.currentTimeMillis();
		Rengine engine = Rengine.getMainEngine();
		if(engine == null)
			engine = new Rengine(new String[] {"--vanilla"}, false, null);

		//Rengine engine  = new Rengine(new String[] {"--no-save"},false,null);
		engine.eval("library(earth)");
		String feature_format = new String();
		Integer check = 0;
		for( Map.Entry<PVAR_NAME,String> entry1 : final_input_R_data.entrySet()){
			engine.eval(entry1.getKey()._sPVarName + "<-"+ entry1.getValue());
			if(check==0){
				feature_format = entry1.getKey()._sPVarName;
				check =1;

			}
			else{
				feature_format.concat(" + "+ entry1.getKey()._sPVarName);
			}



		}

		engine.eval("target <-" + final_output_R_data );

		engine.eval("model<-earth( target ~ " + feature_format + ",nprune=2)");
		String rss_val =engine.eval("format(model$rss)").asString();
		String gcv_val =engine.eval("format(model$gcv)").asString();

		engine.eval("print(summary(model))");
		System.out.println("THE GCV VALUE :" + gcv_val);
		System.out.println("The RSS VALUE :" + rss_val);

		engine.eval("a=predict(model,1)");


		String earth_output = engine.eval("format(model,style='bf')").asString();

		long end_timer = System.currentTimeMillis();




		running_R_api = (double) end_timer - start_timer;






		//System.out.println(earth_output);


		//This will parse and give a EXPR Output.
		EXPR final_expr =parseEarthROutput(earth_output,input_variables,raw_terms);

		return(final_expr);






	}


	public EXPR parseEarthROutput(String earthOutput, ArrayList<PVAR_NAME> input_variables, ArrayList<LTERM> raw_terms) throws Exception {



		String[] list_output = earthOutput.split("\n");


		ArrayList<String> string_pvar = new ArrayList<>();
		for(int i=0;i<input_variables.size();i++){
			string_pvar.add(input_variables.get(i)._sPVarName);

		}


		HashMap<String,Double> coefficient_mapping = new HashMap<>();
		HashMap<String,EXPR> hinge_function  = new HashMap<>();

		Double bias = 0.0;
		//Parsing things with equations.
		for(int i=0;i<list_output.length;i++){
			String temp_str = list_output[i].trim();
			//System.out.println(temp_str);

			if(temp_str.equals("")){continue;}

			//This is for Bias,
			if(!(temp_str.contains("-") || temp_str.contains("+"))){
				bias =Double.parseDouble(temp_str);
				//System.out.println(bias);

			}


			//This is for : - 0.08444618 * bf1
			if(temp_str.contains("*")){
				temp_str = temp_str.replaceAll("\\s","");
				temp_str = temp_str.replaceAll("\\+","");
				String [] term_val = temp_str.split("\\*");
				NumberFormat format = NumberFormat.getInstance();

				Double coeffic = format.parse(term_val[0]).doubleValue();

				coefficient_mapping.put(term_val[1],coeffic);


			}


			//This is for : bf1  h(53.2847-rlevel)

			if(temp_str.contains("bf") && temp_str.contains("h(")){
				//System.out.println("dkjfkdfkdfj");
				String[] term_val =temp_str.split("\\s");

				String key_val = term_val[0];
				String hinge_str = term_val[2];

				hinge_str = hinge_str.replace("h(","");
				hinge_str = hinge_str.replace(")","");

				String [] hinge_values = hinge_str.split("-");

				Double real_val = 0.0;



				if(string_pvar.contains(hinge_values[0])){

					real_val = Double.parseDouble(hinge_values[1]);

					PVAR_EXPR temp_pvar_expr        = new PVAR_EXPR(hinge_values[0],raw_terms);
					REAL_CONST_EXPR temp_const_expr = new REAL_CONST_EXPR(real_val);

					RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_pvar_expr,temp_const_expr,"-");
					RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new REAL_CONST_EXPR(0.0), temp_oper_expr,"max");

					hinge_function.put(key_val,max_oper_expr);
					//System.out.println(max_oper_expr.toString());












				}
				if(string_pvar.contains(hinge_values[1])){
					real_val = Double.parseDouble(hinge_values[0]);
					PVAR_EXPR temp_pvar_expr        = new PVAR_EXPR(hinge_values[1],raw_terms);
					REAL_CONST_EXPR temp_const_expr = new REAL_CONST_EXPR(real_val);

					RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_const_expr,temp_pvar_expr,"-");
					RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new REAL_CONST_EXPR(0.0), temp_oper_expr,"max");

					hinge_function.put(key_val,max_oper_expr);
					//System.out.println(max_oper_expr.toString());




				}


			}









		}


		REAL_CONST_EXPR bias_expr = new REAL_CONST_EXPR(bias);
		RDDL.OPER_EXPR final_expr =new RDDL.OPER_EXPR(new REAL_CONST_EXPR(0.0),bias_expr,"+");


		Integer temp_count = 0;

		for(String key: coefficient_mapping.keySet()){

			Double real_value = coefficient_mapping.get(key);
			REAL_CONST_EXPR temp_real_expr= new REAL_CONST_EXPR(real_value);
			RDDL.OPER_EXPR temp_oper_expr  = new RDDL.OPER_EXPR(temp_real_expr,hinge_function.get(key),"*");

			RDDL.OPER_EXPR temp_final_expr = final_expr;
			final_expr = new RDDL.OPER_EXPR(temp_final_expr,temp_oper_expr,"+");
		}



		return(final_expr);



	}









	public EXPR recursionSubstitution(EXPR e, HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> state_value, ArrayList<PVAR_INST_DEF> action_value) throws Exception {



		if(e.isConstant(constants,objects)){

			double val        = e.getDoubleValue(constants,objects);
			EXPR real_expr    = new REAL_CONST_EXPR(val);

			return real_expr;

		}



		if(e instanceof PVAR_EXPR){



			PVAR_NAME key = ((PVAR_EXPR) e)._pName;



			if(state_value.containsKey(key)){
				HashMap<ArrayList<LCONST>,Object> t = state_value.get(((PVAR_EXPR) e)._pName);

				//Value is Available
				if(t.containsKey(((PVAR_EXPR) e)._alTerms)){
					Object val = t.get(((PVAR_EXPR) e)._alTerms);

					if(val instanceof Double){

						variables_names.put(key,val);
						return new REAL_CONST_EXPR((Double) val);



					}
					else{
						throw new EvalException("THis case not Handled");
					}




				}
				else{

					//Get the Default Value.

					Object val = rddl_state_default.get(key);

					if(val instanceof Double){
						variables_names.put(key,val);

						return new REAL_CONST_EXPR((Double) val);



					}
					else{
						throw new EvalException("THis case not Handled");
					}





				}



			}









		}





		if(e instanceof RDDL.OPER_EXPR){

			EXPR e1   = ((RDDL.OPER_EXPR) e)._e1;
			EXPR e2   = ((RDDL.OPER_EXPR) e)._e2;
			String op = ((RDDL.OPER_EXPR) e)._op;



			EXPR real_expr_1  = recursionSubstitution(e1,state_value,action_value);
			EXPR real_expr_2  = recursionSubstitution(e2,state_value,action_value);


			EXPR new_oper = new RDDL.OPER_EXPR( real_expr_1, real_expr_2,op);

			return new_oper;







		}






		return null;



	}















}