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
 * A generator that creates Thread and Threadpool runtimes in the C
 * programming language
 * @author Brendan, Kevin, Alex
 **/
public class ThreadGenerator extends AbstractGeneratorC {
	private static final String M_THREAD_CPP = "mThread.cpp";

	protected static boolean session_id;

	// The simulated call stack for tasks
	protected static Stack<TaskDeclaration> outputStack = new Stack<TaskDeclaration>();

	// A stack of what we have locks for, this way we can properly unlock them
	// on errors; totally stopgap
	protected static Stack<String> lockStack = new Stack<String>();

	// Is the input to the function the input, or the previous output?
	protected static Stack<Boolean> inSelectStack = new Stack<Boolean>();

	protected static int pool_size = 10;

	protected static boolean thread_pool = true;

	private static Hashtable<String, Integer> stageNumber;

	private static Hashtable<Integer, String> stageName = new Hashtable<Integer, String>();

	private static int totalStages;

	/**
	 * Set the size of the threadpool that is used.
	 * @param size The new size
	 **/
	public static void setThreadPoolSize(int size) {
		pool_size = size;
	}

	/**
	 * Use a thread pool?
	 * @param pool if true, use a thread pool, otherwise 1-1
	 **/
	public static void setUseThreadPool(boolean pool) {
		thread_pool = pool;
	}

	static int lock_id = 0;

	public static void printSessionObject(Collection<AtomicDeclaration> decs,
			VirtualDirectory out) {
		out.getWriter(M_STRUCT_H).println("#include \"rwlock.h\"");
		out.getWriter(M_STRUCT_H).println("namespace flux {");
		out.getWriter(M_STRUCT_H).println("\tclass session {");
		out.getWriter(M_STRUCT_H).println("\tpublic:");
		out.getWriter(M_STRUCT_H).println("\t\tint session_id;");

		Hashtable<Lock, Boolean> defLocks = new Hashtable<Lock, Boolean>();

		for (AtomicDeclaration ad : decs) {
			Vector<Lock> lks = ad.getLocks();
			for (Lock l : lks) {
				if (l.isSession() && defLocks.get(l) == null) {
					defLocks.put(l, Boolean.TRUE);
					out.getWriter(M_STRUCT_H).print("\t\tflux::rwlock ");
					out.getWriter(M_STRUCT_H).println(l.getName() + ";");
				}
			}
		}

		out.getWriter(M_STRUCT_H).println("\t};");
		out.getWriter(M_STRUCT_H).println("};");
	}

	public static void printLock(String sId, AtomicDeclaration ad,
			VirtualDirectory out) {
		if (ad == null)
			return;
		Vector<Lock> lks = ad.getLocks();
		java.util.Collections.sort(lks);
		for (Lock l : lks) {
			String lock;

			if (l.isProgram()) {
				lock = "locks[" + locks.get(l) + "]->";
			} else if (l.isSession()) {
				lock = sId + "->" + l.getName() + ".";
			} else {
				System.err
						.println("Error, " + l + " is not program or session");
				return;
			}

			if (l.isReader())
				out.getWriter(M_LOGIC_CPP).println(
						lock + "readLock(); // Lock " + l.getName());
			else if (l.isWriter())
				out.getWriter(M_LOGIC_CPP).println(
						lock + "writeLock(); // Lock " + l.getName());
			else {
				System.err.println("Error, " + l + " isn't reader or writer?");
			}
		}
	}

	public static void printUnlock(String sId, AtomicDeclaration ad,
			VirtualDirectory out) {
		if (ad == null)
			return;
		Vector<Lock> lks = ad.getLocks();
		java.util.Collections.sort(lks);
		java.util.Collections.reverse(lks);

		for (int i = lks.size() - 1; i > -1; i--) {
			String lock;

			if (lks.get(i).isProgram()) {
				lock = "locks[" + locks.get(lks.get(i)) + "]->";
			} else if (lks.get(i).isSession()) {
				lock = sId + "->" + lks.get(i).getName() + ".";
			} else {
				System.err.println("Error, " + lks.get(i)
						+ " is not program or session");
				return;
			}
			out.getWriter(M_LOGIC_CPP).println(
					lock + "unlock(); // Lock " + lks.get(i).getName());
		}
	}

