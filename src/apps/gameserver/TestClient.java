//package edu.umass.cs.flux;

import java.io.*;
import java.net.*;
import java.util.*;

public class TestClient {
    public static final int CONNECT = 0;
	public static final int DISCONNECT = 1;
	public static final int ENGINE = 2;
	public static final int TURN = 3;
    
	TestClient(int port) throws java.io.IOException {
		int recv_port = 16384;
	
		DatagramSocket socket = new DatagramSocket();
		DatagramSocket s2 = new DatagramSocket(recv_port);

		byte[] buf = new byte[256];
		buf[0] = CONNECT;
    	
		InetAddress host = InetAddress.getLocalHost();
		byte[] addr = host.getAddress();
	
		System.out.println("FOO:"+addr[0]+"."+addr[1]+"."+addr[2]+"."+addr[3]);
	
		buf[2] = addr[0];
		buf[3] = addr[1];
		buf[4] = addr[2];
		buf[5] = addr[3];
    	
		buf[6] = (byte)((recv_port>>24)&0xFF);
		buf[7] = (byte)((recv_port>>16)&0xFF);
		buf[8] = (byte)((recv_port>>8)&0xFF);
		buf[9] = (byte)(recv_port&0xFF);
    	
		System.out.println(host.getHostAddress());
    	
		InetAddress address = InetAddress.getByName("localhost");

		DatagramPacket packet = new DatagramPacket(buf, buf.length,
				address, port);
    	
		socket.send(packet);

		System.out.println("Waiting for response");
		s2.receive(packet);
		int id = packet.getData()[1];
		System.out.println("Got client id: "+id);
	
		for (int j=0;j<10;j++) {
			buf[0] = ENGINE;
			buf[1] = (byte)id;
			buf[2] = 1;
			packet.setData(buf);
			socket.send(packet);
	    
			s2.receive(packet);
			buf = packet.getData();
			int count = buf[2];
			int ix = 3;
			for (int i=0;i<count;i++) {
				int c_id = buf[ix++];
				int x = buf[ix++];
				int y = buf[ix++];
				int dx = buf[ix++];
				int dy = buf[ix++];
				System.out.println("Client: "+id+"@ ("+x+","+y+")");
			}
		}

	}
    
	public static void main(String[] args){
		int port = Integer.parseInt(args[0]);
	
		try
		{
			TestClient testClient = new TestClient(port);
		}
		catch (Exception e)
		{
			System.out.println("HELP!!");
		}
	
	
	}
}
