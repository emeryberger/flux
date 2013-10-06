package edu.umass.cs.flux;

import java.io.PrintWriter;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.Stack;

import jdsl.graph.ref.IncidenceListGraph;
import jdsl.graph.api.*;

/**
 * The base class for code generators that generate code in the C programming
 * language.  Contains numerous utilities shared by concrete implementations
 * @author Brendan Burns
 * @see EventGenerator
 * @see ThreadGenerator
 **/
public class AbstractGeneratorC {
    protected static final String M_STRUCT_H = "mStructs.h";
    protected static final String M_IMPL_H = "mImpl.h";
    protected static final String M_LOCALINCLUDE_H = "localStructs.h";
    protected static final String M_IMPL_CPP = "mImpl.cpp";
    protected static String M_LOGIC_CPP = null;

    protected static Program program;
    protected static ProgramGraph programGraph;
    
    // locks for all atomic declarations
    protected static Hashtable<Lock, Integer> locks = 
	new Hashtable<Lock, Integer>();

    // Indicates whether a symbol is defined or not.
    protected static Hashtable<String, Boolean> defined 
    	= new Hashtable<String, Boolean>();

    // Stages for use in path profiling.
    protected static Hashtable<String, Integer> stageNumber
	= new Hashtable<String, Integer>();
    
    protected static int totalStages = 0;
    
    protected static String currParent = "";
    
    protected static boolean loggingOn  = false;
    

    /**
     * Print the structs for input/output for a task
     * @param td The task
     * @param out Where to write
     **/
    public static void printStructs(TaskDeclaration td, PrintWriter out) {
	Vector<Argument> ins = td.getInputs();
	
	// if the ins are greater than zero, it can't be a source node?
	//if (ins.size() > 0) {
	out.println("struct "+td.getName()+"_in");
	out.println("{");
	out.println("\tflux::session *_mSession;");
	for (Argument a : ins) 
	    out.println("\t"+a.getType()+" "+a.getName()+";");
	out.println("};");
	out.println();
	//}
	
	// if the outs are greater than zero it can't be a final "sync" node?
	Vector<Argument> outs = td.getOutputs();
	//if (outs.size() > 0) {
	out.println("struct "+td.getName()+"_out");
	out.println("{");
	out.println("\tflux::session *_mSession;");
	for (Argument a : outs) 
	    out.println("\t"+a.getType()+" "+a.getName()+";");
	out.println("};");
	out.println();
	
    }

    /**
     * Print a function signature and stub for a task
     * @param td The task
     * @param virt The virtual directory to contain the .h and .cpp files
     **/
    public static void printSignature(TaskDeclaration td,
				      VirtualDirectory virt) 
    {
	Vector<Argument> ins = td.getInputs();
	Vector<Argument> outs = td.getOutputs();
	
	virt.getWriter(M_IMPL_H).print("int "+td.getName()+" (");
	virt.getWriter(M_IMPL_CPP).print("int "+td.getName()+" (");
	if (ins.size() > 0) {
	    virt.getWriter(M_IMPL_H).print(td.getName()+"_in *in");
	    virt.getWriter(M_IMPL_CPP).print(td.getName()+"_in *in");
	    if (outs.size() > 0) {
		virt.getWriter(M_IMPL_H).print(", ");
		virt.getWriter(M_IMPL_CPP).print(", ");
	    }
	}
	if (outs.size() > 0) {
	    virt.getWriter(M_IMPL_H).print(td.getName()+"_out *out");
	    virt.getWriter(M_IMPL_CPP).print(td.getName()+"_out *out");
	}
	virt.getWriter(M_IMPL_H).println(");");
	virt.getWriter(M_IMPL_CPP).println(") {\n\n}\n");
    }
    
    public static void printSessionSignature(Source s, VirtualDirectory out)
    {
	if (s.getSessionFunction() != null) {
	    out.getWriter(M_IMPL_H).println
		("int "+s.getSessionFunction()+"("+s.getTarget()+"_in *in);");
	}
    }

