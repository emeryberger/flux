package edu.umass.cs.flux;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;


/**
 * The top level representation of a Markov program
 * @author Brendan Burns
 * @version $version: $
 **/
public class Program {
    private Vector<Source> sources;
    private Vector<FlowStatement> exps;
    private Vector<ErrorHandler> errors;
    private Hashtable<String, TaskDeclaration> decls;
    private Hashtable<String, TypeDeclaration> type_decs;
    private Hashtable<String, AtomicDeclaration> atomic_defs;
	    
	    
    /**
     * Constructor
     * @param sources A Vector of Sources defining the starting events
     * @param types A Vector of TypeDeclarations defining types.
     * @param decl_list A Vector of TaskDeclarations defining functions
     * @param program A Vector of FlowStatements defining the program
     * @param atomic_decls A Vector of concurrency contraints
     * @param errors A Vector of error handlers
     **/
    public Program(Vector<Source> sources,
		   Vector<TypeDeclaration> types,
		   Vector<TaskDeclaration> decl_list,
		   Vector<FlowStatement> program,
		   Vector<AtomicDeclaration> atomic_decls,
		   Vector<ErrorHandler> errors) {
        this.sources = sources;
        this.exps = program;
	this.errors = errors;
	
	this.decls = new Hashtable<String, TaskDeclaration>();
        for (int i=0;i<decl_list.size();i++) {
            this.decls.put((decl_list.get(i)).getName(),
            				decl_list.get(i));
	}
        this.type_decs = new Hashtable<String, TypeDeclaration>();
        for (int i=0;i<types.size();i++) {
            this.type_decs.put((types.get(i)).getName(),
                    			types.get(i));
        }
	this.atomic_defs = new Hashtable<String, AtomicDeclaration>();
	for (AtomicDeclaration ad : atomic_decls) {
	    this.atomic_defs.put(ad.getName(), ad);
	}
    }

    public Source getSessionStart(FlowStatement fs) {
	for (Source s : sources) {
	    if (s.getTarget().equals(fs.getAssignee()))
		return s;
	}
	return null;
    }
	
    /**
     * Get the set of atomic declarations
     * @return a Vector of AtomicDeclarations
     **/
    public Collection<AtomicDeclaration> getAtomicDeclarations() {
	return atomic_defs.values();
    }

    /**
     * Get a particular atomic declaration
     * @param name The name of the fn
     * @return The named atomic declaration 
     **/
    public AtomicDeclaration getAtomicDeclaration(String name) {
	return atomic_defs.get(name);
    }

    /**
     * Get the set of error handlers
     * @return A Vector of ErrorHandlers
     * @see ErrorHandler
     **/
    public Vector<ErrorHandler> getErrorHandlers() {
	return errors;
    }

    /**
     * Get the set of sources for this Markov program
     * @return A vector of the String names of the sources
     **/
    public Vector<Source> getSources() {
	return sources;
    }
    
    /**
     * Get all of the functions in this Program
     * @return A Collection containing the TaskDeclarations in the program
     **/
    public Collection<TaskDeclaration> getFunctions() {
        return this.decls.values();
    }
    
    
    /**
     * Get all of the types in this program
     * @return A Collection containing the TypeDeclarations
     **/
    public Collection getTypes() {
        return this.type_decs.values();
    }
    
    /**
     * Get the main function.  The main function (not to be confused with
     * the kickstart) is the expression whose left-hand side appears in
     * no right hand sides.
     * @return The FlowStatement which is the main expression for this program
     **/
    public FlowStatement getMain() {
        Vector stmts = (Vector) this.exps.clone();
        Iterator it = stmts.iterator();
        while (it.hasNext()) {
            FlowStatement stmt = (FlowStatement)it.next();
            String name = stmt.getAssignee();
            for (int i=0; i < this.exps.size(); i++) {
                FlowStatement temp = this.exps.get(i);
                if (temp.inRightHandSide(name)) {
                    it.remove();
                }
            }
        }
        System.out.println(stmts.toString());
        return (FlowStatement)stmts.get(0);
    }
    
    /**
     * Get a named type
     * @param name The name of the type
     * @return The type, or null if it can't be found.
     **/
    public TypeDeclaration getType(String name) {
        return this.type_decs.get(name);
    }
    
    /**
     * Get a named task
     * @param name The name of the type
     * @return The task, or null if it can't be found.
     **/
    public TaskDeclaration getTask(String name) {
        return this.decls.get(name);
    }
    
    /**
     * Get all expressions
     * @return a Vector of FlowStatements containing all expressions
     **/
    public Vector getExpressions() {
        return this.exps;
    }
    
    /**
     * Get an indexe expression
     * @param ix The index of the expression
     * @return The expression
     **/
    public FlowStatement getExpression(int ix) {
        return this.exps.get(ix);
    }
    
