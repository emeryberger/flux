package edu.umass.cs.flux;

import java.io.PrintWriter;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

/**
 * A concrete program generator which emits event-driven code
 * @author Brendan Burns, Emery Berger
 * @version $version: $
 **/
public class StageGenerator extends AbstractGenerator {
    
    // Determine the maximum stack size needed during execution.
    // FIXME: We should compute this by determining the
    // longest path length through the stages.
    private static int maximumStackSize = 16;
    
    // Should we generate debug messages?
    private static boolean generateDebugInfo = false;
    
    // Indicates whether a symbol is defined or not.
    private static Hashtable<String, Boolean> defined 
        = new Hashtable<String, Boolean>();
    
    // The stage number corresponding to a given name.
    private static Hashtable<String, Integer> stageNumber
        = new Hashtable<String, Integer>();
    
    /**
     * Translate a program into C code.
     * By default, each stage has exactly one thread;
     * we'll be changing this to allow multiple threads
     * when this is legal.
     * @param output Where to write everything
     * @param p The progarm to translate
     **/
    public static void generate(java.io.PrintWriter output, Program p) {
        // We use the stage number as the index for message queuing operations.
        // totalStages = the total number of stages seen to date.
        int totalStages = 0;
        defined.clear();
        stageNumber.clear();
        
        Collection c = p.getFunctions();

        //
        // Output the includes (top of the main file).
        //
        
        String stackType = "flux::runtimeStack<" + maximumStackSize + ", int>";
        String messageType = "flux::message<" + stackType + ", void *>";
    
        output.println("#include <stdlib.h>\n"
                + "#include <sys/wait.h>\n"
                + "#include \"runtimestack.h\"\n"
                + "#include \"message.h\"\n"
                + "#include \"queue.h\"\n"
                + "#include \"timer.h\"\n"
                + "#include \"fred.h\"\n"
                + "#include \"stage.h\"\n");
        
        output.println ("class messageType : public " + messageType + "{\n"
                      + "public:\n"
                      + "  messageType (void * a, " + stackType + "* b) :\n"
                      + "    " + messageType + "(b, a) {}\n"
                      + "};\n");
        
        output.println("\nvoid threadLauncher();");
        
        //
        // Generate the class declarations for communication between stages.
        // 
        
        Iterator it = c.iterator();
        while (it.hasNext()) {
            totalStages++;
            TaskDeclaration td = (TaskDeclaration)it.next();
            stageNumber.put(td.getName(), new Integer(totalStages));
            generateFunction(output, td);
            output.println();
            generateStruct(output, td.getInputs(), td.getName()+"_struct_in");
            output.println();
            generateStruct(output, td.getOutputs(), td.getName()+"_struct_out");
            output.println();
        }
        
        c = p.getTypes();
        it = c.iterator();
        while (it.hasNext()) {
            generateFunction(output, (TypeDeclaration)it.next());
        }
        
        c = p.getFunctions();
        
        
        FlowStatement stmt = p.getMain();
        TaskDeclaration main = p.getTask(stmt.getAssignee());
	Vector<Source> srcs = p.getSources();
        TaskDeclaration kick = p.getTask(srcs.get(0).getSourceFunction());
        defined.put(kick.getName(), Boolean.TRUE);
        
        Vector in = main.getInputs();
        
        output.println("static bool running = true;");
        output.println ("static flux::stageBase * stage[" + (totalStages+1) + "];");
                
        // Generate the main body ("loop").
        // FIXME: Currently the body just runs forever...
        //
        
        output.println("void loop() {\n"
                + "\tthreadLauncher();\n"
                + "\tstage[" + stageNumber.get(kick.getName()) + "]->queueEvent (NULL);\n"
                + "\twhile (running) { // for now, run forever\n"
                + "\t\tsleep(1000);\n"
                + "\t}\n"
                + "}\n");
        
        output.println("class " + kick.getName() + "_implementation {\n"
                    +  "public:\n"
                    +  "  void run (void * datum) {\n"
                    +  "    while (true) {");
        
        if (generateDebugInfo) {
            output.println("\t\tfprintf(stderr, \"Now running " + kick.getName() + ".\\n\");");
        }

        output.println("    " + stmt.getAssignee()+"_struct_in *in = "
                    + " new "+stmt.getAssignee()+"_struct_in();\n"      
                    + "     "+kick.getName()+"(");
                    
        for (int i = 0; i < in.size(); i++) {
            if (i!=0)
                output.print(",");
            output.print("&(in->"+stmt.getAssignee()+"_struct_in_arg_"+i+")");
        }
        output.println(");");
                    
        output.println("\t\tflux::runtimeStack<"
                + maximumStackSize + ", int> * routing = new flux::runtimeStack<"
                + maximumStackSize + ", int>;\n"
                + "\t\trouting->push(-1); // -1 = Done.\n"
                + "\t\t// Send message to " + stmt.getAssignee() + "\n"
                + "\t\tstage[" + stageNumber.get(stmt.getAssignee()) + "]->queueEvent ("
                + "(void *) new messageType (in, routing));\n"
                + "\t}\n"
                + "\t}\n"
                + "};\n");
                    
        String superclass = "flux::stage<" + kick.getName() + "_implementation, ";

/*        if (kick.isReentrant()) {
            superclass += "true, ";
        } else {
            superclass += "false, ";
        }
        
        if (kick.isReplicable()) {
            superclass += "true, ";
        } else {
            superclass += "false, ";
        }*/
        
        superclass += "16>";
        
        output.print ("class " + kick.getName() + "_stage :\n"
                + "  public " + superclass + " {\n");
        output.println ("public:\n"
                + "  " + kick.getName() + "_stage (void)\n"
                + "    : " + superclass + "(\"" + kick.getName() + "\", "
                + "333) {}\n"
                + "};\n");
        
        //output.print("  public flux::stage<");
        
        //if (kick.isReentrant()) {
        //    output.print("true, ");
        //} else {
        //    output.print("false, ");
        //}

        
        Vector exps = p.getExpressions();
        it = exps.iterator();
        while (it.hasNext()) {
            FlowStatement fs =  (FlowStatement)it.next();
            generateStatement(output,fs,p,p.getTask(fs.getAssignee()));
            defined.put(fs.getAssignee(), Boolean.TRUE);
        }
        
        it = c.iterator();
        while (it.hasNext()) {
            TaskDeclaration task = (TaskDeclaration)it.next();
            if (defined.get(task.getName()) == null) {
                generateTask(output, task);
                defined.put(task.getName(), Boolean.TRUE);
            }
        }
        
        output.println();
        
        //
        // Declare the threads.
        //
        
        it = defined.keySet().iterator();
        while (it.hasNext()) {
            String name = (String)it.next();
            output.println("\tstatic flux::fred thread_"+name+";");
        }
        
        //
        // The procedure to launch the threads.
        //
        
        output.println();
        output.println("void threadLauncher() {");
        
        it = defined.keySet().iterator();
        while (it.hasNext()) {
            String name = (String)it.next();
            output.println
            ("\tstage[" + stageNumber.get(name) + "] = new " + name + "_stage();");
            output.println ("\tstage[" + stageNumber.get(name) + "]->start();");
        }
        output.println("}");
        
        defined.remove(kick.getName());
        
    }
    
