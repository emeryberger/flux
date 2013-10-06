package edu.umass.cs.flux;

import java.io.IOException;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;

/**
 * A code generator that implements batched, threaded code generation<br>
 * <b>Experimental and incomplete</b>
 * @author Brendan Burns
 **/
public class BatchGenerator extends ThreadGenerator {
    public static final String M_BATCH_CPP = "mBatch.cpp";
    
	static int totalStages = 0;
	static Hashtable<String, Integer> stageNumber;
    
    public static void printBatchHandler(String fn, TaskDeclaration td, 
					 ProgramGraph g, VirtualDirectory out)
    {
	out.getWriter(M_LOGIC_CPP).println
	    ("void *"/*+td.getName()*/+"_BatchHandler(void *) {");
	out.getWriter(M_LOGIC_CPP).println("while(running = true) {");
	out.getWriter(M_LOGIC_CPP).println
	    ("std::vector<"+td.getName()+"_in *> * vec = mQueue.fifo_pop();");
	out.getWriter(M_LOGIC_CPP).println
	    ("for(int i=0;i<vec->size();i++) {");
	out.getWriter(M_LOGIC_CPP).println
	    (td.getName()+"_Handler((*vec)[i]);");
	out.getWriter(M_LOGIC_CPP).println("}");
	out.getWriter(M_LOGIC_CPP).println("delete vec;");
	//	out.getWriter(M_LOGIC_CPP).println("delete bs->data;");
	//	out.getWriter(M_LOGIC_CPP).println("delete bs;");
	out.getWriter(M_LOGIC_CPP).println("}");
    }
    
    public static void printBatchCall
	(Source s, Program p, VirtualDirectory out)
    {
	out.getWriter(M_LOGIC_CPP).println
	    ("std::vector<"+s.getTarget()+"_in * > *result_"+s.getTarget()+
	     " = "+s.getSourceFunction()+"();");
	out.getWriter(M_LOGIC_CPP).println
	    ("if (result_"+s.getTarget()+") {");
	out.getWriter(M_LOGIC_CPP).println
	    ("mQueue.fifo_push(result_"+s.getTarget()+");");
	out.getWriter(M_LOGIC_CPP).println("}");
    }

    /**
     * Generate a threaded program
     * @param root The root directory to output into
     * @param g The program graph
     **/
    
    public static void generate(String root, boolean logging, ProgramGraph g, Program pm, Hashtable<String, Integer> h) 
    throws IOException
    {
    	loggingOn = logging;
    	program  = pm;
    	totalStages = h.size();
    	stageNumber = h;
    	programGraph = g;
    	generate(root, g);
    }
    
    public static void generate(String root, ProgramGraph g) 
	throws IOException
    {
	M_LOGIC_CPP = M_BATCH_CPP;

	VirtualDirectory out = new VirtualDirectory(root);

	prologue(g, out);
	out.getWriter(M_LOGIC_CPP).println("#include <queue.h>");
	out.getWriter(M_LOGIC_CPP).println("#include <vector>");
	
	out.getWriter(M_STRUCT_H).println("struct batch {");
	out.getWriter(M_STRUCT_H).println("\tvoid *data;");
	out.getWriter(M_STRUCT_H).println("\tint count;");
	out.getWriter(M_STRUCT_H).println("};");


	// Print the structs and the sigs for all tasks
	Collection fns = g.getFunctions();
	Iterator it = fns.iterator();
	
	while (it.hasNext()) {
	    TaskDeclaration td = (TaskDeclaration)it.next();
	    printStructs(td, out.getWriter(M_STRUCT_H));
	    printSignature(td, out);
	}
    
	// Print the sigs for all sources
	Collection<Source> sources = g.getSources();

	Program p = g.getProgram();

	out.getWriter(M_LOGIC_CPP).println("static bool running;");
	for (Source s : sources) {
	    out.getWriter(M_LOGIC_CPP).println
		("static flux::queue<std::vector< "+s.getTarget()+
		 "_in *> *> mQueue;");
	}
	
	for (Source s : sources) {
	    TaskDeclaration td = g.findTask(s.getTarget());
	    //printSignature(s, td, out);
	    printThreadFn(s.getSourceFunction(), td, g, out);
	    printBatchHandler(s.getSourceFunction(), td, g, out);
	}
	out.getWriter(M_LOGIC_CPP).println("}");
	
	out.getWriter(M_LOGIC_CPP).println("void loop() {");
	out.getWriter(M_LOGIC_CPP).println("running = true;");
	out.getWriter(M_LOGIC_CPP).println("flux::fred hand_thread;");
	out.getWriter(M_LOGIC_CPP).println
	    ("hand_thread.create(_BatchHandler, 0);");
	out.getWriter(M_LOGIC_CPP).println("int count = 0;");
	out.getWriter(M_LOGIC_CPP).println("flux::fred thread;");
	out.getWriter(M_LOGIC_CPP).println("while (running) {");
	for (Source s : sources) {
	    printBatchCall(s, p, out);
	}
	out.getWriter(M_LOGIC_CPP).println("}");
	out.getWriter(M_LOGIC_CPP).println("}");
	
	printMain(out);

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
