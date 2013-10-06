import java.net.*;
import javax.swing.JFrame;

public class GameClient extends AbstractClient {
    GameBoard gb;
    
	public GameClient
			(String server, int server_port, int recvPort, GameBoard gb) 
			throws java.io.IOException
	{
		super(server, server_port, recvPort);
		this.gb = gb;
	}

	public void connect() throws java.io.IOException {
		super.connect();
		gb.setId(id);
	}

	public void updatePlayer(int id, int x, int y, boolean it) {
		gb.updatePlayer(id, x, y, it);
	}

	public static void main(String[] args) throws java.io.IOException {
		int port = Integer.parseInt(args[1]);
		GameBoard gb = new GameBoard(640,480);

		GameClient gc = new GameClient(args[0], 8118, port, gb);
		gc.connect();
	

		JFrame mainframe = new JFrame("Game");
		mainframe.getContentPane().setLayout(new java.awt.FlowLayout());
		GameKeyListener gkl = new GameKeyListener(gc);
	
		mainframe.addKeyListener(gkl);
		gb.addKeyListener(gkl);

		mainframe.getContentPane().add(gb);
		mainframe.pack();
		mainframe.setVisible(true);
	}
}
