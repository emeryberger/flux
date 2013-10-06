package apps.jserver;

public class ServerConfig {
	static String root;
	static int port;
	
	static String getRoot() {
		return root;
	}
	
	static void setRoot(String root) {
		ServerConfig.root = root;
	}
	
	static int getPort() {
		return port;
	}
	
	static void setPort(int port) {
		ServerConfig.port = port;
	}
}
