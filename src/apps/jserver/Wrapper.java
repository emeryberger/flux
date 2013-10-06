package apps.jserver;

public class Wrapper {
	int i;
	char c;
	Object o;
	
	int type=-1;
	
	private static int _INT_TYPE=1;
	private static int _CHAR_TYPE=2;
	private static int _OBJECT_TYPE=3;
	
	public Wrapper(Object o) {
		this.o = o;
		this.type = _OBJECT_TYPE;
	}
	
	public Wrapper(int i) {
		this.i = i;
		this.type = _INT_TYPE;
	}
	
	public Wrapper(char c) {
		this.c = c;
		this.type = _CHAR_TYPE;
	}
	
	public void set(int i) {
		if (this.type == _INT_TYPE)
			this.i = i;
		else
			throw new IllegalStateException("Wrong Wrapper...");
	}
	
	public void set(char c) {
		if (this.type == _CHAR_TYPE)
			this.c = c;
		else
			throw new IllegalStateException("Wrong Wrapper...");
	}
	
	public void set(Object o) {
		if (this.type == _OBJECT_TYPE)
			this.o = o;
		else
			throw new IllegalStateException("Wrong Wrapper...");
	}
}

