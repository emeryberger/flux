import java.net.*;
import javax.swing.JFrame;

public abstract class AbstractClient implements Runnable {
    public static final int CONNECT = 0;
	public static final int DISCONNECT = 1;
	public static final int ENGINE = 2;
	public static final int TURN = 3;
	public static final int STATUS = 5;
    
	String server;
	int server_port;
	InetAddress server_address;
	int recv_port;
	DatagramSocket send;
	DatagramSocket recv;
	byte[] send_buffer;
	byte[] recv_buffer;

	DatagramPacket send_packet = null;
	DatagramPacket recv_packet = null;

	int id = -1;

	double average_interval=0;
	long count=0;
	long last=-1;
	boolean instrument = true;
    
	public AbstractClient(String server, int server_port, int recvPort) 
			throws java.io.IOException
	{
		this.server = server;
		this.server_port = server_port;
		this.server_address = InetAddress.getByName(server);
		System.out.println("server addy " + server_address);
		this.recv_port = recvPort;
		this.recv = new DatagramSocket(recv_port);
		this.send = new DatagramSocket();
		this.send_buffer = new byte[256];
		this.recv_buffer = new byte[256];
	}

	protected void send() throws java.io.IOException {
		if (send_packet == null) {
			send_packet = new DatagramPacket(send_buffer, send_buffer.length,
											 server_address, server_port);
		}
		else {
			send_packet.setData(send_buffer);
		}
		send.send(send_packet);
	}

	protected void receive() throws java.io.IOException {
		if (recv_packet == null) {
			recv_packet = new DatagramPacket(recv_buffer, recv_buffer.length);
		}
		recv.receive(recv_packet);
		System.out.println("recv_packet " + recv_packet);
	}

	public void connect() throws java.io.IOException {
		send_buffer[0] = CONNECT;
    	
		InetAddress host = InetAddress.getLocalHost();
		byte[] addr = host.getAddress();
		
		send_buffer[2] = addr[0];
		send_buffer[3] = addr[1];
		send_buffer[4] = addr[2];
		send_buffer[5] = addr[3];
    	
		send_buffer[6] = (byte)((recv_port>>24)&0xFF);
		send_buffer[7] = (byte)((recv_port>>16)&0xFF);
		send_buffer[8] = (byte)((recv_port>>8)&0xFF);
		send_buffer[9] = (byte)(recv_port&0xFF);
    	
		send();
	
		System.out.println("Waiting for connect...");
		receive();
	
		if (recv_buffer[0] == CONNECT) {
			id = recv_buffer[1];
			System.out.println("Got client id: "+id);
			if (id < 0)
				id += 256;
			System.out.println("Got client id: "+id);
		}
		else {
			System.err.println("Expecting connect msg, got: "+recv_buffer[0]);
		}
		System.out.println("Starting game event thread...");
		new Thread(this).start();
	}

	public void run() {
		while (true) {
			try {
				receive();
				handleEvent();
			} catch (java.io.IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void setInstrument(boolean doIt) {
		this.instrument = doIt;
	}

	public double getAverageInterval() {
		return ((double)average_interval)/count;
	}

	protected void handleEvent() {
		switch(recv_buffer[0]) {
			case STATUS:
				if (instrument) {
					if (last > 0) {
						average_interval += System.currentTimeMillis()-last;
						count++;
					}
					last = System.currentTimeMillis();
				}
				updateStatus();
				break;
			default:
				System.err.println("Unknown msg: "+recv_buffer[0]);
		}
	}

	protected int makeUnsignedShort(byte[] buff, int ix) {
		int result = 0;
		if (buff[ix] < 0)
			result = (buff[ix]+256)*256;
		else
			result = (buff[ix])*256;
		if (buff[ix+1] < 0)
			result += (buff[ix+1]+256);
		else
			result += (buff[ix+1]);
	
		return result;
	}

	protected void updateStatus() {
		int count = recv_buffer[2];
		int ix = 3;
		for (int i=0;i<count;i++) {
			int c_id = recv_buffer[ix++];
			int x = makeUnsignedShort(recv_buffer, ix);
			ix+=2;
			int y = makeUnsignedShort(recv_buffer, ix);
			ix+=2;
			boolean it = (recv_buffer[ix++]==1);
			ix++;//recv_buffer[ix++];
	 
			updatePlayer(c_id, x, y, it);
		}
	}
    
	public abstract void updatePlayer(int id, int x, int y, boolean it);

	public void up() {
		try {
			send_buffer[0] = ENGINE;
			send_buffer[1] = (byte)id;
			send_buffer[2] = -1;
			send();
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}
    
	public void down() {
		try {
			send_buffer[0] = ENGINE;
			send_buffer[1] = (byte)id;
			send_buffer[2] = 1;
			send();
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}
    
	public void left() {
		try {
			send_buffer[0] = TURN;
			send_buffer[1] = (byte)id;
			send_buffer[2] = -1;
			send();
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}

	public void right() {
		try {
			send_buffer[0] = TURN;
			send_buffer[1] = (byte)id;
			send_buffer[2] = 1;
			send();
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}

	public void disconnect() {
		try {
			send_buffer[0] = DISCONNECT;
			send_buffer[1] = (byte)id;
			send();
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}
}
