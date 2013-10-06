package edu.umass.cs.flux;

import java.io.IOException;
import java.io.FileInputStream;

import java.util.Collection;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

/**
 * A generator for a thread-based runtime in the Java programming language
 * <br><b>Experimental</b>
 * @author Brendan Burns
 **/
public class JavaThreadGenerator extends AbstractGeneratorJava {
    // The simulated call stack for tasks
    static Stack<TaskDeclaration> outputStack = new Stack<TaskDeclaration>();
    // Is the input to the function the input, or the previous output?
    static Stack<Boolean> inSelectStack = new Stack<Boolean>();
    static int pool_size = 20;
    static boolean thread_pool = true;
    static String IMPL_CLASS = "mImpl";


    public static void setThreadPoolSize(int size) {
	pool_size = size;
    }

    public static void setUseThreadPool(boolean pool) {
	thread_pool = pool;
    }

     /**
     * Print a task (function call)
     * @param name The name of the function
     * @param p The program
     * @param out Where to write
     **/
    protected static void printTask(String name, Program p, 
				    VirtualDirectory out) {
	TaskDeclaration td = outputStack.pop();
	Boolean inSelect = inSelectStack.pop();
	String prefix;
	String out_str;
	Vector<Argument> args;
	
	if (inSelect.booleanValue()) {
	    prefix = td.getName()+"_var_in";
	    args = td.getInputs();
	}
	else {
	    prefix = td.getName()+"_var_out";
	    args = td.getOutputs();
	}
	 
	TaskDeclaration fn = p.getTask(name);
	Vector<Argument> ins = fn.getInputs();
	
	out.getWriter(M_LOGIC_JAVA).println(fn.getName()+"_in "+
					    fn.getName()+"_var_in = new "+
					    fn.getName()+"_in();");
	
	if (fn.getOutputs().size() > 0) {
	    out_str = ", "+fn.getName()+"_var_out";
	    out.getWriter(M_LOGIC_JAVA).println(fn.getName()+"_out "+
						fn.getName()+"_var_out = new "+
						fn.getName()+"_out();");
	}
	else
	    out_str = "";
	
	for (int i=0;i<args.size();i++)
	    out.getWriter(M_LOGIC_JAVA).println(fn.getName()+"_var_in."+
						ins.get(i).getName()+" = "+
						prefix+"."+
						args.get(i).getName()+";");
	
	out.getWriter(M_LOGIC_JAVA).println("err = impl_interface."+
					    name+"("+fn.getName()+"_var_in"+
					    out_str+");");
	out.getWriter(M_LOGIC_JAVA).println("if (err!=0) {");
	printErrorHandler(fn, p, out);
	out.getWriter(M_LOGIC_JAVA).println("return;");
	out.getWriter(M_LOGIC_JAVA).println("}");
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
					     Program p,
					     VirtualDirectory out)
    {
	Vector args = sfs.getArguments();
        for (int i=0;i<args.size();i++) {
            printFlowRecursive((String)args.get(i), p, out);
        }
    }

    /**
     * Print a typed flow statement
     * @param tfs The statement
     * @param p The program
     * @param out Where to write
     **/
    protected static void printFlowRecursive(TypedFlowStatement tfs,
					     Program p,
					     VirtualDirectory out)
    {
	Vector sts = tfs.getFlowStatements();
	TaskDeclaration type = p.getTask(tfs.getAssignee());
        
	TaskDeclaration save = outputStack.pop();
	Boolean selectSave = inSelectStack.pop();
	
	out.getWriter(M_LOGIC_JAVA).println
	    (type.getName()+"_out "+type.getName()+"_var_out = new "+
	     type.getName()+"_out();");

	for (int i=0;i<sts.size();i++) {
	    outputStack.push(save);
	    inSelectStack.push(selectSave);
	    
	    SimpleFlowStatement fs = (SimpleFlowStatement)sts.get(i);
            
            if (i!=0)
		out.getWriter(M_LOGIC_JAVA).print("else ");
	    generateTypeTests(fs.getTypes(), p, save, selectSave, out);
            out.getWriter(M_LOGIC_JAVA).println("{");
            printFlowRecursive(fs, p, out);
	    
	    Vector<Argument> outs = type.getOutputs();
	    TaskDeclaration last = outputStack.pop();
	    inSelectStack.pop();
	    Vector<Argument> lOuts = last.getOutputs();
	    for (int j=0;j<outs.size();j++) {
		out.getWriter(M_LOGIC_JAVA).println
		    (type.getName()+"_var_out."+outs.get(j).getName()+" = "+
		     last.getName()+"_var_out."+lOuts.get(j).getName()+";");
	    }

	    out.getWriter(M_LOGIC_JAVA).println("}");
        }
        outputStack.push(p.getTask(tfs.getAssignee()));
	inSelectStack.push(Boolean.FALSE);
    }
    
    /**
     * Recursively evaluate the call stack and print a flow statement
     * @param name The name of the statement
     * @param g The program graph
     * @param out The output directory
     **/
    protected static void printFlowRecursive(String name, 
					     ProgramGraph g,
					     VirtualDirectory out) 
    {
	printFlowRecursive(name, g.getProgram(), out);
    }

    /**
     * Recursively evaluate the call stack and print a flow statement
     * @param name The name of the statement
     * @param p The program
     * @param out The output directory
     **/
    protected static void printFlowRecursive(String name,
					     Program p,
					     VirtualDirectory out)
    {
	FlowStatement stmt = p.getFlow(name);
        if (stmt!=null) {
            if (stmt instanceof SimpleFlowStatement)
                printFlowRecursive((SimpleFlowStatement)stmt, p, out);
            else
                printFlowRecursive((TypedFlowStatement)stmt,  p, out);
        }
        else
            printTask(name, p, out);
    }

