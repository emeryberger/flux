package edu.umass.cs.flux;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.Collection;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

/**
 * An event driven runtime in Java<br>
 * <b>Experimental, currently broken</b>
 * @author Brendan Burns
 **/
public class JavaEventGenerator extends AbstractGeneratorJava {
    static Hashtable translation_map = new Hashtable();
    static Hashtable defined = new Hashtable();
    static Set conversionsIn = new HashSet();
    static Set conversionsOut = new HashSet();

    /**
     * Translate a program into Java code, not thread safe!
     * @param directory Where to write everything
     * @param pkg The package for this class
     * @param p The progarm to translate
     **/
    public static void generate(String directory, String pkg, Program p) 
	throws java.io.IOException 
    {
	translation_map.clear();
	defined.clear();
	conversionsIn.clear();
	conversionsOut.clear();

	// Broken!
        //generateFunctionClasses(directory, pkg, p);
       
	FileWriter fw=new FileWriter(directory+File.separator+"EventApp.java");
	PrintWriter pw = new PrintWriter(fw);
	
	pw.println("package "+pkg+";");
	pw.println();
	pw.println("import java.io.*;");
	pw.println("import java.net.*;");
	pw.println("import java.util.LinkedList;");
	pw.println("import edu.umass.cs.flux.runtime.*;");
	pw.println();
	pw.println("public class EventApp implements Runnable {");
	pw.println("private LinkedList<Event> queue;");
	pw.println("private boolean running;");
	pw.println("private Object hold;");
	
	Collection fns = p.getFunctions();
	Iterator it = fns.iterator();
	int ix = 0;
	while (it.hasNext()) {
	    TaskDeclaration td = (TaskDeclaration)it.next();
	    translation_map.put(td.getName(), new Integer(ix++));
	}

	pw.println("public EventApp() {"+
		   "queue = new LinkedList<Event>();"+
		   "hold = new Object();"+
		   "}");
	
	Vector srcs = p.getSources();
	TaskDeclaration kick = p.getTask((String)srcs.get(0));
	FlowStatement main = p.getMain();
	pw.println("private boolean loop_run = true;");
	pw.println("public void loop() {");
	pw.println("while (loop_run) {");
	pw.println(kick.getName()+"Impl in = new "+
		   kick.getName()+"Impl();");
	pw.println("in.execute();");
	Integer start = (Integer)translation_map.get(main.getAssignee());
	// Note, conversion could be inlined since we statically know it...
	pw.println("Event e = new Event(convertOuts(in, "+start+"));");
	pw.println("e.push(-1);");
	pw.println("e.setType("+start+");");
	pw.println("queueEvent(e);");
	pw.println("}");
	pw.println("}");

	pw.println("public void queueEvent(Event e) {"+
		   "synchronized (queue) { queue.addLast(e); }"+
		   "synchronized (hold) { hold.notify(); }"+
		   "}");

	pw.println("private Event dequeueEvent() {"+
		   "while (queue.isEmpty()) {"+
		   "try {"+
		   "synchronized (hold) { hold.wait(); }"+
		   "} catch (InterruptedException ignore) {} }"+
		   "return queue.removeFirst();"+
		   "}");
		   
	pw.println("public void run() {"+
		   "running = true;"+
		   "while(running) {"+
		   "Event e = dequeueEvent();"+
		   "e = handleEvent(e);"+
		   "if (e!=null) {queueEvent(handleEvent(e));}"+
		   "}"+
		   "}");

	pw.println("private Event handleEvent(Event e) {");
	pw.println("TaskBase tb = e.getData();");
	pw.println("Event nxt = null;");
	pw.println("switch (e.getType()) {");
	
	it = fns.iterator();
       	while (it.hasNext()) {
	    TaskDeclaration td = (TaskDeclaration)it.next();
	    Integer n =(Integer)translation_map.get(td.getName());
	    pw.println("case "+n.intValue()+":");
	    pw.println("nxt = handleTask(("+
		       td.getName()+"Base)tb, e);");
	    pw.println("break;");
	}
	pw.println("}");
	pw.println("return nxt;");
	pw.println("}");
	
	Vector exps = p.getExpressions();
        it = exps.iterator();
        while (it.hasNext()) {
            FlowStatement fs =  (FlowStatement)it.next();
            generateStatement(pw,fs,p,p.getTask(fs.getAssignee()));
            defined.put(fs.getAssignee(), Boolean.TRUE);
        }
        
        it = fns.iterator();
        while (it.hasNext()) {
            TaskDeclaration task = (TaskDeclaration)it.next();
            if (defined.get(task.getName()) == null) {
                generateTask(pw, task);
                defined.put(task.getName(), Boolean.TRUE);
            }
        }

	it = conversionsOut.iterator();
	while (it.hasNext()) {
	    TaskDeclaration task = (TaskDeclaration)it.next();
	    generateConversionOut(pw, task, p);
	}

	it = conversionsIn.iterator();
	while (it.hasNext()) {
	    TaskDeclaration task = (TaskDeclaration)it.next();
	    generateConversionIn(pw, task, p);
	}
	pw.println("}");
	pw.flush();
	fw.flush();
	pw.close();
	fw.close();
    }
    
