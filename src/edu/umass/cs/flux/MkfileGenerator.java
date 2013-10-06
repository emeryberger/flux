package edu.umass.cs.flux;

import java.io.*;

/**
 * Abstract parent for makefile generation
 * @author Kevin, Alex
 **/
public class MkfileGenerator
{
    protected String directory;
    protected final String mkfileName =  "Makefile";
    
    MkfileGenerator(String dir)
    {
        directory = dir;
    }
    
    public void generateMakefile()
    {

    }
    /*
    public static void main(String args[]) throws Exception 
    {
    
    
    
    }
    */

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