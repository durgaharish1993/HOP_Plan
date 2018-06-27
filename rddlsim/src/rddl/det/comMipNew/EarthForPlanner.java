package rddl.det.comMipNew;

import gurobi.GRB;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.rosuda.JRI.Rengine;
import rddl.EvalException;
import rddl.RDDL;
import rddl.State;
import util.Pair;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.*;

import rddl.RDDL.EXPR;

import javax.script.ScriptException;

public class EarthForPlanner {

    protected boolean PRINT_TO_R_FILE = true;
    protected String R_FILE_NAME = "model.R";

    public EXPR fitPWL(RDDL.PVAR_NAME target_pVar,boolean reward_type, EXPR e,  State s, ArrayList[] buffers, HashMap<RDDL.PVAR_NAME,Character> type_map, HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables, HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes, RandomDataGenerator random) throws Exception{

        ArrayList<ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>>> buffer_state = buffers[0];
        ArrayList<ArrayList<ArrayList<RDDL.PVAR_INST_DEF>>> buffer_action = buffers[1];


        ArrayList<Object> val = generateFeatureTargetEarth(e,s,buffer_state,buffer_action,type_map,hm_variables,hmtypes,random);

        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>> variables = (TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>>)val.get(0);
        HashMap<String,String> feat_type_map                                  = (HashMap<String,String>) val.get(1);
        ArrayList<HashMap<String,String>>input_data                           = (ArrayList<HashMap<String,String>>)val.get(2);
        String target_type                                                    = (String) val.get(3);
        ArrayList<String> target_data                                         = (ArrayList<String> )val.get(4);
        ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        ArrayList<Object> r_strings = constructRData( variables,input_data,target_data,feat_type_map,target_type);
        //////////////////////////////////////////////////////////////////////////////////
        HashMap<String, String> input_Feat_R_array = (HashMap<String, String>) r_strings.get(0);
        String output_R_array= (String)r_strings.get(1);
        HashMap<String,TreeSet<String>> input_factors =  (HashMap<String,TreeSet<String>>) r_strings.get(2);
        TreeSet<String> output_factors = ( TreeSet<String>) r_strings.get(3);
        ////////////////////////////////////////////////////////////////////////////////
        EXPR final_expr = null;
        if(output_factors.size()==1 && reward_type){

        }

        //Degenerate case. 
        if(output_factors.size()==1){
            String str_val = null;
            for(String key : output_factors){
                str_val = key;
            }
            if(reward_type){
                final_expr = new RDDL.REAL_CONST_EXPR(Double.valueOf(str_val));

            }else{
                if(target_type.equals("bool")){
                    final_expr =  new RDDL.BOOL_CONST_EXPR(str_val=="1" ? true : false);
                }else if(target_type.equals("int")){
                    final_expr = new RDDL.INT_CONST_EXPR(Integer.valueOf(str_val));
                }else if(target_type.equals("real")){
                    final_expr = new RDDL.REAL_CONST_EXPR(Double.valueOf(str_val));
                }else if(target_data.equals("enum")){
                    assert (hmtypes.get(hm_variables.get(target_pVar)._typeRange) instanceof RDDL.ENUM_TYPE_DEF);
                    int index = Integer.valueOf(str_val);
                    final_expr =(RDDL.ENUM_VAL) ((RDDL.ENUM_TYPE_DEF) hmtypes.get(hm_variables.get(target_pVar)._typeRange))._alPossibleValues.get(index);
                }
            }
        }
        if(final_expr!=null){
            return final_expr;
        }

        //////////////////////////////////////////////////////////

        String earth_PWL = runEarth(variables,input_Feat_R_array,output_R_array,feat_type_map,target_type,input_factors,output_factors,type_map,hm_variables,hmtypes);
        final_expr =reconstruct_expr(earth_PWL,(TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>>) val.get(2),hm_variables,hmtypes);
        return final_expr;


    }



