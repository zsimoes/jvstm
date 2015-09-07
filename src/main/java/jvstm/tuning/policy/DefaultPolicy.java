package jvstm.tuning.policy;

import jvstm.Transaction;
import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;

public class DefaultPolicy extends TuningPolicy
{

	public DefaultPolicy(Controller controller)
	{
		super(controller);
		init();
		pointBinder.setMaximum(48);
		// TODO Auto-generated constructor stub
	}

	protected int runCount;
	private int interval;

	public AdjustableSemaphore topLevelSemaphore;
	public AdjustableSemaphore nestedSemaphore;

	private void init()
	{
		resetData();

		topLevelSemaphore = new AdjustableSemaphore(Integer.MAX_VALUE);
		nestedSemaphore = new AdjustableSemaphore(Integer.MAX_VALUE);
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
		// //System.err.println("\tPOLC - Waiting: " +
		// topLevelSemaphore.getQueueLength() + " , "
		// + nestedSemaphore.getQueueLength() + "(available: " +
		// nestedSemaphore.availablePermits() + ")");
		runCount++;
	}

	@Override
	public Tunable newTunable()
	{
		return new ThreadState(ThreadState.RUNNABLE);
	}

	@Override
	public void finishTransaction(Transaction t, boolean nested)
	{
		if (nested)
		{
			nestedSemaphore.release();
		} else
		{
			topLevelSemaphore.release();
		}
		t.getTuningContext().getThreadState().finish();
		t.getTuningContext().getThreadState().setRunnable(false);
	}

	@Override
	public void tryRunTransaction(Transaction t, boolean nested)
	{
		if (nested)
		{
			nestedSemaphore.acquireUninterruptibly();
		} else
		{
			topLevelSemaphore.acquireUninterruptibly();
		}
		t.getTuningContext().getThreadState().tryRun();
	}

}