    /**
     * Print the signature and stub for a source
     * @param s The source
     * @param target The task target of the source
     * @param virt The directory to contain the .h and .cpp files
     **/
    /*
      public static void printSignature(Source s, TaskDeclaration target, 
				      VirtualDirectory virt) {
	Vector<Argument> ins = target.getInputs();
	String fn = s.getSourceFunction();
	virt.getWriter(M_IMPL_H).print
	    ("std::vector<"+target.getName()+"_in *> *"+fn+"()");
	virt.getWriter(M_IMPL_CPP).print
	    ("std::vector<"+target.getName()+"_in *> *"+fn+"() {");
	if (ins.size() == 0) 
	    {
		System.err.println
		    ("Error! Source directed to a function with no input...");
		System.exit(1);
	    }
	virt.getWriter(M_IMPL_H).println(";");
	virt.getWriter(M_IMPL_H).println();
	
	virt.getWriter(M_IMPL_CPP).println();
	virt.getWriter(M_IMPL_CPP).println("}");
	virt.getWriter(M_IMPL_CPP).println();
    }
    */

    /**
     * Prologue output
     * @param out The directory to hold files
     **/
    protected static void prologue(ProgramGraph g, VirtualDirectory out) {
	out.getWriter(M_STRUCT_H).println("#ifndef __M_STRUCTS__");
	out.getWriter(M_STRUCT_H).println("#define __M_STRUCTS__");
	out.getWriter(M_STRUCT_H).println("#include \""+M_LOCALINCLUDE_H+"\"");

	out.getWriter(M_IMPL_H).println("#ifndef __M_IMPL__");
	out.getWriter(M_IMPL_H).println("#define __M_IMPL__");
	out.getWriter(M_IMPL_H).println("#include \""+M_STRUCT_H+"\"");
	out.getWriter(M_IMPL_H).println("#include <vector>");

	out.getWriter(M_IMPL_H).println("void init(int argc, char **argv);");
	
	out.getWriter(M_IMPL_CPP).println("#include \""+M_IMPL_H+"\"");
	out.getWriter(M_IMPL_CPP).println("void init(int argc, char **argv) {");
	out.getWriter(M_IMPL_CPP).println("\n}");
	
	out.getWriter(M_IMPL_H).println("#include \"session_map.h\"");
	out.getWriter(M_IMPL_H).println("static flux::SessionMap session_locks;");
    }
    /**
     * Epilogue output
     * @param out The directory to hold files 
     **/
    protected static void epilogue(VirtualDirectory out) {
	out.getWriter(M_IMPL_H).println("#endif //__M_IMPL__");
	out.getWriter(M_STRUCT_H).println("#endif //__M_STRUCT__");
    }
    
    protected static int printErrorHandler(TaskDeclaration current,
					    Program p, VirtualDirectory out)
    {
    	return printErrorHandler(current, "&"+current.getName()+"_var_in", p, out);
    }

    /**
     * Print the matching error handlers
     * @param current The current task
     * @param p The program
     * @param out The directory to write to
     **/
    protected static int printErrorHandler(TaskDeclaration current,
					    String var,
					    Program p, VirtualDirectory out) 
    {
    	int matches = 0;
    	int pathWeight = 0;
    	Vector<ErrorHandler> errs = p.getErrorHandlers();
    	for (ErrorHandler err : errs) 
    	{
		    if (err.matches(current.getName())) 
		    {
		    	//
		    	
		    	pathWeight = programGraph.getEdgeWeight(current.getName(), err.getFunction());
		    	out.getWriter(M_LOGIC_CPP).println ("\t\t\t" + err.getFunction()+"("+var+", err);");
		    	
		    	if (defined.get(current.getName())==null) 
		    	{
                            out.getWriter(M_IMPL_H).println("void "+err.getFunction() + "("+current.getName()+"_in *in, int err);");
                            out.getWriter(M_IMPL_CPP).println("void "+err.getFunction()+"("+current.getName()+"_in *in, int err) {");
				    out.getWriter(M_IMPL_CPP).println("\n}");
				    defined.put(current.getName(), Boolean.TRUE);
		    	}
		    	matches++;
		    }
    	}  
    	
    	return pathWeight;
    }
    
    /**
     * Generate a test for a type
     * @param type The name of the type
     * @param prefix The prefix for the argument
     * @param arg The argument to test
     * @param p The program which defines the type
     * @param out Where to write the test to
     **/
    public static void generateTypeTest(String type,String prefix,Argument arg, 
					Program p, VirtualDirectory out)
    {
        if (type.equals("*")) {
            out.getWriter(M_LOGIC_CPP).print(" (1) ");
        }
        else {
            TypeDeclaration td = p.getType(type);
            out.getWriter(M_LOGIC_CPP).print
		(" ("+td.getFunction()+"("+prefix+arg.getName()+")) ");
	    out.getWriter(M_IMPL_H).println
		("bool "+td.getFunction()+
		 "("+arg.getType()+" "+arg.getName()+");");

	    out.getWriter(M_IMPL_CPP).println
		("bool "+td.getFunction()+
		 "("+arg.getType()+" "+arg.getName()+") {");
	    out.getWriter(M_IMPL_CPP).println("\n}");
	}
    }
    
