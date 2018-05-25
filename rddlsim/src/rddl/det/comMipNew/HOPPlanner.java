package rddl.det.comMipNew;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

//import org.apache.commons.math3.random.RandomDataGenerator;

import gurobi.*;
import gurobi.GRB.StringAttr;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.rosuda.JRI.Rengine;
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
import rddl.RDDL.REAL_CONST_EXPR;
import rddl.State;
import rddl.det.comMip.HOPTranslate;
import rddl.policy.Policy;
import rddl.viz.StateViz;
import util.Pair;
import util.Timer;

public class HOPPlanner extends Policy {

    private static final boolean SHOW_TIMING = false;
    protected static final boolean OUTPUT_LP_FILE = false;
    protected RandomDataGenerator rand;
    protected boolean SHOW_LEVEL_1 = true;
    protected boolean SHOW_LEVEL_2 = false;
    protected boolean SHOW_GUROBI_ADD = true;
    protected boolean SHOW_PWL_NON_PWL = false;
    protected Map<Pair<String,String>,EXPR> substitute_expression_cache = new HashMap<>();
    private static final int GRB_INFUNBDINFO = 1;
    private static final int GRB_DUALREDUCTIONS = 0;
    private static final double GRB_MIPGAP = 0.01;//0.5; //0.01;
    private static final double GRB_HEURISTIC = 0.2;
    private static final int GRB_IISMethod = 1;
    protected static final LVAR TIME_PREDICATE = new LVAR( "?time" );
    protected static final LVAR future_PREDICATE = new LVAR( "?future" );
    private static final RDDL.TYPE_NAME TIME_TYPE = new RDDL.TYPE_NAME( "time" );
    private static final boolean GRB_LOGGING_ON = false;
    protected static final boolean RECOVER_INFEASIBLE = true;
    protected RDDL rddl_obj;
    //protected int lookahead;
    protected State rddl_state;
    protected RDDL.DOMAIN rddl_domain;
    protected RDDL.INSTANCE rddl_instance;
    protected RDDL.NONFLUENTS rddl_nonfluents;
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
    private HashMap<PVAR_NAME, RDDL.TYPE_NAME> pred_type = new HashMap<>();
    protected ArrayList<ArrayList<PVAR_INST_DEF>> ret_list = new ArrayList<ArrayList<PVAR_INST_DEF>>();
    private String OUTPUT_FILE = "model.lp";
    protected HashMap<RDDL.TYPE_NAME, RDDL.OBJECTS_DEF> objects;
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
    protected HashMap<PVAR_NAME,ArrayList<RDDL.TYPE_NAME>> object_type_name  = new HashMap<>();
    protected HashMap<RDDL.TYPE_NAME,LCONST> object_val_mapping = new HashMap<>();
    protected Double rejection_prob = 0.1;
    //This stores the Expression which are not PWL.
    protected List<EXPR> not_pwl_expr = new ArrayList<>();
    protected HashMap<PVAR_NAME,Object> rddl_state_default = new HashMap<>();
    protected HashMap<PVAR_NAME,Object> variables_names = new HashMap<>();
    protected HashMap<PVAR_NAME,EXPR> replace_cpf_pwl = new HashMap<>();
    protected Double running_R_api = 0.0;
    //these are removed between invocations of getActions()
    protected List<EXPR> to_remove_expr = new ArrayList<RDDL.EXPR>();
    protected List<GRBConstr> to_remove_constr = new ArrayList<>();
    protected ArrayList<Double> objectiveValues = new ArrayList<Double>();


    public static enum FUTURE_SAMPLING{
        SAMPLE {
            @Override
            public EXPR getFuture(EXPR e, RandomDataGenerator rand, Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants, Map<RDDL.TYPE_NAME, RDDL.OBJECTS_DEF> objects )   {
                try {
                    return e.sampleDeterminization(rand,constants,objects);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }return null;

            }
        }, MEAN {
            @Override
            public EXPR getFuture(EXPR e, RandomDataGenerator rand, Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants, Map<RDDL.TYPE_NAME, RDDL.OBJECTS_DEF> objects ) {
                try {
                    return e.getMean(constants, objects);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                return null;
            }
        };

        public abstract EXPR getFuture( final EXPR e , final RandomDataGenerator rand,
                                        Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants, Map<RDDL.TYPE_NAME, RDDL.OBJECTS_DEF> objects );
    }

    protected int num_futures = 0;
    protected FUTURE_SAMPLING future_gen;
    protected Random randint = new Random();


    protected int exit_code ;


    private static final RDDL.TYPE_NAME future_TYPE = new RDDL.TYPE_NAME( "future" );
    protected ArrayList< LCONST > future_TERMS = new ArrayList<>();
    protected enum HINDSIGHT_STRATEGY {
        ROOT, ALL_ACTIONS, CONSENSUS, ROOT_CONSENSUS
    }
    protected HINDSIGHT_STRATEGY hindsight_method;
    protected HashMap< HashMap<EXPR, Object>, Integer > all_votes = new HashMap<>();




    public HOPPlanner(Integer n_futures, Integer n_lookahead, String inst_name, String gurobi_timeout,
                      String future_gen_type,String hindsight_strat, RDDL rddl_object, State s) throws Exception {

        this.num_futures = n_futures;
        this.lookahead = Integer.valueOf(n_lookahead);
        this.future_gen = FUTURE_SAMPLING.valueOf(future_gen_type);
        this.hindsight_method =  HINDSIGHT_STRATEGY.valueOf(hindsight_strat);
        rand = new RandomDataGenerator( );
        TIME_LIMIT_MINS = Double.valueOf(gurobi_timeout);
        initializeCompetitionRDDL(rddl_object,inst_name,s);

        objects = new HashMap<>( rddl_instance._hmObjects );
        if( rddl_nonfluents != null && rddl_nonfluents._hmObjects != null ){
            objects.putAll( rddl_nonfluents._hmObjects );
        }

        getConstants( );

        for( Entry<PVAR_NAME, RDDL.PVARIABLE_DEF> entry : rddl_state._hmPVariables.entrySet() ){

            final RDDL.TYPE_NAME rddl_type = entry.getValue()._typeRange;
            final char grb_type = rddl_type.equals( RDDL.TYPE_NAME.BOOL_TYPE ) ? GRB.BINARY :
                    rddl_type.equals( RDDL.TYPE_NAME.INT_TYPE ) ? GRB.INTEGER : GRB.CONTINUOUS;

            object_type_name.put(entry.getKey(), entry.getValue()._alParamTypes);
            //object_val_mapping.put(object_type_name.get(entry.getKey()),  rddl_state._hmObject2Consts.get(object_type_name.get(entry.getKey()))   );

            if(rddl_type.equals(RDDL.TYPE_NAME.REAL_TYPE)){
                ArrayList<Double> temp_dec_range =new ArrayList<Double>(){{
                    add(0.0);
                    add(50.0); }};
                value_range.put(entry.getKey(),temp_dec_range);
            }

            if(rddl_type.equals(RDDL.TYPE_NAME.BOOL_TYPE)){
                ArrayList<Boolean> temp_bool_range = new ArrayList<Boolean>(){{  add(new Boolean("true"));
                    add(new Boolean("false"));	}};
                value_range.put(entry.getKey(),temp_bool_range);
            }
            type_map.put( entry.getKey(), grb_type );

        }






    }




