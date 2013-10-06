package edu.umass.cs.flux;

import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * The declaration of an error handler for a functional node.
 * @author Brendan Burns
 **/
public class ErrorHandler {
    Vector<String> pattern;
    String function;
    
    /**
     * Constructor
     * @param pattern The pattern of nodes that this handler handles.
     * @param function The name of the function that handles this error.
     **/
    public ErrorHandler(Vector<String> pattern, String function) {
    	this.pattern = pattern;
    	this.function = function;
    }
 
    /**
     * Get the function that handles errors
     * @return The name of the function that is the error handler.
     **/
    public String getFunction() {
    	return function;
    }
    
    
    /**
     * Get the pattern of nodes that this error handler matches
     * @return A Vector of String naming a series of nodes.
     **/
    public Vector<String> getPattern()
    {
        return pattern;
    }
    
    /**
     * Get the first node in the error handler
     * @return The name of the first node in the pattern
     **/
    public String getTarget()
    {
        return pattern.firstElement();
    }
    
    /**
     * Test if a path matches this error handler.
     * @param path The path to test
     * @return true if <code>path</code> matches the pattern of this handler.
     **/
    public boolean matches(String path) 
    {
		StringTokenizer toks = new StringTokenizer(path, ",");
		Vector vec = new Vector<String>();
		while (toks.hasMoreTokens())
		    vec.add(toks.nextToken());
		
		return matches(vec.iterator(), pattern.iterator());
    }
	
    /**
     * Test if a path matches this error handler.
     * @param path The path to test
     * @return true if <code>path</code> matches the pattern of this handler.
     **/
    public boolean matches(Vector<String> path) {
	return matches(path.iterator(), pattern.iterator());
    }
	 
    /**
     * Static test if a path matches a pattern.
     * @param candidate The path to test.
     * @param pattern The pattern to match.
     * @return true if <code>path</code> matches the pattern of this handler.
     **/
    public static boolean matches (Iterator<String> candidate, 
				   Iterator<String> pattern)
    {
	while (pattern.hasNext()) {
	    String key = pattern.next();
	    if ("*".equals(key)) {
		String next = pattern.next();
		String current;
		
		do {
		    if (!candidate.hasNext())
			return false;
		    current = candidate.next();
		} while (!current.equals(next));
	    }
	    else {
		if (!candidate.hasNext())
		    return false;
		if (!candidate.next().equals(key))
		    return false;
	    }
	}
	return true;
    }
    
    /**
     * Static test function
     * @param args The arguments (arg[0] is used for testing...
     **/
    public static void main(String[] args) {
	StringTokenizer toks = new StringTokenizer(args[0], ",");
	Vector<String> pattern = new Vector<String>();
	while (toks.hasMoreTokens()) 
	    pattern.add(toks.nextToken());

	Vector<String> path = new Vector<String>();
	while (toks.hasMoreTokens()) 
	    path.add(toks.nextToken());
	
	ErrorHandler eh = new ErrorHandler(pattern, "Foo");
	
	System.out.println(eh.matches(path));
	System.out.println(eh.matches(args[1]));
    }
}