     /**
     * Print one thread handler function
     * @param src The name of the source
     * @param td The first task to call
     * @param g The program graph
     * @param out Where to writer
     **/
    protected static void printThreadFn(String src, TaskDeclaration td, 
					ProgramGraph g, VirtualDirectory out) 
    {
	out.getWriter(M_LOGIC_JAVA).println
	    ("public class "+td.getName()+"_Handler implements Runnable {");
	out.getWriter(M_LOGIC_JAVA).println("public "+td.getName()+"_in in;");
	out.getWriter(M_LOGIC_JAVA).println("public "+td.getName()+"_Handler("+
					    td.getName()+"_in in) {");
	out.getWriter(M_LOGIC_JAVA).println("this.in = in;}");
	out.getWriter(M_LOGIC_JAVA).println("public void run() {");
	out.getWriter(M_LOGIC_JAVA).println
	    (td.getName()+"_in "+td.getName()+"_var_in = in;");
	out.getWriter(M_LOGIC_JAVA).println("int err = 0;");
	
	outputStack.push(td);
	inSelectStack.push(Boolean.TRUE);
	
	printFlowRecursive(td.getName(), g, out);
	out.getWriter(M_LOGIC_JAVA).println("}");
	out.getWriter(M_LOGIC_JAVA).println("}");
    }

    /**
     * Prologue output
     * @param out The directory to hold files
     **/
    protected static void prologue(VirtualDirectory out) {
	AbstractGeneratorJava.prologue(out);
	printClassHeader(false, "mThread", out.getWriter(M_LOGIC_JAVA));
	out.getWriter(M_LOGIC_JAVA).println
	    ("mImplInterface impl_interface = null;");
    }

     /**
     * Epilogue output
     * @param out The directory to hold files
     **/
    protected static void epilogue(VirtualDirectory out) {
	AbstractGeneratorJava.epilogue(out);
	out.getWriter(M_LOGIC_JAVA).println("}");
    }


    /**
     * Print the call to a source
     * @param s The source
     * @param p The program
     * @param out Where to write
     **/
    public static void printSourceCall
	(Source s, Program p, VirtualDirectory out)
    {
	out.getWriter(M_LOGIC_JAVA).println
	    ("Vector<"+s.getTarget()+"_in> result_"+s.getTarget()+
	     " = impl_interface."+s.getSourceFunction()+"();");
	out.getWriter(M_LOGIC_JAVA).println
	    ("for(int i=0;i<result_"+s.getTarget()+".size();i++) {");
	if (thread_pool) {
	    out.getWriter(M_LOGIC_JAVA).println
		("threadpool.queueTask(new "+s.getTarget()+"_Handler("+
		 "result_"+s.getTarget()+".get(i)));");
	}
	else {
	    out.getWriter(M_LOGIC_JAVA).println
		("new Thread(new "+s.getTarget()+"_Handler(result_"+
		 s.getTarget()+".get(i))).start();");
	}
	out.getWriter(M_LOGIC_JAVA).println("}");
    }

    /**
     * Generate a threaded program
     * @param root The root directory to output into
     * @param g The program graph
     **/
    public static void generate(String root, ProgramGraph g) 
	throws IOException
    {
	M_LOGIC_JAVA = "mThread.java";

	VirtualDirectory out = new VirtualDirectory(root);

	prologue(out);
	
	// Print the structs and the sigs for all tasks
	Collection fns = g.getFunctions();
	Iterator it = fns.iterator();
	Program p = g.getProgram();

	while (it.hasNext()) {
	    TaskDeclaration td = (TaskDeclaration)it.next();
	    printStructs(td, out);
	    if (p.getFlow(td.getName()) == null && !p.isSource(td))
		printSignature(td, out);
	}
    
	// Print the sigs for all sources
	Collection<Source> sources = g.getSources();

	for (Source s : sources) {
	    TaskDeclaration td = g.findTask(s.getTarget());
	    printSignature(s, td, out);
	    printThreadFn(s.getSourceFunction(), td, g, out);
	}
	
	out.getWriter(M_LOGIC_JAVA).println("boolean running;");
	out.getWriter(M_LOGIC_JAVA).println("void loop() {");
	out.getWriter(M_LOGIC_JAVA).println("running = true;");
	out.getWriter(M_LOGIC_JAVA).println("int count = 0;");
	if (thread_pool) {
	    out.getWriter(M_LOGIC_JAVA).println
		("ThreadPool threadpool = new ThreadPool("+pool_size+");");
	}
	out.getWriter(M_LOGIC_JAVA).println("while (running) {");
	for (Source s : sources) {
	    printSourceCall(s, p, out);
	}
	out.getWriter(M_LOGIC_JAVA).println("}");
	out.getWriter(M_LOGIC_JAVA).println("}");

	out.getWriter(M_LOGIC_JAVA).println
	    ("public static void main(String[] args) {");
	out.getWriter(M_LOGIC_JAVA).println("mImplInterface imp = new "+
					    IMPL_CLASS+"();");
	out.getWriter(M_LOGIC_JAVA).println("imp.init(args);");
	out.getWriter(M_LOGIC_JAVA).println("mThread mt = new mThread();");

	out.getWriter(M_LOGIC_JAVA).println("mt.impl_interface = imp;");
	out.getWriter(M_LOGIC_JAVA).println("mt.loop();");
	out.getWriter(M_LOGIC_JAVA).println("}");


	epilogue(out);
	out.flushAndClose();
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