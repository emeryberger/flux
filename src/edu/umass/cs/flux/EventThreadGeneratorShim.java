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
 *  A geneartor that creates a mutli-threaded even-driven runtime
 *  implemented in C programming language
 */
public class EventThreadGeneratorShim extends AbstractGeneratorC {

  
  private static String M_EVENT_CPP = "mEvent.cpp";
  private static String M_IOSHIM_H = "mIOShim.h";
//  private static String M_CONVERT_H = "mConvert.h";
//  private static String M_CONVERT_CPP = "mConvert.cpp";

  //private static ProgramGraph pg;
  //private static Program      pm;
  private static Hashtable<String, Integer> stageNumber;
  private static VirtualDirectory out;
  
  private static int totalStages;

  protected static int threadPoolSize = 4;

  private static int lock_id = 0;

  /* Helper functions */
  public static void setThreadPoolSize(int size) 
  {
    threadPoolSize = size;
  }

  public static void printLocksInit(Collection<AtomicDeclaration> decs,
                                   VirtualDirectory out)
  {
    if (decs.size() < 1) {
      out.getWriter(M_LOGIC_CPP).println("void initLocks() {}\n");
      return;
    }

    out.getWriter(M_LOGIC_CPP).println("#include <vector>");
    out.getWriter(M_LOGIC_CPP).println("#include \"rwlock.h\""); // Q: orig <>, I changed it.
    out.getWriter(M_LOGIC_CPP).println("std::vector<flux::rwlock *> locks;");
    out.getWriter(M_LOGIC_CPP).println("void initLocks() {");
    for (AtomicDeclaration ad : decs) {
      Vector<Lock> lks = ad.getLocks();
      for (Lock l : lks) {
        if (locks.get(l) == null) {
          out.getWriter(M_LOGIC_CPP).println("\tlocks.push_back(new flux::rwlock());");
          locks.put(l, new Integer(lock_id++));
        }
      }
    }
    out.getWriter(M_LOGIC_CPP).println("}\n");
  }

  public static void printSessionObject(Collection<AtomicDeclaration> decs,
                                        VirtualDirectory out) 
  {
    out.getWriter(M_STRUCT_H).println("#include \"rwlock.h\"\n");
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
    out.getWriter(M_STRUCT_H).println("};\n");
  }

  public static void prologue(ProgramGraph g, VirtualDirectory out)
  {
    AbstractGeneratorC.prologue(g, out);
    printLocksInit(g.getProgram().getAtomicDeclarations(), out);
    printSessionObject(g.getProgram().getAtomicDeclarations(),out);

//    out.getWriter(M_CONVERT_CPP).println("#include \"" + M_STRUCT_H +"\"\n"
//            + "#include <stdio.h>\n"
//            + "#include <stdlib.h>\n");

    out.getWriter(M_LOGIC_CPP).println("#include <unistd.h>\n" 
            + "#include <signal.h>\n"
            + "#include <dlfcn.h>\n"
            + "#include <list>\n"
            + "#include <map>\n\n");

    out.getWriter(M_LOGIC_CPP).println("#include \"" + M_IMPL_H + "\"\n"
//            + "#include \"" + M_CONVERT_H + "\"\n"
//            + "#include \"functionclosure.h\"\n"
            + "#include \"runtimestack.h\"\n"
            + "#include \"eventthreadpool.h\"\n"     
            + "#include \"messagequeue.h\"\n"   
            + "#include \"queue.h\"\n"
            + "#include \"event.h\"\n"
            + "#include \"timer.h\"\n"
            + "#include \"logger.h\"\n"
            + "#include \"graphClient.h\"\n"
            + "#include \"" + M_IOSHIM_H + "\"\n"
            + "#include \"fred.h\"\n");

    out.getWriter(M_LOGIC_CPP).println();
  }

