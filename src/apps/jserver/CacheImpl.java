package apps.jserver;

public class CacheImpl extends CacheBase {
    public void execute() {
	Page p = PageCache.instance().lookup(this.file_in);
	if (p == null) {
	    try {
		p = new Page(this.file_in);
		PageCache.instance().put(p, this.file_in);
	    } catch (java.io.IOException ex) {
		ex.printStackTrace();
	    }
	}
	this.socket_out = this.socket_in;
	this.length_out = p.getLength();
	this.content_out = p.getContentType();
	this.output_out = p.getData();
    }
}