    protected void initializeCompetitionRDDL(final RDDL rddl_object,String instanceName,State s) throws Exception{
        //I need to think how to remove this.
        this._rddl = rddl_object;
        RDDL.NONFLUENTS nonFluents = null;
        RDDL.DOMAIN domain = null;
        RDDL.INSTANCE instance = rddl_object._tmInstanceNodes.get(instanceName);
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

        for(Map.Entry<PVAR_NAME, RDDL.PVARIABLE_DEF> entry : rddl_state._hmPVariables.entrySet()){
            PVAR_NAME temp_pvar = entry.getKey();
            if(entry.getValue() instanceof RDDL.PVARIABLE_STATE_DEF){
                Object val = ((RDDL.PVARIABLE_STATE_DEF) entry.getValue())._oDefValue;
                rddl_state_default.put(temp_pvar,val);
            }
        }

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

        constants.putAll( getConsts( all_consts ) );

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



    public HashMap< PVAR_NAME, ArrayList<ArrayList<LCONST>> > collectGroundings( final ArrayList<PVAR_NAME> preds )
            throws EvalException {
        HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> ret
                = new  HashMap<RDDL.PVAR_NAME, ArrayList<ArrayList<LCONST>>>();

        for( PVAR_NAME p : preds ){
            ArrayList<ArrayList<LCONST>> gfluents = rddl_state.generateAtoms(p);
            ret.put(p, gfluents);
            RDDL.PVARIABLE_DEF def = rddl_state._hmPVariables.get(p);
            pred_type.put( p, def._typeRange );
        }
        return ret ;
    }


    protected List<String> cleanMap( final HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> map ) {
        List<String> ret = new ArrayList<String>();
        map.forEach( (a,b) -> b.forEach( m -> ret.add( CleanFluentName( a.toString() + m ) ) ) );
        return ret;
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




    private void initializeGRB( ) throws GRBException {
        this.GRB_log = GRB_LOGGING_ON ? domain_name + "__" + instance_name + ".grb" : "";

        this.grb_env = new GRBEnv(GRB_log);
        grb_env.set( GRB.DoubleParam.TimeLimit, TIME_LIMIT_MINS*60 );
        grb_env.set( GRB.DoubleParam.MIPGap, GRB_MIPGAP );
        grb_env.set( GRB.DoubleParam.Heuristics, GRB_HEURISTIC );
        grb_env.set( GRB.IntParam.InfUnbdInfo , GRB_INFUNBDINFO );
        grb_env.set( GRB.IntParam.DualReductions, GRB_DUALREDUCTIONS );
        grb_env.set( GRB.IntParam.IISMethod, GRB_IISMethod );
        grb_env.set( GRB.IntParam.NumericFocus, 3);
        grb_env.set( GRB.IntParam.MIPFocus, 1);
        grb_env.set( GRB.DoubleParam.FeasibilityTol, 1e-9 );// Math.pow(10,  -(State._df.getMaximumFractionDigits() ) ) ); 1e-6
        grb_env.set( GRB.DoubleParam.IntFeasTol,  1e-9);//Math.pow(10,  -(State._df.getMaximumFractionDigits() ) ) ); //Math.pow( 10 , -(1+State._df.getMaximumFractionDigits() ) ) );
        grb_env.set( GRB.DoubleParam.FeasRelaxBigM, RDDL.EXPR.M);
        grb_env.set( GRB.IntParam.Threads, 1 );
        grb_env.set( GRB.IntParam.Quad, 1 );
        grb_env.set( GRB.IntParam.Method, 1 );
        grb_env.set( GRB.DoubleParam.NodefileStart, 0.5 );

        //grb_env.set(GRB.IntParam.Presolve,0);
        //grb_env.set(DoubleParam.OptimalityTol, 1e-2);
        grb_env.set(GRB.IntParam.NumericFocus, 3);
//		grb_env.set( IntParam.SolutionLimit, 5);

        System.out.println("current nodefile directly " + grb_env.get( GRB.StringParam.NodefileDir ) );

        this.static_grb_model = new GRBModel( grb_env );
        //max
        static_grb_model.set( GRB.IntAttr.ModelSense, -1);

        //create vars for state, action, interm vars over time
//		translate_time.ResumeTimer();
//		addAllVariables();
        static_grb_model.update();
//		translate_time.PauseTimer();

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
            //ArrayList<ArrayList<PVAR_INST_DEF>> ret_list = new ArrayList<ArrayList<PVAR_INST_DEF>>()
            ret_list.clear();
            for(int i =0;i<ret_expr.size();i++){
                ArrayList<PVAR_INST_DEF> ret = getRootActions(ret_expr.get(i),s,i);
                ret_list.add(ret);
            }

            //System.out.println("####################################################");
            System.out.println("These are Root Actions:" + ret_list.get(0).toString());
            ArrayList<PVAR_INST_DEF> returning_action = ret_list.get(0);
            if(exit_code.equals(3)){
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


            cleanUp(static_grb_model);


            return returning_action;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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



    public Pair<ArrayList<Map< EXPR, Double >>,Integer> doPlan(HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> subs ,
                                                               final boolean recover ) throws Exception {
        ArrayList<Map< EXPR, Double >> ret_obj = new ArrayList<Map< EXPR, Double >>();
        System.out.println("------------------------------------------------this is doPlan (HOPTranslate.java Overrided)-------");
        System.out.println("--------------Translating CPTs with random Generated Futures-------------");
        //This is commented by HARISH, FittedHOPTranslate does not generate futures

        translateCPTs( subs, static_grb_model );
        System.out.println("--------------Initial State-------------");
        translateInitialState( static_grb_model, subs );
        //This adds the ROOT POLICY CONSTRAINTS
        if(hindsight_method.toString()=="ROOT_CONSENSUS"){
            addRootPolicyConstraints(static_grb_model);
        }

        exit_code = -1;
        try{
            System.out.println("----------------------------------------------THIS IS WERE STARTING OPTIMIZATION(HOPTranslate.java)!!!");
//			if(gurobi_initialization!=null)
//				setActionVariables(gurobi_initialization,static_grb_model)
            exit_code = goOptimize( static_grb_model);

        }

        catch( GRBException exc ){
            int error_code = exc.getErrorCode();
            System.out.println("Error code : " + error_code );
            if( recover ){//error_code == GRB.ERROR_OUT_OF_MEMORY && recover ){
//				cleanUp(static_grb_model);
                handleOOM( static_grb_model );
                return doPlan( subs, RECOVER_INFEASIBLE );//can cause infinite loop if set to true
            }else{
                System.out.println("The Solution is Infeasible");
                throw exc;
            }
        }
        finally{
            System.out.println("Exit code " + exit_code );
        }

        Map< EXPR, Double > ret  = outputResults( static_grb_model);
        //Map< EXPR, Double > ret1 = outputAllResults(static_grb_model);
        ret_obj.add(ret);
        if( OUTPUT_LP_FILE ) {
            outputLPFile( static_grb_model );
        }
        //This prints the model Summary
        modelSummary( static_grb_model );
        long starttime1 = System.currentTimeMillis();
        System.out.println("---------> Cleaning up the Gurobi Model, Removing Constraints");
        //Double root_obj = static_grb_model.get(DoubleAttr.ObjVal);
        //objectiveValues.add(root_obj);

        //This is done only to compare Root and Consensus policy Objective. Added by Harish.
        if(hindsight_method.toString()=="ROOT_CONSENSUS"){

            removeRootPolicyConstraints(static_grb_model);
            static_grb_model.reset();
            exit_code = goOptimize( static_grb_model);

            Map< EXPR, Double >  outConsensus  = outputResults( static_grb_model);
            Double consensus_obj = static_grb_model.get(GRB.DoubleAttr.ObjVal);
            objectiveValues.add(consensus_obj);
            //System.out.println(outConsensus.toString());
            ret_obj.add(outConsensus);

        }
        cleanUp( static_grb_model );
        long endtime1 = System.currentTimeMillis();
        System.out.println("Gurobi Cleanup time = " +  (endtime1 - starttime1) );
        return new Pair<>(ret_obj,exit_code);
    }



    protected void addExtraPredicates() {
        System.out.println("-----This is addExtraPredicates (Overrided)----");
        System.out.println("Here we are adding Futures------> Adding Extra Predicates");
        removeExtraPredicates();
        for( int t = 0 ; t < lookahead; ++t ){
            //[$time0,$time1,$time2,$time3]
            TIME_TERMS.add( new RDDL.OBJECT_VAL( "time" + t ) );
        }
        objects.put( TIME_TYPE,  new RDDL.OBJECTS_DEF(  TIME_TYPE._STypeName, TIME_TERMS ) );

        if( future_gen.equals( HOPTranslate.FUTURE_SAMPLING.MEAN ) ){
            num_futures = 1;
        }
        for( int f = 0 ; f < this.num_futures; ++f ){
            future_TERMS.add( new RDDL.OBJECT_VAL( "future" + f ) );
        }
        objects.put( future_TYPE,  new RDDL.OBJECTS_DEF(  future_TYPE._STypeName, future_TERMS ) );
    }


    protected void removeExtraPredicates() {
        System.out.println("-----This is removeExtraPredicates (Overrided)----");

        TIME_TERMS.clear();
        objects.remove( TIME_TYPE );
        if( future_TERMS != null ){
            future_TERMS.clear();
        }
        if( objects != null ){
            objects.remove( future_TYPE );
        }
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




    protected void addAllVariables( ) throws Exception{
        System.out.println("-----This is addAllVaribles (Overrided)----");
        HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> src = new HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>();
        src.putAll( rddl_state_vars ); src.putAll( rddl_action_vars ); src.putAll( rddl_interm_vars ); src.putAll( rddl_observ_vars );

        src.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>> >() {
            @Override
            public void accept(PVAR_NAME pvar, ArrayList<ArrayList<LCONST>> u) {
                u.parallelStream().forEach( new Consumer<ArrayList<LCONST>>() {
                    @Override
                    public void accept(ArrayList<LCONST> terms) {
                        //System.out.print(pvar.toString());
                        //EXPR pvar_expr = null;
                        try{
                            EXPR pvar_expr = new PVAR_EXPR(pvar._sPVarName, terms ).addTerm(TIME_PREDICATE, constants, objects)
                                    .addTerm( future_PREDICATE, constants, objects );



                            //System.out.println("HERE IS THE ERROR");

                            TIME_TERMS.parallelStream().forEach( new Consumer<LCONST>() {
                                @Override
                                public void accept(LCONST time_term ) {
                                    try{
                                        EXPR this_t = pvar_expr.substitute( Collections.singletonMap( TIME_PREDICATE, time_term),
                                                constants, objects);

                                        future_TERMS.parallelStream().forEach( new Consumer< LCONST >() {
                                            @Override
                                            public void accept(LCONST future_term) {
                                                //System.out.println(this_t.toString());
                                                try{

                                                    EXPR this_tf =this_t.substitute( Collections.singletonMap( future_PREDICATE, future_term ), constants, objects );


                                                    synchronized( static_grb_model ){
                                                        //System.out.println(this_tf.toString());

                                                        try {
                                                            GRBVar gvar = this_tf.getGRBConstr( GRB.EQUAL, static_grb_model, constants, objects, type_map);
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                        }


                                                        //just remember this is commented by HARISH

//											try {
//												System.out.println("Adding var " + gvar.get(StringAttr.VarName) + " " + this_tf );
//											} catch (GRBException e) {
//												e.printStackTrace();
//												//System.exit(1);
//											}
                                                        saved_expr.add( this_tf );
                                                    }

                                                }
                                                catch (Exception e){
                                                    e.printStackTrace();

                                                }

                                            }
                                        });
                                    } catch (Exception e) {e.printStackTrace(); }

                                }
                            });
                        } catch (Exception e) {e.printStackTrace(); }
                    }
                });
            }
        });



    }




    protected void prepareModel( ) throws Exception{
        System.out.println("This is the first time (FittedEmergencyDo   mainHOP.java");
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
                        PVAR_NAME p = entry.getKey();
                        final String pvarName = entry.getKey()._sPVarName;
                        //System.out.println("Dunno  nn   What's happening");
                        //these are sampled from data in TranslateCPTs()
//                        if( isStochastic(pvarName) || replace_cpf_pwl.containsKey(entry.getKey())){
//                            //This is Changed by HARISH.
//                            //System.out.println("Skipping " + pvarName);
//                            return;
//                        }

                        CPF_DEF cpf = null;
                        if (rddl_state_vars.containsKey(p)) {
                            cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName + "'"));
                        } else {
                            cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName));
                        }


                        if(!cpf._exprEquals._bDet || replace_cpf_pwl.containsKey(entry.getKey())){
                            System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<This is stochastic Pvar ---------->>>>>>>>>> : " + p._sPVarName);
                            return;
                        }
                        //these are deterministic/known world model
                        entry.getValue().stream().forEach( new Consumer< ArrayList<LCONST> >() {
                            @Override
                            public void accept(ArrayList<LCONST> terms) {
                                try {
                                    PVAR_NAME p = entry.getKey();
                                    //This is changed by HARISH
                                    //System.out.println(p + " " + terms);

                                    CPF_DEF cpf = null;
                                    if (rddl_state_vars.containsKey(p)) {
                                        cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName + "'"));
                                    } else {
                                        cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName));
                                    }
                                    Map<LVAR, LCONST> subs = getSubs(cpf._exprVarName._alTerms, terms);
                                    EXPR new_lhs_stationary = null;
                                    EXPR new_rhs_stationary = null;

                                    //old implementation.
                                    //EXPR new_lhs_stationary = cpf._exprVarName.substitute(subs, constants, objects);
                                    //EXPR new_rhs_stationary = cpf._exprEquals.substitute(subs, constants, objects);
                                    if(TIME_FUTURE_CACHE_USE){

                                        EXPR e_lhs = cpf._exprVarName;
                                        EXPR e_rhs = cpf._exprEquals;
                                        Pair<String,String> key_lhs = new Pair(e_lhs.toString(),subs.toString());
                                        //Substitute_expression_cache --> stores substituted_expressions --> Key is a Pair<Expression,subs> --> value is substitutued Expression.
                                        if(substitute_expression_cache.containsKey(key_lhs)){
                                            new_lhs_stationary = substitute_expression_cache.get(key_lhs);
                                        }else{
                                            new_lhs_stationary = e_lhs.substitute(subs, constants, objects);
                                            substitute_expression_cache.put(key_lhs,new_lhs_stationary);
                                        }

                                        Pair<String,String> key_rhs = new Pair(e_rhs.toString(),subs.toString());
                                        if(substitute_expression_cache.containsKey(key_rhs)){
                                            new_rhs_stationary = substitute_expression_cache.get(key_rhs);
                                        }else{
                                            new_rhs_stationary = e_rhs.substitute(subs, constants, objects);
                                            substitute_expression_cache.put(key_rhs,new_lhs_stationary);
                                        }
                                    }else{
                                         new_lhs_stationary = cpf._exprVarName.substitute(subs, constants, objects);
                                         new_rhs_stationary = cpf._exprEquals.substitute(subs, constants, objects);

                                    }


                                    EXPR lhs_with_tf = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects);
                                    EXPR rhs_with_tf = new_rhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects);

