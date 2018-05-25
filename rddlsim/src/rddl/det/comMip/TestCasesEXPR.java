package rddl.det.comMip;
import junit.framework.TestCase;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import rddl.RDDL.LTERM;
import rddl.RDDL.AGG_EXPR;
import rddl.RDDL.OPER_EXPR;
import rddl.RDDL.LTYPED_VAR;
import rddl.RDDL.OBJECT_VAL;
import rddl.RDDL.OBJECT_TYPE_DEF;
import rddl.RDDL.CONN_EXPR;

import javax.print.attribute.HashAttributeSet;

public class TestCasesEXPR {



    public static void testCasesEXPR() throws Exception{


        Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants = new HashMap<>();





        //This is test case for SUM_ (NF * F)
        LTERM a = new LVAR("?up");
        LTERM b = new LVAR("?t");
        ArrayList<LTERM> lterms1 = new ArrayList<>(); lterms1.add(a);
        ArrayList<LTERM> lterms2 = new ArrayList<>(); lterms2.add(b);
        ArrayList<LTYPED_VAR> ltyped = new ArrayList<>();
        PVAR_EXPR p1 =new PVAR_EXPR("f",lterms1); PVAR_EXPR p2 = new PVAR_EXPR("NF",lterms2);
        ArrayList<LTERM> con_var = new ArrayList<>();
        con_var.addAll(lterms1);


        //Buliding Constants.
        Map<ArrayList<LCONST>,Object> cons1 = new HashMap<>();

        ArrayList<Object> temp_objects = new ArrayList<>();
        for(int i = 1; i<3 ; i++){
            ArrayList<LCONST> lconst_array = new ArrayList<>();
            lconst_array.add(new OBJECT_VAL("$t"+Integer.valueOf(i).toString())); //lconst_array.add(new OBJECT_VAL("$t2")); lconst_array.add(new OBJECT_VAL("$t3"));
            cons1.put(lconst_array,10+i);
            con_var.addAll(lconst_array);
            temp_objects.add(new OBJECT_VAL("$t"+ Integer.valueOf(i).toString()));

        }
        constants.put(p2._pName,cons1);

        con_var.add(new OBJECT_VAL("$time10"));
        con_var.add(new OBJECT_VAL("$future200"));
        con_var.add(new LVAR("?down"));


        ArrayList<LTERM> terms = new ArrayList<>();
        for(int i=0;i<con_var.size();i++){
            LTERM x = con_var.get(i);
            if(x instanceof LVAR){
                if( !((LVAR)x)._sVarName.equals("?up") & !((LVAR)x)._sVarName.equals("?down"))
                    terms.add(x);
            }else if(x instanceof LCONST){
                if( !((LCONST) x)._sConstValue.matches(".*(time|future).*"))
                    terms.add(x);
            }
        }



        //Defining Objects
        Map<TYPE_NAME, OBJECTS_DEF> objects = new HashMap<>();
        TYPE_NAME up_type = new TYPE_NAME("real");

        OBJECTS_DEF ob = new OBJECTS_DEF("real",temp_objects);
        objects.put(up_type,ob);
        //objects.put()
        Map<LVAR, LCONST> subs = new HashMap<>();
        LVAR a_lvar = new LVAR("?t");
        LCONST a_cont = new OBJECT_VAL("$t1");
        LCONST a_cont2 = new OBJECT_VAL("$t2");
        LCONST a_cont3 = new OBJECT_VAL("$t3");
        subs.put(a_lvar,a_cont); subs.put(a_lvar,a_cont2); subs.put(a_lvar,a_cont3);

        /////////////////////////////////////////////////////////////////////////
        //Test case 1
        //(sum_{?up : real} (f(?up) * NF(?t)))
        OPER_EXPR op1 =new OPER_EXPR(p1,p2,"*"); LTYPED_VAR up =  new LTYPED_VAR("?up","real"); ltyped.add(up);
        AGG_EXPR ag1 = new AGG_EXPR("sum",ltyped ,op1);

       // ag1.substitute(subs,constants,objects).sampleDeterminization(new RandomDataGenerator(),constants,objects).isPiecewiseLinear(constants,objects)


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        //Test case 2
        //QUANT_EXPR(String quant, ArrayList<LTYPED_VAR> vars, BOOL_EXPR expr )
        //BOOL_EXPR :::: (sum_{?up : real} (f(?up) * NF(?t))) <=1
        //forall    :::: [forall_{?t : real} ((sum_{?up : real} (f(?up) * NF(?t))) <=1)]
        BOOL_EXPR e1 = new COMP_EXPR(ag1,new REAL_CONST_EXPR(2.0),"<=");
        LTYPED_VAR t = new LTYPED_VAR("?t","real") ;
        ArrayList<LTYPED_VAR> array_ltyped = new ArrayList<>();     array_ltyped.add(t);
        QUANT_EXPR q1 = new QUANT_EXPR("forall", array_ltyped, e1 );

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Test Case 2b
        // QUANT_EXPR :  forall(?up:real)[ exists(?t:real])( NF(?up,?t)<=2 ) ]

        ArrayList<LTERM> lterms4 = new ArrayList<>();
        lterms4.addAll(lterms1);
        lterms4.add(b);
        BOOL_EXPR test_2b_e1  = new COMP_EXPR(new PVAR_EXPR("f",lterms4),new REAL_CONST_EXPR(2.0),"<=");
        QUANT_EXPR test_2b_e2 = new QUANT_EXPR("exists",  array_ltyped,test_2b_e1);
        ArrayList<LTYPED_VAR> ltype_2  = new ArrayList<>();     ltype_2.add(up);
        QUANT_EXPR test_2b_3  = new QUANT_EXPR("forall",ltype_2,test_2b_e2);
        System.out.println("dffd");
        subs.put(a_lvar,a_cont);
        //test_2b_3.substitute(subs,constants,objects);


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Test case 2c.....  [forall_{?up : real} [exists_{?t : real} ((f(?up, ?t) <= 2.0) ^ (NF1(?up, ?t) <= 5.0))]]
        ArrayList<LTERM> lterms5 = new ArrayList<>();
        lterms5.addAll(lterms1);
        lterms5.add(b);
        lterms5.add(new LVAR("?q"));

        PVAR_EXPR non_fluent1 = new PVAR_EXPR("NF1",lterms5);
        BOOL_EXPR test_2c_e1  = new COMP_EXPR(new PVAR_EXPR("f",lterms5),new REAL_CONST_EXPR(2.0),"<=");
        BOOL_EXPR test_2c_4   = new COMP_EXPR(non_fluent1, new REAL_CONST_EXPR(5.0),"<=");
        RDDL.CONN_EXPR test_2c_5 = new RDDL.CONN_EXPR(test_2c_e1,test_2c_4,"^");

        LTERM lvar_1 =new LVAR("?t");   LTERM lcont_1 = new LVAR("?up");
        COMP_EXPR  lvar_lcont_equals = new COMP_EXPR(lvar_1,lcont_1,"==");
        test_2c_5 = new RDDL.CONN_EXPR(test_2c_5, lvar_lcont_equals,"^");
        QUANT_EXPR test_2c_6 = new QUANT_EXPR("exists",  array_ltyped,test_2c_5);
        QUANT_EXPR test_2c_7 = new QUANT_EXPR("forall",ltype_2,test_2c_6);
        //RDDL.CONN_EXPR test_2c_8 = new RDDL.CONN_EXPR(test_2c_5,lvar_lcont_equals,"^");
        QUANT_EXPR test_2c_9 = new QUANT_EXPR("exists",array_ltyped,test_2c_5);
        QUANT_EXPR test_2c_10 = new QUANT_EXPR("forall",ltype_2,test_2c_9);

        //adding more to constants
        Map<ArrayList<LCONST>,Object> cons2 = new HashMap<>();

        ArrayList<Object> temp_objects1 = new ArrayList<>();
        System.out.print("dkfjdkfkdfjd");
        for(int i = 1; i<3 ; i++){
            for(int j = 1 ; j<3;j++){
                for (int k =1 ; k < 3 ; k++) {
                    ArrayList<LCONST> lconst_array = new ArrayList<>();
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(i).toString()));
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(j).toString()));
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(k).toString()));
                    if(i==1 && j==1 && k==1){
                        cons2.put(lconst_array, 0);
                    }else if(i==2 && j==1 && k==1){
                        cons2.put(lconst_array, 0);

                    }else{
                        cons2.put(lconst_array,10 + Integer.valueOf(i));
                    }

                }
                //temp_objects.add(new OBJECT_VAL("$t"+ Integer.valueOf(i).toString()));


            }

        }


        constants.put(non_fluent1._pName,cons2);

        HashMap<LVAR,LCONST> subs1 = new HashMap<>();
        subs1.put(new LVAR("?up"),a_cont);

        HashMap<LVAR,LCONST> subs3 = new HashMap<>();
        subs3.put(new LVAR("?q"),a_cont);
        //Double val =new PVAR_EXPR("f",lterms5).getDoubleValue(constants,objects);
        //test_2c_5.isConstant(constants,objects);
        //test_2c_5.getDoubleValue(constants,objects);
        //test_2c_5.substitute(Collections.EMPTY_MAP,constants,objects);
        HashMap<LVAR,LCONST> subs2 = new HashMap<>();
        subs2.put(new LVAR("?t"), new OBJECT_VAL("$t2"));
        lvar_lcont_equals.substitute(Collections.EMPTY_MAP,constants,objects);
        EXPR test_2c_11 = new RDDL.CONN_EXPR(lvar_lcont_equals,test_2c_4,"^");
        //test_2c_10.substitute(Collections.EMPTY_MAP,constants,objects);


        //EXPR temp10 = test_2c_9.substitute(Collections.EMPTY_MAP,constants,objects);
        EXPR temp = test_2c_7.substitute(subs3,constants,objects);
        temp.isConstant(constants,objects);
        test_2c_7.isConstant(constants,objects);
        test_2c_7.getDoubleValue(constants,objects);
        test_2c_7.getDoubleValue(constants,objects);

        test_2c_7.isConstant(constants,objects);
        //EXPR temp =test_2c_7.substitute(subs,constants,objects).substitute(subs1,constants,objects);




        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Test case 3
        // forall :::: [forall_{?t : real} ((sum_{?up : real} (NF(?up,?t))) <=1)]
        //This is failing a bit, need to check.
        //Is Constant Takes lot of time.
        ArrayList<LTERM> lterms3 = new ArrayList<>();
        lterms3.addAll(lterms1);
        lterms3.add(b);
        AGG_EXPR ag2 = new AGG_EXPR("sum",ltyped,new PVAR_EXPR("NF",lterms3));
        BOOL_EXPR e2 = new COMP_EXPR(ag2,new REAL_CONST_EXPR(2.0),"<=");

        QUANT_EXPR q2 = new QUANT_EXPR("forall", array_ltyped, e2 );
        q2.substitute(subs,constants,objects);
       // q2.isConstant(constants,objects);
        EXPR  comp_oper = new COMP_EXPR(a_lvar,a_cont,"==");
        EXPR conn_expr  = new RDDL.CONN_EXPR(comp_oper,p2,"^");
       // conn_expr.substitute(Collections.EMPTY_MAP,constants,objects);
        comp_oper.isConstant(constants,objects);


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Test Case 4
        comp_oper.substitute(Collections.EMPTY_MAP,constants,objects);


    }



    public static void testCase1() throws Exception{

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        //LTERMS, LVARS, LCONSTS
        LTERM lvar_1 = new LVAR("?up");
        LTERM lvar_2 = new LVAR("?t");
        LTERM lvar_3 = new LVAR("?q");
        ArrayList<LTERM> lterms_1_1 = new ArrayList<>(); lterms_1_1.add(lvar_1);
        ArrayList<LTERM> lterms_2_1 = new ArrayList<>(); lterms_2_1.add(lvar_2);
        ArrayList<LTERM> lterms_3_1 = new ArrayList<>(); lterms_3_1.add(lvar_3);

        ArrayList<LTERM> lterms_1_3 = new ArrayList<>(); lterms_1_3.add(lvar_1); lterms_1_3.add(lvar_2); lterms_1_3.add(lvar_3);


        LCONST lconst_t1 = new OBJECT_VAL("$t1");
        LCONST lconst_t2 = new OBJECT_VAL("$t2");
        LCONST lconst_t3 = new OBJECT_VAL("$t3");

        //Adding PVAR_EXPRS

        //This is PVAR_EXPR f, NF.
        PVAR_EXPR p1 =new PVAR_EXPR("f",lterms_1_3);
        PVAR_EXPR p2 = new PVAR_EXPR("NF",lterms_1_3);

        //LTYPED_VAR
        LTYPED_VAR ltyped_1 = new LTYPED_VAR("?t","real") ;
        LTYPED_VAR ltyped_2 = new LTYPED_VAR("?up", "real");
        LTYPED_VAR ltyped_3 = new LTYPED_VAR("?q","real");
        ArrayList<LTYPED_VAR> array_ltyped_1_t  = new ArrayList<>(); array_ltyped_1_t.add(ltyped_1);
        ArrayList<LTYPED_VAR> array_ltyped_1_q  = new ArrayList<>(); array_ltyped_1_q.add(ltyped_3);
        ArrayList<LTYPED_VAR> array_ltyped_1_up = new ArrayList<>(); array_ltyped_1_up.add(ltyped_2);
        ArrayList<LTYPED_VAR> array_ltyped_2 = new ArrayList<>(); array_ltyped_2.add(ltyped_1); array_ltyped_2.add(ltyped_2);
        ArrayList<LTYPED_VAR> array_ltyped_3 = new ArrayList<>(); array_ltyped_3.add(ltyped_1); array_ltyped_3.add(ltyped_2); array_ltyped_3.add(ltyped_3);


        //Defining objects



        //Adding values to Contants
        Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants = new HashMap<>();

        Map<ArrayList<LCONST>,Object> cons2 = new HashMap<>();
        for(int i = 1; i<3 ; i++){
            for(int j = 1 ; j<3;j++){
                for (int k =1 ; k < 3 ; k++) {
                    ArrayList<LCONST> lconst_array = new ArrayList<>();
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(i).toString()));
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(j).toString()));
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(k).toString()));
                    if(i==1 && j==1 && k==1){
                        cons2.put(lconst_array, 0);
                    }else if(i==2 && j==1 && k==1){
                        cons2.put(lconst_array, 0);

                    }else{
                        cons2.put(lconst_array,10 + Integer.valueOf(i));
                    }

                }
            }

        }
        constants.put(p2._pName,cons2);

        //Defining Objects
        Map<TYPE_NAME, OBJECTS_DEF> objects = new HashMap<>();
        TYPE_NAME up_type = new TYPE_NAME("real");
        TYPE_NAME q_type  = new TYPE_NAME("real");
        TYPE_NAME t_type  = new TYPE_NAME("real");

        ArrayList<Object> temp_objects = new ArrayList<>();
        temp_objects.add(lconst_t1); temp_objects.add(lconst_t2); temp_objects.add(lconst_t3);
        OBJECTS_DEF ob = new OBJECTS_DEF("real",temp_objects);
        objects.put(up_type,ob);
        objects.put(q_type,ob);
        objects.put(t_type,ob);

        //Adding Subs
        Map<LVAR, LCONST> subs_q  = new HashMap<>();
        Map<LVAR, LCONST> subs_t  = new HashMap<>();
        Map<LVAR,LCONST>  subs_up = new HashMap<>();

        subs_q.put((LVAR)lvar_3,lconst_t1);
        subs_t.put((LVAR)lvar_1,lconst_t1);
        subs_up.put((LVAR)lvar_2,lconst_t1);
        /////////////////////////////////////////////////////



        //This is the expression
        //Final_expr : [forall_{?up : real} [exists_{?t : real} ((f(?up, ?t, ?q) <= 2.0) ^ (NF(?up, ?t, ?q) <= 5.0) ^ (?up == ?q))]]
        BOOL_EXPR e1   = new COMP_EXPR(p1,new REAL_CONST_EXPR(2.0),"<=");
        BOOL_EXPR e2   = new COMP_EXPR(p2,new REAL_CONST_EXPR(5.0),"<=");
        COMP_EXPR e3   = new COMP_EXPR(lvar_1,lvar_3,"==");
        CONN_EXPR e4   = new CONN_EXPR(e1,e2,"^");
        CONN_EXPR e5   = new CONN_EXPR(e4,e3,"^");
        QUANT_EXPR e6  = new QUANT_EXPR("exists",  array_ltyped_1_t,e5);
        QUANT_EXPR final_expr = new QUANT_EXPR("forall",array_ltyped_1_up,e6);
        e6.substitute(subs_up,constants,objects);
        final_expr.substitute(subs_q,constants,objects);



        System.out.println("dkjfdkjfkd");




    }


    public static void testCase2() throws Exception {



        //LTERMS, LVARS, LCONSTS
        LTERM lvar_1 = new LVAR("?up");
        LTERM lvar_2 = new LVAR("?t");
        LTERM lvar_3 = new LVAR("?q");
        ArrayList<LTERM> lterms_1_1 = new ArrayList<>(); lterms_1_1.add(lvar_1);
        ArrayList<LTERM> lterms_2_1 = new ArrayList<>(); lterms_2_1.add(lvar_2);
        ArrayList<LTERM> lterms_3_1 = new ArrayList<>(); lterms_3_1.add(lvar_3);

        ArrayList<LTERM> lterms_1_3 = new ArrayList<>(); lterms_1_3.add(lvar_1); lterms_1_3.add(lvar_2); lterms_1_3.add(lvar_3);


        LCONST lconst_t1 = new OBJECT_VAL("$t1");
        LCONST lconst_t2 = new OBJECT_VAL("$t2");
        LCONST lconst_t3 = new OBJECT_VAL("$t3");

        //Adding PVAR_EXPRS

        //This is PVAR_EXPR f, NF.
        PVAR_EXPR p1 =new PVAR_EXPR("f",lterms_1_3);
        PVAR_EXPR p2 = new PVAR_EXPR("NF",lterms_1_3);

        //LTYPED_VAR
        LTYPED_VAR ltyped_1 = new LTYPED_VAR("?t","real") ;
        LTYPED_VAR ltyped_2 = new LTYPED_VAR("?up", "real");
        LTYPED_VAR ltyped_3 = new LTYPED_VAR("?q","real");
        ArrayList<LTYPED_VAR> array_ltyped_1_t  = new ArrayList<>(); array_ltyped_1_t.add(ltyped_1);
        ArrayList<LTYPED_VAR> array_ltyped_1_q  = new ArrayList<>(); array_ltyped_1_q.add(ltyped_3);
        ArrayList<LTYPED_VAR> array_ltyped_1_up = new ArrayList<>(); array_ltyped_1_up.add(ltyped_2);
        ArrayList<LTYPED_VAR> array_ltyped_2 = new ArrayList<>(); array_ltyped_2.add(ltyped_1); array_ltyped_2.add(ltyped_2);
        ArrayList<LTYPED_VAR> array_ltyped_3 = new ArrayList<>(); array_ltyped_3.add(ltyped_1); array_ltyped_3.add(ltyped_2); array_ltyped_3.add(ltyped_3);


        //Defining objects



        //Adding values to Contants
        Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants = new HashMap<>();

        Map<ArrayList<LCONST>,Object> cons2 = new HashMap<>();
        for(int i = 1; i<3 ; i++){
            for(int j = 1 ; j<3;j++){
                for (int k =1 ; k < 3 ; k++) {
                    ArrayList<LCONST> lconst_array = new ArrayList<>();
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(i).toString()));
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(j).toString()));
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(k).toString()));
                    if(i==1 && j==1 && k==1){
                        cons2.put(lconst_array, 0);
                    }else if(i==2 && j==1 && k==1){
                        cons2.put(lconst_array, 0);

                    }else{
                        cons2.put(lconst_array,10 + Integer.valueOf(i));
                    }

                }
            }

        }
        constants.put(p2._pName,cons2);

        //Defining Objects
        Map<TYPE_NAME, OBJECTS_DEF> objects = new HashMap<>();
        TYPE_NAME up_type = new TYPE_NAME("real");
        TYPE_NAME q_type  = new TYPE_NAME("real");
        TYPE_NAME t_type  = new TYPE_NAME("real");

        ArrayList<Object> temp_objects = new ArrayList<>();
        temp_objects.add(lconst_t1); temp_objects.add(lconst_t2); temp_objects.add(lconst_t3);
        OBJECTS_DEF ob = new OBJECTS_DEF("real",temp_objects);
        objects.put(up_type,ob);
        objects.put(q_type,ob);
        objects.put(t_type,ob);

        //Adding Subs
        Map<LVAR, LCONST> subs_q  = new HashMap<>();
        Map<LVAR, LCONST> subs_t  = new HashMap<>();
        Map<LVAR,LCONST>  subs_up = new HashMap<>();

        subs_q.put((LVAR)lvar_3,lconst_t1);
        subs_t.put((LVAR)lvar_1,lconst_t1);
        subs_up.put((LVAR)lvar_2,lconst_t1);
        /////////////////////////////////////////////////////



        BOOL_EXPR e1   = new COMP_EXPR(p1,new REAL_CONST_EXPR(2.0),"<=");
        BOOL_EXPR e2   = new COMP_EXPR(p2,new REAL_CONST_EXPR(5.0),"<=");
        COMP_EXPR e3   = new COMP_EXPR(lvar_3,lconst_t1,"==");
        CONN_EXPR e4   = new CONN_EXPR(e1,e2,"^");
        CONN_EXPR e5   = new CONN_EXPR(e4,e3,"^");
        QUANT_EXPR e6  = new QUANT_EXPR("exists",  array_ltyped_1_t,e5);
        QUANT_EXPR final_expr = new QUANT_EXPR("forall",array_ltyped_1_up,e6);

        System.out.println("dflkdlfkld");





    }





    public static void testCase3() throws Exception {



        //LTERMS, LVARS, LCONSTS
        LTERM lvar_1 = new LVAR("?up");
        LTERM lvar_2 = new LVAR("?t");
        LTERM lvar_3 = new LVAR("?q");
        ArrayList<LTERM> lterms_1_1 = new ArrayList<>(); lterms_1_1.add(lvar_1);
        ArrayList<LTERM> lterms_2_1 = new ArrayList<>(); lterms_2_1.add(lvar_2);
        ArrayList<LTERM> lterms_3_1 = new ArrayList<>(); lterms_3_1.add(lvar_3);

        ArrayList<LTERM> lterms_1_3 = new ArrayList<>(); lterms_1_3.add(lvar_1); lterms_1_3.add(lvar_2); lterms_1_3.add(lvar_3);


        LCONST lconst_t1 = new OBJECT_VAL("$t1");
        LCONST lconst_t2 = new OBJECT_VAL("$t2");
        LCONST lconst_t3 = new OBJECT_VAL("$t3");

        //Adding PVAR_EXPRS

        //This is PVAR_EXPR f, NF.
        PVAR_EXPR p1 =new PVAR_EXPR("f",lterms_1_3);
        PVAR_EXPR p2 = new PVAR_EXPR("NF",lterms_1_3);

        //LTYPED_VAR
        LTYPED_VAR ltyped_1 = new LTYPED_VAR("?t","real") ;
        LTYPED_VAR ltyped_2 = new LTYPED_VAR("?up", "real");
        LTYPED_VAR ltyped_3 = new LTYPED_VAR("?q","real");
        ArrayList<LTYPED_VAR> array_ltyped_1_t  = new ArrayList<>(); array_ltyped_1_t.add(ltyped_1);
        ArrayList<LTYPED_VAR> array_ltyped_1_q  = new ArrayList<>(); array_ltyped_1_q.add(ltyped_3);
        ArrayList<LTYPED_VAR> array_ltyped_1_up = new ArrayList<>(); array_ltyped_1_up.add(ltyped_2);
        ArrayList<LTYPED_VAR> array_ltyped_2 = new ArrayList<>(); array_ltyped_2.add(ltyped_1); array_ltyped_2.add(ltyped_2);
        ArrayList<LTYPED_VAR> array_ltyped_3 = new ArrayList<>(); array_ltyped_3.add(ltyped_1); array_ltyped_3.add(ltyped_2); array_ltyped_3.add(ltyped_3);


        //Defining objects



        //Adding values to Contants
        Map<PVAR_NAME, Map<ArrayList<LCONST>, Object>> constants = new HashMap<>();

        Map<ArrayList<LCONST>,Object> cons2 = new HashMap<>();
        for(int i = 1; i<3 ; i++){
            for(int j = 1 ; j<3;j++){
                for (int k =1 ; k < 3 ; k++) {
                    ArrayList<LCONST> lconst_array = new ArrayList<>();
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(i).toString()));
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(j).toString()));
                    lconst_array.add(new OBJECT_VAL("$t" + Integer.valueOf(k).toString()));
                    if(i==1 && j==1 && k==1){
                        cons2.put(lconst_array, 0);
                    }else if(i==2 && j==1 && k==1){
                        cons2.put(lconst_array, 0);

                    }else{
                        cons2.put(lconst_array,10 + Integer.valueOf(i));
                    }

                }
            }

        }
        constants.put(p2._pName,cons2);

        //Defining Objects
        Map<TYPE_NAME, OBJECTS_DEF> objects = new HashMap<>();
        TYPE_NAME up_type = new TYPE_NAME("real");
        TYPE_NAME q_type  = new TYPE_NAME("real");
        TYPE_NAME t_type  = new TYPE_NAME("real");

        ArrayList<Object> temp_objects = new ArrayList<>();
        temp_objects.add(lconst_t1); temp_objects.add(lconst_t2); temp_objects.add(lconst_t3);
        OBJECTS_DEF ob = new OBJECTS_DEF("real",temp_objects);
        objects.put(up_type,ob);
        objects.put(q_type,ob);
        objects.put(t_type,ob);

        //Adding Subs
        Map<LVAR, LCONST> subs_q  = new HashMap<>();
        Map<LVAR, LCONST> subs_t  = new HashMap<>();
        Map<LVAR,LCONST>  subs_up = new HashMap<>();

        subs_q.put((LVAR)lvar_3,lconst_t1);
        subs_t.put((LVAR)lvar_1,lconst_t1);
        subs_up.put((LVAR)lvar_2,lconst_t1);
        /////////////////////////////////////////////////////



        //BOOL_EXPR e1   = new COMP_EXPR(p1,new REAL_CONST_EXPR(2.0),"<=");
        BOOL_EXPR e2   = new COMP_EXPR(p2,new REAL_CONST_EXPR(5.0),"<=");
        COMP_EXPR e3   = new COMP_EXPR(lvar_3,lconst_t1,"==");
        //CONN_EXPR e4   = new CONN_EXPR(e1,e2,"^");
        CONN_EXPR e5   = new CONN_EXPR(e2,e3,"^");
        QUANT_EXPR e6  = new QUANT_EXPR("exists",  array_ltyped_1_t,e5);
        QUANT_EXPR final_expr = new QUANT_EXPR("forall",array_ltyped_1_up,e6);

        System.out.println("dflkdlfkld");





    }



    public static void main(String[] args)throws Exception{

       testCase1();




    }






}
