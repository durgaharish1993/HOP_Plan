package rddl.det.mip;
import org.rosuda.JRI.Rengine;
import org.rosuda.JRI.RVector;


public class TestRJava {











    public static void main(String[] args) throws Exception {


        String javaVector = "c(1,2,3,4,5)";
        Rengine engine    = new Rengine(new String[] {"--no-save"},false,null);

        engine.eval("library(earth)");
        engine.eval("x_values<-runif(1000,0,10)");
        engine.eval("y_values<-x_values**2");
        engine.eval("model<-earth(y_values ~ x_values)");
        engine.eval("a=predict(model,1)");
        String output =engine.eval("format(model,style='pmax')").asString();
        System.out.println(output);

        engine.eval("rVector="+javaVector);

        engine.eval("meanVal=mean(rVector)");

        double mean = engine.eval("meanVal").asDouble();
        System.out.println("Mean of Given Vector is :" + mean);













    }











}