                                    time_terms_indices.stream().forEach(new Consumer<Integer>() {
                                        @Override
                                        public void accept(Integer time_term_index) {
                                            try {
                                                EXPR lhs_with_f_temp = null;
                                                if (rddl_state_vars.containsKey(p)) {
                                                    if (time_term_index == lookahead - 1) {
                                                        return;
                                                    }
                                                    lhs_with_f_temp = lhs_with_tf.substitute(
                                                            Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(time_term_index + 1)), constants, objects);
                                                } else {
                                                    lhs_with_f_temp = lhs_with_tf.substitute(
                                                            Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(time_term_index)), constants, objects);
                                                }
                                                final EXPR lhs_with_f = lhs_with_f_temp;
                                                final EXPR rhs_with_f = rhs_with_tf.substitute(
                                                        Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(time_term_index)), constants, objects);

                                                future_terms_indices.stream().forEach(new Consumer<Integer>() {

                                                    public void accept(Integer future_term_index) {
                                                        try {
                                                            EXPR lhs = lhs_with_f.substitute(
                                                                    Collections.singletonMap(future_PREDICATE, future_TERMS.get(future_term_index)), constants, objects);
                                                            EXPR rhs = rhs_with_f.substitute(
                                                                    Collections.singletonMap(future_PREDICATE, future_TERMS.get(future_term_index)), constants, objects);
                                                            EXPR lhs_future = future_gen.getFuture(lhs, rand, constants, objects);
                                                            EXPR rhs_future = future_gen.getFuture(rhs, rand, constants, objects);

                                                            synchronized (static_grb_model) {

                                                                //System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
                                                                GRBVar lhs_var = lhs_future.getGRBConstr(
                                                                        GRB.EQUAL, static_grb_model, constants, objects, type_map);


                                                                if (SHOW_GUROBI_ADD)
                                                                    System.out.println(rhs_future.toString());

                                                                GRBVar rhs_var = rhs_future.getGRBConstr(
                                                                        GRB.EQUAL, static_grb_model, constants, objects, type_map);

                                                                //System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
                                                                final String nam = RDDL.EXPR.getGRBName(lhs_future) + "=" + RDDL.EXPR.getGRBName(rhs_future);
//														System.out.println(nam);;
                                                                GRBConstr this_constr
                                                                        = static_grb_model.addConstr(lhs_var, GRB.EQUAL, rhs_var, nam);
                                                                saved_constr.add(this_constr);
                                                                saved_expr.add(lhs_future);
                                                                saved_expr.add(rhs_future);

                                                            }
                                                        } catch (GRBException e) {
                                                            e.printStackTrace();
                                                            //System.exit(1);
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                });

                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                } catch (Exception e){e.printStackTrace();}

                            }
                        } );
                    }

                });
            }
        });

        System.out.println("--------------Translating Reward-------------");
        translateReward( static_grb_model );

    }




    protected void translateConstraints(final GRBModel grb_model) throws Exception {

        GRBExpr old_obj = grb_model.getObjective();
//		translateMaxNonDef( );

        System.out.println("--------------Translating Constraints(Overrided) -------------");

        ArrayList<RDDL.BOOL_EXPR> constraints = new ArrayList<RDDL.BOOL_EXPR>();
        //domain constraints
        constraints.addAll( rddl_state._alActionPreconditions ); constraints.addAll( rddl_state._alStateInvariants );

        constraints.stream().forEach( new Consumer<RDDL.BOOL_EXPR>() {
            @Override
            public void accept(RDDL.BOOL_EXPR e) {
                // COMMENT SHOULD BE UNCOMMENTED NOTE CHANGED BY HARISH.
                //System.out.println( "Translating Constraint " + e );
                try{
                    EXPR temp_expr = null;
                    EXPR final_expr  = null;
                    if(TIME_FUTURE_CACHE_USE){
                        Pair<String,String> key = new Pair(e.toString(),Collections.EMPTY_MAP.toString());
                        //Substitute_expression_cache --> stores substituted_expressions --> Key is a Pair<Expression,subs> --> value is substitutued Expression.
                        if(substitute_expression_cache.containsKey(key)){
                            temp_expr = substitute_expression_cache.get(key);
                        }else{
                            //((RDDL.CONN_EXPR) ((RDDL.QUANT_EXPR) e)._expr)._alSubNodes.get(1).substitute(Collections.EMPTY_MAP,constants,objects);
                            temp_expr = e.substitute(Collections.EMPTY_MAP, constants, objects);
                            substitute_expression_cache.put(key,temp_expr);
                        }
                        assert temp_expr!=null;
                        final_expr = temp_expr.addTerm(TIME_PREDICATE, constants, objects)
                                .addTerm(future_PREDICATE, constants, objects);

                    }else{
                        final_expr = e.substitute(Collections.EMPTY_MAP,constants,objects)
                                .addTerm(TIME_PREDICATE,constants,objects)
                                .addTerm(future_PREDICATE,constants,objects);

                    }
                    final EXPR non_stationary_e = final_expr;




                    TIME_TERMS.stream().forEach(new Consumer<LCONST>() {
                        @Override
                        public void accept(LCONST time_term) {
                            future_TERMS.parallelStream().forEach(new Consumer<LCONST>() {
                                @Override
                                public void accept(LCONST future_term) {

                                    try {

                                        EXPR this_tf = null;

                                        if(TIME_FUTURE_CACHE_USE){
                                            EXPR time_sub_expr   = null;
                                            EXPR future_sub_expr = null;
                                            Map<LVAR,LCONST> time_sub   = Collections.singletonMap(TIME_PREDICATE,time_term);
                                            Map<LVAR,LCONST> future_sub = Collections.singletonMap(future_PREDICATE,future_term);
                                            Pair<String,String> time_key = new Pair(non_stationary_e.toString(),time_sub.toString());
                                            if(substitute_expression_cache.containsKey(time_key)){
                                                time_sub_expr = substitute_expression_cache.get(time_key);
                                            }else{
                                                time_sub_expr = non_stationary_e.substitute(time_sub, constants, objects);
                                                substitute_expression_cache.put(time_key,time_sub_expr);
                                            }

                                            assert time_sub_expr!=null;
                                            Pair<String,String> future_key = new Pair(time_sub_expr.toString(),future_sub.toString());
                                            if(substitute_expression_cache.containsKey(future_key)){
                                                future_sub_expr = substitute_expression_cache.get(future_key);

                                            }else{
                                                future_sub_expr = time_sub_expr.substitute(future_sub,constants,objects);
                                                substitute_expression_cache.put(future_key,future_sub_expr);
                                            }
                                            assert future_sub_expr!=null;
                                            this_tf = future_sub_expr;

                                        }else{
                                            this_tf = non_stationary_e
                                                    .substitute(Collections.singletonMap(TIME_PREDICATE, time_term), constants, objects)
                                                    .substitute(Collections.singletonMap(future_PREDICATE, future_term), constants, objects);


                                        }
                                        synchronized (grb_model) {

                                            if (SHOW_GUROBI_ADD)
                                                System.out.println(this_tf);
                                            GRBVar constrained_var = this_tf.getGRBConstr(GRB.EQUAL, grb_model, constants, objects, type_map);

                                            String nam = RDDL.EXPR.getGRBName(this_tf);
                                            GRBConstr this_constr = grb_model.addConstr(constrained_var, GRB.EQUAL, 1, nam);
                                            saved_expr.add(this_tf);
                                            saved_constr.add(this_constr);
                                            //saved_vars.add( constrained_var );

                                        }
                                    } catch (GRBException e) {
                                        e.printStackTrace();
                                    } catch (Exception e1) {
                                        e1.printStackTrace();
                                    }


                                }
                            });
                        }
                    });
                }catch (Exception e1){e1.printStackTrace();}


            }
        });


//		for( final EXPR e : constraints ){
//			System.out.println( "Translating Constraint " + e );
//
////			substitution expands quantifiers
////			better to substitute for time first
//			EXPR non_stationary_e = e.substitute( Collections.EMPTY_MAP, constants, objects)
//					.addTerm(TIME_PREDICATE, constants, objects )
//					.addTerm(future_PREDICATE, constants, objects);
////			this works. but is expensive
////			QUANT_EXPR all_time = new QUANT_EXPR( QUANT_EXPR.FORALL,
////					new ArrayList<>( Collections.singletonList( new LTYPED_VAR( TIME_PREDICATE._sVarName,  TIME_TYPE._STypeName ) ) )
////							, non_stationary_e );
//
//
//			//domain constraints are true for all times and futures
//			for( int t = 0 ; t < TIME_TERMS.size(); ++t ){
//				for( int future_id = 0 ; future_id < num_futures; ++future_id ){
//					EXPR this_tf = non_stationary_e
//							.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(t) ), constants, objects )
//						 	.substitute( Collections.singletonMap( future_PREDICATE, future_TERMS.get(future_id) ), constants, objects );
//
//					GRBVar constrained_var = this_tf.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//					grb_model.addConstr( constrained_var, GRB.EQUAL, 1, "constraint=1_"+e.toString()+"_t"+t+"_f" + future_id );
//
//					saved_expr.add( this_tf ); saved_vars.add( constrained_var );
//
//					if( future_gen.equals( FUTURE_SAMPLING.MEAN ) ){
//						break;
//					}
//				}
//			}
//		}
        System.out.println("Checking hindsight_method");
        //hindishgt constraint
        getHindSightConstraintExpr(hindsight_method).parallelStream().forEach( new Consumer<RDDL.BOOL_EXPR>() {

            @Override
            public void accept( RDDL.BOOL_EXPR t) {
                synchronized( grb_model ){
                    GRBVar gvar = null;
                    try {
                        gvar = t.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        GRBConstr this_constr = grb_model.addConstr( gvar, GRB.EQUAL, 1, RDDL.EXPR.getGRBName(t) );
                        saved_expr.add( t ); // saved_vars.add( gvar );
                        root_policy_expr.add(t);
                        saved_constr.add(this_constr);
                        root_policy_constr.add(this_constr);
                    } catch (GRBException e) {
                        e.printStackTrace();
                        //System.exit(1);
                    }
                }
            }
        });

        //startring actionv ars at 0.0
//		rddl_action_vars.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>>() {
//			@Override
//			public void accept(PVAR_NAME pvar, ArrayList<ArrayList<LCONST>> u) {
//				u.forEach( new Consumer<ArrayList<LCONST>>() {
//					@Override
//					public void accept(ArrayList<LCONST> terms) {
//						TIME_TERMS.forEach( new Consumer<LCONST>() {
//							public void accept(LCONST time_term) {
//								future_TERMS.forEach( new Consumer<LCONST>(){
//									@Override
//									public void accept(LCONST future_term) {
//										EXPR this_expr = new PVAR_EXPR( pvar._sPVarName, terms )
//											.addTerm( TIME_PREDICATE, constants, objects )
//											.substitute( Collections.singletonMap( TIME_PREDICATE, time_term), constants, objects)
//											.addTerm( future_PREDICATE, constants, objects )
//											.substitute( Collections.singletonMap( future_PREDICATE, future_term), constants, objects);
//										final GRBVar this_var = EXPR.getGRBVar(this_expr, grb_model, constants, objects, type_map);
//										try {
//											this_var.set( DoubleAttr.Start, 0.0 );
//										} catch (GRBException e) {
//											e.printStackTrace();
//											//System.exit(1);
//										}
//									}
//								});
//							};
//						});
//					}
//				});
//			}
//		});

        grb_model.setObjective(old_obj);
        grb_model.update();

    }





    protected ArrayList<RDDL.BOOL_EXPR> getHindSightConstraintExpr(HINDSIGHT_STRATEGY hindsight_method )  {
        ArrayList<RDDL.BOOL_EXPR> ret = new ArrayList<RDDL.BOOL_EXPR>();
        System.out.println("-----Getting HindSightConstraintExpr (HOPTranslate.java)-------");
        //the only way to keep this working correctly with expanding enum HINDSIGHT_STRATEGY
        switch( hindsight_method ){
            case ALL_ACTIONS :
                rddl_action_vars.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>> () {
                    public void accept( PVAR_NAME pvar , ArrayList<ArrayList<LCONST>> u) {
                        u.forEach( new Consumer< ArrayList<LCONST>>() {
                            @Override
                            public void accept(ArrayList<LCONST> terms) {
                                try {
                                    PVAR_EXPR pvar_expr = new PVAR_EXPR(pvar._sPVarName, terms);
                                    EXPR with_tf = pvar_expr.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects);

                                    for (final LCONST time : TIME_TERMS) {
                                        EXPR this_t = with_tf.substitute(Collections.singletonMap(TIME_PREDICATE, time), constants, objects);
                                        EXPR ref_expr = this_t.substitute(Collections.singletonMap(future_PREDICATE, future_TERMS.get(0)), constants, objects);

                                        for (final LCONST future : future_TERMS) {
                                            EXPR addedd = this_t.substitute(Collections.singletonMap(future_PREDICATE, future), constants, objects);
                                            try {
                                                ret.add(new RDDL.COMP_EXPR(ref_expr, addedd, RDDL.COMP_EXPR.EQUAL));
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }

                                    }
                                } catch (Exception e){e.printStackTrace();}
                            }
                        });
                    };
                });
                break;
            case CONSENSUS :
                //nothing to add
                break;
            case ROOT :
                System.out.println("-----> This is for ROOT CASE");
                rddl_action_vars.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>> () {
                    public void accept( PVAR_NAME pvar , ArrayList<ArrayList<LCONST>> u) {
                        u.forEach( new Consumer< ArrayList<LCONST>>() {
                            @Override
                            public void accept(ArrayList<LCONST> terms) {
                                try {
                                    PVAR_EXPR pvar_expr = new PVAR_EXPR(pvar._sPVarName, terms);
                                    EXPR with_tf = pvar_expr.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects);

                                    EXPR this_t = with_tf.substitute(Collections.singletonMap(TIME_PREDICATE,
                                            TIME_TERMS.get(0)), constants, objects);
                                    EXPR ref_expr = this_t.substitute(Collections.singletonMap(future_PREDICATE,
                                            future_TERMS.get(0)), constants, objects);

                                    for (final LCONST future : future_TERMS) {

                                        EXPR addedd = this_t.substitute(Collections.singletonMap(future_PREDICATE, future), constants, objects);
                                        if (SHOW_GUROBI_ADD)
                                            System.out.println(addedd.toString());
                                        try {
                                            ret.add(new RDDL.COMP_EXPR(ref_expr, addedd, RDDL.COMP_EXPR.EQUAL));
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }

                                }catch (Exception e){e.printStackTrace();}
                            }
                        });
                    };
                });

                break;


            case ROOT_CONSENSUS://This is Added by harish for getting
                System.out.println("-----> This is for ROOT_CONSENSUS CASE");
                rddl_action_vars.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>> () {
                    public void accept( PVAR_NAME pvar , ArrayList<ArrayList<LCONST>> u) {
                        u.forEach( new Consumer< ArrayList<LCONST>>() {
                            @Override
                            public void accept(ArrayList<LCONST> terms) {
                                try {
                                    PVAR_EXPR pvar_expr = new PVAR_EXPR(pvar._sPVarName, terms);
                                    EXPR with_tf = pvar_expr.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects);

                                    EXPR this_t = with_tf.substitute(Collections.singletonMap(TIME_PREDICATE,
                                            TIME_TERMS.get(0)), constants, objects);
                                    EXPR ref_expr = this_t.substitute(Collections.singletonMap(future_PREDICATE,
                                            future_TERMS.get(0)), constants, objects);

                                    for (final LCONST future : future_TERMS) {

                                        EXPR addedd = this_t.substitute(Collections.singletonMap(future_PREDICATE, future), constants, objects);
                                        System.out.println(addedd.toString());
                                        try {
                                            ret.add(new RDDL.COMP_EXPR(ref_expr, addedd, RDDL.COMP_EXPR.EQUAL));
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                catch (Exception e){e.printStackTrace();}

                            }
                        });
                    };
                });
                break;



            default: try{
                throw new Exception("Unknown hindsight strategy " + hindsight_method );
            }	catch( Exception exc ){
                exc.printStackTrace();
                //System.exit(1);
            }
        }

        System.out.println("----This is end of HindSightConstraintExpr -------");
        return ret;
    }


    protected void translateReward( final GRBModel grb_model ) throws Exception {
        System.out.println("---- This is translate Reward (Overrided) --------");
        grb_model.setObjective( new GRBLinExpr() );
        grb_model.update();

        final EXPR stationary = rddl_state._reward;
        final EXPR stationary_clear = stationary.substitute( Collections.EMPTY_MAP, constants, objects);
        final EXPR non_stationary = stationary_clear.addTerm( TIME_PREDICATE , constants, objects )
                .addTerm( future_PREDICATE, constants, objects);

        GRBLinExpr all_sum = new GRBLinExpr();
        //This piece of code is changed by HARISH
        //System.out.println(non_stationary);

        future_TERMS.parallelStream().forEach( new Consumer<LCONST>() {
            @Override
            public void accept(LCONST future_term) {
                TIME_TERMS.parallelStream().forEach( new Consumer<LCONST>() {
                    @Override
                    public void accept(LCONST time_term) {
                        try {
                            final EXPR subs_tf = non_stationary.substitute(Collections.singletonMap(TIME_PREDICATE, time_term),
                                    constants, objects)
                                    .substitute(Collections.singletonMap(future_PREDICATE, future_term), constants, objects);
                            //System.out.println( subs_tf );//"Reward_" + time_term + "_" + future_term );

                            synchronized (grb_model) {

                                GRBVar this_future_var = null;

                                this_future_var = subs_tf.getGRBConstr(GRB.EQUAL, grb_model, constants, objects, type_map);

                                saved_expr.add(subs_tf);
                                //System.out.println(saved_expr);
//							saved_vars.add( this_future_var );
                                //IDEA : weightby probability of trajecotry of futures
                                //what is the probability of a determinization
                                all_sum.addTerm(1.0d / num_futures, this_future_var);
                                //System.out.println(all_sum);
                            }

                        }
                        catch (Exception e){
                            e.printStackTrace();

                        }

                    }
                });
            }
        });

        grb_model.setObjective(all_sum);
        grb_model.update();

    }




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
                                try{
                                PVAR_NAME p = entry.getKey();

//                                if (!isStochastic(p._sPVarName) && !replace_cpf_pwl.containsKey(p)) {
//                                    System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<This is not stochastic Pvar ---------->>>>>>>>>> : " + p._sPVarName);
//                                    return;
//                                }





                                //System.out.println("These variables are translated : "+ p._sPVarName);
                                CPF_DEF cpf = null;
                                if (rddl_state_vars.containsKey(p)) {
                                    cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName + "'"));
                                } else {
                                    cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName));
                                }


                                if(cpf._exprEquals._bDet && !replace_cpf_pwl.containsKey(p)){
                                    System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<This is not stochastic Pvar ---------->>>>>>>>>> : " + p._sPVarName);
                                    return;
                                }

                                Map<LVAR, LCONST> subs = getSubs(cpf._exprVarName._alTerms, terms);
                                EXPR new_lhs_stationary = cpf._exprVarName.substitute(subs, constants, objects);


                                EXPR new_rhs_stationary = cpf._exprEquals.substitute(subs, constants, objects);

                                if (replace_cpf_pwl.containsKey(p)) {

                                    new_rhs_stationary = replace_cpf_pwl.get(p).substitute(subs, constants, objects);

                                }

