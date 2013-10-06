import java.util.Vector;

public class AIClient extends AbstractClient {
    Vector<Player> players;
	int x, y;
	boolean imIt;
	Player it;
    
	protected int distance(Player p) {
		return (p.getX()-x)*(p.getX()-x)+(p.getY()-y)*(p.getY()-y);
	}
    
	class AIThread implements Runnable {
		java.util.Random r;

		public AIThread() {
			r = new java.util.Random();
		}

		public void run() {
			while (true) {
				if (!imIt) {
					moveAway(it);
				}
				else {
					int max_d = 0;
					Player goTo = null;
					for (Player p : players) {
						if (distance(p) > max_d) {
							goTo = p;
							max_d = distance(p);
						}
					}
					moveToward(goTo);
				}
				try {
					Thread.currentThread().sleep(500);
				} catch (InterruptedException e) {}
			}
		}
	
		public void moveToward(Player p) {
			if (p != null) {
				if (x > p.getX())
					left();
				else 
					right();
				if (y > p.getY())
					up();
				else
					down();
			}
			else {
				switch (r.nextInt(4)) {
					case 0:
						right();
						break;
					case 1:
						left();
						break;
					case 2:
						up();
						break;
					case 3:
						down();
						break;
				}
			}
		}

		public void moveAway(Player p) {
			if (p != null) {
				if (x > p.getX())
					right();
				else 
					left();
				if (y > p.getY())
					down();
				else
					up();
			}
			else {
				switch (r.nextInt(4)) {
					case 0:
						right();
						break;
					case 1:
						left();
						break;
					case 2:
						up();
						break;
					case 3:
						down();
						break;
				}
			}
		}
	}
    
	public AIClient(String server, int server_port, int recvPort) 
			throws java.io.IOException
	{
		super(server, server_port, recvPort);
		players = new Vector<Player>();
		x = y = 0;
		imIt = false;
		it = null;
	}

	public void connect() throws java.io.IOException {
		super.connect();
		new Thread(new AIThread()).start();
	}

	public void updatePlayer(int id, int x, int y, boolean it) {
		if (this.id == id) {
			this.x = x;
			this.y = y;
			this.imIt = it;
		}
		else {
			for (Player p : players) {
				if (p.getId() == id) {
					p.setX(x);
					p.setY(y);
					p.setIt(it);
					if (it)
						this.it = p;
					return;
				}
			}
			Player p = new Player(id);
			p.setX(x);
			p.setY(y);
			p.setIt(it);
			if (it)
				this.it = p;
			players.add(p);
		}
	}
    
	public static void main(String[] args) throws java.io.IOException 
	{
		int port = Integer.parseInt(args[1]);
		AIClient gc = new AIClient(args[0], 8118, port);
		gc.connect();
	}
}