package edu.umass.cs.flux;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.PrintWriter;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import jdsl.graph.ref.IncidenceListGraph;
import jdsl.graph.api.*;

/**
 * The event generator generates an event-driven server implemented in the C
 * programming language.
 * @author Brendan, Alex, Kevin
 **/
public class EventGenerator extends AbstractGeneratorC {
    private static String M_EVENT_CPP = "mEvent.cpp";
    private static String M_CONVERT_H = "mConvert.h";
    private static String M_CONVERT_CPP = "mConvert.cpp";

    private static int totalStages;

    // The stage number corresponding to a given name.
    private static Hashtable<String, Integer> stageNumber
    	= new Hashtable<String, Integer>();
    
    public static void printSessionObject
	(Collection<AtomicDeclaration> decs, VirtualDirectory out) 
    {
	out.getWriter(M_STRUCT_H).println("namespace flux {");
	out.getWriter(M_STRUCT_H).println("\tclass session {");
	out.getWriter(M_STRUCT_H).println("\tpublic:");
	out.getWriter(M_STRUCT_H).println("\t\tint session_id;");
	
	Hashtable<Lock,Boolean> defLocks = new Hashtable<Lock,Boolean>();
	for (AtomicDeclaration ad : decs) {
	    Vector<Lock> lks = ad.getLocks();
	    for (Lock l : lks) {
		if (l.isSession() && defLocks.get(l) == null) {
		    defLocks.put(l, Boolean.TRUE);
		    out.getWriter(M_STRUCT_H).print("\t\tint ");
		    out.getWriter(M_STRUCT_H).println(l.getName()+";");
		}
	    }
	}
	
	out.getWriter(M_STRUCT_H).println("\t};");
	out.getWriter(M_STRUCT_H).println("};");
    }

     public static void printLock
	(String sId, AtomicDeclaration ad, VirtualDirectory out)
    {
	if (ad == null)
	    return;
	Vector<Lock> lks = ad.getLocks();
	boolean first = true;
	for (Lock l : lks) {
	    String lock;
	    if (first) {
		out.getWriter(M_LOGIC_CPP).print("if (");
		first = false;
	    }
	    else {
		out.getWriter(M_LOGIC_CPP).print("|| ");
	    }
	    if (l.isProgram()) {
		lock = "locks["+locks.get(l)+"]";
	    }
	    else if (l.isSession()) {
		lock = sId+"->"+l.getName();
	    }
	    else {
		System.err.println("Error, "+l+" is not program or session");
		return;
	    }
	    
	    if (l.isReader()) {
		out.getWriter(M_LOGIC_CPP).println
		    ("("+lock+"!=2) // Lock "+l.getName());
	    }
	    else if (l.isWriter())
		out.getWriter(M_LOGIC_CPP).println
		    ("("+lock+"==0) // Lock "+l.getName());
	    else {
		System.err.println("Error, "+l+" isn't reader or writer?");
	    }
	}
	out.getWriter(M_LOGIC_CPP).println(") {");
	for (Lock l : lks) {
	    String lock;
	    if (l.isProgram()) {
		lock = "locks["+locks.get(l)+"]";
	    }
	    else if (l.isSession()) {
		lock = sId+"->"+l.getName();
	    }
	    else {
		System.err.println("Error, "+l+" is not program or session");
		return;
	    }
	    
	    if (l.isReader()) {
		out.getWriter(M_LOGIC_CPP).println
		    (lock+"= 1; // Read Lock "+l.getName());
	    }
	    else if (l.isWriter())
		out.getWriter(M_LOGIC_CPP).println
		    (lock+" = 2; // Write Lock "+l.getName());
	    else {
		System.err.println("Error, "+l+" isn't reader or writer?");
	    }
	}
	out.getWriter(M_LOGIC_CPP).println("} else {");
	out.getWriter(M_LOGIC_CPP).println("messageQ.fifo_push(ev);");
	out.getWriter(M_LOGIC_CPP).println("break;");
	out.getWriter(M_LOGIC_CPP).println("}");
    }

    public static void printUnlock
	(String sId, AtomicDeclaration ad, VirtualDirectory out) 
    {
	if (ad == null)
	    return;
	Vector<Lock> lks = ad.getLocks();
	for (Lock l : lks) {
	    String lock;
	    if (l.isProgram()) {
		lock = "locks["+locks.get(l)+"]";
	    }
	    else if (l.isSession()) {
		lock = sId+"->"+l.getName();
	    }
	    else {
		System.err.println("Error, "+l+" is not program or session");
		return;
	    }
	    
	    out.getWriter(M_LOGIC_CPP).println
		(lock+"= 0; // Lock "+l.getName());
	}
    }

