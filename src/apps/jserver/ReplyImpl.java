package apps.jserver;

import java.net.*;
import java.io.*;

public class ReplyImpl extends ReplyBase {
	
	private void writeHeaders(Writer os, int cntLength, String cntType)
	throws IOException
	{
		os.write("Content-Type: "+cntType+"\r\n");
		os.write("Server: Markov (v0.1 [SEDA])\r\n");
		os.write("Content-Length: "+cntLength+"\r\n");
		os.write("\r\n");
	}
	
	public void execute() {
		try {
			OutputStream os = this.socket_in.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os);
			
			if (this.output_in == null) {
				osw.write("HTTP/1.0 404 File not found\r\n");
				String page = 
					"<html><body><h2>404 File Not Found!</h2></body></html>";
				writeHeaders(osw,page.length(), "text/html");
				osw.write(page);
			}
			else {
				osw.write("HTTP/1.0 200 OK\r\n");
				writeHeaders(osw,this.output_in.length(),this.content_in);
				osw.write(this.output_in);
			}
			osw.flush();
			os.flush();
			os.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}
