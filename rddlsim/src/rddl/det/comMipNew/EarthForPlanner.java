package rddl.det.comMipNew;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.rosuda.JRI.Rengine;
import rddl.EvalException;
import rddl.RDDL;
import rddl.State;
import util.Pair;

import java.text.NumberFormat;
import java.util.*;

import rddl.RDDL.EXPR;

public class EarthForPlanner {


    public EXPR fitPWL(EXPR e, ArrayList<RDDL.LTERM> raw_terms, State s, ArrayList[] buffers, RandomDataGenerator random) throws Exception{

        ArrayList<ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>>> buffer_state = buffers[0];
        ArrayList<ArrayList<ArrayList<RDDL.PVAR_INST_DEF>>> buffer_action = buffers[1];


        ArrayList<Object> val = generateFeatureTargetEarth(e,s,buffer_state,buffer_action,random);
        String earth_PWL = runEarth((HashMap<RDDL.PVAR_NAME,String>)val.get(0),(String)val.get(1));
        EXPR final_expr =reconstruct_expr(earth_PWL,(ArrayList<RDDL.PVAR_NAME>)val.get(2),raw_terms);
        return final_expr;

    }



    protected ArrayList<String> getInputVector(TreeSet<RDDL.PVAR_NAME>variables,HashMap<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>lvar_lcont_map,
                                               HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> state_value,
                                               ArrayList<RDDL.PVAR_INST_DEF> action_value){

        //I need to debug and work.
        //This loop is for ordering the inputVariables via variables.
        for(RDDL.PVAR_NAME key : variables){
            ArrayList<RDDL.LCONST> cur_lconsts= lvar_lcont_map.get(key);
            if(state_value.containsKey(key)){
                HashMap<ArrayList<RDDL.LCONST>,Object> cur_lconst_object = state_value.get(key);







            }




        }
        ArrayList temp_array = new ArrayList<>();
        temp_array.add("1"); temp_array.add("2");
        return temp_array;


    }



    public ArrayList<Object> generateFeatureTargetEarth(RDDL.EXPR e, State s, ArrayList<ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>>> buffer_state,
                                                                                                              ArrayList<ArrayList<ArrayList<RDDL.PVAR_INST_DEF>>> buffer_action, RandomDataGenerator random) throws Exception{


        ArrayList<RDDL.PVAR_NAME> input_variables         = new ArrayList<>();
        HashMap<Integer,String> input_Feat_R_array  = new HashMap<>();
        String output_R_array                             = new String();
        output_R_array                                    = "c(";
        ArrayList<Object> output_array                    = new ArrayList<>();
        int count   = 0;
        TreeSet<Pair> var_order_lconst = new TreeSet<>();
        TreeSet<RDDL.PVAR_NAME> variables = new TreeSet<>();
        for(int i=0;i<buffer_state.size();i++){
            ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>> state_trajectory  = buffer_state.get(i);
            ArrayList<ArrayList<RDDL.PVAR_INST_DEF>> action_trajectory = buffer_action.get(i);
            for(int j=0;j<buffer_state.get(i).size();j++){
                HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> state_value = state_trajectory.get(j);
                ArrayList<RDDL.PVAR_INST_DEF> action_value = action_trajectory.get(j);
                var_order_lconst = new TreeSet<>();
                variables = new TreeSet<>();

                //This is for the Input features.
                HashSet<Pair> Gfluents = new HashSet<>();
                e.collectGFluents(null,s,Gfluents);

                HashMap<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>> var_lconsts = new HashMap<>();
                //Var_order_lconst will be passed to other functions (This is the order in variables and not variables+Lconst)

                for(Pair key : Gfluents){
                    variables.add((RDDL.PVAR_NAME) key._o1);
                    var_lconsts.put((RDDL.PVAR_NAME)key._o1,(ArrayList<RDDL.LCONST>)key._o2);

                }
//                for(RDDL.PVAR_NAME key : variables){
//                    var_order_lconst.add(new Pair((RDDL.PVAR_NAME)key,var_lconsts.get(key)));
//                }

                //The order is as per variables which is a TreeSet.
                ArrayList<String> si1 = getInputVector(variables,var_lconsts,state_value,action_value);
                for(int k=0;k<si1.size();k++){
                    //not the first time.
                    if(input_Feat_R_array.containsKey(k)){
                        String temp_str =si1.get(k);
                        input_Feat_R_array.put(k,input_Feat_R_array.get(k) +temp_str+", ");
                    }
                    else{
                        String temp_str ="c(";
                        input_Feat_R_array.put(k,temp_str + si1.get(k)+", ");
                    }
                }

                count+=1;

                //This is for target value.
                HashMap subs = new HashMap<RDDL.LVAR,RDDL.LCONST>();
                double target_val = (double)e.sample(subs,s,random);
                String temp_str = output_R_array;
                output_R_array = temp_str + String.valueOf(target_val) + ", ";
            }
        }
        //Making Sure to close the brackets.
        HashMap<RDDL.PVAR_NAME,String> final_input_R_data = new HashMap<>();





        int k = 0 ;
        for(RDDL.PVAR_NAME key : variables){

            String temp_str =input_Feat_R_array.get(k);
            temp_str = temp_str.trim();
            temp_str = temp_str.substring(0,temp_str.length()-1) + ")";
            final_input_R_data.put(key,temp_str);
            k= k+1;

        }
        String final_output_R_data = output_R_array.trim().substring(0,output_R_array.trim().length()-1) + ")";
        //This keeps track of order of features


        ArrayList final_output_list = new ArrayList<Object>();
        final_output_list.add(final_input_R_data);
        final_output_list.add(final_output_R_data);
        final_output_list.add(variables);
        //final_output_list.add(var_order_lconst);


        return final_output_list;

    }



