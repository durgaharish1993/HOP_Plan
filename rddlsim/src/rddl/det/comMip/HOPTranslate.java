package rddl.det.comMip;

import java.io.File;
import java.io.FileWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.math3.random.RandomDataGenerator;

import org.rosuda.JRI.Rengine;
import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.OPER_EXPR;
import rddl.RDDL.QUANT_EXPR;
import rddl.State;
import rddl.RDDL.BOOL_CONST_EXPR;
import rddl.RDDL.BOOL_EXPR;
import rddl.RDDL.COMP_EXPR;
import rddl.RDDL.CPF_DEF;
import rddl.RDDL.EXPR;
import rddl.RDDL.INT_CONST_EXPR;
import rddl.RDDL.LCONST;
import rddl.RDDL.LVAR;
import rddl.RDDL.OBJECTS_DEF;
import rddl.RDDL.PVAR_EXPR;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.RDDL.PVAR_NAME;
import rddl.RDDL.REAL_CONST_EXPR;
import rddl.RDDL.TYPE_NAME;
import rddl.RDDL.LTERM;
import rddl.RDDL.CONN_EXPR;
import rddl.RDDL.IF_EXPR;
import rddl.policy.Policy;
import gurobi.GRB;
import gurobi.GRBConstr;
import gurobi.GRBException;
import gurobi.GRBExpr;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import gurobi.GRB.DoubleAttr;
import gurobi.GRB.IntAttr;
import gurobi.GRB.StringAttr;
import util.Pair;

import java.util.Random;






public class HOPTranslate extends Translate {

	public static enum FUTURE_SAMPLING{
		SAMPLE {
			@Override
			public EXPR getFuture(EXPR e, RandomDataGenerator rand, Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants, Map<TYPE_NAME, OBJECTS_DEF> objects )   {
				try {
					return e.sampleDeterminization(rand,constants,objects);
				} catch (Exception e1) {
					e1.printStackTrace();
				}return null;

			}
		}, MEAN {
			@Override
			public EXPR getFuture(EXPR e, RandomDataGenerator rand, Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants, Map<TYPE_NAME, OBJECTS_DEF> objects ) {
				try {
					return e.getMean(constants, objects);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			return null;
			}
		};
		
		public abstract EXPR getFuture( final EXPR e , final RandomDataGenerator rand,
                                        Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants, Map<TYPE_NAME, OBJECTS_DEF> objects );
	}
	protected int num_futures = 0;
	protected FUTURE_SAMPLING future_gen;
	protected RandomDataGenerator rand;
	protected Random randint = new Random();


	protected int exit_code ;
	

	private static final TYPE_NAME future_TYPE = new TYPE_NAME( "future" );
	protected ArrayList< LCONST > future_TERMS = new ArrayList<>();
	protected enum HINDSIGHT_STRATEGY { 
		ROOT, ALL_ACTIONS, CONSENSUS, ROOT_CONSENSUS
	}
	protected HINDSIGHT_STRATEGY hindsight_method;
	protected HashMap< HashMap<EXPR, Object>, Integer > all_votes = new HashMap<>();
	private FileWriter fileRootConsensus;


	//here is the constructor!!!.
	public HOPTranslate( List<String> args, RDDL rddl_object, State s ) throws Exception{
		
//		super.TranslateInit( domain_file, inst_file, lookahead, timeout );
		//This Thing initialize all RDDL Domain from Translate.java.
		//This Super Class reads data from RDDL Domain and Instance File, Initialize the data. 
		super( args.subList(0, 5), rddl_object,s );
		System.out.println("------- This is HOPTranslate (HOPTranslate.java)");
		System.out.println( args );
		HOPTranslateInit( args.get( 0 ), args.get( 1 ), Integer.parseInt( args.get( 2 ) ), Double.parseDouble( args.get( 3 ) ), 
				Integer.parseInt( args.get( 5 ) ), FUTURE_SAMPLING.valueOf( args.get( 6 ) ), 
				HINDSIGHT_STRATEGY.valueOf( args.get( 7 ) ) );


		if(hindsight_method.toString()=="ROOT_CONSENSUS"){

			fileRootConsensus = new FileWriter(new File( args.get( args.size()-1 ) ) );
			fileRootConsensus.write("Root_Objective,Consensus_Objective,Root_Action, Consensus_Action\n");





		}




	}
	
	
	public void HOPTranslateInit( final String domain_file, final String inst_file, 
			final int lookahead , final double timeout ,
			int n_futures, final FUTURE_SAMPLING sampling,
			final HINDSIGHT_STRATEGY hinsight_strat ) throws Exception, GRBException {
		
		System.out.println("-----This is HOPTranslateInit ----");
		
		this.num_futures = n_futures;
		this.future_gen = sampling;
		rand = new RandomDataGenerator( );
		this.hindsight_method =  hinsight_strat;
	}
	
	@Override
	protected void removeExtraPredicates() {
		System.out.println("-----This is removeExtraPredicates (Overrided)----");
		
		super.removeExtraPredicates();
		if( future_TERMS != null ){
			future_TERMS.clear();
		}
		if( objects != null ){
			objects.remove( future_TYPE );
		}
	}
	
	@Override
	protected void addExtraPredicates() {
		System.out.println("-----This is addExtraPredicates (Overrided)----");
		System.out.println("Here we are adding Futures------> Adding Extra Predicates");
		super.addExtraPredicates();
		if( future_gen.equals( FUTURE_SAMPLING.MEAN ) ){
			num_futures = 1;
		}
		for( int f = 0 ; f < this.num_futures; ++f ){
			future_TERMS.add( new RDDL.OBJECT_VAL( "future" + f ) );
		}
		objects.put( future_TYPE,  new OBJECTS_DEF(  future_TYPE._STypeName, future_TERMS ) );
	}

	@Override
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
													System.out.println("Adding var " + gvar.get(StringAttr.VarName) + " " + this_tf );
												}catch (GRBException e) {
													e.printStackTrace();
													//System.exit(1);
											}catch(Exception e) {
													e.printStackTrace();
												}
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
	
