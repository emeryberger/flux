import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import javax.swing.*;

public class GameBoard 
    extends JPanel 
{
	Dimension d;
	Vector<Player> players;
	int id = -1;

	public GameBoard(int width, int height) {
		this.d = new Dimension(width, height);
		this.players = new Vector<Player>();
	}

	public void setId(int id) {
		this.id = id;
	}

	public void updatePlayer(int id, int x, int y, boolean it) {
		for (Player p : players) {
			if (p.getId() == id) {
				p.setX(x);
				p.setY(y);
				p.setIt(it);
				repaint();
				return;
			}
		}
		Player p = new Player(id);
		p.setX(x);
		p.setY(y);
		p.setIt(it);
		players.add(p);
		repaint();
	}

	public void addPlayer(Player p) {
		players.add(p);
	}

	public Dimension getPreferredSize() {
		return d;
	}

	public Dimension getMinimumSize() {
		return d;
	}

	public void paint(Graphics g) {
		g.setColor(Color.white);
		g.fillRect(0,0, getWidth(), getHeight());
	
		for (Player p : players) {
			if (p.getIt()) {
				g.setColor(Color.red);
			}
			else if (p.getId() == id) {
				g.setColor(Color.green);
			}
			else {
				g.setColor(Color.blue);
			}
			g.fillRect(p.getX()*2-2,p.getY()*2-2,4,4);
		}
	}
}
