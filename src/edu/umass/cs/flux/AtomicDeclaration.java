package edu.umass.cs.flux;

import java.util.Vector;

/**
 * A declaration of a concurrency constraint for a particular node.
 * @author Brendan Burns
 **/
public class AtomicDeclaration {
    protected String name;
    protected Vector<Lock> locks;
    
    /**
     * @param node The name of the node this constraint applies to
     * @param locks The vector of Locks required by this node
     * @see Lock
     **/
    public AtomicDeclaration(String node, Vector<Lock> locks) {
    	this.name=node;
    	this.locks = locks;
    }

    public void addLock(Lock l) {
	locks.add(l);
    }

    public Lock getLargestLock() {
	java.util.Collections.sort(locks);
	return locks.get(0);
    }

    /**
     * Get the locks required by this constraint
     * @return A Vector of Locks
     * @see Lock
     **/
    Vector<Lock> getLocks() {
    	return locks;
    }

    /**
     * Get the name of the node this constraint applies to.
     * @return The name of the functional node this constraint applies to
     **/
    String getName() {
    	return name;
    }

    public String toString() {
	StringBuffer buff = new StringBuffer(name);
	buff.append(":{");
	for (Lock l : locks) {
	    buff.append(l.toString()+",");
	}
	buff.append("}");
	return buff.toString();
    }
}