    /**
     * Get a named flow statement
     * @param name The name of the left-hand side of the statement
     * @return The FlowStatement whose left-hand side matches name, or null
     **/
    public FlowStatement getFlow(String name) {
        for (int i=0; i < this.exps.size(); i++) {
            if ((this.exps.get(i)).getAssignee().equals(name))
                return this.exps.get(i);
        }
        return null;
    }
    
    /**
     * Find all instances of SimpleFlowStatements matching the left-hand
     * side of the specified statement, remove them from the program
     * and create a TypedFlowStatement containing them.
     * @param stmt The statement to unify
     * @return The new compound TypedFlowStatement
     **/
    public TypedFlowStatement unifyExpression(FlowStatement stmt) {
        TypedFlowStatement result = new TypedFlowStatement(stmt.getAssignee());
        result.addFlowStatement(stmt);
        Iterator it = this.exps.iterator();
        while (it.hasNext()) {
            FlowStatement fs = (FlowStatement)it.next();
            if (fs != stmt) {
                if (fs.getAssignee().equals(result.getAssignee())) {
                    it.remove();
                    result.addFlowStatement(fs);
                }
            }
        }
        return result;
    }
    
    /**
     * Find all instances of typed SimpleFlowStatements and merge them into
     * compound TypedFlowStatements
     * @see TypedFlowStatement
     **/
    public void unifyExpressions() {
        for (int i=0; i < this.exps.size(); i++) {
            if (this.exps.get(i) instanceof SimpleFlowStatement) {
                SimpleFlowStatement fs = (SimpleFlowStatement)this.exps.get(i);
                if (fs.getTypes() != null) {
                    TypedFlowStatement tfs = unifyExpression(fs);
		    this.exps.set(i, tfs);
                }
            }
        }
    }
    
    /**
     * Verify an expression to insure that input types match output types
     * @param stmt The expression to verify
     * @return true if the expression is valid, false otherwise
     **/
    public boolean verifyExpression(SimpleFlowStatement stmt) {
        Vector types = stmt.getTypes();
        Vector args  = stmt.getArguments();
        String left = stmt.getAssignee();
        
        TaskDeclaration td = this.decls.get(left);
        if (td == null) {
            System.err.println("Error, "+left+" is undefined.");
            return false;
        }
        
        if (types != null) {
            if (types.size()  != td.getInputs().size()) {
                System.err.println("Type specification for "+
                        stmt.getAssignee()+
                        " doesn't match arguments ("+
                        types.size()+":"+td.getInputs().size()+")");
                return false;
            }
            for (int i=0;i<types.size();i++) {
            	String type = (String)types.get(i);
            	if (!type.equals("*") && this.type_decs.get(type)==null) {
            		System.err.println("Type "+type+" is undefined.");
            		return false;
            	}
            }
        }
        
        if (args.size() > 0) {
            String now = (String)args.get(0);
            TaskDeclaration current = this.decls.get(now);
            if (current == null) {
                System.err.println("Error, "+now+" is undefined.");
                return false;
            }
            if (!current.isInMatch(td)) {
                System.err.println("Inputs  of "+now+" don't match inputs of "+
                        left);
                return false;
            }
            int i=1;
            while (i<args.size()) {
                String next = (String)args.get(i++);
                TaskDeclaration next_td = this.decls.get(next);
                if (next_td == null) {
                    System.err.println("Error, "+next+" is undefined.");
                    return false;
                }
                if (!current.isOutInMatch(next_td)) {
                    System.err.println("Outputs of "+now+
                            " don't match inputs of "+next+".");
                    return false;
                }
                now = next;
                current = next_td;
            }
            if (!current.isOutMatch(td)) {
                System.err.println("Outputs of "+now+
                        " don't match inputs of "+left+".");
                return false;
            }
        }
        return true;
    }
    