    public static void printLocks
	(Collection<AtomicDeclaration> decs, VirtualDirectory out)
    {
	PrintWriter pw = out.getWriter(M_LOGIC_CPP);
        if (decs.size() < 1)
	    {
		pw.println("void initLocks() {}");
		return;
	    }
	
	pw.println("#include <vector>");
	pw.println("std::vector<int> locks;");
	pw.println("void initLocks() {");
	int id = 0;
	for (AtomicDeclaration ad : decs) {
	    Vector<Lock> lks = ad.getLocks();
	    for (Lock l : lks) {
		if (locks.get(l) == null) {
		    pw.println("locks.push_back(0);");
		    locks.put(l, new Integer(id++));
		}
	    }
	}
	pw.println("}");
    }

     /**
     * Prologue output
     * @param out The directory to hold files
     **/
    protected static void prologue(ProgramGraph g, VirtualDirectory out) {
	AbstractGeneratorC.prologue(g, out);
	printLocks(g.getProgram().getAtomicDeclarations(), out);
	printSessionObject(g.getProgram().getAtomicDeclarations(),out);
	out.getWriter(M_CONVERT_CPP).println("#include \""+M_STRUCT_H+"\"\n"+
					            "#include <stdio.h>\n"+
            "#include <stdlib.h>\n");
    
	out.getWriter(M_LOGIC_CPP).println("#include <unistd.h>\n"+
					   "#include <dlfcn.h>\n"+
					   "#include <list>\n" +
					   "#include <map>\n\n");

	out.getWriter(M_LOGIC_CPP).println("#include \""+M_IMPL_H+"\"\n"+
					   "#include \""+M_CONVERT_H+"\"\n"+
					   "#include \"runtimestack.h\"\n"
					   + "#include \"messagequeue.h\"\n"
					   + "#include \"queue.h\"\n"
					   + "#include \"event.h\"\n"
					   + "#include \"timer.h\"\n"
					   + "#include \"logger.h\"\n"
					   + "#include \"graphClient.h\"\n"
					   + "#include \"fred.h\"\n");
	
		out.getWriter(M_LOGIC_CPP).println();
	
	out.getWriter(M_LOGIC_CPP).println("#define STACK_SIZE                (512*1024)");
	out.getWriter(M_LOGIC_CPP).println("#define INITIAL_THREAD_POOL_SIZE  10");
	if (loggingOn)
	{
		out.getWriter(M_LOGIC_CPP).println("std::map <int, std::map<int,int> > edge_value; // format: from, to -> edge value");
	}
    }

    protected static void doInputConvert
	(TaskDeclaration task, Program p, VirtualDirectory out) 
    {
	out.getWriter(M_CONVERT_H).println
	    ("void *convert("+task.getName()+"_in *in, int next);");
	out.getWriter(M_CONVERT_CPP).println
	    ("void *convert("+task.getName()+"_in *in, int next) {");
	out.getWriter(M_CONVERT_CPP).println("\tvoid *res;");
	out.getWriter(M_CONVERT_CPP).println("\tswitch (next) {");
	Collection<TaskDeclaration> fns = p.getFunctions();
	for (TaskDeclaration t : fns) {
	    if (!t.equals(task)) {
		if (t.isInMatch(task)) {
		    out.getWriter(M_CONVERT_CPP).println
			("\tcase "+getStageNumber(t.getName())+":");
		    out.getWriter(M_CONVERT_CPP).println("\t{");
		    out.getWriter(M_CONVERT_CPP).println
			("\t\t" + t.getName()+"_in *result = new "+
			 t.getName()+"_in;");
		    out.getWriter(M_CONVERT_CPP).println
			("\t\tresult->_mSession = in->_mSession;");
		    Vector<Argument> args1 = task.getInputs();
		    Vector<Argument> args2 = t.getInputs();
		    for (int i=0;i<args1.size();i++) {
			  out.getWriter(M_CONVERT_CPP).println
			      ("\t\tresult->"+args2.get(i).getName()+" = "+
			       "in->"+args1.get(i).getName()+";");
		    }
		    out.getWriter(M_CONVERT_CPP).println("\t\tres = result;");
		    out.getWriter(M_CONVERT_CPP).println("\t\tbreak;");
		    out.getWriter(M_CONVERT_CPP).println("\t}");
		}
	    }
	}
	out.getWriter(M_CONVERT_CPP).println("\tdefault:");
	out.getWriter(M_CONVERT_CPP).println
	    ("\t\tprintf(\"Error, unknown conversion: "+task.getName()+
	     "-> %d\\n\",next);");
	out.getWriter(M_CONVERT_CPP).println("\t\tres = NULL;");
	out.getWriter(M_CONVERT_CPP).println("\t}");
	out.getWriter(M_CONVERT_CPP).println("\treturn res;");
	out.getWriter(M_CONVERT_CPP).println("}\n");
    }

