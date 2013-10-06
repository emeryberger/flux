package apps.jserver;

public class ThreadMain {
	public static void main(String[] args) {
		ServerConfig.setRoot(args[0]);
		ServerConfig.setPort(Integer.parseInt(args[1]));
		
		AppThread.loop();
	}
}
