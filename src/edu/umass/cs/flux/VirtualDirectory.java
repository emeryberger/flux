package edu.umass.cs.flux;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

/**
 * The virtual directory class easy output to a variety of files in
 * a virtual directory tree (rooted at an arbitrary directory)
 * @author Brendan Burns
 **/
public class VirtualDirectory {
    String root;
    Hashtable<String, PrintWriter> writers;
    Hashtable<String, FileWriter> files;

    /**
     * Constructor
     * @param root The root of the directory structure
     **/
    public VirtualDirectory(String root) {
	this.root = root;
	writers = new Hashtable<String, PrintWriter>();
	files = new Hashtable<String, FileWriter>();
    }
    
    /**
     * Get the writer for a file name.
     * @param file The file name
     **/
    public PrintWriter getWriter(String file) {
	PrintWriter result;
       
	result = writers.get(file);
	if (result == null) {
	    try {
		FileWriter fw = new FileWriter(root+File.separator+file);
		files.put(file, fw);
		result = new PrintWriter(fw);
		writers.put(file, result);
	    } catch (IOException ex) {
		ex.printStackTrace();
	    }
	}
	return result;
    }

    /**
     * Flush and close all writers in this virtual directory.
     * @throws IOException if an IO error occurs
     **/
    public void flushAndClose() throws IOException 
    {
	Set< Map.Entry<String, PrintWriter> > entries = writers.entrySet();
	for (Map.Entry<String, PrintWriter> e : entries) 
	    e.getValue().flush();

	Set< Map.Entry<String, FileWriter> > fileEntries = files.entrySet();
	for (Map.Entry<String, FileWriter> e : fileEntries) 
	    e.getValue().flush();

	for (Map.Entry<String, PrintWriter> e : entries) 
	    e.getValue().close();
    }
}