    protected static void doOutputConvert
	(TaskDeclaration task, Program p, VirtualDirectory out) 
    {
	out.getWriter(M_CONVERT_H).println
	    ("void *convert("+task.getName()+"_out *in, int next);");
	out.getWriter(M_CONVERT_CPP).println
	    ("void *convert("+task.getName()+"_out *in, int next) {");
	out.getWriter(M_CONVERT_CPP).println("void *res;");
	out.getWriter(M_CONVERT_CPP).println("switch (next) {");
	Collection<TaskDeclaration> fns = p.getFunctions();
	for (TaskDeclaration t : fns) {
	    if (!t.equals(task)) {
		if (task.isOutInMatch(t)) {
		    out.getWriter(M_CONVERT_CPP).println
			("case "+getStageNumber(t.getName())+":");
		    out.getWriter(M_CONVERT_CPP).println("{");
		    out.getWriter(M_CONVERT_CPP).println
			(t.getName()+"_in *result = new "+
			 t.getName()+"_in;");
		    Vector<Argument> args1 = task.getOutputs();
		    Vector<Argument> args2 = t.getInputs();
		    for (int i=0;i<args1.size();i++) {
			  out.getWriter(M_CONVERT_CPP).println
			      ("result->"+args2.get(i).getName()+" = "+
			       "in->"+args1.get(i).getName()+";");
		    }
		    out.getWriter(M_CONVERT_CPP).println("res = result;");
		    out.getWriter(M_CONVERT_CPP).println("break;");
		    out.getWriter(M_CONVERT_CPP).println("}");
		}
	    }
	}
	out.getWriter(M_CONVERT_CPP).println("default:");
	out.getWriter(M_CONVERT_CPP).println
	    ("printf(\"Error, unknown conversion: "+task.getName()+
	     "-> %d\\n\",next);");
	out.getWriter(M_CONVERT_CPP).println("res = NULL;");
	out.getWriter(M_CONVERT_CPP).println("}");
	out.getWriter(M_CONVERT_CPP).println("return res;");
	out.getWriter(M_CONVERT_CPP).println("}");
    }

    
    public static void generate(String root, boolean logging, ProgramGraph g, Hashtable<String, Integer> h) 
        throws IOException
    {
    	loggingOn = logging;
        totalStages = h.size();
        stageNumber = h;
        generate(root, g);
    
    }
    