    protected static void printPathsHashTable (VirtualDirectory out)
    {
		out.getWriter(M_LOGIC_CPP).println("void setup_hashtable() {");
		//Collection<Source> sources = programGraph.getSources();
		Collection fns = programGraph.getFunctions();
		Iterator it = fns.iterator();
		while (it.hasNext())
		{
			TaskDeclaration td = (TaskDeclaration)it.next();
			Vertex vertex = programGraph.getVertex(td.getName());
			String sourceString = td.getName();
			IncidenceListGraph ilg = programGraph.getGraph();
			EdgeIterator edgeIterator = ilg.incidentEdges(vertex, EdgeDirection.OUT);

			if (td.getErrorHandler() != null)
			{
				out.getWriter(M_LOGIC_CPP).print("\tedge_value[" + programGraph.getStageNumber(sourceString));
				out.getWriter(M_LOGIC_CPP).print("][" + programGraph.getStageNumber(td.getErrorHandler()));
				out.getWriter(M_LOGIC_CPP).print("] = 0;");
				out.getWriter(M_LOGIC_CPP).println("  // from: " + sourceString + "  to: " + td.getErrorHandler());
			}

			
			while(edgeIterator.hasNext())
			{
				Edge edge = edgeIterator.nextEdge();
				Vertex destination = ilg.destination(edge);
				GraphNode graphNode = (GraphNode)destination.element();
				if (graphNode.getNodeType() == GraphNode.DEFAULT)
				{
					TaskDeclaration destTD = (TaskDeclaration)graphNode.getElement();
					String destinationString = destTD.getName();
					out.getWriter(M_LOGIC_CPP).print("\tedge_value[" + programGraph.getStageNumber(sourceString)
							+ "][" + programGraph.getStageNumber(destinationString) +"] = " 
							+ programGraph.getEdgeWeight(sourceString, destinationString) + ";");
					out.getWriter(M_LOGIC_CPP).println("  // from: " + sourceString + "  to: " + destinationString);
				}
			}
		}
		out.getWriter(M_LOGIC_CPP).println("}\n\n");
    }

    protected static void printProbabilitiesHashtable(VirtualDirectory out)
    {
		out.getWriter(M_LOGIC_CPP).println("void setup_probabilities_hashtable() {");
		//Collection<Source> sources = programGraph.getSources();
		Collection fns = programGraph.getFunctions();
		Iterator it = fns.iterator();
		while (it.hasNext())
		{
			TaskDeclaration td = (TaskDeclaration)it.next();
			Vertex vertex = programGraph.getVertex(td.getName());
			String sourceString = td.getName();
			IncidenceListGraph ilg = programGraph.getGraph();
			EdgeIterator edgeIterator = ilg.incidentEdges(vertex, EdgeDirection.OUT);
			
			if (td.getErrorHandler() != null)
			{
				out.getWriter(M_LOGIC_CPP).print("\tedge_count[" + programGraph.getStageNumber(sourceString));
				out.getWriter(M_LOGIC_CPP).print("][" + programGraph.getStageNumber(td.getErrorHandler()));
				out.getWriter(M_LOGIC_CPP).print("] = 0;");
				out.getWriter(M_LOGIC_CPP).println("  // from: " + sourceString + "  to: " + td.getErrorHandler());
			}

			
			while(edgeIterator.hasNext())
			{
				Edge edge = edgeIterator.nextEdge();
				Vertex destination = ilg.destination(edge);
				GraphNode graphNode = (GraphNode)destination.element();
				if (graphNode.getNodeType() == GraphNode.DEFAULT)
				{
					TaskDeclaration destTD = (TaskDeclaration)graphNode.getElement();
					String destinationString = destTD.getName();
					out.getWriter(M_LOGIC_CPP).print("\tedge_count[" + programGraph.getStageNumber(sourceString)
							+ "][" + programGraph.getStageNumber(destinationString) +"] = "
							+ "0;");
					
					out.getWriter(M_LOGIC_CPP).println("  // from: " + sourceString + "  to: " + destinationString);
				}
			}
		}
		
		// now we print out the error nodes...
		// NUMBER_OF_ERRORS
		
//		out.getWriter(M_LOGIC_CPP).println("\n");
		
		out.getWriter(M_LOGIC_CPP).println("}\n\n");
    }
    