    /**
     * Generate a statement
     * @param output Where to write
     * @param stmt The statement to translate
     * @param p The program containing the statement
     * @param task The parent task definition for this statement (left-side)
     **/
    public static void generateStatement
    (PrintWriter output, FlowStatement stmt, Program p, TaskDeclaration task) 
    {
        output.println("class "+stmt.getAssignee()+"_implementation {\n"
                    +  "public:\n"
                    +  "  void run (void * datum) {");

        String messageType = "flux::message<flux::runtimeStack<"
                + maximumStackSize
                + ", int>, void *> *";
        
        output.println("\t\t" + messageType + " msg = (" + messageType + ") datum;"
                + " // this is for " + stmt.getAssignee());
        
        //
        // Now we are actually executing something.
        //
        
        if (generateDebugInfo) {
            output.println("\t\tfprintf(stderr, \"Now running "+stmt.getAssignee()+".\\n\");");
        }
        
        output.println("\t\tflux::runtimeStack<"
                + maximumStackSize
                + ", int> * routing = msg->stack;");
        output.println("\t\t"+stmt.getAssignee()+"_struct_in *in = ("
                +stmt.getAssignee()+"_struct_in *)msg->data;");
        
        if (stmt instanceof SimpleFlowStatement) {
            generateStatementBody(output, (SimpleFlowStatement)stmt, task);
        }
        else {
            generateStatementBody(output, (TypedFlowStatement)stmt, p, task);
        }
        
        output.println("\t}");
        output.println("};\n");
        
        String superclass = "flux::stage<" + stmt.getAssignee() + "_implementation, ";

/*        if (task.isReentrant()) {
            superclass += "true, ";
        } else {
            superclass += "false, ";
        }
        
        if (task.isReplicable()) {
            superclass += "true, ";
        } else {
            superclass += "false, ";
        }*/
        
        superclass += "16>";
        
        output.print ("class " + stmt.getAssignee() + "_stage :\n"
                      + "  public " + superclass + " {\n");
        output.println ("public:\n"
                      + "  " + stmt.getAssignee() + "_stage (void)\n"
                      + "    : " + superclass + "(\"" + stmt.getAssignee() + "\", "
                      + "111) {}\n"
                      + "};\n");
    }
    