    /**
     * Generate a threaded program
     * @param root The root directory to output into
     * @param g The program graph
     **/
    public static void generate(String root, ProgramGraph g) 
	throws IOException
    {
    	M_LOGIC_CPP = M_EVENT_CPP;
    	programGraph = g;
	VirtualDirectory out = new VirtualDirectory(root);
	prologue(g, out);
	defined.clear();

	if (stageNumber == null)
	{
	    totalStages = 0;
	    stageNumber = new Hashtable<String, Integer>();
	    stageNumber.clear();
	}
	
	if (loggingOn)
	{
		out.getWriter(M_LOGIC_CPP).println("#define NUMBER_OF_PATHS " + programGraph.getIntNumPaths());
		out.getWriter(M_LOGIC_CPP).println("int path_profiler[NUMBER_OF_PATHS];");
	}
	// Print the structs and the sigs for all tasks
	Collection fns = g.getFunctions();
	Iterator it = fns.iterator();
	Program p = g.getProgram();
	
	if (loggingOn)
	{
		int size = fns.size() + g.getSources().size();
		Vector<ErrorHandler> errs = p.getErrorHandlers();
		out.getWriter(M_LOGIC_CPP).println("#define NUMBER_OF_ERRORS " + errs.size());
		out.getWriter(M_LOGIC_CPP).println("#define NUMBER_OF_NODES " + size);
		out.getWriter(M_LOGIC_CPP).println("Logger stats_logger[NUMBER_OF_NODES];");
		out.getWriter(M_LOGIC_CPP).println("int num_errors[NUMBER_OF_ERRORS];");
		out.getWriter(M_LOGIC_CPP).println("GraphClient *graphClient;");
	}
	out.getWriter(M_LOGIC_CPP).println("bool running = true;");
	out.getWriter(M_LOGIC_CPP).println("flux::queue<flux::event *> messageQ;");
	
	out.getWriter(M_LOGIC_CPP).println("typedef struct {");
	out.getWriter(M_LOGIC_CPP).println("\tint threads_waiting_to_run;");
	out.getWriter(M_LOGIC_CPP).println("\tint threads_waiting_in_pool;");
	out.getWriter(M_LOGIC_CPP).println("\tpthread_mutex_t *mutex_event_mgr;");
	out.getWriter(M_LOGIC_CPP).println("\tpthread_cond_t *cond_waiting_to_run;");
	out.getWriter(M_LOGIC_CPP).println("\tpthread_cond_t *cond_waiting_in_pool;");
	out.getWriter(M_LOGIC_CPP).println("\tbool *running;");
	out.getWriter(M_LOGIC_CPP).println("\tvoid * (*event_mgr_fn)(void *);");
	out.getWriter(M_LOGIC_CPP).println("} eventmgrinfo_t;\n");

	out.getWriter(M_LOGIC_CPP).println("typedef struct {");
	out.getWriter(M_LOGIC_CPP).println("\tbool *running;");
	out.getWriter(M_LOGIC_CPP).println("\tbool bootstrap;");
	out.getWriter(M_LOGIC_CPP).println("} eventmgrargs_t;\n");

	out.getWriter(M_LOGIC_CPP).println("static eventmgrinfo_t mgrinfo;");
	out.getWriter(M_LOGIC_CPP).println("typedef void (*initShimFnType) (eventmgrinfo_t *);\n");
	    
  	Collection<Source> sources = g.getSources();
	
	while (it.hasNext()) 
	{
	    TaskDeclaration td = (TaskDeclaration)it.next();
	    printStructs(td, out.getWriter(M_STRUCT_H));
	    printSignature(td, out);
	    
	    if (p.getFlow(td.getName()) == null) 
	    {
		if (td.getOutputs().size() > 0 && td.getInputs().size() > 0) {
		    out.getWriter(M_LOGIC_CPP).println
			("int "+td.getName()+"_exec("+td.getName()+"_in *in,"
			+td.getName()+"_out *out, flux::event *ev) {");
		    if (loggingOn)
		    {
		    	out.getWriter(M_LOGIC_CPP).println("int int_stats_logger = stats_logger[ev->type].start();");
		    }
		    out.getWriter(M_LOGIC_CPP).println("int ret = " + td.getName() + "(in, out);");
		    if (loggingOn)
		    {
		    	out.getWriter(M_LOGIC_CPP).println("stats_logger[ev->type].stop(int_stats_logger);");
		    }
		    out.getWriter(M_LOGIC_CPP).println("return ret;");
		    out.getWriter(M_LOGIC_CPP).println("}");
		} else if (td.getOutputs().size() > 0 && td.getInputs().size() == 0) {
		    out.getWriter(M_LOGIC_CPP).println
			("int "+td.getName()+"_exec("+td.getName()+"_out *out, flux::event *ev) {");
		    if (loggingOn)
		    {
		    	out.getWriter(M_LOGIC_CPP).println("int int_stats_logger = stats_logger[ev->type].start();");
		    }
		    out.getWriter(M_LOGIC_CPP).println("int ret = " + td.getName() + "(out);");
		    if (loggingOn)
		    {
		    	out.getWriter(M_LOGIC_CPP).println("stats_logger[ev->type].stop(int_stats_logger);");
		    }
		    out.getWriter(M_LOGIC_CPP).println("return ret;");
		    out.getWriter(M_LOGIC_CPP).println("}");
		} else if (td.getOutputs().size() == 0 && td.getInputs().size() > 0) {
		    out.getWriter(M_LOGIC_CPP).println
			("int "+td.getName()+"_exec("+td.getName()+"_in *in, flux::event *ev) {");
		    if (loggingOn)
		    {
		    	out.getWriter(M_LOGIC_CPP).println("int int_stats_logger = stats_logger[ev->type].start();");
		    }
		    out.getWriter(M_LOGIC_CPP).println("int ret = " + td.getName() + "(in);");
		    if (loggingOn)
		    {
		    	out.getWriter(M_LOGIC_CPP).println("stats_logger[ev->type].stop(int_stats_logger);");
		    }
		    out.getWriter(M_LOGIC_CPP).println("return ret;");
		    out.getWriter(M_LOGIC_CPP).println("}");
		}
	    }
	}

	Vector exps = p.getExpressions();
	it = exps.iterator();
	while (it.hasNext()) 
	{
	    FlowStatement fs =  (FlowStatement)it.next();
	    generateStatement(fs,p,out);
	}
	
	///////////////////////////////////////////////////////////////////////////////////
	// event_handler!
	///////////////////////////////////////////////////////////////////////////////////
	
	out.getWriter(M_LOGIC_CPP).println("void *event_handler(void *arg) {");
	out.getWriter(M_LOGIC_CPP).println("\tflux::event *ev = (flux::event *) arg;");
	out.getWriter(M_LOGIC_CPP).println("\tswitch (ev->type) {");

	it = fns.iterator();
	while (it.hasNext()) 
	{
		TaskDeclaration td = (TaskDeclaration)it.next();
		int stg = getStageNumber(td.getName());
		out.getWriter(M_LOGIC_CPP).println("\tcase "+stg+": //" + td.getName());
		out.getWriter(M_LOGIC_CPP).println("\t{");
		if (td.getInputs().size() > 0)
		out.getWriter(M_LOGIC_CPP).println
			("\t\t" + td.getName()+"_in *ptr = ("+td.getName()+"_in *)ev->in;");
		if (td.getOutputs().size() > 0)
		out.getWriter(M_LOGIC_CPP).println(td.getName()+"_out "+ td.getName()+"_var_out;");
	
		// call the function
		printLock("ptr->_mSession",
				p.getAtomicDeclaration(td.getName()), out);
		out.getWriter(M_LOGIC_CPP).println("\t\tint err = "+ td.getName()+"_exec("
			+ (td.getInputs().size() > 0 ? "ptr, " : "") 
			+ (td.getOutputs().size() > 0 ? "&"+td.getName() + "_var_out, " : "")
			+ "ev);");
		printUnlock("ptr->_mSession",
			p.getAtomicDeclaration(td.getName()), out);
		// if error... push correct event on to stack
		out.getWriter(M_LOGIC_CPP).println("\t\tif (err) {");
		
		int pathWeight = printErrorHandler(td,"ptr",p,out);
		if (loggingOn)
		{
			out.getWriter(M_LOGIC_CPP).print("\t\t\tev->pathSum += " + pathWeight + ";" );
			out.getWriter(M_LOGIC_CPP).println("// from: " + td.getName() + " to: ERRORHANDLER"); 
		}
		if (td.getInputs().size() > 0)
			out.getWriter(M_LOGIC_CPP).println("\t\t\tdelete ptr;");
		out.getWriter(M_LOGIC_CPP).println("\t\t\tdelete ev;");
		if(td.getIsSrc())
		{
			out.getWriter(M_LOGIC_CPP).println("\t\t\tflux::event *evnt = new flux::event();");
			int srcstg = getStageNumber(td.getSrcName());
			out.getWriter(M_LOGIC_CPP).println("\t\t\tevnt->type = " + srcstg + "; //" + td.getSrcName());
			out.getWriter(M_LOGIC_CPP).println("\t\t\tmessageQ.fifo_push(evnt);");
		}
		out.getWriter(M_LOGIC_CPP).println("\t\t\tbreak;");
		out.getWriter(M_LOGIC_CPP).println("\t\t}\n");
	
	    if (td.getIsSrcEnd()) {
	    	out.getWriter(M_LOGIC_CPP).println("\t\t\tflux::event *evnt = new flux::event();");
			int srcstg = getStageNumber(td.getSrcName());
			out.getWriter(M_LOGIC_CPP).println("\t\t\tevnt->type = " + srcstg + "; //" + td.getSrcName());
			out.getWriter(M_LOGIC_CPP).println("\t\t\tmessageQ.fifo_push(evnt);");
		
	    	out.getWriter(M_LOGIC_CPP).println("\t\t\tevnt = new flux::event();");
			srcstg = getStageNumber(p.getSource(td.getSrcName()).getTarget());
			out.getWriter(M_LOGIC_CPP).println("\t\t\tevnt->type = " + srcstg + "; //" + p.getSource(td.getSrcName()));
			if (td.getOutputs().size() > 0) {
				out.getWriter(M_LOGIC_CPP).println("\t\t\tevnt->in = convert(&"+td.getName()+"_var_out, evnt->type);");
				doOutputConvert(td, p, out);
			} else {
				out.getWriter(M_LOGIC_CPP).println ("\t\tevnt->in = NULL;"); 
			}
			out.getWriter(M_LOGIC_CPP).println("\t\t\tmessageQ.fifo_push(evnt);");		
		} else if (p.getFlow(td.getName())!=null) {
			out.getWriter(M_LOGIC_CPP).println("\t\tint current_event_type = ev->type;");
			out.getWriter(M_LOGIC_CPP).println("\t\tint new_event_type = ev->pop();");
			if (loggingOn)
			{
				out.getWriter(M_LOGIC_CPP).println("\t\tev->pathSum += edge_value[current_event_type][new_event_type];");
			}
			out.getWriter(M_LOGIC_CPP).println("\t\tev->type = new_event_type;");
			if (td.getInputs().size() > 0) {
				out.getWriter(M_LOGIC_CPP).println("\t\tev->in = convert(ptr, ev->type);");
				doInputConvert(td, p, out);
			} else {
				out.getWriter(M_LOGIC_CPP).println ("\t\tev->in = NULL;"); 
			}
			out.getWriter(M_LOGIC_CPP).println("\t\tmessageQ.fifo_push(ev);");
	    } else if (td.getOutputs().size() > 0) {
			out.getWriter(M_LOGIC_CPP).println("\t\tint current_event_type = ev->type;");
			out.getWriter(M_LOGIC_CPP).println("\t\tint new_event_type = ev->pop();");
			if (loggingOn)
			{
			out.getWriter(M_LOGIC_CPP).println("\t\tev->pathSum += edge_value[current_event_type][new_event_type];");
			}
			out.getWriter(M_LOGIC_CPP).println("\t\tev->type = new_event_type;");
			if (td.getOutputs().size() > 0) {
				out.getWriter(M_LOGIC_CPP).println ("\t\tev->in = convert(&"+td.getName()+"_var_out, ev->type);");
				doOutputConvert(td, p, out);
			} else {
				out.getWriter(M_LOGIC_CPP).println ("\t\tev->in = NULL;"); 
			}
			out.getWriter(M_LOGIC_CPP).println("\t\tmessageQ.fifo_push(ev);");
		} else { // it's going towards exit! 
			Vertex vertex = programGraph.getVertex(td.getName());
			int edge_value = programGraph.getEdgeWeight(vertex, programGraph.getVertexExit());
			if (loggingOn)
			{
				out.getWriter(M_LOGIC_CPP).println("\t\tev->pathSum += " + edge_value + ";");
			}
			out.getWriter(M_LOGIC_CPP).println("\t\tdelete ev;");
		}

	    if (td.getInputs().size() > 0)
		out.getWriter(M_LOGIC_CPP).println("\t\tdelete(ptr);");

	    out.getWriter(M_LOGIC_CPP).println("\t\tbreak;");
	    out.getWriter(M_LOGIC_CPP).println("\t}");
	}
		
	out.getWriter(M_LOGIC_CPP).println("\tdefault:");
	out.getWriter(M_LOGIC_CPP).println
		    ("\t\tfprintf(stderr, \"Unknown event: %d\\n\", ev->type);");
	out.getWriter(M_LOGIC_CPP).println("\t}");
	out.getWriter(M_LOGIC_CPP).println("}\n");

	out.getWriter(M_LOGIC_CPP).println("void *event_manager(void *arg) {");
	out.getWriter(M_LOGIC_CPP).println("\teventmgrargs_t *mgrargs = (eventmgrargs_t *) arg;");
	out.getWriter(M_LOGIC_CPP).println("\tflux::event *ev;\n");
	out.getWriter(M_LOGIC_CPP).println("\tpthread_mutex_lock(mgrinfo.mutex_event_mgr);\n");
	out.getWriter(M_LOGIC_CPP).println("\tif (!mgrargs->bootstrap) {");
	out.getWriter(M_LOGIC_CPP).println("\t\tmgrinfo.threads_waiting_in_pool++;");
	out.getWriter(M_LOGIC_CPP).println("\t\tpthread_cond_wait(mgrinfo.cond_waiting_in_pool,");
	out.getWriter(M_LOGIC_CPP).println("\t\t\tmgrinfo.mutex_event_mgr);");
	out.getWriter(M_LOGIC_CPP).println("\t\tmgrinfo.threads_waiting_in_pool--;");
	out.getWriter(M_LOGIC_CPP).println("\t}\n");
	out.getWriter(M_LOGIC_CPP).println("\twhile (*(mgrargs->running)) {");
	out.getWriter(M_LOGIC_CPP).println("\t\tpthread_mutex_unlock(mgrinfo.mutex_event_mgr);");
	out.getWriter(M_LOGIC_CPP).println("\t\tpthread_mutex_lock(mgrinfo.mutex_event_mgr);\n");
	out.getWriter(M_LOGIC_CPP).println("\t\tif (mgrinfo.threads_waiting_to_run > 0) {");
	out.getWriter(M_LOGIC_CPP).println("\t\t\tpthread_cond_signal(mgrinfo.cond_waiting_to_run);");
	out.getWriter(M_LOGIC_CPP).println("\t\t\tmgrinfo.threads_waiting_in_pool++;");
	out.getWriter(M_LOGIC_CPP).println("\t\t\tpthread_cond_wait(mgrinfo.cond_waiting_in_pool,");
	out.getWriter(M_LOGIC_CPP).println("\t\t\t\tmgrinfo.mutex_event_mgr);");
	out.getWriter(M_LOGIC_CPP).println("\t\t\tmgrinfo.threads_waiting_in_pool--;");
	out.getWriter(M_LOGIC_CPP).println("\t\t}\n");
	out.getWriter(M_LOGIC_CPP).println("\t\tif(messageQ.fifo_trypop(ev))");
	out.getWriter(M_LOGIC_CPP).println("\t\t\tevent_handler(ev);");
	out.getWriter(M_LOGIC_CPP).println("\t}\n");
	out.getWriter(M_LOGIC_CPP).println("\tdelete mgrargs;");
	out.getWriter(M_LOGIC_CPP).println("}\n");
	
		// ****************************************************************************************
		// SETUP HASHTABLE
		// ****************************************************************************************
		if (loggingOn)
		{
			printPathsHashTable(out);
		}
		// ****************************************************************************************
		// Logger_Updater
		// ****************************************************************************************
		if (loggingOn)
		{
			out.getWriter(M_LOGIC_CPP).println("void* Logger_Updater(void *)"); 
			out.getWriter(M_LOGIC_CPP).println("{");
			out.getWriter(M_LOGIC_CPP).println("while (running)"); 
			out.getWriter(M_LOGIC_CPP).println("{");
			out.getWriter(M_LOGIC_CPP).println("std::string s = \"\";");
			out.getWriter(M_LOGIC_CPP).println("sleep(5);");
			out.getWriter(M_LOGIC_CPP).println("char buf[256];");
			out.getWriter(M_LOGIC_CPP).println("for(int i=0; i< NUMBER_OF_NODES; i++)"); 
			out.getWriter(M_LOGIC_CPP).println("{");
			out.getWriter(M_LOGIC_CPP).println("sprintf(buf, \"%d\", i);");
			out.getWriter(M_LOGIC_CPP).println("s += buf;");
			out.getWriter(M_LOGIC_CPP).println("s += + \" \" + stats_logger[i].getValues() + \";\";");
			out.getWriter(M_LOGIC_CPP).println("}");
			 
			out.getWriter(M_LOGIC_CPP).println("for (int i = 0; i < NUMBER_OF_ERRORS; i++)");
			out.getWriter(M_LOGIC_CPP).println("{");
			out.getWriter(M_LOGIC_CPP).println("sprintf(buf, \"e: %d %d;\", NUMBER_OF_NODES +i, num_errors[i]);");
			out.getWriter(M_LOGIC_CPP).println("s += buf;");
			out.getWriter(M_LOGIC_CPP).println("}");
			out.getWriter(M_LOGIC_CPP).println("graphClient->sendMessage(s);");
			out.getWriter(M_LOGIC_CPP).println("}");
			out.getWriter(M_LOGIC_CPP).println("}");
		}
		// ****************************************************************************************
		// Main
		// ****************************************************************************************

		out.getWriter(M_LOGIC_CPP).println("int main(int argc, char **argv) {");
		out.getWriter(M_LOGIC_CPP).println("\tinit(argc, argv);\n");
		out.getWriter(M_LOGIC_CPP).println("\tinitLocks();\n");

		if (loggingOn)
		{
			out.getWriter(M_LOGIC_CPP).println("\tsetup_hashtable();");
			out.getWriter(M_LOGIC_CPP).println("\tgraphClient = new GraphClient(12345);");
	    	out.getWriter(M_LOGIC_CPP).println("\tfor (int i = 0; i < NUMBER_OF_ERRORS; i++)");
	    	out.getWriter(M_LOGIC_CPP).println("\t\tnum_errors[i] = 0;");
		}
		out.getWriter(M_LOGIC_CPP).println("\tflux::event *ev;");
		sources = g.getSources();
		for (Source s : sources) {
			int stg = getStageNumber(s.getSourceFunction());
			out.getWriter(M_LOGIC_CPP).println("\tev = new flux::event;");
			out.getWriter(M_LOGIC_CPP).println("\tev->type = " + stg + ";");
			out.getWriter(M_LOGIC_CPP).println("\tmessageQ.fifo_push(ev);");
		}

		out.getWriter(M_LOGIC_CPP).println("\tmgrinfo.threads_waiting_to_run = 0;");
		out.getWriter(M_LOGIC_CPP).println("\tmgrinfo.threads_waiting_in_pool = 0;");
		out.getWriter(M_LOGIC_CPP).println("\tmgrinfo.mutex_event_mgr = new pthread_mutex_t;");
		out.getWriter(M_LOGIC_CPP).println("\tmgrinfo.cond_waiting_to_run = new pthread_cond_t;");
		out.getWriter(M_LOGIC_CPP).println("\tmgrinfo.cond_waiting_in_pool = new pthread_cond_t;");
		out.getWriter(M_LOGIC_CPP).println("\tmgrinfo.running = &running;");
		out.getWriter(M_LOGIC_CPP).println("\tmgrinfo.event_mgr_fn = &event_manager;\n");
		out.getWriter(M_LOGIC_CPP).println("\tpthread_mutex_init(mgrinfo.mutex_event_mgr, NULL);");
		out.getWriter(M_LOGIC_CPP).println("\tpthread_cond_init(mgrinfo.cond_waiting_to_run, NULL);");
		out.getWriter(M_LOGIC_CPP).println("\tpthread_cond_init(mgrinfo.cond_waiting_in_pool, NULL);\n");
		out.getWriter(M_LOGIC_CPP).println("\tinitShimFnType f = (initShimFnType)dlsym (RTLD_NEXT, \"initShim\");");
		//out.getWriter(M_LOGIC_CPP).println("\tif (f == NULL) exit(0);");
		out.getWriter(M_LOGIC_CPP).println("\tif (f == NULL){");
		out.getWriter(M_LOGIC_CPP).println("\t\t printf(\"ERROR no SHIM file found... exiting\\n\");");
		out.getWriter(M_LOGIC_CPP).println("\t\t exit(0);");
		out.getWriter(M_LOGIC_CPP).println("\t}");
		out.getWriter(M_LOGIC_CPP).println("\t((f)(&mgrinfo));\n");
		out.getWriter(M_LOGIC_CPP).println("\teventmgrargs_t *mgrargs = new eventmgrargs_t;");
		out.getWriter(M_LOGIC_CPP).println("\tmgrargs->running = &running;");
		out.getWriter(M_LOGIC_CPP).println("\tmgrargs->bootstrap = true;\n");
		out.getWriter(M_LOGIC_CPP).println("\tevent_manager(mgrargs);");
		out.getWriter(M_LOGIC_CPP).println("}");
	
		epilogue(out);
		out.flushAndClose();
    }

