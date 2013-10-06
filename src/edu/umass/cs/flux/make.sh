#!/bin/bash
classic-ant
echo "Generating thread code"
java -classpath ./lib/javacuplex.jar:./classes edu.umass.cs.flux.parser webserver thread > thread.c
echo "Generating event code"
java -classpath ./lib/javacuplex.jar:./classes edu.umass.cs.flux.parser webserver event > event.c
make