    /**
     * Generate a simple flow statement body
     * @param output Where to write
     * @param stmt The statement to translate
     * @param task The parent task definition for this statement (left-side)
     **/
    public static void generateStatementBody
    (PrintWriter output, SimpleFlowStatement stmt, TaskDeclaration task) 
    {
        Vector<String> args = stmt.getArguments();
        
        for (int i=args.size()-1;i>0;i--)
            output.println("\t\trouting->push("+stageNumber.get(args.get(i))+"); // " + args.get(i));
        
        String next = args.get(0);
        output.println("\t\t"+next+"_struct_in *nxt= new "+next+"_struct_in(");

        Vector<Argument> ins = task.getInputs();
        for (int i=0;i<ins.size();i++) {
            output.print("in->"+
                    stmt.getAssignee()+"_struct_in_arg_"+i);
            if (i+1 < ins.size()) {
                output.println (",");
            }
        }
        output.println (");");
        output.println("\t\tint nextStage = " + stageNumber.get(args.get(0)) + "; // " + args.get(0));

        output.println ("\t\t// Send message to " + args.get(0) + "\n"
                      + "\t\tstage[nextStage]->queueEvent ("
                      + "(void *) new messageType (nxt, routing));\n");
    }
    
    /**
     * Generate a typed flow statement body
     * @param output Where to write
     * @param stmt The statement to translate
     * @param p The program containing the statement
     * @param task The parent task definition for this statement (left-side)
     **/
    public static void generateStatementBody
    (PrintWriter output, TypedFlowStatement stmt,
            Program p, TaskDeclaration task) 
    {
        Vector<Argument> ins = task.getInputs();
        Vector<String> vars = new Vector<String>();
        for (int i=0; i < ins.size(); i++)
            vars.add("in->"+stmt.getAssignee()+"_struct_in_arg_"+i);
        
        Vector<FlowStatement> stmts = stmt.getFlowStatements();
        Iterator<FlowStatement> it = stmts.iterator();
        
        //
        // Add stats to keep track of which way we've branched.
        //
        
        if (stmts.size() > 1) {
            // There's more than one option.
            output.println ("int branchTaken[" + stmts.size() + "];\n");
        }
        
        int currentBranch = 0;
        while (it.hasNext()) {
            SimpleFlowStatement sfs = (SimpleFlowStatement)it.next();
            generateTypeTests(output, sfs.getTypes(), p, vars);
            output.println("{");
            
            if (stmts.size() > 1) {
                output.println ("branchTaken[" + currentBranch + "]++;\n");
            }
            
            generateStatementBody(output, sfs, p.getTask(sfs.getAssignee()));
            output.println("}");
            currentBranch++;
        }
    }
    
    /**
     * Generate a task
     * @param output Where to write
     * @param task The task
     **/
    public static void generateTask
    (PrintWriter output, TaskDeclaration task)
    {
        output.println("class "+task.getName()+"_implementation {\n"
                    +  "public:\n"
                    +  "  void run (void * datum) {");

        if (generateDebugInfo) {
            output.println("\t\tfprintf(stderr, \"Now running "
                    + task.getName() + ".\\n\");");
            output.println("\t\tfflush (stderr);");
        }
        
        String messageType = "flux::message<flux::runtimeStack<"
            + maximumStackSize
            + ", int>, void *> *";
    
        output.println("\t\t" + messageType + " msg = (" + messageType + ") datum;"
            + " // this is for " + task.getName());
    
        output.println("\t\tflux::runtimeStack<"
                + maximumStackSize + ", int> * routing = msg->stack;");
        output.println("\t\t"+task.getName()+"_struct_in *in = ("
                +task.getName()+"_struct_in *)msg->data;");
        output.println("\t\t"+task.getName()+"_struct_out *out = "+
                " new " + task.getName()+"_struct_out();");
        output.print("\t\t"+task.getName()+"(");

        Vector<Argument> ins = task.getInputs();
        Vector<Argument> outs = task.getOutputs();
        Iterator<Argument> it = ins.iterator();

        int ix = 0;
        while (it.hasNext()) {
            it.next();
            output.print("in->"+task.getName()+"_struct_in_arg_"+(ix++));
            if (it.hasNext() || outs.size() > 0)
                output.print(",");
        }
        it = outs.iterator();
        ix = 0;
        while (it.hasNext()) {
            it.next();
            output.print("&(out->"+task.getName()+"_struct_out_arg_"+(ix++)+")");
            if (it.hasNext())
                output.print(",");
        }
        output.println(");");
        output.println("\t\tdelete in;");
        output.println("\t\tint nextStage = routing->top();");
        output.println("\t\trouting->pop();");
        //output.println("printf(\"Sending to: %s\\n\", foo);");
        output.println ("\t\tif (nextStage != -1)");
        output.println("\t\tstage[nextStage]->queueEvent ("
                     + "(void *) new messageType (out, routing));");
        output.println("\t}");
        output.println("};");

        
        String superclass = "flux::stage<" + task.getName() + "_implementation, ";

/*        if (task.isReentrant()) {
            superclass += "true, ";
        } else {
            superclass += "false, ";
        }
        
        if (task.isReplicable()) {
            superclass += "true, ";
        } else {
            superclass += "false, ";
        }*/
        
        superclass += "16>";
        
        output.print ("class " + task.getName() + "_stage :\n"
                      + "  public " + superclass + " {\n");
        output.println ("public:\n"
                      + "  " + task.getName() + "_stage (void)\n"
                      + "    : " + superclass + "(\"" + task.getName() + "\", "
                      + "222) {}\n"
                      + "};\n");
    
    }
}