    protected HashMap<String,String> getInputVector(TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>>variables, HashMap<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>lvar_lcont_map,
                                                    HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> state_value,
                                               ArrayList<RDDL.PVAR_INST_DEF> action_value,HashMap<RDDL.PVAR_NAME,
                                                RDDL.PVARIABLE_DEF> hm_variables,HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes) throws Exception {

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
                    String f_val = (Boolean) feat_val ? "1" : "0";
                    feat_values.put(key,f_val);
                }else if(feat_val instanceof RDDL.ENUM_VAL){
                    int f_val = ((RDDL.ENUM_VAL)feat_val).enum_to_int(hmtypes,hm_variables);
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
                    String f_val = (Boolean) feat_val ? "1" : "0";
                    feat_values.put(key,f_val);
                }else if(feat_val instanceof RDDL.ENUM_VAL){
                    int f_val = ((RDDL.ENUM_VAL)feat_val).enum_to_int(hmtypes,hm_variables);
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
        //LCONST  are serpated by ___
        //Feature value is separted by ____
        String val = key_val._o1._sPVarName;
        for(int i=0 ; i<key_val._o2.size();i++){
            String temp_str=key_val._o2.get(i).toString();
            //Assuming $t1,$u2, etc.
            temp_str = temp_str.substring(1,temp_str.length());


            val = val+"___"+temp_str;

        }
        val=val.replaceAll("[-+.^:,]","");
        val = "Feat" +"____"+val;
        val = val + "__END__";
        return val;


    }




    protected String _getFeatType(RDDL.PVAR_NAME pName,HashMap<RDDL.PVAR_NAME,Character> type_map,HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables,HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes ) throws Exception{

        if( (hmtypes.get(hm_variables.get(pName)._typeRange) instanceof RDDL.ENUM_TYPE_DEF) ){
            return "enum";
        }else if(type_map.get(pName).equals(GRB.BINARY)){
           return "bool";
        }else if(type_map.get(pName).equals(GRB.INTEGER)){
            return "int";
        } else if(type_map.get(pName).equals(GRB.CONTINUOUS)){
           return "real";
        }else{
            try{
                throw new Exception("THis case is not handled in R.");
            }catch (Exception e){
                e.printStackTrace();
                throw e;
            }
        }


    }


    protected String _getTargetType(Object target_val) throws Exception{

        if(target_val instanceof Boolean){
            return "bool";
        }else if(target_val instanceof RDDL.ENUM_VAL){
            return "enum";
        }else if(target_val instanceof Double){
            return "real";
        }else if(target_val instanceof Integer){
            return "int";
        }else{
            try{
                throw new Exception("THis case is not handled in R.");
            }catch (Exception e){
                e.printStackTrace();
                throw e;
            }
        }
    }



    protected String _getTargetVal(Object target_val,HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables,HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes) throws Exception{
        if(target_val instanceof Boolean){
            return (Boolean) target_val ? "1" : "0" ;
        }else if(target_val instanceof RDDL.ENUM_VAL){
            return String.valueOf(((RDDL.ENUM_VAL) target_val).enum_to_int(hmtypes,hm_variables));
        }else if(target_val instanceof Double){
            return String.valueOf((Double) target_val);
        }else if(target_val instanceof Integer){
            return String.valueOf((Integer) target_val);
        }else{
            try{
                throw new Exception("THis case is not handled in R.");
            }catch (Exception e){
                e.printStackTrace();
                throw e;
            }
        }

    }


    protected String _getRfactor(TreeSet<String> factors){
        String temp_str = "levels=c(";
        for(String key : factors){
            temp_str = temp_str + key + ",";
        }
        temp_str = temp_str.substring(0,temp_str.length()-1) + ")";
        return temp_str;
    }

