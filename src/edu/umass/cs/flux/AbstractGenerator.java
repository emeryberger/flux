package edu.umass.cs.flux;

import java.io.PrintWriter;

import java.util.Iterator;
import java.util.Vector;


/**
 * The abstract generator class contains utilities for generating
 * code.
 * @author Brendan Burns
 * @version $version$
 **/
public abstract class AbstractGenerator {
    /**
     * Generate a signature for a source function
     * @param output Where to print
     * @param src The source
     **/
    static void generateSourceFunction(PrintWriter output, Source src) {
	output.println("int "+src.getSourceFunction()+
		       "("+src.getTarget()+"_struct_in **in);");
    }
    
    /**
     * Generate a type test function signature
     * @param out Where to print
     * @param type The type to generate
     **/
    public static void generateFunction(PrintWriter out, TypeDeclaration type)
    {
        out.println("bool "+type.getFunction()+"(void *);");
    }
    
    /**
     * Generate a function call
     * @param out Where to write the function
     * @param task The task for which to generate the function
     **/
    public static void generateFunction (PrintWriter out,
					 TaskDeclaration task) {
	generateFunction(out, "void", task);
    }
	
    /**
     * Generate a function call
     * @param out Where to write the function
     * @param return_type The type this function returns
     * @param task The task for which to generate the function
     **/

    public static void generateFunction (PrintWriter out, String return_type,
					 TaskDeclaration task) 
    {
        out.print(return_type);
	out.print(" ");
        out.print(task.getName());
        out.print("(");
        Vector<Argument> ins = task.getInputs();
        Iterator<Argument> it = ins.iterator();
        while (it.hasNext()) {
            Argument a = it.next();
            out.print(a.getType()+" "+a.getName()+"_in");
            if (it.hasNext())
                out.print(",");
        }
        Vector<Argument> outs = task.getOutputs();
        it = outs.iterator();
        while (it.hasNext()) {
            Argument a = it.next();
            if (ins.size() > 0)
                out.print(",");
            out.print(a.getType()+"* "+a.getName()+"_out");
        }
        out.println(");");
    }
    
    /**
     * Generate a C++ <tt>class</tt> containing the specified arguments.
     * This will define a type named &lt;pre&gt;
     * @see Argument
     * @param out Where to write the struct definition
     * @param args The vector of Arguments that make up the struct.
     * @param pre The name of the struct, and the prefix for all argument names
     **/
    public static void generateStruct(PrintWriter out,
				      Vector<Argument> args,
				      String pre) 
    {
        out.println("class "+pre+" {");
        out.println ("public:");
        
        // The default constructor (empty).
        out.println ("\t" + pre + "(void) {}");

        // Constructor with arguments.
        if (args.size() > 0) {
        	out.print ("\t" + pre + "(");
        	for (int i = 0; i < args.size(); i++) {
        		Argument a = args.get(i);
        		out.print(a.getType()+" _arg_" + i);
        		if (i+1 < args.size()) {
        			out.print (", ");
        		}
        	}
        	out.println ("): ");
        	for (int i = 0; i < args.size(); i++) {
        		Argument a = args.get(i);
        		out.print(pre + "_arg_" + i + " ("
        				+ "_arg_" + i + ")");
        		if (i+1 < args.size()) {
        			out.print (", ");
        		}
        		out.println ("");
        	}
        	out.println ("{}\n");
        	for (int i = 0; i < args.size(); i++) {
        		Argument a = args.get(i);
        		out.println("\t"+a.getType()+" "+pre+"_arg_"+i+";");
        	}
        }
        
        out.println("};");
    }
    
    /**
     * Generate a test for a type
     * @param out Where to write the test to
     * @param type The name of the type
     * @param arg The argument to test
     * @param p The program which defines the type
     **/
    public static void generateTypeTest
    (PrintWriter out, String type, String arg, Program p) 
    {
        if (type.equals("*")) {
            out.print(" (1) ");
        }
        else {
            TypeDeclaration td = p.getType(type);
            out.print(" ("+td.getFunction()+"("+arg+")) ");
        }
    }
    
    /**
     * Generate a set of type tests as an <tt>if ...</tt> statement
     * @param out Where to write the test
     * @param types A vector of Strings naming the types
     * @param p The program defining the types
     * @param args Vector of Strings naming the arguments for the type tests
     **/
    public static void generateTypeTests
    (PrintWriter out, Vector<String> types, Program p, Vector<String> args) 
    {
        Iterator<String> it = args.iterator();
        out.print("if (");
        generateTypeTest(out, types.get(0), it.next(), p);
        for (int i=1;i<types.size();i++) {
            out.print(" && ");
            generateTypeTest(out, types.get(i), it.next(), p);
        }
        out.println(")");
    }
}
