/*
 * Created on Jul 8, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.umass.cs.flux;

/**
 * The Lock class represents locks available in the system.
 * @see AtomicDeclaration
 * @author Emery, Brendan 
 */
public class Lock implements Comparable {
    
    /**
     * Constructor
     * @param n The name of the lock.
     * @param type The type of the lock
     **/
    public Lock (String n, int type)
    {
        scope = type & 3;
        variety = type & ~3;
        name = n;
	if (variety == 0)
	    variety = 4;
    }

    public int compareTo(Object o) {
	Lock l = (Lock)o;
	return l.getName().compareTo(getName());
    }

    /**
     * Get the name of this lock
     * @return The lock's name.
     **/
    public String getName () {
        return name;
    }

    /**
     * Is this lock a reader?
     * @return true if the Lock is a reader lock, false otherwise
     **/
    public boolean isReader() {
	return variety == READER;
    }

    /**
     * Is this lock a writer?
     * @return true if the Lock is a writer lock, false otherwise
     **/
    public boolean isWriter() {
	return variety == WRITER;
    }

    /**
     * Is this lock a program lock?
     * @return true if the Lock is a program lock, false otherwise
     **/
    public boolean isProgram() {
	return scope == PROGRAM;
    }

    /**
     * Is this lock a session lock?
     * @return true if the Lock is a session lock, false otherwise
     **/
    public boolean isSession() {
	return scope == SESSION;
    }

    public int hashCode() {
	return name.hashCode();
    }

    public boolean equals(Object o) {
	if (o instanceof Lock) {
		Lock l = (Lock)o;
		return name.equals(l.getName());
	}
	return false;
    }

    public String toString() {
	return name+
	    (isSession()?" session":"")+
	    (isProgram()?" program":"");
    }
    
    private String name;
    private int scope;
    private int variety;
    
    
    // A Lock that has session scope
    public static final int SESSION = 1;    //   00
    // A Lock that has program scope
    public static final int PROGRAM = 2;    //   10
    // A writer Lock
    public static final int WRITER = 4;     //  100
    // A reader Lock
    public static final int READER = 8;     // 1000
}
