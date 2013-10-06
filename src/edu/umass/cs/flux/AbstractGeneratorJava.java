package edu.umass.cs.flux;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.PrintWriter;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

/**
 * The abstract generator class contains utilities for generating
 * Java code.
 * @author Brendan Burns
 * @version $version$
 **/
public class AbstractGeneratorJava {
    protected static String M_IMPL_JAVA = "mImplInterface.java";
    protected static String M_LOGIC_JAVA = null;
    
    // Indicates whether a symbol is defined or not.
    protected static Hashtable<String, Boolean> defined 
    	= new Hashtable<String, Boolean>();

    protected static void printClassHeader(String class_name, PrintWriter pw) {
	printClassHeader(false, class_name, pw);
    }

    protected static void printClassHeader
	(boolean abstct, String class_name, PrintWriter pw) 
    {
	pw.println("import java.io.*;");
	pw.println("import java.nio.*;");
	pw.println("import java.nio.channels.*;");
	pw.println("import java.net.*;");
	pw.println("import java.util.*;");
	pw.println("import edu.umass.cs.flux.runtime.*;");
	pw.print("public ");
	if (abstct)
	    pw.print("abstract ");
	pw.println(" class "+class_name+" {");
    }
    
    /**
     * Print the structs for input/output for a task
     * @param td The task
     * @param out Where to write
     **/
    public static void printStructs(TaskDeclaration td, VirtualDirectory out) {
	Vector<Argument> ins = td.getInputs();
	if (ins.size() > 0) {
	    PrintWriter pw = out.getWriter(td.getName()+"_in.java");
	    printClassHeader(td.getName()+"_in", pw);
	    for (Argument a : ins) 
		pw.println("\tpublic "+a.getType()+" "+a.getName()+";");
	    pw.println("\tpublic Session _session;");
	    pw.println("}");
	}
	 
	Vector<Argument> outs = td.getOutputs();
	if (outs.size() > 0) {
	    PrintWriter pw = out.getWriter(td.getName()+"_out.java");
	    printClassHeader(td.getName()+"_out", pw);
	    for (Argument a : outs) 
		pw.println("\tpublic "+a.getType()+" "+a.getName()+";");
	    pw.println("\tpublic Session _session;");
	    pw.println("}");
	}
    }

    /**
     * Print a function signature and stub for a task
     * @param td The task
     * @param virt The virtual directory to contain the .java files
     **/
    public static void printSignature(TaskDeclaration td,
				      VirtualDirectory virt) 
    {
	Vector<Argument> ins = td.getInputs();
	Vector<Argument> outs = td.getOutputs();
	
	virt.getWriter(M_IMPL_JAVA).print
	    ("public abstract int "+td.getName()+" (");
	if (ins.size() > 0) {
	    virt.getWriter(M_IMPL_JAVA).print(td.getName()+"_in in");
	    if (outs.size() > 0) {
		virt.getWriter(M_IMPL_JAVA).print(", ");
	    }
	}
	if (outs.size() > 0) {
	    virt.getWriter(M_IMPL_JAVA).print(td.getName()+"_out out");
	}
	virt.getWriter(M_IMPL_JAVA).println(");");
    }
	
    /**
     * Print the signature and stub for a source
     * @param s The source
     * @param target The task target of the source
     * @param virt The directory to contain the .h and .cpp files
     **/
    public static void printSignature(Source s, TaskDeclaration target, 
				      VirtualDirectory virt) {
	Vector<Argument> ins = target.getInputs();
	String fn = s.getSourceFunction();
	virt.getWriter(M_IMPL_JAVA).print
	    ("public abstract Vector<"+target.getName()+"_in > "+fn+"();");
	if (ins.size() == 0) 
	    {
		System.err.println
		    ("Error! Source directed to a function with no input...");
		System.exit(1);
	    }
    }
    
    /**
     * Prologue output
     * @param out The directory to hold files
     **/
    protected static void prologue(VirtualDirectory out) {
	printClassHeader(true, "mImplInterface", out.getWriter(M_IMPL_JAVA));
	out.getWriter(M_IMPL_JAVA).println
	    ("public abstract void init(String[] args);");
    }

    /**
     * Epilogue output
     * @param out The directory to hold files
     **/
    protected static void epilogue(VirtualDirectory out) {
	out.getWriter(M_IMPL_JAVA).println("}");
    }
    
    protected static void printErrorHandler(TaskDeclaration current,
					    Program p, VirtualDirectory out)
    {
	printErrorHandler(current, current.getName()+"_var_in", p, out);
    }

    /**
     * Print the matching error handlers
     * @param current The current task
     * @param p The program
     * @param out The directory to write to
     **/
    protected static void printErrorHandler(TaskDeclaration current,
					    String var,
					    Program p, VirtualDirectory out) 
    {
	int matches = 0;
	Vector<ErrorHandler> errs = p.getErrorHandlers();
	for (ErrorHandler err : errs) {
	    if (err.matches(current.getName())) {
		out.getWriter(M_LOGIC_JAVA).println
		    ("impl_interface."+
		     err.getFunction()+"("+var+", err);");
		if (defined.get(current.getName())==null) {
		    out.getWriter(M_IMPL_JAVA).println
			("abstract public void "+err.getFunction()+
			 "("+current.getName()+"_in in, int err);");
		    defined.put(current.getName(), Boolean.TRUE);
		}
		matches++;
	    }
	}  
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
            out.getWriter(M_LOGIC_JAVA).print(" (true) ");
        }
        else {
            TypeDeclaration td = p.getType(type);
            out.getWriter(M_LOGIC_JAVA).print
		(" ("+td.getFunction()+"("+prefix+arg.getName()+")) ");
	    out.getWriter(M_IMPL_JAVA).println
		("abstract public boolean "+td.getFunction()+
		 "("+arg.getType()+" "+arg.getName()+");");
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
	    prefix = last.getName()+"_var_in.";
	}
	else {
	    args = last.getOutputs();
	    prefix = last.getName()+"_var_out.";
	}
	Iterator it = args.iterator();
        out.getWriter(M_LOGIC_JAVA).print("if (");
        generateTypeTest(types.get(0), prefix,
			 (Argument)it.next(), p, out);
        for (int i=1;i<types.size();i++) {
            out.getWriter(M_LOGIC_JAVA).print(" && ");
            generateTypeTest(types.get(i), prefix,
			     (Argument)it.next(),
			     p, out);
        }
        out.getWriter(M_LOGIC_JAVA).println(")");
    }
}