	@Override
	protected void translateCPTs( HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> initState,
			final GRBModel grb_model ) throws GRBException {
		
		GRBExpr old_obj = grb_model.getObjective();
		System.out.println("----- This is translate CPT (Overrided)-----");
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
                                    //This Piece of code is changed by HARISH
                                    //System.out.println(new_lhs_stationary + " " + new_rhs_stationary );

                                    EXPR lhs_with_tf = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects);
                                    EXPR rhs_with_tf = new_rhs_stationary.addTerm(TIME_PREDICATE, constants, objects)
                                            .addTerm(future_PREDICATE, constants, objects);
    //								System.out.println(lhs_with_tf + " " + rhs_with_tf );

								    time_terms_indices.stream().forEach( new Consumer< Integer >() {
									@Override
									public void accept(Integer time_term_index ) {
										EXPR lhs_with_f_temp = null;
										try {
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

                                                                EXPR lhs_future = future_gen.getFuture(lhs, rand, constants, objects);
                                                                EXPR rhs_future = future_gen.getFuture(rhs, rand, constants, objects);


//														synchronized ( lhs_future ) {
//															synchronized ( rhs_future ) {
                                                                synchronized (grb_model) {


                                                                    GRBVar lhs_var = lhs_future.getGRBConstr(
                                                                            GRB.EQUAL, grb_model, constants, objects, type_map);

                                                                    GRBVar rhs_var = rhs_future.getGRBConstr(
                                                                            GRB.EQUAL, grb_model, constants, objects, type_map);

//																		System.out.println( lhs_future.toString()+"="+rhs_future.toString() );
                                                                    final String nam = RDDL.EXPR.getGRBName(lhs_future) + "=" + RDDL.EXPR.getGRBName(rhs_future);
//																		System.out.println(nam);;

                                                                    GRBConstr this_constr
                                                                            = grb_model.addConstr(lhs_var, GRB.EQUAL, rhs_var, nam);
                                                                    to_remove_constr.add(this_constr);
                                                                    to_remove_expr.add(lhs_future);
                                                                    to_remove_expr.add(rhs_future);

                                                                }


                                                            } catch (GRBException e) {
                                                                e.printStackTrace();
                                                                //System.exit(1);
                                                            } catch (Exception e) {
                                                                e.printStackTrace();
                                                            }
//																	saved_vars.add( lhs_var );
//																	saved_vars.add( rhs_var );
                                                        }
//															}
//
                                                    });
                                        }catch (Exception e){e.printStackTrace();}
                                    }
									} );
								}catch (Exception e){e.printStackTrace();}
								}
							} );
					}
				});
			}
		});
		
		grb_model.setObjective(old_obj);
		grb_model.update();

	}
								
