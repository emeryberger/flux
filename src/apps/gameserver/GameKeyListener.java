import java.awt.event.*;

public class GameKeyListener implements KeyListener {
    GameClient client;
    
	public GameKeyListener(GameClient gc) {
		this.client = gc;
	}

	public void keyPressed(KeyEvent e) {
		switch (e.getKeyCode()) {
			case KeyEvent.VK_W:
				client.up();
				break;
			case KeyEvent.VK_S:
				client.down();
				break;
			case KeyEvent.VK_A:
				client.left();
				break;
			case KeyEvent.VK_D:
				client.right();
				break;
			case KeyEvent.VK_ESCAPE:
				client.disconnect();
				System.exit(0);
				break;
		}
	    
	}

	public void keyTyped(KeyEvent e) {
	}

	public void keyReleased(KeyEvent e) {
	}
}