    /**
     * Verify all expression to insure that input types match output types
     * @return true if the expression is valid, false otherwise
     **/
    public boolean verifyExpressions() {
        for (int i=0; i < this.exps.size(); i++) {
            if (!verifyExpression((SimpleFlowStatement)this.exps.get(i))) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * String representation of this program
     * @return The string representing this program
     **/
    public String toString() {
        return this.decls+"\n::\n"+this.exps;
    }

    public void fixDeadlock(Vector<AtomicDeclaration> list) {
	Lock large = null;
	AtomicDeclaration last = null;
	Hashtable<Lock, Boolean> has = new Hashtable<Lock, Boolean>();
	
	for (AtomicDeclaration ad : list) {
	    Vector<Lock> lks = ad.getLocks();
	    Lock temp = lks.get(0);
	    for (Lock l : lks) {
		if (l.compareTo(temp) > 0) {
		    temp = l;
		}
	    }
	    if (large == null)
		large = temp;
	    else {
		if (large.compareTo(temp) < 0 && has.get(temp)==null) {
		    System.err.println
			("Attempting to fix deadlock by elevating Lock: "+temp+
			 " to node "+last);
		    last.addLock(temp);
		    return;
		}
	    }
	    for (Lock l : lks) {
		if (has.get(l) == null) {
		    has.put(l, Boolean.TRUE);
		}
	    }
	    last = ad;
	}
	System.err.println("Error, no deadlock to fix!");
    }

    public Vector<AtomicDeclaration> checkDeadlock() {
	for (FlowStatement fs : exps) {
	    Vector<Vector<AtomicDeclaration> > lock_list;
	    if (fs instanceof SimpleFlowStatement) {
		lock_list = buildLockList((SimpleFlowStatement)fs);
	    }
	    else {
		lock_list = buildLockList((TypedFlowStatement)fs);
	    }
	    for (Vector<AtomicDeclaration> list : lock_list) {
		Hashtable<Lock, Boolean> has = new Hashtable<Lock, Boolean>();
		if (list.size() == 0)
		    break;
		Lock large = null;
		for (AtomicDeclaration ad : list) {
		    Vector<Lock> lks = ad.getLocks();
		    Lock temp = lks.get(0);
		    for (Lock l : lks) {
			if (l.compareTo(temp) > 0) {
			    temp = l;
			}
		    }
		    if (large == null)
			large = temp;
		    else {
			if (large.compareTo(temp) < 0 && has.get(temp)==null) {
			    return list;
			}
		    }
		     for (Lock l : lks) {
			if (has.get(l) == null) {
			    has.put(l, Boolean.TRUE);
			}
		     }
		}
	    }
	}
	return null;
    }

    public Vector<Vector<AtomicDeclaration> >
	buildLockList(TypedFlowStatement fs) {
	Vector<Vector<AtomicDeclaration> > result = 
	    new Vector<Vector<AtomicDeclaration> >();
	AtomicDeclaration ad = atomic_defs.get(fs.getAssignee());
	if (ad == null) 
	    return result;
	
	Vector<FlowStatement> flows = fs.getFlowStatements();
	for (FlowStatement nfs : flows) {
	    Vector<Vector<AtomicDeclaration> > nv;
	    if (nfs instanceof SimpleFlowStatement) {
		nv = buildLockList((SimpleFlowStatement)nfs);
	    }
	    else {
		nv = buildLockList((TypedFlowStatement)nfs);
	    }
	    
	    for (Vector<AtomicDeclaration> vx : nv) {
		result.add(vx);
	    }
	}
	return result;
    }

    public Vector<Vector<AtomicDeclaration> > 
	buildLockList(SimpleFlowStatement fs) 
    {
	Vector<Vector<AtomicDeclaration> > result = 
	    new Vector<Vector<AtomicDeclaration> >();
	
	Vector<AtomicDeclaration> v = new Vector<AtomicDeclaration>();
	AtomicDeclaration ad = atomic_defs.get(fs.getAssignee());
	if (ad == null) 
	    return result;
	v.add(ad);
	result.add(v);
	Vector<String> args = fs.getArguments();
	for (String arg : args) {
	    FlowStatement nfs = getFlow(arg);
	    if (nfs == null) // Its a concrete statement
		{
		    ad = atomic_defs.get(arg);
		    if (ad != null) {
			for (Vector<AtomicDeclaration> vt : result) {
			    vt.add(ad);
			}
		    }
		}
	    else {
		Vector<Vector<AtomicDeclaration> > nv;
		if (nfs instanceof SimpleFlowStatement) {
		    nv = buildLockList((SimpleFlowStatement)nfs);
		}
		else {
		    nv = buildLockList((TypedFlowStatement)nfs);
		}

		Vector<Vector<AtomicDeclaration> > new_result = 
		    new Vector<Vector<AtomicDeclaration> >();

		for (Vector<AtomicDeclaration> vt : result) {
		    for (Vector<AtomicDeclaration> vx : nv) {
			Vector<AtomicDeclaration> new_vec =
			    new Vector<AtomicDeclaration>();
			for (AtomicDeclaration atom : vt) 
			    new_vec.add(atom);
			for (AtomicDeclaration atom : vx)
			    new_vec.add(atom);
			new_result.add(new_vec);
		    }
		}
		result = new_result;
	    }
	}
	return result;
    }
		    
	

    /**
     * Get a source by name
     * @param name The name
     * @param srcs The list of sources
     **/
    Source getSource(String name) {
	for (Source src : sources) {
	    if (src.getSourceFunction().equals(name))
		return src;
	}
	return null;
    }

        /**
     * Is this task declaration a source?
     * @param task The task
     * @return true if the task declaration is a source
     **/
    boolean isSource(TaskDeclaration td) {
	String name = td.getName();
	for (Source src : sources) {
	    if (src.getSourceFunction().equals(name))
		return true;
	}
	return false;
    }

}
