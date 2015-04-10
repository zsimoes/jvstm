package jvstm.tuning;

import java.util.concurrent.atomic.AtomicInteger;

import jvstm.tuning.policy.DiagonalGradientDescent;
import jvstm.tuning.policy.IndependentGradientDescent;
import jvstm.tuning.policy.LinearGradientDescent;
import jvstm.tuning.policy.TuningPolicy;

public class Controller implements Runnable {

	// Region tuning fields
	private TuningPolicy policy;
	// EndRegion

	// Region singleton
	private Controller() {
		policy = new IndependentGradientDescent();
	}

	private static Controller instance;
	private static boolean running = false;

	public static Controller instance() {
		if (instance == null) {
			instance = new Controller();
		}

		return instance;
	}

	public static boolean isRunning() {
		return running;
	}

	protected static void setRunning(boolean isRunning) {
		running = isRunning;
	}

	// EndRegion

	public void registerContext(TuningContext context) {
		policy.registerContext(context);
	}

	// TODO: change boolean to nestinglevel
	public TuningContext registerThread(long threadId, boolean nested) {
		return policy.registerThread(threadId, nested);
	}

	// Region tuning

	@Override
	public void run() {
		while (true) {
			try {
				Thread.sleep(300);
				policy.run(true);
			} catch (InterruptedException e) {
			}
		}
	}

	public static void startThread() {
		if (isRunning())
			return;
		Thread controller = new Thread(instance());
		controller.setDaemon(true);
		setRunning(true);
		controller.start();
	}

	public void finishTransaction(TuningContext t) {
		policy.finishTransaction(t);
	}

	public void tryRunTransaction(TuningContext t) {
		policy.tryRunTransaction(t);
	}

	public AtomicInteger getMaxTopLevelThreads() {
		return policy.getMaxTopLevelThreads();
	}

	public AtomicInteger getMaxNestedThreads() {
		return policy.getMaxNestedThreads();
	}

	// EndRegion

}