    public static void printSourceCall (Source s, Program p, VirtualDirectory out)
    {
    	out.getWriter(M_LOGIC_CPP).println(s.getSourceFunction()+"_src_exec();");
    }

    public static void generateStatement
	(FlowStatement fs, Program p, VirtualDirectory out) 
    {
	TaskDeclaration task = p.getTask(fs.getAssignee());
	out.getWriter(M_LOGIC_CPP).println
	    ("inline int "+task.getName()+"_exec(" 
		+ (task.getInputs().size() > 0 ? task.getName() + "_in *in," : "")
		+ (task.getOutputs().size() > 0 ? task.getName() + "_out *out," : "")
		+ " flux::event *ev) {");
	Source s = p.getSessionStart(fs);
	if (s != null && s.getSessionFunction() != null) {
	    out.getWriter(M_LOGIC_CPP).println 
		("int id = "+ s.getSessionFunction()+"(in);");
	     out.getWriter(M_LOGIC_CPP).println
		 ("in->_mSession=session_locks.getSession(id);");
	}
	if (fs instanceof SimpleFlowStatement)
	{
		if (loggingOn)
		{
			out.getWriter(M_LOGIC_CPP).println("int int_stats_logger = stats_logger[ev->type].start();");
		}
		generateSimpleStatement((SimpleFlowStatement)fs, p, out);
		if (loggingOn)
		{
			out.getWriter(M_LOGIC_CPP).println("stats_logger[ev->type].stop(int_stats_logger);");
		}
	}
	else
	{	
		if (loggingOn)
		{
	    out.getWriter(M_LOGIC_CPP).println("int int_stats_logger = stats_logger[ev->type].start();");	
		}
	    out.getWriter(M_LOGIC_CPP).println(task.getName() + "_in * " + task.getName() 
		+ "_var_in = (" + task.getName() + "_in *) ev->in;"); 
	    generateTypedStatement((TypedFlowStatement)fs, p, out);
		if (loggingOn)
		{
			out.getWriter(M_LOGIC_CPP).println("stats_logger[ev->type].stop(int_stats_logger);");
		}
	}
	out.getWriter(M_LOGIC_CPP).println("return 0;");
	out.getWriter(M_LOGIC_CPP).println("}");
    }