//		for( final HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> map : src ){
//		
//			for( final PVAR_NAME p : map.keySet() ){
//				for( final ArrayList<LCONST> terms : map.get( p ) ){
//					
//					
//					
//					
//					for( int t = 0 ; t < lookahead; ++t ){
//						
//						EXPR new_lhs_non_stationary = null;
////						GRBVar lhs_var = null;
//						
//						if( rddl_state_vars.containsKey(p) ){
//							if( t == lookahead - 1 ){
//								continue;
//							}
//							//FIXME : stationarity assumption
//							new_lhs_non_stationary = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects )
//									.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(t+1) ), constants, objects);
////							lhs_var = new_lhs_non_stationary.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//						}else {
//							new_lhs_non_stationary = new_lhs_stationary.addTerm(TIME_PREDICATE, constants, objects )
//									.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(t) ), constants, objects);
////							lhs_var = new_lhs_non_stationary.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//						}
//						
//						//FIXME : stationarity assumption
//						EXPR new_rhs_non_stationary = new_rhs_stationary.addTerm(TIME_PREDICATE, constants, objects )
//								.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(t) ), constants, objects );
////						GRBVar rhs_var = new_rhs_non_stationary.getGRBConstr(GRB.EQUAL,  grb_model, constants, objects, type_map);
//						
//						System.out.println( p + " " + terms + " " + t );
//						EXPR lhs_with_future = new_lhs_non_stationary.addTerm(future_PREDICATE, constants, objects);
//						EXPR rhs_with_future = new_rhs_non_stationary.addTerm(future_PREDICATE, constants, objects);
//						
//						for( int future_id = 0 ; future_id < num_futures; ++future_id ){
//							EXPR lhs_future_id = lhs_with_future.substitute( 
//									Collections.singletonMap( future_PREDICATE, future_TERMS.get(future_id)), 
//									constants, objects );
//							EXPR rhs_future_id = rhs_with_future.substitute( 
//									Collections.singletonMap( future_PREDICATE, future_TERMS.get(future_id)), 
//									constants, objects );
//							
//							EXPR lhs_future = future_gen.getFuture( lhs_future_id, rand, objects );
//							EXPR rhs_future = future_gen.getFuture( rhs_future_id, rand, objects );
//
//							GRBVar lhs_var = lhs_future.getGRBConstr( 
//									GRB.EQUAL, grb_model, constants, objects, type_map);
//							GRBVar rhs_var = rhs_future.getGRBConstr( 
//									GRB.EQUAL, grb_model, constants, objects, type_map);
//							
//							grb_model.addConstr( lhs_var, GRB.EQUAL, rhs_var, "CPT_t_"+p.toString()+"_"+terms+"_"+t+"_"+future_id );
//
//							saved_expr.add( lhs_future );
//							saved_expr.add( rhs_future );
//
//							saved_vars.add( lhs_var );
//							saved_vars.add( rhs_var );
//							
//							if( future_gen.equals( FUTURE_SAMPLING.MEAN ) ){
//								break;
//							}
//						}
//						
//						grb_model.update();
//						
//					}
//					
//				}
//			}
//			
//		}
		
	@Override
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
//		for( int future_id = 0 ; future_id < num_futures ; ++future_id ){
//			for( int time = 0 ; time < lookahead; ++time ){
//				EXPR subs_tf = non_stationary.substitute( Collections.singletonMap( TIME_PREDICATE, TIME_TERMS.get(time)), 
//						constants, objects)
//						.substitute( Collections.singletonMap( future_PREDICATE, future_TERMS.get(future_id)), 
//								constants, objects);
//
//				GRBVar this_future_var = subs_tf.getGRBConstr( GRB.EQUAL, grb_model, constants, objects, type_map);
//				
//				saved_expr.add( subs_tf );
//				saved_vars.add( this_future_var );
//				//IDEA : weightby probability of trajecotry of futures
//				//what is the probability of a determinization 
//				all_sum.addTerm( 1.0d/num_futures, this_future_var );
//			}
//			if( future_gen.equals( FUTURE_SAMPLING.MEAN ) ){
//				break;
//			}
//		}
		

	
	@Override
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
	



	//This is added by Harish.
	protected void addRootPolicyConstraints(final GRBModel grb_model) throws Exception{
		GRBExpr old_obj = grb_model.getObjective();

		getHindSightConstraintExpr(hindsight_method).parallelStream().forEach( new Consumer< BOOL_EXPR >() {

			@Override
			public void accept( BOOL_EXPR t) {
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
	
	
	
	

	
	
	
	protected ArrayList<BOOL_EXPR> getHindSightConstraintExpr( HINDSIGHT_STRATEGY hindsight_method )  {
		ArrayList<BOOL_EXPR> ret = new ArrayList<BOOL_EXPR>();
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
											ret.add(new COMP_EXPR(ref_expr, addedd, COMP_EXPR.EQUAL));
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
										ret.add(new COMP_EXPR(ref_expr, addedd, COMP_EXPR.EQUAL));
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
										ret.add(new COMP_EXPR(ref_expr, addedd, COMP_EXPR.EQUAL));
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
	
	public static void main(String[] args ,RDDL rddl_object,State s ) throws Exception {
		System.out.println( Arrays.toString( args ));
		System.out.println( new HOPTranslate( Arrays.asList( args ),rddl_object,s ).doPlanInitState() );
	}
	
	
	@Override
	protected Map< EXPR, Double > outputResults( final GRBModel grb_model ) throws GRBException{
		
		System.out.println("------This is output results for GRB MODEL -------");
//		DecimalFormat df = new DecimalFormat("#.##########");
//		df.setRoundingMode( RoundingMode.DOWN );
		if( grb_model.get( IntAttr.SolCount ) == 0 ){
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
								double actual = grb_var.get( DoubleAttr.X );
									   
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
		
		System.out.println( "Maximum (unscaled) bound violation : " +  + grb_model.get( DoubleAttr.BoundVio	) );
		System.out.println("Sum of (unscaled) constraint violations : " + grb_model.get( DoubleAttr.ConstrVioSum ) );
		System.out.println("Maximum integrality violation : "+ grb_model.get( DoubleAttr.IntVio ) );
		System.out.println("Sum of integrality violations : " + grb_model.get( DoubleAttr.IntVioSum ) );
		System.out.println("Objective value : " + grb_model.get( DoubleAttr.ObjVal ) );
		
		return ret;
	}




	protected Map< EXPR, Double > outputAllResults( final GRBModel grb_model ) throws GRBException{


		System.out.println("------This is output results for GRB MODEL -------");
//		DecimalFormat df = new DecimalFormat("#.##########");
//		df.setRoundingMode( RoundingMode.DOWN );
		if( grb_model.get( IntAttr.SolCount ) == 0 ){
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
						TIME_TERMS.stream().forEach( new Consumer< LCONST >() {
							public void accept(LCONST time_term ) {
								future_TERMS.forEach( new Consumer<LCONST>() {
									@Override
									public void accept(LCONST future_term) {
										try{
										EXPR action_var = new PVAR_EXPR(pvar._sPVarName, terms)
												.addTerm(TIME_PREDICATE, constants, objects)
												.addTerm(future_PREDICATE, constants, objects)
												.substitute(Collections.singletonMap(TIME_PREDICATE, time_term), constants, objects)
												.substitute(Collections.singletonMap(future_PREDICATE, future_term), constants, objects);

										try {
											GRBVar grb_var = EXPR.grb_cache.get(action_var);
											System.out.println(action_var);
											//System.out.println(grb_var);
											assert (grb_var != null);
											double actual = grb_var.get(DoubleAttr.X);


											//NOTE : uncomment this part if having issues with constrained actions
											//such as if you get -1E-11 instead of 0,
											//and you are expecting a positive action >= 0
											String interm_val = State._df.format(actual);
											System.out.println(action_var + "Actual Value: " + actual + " rounded to " + interm_val);

											ret.put(action_var, Double.valueOf(interm_val));
										} catch (GRBException e) {
											e.printStackTrace();
											////System.exit(1);
										}

									}catch (Exception e){e.printStackTrace();}

									}
								});

							}
						});




					}
				});
			}

		});

		System.out.println( "Maximum (unscaled) bound violation : " +  + grb_model.get( DoubleAttr.BoundVio	) );
		System.out.println("Sum of (unscaled) constraint violations : " + grb_model.get( DoubleAttr.ConstrVioSum ) );
		System.out.println("Maximum integrality violation : "+ grb_model.get( DoubleAttr.IntVio ) );
		System.out.println("Sum of integrality violations : " + grb_model.get( DoubleAttr.IntVioSum ) );
		System.out.println("Objective value : " + grb_model.get( DoubleAttr.ObjVal ) );

		return ret;
	}







	public void setActionVariables(ArrayList<PVAR_INST_DEF> action_zero, GRBModel static_grb_model){



		//I need to work on setting initialization.
		Object val = null;

		HashMap<EXPR,Object> action_value = new HashMap<>();

		for(int i =0; i<action_zero.size();i++){

			PVAR_INST_DEF act = action_zero.get(i);
			if( act._oValue instanceof Double){
				 val = (Double) act._oValue; }
			else if(act._oValue instanceof Boolean){
				 val = (Boolean) act._oValue; }
			else{
				val = (Integer) act._oValue; }

			for(int j=0; j<future_TERMS.size();j++){
				try {
					EXPR action_var = new PVAR_EXPR(act._sPredName._sPVarName, act._alTerms)
							.addTerm(TIME_PREDICATE, constants, objects)
							.addTerm(future_PREDICATE, constants, objects)
							.substitute(Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(0)), constants, objects)
							.substitute(Collections.singletonMap(future_PREDICATE, future_TERMS.get(j)), constants, objects);
					action_value.put(action_var, val);
				}
				catch (Exception e){e.printStackTrace();}
			}

		}

		if(action_value.size()==0){
			return;

		}


		HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> src= new HashMap<>();
		src.putAll(rddl_action_vars);



		src.forEach( new BiConsumer<PVAR_NAME, ArrayList<ArrayList<LCONST>> >() {
			@Override
			public void accept(PVAR_NAME pvar, ArrayList<ArrayList<LCONST>> u) {
				u.parallelStream().forEach( new Consumer<ArrayList<LCONST>>() {
					@Override
					public void accept(ArrayList<LCONST> terms) {
						//System.out.print(pvar.toString());
						try{
/*
						EXPR pvar_expr = new PVAR_EXPR(pvar._sPVarName, terms)
								.addTerm(TIME_PREDICATE, constants, objects)
								.addTerm(future_PREDICATE, constants, objects);
*/
						//System.out.println("HERE IS THE ERROR");

						future_TERMS.forEach(new Consumer<LCONST>() {
							@Override
							public void accept(LCONST future_term) {
								try {
									EXPR action_var = new PVAR_EXPR(pvar._sPVarName, terms)
											.addTerm(TIME_PREDICATE, constants, objects)
											.addTerm(future_PREDICATE, constants, objects)
											.substitute(Collections.singletonMap(TIME_PREDICATE, TIME_TERMS.get(0)), constants, objects)
											.substitute(Collections.singletonMap(future_PREDICATE, future_term), constants, objects);
									GRBVar this_var = EXPR.grb_cache.get(action_var);

										this_var.set(DoubleAttr.Start, (Double) action_value.get(action_var));


								} catch (GRBException e) {
									e.printStackTrace();
								} catch (Exception e) {
									e.printStackTrace();
								}

							}
						});
					} catch (Exception e){e.printStackTrace();}
					}
				});
			}
		});



	}






	@Override
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
		//This is removed by Harish.
		all_votes.clear();












		//This Code is for Restricting the actions
		//System.out.println("Here is what I am restricting the actions");
		
	//		if(ret.isEmpty()) {
	//			return ret;
	//			
	//		}
	//		else {
	//			
	//			if(ret.size()>causeReq) {
	//				//ArrayList<PVAR_INST_DEF> ret1 = new ArrayList<PVAR_INST_DEF>() ;
	//				 
	//				Collections.shuffle(ret);
	//				ArrayList<PVAR_INST_DEF> ret1 = new ArrayList<PVAR_INST_DEF>(ret.subList(0, causeReq));
	//				return ret1;
	//			}
	//			else {
	//					
	//				return ret;
	//			}
	//			
	//			
	//			
	//			
	//		}
		












		return ret;
		
	}
	
	@Override
	protected void prepareModel( ) throws Exception{
		translate_time.ResumeTimer();
		System.out.println("--------------Translating Constraints (HOPTranslate.java)-------------");
		translateConstraints( static_grb_model );
		System.out.println("--------------Translating Reward (HOPTranslate.java) -------------");
		translateReward( static_grb_model );
		translate_time.PauseTimer();
	}
	
	@Override
	public Pair<ArrayList<Map< EXPR, Double >>,Integer> doPlan(HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> subs ,
															   final boolean recover ) throws Exception {
		ArrayList<Map< EXPR, Double >> ret_obj = new ArrayList<Map< EXPR, Double >>();
		System.out.println("------------------------------------------------this is doPlan (HOPTranslate.java Overrided)-------");
		translate_time.ResumeTimer();
		System.out.println("--------------Translating CPTs with random Generated Futures-------------");
		//This is commented by HARISH, FittedHOPTranslate does not generate futures
		
		translateCPTs( subs, static_grb_model );
		System.out.println("--------------Initial State-------------");
		translateInitialState( static_grb_model, subs );
		translate_time.PauseTimer();
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
			Double consensus_obj = static_grb_model.get(DoubleAttr.ObjVal);
			objectiveValues.add(consensus_obj);
			//System.out.println(outConsensus.toString());
			ret_obj.add(outConsensus);

		}
		cleanUp( static_grb_model );
		long endtime1 = System.currentTimeMillis();
		System.out.println("Gurobi Cleanup time = " +  (endtime1 - starttime1) );
		//This ensure that actions given are not more.
		return new Pair<>(ret_obj,exit_code);
	}











	public void removeRootPolicyConstraints(final GRBModel grb_model) throws GRBException{
		System.out.println("Number of Constraints Before:" + grb_model.get(IntAttr.NumConstrs));

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

		System.out.println("Number of Constraints After :"+ grb_model.get(IntAttr.NumConstrs));


		root_policy_constr.clear();

		root_policy_expr.clear();








	}

