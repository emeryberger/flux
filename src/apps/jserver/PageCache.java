package apps.jserver;

import java.util.Hashtable;

public class PageCache {
	protected Hashtable<String, Page> cache;
	private static PageCache _instance = null;
	
	private PageCache() {
		this.cache = new Hashtable<String, Page>();
	}
	
	public static PageCache instance() {
		if (_instance == null)
			_instance = new PageCache();
		return _instance;
	}
	
	public Page lookup(String file) {
		return cache.get(file);
	}
	
	public void put(Page p, String file) {
		cache.put(file, p);
	}
}

