package edu.umass.cs.flux;

import java.io.*;


/**
 * A convenience class for generating makefiles for compiling event driven
 * code.
 * @author Alex, Kevin
 **/
public class EventMkfileGenerator extends MkfileGenerator{
    /* 
       Constructor
       IN:
       String directory - the directory in which the makefile will go.
       
    */
    EventMkfileGenerator(String dir)
    {
        super(dir);
    }
    
    public void generateMakefile()
    {
            //ProgramGraph pg = new ProgramGraph(pm);
            VirtualDirectory out = new VirtualDirectory(super.directory);
	    PrintWriter pw = new PrintWriter(out.getWriter(super.mkfileName));
            
            //pw.println("alex was here!");
            
            pw.println("CC=g++");
            pw.println("CCFLAGS=-ldl -lpthread");
            pw.println("CCINCL=-I../src/runtime");

            pw.println("all: ");
            pw.println("\ttouch localStructs.h");
            pw.println("\t${CC} ${CCINCL} -c mIOShim.cpp");
            pw.println("\t${CC} -shared mIOShim.o -o mIOShim.so -ldl");
            pw.println("\t${CC} ${CCFLAGS} ${CCINCL} -o server ../src/runtime/Stopwatch.cpp mImpl.cpp mEvent.cpp mConvert.cpp ");

            pw.println("mIOShim.o: ");
            pw.println("\ttouch localStructs.h");
            pw.println("\t${CC} ${CCINCL} -c mIOShim.cpp");
            
            pw.println("mIOShim.so:");
            pw.println("\ttouch localStructs.h");
            pw.println("\t${CC} -shared mIOShim.o -o mIOShim.so -ldl");

            pw.println("server:");
            pw.println("\ttouch localStructs.h");
            pw.println("\t${CC} ${CCFLAGS} ${CCINCL} -o server ../src/runtime/Stopwatch.cpp mImpl.cpp mEvent.cpp mConvert.cpp ");
        
            pw.println("clean:");
            pw.println("\ttouch localStructs.h");
            pw.println("\trm mIOShim.o");
            pw.println("\trm mIOShim.so");
            pw.println("\trm server");
            
            pw.flush();
	    pw.close();
    }

}

/* Event:
CC=g++
CCFLAGS=-ldl -lpthread 
CCINCL=-I../src/runtime

all: 
	${CC} ${CCINCL} -c mIOShim.cpp
	${CC} -shared mIOShim.o -o mIOShim.so -ldl
	${CC} ${CCFLAGS} ${CCINCL} -o server mImpl.cpp mEvent.cpp mConvert.cpp 

mIOShim.o: 
	${CC} ${CCINCL} -c mIOShim.cpp

mIOShim.so:
	${CC} -shared mIOShim.o -o mIOShim.so -ldl

server:        
	${CC} ${CCFLAGS} ${CCINCL} -o server mImpl.cpp mEvent.cpp mConvert.cpp 
        
clean:
	rm mIOShim.o
	rm mIOShim.so
	rm server


*/