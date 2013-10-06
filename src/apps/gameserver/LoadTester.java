import java.util.Random;

public class LoadTester implements Runnable {
    AIClient aic;
	Random r; 
	public LoadTester(Random r, String host, int host_port, int port) {
		try {
			this.aic = new AIClient(host, host_port, port);
		} catch (java.io.IOException ex) {
			ex.printStackTrace();
		}
		this.r = r;
	}

	public void run() {
		try {
			Thread.currentThread().sleep(r.nextInt(20000));
			aic.connect();
		} 
		catch (java.io.IOException ex) {
			ex.printStackTrace();
		}
		catch (InterruptedException ignore) { }
	}

	public AbstractClient getClient() {
		return aic;
	}
    
	public static void main(String[] args) throws java.io.IOException 
	{
		if (args.length != 3) {
			System.err.println("Usage: <clients> <host> <host-port>");
		}
		final int count = Integer.parseInt(args[0]);
		String host = args[1];
		int host_port = Integer.parseInt(args[2]);
		//System.out.println("host: " + host);
		Random r = new Random();
		final LoadTester[] testers = new LoadTester[count];
		for (int i=0;i<count;i++) {
			testers[i] = new LoadTester(r, host, host_port, 1024+i);
			new Thread(testers[i]).start();
		}
		new Thread(new Runnable() {
			public void run() {
				double total;
				long time = System.currentTimeMillis();
				while (System.currentTimeMillis()-time < 120000) {
					try {
						Thread.currentThread().sleep(5000);
					} catch (InterruptedException ex) {}
					total = 0;
					for (int i=0;i<count;i++)
						total+=testers[i].getClient().getAverageInterval();
					System.out.println(total/count);
				}
				System.exit(0);
			}
		}).start();
	}
}