  public static void printShimHeader() {
    out.getWriter(M_IOSHIM_H).println("#ifndef _MSHIM_H_");
    out.getWriter(M_IOSHIM_H).println("#define _MSHIM_H_\n");

    out.getWriter(M_IOSHIM_H).println("typedef struct {");
    out.getWriter(M_IOSHIM_H).println("\tint threads_waiting_to_run[" 
            + threadPoolSize + "];");
    out.getWriter(M_IOSHIM_H).println("\tint threads_waiting_in_pool[" 
            + threadPoolSize + "];");
    out.getWriter(M_IOSHIM_H).println("\tpthread_mutex_t *mutex_event_mgr[" 
            + threadPoolSize + "];");
    out.getWriter(M_IOSHIM_H).println("\tpthread_cond_t *cond_waiting_to_run[" 
            + threadPoolSize + "];");
    out.getWriter(M_IOSHIM_H).println("\tpthread_cond_t *cond_waiting_in_pool[" 
            + threadPoolSize + "];");
    out.getWriter(M_IOSHIM_H).println("\tbool *running;");
    out.getWriter(M_IOSHIM_H).println("\tvoid *(*event_mgr_fn)(void *);");
    out.getWriter(M_IOSHIM_H).println("} eventmgrinfo_t;\n");

    out.getWriter(M_IOSHIM_H).println("typedef struct { // args for event_handler()");
    out.getWriter(M_IOSHIM_H).println("\tbool *running;");
    out.getWriter(M_IOSHIM_H).println("\tbool bootstrap;");
    out.getWriter(M_IOSHIM_H).println("\tint currentQueueIndex;");
    out.getWriter(M_IOSHIM_H).println("}event_handler_args_t;\n");

    out.getWriter(M_IOSHIM_H).println("#endif // _MSHIM_H_");

  }

  public static void printDeclarationsLogic()
  {
    printShimHeader();
    
    out.getWriter(M_LOGIC_CPP).println("/*====== Variables and Structures ======*/");

    out.getWriter(M_LOGIC_CPP).println("static eventmgrinfo_t mgrinfo;");
    out.getWriter(M_LOGIC_CPP).println("typedef void(*initShimFnType)(eventmgrinfo_t *);");

    out.getWriter(M_LOGIC_CPP).println("void *event_handler(void *); // forward declaration\n");
    out.getWriter(M_LOGIC_CPP).println("static bool running = true;");
    out.getWriter(M_LOGIC_CPP).println("static event_handler_args_t init_arg = {&running, true, -1};");
    out.getWriter(M_LOGIC_CPP).println("static flux::eventpool < " +
            + threadPoolSize + " > eventPool;");
//    out.getWriter(M_LOGIC_CPP).println("static flux::eventthreadpool < " +
//            + threadPoolSize + " > eventThreadPool(event_handler, (void *)&init_arg);");
    out.getWriter(M_LOGIC_CPP).println("static flux::eventthreadpool < " +
            + threadPoolSize + " > *eventThreadPool;");
    out.getWriter(M_LOGIC_CPP).println("int __thread queueIndex = -1;");
    out.getWriter(M_LOGIC_CPP).println("static int queueAssign = 0;");
    out.getWriter(M_LOGIC_CPP).println();
  }

  public static void printHeaderFiles() 
  {
    Collection fns = programGraph.getFunctions();
    Iterator it = fns.iterator();

    while (it.hasNext()) {
      TaskDeclaration td = (TaskDeclaration)it.next();
      printStructs(td, out.getWriter(M_STRUCT_H));
      programGraph.getStageNumber(td.getName());
      if (program.getFlow(td.getName()) == null) 
        printSignature(td, out);
    }
  }

  public static void generateSimpleStatement(TaskDeclaration td, SimpleFlowStatement sfs) 
  {
    Vector<String> args = sfs.getArguments();
    
    for (int i = args.size() - 1; i >= 0; i--) {
      int stg = programGraph.getStageNumber(args.get(i));
      out.getWriter(M_LOGIC_CPP).println("\t\tev->push(" + stg + "); // " + args.get(i));
    }
  }

  public static void printTypeTests(Vector<String> types, Vector<Argument> inputs)
  {
    out.getWriter(M_LOGIC_CPP).print("if ((1)");
    for  (int i = 0; i < types.size(); i++) {
      String typeName = types.get(i);
      Argument arg    = inputs.get(i);

      if (!typeName.equals("*")) {
        TypeDeclaration typed = program.getType(typeName);
        out.getWriter(M_LOGIC_CPP).print(" && ("
                + typed.getFunction() + "(in->"
                + arg.getName() + ")) ");

        // prototypes
        out.getWriter(M_IMPL_H).println("bool "
                + typed.getFunction() + "("
                + arg.getType() + " " + arg.getName() + ");");
        out.getWriter(M_IMPL_CPP).println("bool "
                + typed.getFunction() + "("
                + arg.getType() + " " + arg.getName() + ") {\n}\n");
      }
    }
    out.getWriter(M_LOGIC_CPP).println(")");
  }

