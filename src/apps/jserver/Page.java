package apps.jserver;

import java.io.*;

public class Page {
	private String data;
	private String contentType;
	private String name;
	
	public Page(String file) 
	throws IOException
	{
		this.name = file;
		File f = new File(ServerConfig.getRoot()+File.separator+file);
		if (f.isDirectory()) {
			name = file+File.separator+"index.html";
			f = new File(f, "index.html");
		}
		if (f.isFile()) {
			FileInputStream fis = new FileInputStream(f);
			byte[] data = new byte[(int)f.length()];
			int rd = fis.read(data);
			while (rd < data.length)
				rd = fis.read(data, rd, data.length-rd);
			
			this.data = new String(data);
			
			if (name.endsWith(".html")) 
				contentType = "text/html";
			else if (name.endsWith(".png")) 
				contentType = "image/png";
			else if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
				contentType = "image/jpeg";
			else if (name.endsWith(".gif")) 
				contentType = "image/gif";
			else 
				contentType = "text/plain";
		}
		else {
			contentType = null;
			data = null;
		}
	}
	
	public int getLength() {
		if (data != null)
			return data.length();
		else
			return 0;
	}
	
	public String getData() {
		return data;
	}
	
	public String getContentType() {
		return contentType;
	}
	
	public int hashCode() {
		return name.hashCode();
	}
}



