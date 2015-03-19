package jvstm.tuning;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import jvstm.Transaction;

public class Controller implements Runnable {

	private Semaphore topLevelSemaphore;
	private AtomicInteger maxTopLevelThreads;
	private AtomicInteger maxNestedThreads;

	private Map<Long, ThreadStatistics> topLevelStatistics;
	private Map<Long, ThreadStatistics> nestedStatistics;

	private ThreadStatistics globalTopLevelStatistics;
	private ThreadStatistics globalNestedStatistics;

	private Controller() {
		topLevelStatistics = new HashMap<Long, ThreadStatistics>();
		nestedStatistics = new HashMap<Long, ThreadStatistics>();
		topLevelSemaphore = new Semaphore(Integer.MAX_VALUE);
		
		maxTopLevelThreads = new AtomicInteger(Integer.MAX_VALUE);
		maxNestedThreads = new AtomicInteger(Transaction.getThreadPoolSize());
	}

	private static Controller instance;
	private static boolean running = false;

	public static Controller instance() {
		if (instance == null) {
			instance = new Controller();
		}

		return instance;
	}

	public void register(ThreadStatistics stats, boolean nested) {
		if (nested) {
			nestedStatistics.put(stats.getThreadId(), stats);
		} else {
			topLevelStatistics.put(stats.getThreadId(), stats);
		}
	}

	// to do: use lock?
	// does not cause memory consistency errors but is not thread-safe
	public void merge() {
		for (ThreadStatistics stat : topLevelStatistics.values()) {
			stat.addTo(globalTopLevelStatistics);
		}
		for (ThreadStatistics stat : nestedStatistics.values()) {
			stat.addTo(globalNestedStatistics);
		}
	}
	
	public static boolean isRunning() {
		return running;
	}
	
	protected static void setRunning(boolean isRunning) {
		running = isRunning;
	}

	/* for test purposes */
	private int originalNestedPoolSize;
	
	/* for test purposes */
	private int originalTopLevelSize;
	
	@Override
	public void run() {
		if(isRunning()) return; 
		originalNestedPoolSize = Transaction.getThreadPoolSize();
		originalTopLevelSize = maxTopLevelThreads.get();
		while (true) {
			try {
				Thread.sleep(100);
				merge();
				manageNestedPool();
				manageTopLevelThreads();
			} catch (InterruptedException e) {
			}
		}
	}
	
	public static void startThread() {
		if(isRunning()) return; 
		Thread controller = new Thread(instance());
		setRunning(true);
		controller.start();
	}

	protected void manageNestedPool() {
		if (Transaction.getThreadPoolSize() == originalNestedPoolSize) {
			Transaction.setThreadPoolSize(originalNestedPoolSize / 2);
		} else {
			Transaction.setThreadPoolSize(originalNestedPoolSize);
		}
	}
	
	protected void manageTopLevelThreads() {
	}

	public void acquireTopLevelTransactionPermit() {
		try {
			topLevelSemaphore.acquire();
		} catch (InterruptedException e) {
		}
	}

	public void releaseTopLevelTransactionPermit() {
		topLevelSemaphore.release();
	}

	public int getMaxTopLevelThreads() {
		return maxTopLevelThreads.get();
	}

	public void setMaxTopLevelThreads(int maxTopLevelThreads) {
		this.maxTopLevelThreads.set(maxTopLevelThreads);
	}

	public int getMaxNestedThreads() {
		return maxNestedThreads.get();
	}

	public void setMaxNestedThreads(int maxNestedThreads) {
		this.maxNestedThreads.set(maxNestedThreads);
	}

}