//								System.out.println(new_lhs_stationary );//.+ " " + new_rhs_stationary );
                                //This is commented by HARISH.
                                //System.out.println(new_lhs_stationary + " " + new_rhs_stationary );

                                EXPR lhs_with_tf = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
                                        .addTerm(future_PREDICATE, constants, objects);
                                EXPR rhs_with_tf = new_rhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
                                        .addTerm(future_PREDICATE, constants, objects);
                                System.out.println(lhs_with_tf + " " + rhs_with_tf);

                                time_terms_indices.stream().forEach(new Consumer<Integer>() {
                                    @Override
                                    public void accept(Integer time_term_index) {
                                        try {
                                            EXPR lhs_with_f_temp = null;
                                            if (rddl_state_vars.containsKey(p)) {
                                                if (time_term_index == lookahead - 1) {
                                                    return;
                                                }

                                                lhs_with_f_temp = lhs_with_tf.substitute(
                                                        Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(time_term_index + 1)), constants, objects);
                                            } else {
                                                lhs_with_f_temp = lhs_with_tf.substitute(
                                                        Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(time_term_index)), constants, objects);
                                            }
                                            final EXPR lhs_with_f = lhs_with_f_temp;

                                            final EXPR rhs_with_f = rhs_with_tf.substitute(
                                                    Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(time_term_index)), constants, objects);

                                            future_terms_indices.stream().forEach(
                                                    new Consumer<Integer>() {
                                                        public void accept(Integer future_term_index) {
                                                            try {
                                                                EXPR lhs = lhs_with_f.substitute(
                                                                        Collections.singletonMap(future_PREDICATE, future_TERMS.get(future_term_index)), constants, objects);
                                                                EXPR rhs = rhs_with_f.substitute(
                                                                        Collections.singletonMap(future_PREDICATE, future_TERMS.get(future_term_index)), constants, objects);
                                                                //System.out.println("Something related to future is happening");
                                                                EXPR lhs_future = future_gen.getFuture(lhs, rand, constants,objects);
                                                                EXPR rhs_future = future_gen.getFuture(rhs, rand, constants, objects);
                                                                //System.out.println("lhs_future:"+ lhs_future+ "  rhs_future:"+ rhs_future );
//														synchronized ( lhs_future ) {
//															synchronized ( rhs_future ) {
                                                                synchronized (grb_model) {

                                                                    try {
                                                                        GRBVar lhs_var = lhs_future.getGRBConstr(
                                                                                GRB.EQUAL, grb_model, constants, objects, type_map);

                                                                        GRBVar rhs_var = rhs_future.getGRBConstr(
                                                                                GRB.EQUAL, grb_model, constants, objects, type_map);

                                                                        //System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
                                                                        final String nam = RDDL.EXPR.getGRBName(lhs_future) + "=" + RDDL.EXPR.getGRBName(rhs_future);
//																		System.out.println(nam);;

                                                                        GRBConstr this_constr
                                                                                = grb_model.addConstr(lhs_var, GRB.EQUAL, rhs_var, nam);
                                                                        to_remove_constr.add(this_constr);
                                                                        to_remove_expr.add(lhs_future);
                                                                        to_remove_expr.add(rhs_future);

                                                                    } catch (GRBException e) {
                                                                        e.printStackTrace();
                                                                        //System.exit(1);
                                                                    } catch (Exception e) {
                                                                        e.printStackTrace();
                                                                    }

                                                                }

                                                            } catch (Exception e) {
                                                                e.printStackTrace();
                                                            }
//															}
//														}

                                                        }
                                                    });
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            } catch (Exception e){e.printStackTrace();}
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

    }







    protected void translateInitialState( final GRBModel grb_model,
                                          HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> subs )
            throws GRBException {
        System.out.println("----Translating Initial State (HOPTRanslate.java)------");

        GRBExpr old_obj = grb_model.getObjective();

        for( final PVAR_NAME p : rddl_state_vars.keySet() ){
            for( final ArrayList<LCONST> terms : rddl_state_vars.get( p ) ){
                try{

                    Object rhs = null;
                    if( subs.containsKey( p ) && subs.get( p ).containsKey( terms ) ){
                        rhs = subs.get(p).get( terms );
                    }else{
                        rhs = rddl_state.getDefaultValue(p);
                    }

                    EXPR rhs_expr = null;
                    if( rhs instanceof Boolean ){
                        rhs_expr = new BOOL_CONST_EXPR( (boolean) rhs );
                    }else if( rhs instanceof Double ){
                        rhs_expr = new REAL_CONST_EXPR( (double)rhs );
                    }else if( rhs instanceof Integer ){
                        rhs_expr = new INT_CONST_EXPR( (int)rhs );
                    }
                    GRBVar rhs_var = null;

                    rhs_var = rhs_expr.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map );

                    PVAR_EXPR stationary_pvar_expr = new PVAR_EXPR( p._sPVarName, terms );
                    EXPR non_stationary_pvar_expr = stationary_pvar_expr
                            .addTerm( TIME_PREDICATE, constants, objects )
                            .addTerm( future_PREDICATE, constants, objects);
                    //This Piece of code is changed by HARISH
                    //System.out.println( p+" "+terms );
                    //System.out.println("Thinking about future");
                    for( int future_id = 0 ; future_id < num_futures; ++future_id ){

                        try {
                            EXPR this_future_init_state = non_stationary_pvar_expr
                                    .substitute(Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(0)), constants, objects)
                                    .substitute(Collections.singletonMap(future_PREDICATE, future_TERMS.get(future_id)), constants, objects);

                            GRBVar lhs_var = null;

                            lhs_var = this_future_init_state.getGRBConstr(GRB.EQUAL, grb_model, constants, objects, type_map);


                            final String nam = RDDL.EXPR.getGRBName(this_future_init_state) +
                                    "=" + RDDL.EXPR.getGRBName(rhs_expr);

                            GRBConstr this_constr = grb_model.addConstr(lhs_var, GRB.EQUAL, rhs_var, nam);
                            //This Piece of Code is changed by HARISH.
                            //System.out.println( this_future_init_state+" "+rhs_expr );
//					System.out.println( nam );

//					System.out.println( this_constr.get(StringAttr.ConstrName) );
//					saved_vars.add( lhs_var ); saved_expr.add( this_future_init_state );
//					saved_vars.add( rhs_var ); saved_expr.add( rhs_expr );

                            to_remove_expr.add(this_future_init_state);
                            to_remove_constr.add(this_constr);
                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }


                    }

                } catch (Exception e) { e.printStackTrace(); }

            }
        }

        grb_model.setObjective(old_obj);
        grb_model.update();
    }






    protected ArrayList<PVAR_INST_DEF> getRootActions(Map<EXPR, Double> ret_expr, State s, int decision_value) {
        System.out.println("------Gettting RootActions (Overrided) -------");
        final ArrayList<PVAR_INST_DEF> ret = new ArrayList<PVAR_INST_DEF>();
        if( ret_expr == null ){
            System.out.println("The Solution is Unbounded / inFeasible, Returing nothing");
            return ret;

        }
//
//		String callcode = "";
//		Integer causeReq;
//		ArrayList<Object> temp_req = new ArrayList<Object>();
//		callcode = rddl_state.getCurrentCode();
//		temp_req = rddl_state.getCauseRequirment(callcode);
//		causeReq = (int) temp_req.get(1);


        //causeReq = rddl_state.getCauseRequirment(callcode);


        //these are computed always
        //HashMap< HashMap<EXPR, Object>, Integer > all_votes = new HashMap<>();

        future_TERMS.stream().forEach( new Consumer<LCONST>() {
            @Override
            public void accept(LCONST future_term) {

                HashMap<EXPR, Object> this_future_actions = new HashMap<EXPR, Object>();

                rddl_action_vars.entrySet().stream().forEach( new Consumer< Map.Entry< PVAR_NAME, ArrayList<ArrayList<LCONST>> > >() {
                    @Override
                    public void accept( Map.Entry< PVAR_NAME , ArrayList<ArrayList<LCONST>> > entry ) {
                        final PVAR_NAME pvar = entry.getKey();
                        final Object def_val = rddl_state.getDefaultValue( pvar );

                        entry.getValue().stream().forEach( new Consumer< ArrayList<LCONST> >() {
                            @Override
                            public void accept(ArrayList<LCONST> terms ) {
                                try {
                                    final PVAR_EXPR action_var = new PVAR_EXPR(pvar._sPVarName, terms);

                                    EXPR this_action_var = action_var.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects)
                                            .substitute(Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(0)), constants, objects)
                                            .substitute(Collections.singletonMap(future_PREDICATE, future_term), constants, objects);
                                    assert (ret_expr.containsKey(this_action_var));

                                    Object value = sanitize(action_var._pName, ret_expr.get(this_action_var));

                                    if (!value.equals(def_val)) {
                                        this_future_actions.put(action_var, value);
                                    }
                                }
                                catch (Exception e){e.printStackTrace();}


                            }

                        });
                    }
                } );




                if( all_votes.containsKey( this_future_actions ) ){
                    all_votes.put( this_future_actions,  all_votes.get( this_future_actions ) + 1 );
                }else{
                    all_votes.put( this_future_actions,  1 );
                }





            }
        });

        System.out.println("Votes  " + all_votes );
        HashMap<EXPR, Object> chosen_vote = null;


        if( hindsight_method.equals( HINDSIGHT_STRATEGY.CONSENSUS )  || decision_value==1){
            final int max_votes = all_votes.values().stream().mapToInt(m->m).max().getAsInt();
            List<Entry<HashMap<EXPR, Object>, Integer>> ties  =
                    all_votes.entrySet().stream().filter( m -> (m.getValue()==max_votes) )
                            .collect( Collectors.toList() );
            if(ties.size()==1){
                chosen_vote=ties.get(0).getKey();
            }else{
                chosen_vote = ties.get( rand.nextInt(0, ties.size()-1) ).getKey();
            }
        }

        final HashMap<EXPR, Object> winning_vote = chosen_vote;
        ArrayList<Double> violations = new ArrayList<>();
        rddl_action_vars.entrySet().stream().forEach( new Consumer< Map.Entry< PVAR_NAME, ArrayList<ArrayList<LCONST>> > >() {
            @Override
            public void accept( Map.Entry< PVAR_NAME , ArrayList<ArrayList<LCONST>> > entry ) {
                final PVAR_NAME pvar = entry.getKey();
                //assuming number here
                final Object def_val = rddl_state.getDefaultValue( pvar );
                entry.getValue().stream().forEach( new Consumer< ArrayList<LCONST> >() {
                    @Override
                    public void accept(ArrayList<LCONST> terms ) {

                        final PVAR_EXPR action_var = new PVAR_EXPR( pvar._sPVarName, terms );
                        EXPR lookup = null;
                        Object ret_value = Double.NaN;

                        switch( hindsight_method ){
                            case ALL_ACTIONS :
                            case ROOT :
                                try {
                                    lookup = action_var
                                            .addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects)
                                            .substitute(Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(0)), constants, objects)
                                            .substitute(Collections.singletonMap(future_PREDICATE, future_TERMS.get(0)), constants, objects);
                                    assert (ret_expr.containsKey(lookup));
                                    ret_value = sanitize(action_var._pName, ret_expr.get(lookup));
                                    break;
                                }catch (Exception e){e.printStackTrace();}

                            case CONSENSUS :
                                ret_value = winning_vote.containsKey( action_var ) ?
                                        winning_vote.get( action_var ) : def_val;
                                break;

                            case ROOT_CONSENSUS:
                                try{
                                    if(decision_value ==0){
                                        lookup = action_var
                                                .addTerm(TIME_PREDICATE, constants, objects)
                                                .addTerm(future_PREDICATE, constants, objects)
                                                .substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(0) ), constants, objects)
                                                .substitute( Collections.singletonMap( future_PREDICATE, future_TERMS.get(0) ) , constants, objects);
                                        assert( ret_expr.containsKey( lookup ) );
                                        ret_value = sanitize( action_var._pName, ret_expr.get( lookup ) );
                                        break;

                                    }
                                    else{
                                        ret_value = winning_vote.containsKey( action_var ) ?
                                                winning_vote.get( action_var ) : def_val;
                                        break;


                                    }}catch (Exception e){e.printStackTrace();}
                            default : try{
                                throw new Exception("unknown hindisght strategy");
                            }catch( Exception exc ){
                                exc.printStackTrace();
                                //System.exit(1);
                            }
                        }

                        if( ! ret_value.equals( def_val ) ){
                            synchronized( ret ){
                                ret.add( new PVAR_INST_DEF( pvar._sPVarName, ret_value, terms ) );
                            }
                        }

                        final double ref_value = (ret_value instanceof Boolean) ?
                                ((boolean) ret_value ? 1 : 0) : ((Number)ret_value).doubleValue();

                        future_TERMS.stream().forEach( new Consumer<LCONST>() {
                            @Override
                            public void accept(LCONST future_term) {
                                try {
                                    EXPR this_action_var = action_var.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects)
                                            .substitute(Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(0)), constants, objects)
                                            .substitute(Collections.singletonMap(future_PREDICATE, future_term), constants, objects);

                                    assert (ret_expr.containsKey(this_action_var));

                                    double value = ret_expr.get(this_action_var);
                                    final double this_vio = Math.abs(ref_value - value);

                                    final Double added = new Double(this_vio);
                                    assert (added != null);

                                    synchronized (violations) {
                                        violations.add(added);
                                    }
                                }catch (Exception e){e.printStackTrace();}


                            }
                        });
                    }
                });
            }
        });
        System.out.println("Total violation of root action " + violations.stream().mapToDouble(m->m).sum() );
        System.out.println("Average absolute violation of root action " + violations.stream().mapToDouble(m->m).average().getAsDouble() );
        violations.clear();
        all_votes.clear();
        return ret;

    }




    protected int goOptimize(final GRBModel grb_model) throws GRBException {

        grb_model.update();
        System.out.println("Optimizing.............");
        grb_model.write("/Users/dimbul/Desktop/Output.lp");
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

        return grb_model.get( GRB.IntAttr.Status );
    }



    protected Map< EXPR, Double > outputResults( final GRBModel grb_model ) throws GRBException{

        System.out.println("------This is output results for GRB MODEL -------");
//		DecimalFormat df = new DecimalFormat("#.##########");
//		df.setRoundingMode( RoundingMode.DOWN );
        if( grb_model.get( GRB.IntAttr.SolCount ) == 0 ){
            return null;
        }

        Map< EXPR, Double > ret = new HashMap< EXPR, Double >();

        HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> src = new HashMap<>();
        src.putAll( rddl_action_vars );
        src.putAll( rddl_interm_vars );
        src.putAll( rddl_state_vars );

        src.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST> > >( ) {

            @Override
            public void accept(PVAR_NAME pvar,
                               ArrayList<ArrayList<LCONST>> u) {
                u.forEach( new Consumer< ArrayList<LCONST> >( ) {
                    @Override
                    public void accept(ArrayList<LCONST> terms ) {
                        future_TERMS.forEach( new Consumer<LCONST>() {
                            @Override
                            public void accept(LCONST future_term) {
                                try {
                                    EXPR action_var = new PVAR_EXPR( pvar._sPVarName, terms )
                                            .addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects)
                                            .substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(0) ), constants, objects)
                                            .substitute( Collections.singletonMap( future_PREDICATE, future_term ) , constants, objects);


                                    GRBVar grb_var = EXPR.grb_cache.get( action_var );
                                    assert( grb_var != null );
                                    double actual = grb_var.get( GRB.DoubleAttr.X );

                                    //NOTE : uncomment this part if having issues with constrained actions
                                    // such as if you get -1E-11 instead of 0,
                                    //and you are expecting a positive action >= 0
                                    String interm_val = State._df.format( actual );
//									   System.out.println( actual + " rounded to " + interm_val );

                                    ret.put( action_var, Double.valueOf(  interm_val ) );
                                } catch (GRBException e) {
                                    e.printStackTrace();
                                    ////System.exit(1);
                                }
                                catch (Exception e){
                                    e.printStackTrace();
                                }

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



    protected void modelSummary(final GRBModel grb_model) throws GRBException {
        System.out.println( "Status : "+ grb_model.get( GRB.IntAttr.Status ) + "(Optimal/Inf/Unb: " + GRB.OPTIMAL + ", " + GRB.INFEASIBLE +", " + GRB.UNBOUNDED + ")" );
        System.out.println( "Number of solutions found : " + grb_model.get( GRB.IntAttr.SolCount ) );
        System.out.println( "Number of simplex iterations performed in most recent optimization : " + grb_model.get( GRB.DoubleAttr.IterCount ) );
        System.out.println( "Number of branch-and-cut nodes explored in most recent optimization : " + grb_model.get( GRB.DoubleAttr.NodeCount ) );

        //System.out.println("Maximum (unscaled) primal constraint error : " + grb_model.get( DoubleAttr.ConstrResidual ) );

//		System.out.println("Sum of (unscaled) primal constraint errors : " + grb_model.get( DoubleAttr.ConstrResidualSum ) );
//		System.out.println("Maximum (unscaled) dual constraint error : " + grb_model.get( DoubleAttr.DualResidual ) ) ;
//		System.out.println("Sum of (unscaled) dual constraint errors : " + grb_model.get( DoubleAttr.DualResidualSum ) );

        System.out.println( "#Variables : "+ grb_model.get( GRB.IntAttr.NumVars ) );
        System.out.println( "#Integer variables : "+ grb_model.get( GRB.IntAttr.NumIntVars ) );
        System.out.println( "#Binary variables : "+ grb_model.get( GRB.IntAttr.NumBinVars ) );
        System.out.println( "#Constraints : "+ grb_model.get( GRB.IntAttr.NumConstrs ) );
        System.out.println( "#NumPWLObjVars : "+ grb_model.get( GRB.IntAttr.NumPWLObjVars ) );

        System.out.println("#State Vars : " + string_state_vars.size() );
        System.out.println("#Action Vars : " + string_action_vars.size() );
        System.out.println("Optimization Runtime(mins) : " + grb_model.get( GRB.DoubleAttr.Runtime ) );
    }


    public void removeRootPolicyConstraints(final GRBModel grb_model) throws GRBException{
        System.out.println("Number of Constraints Before:" + grb_model.get(GRB.IntAttr.NumConstrs));

        for( final GRBConstr constr : root_policy_constr ){

//			System.out.println(constr.toString());
            try{
//				System.out.println("Removing constraint " + );
                constr.get(StringAttr.ConstrName);
                //get can throw an exception

            }catch(GRBException exc){
                System.out.println(exc.getErrorCode());
                exc.printStackTrace();
                //System.exit(1);
            }
            grb_model.remove( constr );
            grb_model.update();
            //System.out.println(grb_model.get( IntAttr.NumConstrs ));
            //System.out.println(grb_model.toString());


        }

        System.out.println("Number of Constraints After :"+ grb_model.get(GRB.IntAttr.NumConstrs));


        root_policy_constr.clear();

        root_policy_expr.clear();








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




    protected Map<LVAR,LCONST> getSubs(ArrayList<RDDL.LTERM> terms, ArrayList<LCONST> consts) {
        Map<LVAR, LCONST> ret = new HashMap<RDDL.LVAR, RDDL.LCONST>();
        for( int i = 0 ; i < terms.size(); ++i ){
            assert( terms.get(i) instanceof LVAR );
            ret.put( (LVAR)terms.get(i), consts.get(i) );
        }
        return ret;
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

        grb_model.get( GRB.IntAttr.NumConstrs );
        to_remove_constr.clear();

        to_remove_expr.clear();
    }


    //This is added by Harish.
    protected void addRootPolicyConstraints(final GRBModel grb_model) throws Exception{
        GRBExpr old_obj = grb_model.getObjective();

        getHindSightConstraintExpr(hindsight_method).parallelStream().forEach( new Consumer<RDDL.BOOL_EXPR>() {

            @Override
            public void accept( RDDL.BOOL_EXPR t) {
                synchronized( grb_model ){
                    try {
                        GRBVar gvar = t.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);

                        GRBConstr this_constr = grb_model.addConstr( gvar, GRB.EQUAL, 1, RDDL.EXPR.getGRBName(t) );
                        saved_expr.add( t ); // saved_vars.add( gvar );
                        root_policy_expr.add(t);
                        saved_constr.add(this_constr);
                        root_policy_constr.add(this_constr);
                    } catch (GRBException e) {
                        e.printStackTrace();
                        //System.exit(1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        //startring actionv ars at 0.0
//		rddl_action_vars.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>>>() {
//			@Override
//			public void accept(PVAR_NAME pvar, ArrayList<ArrayList<LCONST>> u) {
//				u.forEach( new Consumer<ArrayList<LCONST>>() {
//					@Override
//					public void accept(ArrayList<LCONST> terms) {
//						TIME_TERMS.forEach( new Consumer<LCONST>() {
//							public void accept(LCONST time_term) {
//								future_TERMS.forEach( new Consumer<LCONST>(){
//									@Override
//									public void accept(LCONST future_term) {
//										EXPR this_expr = new PVAR_EXPR( pvar._sPVarName, terms )
//											.addTerm( TIME_PREDICATE, constants, objects )
//											.substitute( Collections.singletonMap( TIME_PREDICATE, time_term), constants, objects)
//											.addTerm( future_PREDICATE, constants, objects )
//											.substitute( Collections.singletonMap( future_PREDICATE, future_term), constants, objects);
//										final GRBVar this_var = EXPR.getGRBVar(this_expr, grb_model, constants, objects, type_map);
//										try {
//											this_var.set( DoubleAttr.Start, 0.0 );
//										} catch (GRBException e) {
//											e.printStackTrace();
//											//System.exit(1);
//										}
//									}
//								});
//							};
//						});
//					}
//				});
//			}
//		});

        grb_model.setObjective(old_obj);
        grb_model.update();





    }


    @Override
    public void runRandompolicyForState(State s)throws Exception{


        //Copying the state
        HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> copiedState = new HashMap<>();
        copiedState = deepCopyState(s);
        System.out.println("I am after deepCopy State");
        //Getting Random Trajectories and adding to buffer.
        runRandomPolicy(s,2, 100, randint);
        //We are updating current as previous.
        pre_buffer_action = buffer_action;
        pre_buffer_state  = buffer_state;
        pre_buffer_reward = buffer_reward;





        //This is for random policy immediate average reward.
        Double sum_rand_reward=0.0;
        Double max_rand_reward=-Double.MAX_VALUE;

        for(int num_traj=0 ; num_traj < buffer_reward.size();num_traj++){

            ArrayList<Double> traj_reward = buffer_reward.get(num_traj);


            sum_rand_reward = sum_rand_reward + traj_reward.get(0);
            if(traj_reward.get(0)>max_rand_reward){
                max_rand_reward = traj_reward.get(0);
            }




        }
        Double average_rand_reward = sum_rand_reward/buffer_reward.size();





        long endTime1 = System.currentTimeMillis();

        s.copyStateRDDLState(copiedState,true);






    }


    public void convertNPWLtoPWL(State s) throws Exception {

        long startTime1 = System.currentTimeMillis();
        checkNonLinearExpressions(s);
        long endTime2 = System.currentTimeMillis();
        double pwl_timer_value =(double) (endTime2-startTime1)/1000;
        double r_api_timer = running_R_api/1000;



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
    public Pair<Integer,Integer> CompetitionExploarationPhase(String rddl_filepath, String instanceName, Integer n_futures, Integer n_lookahead, String gurobi_timeout,
                                                              String future_gen_type,String hindsight_strat, RDDL rddl_object, State s) throws Exception{


        ////////////////////////////////////////////////////////////////////////////
        long start_time = System.currentTimeMillis();
        RDDL rddl = null;
        RDDL.DOMAIN domain = null;
        RDDL.INSTANCE instance = null;
        State state = null;
        RDDL.NONFLUENTS nonFluents = null;
        ////////////////////////////////////////////////////////////////////////////

        Integer exp_steps = 8;
        Integer exp_rounds =10;
        HashMap<Pair<Integer,Integer>,Double> exploration_rewards = new HashMap<>();
        Integer current_lookAhead = 5;
        Integer START_FUTURE_VALUE = 5;
        Integer FUTURE_VALUE = 5;
        ////////////////////////////////////////////////////////////////////////////

        boolean EXPLORATION = true;
        int best_lookahead = Integer.MAX_VALUE;
        int best_future    = Integer.MAX_VALUE;
        //This one is for lookahead value.
        for(int lookahead_Iteration = 1 ; lookahead_Iteration<3 ; lookahead_Iteration++){
            //This is the starting value of future.
            FUTURE_VALUE = START_FUTURE_VALUE;
            while(FUTURE_VALUE < 6) {
                ////////////////////////////////////////////////////////////////////////////
                rddl = new RDDL(rddl_filepath);
                state = new State();
                instance = rddl._tmInstanceNodes.get(instanceName);
                if (instance._sNonFluents != null) {
                    nonFluents = rddl._tmNonFluentNodes.get(instance._sNonFluents);
                }
                domain = rddl._tmDomainNodes.get(instance._sDomain);

                state.init(domain._hmObjects, nonFluents != null ? nonFluents._hmObjects : null, instance._hmObjects,
                        domain._hmTypes, domain._hmPVariables, domain._hmCPF,
                        instance._alInitState, nonFluents == null ? new ArrayList<PVAR_INST_DEF>() : nonFluents._alNonFluents, instance._alNonFluents,
                        domain._alStateConstraints, domain._alActionPreconditions, domain._alStateInvariants,
                        domain._exprReward, instance._nNonDefActions);

                // If necessary, correct the partially observed flag since this flag determines what content will be seen by the Client
                if ((domain._bPartiallyObserved && state._alObservNames.size() == 0)
                        || (!domain._bPartiallyObserved && state._alObservNames.size() > 0)) {
                    boolean observations_present = (state._alObservNames.size() > 0);
                    System.err.println("WARNING: Domain '" + domain._sDomainName
                            + "' partially observed (PO) flag and presence of observations mismatched.\nSetting PO flag = " + observations_present + ".");
                    domain._bPartiallyObserved = observations_present;
                }

                ///################################################################################################################

                //This is to set lookahead value and future value.

                n_lookahead = current_lookAhead;
                n_futures = FUTURE_VALUE;

                HOPPlanner explo_planner  = new HOPPlanner( n_futures,  n_lookahead,  instanceName,  gurobi_timeout,
                         future_gen_type, hindsight_strat,  rddl,state);

                explo_planner.setRDDL(rddl);
                Double average_round_reward = 0.0;
                ////////////////////////////////////////////////////////////////////////////

                for (int j = 0; j < exp_rounds; j++) {
                    state.init(domain._hmObjects, nonFluents != null ? nonFluents._hmObjects : null, instance._hmObjects,
                            domain._hmTypes, domain._hmPVariables, domain._hmCPF,
                            instance._alInitState, nonFluents == null ? new ArrayList<PVAR_INST_DEF>() : nonFluents._alNonFluents, instance._alNonFluents,
                            domain._alStateConstraints, domain._alActionPreconditions, domain._alStateInvariants,
                            domain._exprReward, instance._nNonDefActions);

                    Double round_reward = 0.0;
                    ArrayList<PVAR_INST_DEF> round_best_action = new ArrayList<>();
                    Double max_step_reward = -Double.MAX_VALUE;

                    //This is for sequential Steps..
                    for (int n = 0; n < exp_steps; n++) {

                        //This is to check if there are any NPWL expression.  If there are no,then DO_NWPL_PWL will be false.
                        explo_planner.DO_NPWL_PWL = false;
                        if(explo_planner.DO_NPWL_PWL){
                            //This code is for random action
                            explo_planner.runRandompolicyForState(state);
                            //Convert NPWL to PWL
                            explo_planner.convertNPWLtoPWL(state);

                        }
                        ArrayList<PVAR_INST_DEF> actions = explo_planner.getActions(state);
                        System.out.println("The Action Taken is >>>>>>>>>>>>>>>>>>>>>>>" + actions.toString());

                        //state.checkActionConstraints(actions);
                        state.computeNextState(actions, rand);
                        final double immediate_reward = ((Number) domain._exprReward.sample(
                                new HashMap<RDDL.LVAR, LCONST>(), state, rand)).doubleValue();
                        state.advanceNextState();
                        round_reward += immediate_reward;
                        if (immediate_reward > max_step_reward) {
                            round_best_action = actions;
                            max_step_reward = immediate_reward;
                            gurobi_initialization = round_best_action;
                        }
                    }
                    average_round_reward += round_reward;
                }
                exploration_rewards.put(new Pair<>(current_lookAhead, FUTURE_VALUE), average_round_reward / exp_rounds);
                explo_planner.dispose_Gurobi();
                long endtime = System.currentTimeMillis();
                FUTURE_VALUE = getFutureValue(FUTURE_VALUE,true); //Double.valueOf(Math.pow(FUTURE_VALUE,2)).intValue();
            }
            current_lookAhead=getLookAheadValue(current_lookAhead,true);
        }
        Double max_reward = -Double.MAX_VALUE;

        //This piece of code is to get the best lookahead and future pair.
        HashMap<Pair<Integer,Integer>,Double> equal_rewards = new HashMap<>();
        for(Pair<Integer,Integer> key : exploration_rewards.keySet()){
            if(exploration_rewards.get(key)>=max_reward){
                if(exploration_rewards.get(key).equals(max_reward)){ equal_rewards.put(key,max_reward); }
                else{ max_reward = exploration_rewards.get(key);if(equal_rewards!=null){equal_rewards.clear();}
                    equal_rewards.put(key,max_reward); }
            }
        }
        for(Pair<Integer,Integer> key : equal_rewards.keySet()){
            if(key._o1 < best_lookahead){
                best_lookahead = key._o1;
                best_future    = key._o2;
            }
        }



        return new Pair<Integer, Integer>(best_lookahead,best_future);




    }



    static Integer getLookAheadValue(Integer current_lookahead, Boolean change){


        Integer next_look_ahead = current_lookahead;
        if(change){
            if(current_lookahead==1){
                next_look_ahead = 2;
            }
            else {
                //next_look_ahead = Double.valueOf(Math.pow(current_lookahead,2)).intValue() ;
                next_look_ahead = current_lookahead + 2 ;}
        }
        else{
            next_look_ahead = current_lookahead;
        }

        return next_look_ahead;









    }



    static Integer getFutureValue(Integer current_future, Boolean change){


        Integer next_future_value = current_future;
        if(change){
            if(current_future==1){
                next_future_value = 2;
            }
            else {
                //next_look_ahead = Double.valueOf(Math.pow(current_lookahead,2)).intValue() ;
                next_future_value = current_future + 2 ;}
        }
        else{
            next_future_value = current_future;
        }

        return next_future_value;









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
                    if(temp_lconst instanceof RDDL.OBJECT_VAL){
                        LCONST new_lconst = new RDDL.OBJECT_VAL(temp_lconst._sConstValue);
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



    protected HashMap<ArrayList<LCONST>,Object> getActionInstantiations(PVAR_NAME action_var, RDDL.TYPE_NAME action_type, Random rand){
        //This function gives the intansiations of the parameters.
        //




        HashMap<ArrayList<LCONST>,Object> action_terms_assign = new HashMap<>();

        ArrayList<RDDL.TYPE_NAME> temp_objects = object_type_name.get(action_var);
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
        if(action_type.equals(RDDL.TYPE_NAME.REAL_TYPE)){
            //select_range Has values [min,max]
            ArrayList<Double> select_range = value_range.get(action_var);
            Double take_action_val = select_range.get(0) + ((select_range.get(1)-select_range.get(0)) * rand.nextFloat());
            action_terms_assign.put(action_terms,take_action_val);

        }




        if(action_type.equals(RDDL.TYPE_NAME.BOOL_TYPE)){

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

            RDDL.TYPE_NAME type_val = s._hmPVariables.get(action_var)._typeRange;

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
        //This stores the non-PWL-EXPR's as a global variable.
        not_pwl_expr.clear();



//		//This is for Constraints
//		ArrayList<BOOL_EXPR> constraints = new ArrayList<>();
//
//		constraints.addAll(rddl_state._alActionPreconditions); constraints.addAll(rddl_state._alStateInvariants);
//
//
//		//Need to Handle for Action Constraints and State Invariants.
////		for(BOOL_EXPR e : constraints){
////
////			System.out.println("dkfjdkjfkd");
////
////
////
////		}
////
//
//
//
//		//This is for State_vars, Interm_vars, Observ_vars.
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


                    try{

                        //new COMP_EXPR(raw_terms.get(0),terms.get(0),"==").toString()
                        if(SHOW_PWL_NON_PWL)
                            System.out.println(cpf._exprEquals.toString());
                        Boolean check_PWL = cpf._exprEquals.substitute(subs1,constants,objects).sampleDeterminization(rand,constants,objects).isPiecewiseLinear(constants,objects);
                        if(SHOW_PWL_NON_PWL)
                            System.out.println(check_PWL);
                        if(!check_PWL){

                            if(!not_pwl_expr.contains(cpf._exprEquals)){
                                not_pwl_expr.add(cpf._exprEquals);

                            }
                            EXPR final_expr = generateDataForPWL(cpf._exprEquals.substitute(subs1,constants,objects), raw_terms);
                            //This is Getting Condition.
                            RDDL.BOOL_EXPR conditional_state = new BOOL_CONST_EXPR(true);

                            for(int i=0;i<terms.size();i++){
                                RDDL.BOOL_EXPR cur_cond_statement = conditional_state;
                                RDDL.COMP_EXPR temp_expr = new RDDL.COMP_EXPR(raw_terms.get(i), terms.get(i), "==");
                                conditional_state = new RDDL.CONN_EXPR(cur_cond_statement,temp_expr,"^");
                            }
                            final_pwl_true.add(final_expr);
                            final_pwl_cond.add(conditional_state);
                        }


                    }catch (Exception e){
                        e.printStackTrace();
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
    public EXPR generateDataForPWL(EXPR e, ArrayList<RDDL.LTERM> raw_terms) throws Exception {



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
        if(SHOW_PWL_NON_PWL) {
            System.out.println("THE GCV VALUE :" + gcv_val);
            System.out.println("The RSS VALUE :" + rss_val);
        }
        engine.eval("a=predict(model,1)");


        String earth_output = engine.eval("format(model,style='bf')").asString();

        long end_timer = System.currentTimeMillis();




        running_R_api = (double) end_timer - start_timer;




        //This will parse and give a EXPR Output.
        EXPR final_expr =parseEarthROutput(earth_output,input_variables,raw_terms);

        return(final_expr);






    }


    public EXPR parseEarthROutput(String earthOutput, ArrayList<PVAR_NAME> input_variables, ArrayList<RDDL.LTERM> raw_terms) throws Exception {



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


            if(temp_str.equals("")){continue;}

            //This is for Bias,
            if(!(temp_str.contains("-") || temp_str.contains("+"))){
                bias =Double.parseDouble(temp_str);

            }


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
                    if(SHOW_PWL_NON_PWL)
                        System.out.println("PWL_NON_PWL ::: " + max_oper_expr.toString());



                }
                if(string_pvar.contains(hinge_values[1])){
                    real_val = Double.parseDouble(hinge_values[0]);
                    PVAR_EXPR temp_pvar_expr        = new PVAR_EXPR(hinge_values[1],raw_terms);
                    REAL_CONST_EXPR temp_const_expr = new REAL_CONST_EXPR(real_val);

                    RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_const_expr,temp_pvar_expr,"-");
                    RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new REAL_CONST_EXPR(0.0), temp_oper_expr,"max");

                    hinge_function.put(key_val,max_oper_expr);
                    if(SHOW_PWL_NON_PWL)
                        System.out.println("PWL_NON_PWL ::: "+ max_oper_expr.toString());




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


    // This is added by Harish.
    public void dispose_Gurobi() throws GRBException {

        System.out.println("Cleaning Gurobi");

//		static_grb_model.dispose();
//
//		grb_env.dispose();
//


        cleanUp(static_grb_model);
        static_grb_model.getEnv().dispose();
        static_grb_model.dispose();

        RDDL.EXPR.cleanUpGRB();
        System.gc();



    }


}
