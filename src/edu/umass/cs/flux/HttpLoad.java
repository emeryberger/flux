package edu.umass.cs.flux;

import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.net.*;
import java.util.*;
import java.io.IOException;

/**
 * A Java based load tester
 * @author Brendan Burns
 **/
public class HttpLoad {
    /**
     * An individual HTTP client
     * @author Brendan Burns
     **/
    public static class Client {
	static Charset charset = Charset.forName("us-ascii");
	static CharsetDecoder decoder = charset.newDecoder();
	
	SocketAddress addr;
	Selector selector;
	SocketChannel sc;
	
	long content_length;
	long content;
	boolean close;
	
	double complete;
	double bytes;
	double latency;
	long time;
	
	int state;
	StringBuffer line;
	ByteBuffer buf;
	
	int written = 0;
	String msg;
	
	/**
	 * Constructor
	 * @param addr The address of the server
	 * @param selector The selector that listens on connections
	 * @param file The file to request
	 **/
	public Client(SocketAddress addr, Selector selector, String file) 
	    throws IOException
	{
	    bytes = 0;
	    state = 0;
	    complete = 0;
	    line = new StringBuffer();
	    msg = "GET "+file+" HTTP/1.1\r\n\r\n";
	    buf = ByteBuffer.wrap(msg.getBytes());
	    
	    this.addr = addr;
	    this.selector = selector;

	    reconnect();
	}

	private void reconnect() throws IOException {
	    sc = SocketChannel.open();
	    sc.configureBlocking(false);
	    SelectionKey readWrite = 
		sc.register(selector,
			    SelectionKey.OP_CONNECT |
			    SelectionKey.OP_READ | 
			    SelectionKey.OP_WRITE);
	    readWrite.attach(this);
	    sc.connect(addr);
	}
	
	/**
	 * Parse a line of headers from the server.
	 * @param line The line to parse
	 **/
	public void parseLine(String line) {
	    //System.out.println(">"+line);
	    if (line.startsWith("Content-Length:")) {
		content_length = 
		    Long.parseLong(line.substring(17));
		//System.out.println("Length: "+content_length);
	    }
	    else if (line.startsWith("Connection:")) {
		close = line.indexOf("close")!=-1;
	    }
	    if (line.length() == 0) {
		state = 1;
		content = 0;
	    }
	}

	/**
	 * Finish a connection
	 * @throws IOException if an io error occurs
	 **/
	public void finishConnect() throws java.io.IOException {
	    if (sc.isConnectionPending()) 
		sc.finishConnect();
	    if (sc.isConnected()) {
		//System.out.println("Connected!");
	    }
	    else {
		System.err.println("Error Connecting");
		System.exit(1);
	    }
	}

	/**
	 * Write any available data to the server
	 * @throws IOException if an io error occurs
	 **/
	public void write() throws java.io.IOException {
	    if (state == 2) {
		//System.out.println("Resetting");
		written = 0;
		state = 0;
		buf.rewind();
	    }
	    if (written < msg.length() && sc.isConnected()) {
		 written += sc.write(buf);
		 //System.out.println
		 //("Wrote "+written+" of "+msg.length());
		 if (written == msg.length()) {
		     content_length = -1;
		     content = 0;
		     time = System.currentTimeMillis();
		 }
	    }
	}
	

	/**
	 * Read some data from the server, parse lines as they arrive.
	 * @param inBuff The buffer to read from
	 **/
	public void readBytes(ByteBuffer inBuff) 
	    throws CharacterCodingException, IOException
	{
	    if (inBuff.remaining() == 0)
		return;
	    //CharBuffer chars = decoder.decode(inBuff);
	    //System.out.println(bytes);
	    //while(chars.remaining()>0) {
	    while(inBuff.remaining()>0) {
		//char c=chars.get();
		char c = (char)inBuff.get();
		bytes++;
		if (state == 0) {
		    if (c=='\r') {
			parseLine(line.toString());
			line.setLength(0);
			//c = chars.get(); // read the \n
			c = (char)inBuff.get();
			bytes++;
			if (c!='\n' && c!='\r')
			    line.append(c);
		    }
		    else {
			line.append(c);
		    }
		}
		else if (state == 1) {
		    content++;
		    if (content == content_length) {
			state = 2;
			latency += (System.currentTimeMillis()-time);
			complete++;

			//System.out.println("Got it");
			if (close) {
			    System.out.println("Closing and re-opening...");
			    sc.close();
			    reconnect();
			}
		    }
		    //System.out.print(c);
		}//System.out.println(bytes);
	    }
	}
	
	/**
	 * Get the number of bytes read.
	 * @return The total number of bytes read by this client
	 **/
	public double getBytes() {
	    return this.bytes;
	}
	
	/**
	 * Get the number of completes
	 * @return The total number of completed requests by this client
	 **/
	public double getCompletes() {
	    return complete;
	}

	/**
	 * Get the average latency of a request
	 * @return The average latency for a request from this client
	 **/
	public double getLatency() {
	    if (complete == 0) return 0;
	    return latency/complete;
	}
	
	/**
	 * Clear all stored statistics.
	 **/
	public void clear() {
		this.bytes = 0;
		this.complete = 0;
		this.latency = 0;
	}
    }
	
    /**
     * Calculate and print the current statistics for a number of clients
     * @param clients An array of currently active clients
     * @param secs The ammount of time that has passed.
     **/
    public static void statistics(Client[] clients, double secs) {
	double bytes = 0;
	double completes = 0;
	double latency = 0;
	for (int i=0;i<clients.length;i++) {
	    bytes+=clients[i].getBytes();
	    completes+=clients[i].getCompletes();
	    latency += clients[i].getLatency();
		clients[i].clear();
	}
	System.out.println("Mb/Sec. :"+(((bytes*8)/1000000)/secs));
	System.out.println("Completes/Sec. :"+(completes/secs));
	System.out.println("Latency :"+((latency/clients.length)));
    }

    /**
     * Main function
     * <br>Arguments: <code>host port #-of-clients file-request</code>
     * @param args The arguments
     * @throws Exception when an error occurs
     **/
    public static void main(String[] args) throws Exception {
	String host = args[0];
	int port = Integer.parseInt(args[1]);
	int client_size = Integer.parseInt(args[2]);
	String file = args[3];
	Client[] clients = new Client[client_size];

	InetSocketAddress isa = new InetSocketAddress(host, port);
	Selector selector = Selector.open();
	
	for (int i=0;i<clients.length;i++) {
	    clients[i] = new Client(isa, selector, file);
	}

	ByteBuffer inBuff = ByteBuffer.allocate(1024*1024);

	int keysAdded;
	long time = System.currentTimeMillis();
	long inc = 2000;
	long next = time+inc;
	long last=time;
	while ((keysAdded=selector.select(500)) > 0) {
	    time = System.currentTimeMillis();
	    if (time > next) {
		double secs = ((double)(time-last))/1000;
		statistics(clients, secs);
		last = time;
		next+=inc;
	    }
	    Set readyKeys = selector.selectedKeys();
	    Iterator it = readyKeys.iterator();
	    while (it.hasNext()) {
		SelectionKey key = (SelectionKey)it.next();
		it.remove();
		Client c = (Client)key.attachment();
		if (key.isConnectable()) {
		    c.finishConnect();
		}
		if (key.isWritable()) {
		    c.write();
		}
		if (key.isReadable()) {
		    int nbytes = c.sc.read(inBuff);
		    if (nbytes > 0) {
			inBuff.flip();
			c.readBytes(inBuff);
			inBuff.clear();
		    }
		}
	    }
	}
    }
}
		
