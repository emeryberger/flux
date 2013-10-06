package apps.jserver;

import java.net.*;
import java.io.*;

public class ListenImpl extends ListenBase {
    static ServerSocket ss = null;
    
    public ListenImpl() {
	if (ss == null) {
	    try {
		ss = new ServerSocket(ServerConfig.getPort());
	    } catch (IOException ex) {
		ex.printStackTrace();
	    }
	}
    }
    
    public void execute() {
	try {
	    socket_out = ss.accept();
	} catch (IOException ex) {
	    ex.printStackTrace();
	}
    }
}