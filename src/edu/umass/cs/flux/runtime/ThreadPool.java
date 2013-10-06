package edu.umass.cs.flux.runtime;

import java.util.LinkedList;

public class ThreadPool {
	LinkedList queue;
	Thread[] workers;
	
	public static class ThreadWorker implements Runnable {
		ThreadPool pool;
		int id;
		boolean running;
		
		public ThreadWorker(ThreadPool pool, int id) {
			this.pool = pool;
			this.id = id;
		}
		
		public int getId() {
			return id;
		}
		
		public void stop() {
			running = false;
		}
		
		public void run() {
			running = true;
			while (running) {
			    Runnable r = pool.getTask();
			    if (r != null)
				r.run();
			    else
				synchronized(pool) {
				    try {
					pool.wait();
				    } catch (InterruptedException ignore) {}
				}
			}
		}
	}
	
	public ThreadPool(int size) {
		queue = new LinkedList();
		
		workers = new Thread[size];
		for (int i=0;i<size;i++) {
			workers[i] = new Thread(new ThreadWorker(this, i));
			workers[i].start();
		}
	}
	
	
	public synchronized void queueTask(Runnable r) {
		queue.addLast(r);
		notifyAll();
	}
	
	public synchronized Runnable getTask() {
	    if (queue.isEmpty()) 
		return null;
	    return (Runnable)queue.removeFirst();
	}
	
	public void finish() throws InterruptedException {
		for (int i=0;i<workers.length;i++)
			workers[i].join();
	}
}