	public static void printLocks(Collection<AtomicDeclaration> decs,
			VirtualDirectory out) {
		PrintWriter pw = out.getWriter(M_LOGIC_CPP);
		if (decs.size() < 1) {
			pw.println("void initLocks() {}");
			return;
		}
		pw.println("#include <rwlock.h>");
		pw.println("#include <vector>");
		pw.println("std::vector<flux::rwlock *> locks;");
		pw.println("void initLocks() {");
		for (AtomicDeclaration ad : decs) {
			Vector<Lock> lks = ad.getLocks();
			for (Lock l : lks) {
				if (locks.get(l) == null) {
					pw.println("locks.push_back(new flux::rwlock());");
					locks.put(l, new Integer(lock_id++));
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
		printSessionObject(g.getProgram().getAtomicDeclarations(), out);

		out.getWriter(M_LOGIC_CPP).println("#include \"" + M_IMPL_H + "\"");
		out.getWriter(M_LOGIC_CPP).println("#include \"functionclosure.h\"");
		out.getWriter(M_LOGIC_CPP).println(
				"#include \"threadpool.h\"\n" + "#include \"timer.h\"\n"
						+ "#include \"logger.h\"\n"
						+ "#include \"graphClient.h\"\n"
						+ "#include <signal.h>");
		
		if (!thread_pool)
			out.getWriter(M_LOGIC_CPP).println("void *runAndDelete(void *);");
		if (loggingOn) {
			out.getWriter(M_LOGIC_CPP).println(
							"std::map <int, std::map<int, int> > edge_value; // format: from, to -> edge value");
			out.getWriter(M_LOGIC_CPP).println(
							"std::map <int, std::map<int, int> > edge_count; // format: from, to -> edge value");
		}
	}

	/*
	protected static int getStageNumber(String name) {
		Integer stg = stageNumber.get(name);
		if (stg == null) {
			stg = new Integer(totalStages);
			totalStages++;
			stageNumber.put(name, stg);
		}
		return stg.intValue();
	}
	*/
	/**
	 * Print one thread handler function
	 * @param src The name of the source
	 * @param td The first task to call
	 * @param g The program graph
	 * @param out Where to write
	 **/
	protected static void printThreadFn(String src, TaskDeclaration td,
			ProgramGraph g, VirtualDirectory out) {
		
		out.getWriter(M_LOGIC_CPP).println
		    ("void * " + td.getName() + "_Handler(void *arg) {"+
		     "wrapper_struct *wrapper = (wrapper_struct *)arg;");
		out.getWriter(M_LOGIC_CPP).println("double currTime;");
		out.getWriter(M_LOGIC_CPP).println("int pathSum = wrapper->pathSum;");
		out.getWriter(M_LOGIC_CPP).println(
				"double currPathTimeSum = wrapper->pathTimeSum;");
		//out.getWriter(M_LOGIC_CPP).println("TaskDeclaration: " + td.getName());
		if (loggingOn) {
			if (program.getFlow(td.getName()) != null) {
				out.getWriter(M_LOGIC_CPP).println(
						"int int_log_" + td.getName()
								+ g.getStageNumber(td.getName())
								+ " = stats_logger["
								+ g.getStageNumber(td.getName()) + "].start();");
			}
		}
		out.getWriter(M_LOGIC_CPP).println(
				td.getName() + "_in *ptr = (" + td.getName()
						+ "_in *)wrapper->struct_pointer;");
		out.getWriter(M_LOGIC_CPP).println(
				td.getName() + "_in " + td.getName() + "_var_in = *ptr;");
		out.getWriter(M_LOGIC_CPP).println("int err = 0;");

		outputStack.push(td);
		inSelectStack.push(Boolean.TRUE);
		if (loggingOn) {
			if (program.getFlow(td.getName()) != null) {
				out.getWriter(M_LOGIC_CPP).print("currTime = ");
				out.getWriter(M_LOGIC_CPP).println(
						"stats_logger[" + g.getStageNumber(td.getName())
								+ "].stop(int_log_" + td.getName()
								+ g.getStageNumber(td.getName()) + ");");
				out.getWriter(M_LOGIC_CPP)
						.print("currPathTimeSum += currTime;");
			}
		}
		printFlowRecursive(td.getName(), g, out);

		// TODO:adsfa
		if (loggingOn) {
			out.getWriter(M_LOGIC_CPP).println("path_profiler[pathSum]++;");
			out.getWriter(M_LOGIC_CPP).println(
					"path_time_sum[pathSum] += currPathTimeSum;");
		}
		out.getWriter(M_LOGIC_CPP).println("return 0;");
		out.getWriter(M_LOGIC_CPP).println("}");

		out.getWriter(M_LOGIC_CPP).println(
				"void *" + td.getName() + "_Execute(" + "void *in) {");
		out.getWriter(M_LOGIC_CPP).println("wrapper_struct *wrapper = (wrapper_struct *) in;");
		out.getWriter(M_LOGIC_CPP).println(td.getName() + "_Handler( wrapper );");
		out.getWriter(M_LOGIC_CPP).println("delete (" + td.getName() 
				+ "_in *) wrapper->struct_pointer;");
		out.getWriter(M_LOGIC_CPP).println("delete wrapper;");
		out.getWriter(M_LOGIC_CPP).println("return NULL;");
		out.getWriter(M_LOGIC_CPP).println("}");

	}

	/**
	 * Recursively evaluate the call stack and print a flow statement
	 * @param name The name of the statement
	 * @param g The program graph
	 * @param out The output directory
	 **/
	protected static void printFlowRecursive(String name, ProgramGraph g,
			VirtualDirectory out) {
		printFlowRecursive(name, g.getProgram(), out);
	}

	/**
	 * Recursively evaluate the call stack and print a flow statement
	 * @param name The name of the statement
	 * @param p The program
	 * @param out The output directory
	 **/
	protected static void printFlowRecursive(String name, Program p,
			VirtualDirectory out) {
		FlowStatement stmt = p.getFlow(name);
		if (stmt != null) {

			// a node that does work
			if (stmt instanceof SimpleFlowStatement) {
				printFlowRecursive((SimpleFlowStatement) stmt, name, p, out);
			}
			// a node that decides which direction you should go into
			else {
				printFlowRecursive((TypedFlowStatement) stmt, p, out);
			}
		} else
			printTask(name, p, out);
	}

	/**
	 * Print a task (function call)
	 * @param name The name of the function
	 * @param p The program
	 * @param out Where to write
	 **/
	protected static void printTask(String name, Program p, VirtualDirectory out) {
		String prefix;
		String in_str;
		String out_str;
		Vector<Argument> args;

		// Find the task we need to run
		TaskDeclaration fn = p.getTask(name);

		// Find out what the last task to be called was so we can use its output
		TaskDeclaration td = outputStack.pop();
		Boolean inSelect = inSelectStack.pop();

		// If we're actually the first task nested in a flow statement we want to
		// use the _input_ to the flow statement, otherwise the output from the 
		// last task
		if (inSelect.booleanValue()) {
			prefix = td.getName() + "_var_in";
			args = td.getInputs();
		} else {
			prefix = td.getName() + "_var_out";
			args = td.getOutputs();
		}

		Vector<Argument> ins = fn.getInputs();

		// Check if this task actually has any inputs, and if so figure out
		// what the input structure is called
		if (args.size() > 0) {
			in_str = "&" + fn.getName() + "_var_in";
			if (td != fn)
				out.getWriter(M_LOGIC_CPP).println(
						fn.getName() + "_in " + fn.getName() + "_var_in;");
		} else
			in_str = "";
		// See if it has outputs; if so create a structure to hold them
		if (fn.getOutputs().size() > 0) {
			out_str = "&" + fn.getName() + "_var_out";
			out.getWriter(M_LOGIC_CPP).println(
					fn.getName() + "_out " + fn.getName() + "_var_out;");
		} else
			out_str = "";

		// If it has both inputs and outputs we need a comma between them in the
		// function call
		if (!in_str.equals("") && !out_str.equals(""))
			out_str = ", " + out_str;

		// Fill in the input structure
		if (td != fn) { // If td == fn that means that we dropped straight into
			// PrintTask from PrintFlowRecursive, so our input structure
			// was already made for us, so we don't need to copy it
			for (int i = 0; i < args.size(); i++)
				out.getWriter(M_LOGIC_CPP).println(
						fn.getName() + "_var_in." + ins.get(i).getName()
								+ " = " + prefix + "." + args.get(i).getName()
								+ ";");
			if (session_id && fn.getOutputs().size() > 0)
				out.getWriter(M_LOGIC_CPP).println(
						fn.getName() + "_var_in._mSession = " + prefix
								+ "._mSession;");
		}
		if (loggingOn) {
			out.getWriter(M_LOGIC_CPP).println(
					"int int_log_" + fn.getName()
							+ programGraph.getStageNumber(fn.getName()) + " = stats_logger["
							+ programGraph.getStageNumber(fn.getName()) + "].start();");
		}
		// Get locks, call function, error out if necessary
		printLock(fn.getName() + "_var_in._mSession", p
				.getAtomicDeclaration(name), out);
		out.getWriter(M_LOGIC_CPP).println(
				"err = " + name + "(" + in_str + out_str + ");");
		if (session_id && out_str.length() > 0)
			out.getWriter(M_LOGIC_CPP).println(
					name + "_var_out._mSession = " + name
							+ "_var_in._mSession;");
		printUnlock(fn.getName() + "_var_in._mSession", p
				.getAtomicDeclaration(name), out);

		if (loggingOn)
		{
			if (loggingOn) {
				out.getWriter(M_LOGIC_CPP).print("currTime = ");
				out.getWriter(M_LOGIC_CPP).println(
						"stats_logger[" + programGraph.getStageNumber(fn.getName())
								+ "].stop(int_log_" + fn.getName()
								+ programGraph.getStageNumber(fn.getName()) + ");");
				out.getWriter(M_LOGIC_CPP).println("currPathTimeSum += currTime;");

			}
		}
		
		out.getWriter(M_LOGIC_CPP).println("if (err) {");		
		String errorName = "ERROR";
		if (fn.getErrorHandler() != null) 
		{
			errorName = fn.getErrorHandler();
			int errorNumber = programGraph.getErrorNumber(fn.getErrorHandler());
			if (loggingOn)
			{	
/*
				out.getWriter(M_LOGIC_CPP).println("stats_logger[" + programGraph.getStageNumber(fn.getName())
						+ "].stop(int_log_" + fn.getName()
						+ programGraph.getStageNumber(fn.getName()) + ");");
*/
				out.getWriter(M_LOGIC_CPP).print("edge_count[" + programGraph.getStageNumber(fn.getName()));
				out.getWriter(M_LOGIC_CPP).print("][" + programGraph.getStageNumber(fn.getErrorHandler()));
				out.getWriter(M_LOGIC_CPP).println("]++;");
				
				out.getWriter(M_LOGIC_CPP).print("int err_num_" + programGraph.getStageNumber(fn.getErrorHandler()) + " = ");
				out.getWriter(M_LOGIC_CPP).println("stats_logger[" + programGraph.getStageNumber(fn.getErrorHandler()) +
						"].start();");
			}
						

		}
		for (String tdlk : lockStack)
			printUnlock(fn.getName() + "_var_in._mSession", p
					.getAtomicDeclaration(tdlk), out);

		int path_weight = printErrorHandler(fn, p, out);
		
		if (loggingOn) 
		{
			out.getWriter(M_LOGIC_CPP).println(
					"pathSum +=" + path_weight + ";  //FROM: " + fn.getName()
							+ " to:" + errorName);
			if (fn.getErrorHandler() != null) 
				out.getWriter(M_LOGIC_CPP).println("stats_logger[" + programGraph.getStageNumber(fn.getErrorHandler()) +
						"].stop(err_num_" + programGraph.getStageNumber(fn.getErrorHandler()) + ");");
			
			out.getWriter(M_LOGIC_CPP).println("path_profiler[pathSum]++;");
			out.getWriter(M_LOGIC_CPP).print("currTime = ");
			out.getWriter(M_LOGIC_CPP).println("currPathTimeSum += currTime;");
			out.getWriter(M_LOGIC_CPP).println(
					"path_time_sum[pathSum] += currPathTimeSum;");
		}
		out.getWriter(M_LOGIC_CPP).println("return 0;");
		out.getWriter(M_LOGIC_CPP).println("}");
/*
		if (loggingOn) {
			out.getWriter(M_LOGIC_CPP).print("currTime = ");
			out.getWriter(M_LOGIC_CPP).println(
					"stats_logger[" + programGraph.getStageNumber(fn.getName())
							+ "].stop(int_log_" + fn.getName()
							+ programGraph.getStageNumber(fn.getName()) + ");");
			out.getWriter(M_LOGIC_CPP).println("currPathTimeSum += currTime;");

		}
*/
		outputStack.push(fn);
		inSelectStack.push(Boolean.FALSE);
	}

	/**
	 * Print a simple flow statement
	 * @param sfs The statement
	 * @param p The program
	 * @param out Where to write
	 **/
	protected static void printFlowRecursive(SimpleFlowStatement sfs,
			String parent, Program p, VirtualDirectory out) {
		TaskDeclaration td = outputStack.peek();
		Boolean inSelect = inSelectStack.peek();
		String prefix;
		// last task
		if (inSelect.booleanValue()) {
			prefix = td.getName() + "_var_in";
		} else {
			prefix = td.getName() + "_var_out";
		}

		lockStack.push(sfs.getAssignee());

		printLock(prefix + "._mSession", p.getAtomicDeclaration(sfs
				.getAssignee()), out);
		Vector args = sfs.getArguments();
		
		//Hashtable<String, Integer> hash = stageNumber;
		
		for (int i = 0; i < args.size(); i++) {
			//
			if (loggingOn) {
				int edgeWeight;
				if (currParent == "") {
					edgeWeight = programGraph.getEdgeWeight(parent,
							(String) args.get(i));
					out.getWriter(M_LOGIC_CPP).println(
							"pathSum += " + edgeWeight + "; //from: " + parent
									+ " to: " + (String) args.get(i));
					
					out.getWriter(M_LOGIC_CPP).print("edge_count[" + stageNumber.get(parent) + "][");
					out.getWriter(M_LOGIC_CPP).println(stageNumber.get((String)args.get(i)) + "]++;");
					
					//out.getWriter(M_LOGIC_CPP).print();
					
				} else {
					edgeWeight = programGraph.getEdgeWeight(currParent,
							(String) args.get(i));
					out.getWriter(M_LOGIC_CPP).println(
							"pathSum += " + edgeWeight + "; //from: "
									+ currParent + " to: "
									+ (String) args.get(i));

					out.getWriter(M_LOGIC_CPP).print("edge_count[" + stageNumber.get(currParent) + "][");
					out.getWriter(M_LOGIC_CPP).println(stageNumber.get((String)args.get(i)) + "]++;");
					
					
					
				}
			}
			currParent = (String) args.get(i);
			printFlowRecursive((String) args.get(i), p, out);
		}
		lockStack.pop();
		printUnlock(prefix + "._mSession", p.getAtomicDeclaration(sfs
				.getAssignee()), out);
	}

	/**
	 * Print a typed flow statement
	 * @param tfs The statement
	 * @param p The program
	 * @param out Where to write
	 **/
	protected static void printFlowRecursive(TypedFlowStatement tfs, Program p,
			VirtualDirectory out) {
		Vector sts = tfs.getFlowStatements();
		TaskDeclaration type = p.getTask(tfs.getAssignee());

		String parent = type.getName();

		TaskDeclaration save = outputStack.pop();
		Boolean selectSave = inSelectStack.pop();

		out.getWriter(M_LOGIC_CPP).println(
				type.getName() + "_out " + type.getName() + "_var_out;");
		String internalCurrParent = currParent;

		if (loggingOn) {
			out.getWriter(M_LOGIC_CPP).println(
					"int int_log_" + type.getName()
							+ programGraph.getStageNumber(type.getName())
							+ " = stats_logger["
							+ programGraph.getStageNumber(type.getName()) + "].start();");
		}
		for (int i = 0; i < sts.size(); i++) {
			outputStack.push(save);
			inSelectStack.push(selectSave);

			currParent = internalCurrParent;
			SimpleFlowStatement fs = (SimpleFlowStatement) sts.get(i);

			if (i != 0)
				out.getWriter(M_LOGIC_CPP).print("else ");
			generateTypeTests(fs.getTypes(), p, save, selectSave, out);
			out.getWriter(M_LOGIC_CPP).println("{");

			if (loggingOn) {
				out.getWriter(M_LOGIC_CPP).print("currTime = ");
				out.getWriter(M_LOGIC_CPP).println(
						"stats_logger[" + programGraph.getStageNumber(parent)
								+ "].stop(int_log_" + parent
								+ programGraph.getStageNumber(parent) + ");");
				out.getWriter(M_LOGIC_CPP).println(
						"currPathTimeSum += currTime;");
			}
			printFlowRecursive(fs, fs.getAssignee(), p, out);

			Vector<Argument> outs = type.getOutputs();
			TaskDeclaration last = outputStack.pop();
			inSelectStack.pop();
			Vector<Argument> lOuts = last.getOutputs();
			for (int j = 0; j < outs.size(); j++) {
				out.getWriter(M_LOGIC_CPP).println(
						type.getName() + "_var_out." + outs.get(j).getName()
								+ " = " + last.getName() + "_var_out."
								+ lOuts.get(j).getName() + ";");
			}

			if (session_id) {
				out.getWriter(M_LOGIC_CPP).println(
						type.getName() + "_var_out._mSession = "
								+ last.getName() + "_var_in._mSession;");
			}

			out.getWriter(M_LOGIC_CPP).println("}");
		}
		outputStack.push(p.getTask(tfs.getAssignee()));
		inSelectStack.push(Boolean.FALSE);
	}

	/**
	 * Print the function to execute a source
	 * @param s The source
	 * @param p The program
	 * @param out Where to write
	 **/

	public static void printSourceFunction(Source s, Program p,
			VirtualDirectory out) {
		String prefix;
		Vector<Argument> args;
		currParent = "";
		out.getWriter(M_LOGIC_CPP).println(
				"int " + s.getSourceFunction() + "_exec() {");
		out.getWriter(M_LOGIC_CPP).println("int err = 0;");
		out.getWriter(M_LOGIC_CPP).println("double currTime = 0;");
		out.getWriter(M_LOGIC_CPP).println("double currPathTimeSum = 0;");
		out.getWriter(M_LOGIC_CPP).println("int pathSum = 0;");
		outputStack.push(p.getTask(s.getSourceFunction()));
		inSelectStack.push(Boolean.TRUE);
		session_id = false;
		printFlowRecursive(s.getSourceFunction(), p, out);
		session_id = true;

		currParent = "";
		TaskDeclaration src_out = outputStack.pop();
		Boolean inSelect = inSelectStack.pop();

		if (loggingOn) {
			int currSum = programGraph.getEdgeWeight(programGraph
					.getVertexEntry(), programGraph.getVertex(s
					.getSourceFunction()));
			out.getWriter(M_LOGIC_CPP).print("pathSum += " + currSum + ";");
			out.getWriter(M_LOGIC_CPP).println(
					"  // from: ENTRY" + " to: " + s.getSourceFunction());
			if (s.getIsMSN() == false) {
				currSum = programGraph.getEdgeWeight(s.getSourceFunction(), s
						.getTarget());
				out.getWriter(M_LOGIC_CPP).print("pathSum += " + currSum + ";");
				out.getWriter(M_LOGIC_CPP).println(
						"  // from: " + s.getSourceFunction() + " to: "
								+ s.getTarget());
				
				out.getWriter(M_LOGIC_CPP).print("edge_count[" + stageNumber.get(s.getSourceFunction()) + "][");
				out.getWriter(M_LOGIC_CPP).println(stageNumber.get(s.getTarget()) + "]++;");
				
			} else {
				currSum = programGraph.getEdgeWeight(src_out.getName(), s
						.getTarget());

				out.getWriter(M_LOGIC_CPP).print("pathSum += " + currSum + ";");
				out.getWriter(M_LOGIC_CPP).println(
						"  // from: " + src_out.getName() + " to: "
								+ s.getTarget());

				out.getWriter(M_LOGIC_CPP).print("edge_count[" + stageNumber.get(src_out.getName()) + "][");
				out.getWriter(M_LOGIC_CPP).println(stageNumber.get(s.getTarget()) + "]++;");

			}
		}
		if (inSelect.booleanValue()) {
			prefix = src_out.getName() + "_var_in";
			args = src_out.getInputs();
		} else {
			prefix = src_out.getName() + "_var_out";
			args = src_out.getOutputs();
		}

		TaskDeclaration path_in = p.getTask(s.getTarget());
		Vector<Argument> ins = path_in.getInputs();

		if (args.size() > 0)
			out.getWriter(M_LOGIC_CPP).println(
					path_in.getName() + "_in *" + path_in.getName()
							+ "_var_in = new " + path_in.getName() + "_in;");

		for (int i = 0; i < args.size(); i++)
			out.getWriter(M_LOGIC_CPP).println(
					path_in.getName() + "_var_in->" + ins.get(i).getName()
							+ " = " + prefix + "." + args.get(i).getName()
							+ ";");

		if (session_id) {
			if (s.getSessionFunction() != null) {
				out.getWriter(M_LOGIC_CPP).println(
						"int id = " + s.getSessionFunction() + "("
								+ path_in.getName() + "_var_in);");
				out.getWriter(M_LOGIC_CPP).println(
						"flux::session *s=session_locks.getSession(id);");
				out.getWriter(M_LOGIC_CPP).println(
						path_in.getName() + "_var_in->_mSession = s;");
			} else {
				out.getWriter(M_LOGIC_CPP).print(
						path_in.getName() + "_var_in->_mSession = ");
				out.getWriter(M_LOGIC_CPP).println("NULL;");
			}
		}

		if (thread_pool) {
			out.getWriter(M_LOGIC_CPP).println(
					"wrapper_struct *wrapper = new wrapper_struct;");
			out.getWriter(M_LOGIC_CPP).println(
					"wrapper->struct_pointer = " + s.getTarget() + "_var_in;");
			out.getWriter(M_LOGIC_CPP).println("wrapper->pathSum = pathSum;");
			out.getWriter(M_LOGIC_CPP).println(
					"wrapper->pathTimeSum = currPathTimeSum;");
			out.getWriter(M_LOGIC_CPP).println(
					"threadpool.queue_task(" + s.getTarget() + "_Execute, "
							+ " wrapper);");
		}

		else {
			out.getWriter(M_LOGIC_CPP).println(
					"wrapper_struct *wrapper = new wrapper_struct;");
			out.getWriter(M_LOGIC_CPP).println(
					"wrapper->struct_pointer = " + s.getTarget() + "_var_in;");
			out.getWriter(M_LOGIC_CPP).println("wrapper->pathSum = pathSum;");
			out.getWriter(M_LOGIC_CPP).println("wrapper->pathTimeSum = 0;");

			out.getWriter(M_LOGIC_CPP).println(
					"flux::fred *thread = new flux::fred;");

			out
					.getWriter(M_LOGIC_CPP)
					.println(
							"thread->create(runAndDelete, new std::pair<flux::fred *,flux::functionClosure *>(thread, new flux::functionClosure("
									+ s.getTarget() + "_Handler, wrapper)));");

			out.getWriter(M_LOGIC_CPP).println("thread->detach();");
		}

		out.getWriter(M_LOGIC_CPP).println("return 0;");
		out.getWriter(M_LOGIC_CPP).println("}");

	}

	/**
	 * Print the main function
	 * @param out Where to write
	 **/
	public static void printMain(VirtualDirectory out) {
		if (!thread_pool) {
			out.getWriter(M_LOGIC_CPP).println("void *runAndDelete(void *a) {");
			out
					.getWriter(M_LOGIC_CPP)
					.println(
							"std::pair<flux::fred *,flux::functionClosure *> *arg = (std::pair<flux::fred *,flux::functionClosure *> *)a;");
			out.getWriter(M_LOGIC_CPP).println("arg->second->run();");
			out.getWriter(M_LOGIC_CPP).println("delete arg->second;");
			out.getWriter(M_LOGIC_CPP).println("delete arg->first;");
			out.getWriter(M_LOGIC_CPP).println("delete arg;");
			out.getWriter(M_LOGIC_CPP).println("}");
		}
		if (loggingOn) {
			printPathsHashTable(out);
			printProbabilitiesHashtable(out);
			printStatistics(out);
			printPathStatistics(out);
			printExit(out);
		}
		out.getWriter(M_LOGIC_CPP).println("int main(int argc, char **argv)");
		out.getWriter(M_LOGIC_CPP).println("{");

		if (loggingOn) {
			/*
			out.getWriter(M_LOGIC_CPP).println("\tfor (int i = 0; i < NUMBER_OF_ERRORS; i++)");
			out.getWriter(M_LOGIC_CPP).println("\t\tnum_errors[i] = 0;");
			*/
			out.getWriter(M_LOGIC_CPP).println(
					"\tfor (int i=0; i < NUMBER_OF_PATHS; i++){");
			out.getWriter(M_LOGIC_CPP).println("path_time_sum[i]=0;");
			out.getWriter(M_LOGIC_CPP).println("path_profiler[i]=0;");
			out.getWriter(M_LOGIC_CPP).println("}");
			out.getWriter(M_LOGIC_CPP).println("\tsetup_hashtable();");
			out.getWriter(M_LOGIC_CPP).println(
					"\tgraphClient = new GraphClient(12345);");
			out.getWriter(M_LOGIC_CPP).println("signal(SIGINT, sig_handler);");
		}
		out.getWriter(M_LOGIC_CPP).println("\tinitLocks();");
		
		out.getWriter(M_LOGIC_CPP).println("\tinit(argc, argv);");
		out.getWriter(M_LOGIC_CPP).println("\tloop();");
		out.getWriter(M_LOGIC_CPP).println("}");
	}

	public static void generate(String root, boolean logging, ProgramGraph g,
			Program pm, Hashtable<String, Integer> h) throws IOException {
		loggingOn = logging;
		program = pm;
		totalStages = h.size();
		stageNumber = h;
		programGraph = g;
		generate(root, g);
	}

	/**
	 * Generate a threaded program
	 * @param root The root directory to output into
	 * @param g The program graph
	 **/
	public static void generate(String root, ProgramGraph g) throws IOException {
		M_LOGIC_CPP = M_THREAD_CPP;

		VirtualDirectory out = new VirtualDirectory(root);

		prologue(g, out);

		session_id = true;
		if (stageNumber == null) {
			totalStages = 0;
			stageNumber = new Hashtable<String, Integer>();
			stageNumber.clear();
		}

		// Print the structs and the sigs for all tasks
		Collection fns = g.getProgram().getFunctions();
		Iterator it = fns.iterator();
		Collection<Source> sources = g.getSources();
		int size = fns.size();// + g.getSources().size();

		out.getWriter(M_LOGIC_CPP).println("static bool running;");
		if (loggingOn) {
			out.getWriter(M_LOGIC_CPP).println("GraphClient *graphClient;");
			out.getWriter(M_LOGIC_CPP).println(
					"#define NUMBER_OF_ERRORS "
							+ g.getProgram().getErrorHandlers().size());
			out.getWriter(M_LOGIC_CPP).println(
					"#define NUMBER_OF_PATHS " + programGraph.getIntNumPaths());
			out.getWriter(M_LOGIC_CPP).println(
					"#define NUMBER_OF_NODES " + size);
			
			//out.getWriter(M_LOGIC_CPP).println("Logger stats_logger[NUMBER_OF_NODES];");
			out.getWriter(M_LOGIC_CPP).println("Logger stats_logger[NUMBER_OF_NODES + NUMBER_OF_ERRORS];");
			out.getWriter(M_LOGIC_CPP).println(
					"int path_profiler[NUMBER_OF_PATHS];");
			out.getWriter(M_LOGIC_CPP).println(
					"double path_time_sum[NUMBER_OF_PATHS];");
			//out.getWriter(M_LOGIC_CPP).println("int num_errors[NUMBER_OF_ERRORS];");

			out.getWriter(M_LOGIC_CPP)
					.println("void* Logger_Updater(void *) {");
			out.getWriter(M_LOGIC_CPP).println("\twhile (running) {");
			out.getWriter(M_LOGIC_CPP).println("\tstd::string s = \"\";");
			out.getWriter(M_LOGIC_CPP).println("\tsleep(5);");
			out.getWriter(M_LOGIC_CPP).println(
					"\tfor(int i=0; i< NUMBER_OF_NODES; i++) ");
			out.getWriter(M_LOGIC_CPP).println("\t{");
			out.getWriter(M_LOGIC_CPP).println("\t\tchar buf[256];");
			out.getWriter(M_LOGIC_CPP).println("\t\tsprintf(buf, \"%d\", i);");
			out.getWriter(M_LOGIC_CPP).println("\t\ts += buf;");
			out.getWriter(M_LOGIC_CPP).println(
					"\t\ts += + \" \" + stats_logger[i].getValues() + \";\";");
			out.getWriter(M_LOGIC_CPP).println("\t}");

			/*
			out.getWriter(M_LOGIC_CPP).println("\tfor (int i = 0; i < NUMBER_OF_ERRORS; i++)");
			out.getWriter(M_LOGIC_CPP).println("\t{");
			out.getWriter(M_LOGIC_CPP).println("\t\t// e<error #> <count>");
			out.getWriter(M_LOGIC_CPP).println("\t\tchar buf[256];");
			out.getWriter(M_LOGIC_CPP).println("\t\tsprintf(buf, \"e: %d %d;\", NUMBER_OF_NODES +i, num_errors[i]);");
			out.getWriter(M_LOGIC_CPP).println("\t\ts += buf;");
			out.getWriter(M_LOGIC_CPP).println("\t}");
			*/
			
			out.getWriter(M_LOGIC_CPP)
					.println("\tgraphClient->sendMessage(s);");
			out.getWriter(M_LOGIC_CPP).println("\t}");
			out.getWriter(M_LOGIC_CPP).println("}");
		}

		while (it.hasNext()) {
			TaskDeclaration td = (TaskDeclaration) it.next();
			printStructs(td, out.getWriter(M_STRUCT_H));
			programGraph.getStageNumber(td.getName());
			printSignature(td, out);
		}

		// print the wrapper struct
		out.getWriter(M_STRUCT_H).println("struct wrapper_struct{");
		out.getWriter(M_STRUCT_H).println("\tvoid *struct_pointer;");
		out.getWriter(M_STRUCT_H).println("\tint pathSum;");
		out.getWriter(M_STRUCT_H).println("\tdouble pathTimeSum;");
		out.getWriter(M_STRUCT_H).println("};");

		// Print the sigs for all sources

		Program p = g.getProgram();

		if (thread_pool) {
			out.getWriter(M_LOGIC_CPP).println(
					"static flux::threadpool<" + pool_size + "> threadpool;");
		}
		for (Source s : sources) {
			TaskDeclaration td = g.findTask(s.getTarget());

			printSessionSignature(s, out);

			programGraph.getStageNumber(td.getName());
			printThreadFn(s.getSourceFunction(), td, g, out);
			printSourceFunction(s, g.getProgram(), out);
			out.getWriter(M_LOGIC_CPP).println(
					"void *" + s.getSourceFunction()
							+ "_source_handler(void *) {");
			out.getWriter(M_LOGIC_CPP).println("while (running) {");
			out.getWriter(M_LOGIC_CPP).println(
					s.getSourceFunction() + "_exec();");
			out.getWriter(M_LOGIC_CPP).println("}");
			out.getWriter(M_LOGIC_CPP).println("}");
		}

		out.getWriter(M_LOGIC_CPP).println("void loop() {");
		out.getWriter(M_LOGIC_CPP).println("running = true;");
		out.getWriter(M_LOGIC_CPP).println("int count = 0;");

		Boolean tr_declared = false;
		// The first source in the list of sources will get executed in the
		// initial thread, all the rest of the sources need to have a thread
		// spawned for them.
		Source firstSource = sources.iterator().next();

		out.getWriter(M_LOGIC_CPP).println("flux::fred *tr;");
		tr_declared = true;
		if (loggingOn) {
			out.getWriter(M_LOGIC_CPP).println("tr = new flux::fred();");
			out.getWriter(M_LOGIC_CPP).println(
					"tr->create(Logger_Updater, NULL);");
		}
		for (Source s : sources) {
			if (s != firstSource) {
				if (!tr_declared) {
					out.getWriter(M_LOGIC_CPP).println("flux::fred *tr;");
					tr_declared = true;
				}
				out.getWriter(M_LOGIC_CPP).println("tr = new flux::fred();");
				out.getWriter(M_LOGIC_CPP).println(
						"tr->create(" + s.getSourceFunction()
								+ "_source_handler," + "NULL);");
			}
		}
	
		out.getWriter(M_LOGIC_CPP).println(
				firstSource.getSourceFunction() + "_source_handler(NULL);");
		out.getWriter(M_LOGIC_CPP).println("}");

		printMain(out);

		epilogue(out);

		out.flushAndClose();
	}

	/**
	 * Main routine
	 **/
	public static void main(String[] args) throws Exception {
		parser p = new parser(new Yylex(new FileInputStream(args[0])));
		Program pm = (Program) p.parse().value;
		if (pm.verifyExpressions()) {
			pm.unifyExpressions();
			ProgramGraph pg = new ProgramGraph(pm);
			generate(args[1], pg);
		}
	}
}
