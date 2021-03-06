/**
 * RDDL: Main client code for interaction with RDDLSim server
 * 
 * @author Sungwook Yoon (sungwook.yoon@gmail.com)
 * @version 10/1/10
 *
 **/

package rddl.competition;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.xerces.parsers.DOMParser;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import rddl.EvalException;
import rddl.RDDL;
import rddl.RDDL.DOMAIN;
import rddl.RDDL.INSTANCE;
import rddl.RDDL.LCONST;
import rddl.RDDL.NONFLUENTS;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.RDDL.PVAR_NAME;
import rddl.State;
import rddl.parser.parser;
import rddl.policy.Policy;
import rddl.policy.RandomBoolPolicy;
import rddl.policy.RandomEnumPolicy;
import rddl.viz.StateViz;
import util.Pair;

/** The SocketClient class is a simple example of a TCP/IP Socket Client.
 *
 */

public class Client {
	
	public static final boolean SHOW_XML = false;
	public static final boolean SHOW_MSG = false;
	public static final boolean SHOW_MEMORY_USAGE = true;
	public static final Runtime RUNTIME = Runtime.getRuntime();
	public static final int DEFAULT_RANDOM_SEED = 0;
	private static DecimalFormat _df = new DecimalFormat("0.##");	
	enum XMLType {
		ROUND,TURN,ROUND_END,END_TEST,NONAME
	}

	private static RDDL rddl = null;

	int numRounds;
	double timeAllowed;
	int curRound;
	double reward;
	int id;
	String taskDec;

	Client () {
		numRounds = 0;
		timeAllowed = 0;
		curRound = 0;
		reward = 0;
		id = 0;
	}
	
	/**
	 * 
	 * @param args
	 * 1. rddl description file name with RDDL syntax, with complete path (sysadmin.rddl)
	 * 2. instance name in rddl / directory of rddl files
	 * 3. host name (local host)
	 * 4. client name (for record keeping purpose on server side. identify yourself with name.
	 * 5. (optional) port number
	 * 6. (optional) random seed
	 */
	public static void main(String[] args) {

		System.setProperty( "ld.library.path", "/opt/gurobi600/linux64/lib/");
		System.out.println(System.getProperty("java.library.path"));

		/** Define a host server */
		String host = Server.HOST_NAME;
		/** Define a port */
		int port = Server.PORT_NUMBER;
		String clientName = "random";
		String instanceName = null;
		int randomSeed = DEFAULT_RANDOM_SEED;
		
		State      state;
		INSTANCE   instance;
		NONFLUENTS nonFluents = null;
		DOMAIN     domain;
		StateViz   stateViz;
		
		StringBuffer instr = new StringBuffer();
		String TimeStamp;
		
		if ( args.length < 4 ) {
			System.out.println("usage: rddlfilename hostname clientname policyclassname " +
					"(optional) portnumber randomSeed instanceName/directory");
			System.exit(1);
		}

		//This is new added to this file.
		host = args[1];
		clientName = args[2];
		port = Integer.valueOf(args[4]);
		instanceName = args[6];
		ArrayList<String> parameters = new ArrayList<String>(Arrays.asList(args).subList(7,args.length-2));

















		double timeLeft = 0;
		try {


			/** Obtain an address object of the server */
			InetAddress address = InetAddress.getByName(host);
			System.out.println(address.toString());
			/** Establish a socket connetion */
			//Socket connection = new Socket(address, port);
			Socket connection = new Socket();
			connection.setSoTimeout(100);
			connection.connect(new InetSocketAddress(address, port), 100);

			System.out.println("RDDL client initialized");

			/** Instantiate a BufferedOutputStream object */
			BufferedOutputStream bos = new BufferedOutputStream(connection.
					getOutputStream());
			/** Instantiate an OutputStreamWriter object with the optional character
			 * encoding.
			 */
			OutputStreamWriter osw = new OutputStreamWriter(bos, "US-ASCII");
			/** Write across the socket connection and flush the buffer */
			String msg = createXMLSessionRequest(instanceName, clientName);
			Server.sendOneMessage(osw, msg);
			BufferedInputStream isr = new BufferedInputStream(connection.getInputStream());
			/**Instantiate an InputStreamReader with the optional
			 * character encoding.
			 */
			//InputStreamReader isr = new InputStreamReader(bis, "US-ASCII");
			DOMParser p = new DOMParser();

			/**Read the socket's InputStream and append to a StringBuffer */

			InputSource isrc = Server.readOneMessage(isr);
			Client client = processXMLSessionInit(p, isrc);
			System.out.println(client.id + ":" + client.numRounds);


			//Instead of getting the file, we need to ge the instance and domain from a file.

			String domain_instance_text = client.taskDec;


			Files.deleteIfExists(Paths.get("/tmp/temp_domain_instance.rddl"));



			try (Writer writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream("/tmp/temp_domain_instance.rddl"), "utf-8")))

