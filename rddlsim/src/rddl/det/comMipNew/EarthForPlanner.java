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


    public EXPR fitPWL(EXPR e, State s, ArrayList[] buffers, HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables,HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes, RandomDataGenerator random) throws Exception{

        ArrayList<ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>>> buffer_state = buffers[0];
        ArrayList<ArrayList<ArrayList<RDDL.PVAR_INST_DEF>>> buffer_action = buffers[1];


        ArrayList<Object> val = generateFeatureTargetEarth(e,s,buffer_state,buffer_action,hm_variables,hmtypes,random);
        String earth_PWL = runEarth((HashMap<String,String>)val.get(0),(String)val.get(1));
        EXPR final_expr =reconstruct_expr(earth_PWL,(TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>>) val.get(2),hm_variables,hmtypes);
        return final_expr;


    }



    protected HashMap<String,String> getInputVector(TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>>variables, HashMap<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>lvar_lcont_map,
                                               HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> state_value,
                                               ArrayList<RDDL.PVAR_INST_DEF> action_value,HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables,HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes) throws Exception {

        //I need to debug and work.
        //This loop is for ordering the inputVariables via variables.
        HashMap<String,String> feat_values = new HashMap<>();
        for(String key : variables.keySet()){
            ArrayList<RDDL.LCONST> cur_lconsts= variables.get(key)._o2;

            if(state_value.containsKey(variables.get(key)._o1)){
                HashMap<ArrayList<RDDL.LCONST>,Object> cur_lconst_object = state_value.get(variables.get(key)._o1);
                Object feat_val = null;
                if(cur_lconst_object.containsKey(cur_lconsts)){
                     feat_val = cur_lconst_object.get(cur_lconsts);
                }else{
                    RDDL.PVAR_NAME pname =variables.get(key)._o1;
                    if(hm_variables.get(pname) instanceof RDDL.PVARIABLE_STATE_DEF){
                        feat_val = ((RDDL.PVARIABLE_STATE_DEF) hm_variables.get(pname))._oDefValue;
                    }else if(hm_variables.get(pname) instanceof RDDL.PVARIABLE_ACTION_DEF){
                        feat_val = ((RDDL.PVARIABLE_ACTION_DEF) hm_variables.get(pname))._oDefValue;
                    }else{
                        throw new Exception("This is no Default value for the type you are looking");
                    }
                }

                if(feat_val instanceof Double){
                    feat_values.put(key,String.valueOf((Double)feat_val ));
                }else if(feat_val instanceof Integer){
                    feat_values.put(key,String.valueOf((Integer) feat_val ));
                }else if(feat_val instanceof Boolean){
                    double f_val = (Boolean) feat_val ? 1.0 : 0.0;
                    feat_values.put(key,String.valueOf(f_val));
                }else if(feat_val instanceof RDDL.ENUM_VAL){
                    int f_val = ((RDDL.ENUM_VAL)feat_val).enum_to_int(hmtypes,hm_variables;
                    feat_values.put(key,String.valueOf(f_val));

                }else{
                    throw new Exception("The value is not a Double,Integer,boolean or ENUM_VAL");
                }

            }else{
                boolean found = false;
                Object feat_val = null;
                for(RDDL.PVAR_INST_DEF array_val : action_value){
                    if((variables.get(key)._o1).equals(array_val._sPredName) && cur_lconsts.equals(array_val._alTerms)){
                         feat_val = array_val._oValue;
                    }
                }

                if(!found){
                    RDDL.PVAR_NAME pname =variables.get(key)._o1;
                    if(hm_variables.get(pname) instanceof RDDL.PVARIABLE_STATE_DEF){
                        feat_val = ((RDDL.PVARIABLE_STATE_DEF) hm_variables.get(pname))._oDefValue;
                    }else if(hm_variables.get(pname) instanceof RDDL.PVARIABLE_ACTION_DEF){
                        feat_val = ((RDDL.PVARIABLE_ACTION_DEF) hm_variables.get(pname))._oDefValue;
                    }else{
                        throw new Exception("This is no Default value for the type you are looking");
                    }
                }


                if(feat_val instanceof Double){
                    feat_values.put(key,String.valueOf((Double)feat_val ));
                }else if(feat_val instanceof Integer){
                    feat_values.put(key,String.valueOf((Integer)feat_val ));
                }else if(feat_val instanceof Boolean){
                    double f_val = (Boolean) feat_val ? 1.0 : 0.0;
                    feat_values.put(key,String.valueOf(f_val));
                }else if(feat_val instanceof RDDL.ENUM_VAL){
                    int f_val = ((RDDL.ENUM_VAL)feat_val).enum_to_int(hmtypes,hm_variables;
                    feat_values.put(key,String.valueOf(f_val));

                } else{
                    throw new Exception("In Action value did not find  a Double,Integer,boolean");
                }
            }




        }

        return feat_values;


    }
    //Helper function,
    protected String _getRFeat(Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>> key_val){
        String val = key_val._o1._sPVarName;
        for(int i=0 ; i<key_val._o2.size();i++){
            String temp_str=key_val._o2.get(i).toString();
            //Assuming $t1,$u2, etc.
            temp_str =temp_str.substring(1,temp_str.length());

            val = val+"_"+temp_str;

        }
        return val;


    }



    public ArrayList<Object> generateFeatureTargetEarth(RDDL.EXPR e, State s, ArrayList<ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>>> buffer_state, ArrayList<ArrayList<ArrayList<RDDL.PVAR_INST_DEF>>> buffer_action,
                                                        HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables,HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes,RandomDataGenerator random) throws Exception{

        HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> method_start_state= deepCopyState(s);
        HashMap<String,String> input_Feat_R_array        = new HashMap<>();
        String output_R_array                             = new String();
        output_R_array                                    = "c(";


        TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>> variables = new TreeMap<>();
        HashMap<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>> var_lconsts = new HashMap<>();
        ArrayList<Pair<String,HashMap<String,String>>> input_output_data = new ArrayList<>();
        for(int i=0;i<buffer_state.size();i++){
            ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>> state_trajectory  = buffer_state.get(i);
            ArrayList<ArrayList<RDDL.PVAR_INST_DEF>> action_trajectory = buffer_action.get(i);
            for(int j=0;j<buffer_state.get(i).size();j++){
                HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> state_value = state_trajectory.get(j);
                ArrayList<RDDL.PVAR_INST_DEF> action_value = action_trajectory.get(j);

                //This is for the Input features.
                HashSet<Pair> Gfluents = new HashSet<>();
                e.collectGFluents(null,s,Gfluents);
                for(Pair key : Gfluents){
                    // "<rlevel, [$t1]>" this type feature name doesn't work in R.  converting to rlevel_t1_t2....
                    String str_key =_getRFeat( key );

                    if(!variables.containsKey(str_key))
                        variables.put(str_key,key);
                }
                HashMap<String,String> si1 = null;
                try{
                    si1= getInputVector(variables,var_lconsts,state_value,action_value,hm_variables,hmtypes);
                    HashMap subs = new HashMap<RDDL.LVAR,RDDL.LCONST>();
                    s.copyStateRDDLState(state_value,true);
                    double target_val = (double)e.sample(subs,s,random);
                    input_output_data.add(new Pair(String.valueOf(target_val),si1));

                }catch (Exception e1){
                    e1.printStackTrace();

                }


            }
        }

        //construction of Input and target vector in R format.
        for(int k=0 ; k<input_output_data.size();k++){
            //This is output R data
            String temp_str =input_output_data.get(k)._o1;
            output_R_array = output_R_array+ temp_str+ ",";

            //This is for input R data. variables has features and
            for(String feat_name : variables.keySet()){
                HashMap<String,String> temp_map = input_output_data.get(k)._o2;
                if(temp_map.containsKey(feat_name)){
                    if(!input_Feat_R_array.containsKey(feat_name)){
                        //This is first time Addition.
                        input_Feat_R_array.put(feat_name,"c("+temp_map.get(feat_name));
                    }else{
                        String temp_str1 = input_Feat_R_array.get(feat_name);
                        input_Feat_R_array.put(feat_name,temp_str1 +", "+temp_map.get(feat_name) );
                    }


                }else{//When feature rlevel_r3 not available.
                    //Sparse the feature is not avilable in the input_features for that Example.
                    if(!input_Feat_R_array.containsKey(feat_name)){
                        //This is first time Addition.
                        input_Feat_R_array.put(feat_name,"c("+ "0.0");
                    }else{
                        String temp_str1 = input_Feat_R_array.get(feat_name);
                        input_Feat_R_array.put(feat_name,temp_str1+", "+ "0.0" );
                    }
                }
            }
        }

        //adding ")" at the end
        String final_output_R_data = output_R_array.trim().substring(0,output_R_array.trim().length()-1) + ")";


        //Making Sure to close the brackets.
        HashMap<String,String> final_input_R_data = new HashMap<>();
        //This for loop to remove "," and add ")" for input_features.
        for(String key : variables.keySet()){
            String temp_str =input_Feat_R_array.get(key);
            temp_str = temp_str.trim();
            temp_str = temp_str+ ")";
            final_input_R_data.put(key,temp_str);
        }



        ArrayList final_output_list = new ArrayList<Object>();
        final_output_list.add(final_input_R_data);
        final_output_list.add(final_output_R_data);
        final_output_list.add(variables);



        s.copyStateRDDLState(method_start_state,true);

        return final_output_list;

    }



    protected String runEarth(HashMap<String,String> input_features, String output) throws Exception{

        //Starting Rengine.
        Rengine engine = Rengine.getMainEngine();
        if(engine == null)
            engine = new Rengine(new String[] {"--vanilla"}, false, null);

        //Rengine engine  = new Rengine(new String[] {"--no-save"},false,null);
        engine.eval("library(earth)");
        String feature_format = new String();
        Integer check = 0;
        for( Map.Entry<String,String> entry1 : input_features.entrySet()){
            engine.eval(entry1.getKey() + "<-"+ entry1.getValue());
            if(check==0){
                feature_format = entry1.getKey();
                check =1; }
            else{ feature_format.concat(" + "+ entry1.getKey()); }
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





    public RDDL.EXPR reconstruct_expr(String earthOutput, TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>> variables,
                                      HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables,
                                      HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes) throws Exception {

        //Sample Earth Output
        //Earth Output
        // 1 +
        //   1.3 * bf1
        //
        // bf1 : h(53.2847-rlevel)
        //Where h is the hinge Function.

        String[] list_output = earthOutput.split("\n");



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
                if(variables.containsKey(hinge_values[0])){
                    real_val = Double.parseDouble(hinge_values[1]);
                    hm_variables.get(hinge_values[0]);
                    RDDL.PVAR_EXPR temp_pvar_expr        = new RDDL.PVAR_EXPR(variables.get(hinge_values[0])._o1._sPVarName,variables.get(hinge_values[0])._o2);

                    RDDL.REAL_CONST_EXPR temp_const_expr = new RDDL.REAL_CONST_EXPR(real_val);
                    RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_pvar_expr,temp_const_expr,"-");
                    RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new RDDL.REAL_CONST_EXPR(0.0), temp_oper_expr,"max");
                    hinge_function.put(key_val,max_oper_expr);

                }
                if(variables.containsKey(hinge_values[1])){
                    real_val = Double.parseDouble(hinge_values[0]);
                    RDDL.PVAR_EXPR temp_pvar_expr        = new RDDL.PVAR_EXPR(variables.get(hinge_values[0])._o1._sPVarName,variables.get(hinge_values[1])._o2);
                    RDDL.REAL_CONST_EXPR temp_const_expr = new RDDL.REAL_CONST_EXPR(real_val);

                    RDDL.OPER_EXPR temp_oper_expr        = new RDDL.OPER_EXPR(temp_const_expr,temp_pvar_expr,"-");
                    RDDL.OPER_EXPR max_oper_expr         = new RDDL.OPER_EXPR(new RDDL.REAL_CONST_EXPR(0.0), temp_oper_expr,"max");

                    hinge_function.put(key_val,max_oper_expr);

                }
            }

        }
        RDDL.EXPR final_expr = new RDDL.REAL_CONST_EXPR(bias);

        Integer temp_count = 0;
        for(String key: coefficient_mapping.keySet()){
            Double real_value = coefficient_mapping.get(key);
            RDDL.REAL_CONST_EXPR temp_real_expr= new RDDL.REAL_CONST_EXPR(real_value);
            RDDL.EXPR temp_oper_expr  = new RDDL.OPER_EXPR(temp_real_expr,hinge_function.get(key),"*");
            RDDL.EXPR temp_final_expr = final_expr;
            final_expr = new RDDL.OPER_EXPR(temp_final_expr,temp_oper_expr,"+");
        }
        return final_expr;



    }




    protected HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> deepCopyState(final State rddl_state) {

        HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> temp = rddl_state._state;
        HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> copied_state =  new HashMap<>();

        for(RDDL.PVAR_NAME pvar : temp.keySet()){
            RDDL.PVAR_NAME new_pvar = new RDDL.PVAR_NAME(pvar._sPVarName);
            HashMap<ArrayList<RDDL.LCONST>,Object> temp_hashmap = temp.get(pvar);
            HashMap<ArrayList<RDDL.LCONST>,Object> new_hashmap =  new HashMap<>();
            for(ArrayList<RDDL.LCONST> temp_array : temp_hashmap.keySet()){
                ArrayList<RDDL.LCONST> new_array = new ArrayList<>();
                for(int i=0; i<temp_array.size(); i++){
                    RDDL.LCONST temp_lconst = temp_array.get(i);
                    if(temp_lconst instanceof RDDL.OBJECT_VAL){
                        RDDL.LCONST new_lconst = new RDDL.OBJECT_VAL(temp_lconst._sConstValue);
                        new_array.add(new_lconst); }
                    if(temp_lconst instanceof RDDL.ENUM_VAL){
                        RDDL.LCONST new_lconst = new RDDL.ENUM_VAL(temp_lconst._sConstValue);
                        new_array.add(new_lconst); }
                }
                //This is for deep copy for Object
                Object temp_objval = temp_hashmap.get(temp_array);
                Object new_objval = null;
                if( temp_hashmap.get(temp_array) instanceof Boolean){
                    new_objval = new Boolean(((Boolean)temp_objval).booleanValue());
                }
                else if( temp_hashmap.get(temp_array) instanceof Double){
                    new_objval = new Double(((Double)temp_objval).doubleValue());
                }
                else if(temp_hashmap.get(temp_array) instanceof RDDL.ENUM_VAL){
                    new_objval = new RDDL.ENUM_VAL(((RDDL.ENUM_VAL)temp_objval)._sConstValue);
                }
                else if(temp_hashmap.get(temp_array) instanceof  Integer){
                    new_objval = new Integer(((Integer)temp_objval).intValue());
                }
                else{
                    System.out.println(temp_objval + " instance of an Object is Not Implemented");
                    throw new AssertionError();
                }
                assert( new_objval != null );
                new_hashmap.put(new_array, new_objval);
            }
            copied_state.put(new_pvar,new_hashmap);
        }
        return copied_state;
    }



}