    protected static void printExit(VirtualDirectory out)
    {
    	out.getWriter(M_LOGIC_CPP).println("void sig_handler(int signal){");
    	out.getWriter(M_LOGIC_CPP).println("\tprint_statistics();");
    	out.getWriter(M_LOGIC_CPP).println("\tprint_path_statistics();");
    	out.getWriter(M_LOGIC_CPP).println("\texit(0);");
    	out.getWriter(M_LOGIC_CPP).println("}");
    }
    
    protected static void printStatistics(VirtualDirectory out)
    {
    	
    	out.getWriter(M_LOGIC_CPP).println("void print_statistics() {");

    	// create the file and open it 
    	out.getWriter(M_LOGIC_CPP).println("FILE *fp;");
    	out.getWriter(M_LOGIC_CPP).println("fp = fopen(\"flux_csim_includes.h\", \"w\");");
    	
		Collection<Source> sources = programGraph.getSources();
		Collection fns = programGraph.getFunctions();
		
		//Iterator srcIterator = sources.iterator();
		out.getWriter(M_LOGIC_CPP).println("\n\n// we need the source inter-arrival times");
		out.getWriter(M_LOGIC_CPP).println("double curr_IA_time = 0.0;");
		for(Source src: sources)
		{
			int srcNum = programGraph.getStageNumber(src.getSourceFunction());
			String iaTimeStr = "#define IA_TIME_" + src.getSourceFunction().toUpperCase();
			//get_avg_ia_time
			out.getWriter(M_LOGIC_CPP).print("curr_IA_time = stats_logger[" + srcNum + "]");
			out.getWriter(M_LOGIC_CPP).println(".get_avg_ia_time();");
			out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"" + iaTimeStr + "\t%f\\n\", curr_IA_time );");
			//out.getWriter(M_LOGIC_CPP).println("\t" + );
		}
		
		out.getWriter(M_LOGIC_CPP).println("\n\n// now we print out all of the completion times");
		
		Iterator it = fns.iterator();
		
		out.getWriter(M_LOGIC_CPP).println("double curr_cmp_clock_time = 0.0;");
		out.getWriter(M_LOGIC_CPP).println("double curr_cmp_cpu_time = 0.0;");
		
