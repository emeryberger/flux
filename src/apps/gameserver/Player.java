public class Player {
	int x, y;
	int id;
    
	boolean it;
    
	public Player(int id) {
		this.x = 0;
		this.y = 0;
		this.it = false;
		this.id = id;
	}

	public void setX(int x) { this.x = x; }
	public void setY(int y) { this.y = y; }
	public void setIt(boolean it) { this.it = it; }

	public int getX() { return x; }
	public int getY() { return y; }
	public boolean getIt() { return it; }
	public int getId() { return id; }
}
