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
 * A generator for the SEDA event runtime (c) Matt Welsh
 * <br><b>Experimental</b>
 * @author Brendan Burns
 **/
public class JavaSedaGenerator extends AbstractGeneratorJava {
    static Hashtable translation_map = new Hashtable();
    static Hashtable defined = new Hashtable();
    static Set conversionsIn = new HashSet();
    static Set conversionsOut = new HashSet();

    /**
     * Translate a program into Java code, not thread safe!
     * @param directory The root directory for generated code
     * @param pkg The package for the generated class files
     * @param init The initialization function
     * @param p The progarm to translate
     **/
    public static void generate(String directory, String pkg, 
				String init, Program p) 
	throws java.io.IOException 
    {
	translation_map.clear();
	defined.clear();
	conversionsIn.clear();
	conversionsOut.clear();

	//Broken!!
        //generateFunctionClasses(directory, pkg, p);
	
	Collection fns = p.getFunctions();
	Iterator it = fns.iterator();
	int ix = 0;
	while (it.hasNext()) {
	    TaskDeclaration td = (TaskDeclaration)it.next();
	    translation_map.put(td.getName(), new Integer(ix++));
	}
	
	Vector srcs = p.getSources();
	TaskDeclaration kick = p.getTask((String)srcs.get(0));
	FlowStatement main = p.getMain();
	
	FileWriter fw=new FileWriter(directory+File.separator+
				     kick.getName()+"Stage.java");
	PrintWriter pw = new PrintWriter(fw);

	defined.put(kick.getName(), Boolean.TRUE);
	
	conversionsOut.add(kick);
	
	pw.println("package "+pkg+";");
	pw.println("import java.io.*;");
	pw.println("import java.net.*;");
	pw.println("import seda.sandStorm.api.*;");
	pw.println("import seda.sandStorm.core.*;");
	pw.println("import edu.umass.cs.flux.runtime.*;");
	pw.println();
	pw.println("class TimerEvent implements QueueElementIF { }");
	pw.println("public class "+kick.getName()+"Stage "+
		   "implements EventHandlerIF {");
	pw.println("private SinkIF nextsink, mysink;");
	pw.println("private ManagerIF mgr;");
	pw.println("private ssTimer t;");
	
	pw.println("public void init(ConfigDataIF config) throws Exception {"+
		   "mgr = config.getManager();"+
		   init+
		   "nextsink = mgr.getStage(\""+main.getAssignee()+"\").getSink();"+
		   "mysink = config.getStage().getSink();"+
		   "t = new ssTimer();"+
		   "t.registerEvent(2000, new TimerEvent(), mysink);"+
		   "}");
	
	pw.println("public void handleEvent(QueueElementIF item) {"+
		   "if (item instanceof TimerEvent) {"+
		   //"System.out.println(\""+kick.getName()+"\");"+
		   kick.getName()+"Impl task = new "+kick.getName()+"Impl();"+
		   "task.execute();");
	Integer start = (Integer)translation_map.get(main.getAssignee());
	pw.println("Event e = new Event(Conversion.convertOuts(task, "+start+"));");
	pw.println("e.push(-1);");
	pw.println("e.setType("+start+");");
	pw.println("try {");
	pw.println("nextsink.enqueue(new SedaWrapper(e));");
	pw.println("mysink.enqueue(item);");
	pw.println("} catch (SinkException ex) {ex.printStackTrace();}");
	pw.println("}");
	pw.println("else { System.err.println(\"Unknown Event: \"+item); }");
	pw.println("}");
	pw.println("public void handleEvents(QueueElementIF items[]) {"+
		   "for(int i=0; i<items.length; i++) {"+
		   "handleEvent(items[i]);"+
		   "}"+
		   "}");
	pw.println("public void destroy() {}");
	pw.println("}");
	pw.flush();
	fw.flush();
	pw.close();
	fw.close();

		
	Vector exps = p.getExpressions();
        it = exps.iterator();
        while (it.hasNext()) {
            FlowStatement fs =  (FlowStatement)it.next();
	    fw=new FileWriter(directory+File.separator+
			      fs.getAssignee()+"Stage.java");
	    pw = new PrintWriter(fw);
            generateStatement(pw,fs,p,p.getTask(fs.getAssignee()));
            defined.put(fs.getAssignee(), Boolean.TRUE);
	    pw.flush();
	    fw.flush();
	    fw.close();
	}
        
        it = fns.iterator();
        while (it.hasNext()) {
            TaskDeclaration task = (TaskDeclaration)it.next();
            if (defined.get(task.getName()) == null) {
		fw=new FileWriter(directory+File.separator+
				  task.getName()+"Stage.java");
		pw = new PrintWriter(fw);
		generateTask(pw, task);
		defined.put(task.getName(), Boolean.TRUE);
		pw.flush();
		fw.flush();
		fw.close();
	    }
        }
	
	fw=new FileWriter(directory+File.separator+"Conversion.java");
	pw = new PrintWriter(fw);
	pw.println("package "+pkg+";");
	pw.println("import edu.umass.cs.flux.runtime.*;");
	pw.println("public class Conversion {");
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
     * @param pw Where to write
     * @param stmt The statement to translate
     * @param p The program containing the statement
     * @param task The parent task definition for this statement (left-side)
     **/
    public static void generateStatement
	(PrintWriter pw, FlowStatement stmt, 
	 Program p, TaskDeclaration task) 
    {
	
	pw.println("package apps.jserver;");
	pw.println("import java.io.*;");
	pw.println("import java.net.*;");
	pw.println("import seda.sandStorm.api.*;");
	pw.println("import seda.sandStorm.core.*;");
	pw.println("import edu.umass.cs.flux.runtime.*;");
	pw.println();
	pw.println("public class "+task.getName()+"Stage "+
		   "implements EventHandlerIF {");
	pw.println("private SinkIF mysink;");
	pw.println("private ManagerIF mgr;");
		
	if (stmt instanceof SimpleFlowStatement) {
            generateStatementBody(pw, (SimpleFlowStatement)stmt, task);
        }
        else {
            generateStatementBody(pw, (TypedFlowStatement)stmt, p, task);
        }
        pw.println("}");
    }

    /**
     * Generate a simple flow statement body
     * @param pw Where to write
     * @param stmt The statement to translate
     * @param task The parent task definition for this statement (left-side)
     **/
    public static void generateStatementBody
	(PrintWriter pw, SimpleFlowStatement stmt, TaskDeclaration task) 
    {
	pw.println("private SinkIF nextSink;");
	Vector<String> args = stmt.getArguments();
	pw.println("public void init(ConfigDataIF config) throws Exception {"+
		   "mgr = config.getManager();"+
		   "nextSink = mgr.getStage(\""+args.get(0)+"\").getSink();"+
		   "mysink = config.getStage().getSink();"+
		   "}");

	pw.println("public void handleEvent(QueueElementIF item) {"+
		   "if (item instanceof SedaWrapper) {"+
		   //"System.out.println(\""+task.getName()+"\");"+
		   "SedaWrapper sw = (SedaWrapper)item;"+
		   "Event e = sw.getEvent();"+
		   task.getName()+
		   "Impl in = ("+task.getName()+"Impl)e.getData();");
	generateHandlerBody(pw, stmt, task);
	pw.println("}");
	pw.println("else { System.err.println(\"Unknown Event: \"+item); }");
	pw.println("}");
	pw.println("public void handleEvents(QueueElementIF items[]) {"+
		   "for(int i=0; i<items.length; i++) {"+
		   "handleEvent(items[i]);"+
		   "}"+
		   "}");
	pw.println("public void destroy() {}");
    }

    public static void generateHandlerBody
	(PrintWriter pw, SimpleFlowStatement fs, TaskDeclaration td) 
    {
	Vector<String> args = fs.getArguments();
	for (int i=args.size()-1;i>0;i--)
	    pw.println("\te.push("+
		       translation_map.get(args.get(i))+"); // "+
		       args.get(i));
	String next = args.get(0);
	pw.println("e.setData(Conversion.convertIns(in, "+
		   translation_map.get(next)+")); //"+next);
	pw.println("e.setType("+
		   translation_map.get(next)+"); //"+next);
	pw.println("try {");
	pw.println("nextSink.enqueue(item);");
	pw.println("} catch (SinkException ex) {ex.printStackTrace();}");
	conversionsIn.add(td);
    }
    
    /**
     * Generate a typed flow statement body
     * @param pw Where to write
     * @param stmt The statement to translate
     * @param p The program containing the statement
     * @param task The parent task definition for this statement (left-side)
     **/
    public static void generateStatementBody
    (PrintWriter pw, TypedFlowStatement stmt,
            Program p, TaskDeclaration task) 
    {
	Vector<FlowStatement> stmts = stmt.getFlowStatements();
	pw.println("SinkIF[] sinks = new SinkIF["+stmts.size()+"];");
	pw.println("public void init(ConfigDataIF config) {"+
		   "mgr = config.getManager();");
	pw.println("try {");
	for (int i=0;i<stmts.size();i++) {
	    SimpleFlowStatement sfs = (SimpleFlowStatement)stmts.get(i);
	    pw.println("sinks["+i+"] = "+
		       "mgr.getStage(\""+
		       sfs.getArguments().get(0)+"\").getSink();");
	}
	pw.println("} catch (NoSuchStageException ex) {ex.printStackTrace();}");
	pw.println("mysink = config.getStage().getSink();"+
		   "}");
	pw.println("public void destroy() {}");
	pw.println("public void handleEvent(QueueElementIF item) {"+
		   "if (item instanceof SedaWrapper) {"+
		   //"System.out.println(\""+task.getName()+"\");"+
		   "SedaWrapper sw = (SedaWrapper)item;"+
		   "Event e = sw.getEvent();"+
		   "SinkIF nextSink;"+
		   task.getName()+
		   "Impl in = ("+task.getName()+"Impl)e.getData();");
	
	Vector<Argument> ins = task.getInputs();
	Vector<String> vars = new Vector<String>();
        for (int i=0; i < ins.size(); i++)
            vars.add("in."+ins.get(i).getName()+"_in");

	for (int i=0;i<stmts.size();i++) {
            SimpleFlowStatement sfs = (SimpleFlowStatement)stmts.get(i);
            //Broken!!!
	    //generateTypeTests(pw, sfs.getTypes(), p, vars);
            pw.println("{");
	    pw.println("nextSink = sinks["+i+"];");
	    generateHandlerBody(pw, sfs, p.getTask(sfs.getAssignee()));
            pw.println("}");
	}
	pw.println("}");
	pw.println("else { System.err.println(\"Unknown Event: \"+item); }");
	pw.println("}");
	pw.println("public void handleEvents(QueueElementIF items[]) {"+
		   "for(int i=0; i<items.length; i++) {"+
		   "handleEvent(items[i]);"+
		   "}"+
		   "}");
    }
    
     /**
     * Generate a task
     * @param pw Where to write
     * @param task The task
     **/
    public static void generateTask(PrintWriter pw, TaskDeclaration task)
    {
	pw.println("package apps.jserver;");
	pw.println("import java.io.*;");
	pw.println("import java.net.*;");
	pw.println("import seda.sandStorm.api.*;");
	pw.println("import seda.sandStorm.core.*;");
	pw.println("import edu.umass.cs.flux.runtime.Event;");
	pw.println();
	pw.println("public class "+task.getName()+"Stage "+
		   "implements EventHandlerIF {");
	
	Collection c = translation_map.keySet();
	Iterator it = c.iterator();
	pw.println("private String[] stage_names = new String["+c.size()+"];");
	pw.println("private SinkIF mysink;");
	pw.println("private ManagerIF mgr;");
	
	pw.println("public void init(ConfigDataIF config) {"+
		   "mgr = config.getManager();"+
		   "mysink = config.getStage().getSink();");
	while (it.hasNext()) {
	    String key = (String)it.next();
	    Integer val = (Integer)translation_map.get(key);
	    pw.println("stage_names["+val+"] = \""+key+"\";");
	}
	pw.println("}");

	pw.println("public void handleEvent(QueueElementIF item) {"+
		   "if (item instanceof SedaWrapper) {"+
		   //"System.out.println(\""+task.getName()+"\");"+
		   "SedaWrapper sw = (SedaWrapper)item;"+
		   "Event e = sw.getEvent();"+
		   task.getName()+"Impl in = "+
		   "("+task.getName()+"Impl)e.getData();"+
		   "in.execute();"+
		   "int nxt = e.pop();"+
		   "if (nxt != -1) {");
	conversionsOut.add(task);
	pw.println("e.setData(Conversion.convertOuts(in, nxt));");
	pw.println("e.setType(nxt);");
	pw.println("try {");
	pw.println("mgr.getStage(stage_names[nxt]).getSink().enqueue(sw);");
	pw.println("} catch (NoSuchStageException ex) {ex.printStackTrace();}");
	pw.println(" catch (SinkException ex) {ex.printStackTrace();}");
	pw.println("}");
	pw.println("}");
	pw.println("else { System.err.println(\"Unknown Event: \"+item); }");
	pw.println("}");

	pw.println("public void handleEvents(QueueElementIF items[]) {"+
		   "for(int i=0; i<items.length; i++) {"+
		   "handleEvent(items[i]);"+
		   "}"+
		   "}");
	pw.println("public void destroy() {}");
	pw.println("}");
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
	out.println("public static TaskBase convertOuts("+
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
	out.println("public static TaskBase convertIns("+
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