    protected static int getStageNumber(String name) {
		Integer stg = stageNumber.get(name);
		if (stg == null) {
		    stg = new Integer(totalStages);
		    totalStages++;
		    stageNumber.put(name, stg);
		}
		return stg.intValue();
    }

    public static void generateSimpleStatement
	(SimpleFlowStatement sfs, Program p, VirtualDirectory out) 
    {
	Vector<String> args = sfs.getArguments();
        
        for (int i=args.size()-1;i>=0;i--) {
	    int stg = getStageNumber(args.get(i));
	    out.getWriter(M_LOGIC_CPP).println
		("\t\tev->push("+stg+"); // "+args.get(i));
	}
    }

    /**
     * Generate a set of type tests as an <tt>if ...</tt> statement
     * @param types A vector of Strings naming the types
     * @param p The program defining the types
     * @param last The previous task declaration (containing the tested var)
     * @param select Test input if true, otherwise test output
     * @param out Where to write the test
     **/
    public static void generateTypeTests
	(Vector<String> types, Program p, 
	 TaskDeclaration last, Boolean select, VirtualDirectory out) 
    {
    	
        Vector<Argument> args;
	String prefix;
	if (select.booleanValue()) {
	    args = last.getInputs();
	    prefix = last.getName()+"_var_in->";
	}
	else {
	    args = last.getOutputs();
	    prefix = last.getName()+"_var_out->";
	}
	Iterator it = args.iterator();
        out.getWriter(M_LOGIC_CPP).print("if (");
        generateTypeTest(types.get(0), prefix,
			 (Argument)it.next(), p, out);
        for (int i=1;i<types.size();i++) {
            out.getWriter(M_LOGIC_CPP).print(" && "); 
            generateTypeTest(types.get(i), prefix,
			     (Argument)it.next(),
			     p, out);
        }
        // TODO: i think i have to 
        //out.getWriter(M_LOGIC_CPP).println("stats_logger["+getStageNumber(fn.getName())+"].stop(int_log_" + fn.getName() + getStageNumber(fn.getName()) +");");
        out.getWriter(M_LOGIC_CPP).println(")");
        
    }

    public static void generateTypedStatement
	(TypedFlowStatement tfs, Program p, VirtualDirectory out)
    {
	TaskDeclaration task = p.getTask(tfs.getAssignee());
	
        
        Vector<FlowStatement> stmts = tfs.getFlowStatements();
        Iterator<FlowStatement> it = stmts.iterator();
        while (it.hasNext()) {
            SimpleFlowStatement sfs = (SimpleFlowStatement)it.next();
            generateTypeTests(sfs.getTypes(), p, task, Boolean.TRUE, out);
            out.getWriter(M_LOGIC_CPP).println("{");
            generateSimpleStatement(sfs, p, out);
            out.getWriter(M_LOGIC_CPP).println("}");
        }
    }

    /**
     * Main routine
     **/
    public static void main(String[] args) 
	throws Exception
    {
	parser p = new parser(new Yylex(new FileInputStream(args[0])));
	Program pm = (Program)p.parse().value;
	if (pm.verifyExpressions()) {
	    pm.unifyExpressions();
	    ProgramGraph pg = new ProgramGraph(pm);
	    generate(args[1], pg);
	}
    }
}
	
