Flux
====

Flux.

small readme for now.  questions? -> bburns@cs.umass.edu, emery@cs.umass.edu

Requirements:
	STL
	Java 1.5 (http://java.sun.com)
	Ant (http://ant.apache.org)

to build:
	% ant
	
to run unit tests on the runtime:
	% cd src/runtime
	% make

to build a server:
	% mkdir <my server dir>
	% java -cp ./bin:./lib/jdsl.jar:./lib/javacuplex.jar:lib/getopt.jar \
		edu.umass.cs.flux.Main -r <my server dir> <server-type> \
		<server-opts> src/apps/webserver/webserver
	 *where <server-type> is:
		-t [threaded]
		-b [batched]
		-e [event]
	 *where <server-opts> are:
		-p [threadpool]
		-S <size> [threadpool size]
                -d <dot-file-name (graph.dot) by default>
	% cp src/apps/webserver/mImpl.cpp <my server dir>
	% cd <my server dir>
	% compile all .cpp files 
		(sooner or later a Makefile will be auto generated...)
	% if the server is an event-based server, compile 
		src/runtime/mIOShim.cpp into a shared object file,
		and add it to the LD_PRELOAD environment variable
	% link to the executable (remember to "-lpthread")
	% ./<my-server-executable> <port> <root>

to build a Java web server:
	% mkdir <my server dir>
	% java -cp ./bin:./lib/jdsl.jar:./lib/javacuplex.jar:./lib/getopt.jar \
		edu.umass.cs.flux.Main -J -r <my server dir> \
		<server-type> <server-opts> src/apps/jserver/jserver2
	  *where <server-type> is:
		-t [threaded]
	  *where <server-opts> are:
		-p [threadpool]
		-S <size> [threadpool size]
	% cp src/apps/jserver/mImpl.java <my server dir>
	% cd <my server dir>
	% javac -classpath /path/to/flux/bin *.java
	% java -cp /path/to/flux/bin mThread:./ <port> <root>