//	@Override
//	public Map< EXPR, Double >  doPlan( final ArrayList<PVAR_INST_DEF> initState,
//			final boolean recover ) throws Exception{
//
////		System.out.println( "Names : " );
////		RDDL.EXPR.name_map.forEach( (a,b) -> System.out.println( a + " " + b ) );
////		grb_model.set( GRB.IntParam.SolutionLimit, 1 );
////		prepareModel( initState ); model already prepared in constructor
//		HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> subs = getConsts(initState);
//		
//		translate_time.ResumeTimer();
//		System.out.println("--------------Translating CPTs-------------");
//		translateCPTs( subs );
//		System.out.println("--------------Initial State-------------");
//		translateInitialState( subs );
//		translate_time.PauseTimer();	
//		
//		try{
//			goOptimize();
//		}catch( GRBException exc ){
//			int error_code = exc.getErrorCode();
//			if( error_code == GRB.ERROR_OUT_OF_MEMORY  && recover ){
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





























//This part is added by Harish for getting thing don with random policy.


//	protected HashMap<ArrayList<LCONST>,Object> getActionInstantiations(PVAR_NAME action_var, TYPE_NAME action_type, Random rand){
//		//This function gives the intansiations of the parameters.
//		//
//
//
//
//
//		HashMap<ArrayList<LCONST>,Object> action_terms_assign = new HashMap<>();
//
//		ArrayList<TYPE_NAME> temp_objects = object_type_name.get(action_var);
//		ArrayList<LCONST> action_terms    = new ArrayList<>();
//		for(int i = 0;i<temp_objects.size();i++){
//			ArrayList<LCONST> temp_array =rddl_state._hmObject2Consts.get(temp_objects.get(i));
//			//This is selecting the object value and creating a ArrayList<LCONST> whichs goes as _alTerms
//			int j = rand.nextInt(temp_array.size());
//			LCONST val = temp_array.get(j);
//			if(val instanceof RDDL.OBJECT_VAL){
//
//
//				RDDL.OBJECT_VAL new_val = new RDDL.OBJECT_VAL(val._sConstValue);
//
//				action_terms.add(new_val);
//
//
//
//			}
//
//		}
//
//
//		//Selecting the value of each object.
//		if(action_type.equals(TYPE_NAME.REAL_TYPE)){
//			//select_range Has values [min,max]
//			ArrayList<Double> select_range = value_range.get(action_var);
//			Double take_action_val = select_range.get(0) + ((select_range.get(1)-select_range.get(0)) * rand.nextFloat());
//			action_terms_assign.put(action_terms,take_action_val);
//
//		}
//
//
//
//
//		if(action_type.equals(TYPE_NAME.BOOL_TYPE)){
//
//			ArrayList<Boolean>  select_range = value_range.get(action_var);
//			int j = rand.nextInt(select_range.size());
//
//			Boolean take_action_val = select_range.get(j);
//			action_terms_assign.put(action_terms,take_action_val);
//
//
//		}
//
//
//
//
//
//		return action_terms_assign;
//
//
//
//
//	}
//
//
//
//
//
//
//
//
//	protected ArrayList<PVAR_INST_DEF> getRandomAction(State s, Random randint) throws EvalException{
//		//Need to Write Function for Getting a Random Action.
//
//
//
//
//
//
//
//		ArrayList<PVAR_INST_DEF> final_output_actions  = new ArrayList<>();
//
//		//This is  a buffer list to check the instansiations are already exists or not.
//		ArrayList<ArrayList<LCONST>> alaction_terms  = new ArrayList<>();
//		HashMap<ArrayList<LCONST>,Object> final_action_val = new HashMap<>();
//
//
//
//		for(PVAR_NAME action_var : rddl_action_vars.keySet()){
//
//			TYPE_NAME type_val = s._hmPVariables.get(action_var)._typeRange;
//
//
//
//
//
//			//This function instansiates t
//			HashMap<ArrayList<LCONST>,Object> action_terms_val = getActionInstantiations(action_var,type_val,randint);
//			for(ArrayList<LCONST> o : action_terms_val.keySet()){
//				if(!alaction_terms.contains(o)){
//
//					Double rand_number = randint.nextDouble();
//
//					if(! (rand_number< rejection_prob)){
//						alaction_terms.add(o);
//						final_action_val.put(o,action_terms_val.get(o));
//					}
//
//
//
//				}
//
//
//			}
//
//
//
//
//
//
//
//
//
//			ArrayList<PVAR_INST_DEF> output_actions = new ArrayList<>();
//			for(ArrayList<LCONST> key : final_action_val.keySet()){
//
//
//				PVAR_INST_DEF aa  = new PVAR_INST_DEF(action_var._sPVarName,final_action_val.get(key),key);
//
//				final_output_actions.add(aa);
//
//
//			}
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
//
//
//
//
//
//
//
//		}
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
//
//
//		return final_output_actions;
//
//
//
//
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
//	protected void runRandomPolicy(final State rddl_state , int trajectory_length, int number_trajectories, Random rand1) throws EvalException {
//
//
//		HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> traj_inital_state  = deepCopyState(rddl_state);
//
//		buffer_state.clear();
//		buffer_action.clear();
//		buffer_reward.clear();
//
//
//
//
//		for(int j=0;j<number_trajectories; j++){
//			//HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> traj_state_values  = deepCopyState(rddl_state);
//
//			rddl_state.copyStateRDDLState(traj_inital_state,true);
//			traj_inital_state = deepCopyState(rddl_state);
//
//
//			ArrayList<HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>>> store_traj_states = new ArrayList<>();
//			ArrayList<Double> store_traj_rewards = new ArrayList<>();
//			ArrayList<ArrayList<PVAR_INST_DEF>> store_traj_actions = new ArrayList<>();
//			boolean check_action_feasible = false;
//			boolean all_infeasible        = false;
//
//			for(int i=0;i<trajectory_length;i++){
//
//
//				check_action_feasible = false;
//				ArrayList<PVAR_INST_DEF> traj_action =null;
//
//
//				int cur_while_check = 0;
//
//				while(!check_action_feasible || (cur_while_check<number_of_iterations)){
//					//System.out.println("ITERATION.");
//					cur_while_check = cur_while_check+1;
//
//
//					traj_action = getRandomAction(rddl_state,rand1);
//					check_action_feasible = rddl_state.checkActionConstraints(traj_action);
//
//
//
//				}
//
//
//				//When the actions are infeasible.
//				if(! check_action_feasible){
//
//
//					System.out.println("The actions are not feasible");
//					all_infeasible = true;
//					break;
//
//
//
//				}
//
//
//
//
//
//
//
//
//
//				//Check the action Constraint
//
//
//
//
//
//				HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> store_state =  deepCopyState(rddl_state);
//
//
//
//
//				store_traj_states.add(store_state);
//
//				//Advance to Next State
//				rddl_state.computeNextState(traj_action, rand);
//
//				//Calculate Immediate Reward
//				final double immediate_reward = ((Number)rddl_domain._exprReward.sample(
//						new HashMap<LVAR,LCONST>(),rddl_state, rand)).doubleValue();
//
//
//				store_traj_rewards.add(immediate_reward);
//				store_traj_actions.add(traj_action);
//
//
//
//
//
//
//				//System.out.println("Immediate Reward :"+ immediate_reward);
//
//
//
//
//
//				rddl_state.advanceNextState();
//
//
//
//
//
//
//			}
//
//
//
//			//This is checking the trajectory turned out to be bad.
//			if(check_action_feasible && all_infeasible){
//				// we are overriding the pre_buffer_state values.
//				int traj_id = rand1.nextInt(pre_buffer_state.size());
//				store_traj_states  = pre_buffer_state.get(traj_id);
//				store_traj_actions = pre_buffer_action.get(traj_id);
//				store_traj_rewards = pre_buffer_reward.get(traj_id);
//
//
//
//			}
//
//
//			buffer_state.add(store_traj_states);
//			buffer_action.add(store_traj_actions);
//			buffer_reward.add(store_traj_rewards);
//
//
//
//		}
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
//
//
//
//
//
//
//
//	}
//
//
//
//
//	protected void checkNonLinearExpressions(final State rddl_state) throws Exception {
//		//Clear the things
//		not_pwl_expr.clear();
//
//
//
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
//		ArrayList<HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>>> pvar_variables = new ArrayList<>();
//
//		pvar_variables.add(rddl_state_vars); pvar_variables.add(rddl_interm_vars); pvar_variables.add(rddl_observ_vars);
//
//
//
//		for( final HashMap<PVAR_NAME, ArrayList<ArrayList<LCONST>>> map : pvar_variables ) {
//
//			for (final PVAR_NAME p : map.keySet()) {
//
//				Map<LVAR, LCONST> subs = new HashMap<>();
//				CPF_DEF cpf = null;
//
//
//				if (rddl_state_vars.containsKey(p)) {
//					cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName + "'"));
//
//				} else {
//					cpf = rddl_state._hmCPFs.get(new PVAR_NAME(p._sPVarName));
//				}
//
//				ArrayList<RDDL.LTERM> raw_terms =cpf._exprVarName._alTerms;
//				ArrayList<EXPR> final_pwl_cond = new ArrayList<>();
//				ArrayList<EXPR> final_pwl_true = new ArrayList<>();
//
//				//This loop is for $t1, $t2, $t3..........
//				for (final ArrayList<LCONST> terms : map.get(p)) {
//					//This piece of code is Changed by HARISH
//					//System.out.println( "CPT for " + p.toString() + terms );
//					Map<LVAR,LCONST> subs1 = getSubs( cpf._exprVarName._alTerms, terms );
//
//
//					//new COMP_EXPR(raw_terms.get(0),terms.get(0),"==").toString()
//					if(!cpf._exprEquals.substitute(subs1,constants,objects).isPiecewiseLinear(constants,objects)){
//
//						if(!not_pwl_expr.contains(cpf._exprEquals)){
//							not_pwl_expr.add(cpf._exprEquals);
//
//						}
//
//
//
//						EXPR final_expr = generateDataForPWL(cpf._exprEquals.substitute(subs1,constants,objects), raw_terms);
//
//
//
//						//This is Getting Condition.
//
//						BOOL_EXPR conditional_state = new BOOL_CONST_EXPR(true);
//
//						for(int i=0;i<terms.size();i++){
//
//							BOOL_EXPR cur_cond_statement = conditional_state;
//
//							RDDL.COMP_EXPR temp_expr = new RDDL.COMP_EXPR(raw_terms.get(i), terms.get(i), "==");
//
//							conditional_state = new RDDL.CONN_EXPR(cur_cond_statement,temp_expr,"^");
//
//
//
//
//						}
//
//
//						final_pwl_true.add(final_expr);
//						final_pwl_cond.add(conditional_state);
//
//
//
//
//
//
//
//
//
//						//System.out.println("dkjfkdjfkdj");
//
//
//
//
//
//					}
//
//
//
//
//				}
//
//				EXPR ifelse_expr = new BOOL_CONST_EXPR(true);
//				if(!final_pwl_cond.isEmpty()){
//
//					ifelse_expr = recursiveAdditionIfElse(final_pwl_cond,final_pwl_true,1);
//
//					replace_cpf_pwl.put(p,ifelse_expr);
//
//
//
//
//				}
//
//
//
//
//
//			}
//		}
//
//
//
//
//	}
//
//	public EXPR recursiveAdditionIfElse(List<EXPR> condition_part,List<EXPR> true_part,Integer check_first){
//
//		EXPR cond_expr=condition_part.get(0);
//		EXPR true_expr=true_part.get(0);
//		if(condition_part.size()==1){
//
//			return new RDDL.IF_EXPR(cond_expr,true_expr,new REAL_CONST_EXPR(0.0));
//
//		}
//
//
//
//		List<EXPR> true_sub_list = new ArrayList<>();
//		List<EXPR> cond_sub_list = new ArrayList<>();
//		true_sub_list = true_part.subList(1,true_part.size());
//		cond_sub_list = condition_part.subList(1,condition_part.size());
//
//
//		return new RDDL.IF_EXPR(cond_expr,true_expr,recursiveAdditionIfElse(cond_sub_list,true_sub_list,2));
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
//
//
//
//
//
//
//
//
//	}
//
//
//
//
//	//This will generate Data.
//	public EXPR generateDataForPWL(EXPR e, ArrayList<LTERM> raw_terms) throws Exception {
//
//
//
//		//Getting desired format  as  a String
//		ArrayList<PVAR_NAME> input_variables = new ArrayList<>();
//		HashMap<Integer,ArrayList<Object>> input_array = new HashMap();
//
//		HashMap<Integer,String> input_R_array = new HashMap<Integer, String>();
//		String output_R_array = new String();
//		output_R_array = "c(";
//
//
//
//
//
//
//
//		ArrayList<Object> output_array       = new ArrayList<>();
//		for(int i=0;i<buffer_state.size();i++){
//			ArrayList<HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>>> state_trajectory  = buffer_state.get(i);
//			ArrayList<ArrayList<PVAR_INST_DEF>> action_trajectory = buffer_action.get(i);
//
//
//			for(int j=0;j<buffer_state.get(i).size();j++){
//
//				HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> state_value = state_trajectory.get(j);
//				ArrayList<PVAR_INST_DEF> action_value = action_trajectory.get(j);
//
//
//				//This is a global temp variable which stores the values.m
//				variables_names.clear();
//
//
//				EXPR temp  = recursionSubstitution(e,state_value,action_value);
//				Double val = temp.getDoubleValue(constants,objects);
//
//
//				if(i==0 && j==0 && !variables_names.isEmpty()){
//
//					input_variables.addAll(variables_names.keySet());
//
//
//				}
//
//
//
//
//				for(int k=0;k<input_variables.size();k++){
//
//					if(input_array.containsKey(k)){
//						input_array.get(k).add(variables_names.get(input_variables.get(k)));
//						String temp_str =input_R_array.get(k);
//						input_R_array.put(k,temp_str + variables_names.get(input_variables.get(k)).toString()+", ");
//
//
//						//System.out.println("dkjfkdjfkdfkd");
//
//
//					}
//					else{
//						ArrayList<Object> temp_array = new ArrayList<>();
//						temp_array.add(variables_names.get(input_variables.get(k)));
//						input_array.put(k,temp_array);
//						String temp_str ="c(";
//						input_R_array.put(k,temp_str + variables_names.get(input_variables.get(k)).toString()+", ");
//
//					}
//
//
//
//
//
//				}
//				output_array.add(val);
//				String temp_str = output_R_array;
//				output_R_array = temp_str + val.toString() + ", ";
//
//
//
//
//
//
//
//			}
//
//
//
//
//
//		}
//
//
//
//
//		//Making Sure to close the brackets.
//		HashMap<PVAR_NAME,String> final_input_R_data = new HashMap<>();
//
//		for( Map.Entry<Integer,String> entry1 : input_R_array.entrySet()){
//
//			input_variables.get(entry1.getKey());
//
//			String temp_str = entry1.getValue();
//
//			temp_str = temp_str.trim();
//			temp_str = temp_str.substring(0,temp_str.length()-1) + ")";
//
//			//System.out.println("Dkfjdkfkdfkj");
//			final_input_R_data.put(input_variables.get(entry1.getKey()),temp_str);
//
//
//
//
//		}
//
//
//
//
//		String final_output_R_data = output_R_array.trim().substring(0,output_R_array.trim().length()-1) + ")";
//
//
//
//
//
//
//
//
//		//////////??????#############################################################################
//		//Getting R functions.
//
//
//		//Not thinking about optimizing the code, Just make it work.
//		//This is for number of examples
//
//		long start_timer = System.currentTimeMillis();
//		Rengine engine = Rengine.getMainEngine();
//		if(engine == null)
//			engine = new Rengine(new String[] {"--vanilla"}, false, null);
//
//		//Rengine engine  = new Rengine(new String[] {"--no-save"},false,null);
//		engine.eval("library(earth)");
//		String feature_format = new String();
//		Integer check = 0;
//		for( Map.Entry<PVAR_NAME,String> entry1 : final_input_R_data.entrySet()){
//			engine.eval(entry1.getKey()._sPVarName + "<-"+ entry1.getValue());
//			if(check==0){
//				feature_format = entry1.getKey()._sPVarName;
//				check =1;
//
//			}
//			else{
//				feature_format.concat(" + "+ entry1.getKey()._sPVarName);
//			}
//
//
//
//		}
//
//		engine.eval("target <-" + final_output_R_data );
//
//		engine.eval("model<-earth( target ~ " + feature_format + ",nprune=2)");
//		String rss_val =engine.eval("format(model$rss)").asString();
//		String gcv_val =engine.eval("format(model$gcv)").asString();
//
//		engine.eval("print(summary(model))");
//		System.out.println("THE GCV VALUE :" + gcv_val);
//		System.out.println("The RSS VALUE :" + rss_val);
//
//		engine.eval("a=predict(model,1)");
//
//
//		String earth_output = engine.eval("format(model,style='bf')").asString();
//
//		long end_timer = System.currentTimeMillis();
//
//
//
//
//		running_R_api = (double) end_timer - start_timer;
//
//
//
//
//
//
//		//System.out.println(earth_output);
//
//
//		//This will parse and give a EXPR Output.
//		EXPR final_expr =parseEarthROutput(earth_output,input_variables,raw_terms);
//
//		return(final_expr);
//
//
//
//
//
//
//	}
//
//
//	public EXPR parseEarthROutput(String earthOutput, ArrayList<PVAR_NAME> input_variables, ArrayList<LTERM> raw_terms) throws Exception {
//
//
//
//		String[] list_output = earthOutput.split("\n");
//
//
//		ArrayList<String> string_pvar = new ArrayList<>();
//		for(int i=0;i<input_variables.size();i++){
//			string_pvar.add(input_variables.get(i)._sPVarName);
//
//		}
//
//
//		HashMap<String,Double> coefficient_mapping = new HashMap<>();
//		HashMap<String,EXPR> hinge_function  = new HashMap<>();
//
//		Double bias = 0.0;
//		//Parsing things with equations.
//		for(int i=0;i<list_output.length;i++){
//			String temp_str = list_output[i].trim();
//			//System.out.println(temp_str);
//
//			if(temp_str.equals("")){continue;}
//
//			//This is for Bias,
//			if(!(temp_str.contains("-") || temp_str.contains("+"))){
//				bias =Double.parseDouble(temp_str);
//				//System.out.println(bias);
//
//			}
//
//
//			//This is for : - 0.08444618 * bf1
//			if(temp_str.contains("*")){
//				temp_str = temp_str.replaceAll("\\s","");
//				temp_str = temp_str.replaceAll("\\+","");
//				String [] term_val = temp_str.split("\\*");
//				NumberFormat format = NumberFormat.getInstance();
//
//				Double coeffic = format.parse(term_val[0]).doubleValue();
//
//				coefficient_mapping.put(term_val[1],coeffic);
//
//
//			}
//
//
//			//This is for : bf1  h(53.2847-rlevel)
//
//			if(temp_str.contains("bf") && temp_str.contains("h(")){
//				//System.out.println("dkjfkdfkdfj");
//				String[] term_val =temp_str.split("\\s");
//
//				String key_val = term_val[0];
//				String hinge_str = term_val[2];
//
//				hinge_str = hinge_str.replace("h(","");
//				hinge_str = hinge_str.replace(")","");
//
//				String [] hinge_values = hinge_str.split("-");
//
//				Double real_val = 0.0;
//
//
//
//				if(string_pvar.contains(hinge_values[0])){
//
//					real_val = Double.parseDouble(hinge_values[1]);
//
//					PVAR_EXPR temp_pvar_expr        = new PVAR_EXPR(hinge_values[0],raw_terms);
//					REAL_CONST_EXPR temp_const_expr = new REAL_CONST_EXPR(real_val);
//
//					RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_pvar_expr,temp_const_expr,"-");
//					RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new REAL_CONST_EXPR(0.0), temp_oper_expr,"max");
//
//					hinge_function.put(key_val,max_oper_expr);
//					//System.out.println(max_oper_expr.toString());
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
//
//
//				}
//				if(string_pvar.contains(hinge_values[1])){
//					real_val = Double.parseDouble(hinge_values[0]);
//					PVAR_EXPR temp_pvar_expr        = new PVAR_EXPR(hinge_values[1],raw_terms);
//					REAL_CONST_EXPR temp_const_expr = new REAL_CONST_EXPR(real_val);
//
//					RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_const_expr,temp_pvar_expr,"-");
//					RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new REAL_CONST_EXPR(0.0), temp_oper_expr,"max");
//
//					hinge_function.put(key_val,max_oper_expr);
//					//System.out.println(max_oper_expr.toString());
//
//
//
//
//				}
//
//
//			}
//
//
//
//
//
//
//
//
//
//		}
//
//
//		REAL_CONST_EXPR bias_expr = new REAL_CONST_EXPR(bias);
//		RDDL.OPER_EXPR final_expr =new RDDL.OPER_EXPR(new REAL_CONST_EXPR(0.0),bias_expr,"+");
//
//
//		Integer temp_count = 0;
//
//		for(String key: coefficient_mapping.keySet()){
//
//			Double real_value = coefficient_mapping.get(key);
//			REAL_CONST_EXPR temp_real_expr= new REAL_CONST_EXPR(real_value);
//			RDDL.OPER_EXPR temp_oper_expr  = new RDDL.OPER_EXPR(temp_real_expr,hinge_function.get(key),"*");
//
//			RDDL.OPER_EXPR temp_final_expr = final_expr;
//			final_expr = new RDDL.OPER_EXPR(temp_final_expr,temp_oper_expr,"+");
//		}
//
//
//
//		return(final_expr);
//
//
//
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
//	public EXPR recursionSubstitution(EXPR e, HashMap<PVAR_NAME,HashMap<ArrayList<LCONST>,Object>> state_value, ArrayList<PVAR_INST_DEF> action_value) throws Exception {
//
//
//
//		if(e.isConstant(constants,objects)){
//
//			double val        = e.getDoubleValue(constants,objects);
//			EXPR real_expr    = new REAL_CONST_EXPR(val);
//
//			return real_expr;
//
//		}
//
//
//
//		if(e instanceof PVAR_EXPR){
//
//
//
//			PVAR_NAME key = ((PVAR_EXPR) e)._pName;
//
//
//
//			if(state_value.containsKey(key)){
//				HashMap<ArrayList<LCONST>,Object> t = state_value.get(((PVAR_EXPR) e)._pName);
//
//				//Value is Available
//				if(t.containsKey(((PVAR_EXPR) e)._alTerms)){
//					Object val = t.get(((PVAR_EXPR) e)._alTerms);
//
//					if(val instanceof Double){
//
//						variables_names.put(key,val);
//						return new REAL_CONST_EXPR((Double) val);
//
//
//
//					}
//					else{
//						throw new EvalException("THis case not Handled");
//					}
//
//
//
//
//				}
//				else{
//
//					//Get the Default Value.
//
//					Object val = rddl_state_default.get(key);
//
//					if(val instanceof Double){
//						variables_names.put(key,val);
//
//						return new REAL_CONST_EXPR((Double) val);
//
//
//
//					}
//					else{
//						throw new EvalException("THis case not Handled");
//					}
//
//
//
//
//
//				}
//
//
//
//			}
//
//
//
//
//
//
//
//
//
//		}
//
//
//
//
//
//		if(e instanceof RDDL.OPER_EXPR){
//
//			EXPR e1   = ((RDDL.OPER_EXPR) e)._e1;
//			EXPR e2   = ((RDDL.OPER_EXPR) e)._e2;
//			String op = ((RDDL.OPER_EXPR) e)._op;
//
//
//
//			EXPR real_expr_1  = recursionSubstitution(e1,state_value,action_value);
//			EXPR real_expr_2  = recursionSubstitution(e2,state_value,action_value);
//
//
//			EXPR new_oper = new RDDL.OPER_EXPR( real_expr_1, real_expr_2,op);
//
//			return new_oper;
//
//
//
//
//
//
//
//		}
//
//
//
//
//
//
//		return null;
//
//
//
//	}
//
//
//

































































}