    protected ArrayList<Object> constructRData(TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>> variables,ArrayList<HashMap<String,String>>input_data, ArrayList<String> target_data,HashMap<String,String> input_type_map,String target_type) {
        //This has to be key of Variables, and
        HashMap<String, String> input_Feat_R_array = new HashMap<>();
        String output_R_array = new String();
        output_R_array = "c(";
        ArrayList<Object> out_val = new ArrayList<>();
        HashMap<String,TreeSet<String>> input_factors = new HashMap<>();
        TreeSet<String> output_factors = new TreeSet<>();

        //construction of Input and target vector in R format.
        try {
            assert input_data.size() == target_data.size();
        } catch (AssertionError e) {
            throw e;
        }
        int data_length =input_data.size();
        data_length =3;
        for (int k = 0; k < data_length; k++) {
            //This is output R data
            String out_temp_str = target_data.get(k);
            if (k == data_length - 1) {
                output_R_array = output_R_array + out_temp_str + " )";
                output_factors.add(out_temp_str);
            } else {
                output_R_array = output_R_array + out_temp_str + ",";
                output_factors.add(out_temp_str);
            }


            //This is for input_features... This will make sure of the order. TreeMap (Important).
            //focus on one input_data.
            HashMap<String, String> temp_input_data = input_data.get(k);

            for (String feat_name : variables.keySet()) {
                if (temp_input_data.containsKey(feat_name)) {
                    //When Feature is avialble in the data
                    String input_temp_str = temp_input_data.get(feat_name);
                    if (!input_Feat_R_array.containsKey(feat_name)) {
                        input_Feat_R_array.put(feat_name, "c(" + input_temp_str);
                        if(!input_factors.containsKey(feat_name)){
                            TreeSet<String>temp_hashSet = new TreeSet<String>();
                            temp_hashSet.add(input_temp_str);
                            input_factors.put(feat_name,temp_hashSet);

                        }else{
                            input_factors.get(feat_name).add(input_temp_str);
                        }

                    } else {
                        String cur_str = input_Feat_R_array.get(feat_name);
                        String input_temp_str1 = temp_input_data.get(feat_name);
                        if (k == data_length - 1) { //Last element.
                            input_Feat_R_array.put(feat_name, cur_str + ", " + input_temp_str1 + " )");
                            if(!input_factors.containsKey(feat_name)){
                                TreeSet<String>temp_hashSet = new TreeSet<String>();
                                temp_hashSet.add(input_temp_str1);
                                input_factors.put(feat_name,temp_hashSet);

                            }else{
                                input_factors.get(feat_name).add(input_temp_str1);
                            }
                        } else {
                            input_Feat_R_array.put(feat_name, cur_str + ", " + input_temp_str1);
                            if(!input_factors.containsKey(feat_name)){
                                TreeSet<String>temp_hashSet = new TreeSet<String>();
                                temp_hashSet.add(input_temp_str1);
                                input_factors.put(feat_name,temp_hashSet);

                            }else{
                                input_factors.get(feat_name).add(input_temp_str1);
                            }
                        }

                    }
                } else {
                    //When feature not availble.
                    if (!input_Feat_R_array.containsKey(feat_name)) {
                        input_Feat_R_array.put(feat_name, "c(" + "0");
                    } else {
                        String cur_str = input_Feat_R_array.get(feat_name);
                        if (k == data_length - 1) { //Last element.
                            input_Feat_R_array.put(feat_name, cur_str + ", " + "0 )");
                            if(!input_factors.containsKey(feat_name)){
                                TreeSet<String>temp_hashSet = new TreeSet<String>();
                                temp_hashSet.add("0");
                                input_factors.put(feat_name,temp_hashSet);

                            }else{
                                input_factors.get(feat_name).add("0");
                            }
                        } else { // Not Last element.
                            input_Feat_R_array.put(feat_name, cur_str + ", " + "0");
                            if(!input_factors.containsKey(feat_name)){
                                    TreeSet<String>temp_hashSet = new TreeSet<String>();
                                    temp_hashSet.add("0");
                                    input_factors.put(feat_name,temp_hashSet);

                            }else{
                                input_factors.get(feat_name).add("0");
                            }
                        }

                    }
                }
            }
        }







        out_val.add(input_Feat_R_array);
        out_val.add(output_R_array);
        out_val.add(input_factors);
        out_val.add(output_factors);
        return out_val;

    }

