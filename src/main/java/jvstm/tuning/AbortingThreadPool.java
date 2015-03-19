package jvstm.tuning;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AbortingThreadPool extends ThreadPoolExecutor {

	public static ExecutorService newAbortingThreadPool(int nThreads, ThreadFactory threadFactory) {
		return new AbortingThreadPool(nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),
                threadFactory);
	}

	
	public AbortingThreadPool(int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
	}

	public AbortingThreadPool(int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit,
			BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
	}

	public AbortingThreadPool(int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit,
			BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
			RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
				threadFactory, handler);
	}

	public AbortingThreadPool(int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit,
			BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
				threadFactory);
	}



	/**
	 * A runtime exception used to prematurely terminate threads in this pool.
	 */
	static class ShutdownException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		ShutdownException(String message) {
			super(message);
		}
	}

	/**
	 * This uncaught exception handler is used only as threads are entered into
	 * their shutdown state.
	 */
	static class ShutdownHandler implements UncaughtExceptionHandler {
		private UncaughtExceptionHandler handler;

		/**
		 * Create a new shutdown handler.
		 *
		 * @param handler
		 *            The original handler to delegate non-shutdown exceptions
		 *            to.
		 */
		ShutdownHandler(UncaughtExceptionHandler handler) {
			this.handler = handler;
		}

		/**
		 * Quietly ignore {@link ShutdownException}.
		 * <p>
		 * Do nothing if this is a ShutdownException, this is just to prevent
		 * logging an uncaught exception which is expected. Otherwise forward it
		 * to the thread group handler (which may hand it off to the default
		 * uncaught exception handler).
		 * </p>
		 */
		public void uncaughtException(Thread thread, Throwable throwable) {
			if (!(throwable instanceof ShutdownException)) {
				/*
				 * Use the original exception handler if one is available,
				 * otherwise use the group exception handler.
				 */
				if (handler != null) {
					handler.uncaughtException(thread, throwable);
				}
			}
		}
	}

	private Semaphore terminations = new Semaphore(0, true);
	
	@Override
	protected void beforeExecute(final Thread thread, final Runnable job) {
		if (terminations.tryAcquire()) {
			/*
			 * Replace this item in the queue so it may be executed by another
			 * thread
			 */
			getQueue().add(job);

			thread.setUncaughtExceptionHandler(new ShutdownHandler(thread
					.getUncaughtExceptionHandler()));

			/*
			 * Throwing a runtime exception is the only way to prematurely cause
			 * a worker thread from the TheadPoolExecutor to exit.
			 */
			throw new ShutdownException("Terminating thread");
		}
	}

	public void setCorePoolSize(final int size) {
		int delta = getActiveCount() - size;

		super.setCorePoolSize(size);

		if (delta > 0) {
			terminations.release(delta);
		}
	}

}
