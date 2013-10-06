package edu.umass.cs.flux;

import java.io.*;
import java.util.Vector;
import gnu.getopt.Getopt;

/**
 * The command line application
 * @author Brendan, Alex, Kevin
 **/
public class Main {
    private static final String USAGE = 
	"Usage: java edu.umass.cs.flux.Main\n"+
	"\t[-l]:\tEnable logging\n"+
	"\t[-b]:\tGenerate batch thread runtime !!Experimental!!\n"+
	"\t[-e]:\tGenerate event-based runtime\n"+
	"\t[-p]:\tUse a threadpool (Thread runtimes only)\n"+
	"\t[-c]:\tUse plain C (not C++) !!Experimental!!\n"+
	"\t[-t]:\tGenerate thread-based runtime\n"+
	"\t[-J]:\tGenerate Java code. !!Experimental!!\n"+
	"\t[-S <size>]:\tThreadpool size\n"+
	"\t[-d <name>]:\tGenerate the program file as a dot graph\n"+
	"\t[-r <root>]:\tGenerate code in the <root> directory";
    
    /**
     * Main function.<br>
     * Arguments:<br>
     * <ul>
     * <li><code>-l</code>: Enable logging
     * <li><code>-b</code>: Generate batch thread runtime <b>Experimental</b>
     * <li><code>-e</code>: Generate event-based runtime
     * <li><code>-p</code>: Use a threadpool (Thread runtimes only)
     * <li><code>-c</code>: Use plain C (not C++) <b>Experimental</b>
     * <li><code>-t</code>: Generate thread-based runtime
     * <li><code>-J</code>: Generate Java code. <b>Experimental</b>
     * <li><code>-S size</code>: Threadpool size
     * <li><code>-d name</code>: Generate the program file as a dot graph
     * <li><code>-r root</code>: Generate code in the &lt;root&gt; directory
     * </ul>
     **/
    public static void main(String args[]) throws Exception {
	parser p;
	boolean thread = false;
	boolean event  = false;
	boolean batch  = false;
	boolean pool   = false;
	boolean plainc = false;
  boolean eventthreadpool = false; // TY
  boolean ioshim = false;  // TY
	int size = 100;
	String root = "./";
	String dotFile = "graph.dot";
	boolean java = false;
	boolean logging = false;

	Getopt g = new Getopt("Main",args, "lbepcmhtJS:d:r:");
	int c;
	
	while ((c = g.getopt()) != -1) {
	    switch (c) {
        case 'b':
            batch = true;
            break;
        case 'e':
            event = true;
            break;
        case 'p':
            pool = true;
            break;
        case 't':
            thread = true;
            break;
        case 'c':
        	plainc = true;
        	break;
        case 'm':
          eventthreadpool = true;
          break;
        case 'h':
          ioshim = true;
          break;
        case 'J':
            java = true;
            break;
        case 'S':
            size=Integer.parseInt(g.getOptarg());
            break;
        case 'd':
            dotFile = g.getOptarg();
            break;
	case 'l':
	logging = true;
	break;
        case 'r':
            root = g.getOptarg();
            break;
	    }
	}
	if (g.getOptind() >= args.length) {
	    System.err.println(USAGE);
	    System.exit(0);
	}
	p = new parser(new Yylex(new FileInputStream(args[g.getOptind()])));
	Program pm = (Program)p.parse().value;
	if (pm.verifyExpressions()) {
	    pm.unifyExpressions();
	    Vector<AtomicDeclaration> list = pm.checkDeadlock();
	    while (list != null) {
		System.err.println("Error: Deadlock detected in path:\n\t"+
				   list);
		System.err.println("Attempting to fix...");
		pm.fixDeadlock(list);
		list = pm.checkDeadlock();
	    }
            // Dynamically Generate a DotGraph Here
            ProgramGraph pg = new ProgramGraph(pm);
            VirtualDirectory out = new VirtualDirectory(root);
	    PrintWriter pw = new PrintWriter(out.getWriter(dotFile));
        int ix = args[0].lastIndexOf(File.separator);
	    pg.outputDot(pw, args[0].substring(ix+1));
	    pw.flush();
	    pw.close();

	    if (logging)
	    {
	    	SimulatorGenerator sim = new SimulatorGenerator(pm, pg, root);
	    }

	    
	    if (!java) {
		if (thread) {
		    ThreadGenerator.setUseThreadPool(pool);
		    ThreadGenerator.setThreadPoolSize(size);
            ThreadGenerator.generate(root, logging, pg, pm, pg.getStageHashtable());
            ThreadMkfileGenerator threadMkfileGenerator = new ThreadMkfileGenerator(root);
            threadMkfileGenerator.generateMakefile();
		}
		else if (batch) {
		    BatchGenerator.generate(root, logging, pg, pm, pg.getStageHashtable());
		}
		else if (event) {
			EventGenerator.generate(root, logging, pg, pg.getStageHashtable());
            EventMkfileGenerator eventMkFileGenerator = new EventMkfileGenerator(root);
            eventMkFileGenerator.generateMakefile();
		}
		else if (plainc) {
			DummyGeneratorC generator = new DummyGeneratorC(root, pm);
			generator.generate();
		}
    else if (eventthreadpool) { // TY
      if (ioshim) {
        EventThreadGeneratorShim.setThreadPoolSize(size);
        EventThreadGeneratorShim.generate(root, logging, pg);
      }
      else {
        EventThreadGenerator.setThreadPoolSize(size);
        EventThreadGenerator.generate(root, logging, pg);
      }
    } // TY: End
		else {
		    System.err.println("Error no generation type specified");
		}
	    }
	    else {
		if (thread) {
		    JavaThreadGenerator.setUseThreadPool(pool);
		    JavaThreadGenerator.setThreadPoolSize(size);
		    JavaThreadGenerator.generate(root, new ProgramGraph(pm));
		}
		else {
		    System.err.println("Unsupported generation type");
		}
	    }
	}
	else {
	    System.out.println("Usage: [-t | -e | -s ] [-l] [-p] [-d dot-file] [-S pool-size] [-f output-file] <program>");
	}
    }
}
