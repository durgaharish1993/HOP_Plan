package rddl.det.comMip;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;

//import org.apache.commons.math3.random.RandomDataGenerator;

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
import rddl.RDDL.REAL_CONST_EXPR;
import rddl.State;
import rddl.policy.Policy;
import rddl.viz.StateViz;
import util.Pair;
import util.Timer;

public class FittedCompetitionReservoirDomain extends HOPTranslate {

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
    private static final String xPos = "xPos";
    private static final String yPos = "yPos";
    private static final String rainFall ="rainfall";


    //private EmergencyDomainDataReel reel;
    private FileWriter outFile;
    private static FileWriter fw;
    private static FileWriter fw1;

    //Timer Variables



    public FittedCompetitionReservoirDomain(ArrayList<String> args, RDDL rddl_object, State s) throws Exception {
        //The parent class is HOPTranslate --> Translate!!!
        //Total length of args = 15 , length passed here is 13

        super(args.subList(0, args.size()-3),rddl_object, s);
        //int sub_val = 2 ;
        //System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$The Code ran was initiazed HOPTranslate.java and Translate.java.$$$$$$$$");
        //This piece of code reads the historic data.
        //No need for Sampling Data.
//		reel = new EmergencyDomainDataReel( args.get( args.size()-5 ), ",", true,
//				false, Integer.parseInt( args.get( args.size()- 4 ) ), //numfolds
//				Integer.parseInt( args.get( args.size()- 3 ) ), //training fold
//				Integer.parseInt( args.get( args.size()- 2) ) ); //testing fold


        //outFile = new FileWriter(new File( args.get( args.size()-1 ) ) );
        //outFile.write("Round,Step,random_policy_time,R_API_timer,PWL_translate_time,HOP_action_time,Action_taken,Immediate_Reward,Random_Policy_AvgReward, Random_Policy_MaxReward,Exit_code\n");

        System.out.println("-----End of FittedEmergencyDomainHOPTranslate INIT Function------------");
    }