    protected String runEarth(HashMap<RDDL.PVAR_NAME,String> input_features, String output ) throws Exception{

        //Starting Rengine.
        Rengine engine = Rengine.getMainEngine();
        if(engine == null)
            engine = new Rengine(new String[] {"--vanilla"}, false, null);

        //Rengine engine  = new Rengine(new String[] {"--no-save"},false,null);
        engine.eval("library(earth)");
        String feature_format = new String();
        Integer check = 0;
        for( Map.Entry<RDDL.PVAR_NAME,String> entry1 : input_features.entrySet()){
            engine.eval(entry1.getKey()._sPVarName + "<-"+ entry1.getValue());
            if(check==0){
                feature_format = entry1.getKey()._sPVarName;
                check =1; }
            else{
                feature_format.concat(" + "+ entry1.getKey()._sPVarName); }
        }

        engine.eval("target <-" + output );
        engine.eval("model<-earth( target ~ " + feature_format + ",nprune=2)");
        String rss_val =engine.eval("format(model$rss)").asString();
        String gcv_val =engine.eval("format(model$gcv)").asString();
        engine.eval("print(summary(model))");
        engine.eval("a=predict(model,1)");
        String earth_output = engine.eval("format(model,style='bf')").asString();

        //Need to be careful or order of input features.
        return earth_output;

    }





    public RDDL.EXPR reconstruct_expr(String earthOutput, ArrayList<RDDL.PVAR_NAME> input_variables,ArrayList<RDDL.LTERM> raw_terms) throws Exception {

        //Sample Earth Output
        //Earth Output
        // 1 +
        //   1.3 * bf1
        //
        // bf1 : h(53.2847-rlevel)
        //Where h is the hinge Function.

        String[] list_output = earthOutput.split("\n");
        ArrayList<String> string_pvar = new ArrayList<>();
        for(int i=0;i<input_variables.size();i++){
            string_pvar.add(input_variables.get(i)._sPVarName);
        }
        HashMap<String,Double> coefficient_mapping = new HashMap<>();
        HashMap<String, RDDL.EXPR> hinge_function  = new HashMap<>();
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
                    RDDL.PVAR_EXPR temp_pvar_expr        = new RDDL.PVAR_EXPR(hinge_values[0],raw_terms);

                    RDDL.REAL_CONST_EXPR temp_const_expr = new RDDL.REAL_CONST_EXPR(real_val);
                    RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_pvar_expr,temp_const_expr,"-");
                    RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new RDDL.REAL_CONST_EXPR(0.0), temp_oper_expr,"max");
                    hinge_function.put(key_val,max_oper_expr);

                }
                if(string_pvar.contains(hinge_values[1])){
                    real_val = Double.parseDouble(hinge_values[0]);
                    RDDL.PVAR_EXPR temp_pvar_expr        = new RDDL.PVAR_EXPR(hinge_values[1],raw_terms);
                    RDDL.REAL_CONST_EXPR temp_const_expr = new RDDL.REAL_CONST_EXPR(real_val);

                    RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_const_expr,temp_pvar_expr,"-");
                    RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new RDDL.REAL_CONST_EXPR(0.0), temp_oper_expr,"max");

                    hinge_function.put(key_val,max_oper_expr);

                }
            }

        }
        RDDL.REAL_CONST_EXPR bias_expr = new RDDL.REAL_CONST_EXPR(bias);
        RDDL.OPER_EXPR final_expr =new RDDL.OPER_EXPR(new RDDL.REAL_CONST_EXPR(0.0),bias_expr,"+");
        Integer temp_count = 0;
        for(String key: coefficient_mapping.keySet()){
            Double real_value = coefficient_mapping.get(key);
            RDDL.REAL_CONST_EXPR temp_real_expr= new RDDL.REAL_CONST_EXPR(real_value);
            RDDL.OPER_EXPR temp_oper_expr  = new RDDL.OPER_EXPR(temp_real_expr,hinge_function.get(key),"*");
            RDDL.OPER_EXPR temp_final_expr = final_expr;
            final_expr = new RDDL.OPER_EXPR(temp_final_expr,temp_oper_expr,"+");
        }
        return final_expr;



    }





}
