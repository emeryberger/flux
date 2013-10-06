CC=g++
CCFLAGS=-ldl -g  -lpthread -lsocket -lnsl -ljpeg -I../src/runtime  
# -I/usr/fit/tmp1/akostadi/jpeg-6b -L/usr/fit/tmp1/akostadi/jpeg-6b

server: mThread.cpp mImpl.cpp ../src/runtime/logger.h
	touch localStructs.h
	${CC} ${CCFLAGS} -o server  mImpl.cpp mThread.cpp
clean:
	touch localStructs.h
	rm server
