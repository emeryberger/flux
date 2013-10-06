import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.util.*;
import edu.umass.cs.flux.runtime.*;

public class mImpl extends mImplInterface {
    LinkedList<SocketChannel> channels;
    LinkedList<SocketChannel> reuse;
	
    protected ServerSocketChannel sock;
    protected File root;
    protected Selector sel;

    public void init(String[] args) {
	channels = new LinkedList<SocketChannel>();
	reuse = new LinkedList<SocketChannel>();
	
	try {
	    int port = Integer.parseInt(args[0]);
	    sock = ServerSocketChannel.open();
	    ServerSocket ss = sock.socket();
	    
	    ss.bind(new InetSocketAddress(port));
	    
	    sock.configureBlocking(false);
	    	    
	    root = new File(args[1]);
	    
	    sel = Selector.open();
	    sock.register(sel, SelectionKey.OP_ACCEPT, this);
	} catch (IOException ex) {
	    ex.printStackTrace();
	}
    }

    public int Reply (Reply_in in) {
	if (in.close)
	    try {
		in.socket.close();
	    } catch (IOException ex) {
		ex.printStackTrace();
		return -1;
	    }
	else {
	    synchronized (reuse) {
		reuse.add(in.socket);
	    }
	}
	return 0;
    }

    public static int BUFFER_SIZE = 8192;

    public int ReadRequest (ReadRequest_in in, ReadRequest_out out) {
	try {
	    ByteBuffer buff = ByteBuffer.allocateDirect(BUFFER_SIZE);
	    int rd;
	    int length = 0; 
	    boolean doneRequest = false;
	    out.request = null;
	    out.close = false;

	    int start = 0;
	    int end = 0;
	    
	    char[] line = new char[1024];

	    //DEBUG printf("ReadRequest in\n");
	    do {
		end = 0;
		buff.clear();
		rd =  in.socket.read(buff);
		if (rd == -1) {
		    //System.err.println("Reading request");
		    in.socket.close();
		    return -1;
		}
		else if (rd == 0) {
		    try {
			Thread.currentThread().sleep(10);
		    } catch (InterruptedException ex) {}
		}
		else {
		    while (end < rd) {
			char c  = (char)buff.get(end++);
			if (c == '\r' || c == '\n') {
			    //System.out.println("Line end: "
			    //+new String(line, 0, length));
			    if (length == 0) 
				doneRequest = true;
			    else if (out.request == null) { // parse req
				start = 0;
				while (line[start] != ' ') {
				    start++;
				}
				start++;
				int ix = 0;
				while (line[start+ix] != ' ')
				    ix++;
				out.request = new String(line, start, ix);
				
				while (line[start] != 'H')
				    start++;
				while (line[start] != '/')
				    start++;
				start++;
				// HACK HACK HACK Assumes ASCII
				int major = line[start]-'0'; 
				start+=2;
				int minor = line[start]-'0';
				if (major != 1 || (minor > 1))
				    System.err.println
					("Urm, HTTP version: "+
					 major+"."+minor+
					 " we may be in trouble...");
				if (major == 1 && minor == 0) {
				    out.close = true;
				}
				//System.out.println(out.request);
			    }
			    else {
				if (line[0] == 'C' &&
				    line[1] == 'o' &&
				    line[2] == 'n') {
				    String ln = new String(line, 0, length);
				    if (ln.startsWith("Connection:"))
					out.close=ln.indexOf("close")!=-1;
				}
			    }
			    if (c == '\r')
				end++; // eat the \n
			    length = 0;
			}
			else {
			    line[length++] = c;
			}
		    }
		}
	    } while (!doneRequest);
	    if (out.request == null) {
		out.request = "/sys_error.html";
	    }
	    
	    out.socket = in.socket;
	} catch (IOException ex) {
	    ex.printStackTrace();
	    return -1;
	}
	return 0;
    }
    
