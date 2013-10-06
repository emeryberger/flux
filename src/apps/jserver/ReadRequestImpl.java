package apps.jserver;

import java.io.*;

public class ReadRequestImpl extends ReadRequestBase {
	public void execute() {
		try {
			BufferedReader in = new BufferedReader
			(new InputStreamReader(this.socket_in.getInputStream()));
			
			String line = in.readLine();
			while (!(line.startsWith("GET") || line.startsWith("POST"))) {
				line = in.readLine();
			}
			int ix = line.indexOf(" ");
			int ix2 = line.indexOf(" ", ix+1);
			
			String req = line.substring(ix+1, ix2);
			if (req.startsWith("http://")) {
				ix = req.indexOf("/", 8);
				req = req.substring(ix, req.indexOf(" ", ix));
			}
			this.socket_out = this.socket_in;
			this.request_out = req;
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}
