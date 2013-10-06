package apps.jserver;

public class EventMain {
    public static void main(String[] args) {
	ServerConfig.setRoot(args[0]);
	ServerConfig.setPort(Integer.parseInt(args[1]));
	
	EventApp ea = new EventApp();
	new Thread(ea).start();
	ea.loop();
    }
}