    @Override
    protected void prepareModel( ) throws Exception{
        translate_time.ResumeTimer();
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

                        final String pvarName = entry.getKey()._sPVarName;
                        //System.out.println("Dunno  nn   What's happening");
                        //these are sampled from data in TranslateCPTs()
                        if( isStochastic(pvarName) || replace_cpf_pwl.containsKey(entry.getKey())){
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
                                //This piece of code is for replacing Non-pwl to PWL.
//								if(replace_cpf_pwl.containsKey(p)){
//
//									new_rhs_stationary = replace_cpf_pwl.get(p).substitute(subs,constants,objects);
//
//								}





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
                                                        //System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
                                                        GRBVar lhs_var = lhs_future.getGRBConstr(
                                                                GRB.EQUAL, static_grb_model, constants, objects, type_map);

                                                        if(rhs_future.toString().equals("if (damaged($l1, $time0, $future0)) then [~[exists_{?r : rover} repair(?r, $l1, $time0, $future0)]] else [(0.18924808898947454 > (1.0 - (sum_{?loc : location, ?r : rover} ((move(?r, ?loc, $time0, $future0) * TOOL-ON($l1, ?r)) * DAMAGE-PROB(?loc)))))]")){
                                                            System.out.println("dkjfkd");


                                                        }

                                                        System.out.println(rhs_future.toString());

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

                                                        GRBVar rhs_var = rhs_future.getGRBConstr(
                                                                GRB.EQUAL, static_grb_model, constants, objects, type_map);

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
            new String[]{rainFall, xPos, yPos,
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

                                if( !isStochastic(p._sPVarName) && !replace_cpf_pwl.containsKey(p) ){
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

                                if(replace_cpf_pwl.containsKey(p)){

                                    new_rhs_stationary = replace_cpf_pwl.get(p).substitute(subs,constants,objects);

                                }




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
                                                        //System.out.println("lhs_future:"+ lhs_future+ "  rhs_future:"+ rhs_future );
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







    @Override
    public void runRandompolicy(State s)throws Exception{


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
        Double max_rand_reward=-1e6;

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




            //reel.resetTestIndex( 0 ); //test_start_idx );

            //EmergencyDomainDataReelElement stored_next_thing = null;
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



//				if( step == 0 || !randomize_test ){
                //copy back next state of exogenous var, Generating Next State from the testing data.

                //EmergencyDomainDataReelElement exo_thing = reel.getNextTestingInstance();
                //This is the place, we set the next state. Change when you want to use the real emergency data
                //exo_thing.setInState( rddl_state );

                //callcode = rddl_state.getCurrentCode();
                //temp_req = rddl_state.getCauseRequirment(callcode);
                //cur_location = rddl_state.getCurrentCallLoction();

                //causeReq = temp_req.get(0).toString();






//					if(cur_location.get("[$xpos]")>1400) {
//
//						region_data = "Region_2";
//					}
//					else {
//						region_data = "Region_1";
//					}
//
//
//					//causeReq = rddl_state.getCauseRequirment(callcode);
//					System.out.println("Current Call Code :" + callcode);
//					System.out.println("Current Requirment :"+causeReq );



//				}else{
//					stored_next_thing.setInState( rddl_state );
//					callcode = rddl_state.getCurrentCode();
//					temp_req = rddl_state.getCauseRequirment(callcode);
//					causeReq = temp_req.get(0).toString();
//					//System.out.println("Current State ID:"+exo_thing.callId);
//					System.out.println("Current Call Code :" + callcode);
//					System.out.println("Current Requirment :"+causeReq );
//				}
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

                System.out.println("I am here");

                //Copying the state
                copiedState = deepCopyState(rddl_state);
                System.out.println("I am after deepCopy State");
                //Getting Random Trajectories and adding to buffer.
                runRandomPolicy(rddl_state,2, 100, randint);
                //We are updating current as previous.
                pre_buffer_action = buffer_action;
                pre_buffer_state  = buffer_state;
                pre_buffer_reward = buffer_reward;




                System.out.println("I am After Random Policy");


                //This is for random policy immediate average reward.
                Double sum_rand_reward=0.0;
                Double max_rand_reward=-1e6;

                for(int num_traj=0 ; num_traj < buffer_reward.size();num_traj++){

                    ArrayList<Double> traj_reward = buffer_reward.get(num_traj);


                    sum_rand_reward = sum_rand_reward + traj_reward.get(0);
                    if(traj_reward.get(0)>max_rand_reward){
                        max_rand_reward = traj_reward.get(0);
                    }




                }
                Double average_rand_reward = sum_rand_reward/buffer_reward.size();





                long endTime1 = System.currentTimeMillis();

                double random_timer_value =(double) (endTime1-startTime)/1000;




                //Setting back the original State
                rddl_state.copyStateRDDLState(copiedState,true);


                long startTime1 = System.currentTimeMillis();
                checkNonLinearExpressions(rddl_state);
                long endTime2 = System.currentTimeMillis();
                double pwl_timer_value =(double) (endTime2-startTime1)/1000;
                double r_api_timer = running_R_api/1000;







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


                String action_taken = null;

                try {
                    //System.out.println("I am here ----> 2");
                    System.out.println("------------------------------------");
                    //System.out.println("State : " + rddl_state );
                    System.out.println("Action From Optimizer : " + rddl_action);
                    fw.write('\n'+ rddl_action.toString() + '\n');
                    fw1.write(rddl_action.toString()+"\n");
                    fw1.flush();
                    action_taken = rddl_action.toString().replaceAll(",", ":");
                    System.out.println("------------------------------------");
                    //boolean novehicle = rddl_action.toString().equals("[]");



                    ///This is the place where we are defining randomonized tests!!!!
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
                    //This is the place where we are getting next action, Need to check the procedure.








                    System.out.println("----------Calculating Interm State and Printing it!!!!!");
                    //Computing Next State


                    rddl_state.computeNextState(rddl_action, rand);



                    //distance_emergency =  rddl_state.getDistanceEmergency();
                    //cur_availbility    =  rddl_state.getVehicleAvailability();

                    //String greedy_choice = getGreedyChoice(distance_emergency,cur_availbility);
                    //HashMap<String,String> resource_availbility = getResourceAvailbility(cur_availbility);

                    //String Vehicles_available = resource_availbility.get("Vehicles_available");
                    //String Number_available = resource_availbility.get("Number_available");



                    System.out.println("Interm State : "  );
                    System.out.println(rddl_state);
                    fw.write("----------------Interm State:\n");
                    fw.write(rddl_state.toString() +"\n");
                    fw.flush();




//					System.out.println("----------------------------------------------------------------------------");
//					System.out.println("------>Writing FirstResponse, FullResponse, OverWhelm Time to .csv File!!!!.");
//					outFile.write( round +","+step +","+ 60*EmergencyDomainHOPTranslate.getFirstResponse(rddl_state) + "," + 60*EmergencyDomainHOPTranslate.getFullResponse(rddl_state)  + "," + EmergencyDomainHOPTranslate.getOverwhelm(rddl_state) + "," +novehicle+","+ t1 + "," + callcode +"," + causeReq + "," + action_taken +","+ exit_code+","+ region_data+","+greedy_choice+","+store_all_votes +","+Vehicles_available+","+Number_available);

//					outFile.write( 60*EmergencyDomainHOPTranslate.getFirstResponse(rddl_state)
//							+ "," + 60*EmergencyDomainHOPTranslate.getFullResponse(rddl_state)
//							+ "," + EmergencyDomainHOPTranslate.getOverwhelm(rddl_state) );





//					outFile.write("\n");
//					outFile.flush();










                    if(stateViz != null){
                        stateViz.display(rddl_state, step);
                    }
                } catch (Exception ee) {
                    System.out.println("FATAL SERVER EXCEPTION:\n" + ee);
                    throw ee;
                }






//				try {
//					//Need to Understand what's happening here!!!.
//					System.out.println("===========> Checking State Action Constraints!!!!!");
//					rddl_state.checkStateActionConstraints(rddl_action);
//				} catch (Exception e) {
//					System.out.println("TRIAL ERROR -- STATE-ACTION CONSTRAINT VIOLATION:\n" + e);
//					throw e;
//				}
////
//
//
//




                if (SHOW_TIMING){
                    System.out.println("**TIME to compute next state: " + timer.GetTimeSoFarAndReset());
                }





                // Calculate reward / objective and store
                final double immediate_reward = ((Number)rddl_domain._exprReward.sample(
                        new HashMap<LVAR,LCONST>(),rddl_state, rand)).doubleValue();



                accum_reward += cur_discount * immediate_reward;
                cur_discount *= rddl_instance._dDiscount;






                //outFile.write( round +","+step +"," + random_timer_value + ","+ r_api_timer+ ","+ pwl_timer_value+ "," +t1 + "," +  action_taken +","+ String.valueOf(immediate_reward)+ "," + average_rand_reward.toString()+ "," + max_rand_reward.toString() +","+exit_code+"\n");







                if (SHOW_TIMING){
                    System.out.println("**TIME to copy observations & update rewards: " + timer.GetTimeSoFarAndReset());
                }



                //Need to Understand This Function
                System.out.println("===========> Calculating AdvanceNext State !!!!!");
                rddl_state.advanceNextState();
                //outFile.flush();
                if (SHOW_TIMING){
                    System.out.println("**TIME to advance state: " + timer.GetTimeSoFarAndReset());
                }
            }

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

//    public static void main(String[] args) throws Exception {
//        System.out.println( Arrays.toString( args ) );
//
//        fw = new FileWriter(new File( Arrays.asList(args).get( Arrays.asList(args).size()-2 ) ) );
//        fw1 = new FileWriter(new File( Arrays.asList(args).get( Arrays.asList(args).size()-1 ) ) );
//
//        FittedCompetitionReservoirDomain planner = new FittedCompetitionReservoirDomain(
//                Arrays.asList( args ).subList(0, args.length-2-2) );
//
//
//        System.out.println("-----------Evaluating the planner Started---------------");
//        //This piece of code for evaluting the planner!!!.
//        System.out.println( planner.evaluatePlanner(
//                Integer.parseInt( args[args.length-2-2] ),
//                null, //new EmergencyDomainStateViz(1300,30,1500,80),
//                Boolean.parseBoolean( args[ args.length-1-2 ] ) ) );
//
//
//    }

}