  public static void generateTypedStatement(TaskDeclaration td, TypedFlowStatement tfs)
  {
      // Q: always have input ???
      //out.getWriter(M_LOGIC_CPP).println(td.getName() + "_in * "
      //        + td.getName() + "_var_in = ("
      //        + td.getName() + "_in *) ev->in;");
      Vector<FlowStatement> stmts = tfs.getFlowStatements();
      Iterator<FlowStatement> it = stmts.iterator();
      while (it.hasNext()) {
        // Q: must be SimpleFlowStatement? 
        SimpleFlowStatement sfs = (SimpleFlowStatement)it.next();
        printTypeTests(sfs.getTypes(), td.getInputs());
        out.getWriter(M_LOGIC_CPP).println("{");
        generateSimpleStatement(td, sfs);
        out.getWriter(M_LOGIC_CPP).println("}");
      }
  }

  public static void generateStatement(TaskDeclaration td, FlowStatement fs) 
  {
    out.getWriter(M_LOGIC_CPP).println("inline int " + td.getName() + "("
            + (td.getInputs().size() > 0 ? td.getName() + "_in *in, " : "")
            + (td.getOutputs().size() > 0 ? td.getName() + "_out *out, " : "")
            + "flux::event *ev) {");

    if (fs instanceof SimpleFlowStatement) {
      generateSimpleStatement(td, (SimpleFlowStatement)fs);
    }
    else {
      generateTypedStatement(td, (TypedFlowStatement)fs);
    }
    out.getWriter(M_LOGIC_CPP).println("return 0;");
    out.getWriter(M_LOGIC_CPP).println("}\n");
  }

