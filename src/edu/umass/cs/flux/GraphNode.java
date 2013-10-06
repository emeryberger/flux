package edu.umass.cs.flux;

import java.io.*;
import java.util.*;
import jdsl.graph.ref.IncidenceListGraph;
import jdsl.graph.api.*;

/**
 * A node in a program graph for Dot generation
 * @author Alex, Kevin.
 **/ 
public class GraphNode
{
        final static public int WHITE = 0;
        final static public int GREY = 1;
        final static public int BLACK = 2;
        
        final static public int DEFAULT = 0;
        final static public int ENTRY = 1;
        final static public int ERROR = 2;
        final static public int ERROR_HANDLER = 3;
        final static public int EXIT = 4;

        private int nodeColor = WHITE;
        private int numPaths = 0;
        
        private int nodeType;	
        
        private Object element; 
        
        public GraphNode(Object e, int nodeType)
        {
            element = e;
            numPaths = 0;
            if ((nodeType == DEFAULT) 
            		|| (nodeType == ENTRY) 
            		|| (nodeType == ERROR) 
            		|| (nodeType == ERROR_HANDLER)
            		|| (nodeType == EXIT))
                this.nodeType = nodeType;
            else
            {
                System.out.println("Error: " + nodeType + " is not a valid Node Type");
                System.exit(1);
            }
        }
    
        public String toString()
        {
            if (nodeType == ENTRY)
                return "ENTRY";
            if (nodeType == ERROR)
                return "ERROR";
            if (nodeType == EXIT)
                return "EXIT";
            if (nodeType ==  ERROR_HANDLER)
            	return ((ErrorHandler)element).getFunction();
            if (element instanceof Source)
                return ((Source)element).getSourceFunction();
            if (element instanceof TaskDeclaration)    
                return ((TaskDeclaration)element).getName();
            else 
                return "e is null";
        }

        
    public int getNodeColor()
    {
        return nodeColor;
    }

    public int getNumPaths()
    {
    	return numPaths;
    }
    
    public void setNumPaths(int num)
    {
    	numPaths = num;
    }
    
    public void setNodeColor(int nodeColor)
    {
        if ((nodeColor == this.WHITE)||(nodeColor == this.GREY)||(nodeColor == this.BLACK))
        {
            this.nodeColor = nodeColor;
        }
        else
        {
            System.out.println("Error: " + nodeColor + " is not a valid Node Color");
            System.exit(1);
        }
            
    }
    
    /*
    public int getNodeColor()
    {
    	return this.nodeColor;
    }
    */
        
        public int getNodeType()
        {
                return this.nodeType;
        }

        
        public Object getElement()
        {
                return element;
        }
}