    public int ReadWrite (ReadWrite_in in, ReadWrite_out out) {
	ByteBuffer byte_buffer = ByteBuffer.allocateDirect(8192);
	try {
	    if (in.file == null)
		return 404;
	    File f = new File(root, in.file);
	    if (!f.exists())
		return 404;
	    
	    //CharBuffer buff = bf.asCharBuffer();
	    StringBuffer buff = new StringBuffer();
	    SocketChannel s = in.socket;
	    buff.append("HTTP/1.1 200 OK\r\nContent-Length: ");
	    buff.append(f.length());
	    buff.append("\r\n");
	    if (in.close)
		buff.append("Connection: close\r\n");
	    buff.append("\r\n");
	    ByteBuffer bf = ByteBuffer.wrap(buff.toString().getBytes());
	    //System.out.println("Foo:" +buff.toString());
	    //bf.flip();
	    while (bf.hasRemaining())
		s.write(bf);

    	    FileInputStream fis = new FileInputStream(f);
	    FileChannel fc = fis.getChannel();
	    int written = 0;
	    while ((fc.read(byte_buffer)) != -1) {
		byte_buffer.flip();
		while (byte_buffer.hasRemaining())
		    written += s.write(byte_buffer);
		byte_buffer.clear();
	    }
	    //System.out.println(written+":");
	    fc.close();
	    fis.close();
	    s.socket().getOutputStream().flush();
	    //System.out.println("We're out...");
	} catch (IOException ex) {
	    ex.printStackTrace();
	    return 1;
	}
	out.socket = in.socket;
	out.close = in.close;
	
	return 0;
    }
    
    public Vector<Page_in > Listen() {
	Vector<Page_in> result = new Vector<Page_in>();
	//LinkedList<SocketChannel> news = new LinkedList<SocketChannel>();
	LinkedList<SocketChannel> tmp;
	synchronized(reuse) {
	    tmp = reuse;
	    reuse = new LinkedList<SocketChannel>();
	}
	
	try {
	    Iterator it = tmp.iterator();
	    while (it.hasNext()) {
		SocketChannel sc = (SocketChannel)it.next();
		if (sc.isOpen()) {
		    sc.register(sel, SelectionKey.OP_READ, null);
		}
	    }
	    
	    int count = sel.select(100);
	    Set s = sel.selectedKeys();
	    it = s.iterator();
	    while (it.hasNext()) {
		SelectionKey sk = (SelectionKey)it.next();
		if (sk.attachment() == null) {
		    SocketChannel sc = (SocketChannel)sk.channel();
		    if (sc.isOpen()) {
			//System.err.println("Reuse!");
			Page_in in = new Page_in();
			in.socket = sc;
			result.add(in);
		    }
		    sk.interestOps(0);
		}
		else {
		    //System.err.println("Create!");
		    Page_in in = new Page_in();
		    in.socket = sock.accept();
		    in.socket.configureBlocking(false);
		    result.add(in);
		}
		it.remove();
	    }
	} catch (IOException ex) {
	    ex.printStackTrace();
	    return result;
	}
	return result;
    }
    
    public void BadRequest(ReadRequest_in in, int err) {
	//System.err.println("Bad Request");
    }
    
    public static final byte[] FOUR_OH_FOUR_MESSAGE =
	"<body><h3>File not found</h3>You step in the stream,<br>but the water has moved on.<br>This page is not here.</body>".getBytes();


    public void FourOhFor(ReadWrite_in in, int err) {
	SocketChannel s = in.socket;
	if (s == null)
	    return;
	try {
	    s.write(ByteBuffer.wrap("HTTP/1.1 404 File Not Found\r\n".getBytes()));
	    s.write(ByteBuffer.wrap("Connection: close\r\n".getBytes()));
	    s.write(ByteBuffer.wrap("\r\n".getBytes()));
	    s.write(ByteBuffer.wrap(FOUR_OH_FOUR_MESSAGE));
	    s.socket().getOutputStream().flush();
	    s.socket().getOutputStream().close();
	    s.close();
	} catch (IOException ex) {
	    ex.printStackTrace();
	}
    }
}