    public ArrayList<Object> generateFeatureTargetEarth(RDDL.EXPR e, State s, ArrayList<ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>>> buffer_state, ArrayList<ArrayList<ArrayList<RDDL.PVAR_INST_DEF>>> buffer_action,
                                                        HashMap<RDDL.PVAR_NAME,Character> type_map, HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables,HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes, RandomDataGenerator random) throws Exception{

        HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> method_start_state= deepCopyState(s);
        TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>> variables = new TreeMap<>();
        HashMap<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>> var_lconsts = new HashMap<>();
        ArrayList<HashMap<String,String>>input_data  = new ArrayList<>();
        ArrayList<String> target_data = new ArrayList<>();
        HashMap<String,String> feat_type_map = new HashMap<>();
        String target_type = null;



        for(int i=0;i<buffer_state.size();i++){
            ArrayList<HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>>> state_trajectory  = buffer_state.get(i);
            ArrayList<ArrayList<RDDL.PVAR_INST_DEF>> action_trajectory = buffer_action.get(i);
            for(int j=0;j<buffer_state.get(i).size();j++){
                HashMap<RDDL.PVAR_NAME,HashMap<ArrayList<RDDL.LCONST>,Object>> state_value = state_trajectory.get(j);
                ArrayList<RDDL.PVAR_INST_DEF> action_value = action_trajectory.get(j);

                HashSet<Pair> Gfluents = new HashSet<>();
                e.collectGFluents(null,s,Gfluents);
                for(Pair key : Gfluents){
                    // "<rlevel, [$t1]>" this type feature name doesn't work in R.  converting to rlevel_t1_t2....
                    String str_key =_getRFeat( key );
                    String feat_type = _getFeatType((RDDL.PVAR_NAME)key._o1,type_map, hm_variables,hmtypes);
                    if(!feat_type_map.containsKey(str_key))
                        feat_type_map.put(str_key,feat_type);


                    if(!variables.containsKey(str_key))
                        variables.put(str_key,key);
                }

                HashMap<String,String> si1 = null;
                try{
                    //<feat,val>
                    si1= getInputVector(variables,var_lconsts,state_value,action_value,hm_variables,hmtypes);
                    HashMap subs = new HashMap<RDDL.LVAR,RDDL.LCONST>();
                    s.copyStateRDDLState(state_value,true);
                    Object target_val      = e.sample(subs,s,random);
                    target_type            = _getTargetType(target_val);
                    String target_val_str  =_getTargetVal(target_val,hm_variables,hmtypes);
                    input_data.add(si1);
                    target_data.add(target_val_str);

                }catch (Exception e1){
                    e1.printStackTrace();

                }


            }
        }

        s.copyStateRDDLState(method_start_state,true);


        ArrayList final_output_list = new ArrayList<Object>();
        final_output_list.add(variables);
        final_output_list.add(feat_type_map);
        final_output_list.add(input_data);
        final_output_list.add(target_type);
        final_output_list.add(target_data);

        return final_output_list;

    }



