//package rddl.det.mip;
//
//import java.time.LocalDate;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.TreeMap;
//import java.io.*;
//
//import javax.management.relation.RoleResult;
//
//import org.apache.commons.math3.random.RandomDataGenerator;
//
//import gurobi.GRBException;
//import gurobi.GRBModel;
//import rddl.EvalException;
//import rddl.State;
//import util.Pair;
//import util.Timer;
//import rddl.RDDL.LCONST;
//import rddl.RDDL.LVAR;
//import rddl.RDDL.PVAR_INST_DEF;
//import rddl.RDDL.PVAR_NAME;
//import rddl.viz.StateViz;
//import rddl.RDDL.OBJECT_VAL;
//
//
//
//public class EmergencyDomainPolicyRollOut extends EmergencyDomainHOPTranslate {
//
//	private static final PVAR_NAME vehicleInServicePvar = new PVAR_NAME("unitInService");
//	private static final PVAR_NAME callMilesPvar = new PVAR_NAME("callMiles");
//	private static final ArrayList<ArrayList<LCONST>> vehicleSubs;
//	private static final int NUMVEHICLES=5;
//	private static final int ROLLOUT_LOOKAHEAD = 5;
//	private static final int ROLLOUT_TRAJECTORIES = 2;
//	private EmergencyDomainDataReel reel;
//	//private RandomDataGenerator rand;
//
//	static{
//		vehicleSubs = new ArrayList<ArrayList<LCONST>>();
//		for( int i = 1; i<= NUMVEHICLES; ++i ){
//			vehicleSubs.add( new ArrayList<LCONST>( Arrays.<LCONST>asList( new OBJECT_VAL[]{new OBJECT_VAL("u" + i)} ) ) );
//		}
//	}
//
//	private PVAR_NAME hasCapabilityPvar = new PVAR_NAME("HasCapability");
//	private PVAR_NAME causeRequirementPvar = new PVAR_NAME("CauseRequirement");
//
//	private static final ArrayList<OBJECT_VAL> roles = new ArrayList<>( Arrays.<OBJECT_VAL>asList( new OBJECT_VAL[]{
//			new OBJECT_VAL("Engine"), new OBJECT_VAL("Ambulance"),
//			new OBJECT_VAL("Ladder"), new OBJECT_VAL("Command") } ) );
//
//	public EmergencyDomainPolicyRollOut(List<String> args) throws Exception {
//		super(args);
//	}
//
//
//
//	public ArrayList<ArrayList<PVAR_INST_DEF>>  getLegalActions(State s) throws EvalException{
//
//		//this function is for running trajectories
//
//
//
//		s.computeIntermFluents( new ArrayList<PVAR_INST_DEF>(), rand);
//
//		ArrayList<ArrayList<PVAR_INST_DEF>> ret = new ArrayList<ArrayList<PVAR_INST_DEF>>();
//
//
//
//		//Map<String, Boolean> added = new HashMap<>();
//		String this_code = EmergencyDomainDataReelElement.getCurrentCauseCode(s);
//		System.out.println("Here code for debug");
//		//TreeMap<Double,ArrayList<LCONST>> candidates = new TreeMap<>();
//		int count =0;
//		for( OBJECT_VAL this_ole : roles ){
//			//This function talks about the required roles....
//			int role_required = (int)s.getPVariableAssign(causeRequirementPvar , new ArrayList<LCONST>( Arrays.<LCONST>asList(
//						new LCONST[]{ new OBJECT_VAL(this_code), this_ole } )));
//
//			System.out.println(this_ole + " " + role_required );
//
//			if( role_required == 0 ){
//				continue;
//			}
//
//			TreeMap<Double,ArrayList<LCONST>> candidates = new TreeMap<>();
//			for( ArrayList<LCONST> vehicleSub : vehicleSubs ){
////				if( added.containsKey(vehicleSub.get(0)._sConstValue) ){
////					continue;
////				}
//				//System.out.println(vehicleSub);
//				//System.out.println(((Number)s.getPVariableAssign(callMilesPvar, vehicleSub)).doubleValue());
//
//				double this_miles = ((Number)s.getPVariableAssign(callMilesPvar, vehicleSub)).doubleValue();
//				boolean this_service = (boolean)s.getPVariableAssign(vehicleInServicePvar, vehicleSub);
//				ArrayList<LCONST> this_params = new ArrayList<LCONST>();
//				this_params.add( vehicleSub.get(0) );
//				this_params.add( this_ole );
//
//				boolean this_capable = (boolean)s.getPVariableAssign(hasCapabilityPvar , this_params );
//
//				if( this_capable && this_service ){
//					if( !candidates.containsKey(this_miles) ){
//						this_miles += 0.01;
//					}
//					candidates.put( this_miles, this_params );
//					System.out.println("Cause:"+ this_ole  + "     Vehicle Number :"+ vehicleSub + "    Miles_Away:" + this_miles + "    Serviced:" +this_service + "      Capability:" + this_capable );
//
//				}
//			}
//
//			for( int i = 0 ; i < role_required; ++i ){
//
//
//
//
//				if(count ==0) {
//					for(Map.Entry<Double,ArrayList<LCONST>> entry : candidates.entrySet()) {
//
//
//
//						ArrayList<PVAR_INST_DEF> tempArrayList = new ArrayList<PVAR_INST_DEF>();
//						tempArrayList.add( new PVAR_INST_DEF("dispatch", true, entry.getValue()));
//						ret.add( tempArrayList);
//						count+=1;
//
//					}
//				}
//
//
//
//
//
//
//				else {
//					for(Map.Entry<Double,ArrayList<LCONST>> entry : candidates.entrySet()) {
//						for(int j=1; j<=ret.size(); ++j) {
//
//							ret.get(j-1).add(new PVAR_INST_DEF("dispatch", true, entry.getValue() )  );
//
//
//						}
//
//					}
//
//
//
//
//
//
//
//
//				}
//
//
////				Entry<Double, ArrayList<LCONST>> selected = candidates.pollFirstEntry();
////				if( selected == null ){
////					break;
////				}
//				//ret.add( new PVAR_INST_DEF("dispatch", true, selected.getValue() ) );
//				//added.put( selected.getValue().get(0)._sConstValue, true );
//			}
//
//
//
//
//
//		}
//
//
//		return ret;
//
//
//
//
//	}
//
//
//	public State getNextState(final State s,ArrayList<PVAR_INST_DEF> actions) throws EvalException {
//
//		reel_test.resetTestIndex( 0 );
//		EmergencyDomainDataReelElement stored_next_thing = null;
//		EmergencyDomainDataReelElement cur_thing = new EmergencyDomainDataReelElement(s);
//		ArrayList<Integer> next_indices = reel_test.getLeads(cur_thing, reel_test.getTestingFoldIdx() );
//		EmergencyDomainDataReelElement thatElem = reel_test.getInstance(
//				next_indices.get( rand.nextInt(0, next_indices.size()-1) ), reel_test.getTestingFoldIdx() );
//
//		//fix date to be not in the past
//		LocalDate newCallDate;
//		if( thatElem.callTime.isBefore(cur_thing.callTime) ){
//			newCallDate = LocalDate.ofYearDay( cur_thing.callDate.getYear(), cur_thing.callDate.getDayOfYear()+1);
//		}else{
//			newCallDate = LocalDate.ofYearDay( cur_thing.callDate.getYear(), cur_thing.callDate.getDayOfYear());
//		}
//		stored_next_thing = new EmergencyDomainDataReelElement( thatElem.callId, thatElem.natureCode,
//				newCallDate, thatElem.callTime, thatElem.callAddress, thatElem.callX, thatElem.callY, false );
//
//
//		s.computeNextState(actions, rand);
//
//		s.advanceNextState();
//
//		stored_next_thing.setInState(s);
//
//
//
//
//
//
//
//		return s;
//
//
//	}
//
//
//
//
//	public State deepCloneState(State s) throws EvalException,CloneNotSupportedException{
//		State temp_s = (State) s.clone();
//
//
//		return s;
//	}
//
//
//	public void serializeState(State s) throws EvalException{
//
//
//        try
//        {
//            //Saving of object in a file
//            FileOutputStream file = new FileOutputStream("./files/serial_objects/state.ser");
//            ObjectOutputStream out = new ObjectOutputStream(file);
//
//            // Method for serialization of object
//            out.writeObject(s);
//
//            out.close();
//            file.close();
//
//            System.out.println("Object has been serialized");
//
//        }
//
//        catch(IOException ex)
//        {
//        		ex.printStackTrace();
//        }
//
//
//
//	}
//
//
//	public State deSerializeState() {
//
//		State object1 = null;
//
//		try {
//		// Reading the object from a file
//	    FileInputStream file = new FileInputStream("./files/serial_objects/state.ser");
//	    ObjectInputStream in = new ObjectInputStream(file);
//
//	    // Method for deserialization of object
//	    object1 = (State)in.readObject();
//
//	    in.close();
//	    file.close();
//
//
//		}
//		catch(IOException ex)
//		{
//			System.out.println("kdfjkdkfdf");
//
//
//		}
//
//		catch(ClassNotFoundException ex)
//		{
//			System.out.println("kdjfkdjfkdjfkd");
//		}
//		return object1;
//
//
//	}
//
//
//	@Override
//	public ArrayList<PVAR_INST_DEF> getActions(State s ) throws EvalException, CloneNotSupportedException {
//
//
//		//for all legal actions
//
//		ArrayList<ArrayList<PVAR_INST_DEF>> candidates = getLegalActions(s);
//
//		HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>> a = getSubsWithDefaults(s);
//
//
//		for(int a_size=1; a_size<=candidates.size(); ++a_size) {
//			State temp_s = deepCloneState(s);
//			//State temp_s = (State) s.clone();
//			//temp_s._actions = (HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, Object>>) s._actions.clone();
//			State s1 = getNextState(temp_s,candidates.get(a_size-1));
//
//			State s3 = deSerializeState();
//
//			for(int i =1 ; i<=ROLLOUT_TRAJECTORIES;++i) {
//
//
//				double curReward = getTrajectory(s);
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
//		}
//
//
//
//
//		return candidates.get(0);
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
//	public double getTrajectory(State s) throws EvalException{
//
//
//		//Initalize for trajectory
//		EmergencyDomainDataReelElement stored_next_thing = null;
//
//
//		for(int i=1; i<=ROLLOUT_LOOKAHEAD; ++i) {
//			ArrayList<PVAR_INST_DEF> rddl_action =getRollOutAction(s);
//
//
//
//
//
//
//
//			if( true ){
//				//This piece of code is for getting randomly n
//				EmergencyDomainDataReelElement cur_thing = new EmergencyDomainDataReelElement(rddl_state);
//				ArrayList<Integer> next_indices = reel.getLeads(cur_thing, reel.getTestingFoldIdx() );
//				EmergencyDomainDataReelElement thatElem = reel.getInstance(
//						next_indices.get( rand.nextInt(0, next_indices.size()-1) ), reel.getTestingFoldIdx() );
//
//				//fix date to be not in the past
//				LocalDate newCallDate;
//				if( thatElem.callTime.isBefore(cur_thing.callTime) ){
//					newCallDate = LocalDate.ofYearDay( cur_thing.callDate.getYear(), cur_thing.callDate.getDayOfYear()+1);
//				}else{
//					newCallDate = LocalDate.ofYearDay( cur_thing.callDate.getYear(), cur_thing.callDate.getDayOfYear());
//				}
//				stored_next_thing = new EmergencyDomainDataReelElement( thatElem.callId, thatElem.natureCode,
//						newCallDate, thatElem.callTime, thatElem.callAddress, thatElem.callX, thatElem.callY, false );
////
////				System.out.println( "Current : " + cur_thing );
////				System.out.println( "Candidates : " + next_indices );
////				System.out.println( "Selected : " + stored_next_thing );
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
//		return 0.0;
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
//	public ArrayList<PVAR_INST_DEF> getRollOutAction(State s) throws EvalException {
//		s.computeIntermFluents( new ArrayList<PVAR_INST_DEF>(), rand);
//
//		ArrayList<PVAR_INST_DEF> ret = new ArrayList<PVAR_INST_DEF>();
//		Map<String, Boolean> added = new HashMap<>();
//		String this_code = EmergencyDomainDataReelElement.getCurrentCauseCode(s);
//		  System.out.println("Here code for debug");
//		for( OBJECT_VAL this_ole : roles ){
//			//This function talks about the required roles....
//			int role_required = (int)s.getPVariableAssign(causeRequirementPvar , new ArrayList<LCONST>( Arrays.<LCONST>asList(
//						new LCONST[]{ new OBJECT_VAL(this_code), this_ole } )));
//
//			System.out.println(this_ole + " " + role_required );
//
//			if( role_required == 0 ){
//				continue;
//			}
//
//			TreeMap<Double,ArrayList<LCONST>> candidates = new TreeMap<>();
//			for( ArrayList<LCONST> vehicleSub : vehicleSubs ){
//				if( added.containsKey(vehicleSub.get(0)._sConstValue) ){
//					continue;
//				}
//				//System.out.println(vehicleSub);
//				//System.out.println(((Number)s.getPVariableAssign(callMilesPvar, vehicleSub)).doubleValue());
//
//				double this_miles = ((Number)s.getPVariableAssign(callMilesPvar, vehicleSub)).doubleValue();
//				boolean this_service = (boolean)s.getPVariableAssign(vehicleInServicePvar, vehicleSub);
//				ArrayList<LCONST> this_params = new ArrayList<LCONST>();
//				this_params.add( vehicleSub.get(0) );
//				this_params.add( this_ole );
//
//				boolean this_capable = (boolean)s.getPVariableAssign(hasCapabilityPvar , this_params );
//
//				if( this_capable && this_service ){
//					if( !candidates.containsKey(this_miles) ){
//						this_miles += 0.01;
//					}
//					candidates.put( this_miles, this_params );
//				}
//				System.out.println("Cause:"+ this_ole  + "     Vehicle Number :"+ vehicleSub + "    Miles_Away:" + this_miles + "    Serviced:" +this_service + "      Capability:" + this_capable );
//			}
//
////			Collections.sort( candidates );
//			System.out.println(this_ole + " " + candidates );
//
//			for( int i = 0 ; i < role_required; ++i ){
//				Entry<Double, ArrayList<LCONST>> selected = candidates.pollFirstEntry();
//				if( selected == null ){
//					break;
//				}
//				ret.add( new PVAR_INST_DEF("dispatch", true, selected.getValue() ) );
//				added.put( selected.getValue().get(0)._sConstValue, true );
//			}
//		}
//
//		s.clearIntermFluents();
//		//ret Returns the actions.
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
//
//
//
//
//
//	@Override
//	protected void cleanUp(final GRBModel grb_model) throws GRBException {
//		return;
//	}
//
//	@Override
//	protected void handleOOM(final GRBModel grb_model) {
//		return;
//	}
//
//
//
//
//
//
//	public static void main(String[] args) throws Exception {
//		System.out.println( Arrays.toString( args ) );
//
//		EmergencyDomainHOPTranslate planner = new EmergencyDomainPolicyRollOut(
//				Arrays.asList( args ).subList(0, args.length ));
//
//
//
//		System.out.println( planner.evaluateRollOutPlanner(
//				Integer.parseInt( args[args.length-2-2] ),
//				null, //new EmergencyDomainStateViz(1300,30,1500,80),
//				Boolean.parseBoolean( args[ args.length-1-2 ] ) ) );
//
//
//
//	}
//
//}