			{
				writer.write(domain_instance_text);
			}







			// Cannot assume always in rddl.policy
			Class c = Class.forName(args[3]);
			
			// Load RDDL files
			//rddl = new RDDL(args[0]);
			rddl = new RDDL("/tmp/temp_domain_instance.rddl");
			
			if ( args.length > 4 ) {
				port = Integer.valueOf(args[4]);
			}
			if ( args.length > 5) {
				randomSeed = Integer.valueOf(args[5]);
			}
			if ( args.length > 6) {
				instanceName = args[6];
			}
			if (!rddl._tmInstanceNodes.containsKey(instanceName)) {
				System.out.println("Instance name '" + instanceName + "' not found in " + args[0] + "\nPossible choices: " + rddl._tmInstanceNodes.keySet());
				System.exit(1);
			}
			state = new State();
			

			
			instance = rddl._tmInstanceNodes.get(instanceName);
			if (instance._sNonFluents != null) {
				nonFluents = rddl._tmNonFluentNodes.get(instance._sNonFluents);
			}
			domain = rddl._tmDomainNodes.get(instance._sDomain);
			if (nonFluents != null && !instance._sDomain.equals(nonFluents._sDomain)) {
				System.err.println("Domain name of instance and fluents do not match: " + 
							instance._sDomain + " vs. " + nonFluents._sDomain);
				System.exit(1);
			}
			
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




			//This is new change to this file.
			// Note: following constructor approach suggested by Alan Olsen
			//Policy policy = (Policy)c.getConstructor(
			//		new Class[]{ArrayList.class,RDDL.class,State.class}).newInstance(parameters,rddl,state);



			//policy.setRDDL(rddl);
			//policy.setRandSeed(randomSeed);




			// Not strictly enforcing flags anymore... 
			//if ((domain._bPartiallyObserved && state._alObservNames.size() == 0)
			//		|| (!domain._bPartiallyObserved && state._alObservNames.size() > 0)) {
			//	System.err.println("Domain '" + domain._sDomainName + "' partially observed flag and presence of observations mismatched.");
			//}



			// This piece of code is for exploration in first few rounds (20 rounds)
			// These are the changes that I am interested in changing : By Exploring first 20 rounds, I need to get approx. best lookahead value.

			//Policy policy = (Policy)c.getConstructor(
			//		new Class[]{ArrayList.class,RDDL.class,State.class}).newInstance(parameters,rddl,state);







			//##################################################################################################
			//These are the things which are needed for exploration.
			HashMap<Integer, Pair<Integer,Double>> exploration_rewards = new HashMap<>();

			//HashMap<Integer,Double> round_avg_time = new HashMap<>();
			ArrayList<Double> round_reward = new ArrayList<>();
			int exploration_rounds = 5;
			Integer current_lookAhead = 1;
			Integer next_lookahead = current_lookAhead;
			Boolean exploration = true;
			Double max_time_allowed = 2.5;
			Integer best_lookahead = current_lookAhead;


