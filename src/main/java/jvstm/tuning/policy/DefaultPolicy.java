package jvstm.tuning.policy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningContext;
import jvstm.util.Pair;

public class DefaultPolicy extends TuningPolicy
{

	public DefaultPolicy(Controller controller)
	{
		super(controller);
		init();
		// TODO Auto-generated constructor stub
	}

	protected int runCount;
	private int interval;
	
	protected AdjustableSemaphore topLevelSemaphore;
	protected AdjustableSemaphore nestedSemaphore;

	private void init()
	{
		maxTopLevelThreads = new AtomicInteger(24);
		maxNestedThreads = new AtomicInteger(24);
		resetData();
		
		topLevelSemaphore = new AdjustableSemaphore(maxTopLevelThreads.get());
		nestedSemaphore = new AdjustableSemaphore(maxNestedThreads.get());
	}

	@Override
	public void clearInternalData()
	{
		resetData();
		init();
	}

	protected void resetData()
	{
		runCount = 0;
	}

	@Override
	public void run(boolean mergePerThreadStatistics)
	{
		System.err.println("\tPOLC - Waiting: " + topLevelSemaphore.getQueueLength() + " , " + nestedSemaphore.getQueueLength());
		runCount++;
	}

	@Override
	public Tunable newTunable()
	{
		return new ThreadState(ThreadState.RUNNABLE);
	}
	
	@Override
	public void finishTransaction(TuningContext t)
	{
		if (t.isNested())
		{
			nestedSemaphore.release();
			currentNestedThreads.decrementAndGet();
		} else
		{
			topLevelSemaphore.release();
			currentTopLevelThreads.decrementAndGet();
		}
		t.getThreadState().finish();
		t.getThreadState().setRunnable(false);
	}

	@Override
	public void tryRunTransaction(TuningContext t, boolean nested)
	{
		if (nested)
		{
			System.err.println("nested");
			nestedSemaphore.acquireUninterruptibly();
			currentNestedThreads.incrementAndGet();
		} else
		{
			topLevelSemaphore.acquireUninterruptibly();
			currentTopLevelThreads.incrementAndGet();
		}
		t.getThreadState().tryRun();
	}

	
}
