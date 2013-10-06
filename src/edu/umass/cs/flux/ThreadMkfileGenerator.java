package edu.umass.cs.flux;

import java.io.*;

/**
 * Generate Makefiles for thread-based runtimes
 * @author Kevin, Alex
 **/
public class ThreadMkfileGenerator extends MkfileGenerator
{
    ThreadMkfileGenerator(String dir)
    {
        super(dir);
    }
    
    public void generateMakefile()
    {
        //ProgramGraph pg = new ProgramGraph(pm);
        VirtualDirectory out = new VirtualDirectory(super.directory);
        PrintWriter pw = new PrintWriter(out.getWriter(super.mkfileName));
        
        pw.println("CC=g++");
        pw.println("CCFLAGS=-ldl -lpthread");
        
        pw.println("CC=g++");
        pw.println("CCFLAGS=-ldl -lpthread -I../src/runtime");

        pw.println("server: ");
        pw.println("\ttouch localStructs.h");
        pw.println("\t${CC} ${CCFLAGS} -o server ../src/runtime/Stopwatch.cpp mImpl.cpp mThread.cpp");
        
        pw.println("clean:");
        pw.println("\ttouch localStructs.h");
        pw.println("\trm server");
        
        pw.flush();
        pw.close();
    }
}

/* 
Thread:
CC=g++
CCFLAGS=-ldl -lpthread -I../src/runtime

server: 
	${CC} ${CCFLAGS} -o server mImpl.cpp mThread.cpp
	
clean:
	rm server
*/