			//state = new State();
			int r = 0;
			Policy policy = null;
			for( ; r < client.numRounds; r++ ) {

				exploration = false;

				//Exploration phase
				if( r < exploration_rounds){


					exploration = true;




					parameters.set(2,current_lookAhead.toString());




					policy = (Policy)c.getConstructor(
							new Class[]{ArrayList.class,RDDL.class,State.class}).newInstance(parameters,rddl,state);




					//policy.setRDDL(rddl);
					policy.setRandSeed(randomSeed);



					//This is for next round.





				}

				else{


					exploration = false;
					parameters.set(2,best_lookahead.toString());
					policy = (Policy)c.getConstructor(
							new Class[]{ArrayList.class,RDDL.class,State.class}).newInstance(parameters,rddl,state);


					policy.setRandSeed(randomSeed);








				}





				if (SHOW_MEMORY_USAGE)
					System.out.print("[ Memory usage: " + 
							_df.format((RUNTIME.totalMemory() - RUNTIME.freeMemory())/1e6d) + "Mb / " + 
							_df.format(RUNTIME.totalMemory()/1e6d) + "Mb" + 
							" = " + _df.format(((double) (RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 
											   (double) RUNTIME.totalMemory())) + " ]\n");
				state.init(domain._hmObjects, nonFluents != null ? nonFluents._hmObjects : null, instance._hmObjects,
						domain._hmTypes, domain._hmPVariables, domain._hmCPF,
						instance._alInitState, nonFluents == null ? new ArrayList<PVAR_INST_DEF>() : nonFluents._alNonFluents, instance._alNonFluents,
						domain._alStateConstraints, domain._alActionPreconditions, domain._alStateInvariants,  
						domain._exprReward, instance._nNonDefActions);
				msg = createXMLRoundRequest("yes");
				Server.sendOneMessage(osw, msg);
				isrc = Server.readOneMessage(isr);
				timeLeft = processXMLRoundInit(p, isrc, r+1);
				policy.roundInit(timeLeft, instance._nHorizon, r+1 /*round*/, client.numRounds);
				if ( timeLeft < 0 ) {
					break;
				} // TODO
				int h =0;
				//System.out.println(instance._nHorizon);
				boolean round_ended_early = false;
				long exploration_round_time = 0l;

				for(; h < instance._nHorizon; h++ ) {

					long startTime = System.currentTimeMillis();



					if (SHOW_MSG) System.out.println("Reading turn message");
					isrc = Server.readOneMessage(isr);
					Element e = parseMessage(p, isrc);
					round_ended_early = e.getNodeName().equals(Server.ROUND_END);
					if (round_ended_early)
						break;
					if (SHOW_MSG) System.out.println("Done reading turn message");
					//if (SHOW_XML)
					//	Server.printXMLNode(e); // DEBUG
					ArrayList<PVAR_INST_DEF> obs = processXMLTurn(e,state);
					if (SHOW_MSG) System.out.println("Done parsing turn message");
					if ( obs == null ) {
						if (SHOW_MSG) System.out.println("No state/observations received.");
						if (SHOW_XML)
							Server.printXMLNode(p.getDocument()); // DEBUG
					} else if (domain._bPartiallyObserved) {
						state.clearPVariables(state._observ);
						state.setPVariables(state._observ, obs);
					} else {
						state.clearPVariables(state._state);
						state.setPVariables(state._state, obs);
					}




					ArrayList<PVAR_INST_DEF> actions = 
     						policy.getActions(obs == null ? null : state);


					System.out.println(("^^^^##################################################################################################"));
					System.out.println("^^^^THE CURRENT STATE IS : "+ state._state.toString());
					System.out.println("^^^^Next State is        : " + state._nextState.toString());
					System.out.println("^^^^Action taken is      :    "+ actions.toString());
					System.out.println(("^^^^##################################################################################################"));




					msg = createXMLAction(actions);
					if (SHOW_MSG)
						System.out.println("Sending: " + msg);
					Server.sendOneMessage(osw, msg);

					long endTime = System.currentTimeMillis();


					exploration_round_time +=  (endTime - startTime);

				}

				double avg_exploration_time  = (double)exploration_round_time;
				avg_exploration_time = avg_exploration_time/(1000*instance._nHorizon);


				if ( h < instance._nHorizon ) {
					break;
				}
				if (!round_ended_early) // otherwise isrc is the round-end message
					isrc = Server.readOneMessage(isr);
				Element round_end_msg = parseMessage(p, isrc);
				double reward = processXMLRoundEnd(round_end_msg);
				exploration_rewards.put(r,new Pair<>(current_lookAhead,reward));

				if(exploration){
					if(avg_exploration_time < max_time_allowed){
						current_lookAhead = getLookAheadValue(current_lookAhead,true);
					}
					else{

						current_lookAhead = getLookAheadValue(current_lookAhead,false);
					}




					if(r==exploration_rounds-1){

						System.out.println("dkfjdkfjkdjfkdjfkjdkfjdjfkd");
						//I need to do to find the best lookAhead for the domain .

					}




				}








				round_reward.add(reward);
				policy.roundEnd(reward);
				//System.out.println("Round reward: " + reward);













				if (getTimeLeft(round_end_msg) <= 0.0)
					break;
			}
			isrc = Server.readOneMessage(isr);
			double total_reward = processXMLSessionEnd(p, isrc);
			policy.sessionEnd(total_reward);
			
			/** Close the socket connection. */
			connection.close();
			System.out.println(instr);
		}
		catch (Exception g) {
			System.out.println("Exception: " + g);
			g.printStackTrace();
		}
	}




	static Integer getLookAheadValue(Integer current_lookahead, Boolean change){


		int next_look_ahead = current_lookahead;

		if(change){

			if(current_lookahead==1){
				next_look_ahead = 2;

			}
			else {

				next_look_ahead = next_look_ahead *2 ;




			}



		}

		else{


			next_look_ahead = current_lookahead;



		}

		return next_look_ahead;









	}
	
	static Element parseMessage(DOMParser p, InputSource isrc) throws RDDLXMLException {
		try {
			p.parse(isrc);
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			// Debug info to explain parse error
			//Server.showInputSource(isrc);
			throw new RDDLXMLException("sax exception");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RDDLXMLException("io exception");
		}
		if (SHOW_XML)
			Server.printXMLNode(p.getDocument()); // DEBUG

		return p.getDocument().getDocumentElement();
	}
	
	static String serialize(Document dom) {
		OutputFormat format = new OutputFormat(dom);
//		format.setIndenting(true);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		XMLSerializer xmls = new XMLSerializer(baos, format);
		try {
			xmls.serialize(dom);
			return baos.toString();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	static XMLType getXMLType(DOMParser p,InputSource isrc) {
		Element e = p.getDocument().getDocumentElement();
		if ( e.getNodeName().equals("turn") ) {
			return XMLType.TURN;
		} else if (e.getNodeName().equals("round")) {
			return XMLType.ROUND;
		} else if (e.getNodeName().equals("round-end")) {
			return XMLType.ROUND_END;
		} else if (e.getNodeName().equals("end-test")) {
			return XMLType.END_TEST;
		} else {
			return XMLType.NONAME;
		}
	}
	
	static String createXMLSessionRequest (String problemName, String clientName) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			//get an instance of builder
			DocumentBuilder db = dbf.newDocumentBuilder();

			//create an instance of DOM
			Document dom = db.newDocument();
			Element rootEle = dom.createElement(Server.SESSION_REQUEST);
			dom.appendChild(rootEle);
			Server.addOneText(dom, rootEle, Server.PROBLEM_NAME, problemName);
			Server.addOneText(dom, rootEle, Server.CLIENT_NAME, clientName);
			return serialize(dom);
		} catch (Exception e) {
			return null;
		}
	}
	
	static String createXMLRoundRequest(String decide) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document dom = db.newDocument();
			Element rootEle = dom.createElement(Server.ROUND_REQUEST);
			dom.appendChild(rootEle);

			Server.addOneText(dom,rootEle, Server.EXECUTE_POLICY,decide);

			return serialize(dom);
		} catch (Exception e) {
			return null;
		}
	}
	
	static Client processXMLSessionInit(DOMParser p, InputSource isrc) throws RDDLXMLException {
		try {
			p.parse(isrc);
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RDDLXMLException("sax exception");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RDDLXMLException("io exception");
		}
		Client c = new Client();
		Element e = p.getDocument().getDocumentElement();
		
		if ( !e.getNodeName().equals(Server.SESSION_INIT) ) {
			throw new RDDLXMLException("not session init");
		}
		ArrayList<String> r = Server.getTextValue(e, Server.SESSION_ID);
		if ( r != null ) {
			c.id = Integer.valueOf(r.get(0));
		}
		r = Server.getTextValue(e, Server.NUM_ROUNDS);
		if ( r != null ) {
			c.numRounds = Integer.valueOf(r.get(0));
		}
		r = Server.getTextValue(e, Server.TIME_ALLOWED);
		if ( r!= null ) {
			c.timeAllowed = Double.valueOf(r.get(0));
		}


		r = Server.getTextValue(e, Server.TASK_DESC);
		if ( r!= null ) {
			String decodedBytes = new String(Base64.getDecoder().decode(r.get(0)));
			c.taskDec = decodedBytes;
		}




		return c;
	}
	
	static String createXMLAction(ArrayList<PVAR_INST_DEF> ds) {
	//static String createXMLAction(State state, Policy policy) {

		System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
		try {  
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document dom = db.newDocument();
			Element actions = dom.createElement(Server.ACTIONS);
			dom.appendChild(actions);
			System.out.println("####################################################################");
			for ( PVAR_INST_DEF d : ds ) {
				Element action = dom.createElement(Server.ACTION);
				actions.appendChild(action);
				Element name = dom.createElement(Server.ACTION_NAME);
				action.appendChild(name);
				Text textName = dom.createTextNode(d._sPredName.toString());
				System.out.println(d._sPredName.toString());
				name.appendChild(textName);
				for( LCONST lc : d._alTerms ) {
					Element arg = dom.createElement(Server.ACTION_ARG);
					Text textArg = dom.createTextNode(lc.toSuppString()); // TODO $ <>... done$
					arg.appendChild(textArg);
					action.appendChild(arg);
				}

				Element value = dom.createElement(Server.ACTION_VALUE);
				Text textValue = d._oValue instanceof LCONST 
						? dom.createTextNode( ((LCONST)d._oValue).toSuppString())
						: dom.createTextNode( d._oValue.toString() ); // TODO $ <>... done$
				System.out.println(textValue);

				value.appendChild(textValue);
				action.appendChild(value);
			}
			// Sungwook: a noop is just an all-default action, not a special
			// action.  -Scott
			//if ( ds.size() == 0) {
			//	Element noop = dom.createElement(Server.NOOP);
			//	actions.appendChild(noop);
			//}

			if (SHOW_XML) {
				Server.printXMLNode(dom);
				System.out.println();
				System.out.flush();
			}

			return serialize(dom);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	static int getANumber (DOMParser p, InputSource isrc, 
			String parentName, String name) {
		Element e = p.getDocument().getDocumentElement();
		if ( e.getNodeName().equals(parentName) ) {
			String turnnum = Server.getTextValue(e, name).get(0);
			return Integer.valueOf(turnnum);
		}
		return -1;
	}

	static double processXMLRoundInit(DOMParser p, InputSource isrc,
			int curRound) throws RDDLXMLException {
		try {
			p.parse(isrc);
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RDDLXMLException("sax exception");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RDDLXMLException("io exception");
		}
		Element e = p.getDocument().getDocumentElement();
		if ( !e.getNodeName().equals(Server.ROUND_INIT)) {
			return -1;
		}
		ArrayList<String> r = Server.getTextValue(e, Server.ROUND_NUM);
		if ( r == null || curRound != Integer.valueOf(r.get(0))) {
			return -1;
		}
		r =	Server.getTextValue(e, Server.TIME_LEFT);
		if ( r == null ) {
			return -1;
		}
		return Double.valueOf(r.get(0));
	}
	
	static double getTimeLeft(Element e) {
		ArrayList<String> r = Server.getTextValue(e, Server.TIME_LEFT);
		if ( r == null ) {
			return -1;
		}
		double val = Double.parseDouble(r.get(0));
		return val;
		//return Long.valueOf(r.get(0));
	}
	
	static ArrayList<PVAR_INST_DEF> processXMLTurn (Element e,
			State state) throws RDDLXMLException {

		if ( e.getNodeName().equals(Server.TURN) ) {
			
			// We need to be able to distinguish no observations from
			// all default observations.  -Scott
			if (e.getElementsByTagName(Server.NULL_OBSERVATIONS).getLength() > 0) {
				return null;
			}
			
			// FYI: I think nl is never null.  -Scott
			NodeList nl = e.getElementsByTagName(Server.OBSERVED_FLUENT);
			if(nl != null && nl.getLength() > 0) {
				ArrayList<PVAR_INST_DEF> ds = new ArrayList<PVAR_INST_DEF>();
				for(int i = 0 ; i < nl.getLength();i++) {
					Element el = (Element)nl.item(i);
					String name = Server.getTextValue(el, Server.FLUENT_NAME).get(0);
					ArrayList<String> args = Server.getTextValue(el, Server.FLUENT_ARG);
					ArrayList<LCONST> lcArgs = new ArrayList<LCONST>();
					for( String arg : args ) {
						if (arg.startsWith("@"))
							lcArgs.add(new RDDL.ENUM_VAL(arg));
						else // TODO $ <> (forgiving)... done$
							lcArgs.add(new RDDL.OBJECT_VAL(arg));
					}
					String value = Server.getTextValue(el, Server.FLUENT_VALUE).get(0);
					Object r = Server.getValue(name, value, state); // TODO $ <> (forgiving)... done$
					PVAR_INST_DEF d = new PVAR_INST_DEF(name, r, lcArgs);
					ds.add(d);
				}
				return ds;
			} else
				return new ArrayList<PVAR_INST_DEF>();
		}
		throw new RDDLXMLException("Client.processXMLTurn: Should not reach this point");
		//return null;
	}
	
	static double processXMLRoundEnd(Element e) throws RDDLXMLException {
		if ( e.getNodeName().equals(Server.ROUND_END) ) {
			ArrayList<String> text = Server.getTextValue(e, Server.ROUND_REWARD);
			if ( text == null ) {
				return -1;
			}
			return Double.valueOf(text.get(0));
		}
		return -1;
	}
			
	static double processXMLSessionEnd(DOMParser p, InputSource isrc) throws RDDLXMLException {
		try {
			p.parse(isrc);
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RDDLXMLException("sax exception");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			throw new RDDLXMLException("io exception");
		}
		Element e = p.getDocument().getDocumentElement();
		if ( e.getNodeName().equals(Server.SESSION_END) ) {
			ArrayList<String> text = Server.getTextValue(e, Server.TOTAL_REWARD);
			if ( text == null ) {
				return -1;
			}
			return Double.valueOf(text.get(0));
		}
		return -1;
	}
}