		while (it.hasNext())
		{
			TaskDeclaration td = (TaskDeclaration)it.next();
			//Vertex vertex = programGraph.getVertex(td.getName());
			String currFunc = td.getName();
			
			int funcNum = programGraph.getStageNumber(currFunc);
			

			out.getWriter(M_LOGIC_CPP).print("curr_cmp_cpu_time = ");
			out.getWriter(M_LOGIC_CPP).println("stats_logger[" + funcNum + "].get_avg_cpu_time();");

			out.getWriter(M_LOGIC_CPP).print("curr_cmp_clock_time = ");
			out.getWriter(M_LOGIC_CPP).println("stats_logger[" + funcNum + "].get_avg_wallclock_time();");

			
			String cmpTimeCpuClockStr = "#define CMP_TIME_CPU_" + currFunc.toUpperCase();
			String cmpTimeWallClockStr = "#define CMP_TIME_CLOCK_" + currFunc.toUpperCase();
			out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"" + cmpTimeCpuClockStr + "\t%f\\n\", curr_cmp_cpu_time );");
			out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"" + cmpTimeWallClockStr + "\t%f\\n\", curr_cmp_clock_time );");
			
			if (td.getErrorHandler() != null)
			{
				out.getWriter(M_LOGIC_CPP).print("curr_cmp_cpu_time = ");
				out.getWriter(M_LOGIC_CPP).println("stats_logger[" + programGraph.getStageNumber(td.getErrorHandler()) + "].get_avg_cpu_time();");

				out.getWriter(M_LOGIC_CPP).print("curr_cmp_clock_time = ");
				out.getWriter(M_LOGIC_CPP).println("stats_logger[" + programGraph.getStageNumber(td.getErrorHandler()) + "].get_avg_wallclock_time();");

				cmpTimeCpuClockStr = "#define CMP_TIME_CPU_" + td.getErrorHandler().toUpperCase();
				cmpTimeWallClockStr = "#define CMP_TIME_CLOCK_" + td.getErrorHandler().toUpperCase();
				out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"" + cmpTimeCpuClockStr + "\t%f\\n\", curr_cmp_cpu_time );");
				out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"" + cmpTimeWallClockStr + "\t%f\\n\", curr_cmp_clock_time );");
			}
		}
		
		
		out.getWriter(M_LOGIC_CPP).println("\n\n// now we print the probabilities!!");
		
		out.getWriter(M_LOGIC_CPP).println("double curr_prob = 0.0;");
		out.getWriter(M_LOGIC_CPP).println("unsigned int curr_num_visits = 0;");

		
		it = fns.iterator();
		while (it.hasNext())
		{
			TaskDeclaration td = (TaskDeclaration)it.next();
			Vertex vertex = programGraph.getVertex(td.getName());
			String sourceString = td.getName();
			IncidenceListGraph ilg = programGraph.getGraph();
			EdgeIterator edgeIterator = ilg.incidentEdges(vertex, EdgeDirection.OUT);

			int currNodeNum = programGraph.getStageNumber(sourceString);
			
			out.getWriter(M_LOGIC_CPP).print("curr_num_visits = ");
			out.getWriter(M_LOGIC_CPP).println("stats_logger[" + currNodeNum + "].get_num_arrivals();");
			
			while(edgeIterator.hasNext())
			{
				Edge edge = edgeIterator.nextEdge();
				Vertex destination = ilg.destination(edge);
				GraphNode graphNode = (GraphNode)destination.element();
				if (graphNode.getNodeType() == GraphNode.DEFAULT)
				{
					
					TaskDeclaration destTD = (TaskDeclaration)graphNode.getElement();
					String destinationString = destTD.getName();
					
					out.getWriter(M_LOGIC_CPP).println("if (curr_num_visits > 0){");
					
					out.getWriter(M_LOGIC_CPP).print("curr_prob = (double) (");
					out.getWriter(M_LOGIC_CPP).print("(double)edge_count[" + programGraph.getStageNumber(sourceString)
							+ "][" + programGraph.getStageNumber(destinationString) +"]");
					out.getWriter(M_LOGIC_CPP).println(" / (double) curr_num_visits);");

					String probStr = "#define PROB_" + sourceString.toUpperCase() + "_TO_" + destinationString.toUpperCase();;
					out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"" + probStr + "\t%f\\n\", curr_prob );");

					if (td.getErrorHandler() != null)
					{

						out.getWriter(M_LOGIC_CPP).print("curr_prob = (double) (");
						out.getWriter(M_LOGIC_CPP).print("(double)edge_count[" + programGraph.getStageNumber(sourceString)
								+ "][" + programGraph.getStageNumber(td.getErrorHandler()) +"]");
						out.getWriter(M_LOGIC_CPP).println(" / (double) curr_num_visits);");

						probStr = "#define PROB_" + sourceString.toUpperCase() + "_TO_" + td.getErrorHandler().toUpperCase();
						out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"" + probStr + "\t%f\\n\", curr_prob );");
					}

					
					out.getWriter(M_LOGIC_CPP).println("}");
				}
			}
		}
		
    	out.getWriter(M_LOGIC_CPP).println("fclose(fp);");
    	out.getWriter(M_LOGIC_CPP).println("}\n\n");
    }

    protected static void printPathStatistics(VirtualDirectory out)
    {
    	out.getWriter(M_LOGIC_CPP).println("void print_path_statistics() {");
    	out.getWriter(M_LOGIC_CPP).println("FILE *fp;");
    	out.getWriter(M_LOGIC_CPP).println("fp = fopen(\"path_results.txt\", \"w\");");
    	// first print out the path frequencies 
    	out.getWriter(M_LOGIC_CPP).println("for(int i = 0; i < NUMBER_OF_PATHS; i++){");
    	out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"path # %d frequency: %d \\n \", i, path_profiler[i]);");
    	out.getWriter(M_LOGIC_CPP).println("}");

    	//	then print out the path durations
    	out.getWriter(M_LOGIC_CPP).println("for(int i = 0; i < NUMBER_OF_PATHS; i++){");
    	out.getWriter(M_LOGIC_CPP).println("fprintf(fp, \"path # %d duration: %f \\n \"," +
    			" i, (double)(path_time_sum[i] / path_profiler[i]));");
    	out.getWriter(M_LOGIC_CPP).println("}");
    	
    	out.getWriter(M_LOGIC_CPP).println("fclose(fp);");
    	out.getWriter(M_LOGIC_CPP).println("}\n\n");
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
	    prefix = last.getName()+"_var_in.";
	}
	else {
	    args = last.getOutputs();
	    prefix = last.getName()+"_var_out.";
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
        out.getWriter(M_LOGIC_CPP).println(")");
        
    }
}