  public static void printNodeFunctions() 
  {

    Collection fns = programGraph.getFunctions();
    Iterator it = fns.iterator();

    while (it.hasNext()) {
      TaskDeclaration td = (TaskDeclaration)it.next();

      if (td.getIsSrc()) // ignore source nodes, handled specially
        continue;

      FlowStatement fs = program.getFlow(td.getName());
      if (fs != null) {
        generateStatement(td, fs);
      }
    }
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

  public static void printEventHandler()
  {
    PrintWriter pw = out.getWriter(M_LOGIC_CPP); 

    // event processor that does the real job
    pw.println("void *event_processor(flux::event *ev, flux::queue<flux::event *> *messageQ) {");
    pw.println("\tswitch (ev->type) {");

    Collection fns = programGraph.getFunctions();
    Iterator it = fns.iterator();
    while (it.hasNext()) 
    {
      TaskDeclaration td = (TaskDeclaration)it.next();
      if (td.getIsSrc()) // ignore source nodes, handled specially
        continue;

      int stg = programGraph.getStageNumber(td.getName());
      pw.println("\tcase " + stg + ": //" + td.getName());
      pw.println("\t{");

      // prepare input and output structures
      if (td.getInputs().size() > 0)
        pw.println("\t\t" + td.getName() + "_in *inptr = ("
                + td.getName() + "_in *)ev->in;");
      if (td.getOutputs().size() > 0)         // TY: changed
        pw.println("\t\t" + td.getName() + "_out *outptr = new "
                + td.getName() + "_out;");
        //pw.println("\t\t" + td.getName() + "_out"
        //        + td.getName() + "_var_out;");

      // call the function
      printLock("ptr->_mSession", program.getAtomicDeclaration(td.getName()), out);
      if (program.getFlow(td.getName()) != null)
        pw.println("\t\tint err = " + td.getName() + "("
                + (td.getInputs().size() > 0 ? "inptr, " : "")
                + (td.getOutputs().size() > 0 ? "outptr, " : "")
                + "ev);");
      else
        pw.println("\t\tint err = " + td.getName() + "("
                + (td.getInputs().size() > 0 ? "inptr" : "")
                + (td.getOutputs().size() > 0 ? 
                  (td.getInputs().size() > 0 ? ", outptr": "outptr") : "")
                + ");");
      printUnlock("ptr->_mSession", program.getAtomicDeclaration(td.getName()), out);

      // error handler
      pw.println("\t\tif (err) {");
      int pathWeight = printErrorHandler(td, "inptr", program, out);
      if (td.getInputs().size() > 0)
        pw.println("\t\t\tdelete inptr;");
      if (td.getOutputs().size() > 0) // TY: changed
        pw.println("\t\t\tdelete outptr;");
      pw.println("\t\t\tdelete ev;");

      pw.println("\t\t\tbreak;");
      pw.println("\t\t}\n");

      // convert to next event
      if (program.getFlow(td.getName()) != null) {
        // an abstract node, containing one or more concrete node
        pw.println("\t\tint current_event_type = ev->type;"); 
        pw.println("\t\tint new_event_type = ev->pop();"); 
        pw.println("\t\tev->type = new_event_type;"); 
        if (td.getInputs().size() > 0) {
          // input of an abstract node is the input of its first node
          //pw.println("\t\tev->in = convert(ptr, ev->type);"); 
          pw.println("\t\tev->in = (void *)inptr;"); 
          // convert the input of the abstract node into the input of it first node
          // doInputConvert(td);  
        } else {
          pw.println("\t\tev->in = NULL;");
        }
        pw.println("\t\tmessageQ->fifo_push(ev);");
      }
      else if (td.getOutputs().size() > 0 ) {
        // a concrete node that has outputs, which implies it has a follower
        pw.println("\t\tint current_event_type = ev->type;"); 
        pw.println("\t\tint new_event_type = ev->pop();"); 
        pw.println("\t\tev->type = new_event_type;"); 
        pw.println("\t\tev->in = (void *)outptr;"); 
        // convert my outpur into the input of the next node
        //doOutputConvert(td);
        pw.println("\t\tmessageQ->fifo_push(ev);");

        if (td.getInputs().size() > 0) // has inputs, free it 
          pw.println("\t\tdelete inptr;");
      }
      else {
        // a concrete node has no outputs: the end of the flow, going towards exit!
        if (td.getInputs().size() > 0) // has inputs, free it 
          pw.println("\t\tdelete inptr;");
        pw.println("\t\tdelete ev;");
      }
       

      pw.println("\t\tbreak;");
      pw.println("\t}");
    }
    pw.println("\tdefault:");
    pw.println("\t\tfprintf(stderr, \"Unkown event: %d\\n\", ev->type);");
    pw.println("\t}");
    pw.println("}\n");

    // event handler in a loop that picks an event from local event queue,
    // and then invokes the event processor to process the selected event,
    pw.println("void *event_handler(void *arg) {");
    pw.println("\tevent_handler_args_t *inputs = (event_handler_args_t *)arg;");
    pw.println("\tflux::event *ev;\n");
    pw.println("\tif (inputs->currentQueueIndex == -1) {");
    pw.println("\t\tqueueIndex = queueAssign;");
    pw.println("\t\tqueueAssign++;");
    pw.println("\t}");
    pw.println("\telse {");
    pw.println("\t\tqueueIndex = inputs->currentQueueIndex;");
    pw.println("\t}\n");

    pw.println("\tflux::queue<flux::event *> *eventQ = eventPool.getEventQueue(queueIndex);\n");

    pw.println("\tpthread_mutex_lock(mgrinfo.mutex_event_mgr[queueIndex]);");
    pw.println("\tif (!inputs->bootstrap) {");
    pw.println("\t\tmgrinfo.threads_waiting_in_pool[queueIndex]++;");
    pw.println("\t\tpthread_cond_wait(mgrinfo.cond_waiting_in_pool[queueIndex], mgrinfo.mutex_event_mgr[queueIndex]);");
    pw.println("\t\tmgrinfo.threads_waiting_in_pool[queueIndex]--;");
    pw.println("\t}\n");

    pw.println("\twhile(1) {");
    pw.println("\t\tpthread_mutex_unlock(mgrinfo.mutex_event_mgr[queueIndex]);");
    pw.println("\t\tpthread_mutex_lock(mgrinfo.mutex_event_mgr[queueIndex]);\n");

    pw.println("\t\tif (mgrinfo.threads_waiting_to_run[queueIndex] > 0) {");
    pw.println("\t\t\tpthread_cond_signal(mgrinfo.cond_waiting_to_run[queueIndex]);");
    pw.println("\t\t\tmgrinfo.threads_waiting_in_pool[queueIndex]++;");
    pw.println("\t\t\tpthread_cond_wait(mgrinfo.cond_waiting_in_pool[queueIndex], mgrinfo.mutex_event_mgr[queueIndex]);");
    pw.println("\t\t\tmgrinfo.threads_waiting_in_pool[queueIndex]--;");
    pw.println("\t\t}\n");

    pw.println("\t\tev = eventQ->lifo_pop();");
    pw.println("\t\tevent_processor(ev, eventQ);");
    pw.println("\t}");
    pw.println("}\n");
  }

  public static void generateTaskCall(TaskDeclaration td, String in, 
                                      String outp, boolean cont)
  {
    printLock( "(" + in + ")->_mSession",
              program.getAtomicDeclaration(td.getName()), out);
    out.getWriter(M_LOGIC_CPP).println("err = " + td.getName() + "("
            + (in != null ? in : "" )
            + (outp != null ?  (in != null ? ", " + outp : outp) : "")
            + ");");
    printUnlock("(" + in + ")->_mSession",
              program.getAtomicDeclaration(td.getName()), out);

    out.getWriter(M_LOGIC_CPP).println("if (err != 0) {");
    int path_weight = printErrorHandler(td, "(" + in + ")", program, out);
    if (cont)
      out.getWriter(M_LOGIC_CPP).println("continue;");
    else
      out.getWriter(M_LOGIC_CPP).println("return -1;");
    
    out.getWriter(M_LOGIC_CPP).println("}");
  }

  public static void generateSimpleFlowBody(Vector<String> nodeNames,
                                            TaskDeclaration compound) 
  {
    // first node uses 'in' as input, and others uses the
    // output of previous node
    String lastNodeOut = null; 
    if (compound.getInputs().size() > 0) {
      lastNodeOut = "in";
    }

    out.getWriter(M_LOGIC_CPP).println("int err;");
    for (int i = 0; i < (nodeNames.size() -1); i++) {
      TaskDeclaration node = program.getTask(nodeNames.get(i));

      if (node == null) {
        System.err.println("node not found: " + nodeNames.get(i));
        System.exit(1);
      }

      out.getWriter(M_LOGIC_CPP).println(node.getName()+ "_out "
              + node.getName() + "_var_out;");
      //if (lastNode != null) {
      //  out.getWriter(M_LOGIC_CPP).println(node.getName()
      //          + "_var_out._mSession = in->_mSession;");
      //}
      generateTaskCall(node, (lastNodeOut != null ? 
                      "(" + node.getName() +"_in *)" + lastNodeOut : lastNodeOut), 
                      "&"+ node.getName() + "_var_out", false);

      lastNodeOut = "&" + node.getName() + "_var_out";
    }

    // last not uses 'out' as output
    TaskDeclaration node = program.getTask(nodeNames.lastElement());
    if (node == null) {
      System.err.println("node not found: " + nodeNames.lastElement());
      System.exit(1);
    }
    if (compound.getOutputs().size() > 0)
      generateTaskCall(node, (lastNodeOut != null ? 
                      "(" + node.getName() + "_in *)" + lastNodeOut : lastNodeOut), 
                      "(" + node.getName() + "_out *)out", false);
    else 
      generateTaskCall(node, (lastNodeOut != null ? 
                      "(" + node.getName() + "_in *)" + lastNodeOut : lastNodeOut),
                       null, false);

    // success and return
    out.getWriter(M_LOGIC_CPP).print("return 0;");

  }

  public static void generateFromSimpleFlow(SimpleFlowStatement sfs)
  {
    TaskDeclaration compound = program.getTask(sfs.getAssignee());
    Vector<String> nodeNames = sfs.getArguments();

    // generate code for all of its components, if it is a compound node
    for (Iterator<String> it = nodeNames.iterator(); it.hasNext(); ) {
      generateTask(program.getTask(it.next()));
    }

    // generate function for the flow
    out.getWriter(M_LOGIC_CPP).println("int " + compound.getName() + "("
                + (compound.getInputs().size() > 0 ? compound.getName() + "_in *in" : "")
                + (compound.getOutputs().size() > 0 ? 
                  (compound.getInputs().size() > 0 ? ", " + compound.getName() + "_out *out" :
                   compound.getName() + "_out *out") : "")
                + ") {");

    generateSimpleFlowBody(nodeNames, compound);

    out.getWriter(M_LOGIC_CPP).print("}\n");
  }

  public static void generateOneBranch(SimpleFlowStatement sfs)
  {
    Vector<String> types = sfs.getTypes();
    Vector<String> nodeNames = sfs.getArguments();
    TaskDeclaration compound = program.getTask(sfs.getAssignee());
    Vector<Argument> inputs = compound.getInputs();

    printTypeTests(types, inputs);
    out.getWriter(M_LOGIC_CPP).println("{");
    generateSimpleFlowBody(nodeNames, compound);
    out.getWriter(M_LOGIC_CPP).println("}\n");
  }

  public static void generateFromTypedFlow(TypedFlowStatement tfs)
  {
    TaskDeclaration compound = program.getTask(tfs.getAssignee());
    Vector<FlowStatement> cases = tfs.getFlowStatements();

    // generate all its cases and components
    for (Iterator<FlowStatement> fi= cases.iterator(); fi.hasNext(); ) {
      SimpleFlowStatement sfs = (SimpleFlowStatement)fi.next();
      for (Iterator<String> ti = sfs.getArguments().iterator(); ti.hasNext(); ) {
        generateTask(program.getTask(ti.next()));
      }
    }

    // generate function for the flow
    out.getWriter(M_LOGIC_CPP).println("int " + compound.getName() + "("
                + (compound.getInputs().size() > 0 ? compound.getName() + "_in *in" : "")
                + (compound.getOutputs().size() > 0 ? 
                  (compound.getInputs().size() > 0 ? ", " + compound.getName() + "_out *out" :
                   compound.getName() + "_out *out") : "")
                + ") {");

    // generate branches
    out.getWriter(M_LOGIC_CPP).println("int err;");
    for (int i = 0; i < cases.size(); i++) {
      if (i != 0) out.getWriter(M_LOGIC_CPP).print("else ");
      SimpleFlowStatement sf = (SimpleFlowStatement) cases.get(i);
      generateOneBranch(sf);
    }
    
    // success and return
    out.getWriter(M_LOGIC_CPP).print("return 0;\n}\n");
    
  }

  public static void generateTask(TaskDeclaration td) 
  {
    FlowStatement stmt = program.getFlow(td.getName());

    if (stmt == null) return;

    if (stmt instanceof SimpleFlowStatement)
      generateFromSimpleFlow((SimpleFlowStatement)stmt);
    else
      generateFromTypedFlow((TypedFlowStatement)stmt);
  }

  public static void printSourceHandlers() 
  {
    PrintWriter pw = out.getWriter(M_LOGIC_CPP);

    Collection<Source> sources = programGraph.getSources();
    for (Source s: sources)  {

      generateTask(program.getTask(s.getSourceFunction()));
      // print the loop for source node
      pw.println("void *" + s.getSourceFunction()
              + "_source_handler(void *in) {");
      pw.println("\twhile (running) {");

      //print a call
      TaskDeclaration source = program.getTask(s.getSourceFunction());
      // declare out variable in stack 
      out.getWriter(M_LOGIC_CPP).println(source.getName() + "_out *out = new "
              + source.getName() + "_out;");
      out.getWriter(M_LOGIC_CPP).println("int err;");
      generateTaskCall(source, null, 
              (source.getOutputs().size() > 0 ? "out": null), true);
      
      //add an new event
      // success, then copy the out, and then pass to event handler.
      
      TaskDeclaration destination = program.getTask(s.getTarget());
      out.getWriter(M_LOGIC_CPP).println("flux::event *ev = new flux::event();");
      int stg = programGraph.getStageNumber(destination.getName());
      out.getWriter(M_LOGIC_CPP).println("ev->type = " + stg + ";");
      out.getWriter(M_LOGIC_CPP).println("ev->in = (void *)out;");
      out.getWriter(M_LOGIC_CPP).println("eventPool.add_event(ev);");
      
      pw.println("\t}");
      pw.println("}");
    }

    // create one thread for each source node
    pw.println("void startSources() {"); 
    pw.println("\trunning = true;");
    pw.println("\tint count = 0;");

    Source firstSource = sources.iterator().next();
    //pw.println("\tflux::fred *tr;");
    Boolean tr_declared = false;
    for (Source s: sources) {
      if ( s != firstSource) {
        if (!tr_declared) {
          pw.println("\tflux::fred *tr;");
          tr_declared = true;
        }
        pw.println("\ttr = new flux::fred();");
        pw.println("\ttr->create(" + s.getSourceFunction()
                + "_source_handler, NULL);");
      }
    }

    // start the logging thread here if necessary

    // start the firstSource, which runs on the main thread 
    pw.println("\t" + firstSource.getSourceFunction() + "_source_handler(NULL);"); 
    pw.println("}\n");

  }

  public static void printMain()
  {
    PrintWriter pw = out.getWriter(M_LOGIC_CPP);

    pw.println("int main(int argc, char **argv)");
    pw.println("{");
    pw.println("\tinitLocks();\n");

    pw.println("\tmgrinfo.running = &running;");
    pw.println("\tmgrinfo.event_mgr_fn = &event_handler;");
    pw.println("\tfor (int i = 0; i < " + threadPoolSize + "; i++) {");
    pw.println("\t\tmgrinfo.threads_waiting_to_run[i] = 0;");
    pw.println("\t\tmgrinfo.threads_waiting_in_pool[i] = 0;");
    pw.println("\t\tmgrinfo.mutex_event_mgr[i] = new pthread_mutex_t;");
    pw.println("\t\tmgrinfo.cond_waiting_to_run[i] = new pthread_cond_t;");
    pw.println("\t\tmgrinfo.cond_waiting_in_pool[i] = new pthread_cond_t;");
    pw.println("\t\tpthread_mutex_init(mgrinfo.mutex_event_mgr[i], NULL);");
    pw.println("\t\tpthread_cond_init(mgrinfo.cond_waiting_to_run[i], NULL);");
    pw.println("\t\tpthread_cond_init(mgrinfo.cond_waiting_in_pool[i], NULL);");
    pw.println("\t}\n");

    pw.println("\teventThreadPool = new flux::eventthreadpool < "
            + threadPoolSize + " > (event_handler, (void *)&init_arg, &eventPool);\n");

    pw.println("\tinitShimFnType f = (initShimFnType)dlsym(RTLD_NEXT, \"initShim\");");
    pw.println("\tif ( f == NULL ) {");
    pw.println("\t\tprintf(\"ERROR: no SHIM file found ... exiting\\n\");");
    pw.println("\t\texit(0);");
    pw.println("\t}");
    pw.println("((f)(&mgrinfo));");
    
    pw.println("\tinit(argc, argv);\n");
    pw.println("\tstartSources();\n");
    pw.println("}");
  }

	public static void generate(String root, boolean logging, 
                              ProgramGraph graph) 
                              throws IOException 
  {
      loggingOn = logging;
      programGraph = graph;
      program = programGraph.getProgram();
      stageNumber = programGraph.getStageHashtable();
      totalStages = stageNumber.size();
      out = new VirtualDirectory(root);

      generate();
  }

	public static void generate() throws IOException 
  {
    M_LOGIC_CPP = M_EVENT_CPP;

    /* print out prologue */
    prologue(programGraph, out);  // check

    /* print out structures variables needed in M_LOGIC_CPP */
    printDeclarationsLogic(); // check

    /* print structures and signatures in .h file M_STRUCT_H and M_IMPL_H */ 
    printHeaderFiles();   // check

    /* print Node functions in M_LOGIC_CPP */
    printNodeFunctions();  // check

    /* print Event Handler in M_LOGIC_CPP */
    printEventHandler(); // check

    /* print Source functions in M_LOGIC_CPP */
    printSourceHandlers(); // check

    /* print Main() */
    printMain();

    /* print epilogue */
    epilogue(out);

    out.flushAndClose();

  }
}
