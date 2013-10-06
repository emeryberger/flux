package edu.umass.cs.flux;

import java.util.Vector;
import java.util.Iterator;

/**
 * A simple flow statement is single expression of flow.  It may be typed.
 * @see TypedFlowStatement
 * @author Brendan Burns
 * @version $version: $
 **/
public class SimpleFlowStatement extends FlowStatement {
    protected Vector<String> types;
    protected Vector<String> args;
    
    /**
     * Constructor
     * @param assignee Left hand side
     * @param args Vector of strings defining the right hand side
     **/
    public SimpleFlowStatement(String assignee, Vector<String> args) {
        this(assignee, null, args);
    }
    
    /**
     * Constructor with types
     * @param assignee Left hand side
     * @param types Vector of String defining the type pattern
     * @param args Vector of String defining the right hand side
     **/
    public SimpleFlowStatement(String assignee,
    						   Vector<String> types,
    						   Vector<String> args) {
        super(assignee);
        this.types = types;
        this.args = (Vector<String>) args.clone();
    }
    
    /**
     * Get the types Vector for this statement
     * @return A Vector of String defining the type pattern, null if untyped
     **/
    public Vector<String> getTypes() {
        return this.types;
    }
    
    /**
     * Get the right hand side of this statement
     * @return A Vector of String defining the right hand side of the statement
     **/
    public Vector<String> getArguments() {
        return this.args;
    }
    
    /**
     * Test for presence in the right hand side
     * @param name The name to test
     * @return true if name appears in the right hand side of this statement
     **/
    public boolean inRightHandSide(String name) {
        for (int i=0; i < this.args.size(); i++)
            if (name.equals(this.args.get(i))) 
                return true;
        return false;
    }
    
    /**
     * Return a string representation of this statement
     * @return The string-i-fied version of this statement
     **/
    public String toString() {
        StringBuffer b = new StringBuffer(getAssignee()+":"+this.types+"=");
        Iterator it = this.args.iterator();
        b.append(it.next());
        while (it.hasNext()) {
            b.append("|"+it.next());
        }
        return b.toString();
    }
}