    protected String runEarth(TreeMap<String,Pair<RDDL.PVAR_NAME,ArrayList<RDDL.LCONST>>> variables, HashMap<String,String> input_features, String output,
                              HashMap<String,String> input_type_map , String target_type, HashMap<String,TreeSet<String>>input_factors,TreeSet<String>target_factors,
                              HashMap<RDDL.PVAR_NAME,Character> type_map, HashMap<RDDL.PVAR_NAME, RDDL.PVARIABLE_DEF> hm_variables,
                              HashMap<RDDL.TYPE_NAME, RDDL.TYPE_DEF> hmtypes ) throws Exception{

        //Starting Rengine.
        Writer writer = null;
        Rengine engine = Rengine.getMainEngine();
        if(engine == null)
            engine = new Rengine(new String[] {"--vanilla"}, false, null);

        //Rengine engine  = new Rengine(new String[] {"--no-save"},false,null);

        if(PRINT_TO_R_FILE){
            writer= new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(R_FILE_NAME), "utf-8"));
        }

        engine.eval("library(earth)");
        String feature_format = new String();
        Integer check = 0;


        for( Map.Entry<String,String> entry1 : input_features.entrySet()){
            String key = entry1.getKey();
            String value = entry1.getValue();


            if(input_type_map.get(key).equals("enum")){
                String r_factors = _getRfactor(input_factors.get(key));
                String s1 = key + "<-" + "factor("+value +"," +r_factors +")";
                Object temp = engine.eval(s1);
                if(PRINT_TO_R_FILE){
                    writer.write(s1+"\n");
                }


            }else if(input_type_map.get(key).equals("bool")){
                String r_factors = _getRfactor(input_factors.get(key));
                String s1 = key + "<-" + "factor("+value +"," +r_factors +")";
                Object temp = engine.eval(s1);
                if(PRINT_TO_R_FILE){
                    writer.write(s1+"\n");
                }

            }else if(input_type_map.get(key).equals("int")){
                String s1= key + "<-"+ value +")";
                if(PRINT_TO_R_FILE){
                    writer.write(s1+"\n");
                }
                Object temp = engine.eval(s1);


            }else if(input_type_map.get(key).equals("real")){
                String s1 = key + "<-"+ value +")";
                if(PRINT_TO_R_FILE){
                    writer.write(s1+"\n");
                }
                Object temp = engine.eval(s1);

            }else{
                try{
                    throw new Exception("THis case is not handled in R.");
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            if(check==0){
                feature_format = entry1.getKey();
                check =1;
            } else{
                feature_format=feature_format.concat(" + "+ entry1.getKey());
            }
        }


        engine.eval("target <-" + output );
        if(target_type.equals("enum")){
            String r_factors = _getRfactor(target_factors);
            String s1 = "target <-" + "factor( target " +"," +r_factors +")";
            if(PRINT_TO_R_FILE){
                writer.write(s1+"\n");
            }
            Object  temp = engine.eval(s1 );


        }else if(target_type.equals("bool")){
            String r_factors = _getRfactor(target_factors);
            final String s1 = "target <-" + "factor( target " +"," +r_factors +")";
            if(PRINT_TO_R_FILE){
                writer.write(s1+"\n");
            }
            Object temp = engine.eval(s1);


        }else if(target_type.equals("int")){


        }else if(target_type.equals("real")){

        }else{
            try{
                throw new Exception("THis case is not handled in R.");
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        String s1 ="model<-earth( target ~ " + feature_format + ",nprune=2)";
        if(PRINT_TO_R_FILE){
            writer.write(s1+"\n");
        }

        engine.eval(s1);
//        String rss_val =engine.eval("format(model$rss)").asString();
//        String gcv_val =engine.eval("format(model$gcv)").asString();
        s1 = "print(summary(model))";
        if(PRINT_TO_R_FILE){
            writer.write(s1+"\n");
        }
        engine.eval(s1);
//        "a=predict(model,1)"
//        engine.eval(");
        s1 = "format(model,style='bf')";
        if(PRINT_TO_R_FILE){
            writer.write(s1+"\n");
        }
        String earth_output = engine.eval(s1).asString();

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
            if(temp_str.equals("")) {
                continue;
            }else if( temp_str.contains("e-")  && (!temp_str.contains("*"))){
                bias =Double.parseDouble(temp_str);
                continue;

            } else if(temp_str.contains("*")){

                temp_str = temp_str.replaceAll("\\s","");
                temp_str = temp_str.replaceAll("\\+","");
                String [] term_val = temp_str.split("\\*");
                NumberFormat format = NumberFormat.getInstance();
                Double coeffic = format.parse(term_val[0]).doubleValue();
                coefficient_mapping.put(term_val[1],coeffic);
                continue;
            }else if(temp_str.contains("bf") && temp_str.contains("h(")){
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
                continue;
            }else if(temp_str.contains("bf")){

                String[] term_val =temp_str.split("\\s");
                String key_val = term_val[0];
                String hinge_str = term_val[2];

                String [] hinge_values = hinge_str.split("-");
                for(String key : variables.keySet()){
                    if(hinge_str.length() >= key.length() ){
                        if(hinge_str.substring(0,key.length()).equals(key)) {
                            RDDL.PVAR_EXPR temp_pvar_expr = new RDDL.PVAR_EXPR(variables.get(key)._o1._sPVarName, variables.get(key)._o2);
                            hinge_function.put(key_val, temp_pvar_expr);
                            break;
                        }
                    }
                }
                continue;
            } else if(!(temp_str.contains("-") || temp_str.contains("+"))){

                bias =Double.parseDouble(temp_str);
                continue;
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