    /**
     * Generate a statement
     * @param output Where to write
     * @param stmt The statement to translate
     * @param p The program containing the statement
     * @param task The parent task definition for this statement (left-side)
     **/
    public static void generateStatement
	(PrintWriter output, FlowStatement stmt, 
	 Program p, TaskDeclaration task) 
    {
        output.println("Event handleTask("+stmt.getAssignee()+"Base in, Event last) {");
	if (stmt instanceof SimpleFlowStatement) {
            generateStatementBody(output, (SimpleFlowStatement)stmt, task);
        }
        else {
            generateStatementBody(output, (TypedFlowStatement)stmt, p, task);
        }
        output.println("\treturn last;");
        output.println("}");
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
	    output.println("\tlast.push("+
			   translation_map.get(args.get(i))+"); // "+
			   args.get(i));
        
       	String next = args.get(0);
	
	output.println("last.setData(convertIns(in, "+
		       translation_map.get(next)+")); //"+next);
	output.println("last.setType("+
		       translation_map.get(next)+"); //"+next);
	conversionsIn.add(task);
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
	Vector<FlowStatement> stmts = stmt.getFlowStatements();
        Iterator<FlowStatement> it = stmts.iterator();
	
	Vector<Argument> ins = task.getInputs();
	Vector<String> vars = new Vector<String>();
        for (int i=0; i < ins.size(); i++)
            vars.add("in."+ins.get(i).getName()+"_in");

	while (it.hasNext()) {
            SimpleFlowStatement sfs = (SimpleFlowStatement)it.next();
            // Broken!
	    //generateTypeTests(output, sfs.getTypes(), p, vars);
            output.println("{");
	    generateStatementBody(output, sfs, p.getTask(sfs.getAssignee()));
            output.println("}");
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
        output.println("Event handleTask("+
		       task.getName()+"Base in,Event last) {");
	output.println("in.execute();");
	output.println("int nxt = last.pop();");
	output.println("if (nxt != -1) {");
	conversionsOut.add(task);
	output.println("last.setData(convertOuts(in, nxt));");
	output.println("last.setType(nxt);");
	output.println("return last;");
	output.println("}");
	output.println("return null;");
	output.println("}");
    }
    
    /**
     * Generate a conversion function
     * @param out Where to write
     * @param t The task to convert from
     * @param p The program
     **/
    public static void generateConversionOut
	(PrintWriter out, TaskDeclaration t, Program p) 
    {
	out.println("private TaskBase convertOuts("+
		    t.getName()+"Base from, int to) {");
	out.println("TaskBase out = null;");
	out.println("switch(to) {");
	Collection c = translation_map.keySet();
	Iterator it = c.iterator();
	while (it.hasNext()) {
	    String name = (String)it.next();
	    TaskDeclaration td = p.getTask(name);
	    if (t.isOutInMatch(td) && !(t.getName().equals(td.getName()))) {
		Integer ix = (Integer)translation_map.get(name);
		out.println("case "+ix.intValue()+": {");
		out.println(td.getName()+"Impl o = new "+
			    td.getName()+"Impl();");
		Vector<Argument> outs = t.getOutputs();
		Vector<Argument> ins = td.getInputs();
		for(int i=0;i<outs.size();i++) {
		    out.println("o."+ins.get(i).getName()+"_in = "+
				"from."+outs.get(i).getName()+"_out;");
		}
		out.println("out = o; }");
		out.println("break;");
	    }
	}
	out.println("default:");
	out.println("throw new IllegalArgumentException(\"Bad Conversion!"+
		    t.getName()+"->\"+to);");
	out.println("}");
	out.println("return out;");
	out.println("}");
    }
    
    /**
     * Generate a conversion function
     * @param out Where to write
     * @param t The task to convert from
     * @param p The program
     **/
    public static void generateConversionIn
	(PrintWriter out, TaskDeclaration t, Program p) 
    {
	out.println("private TaskBase convertIns("+
		    t.getName()+"Base from, int to) {");
	out.println("TaskBase out = null;");
	out.println("switch(to) {");
	Collection c = translation_map.keySet();
	Iterator it = c.iterator();
      	while (it.hasNext()) {
	    String name = (String)it.next();
	    TaskDeclaration td = p.getTask(name);
	    if (t.isInMatch(td) && !(t.getName().equals(td.getName()))) {
		Integer ix = (Integer)translation_map.get(name);
		out.println("case "+ix.intValue()+": {");
		out.println(td.getName()+"Impl o = new "+
			    td.getName()+"Impl();");
		Vector<Argument> in1 = t.getInputs();
		Vector<Argument> in2 = td.getInputs();
		for(int i=0;i<in1.size();i++) {
		    out.println("o."+in2.get(i).getName()+"_in = "+
				"from."+in1.get(i).getName()+"_in;");
		}
		out.println("out = o; }");
		out.println("break;");
	    }
	}
	out.println("default:");
	out.println("throw new IllegalArgumentException(\"Bad Conversion!\");");
	out.println("}");
	out.println("return out;");
	out.println("}");
    }
}
		   
	    
