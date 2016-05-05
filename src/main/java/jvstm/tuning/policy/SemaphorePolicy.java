package jvstm.tuning.policy;

import jvstm.Transaction;
import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.Parameters;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningPoint;


/*
 * This is an abstract policy which uses semaphores to control the
 * number of allowed nested and top-level transactions
 */
public abstract class SemaphorePolicy extends TuningPolicy
{

	protected AdjustableSemaphore topLevelSemaphore;
	protected AdjustableSemaphore nestedSemaphore;

	protected TuningPoint currentPoint;

	public SemaphorePolicy(Controller controller)
	{
		super(controller);
		init();
	}

	protected abstract void init();

	@Override
	public abstract void clearInternalData();

	@Override
	public void run(boolean mergePerThreadStatistics)
	{
		if (mergePerThreadStatistics)
		{
			mergeStatistics();
		}
		
		if (pointProvider.isFirstRound())
		{
			TuningPoint point = pointProvider.getInitialPoint();
			pointProvider.initRound(point);

			// System.err.println("# GD4 FIRST ROUND: new fixed Point is " +
			// point);
			setCurrentPoint(point);
			return;
		}

		float throughput = getThroughput(true), tcr = getTCR(true);
		pointProvider.saveCurrentPoint(throughput, tcr);

		TuningPoint point = null;

		if (pointProvider.isRoundEnd())
		{
			point = pointProvider.initRound();
		} else
		{
			point = pointProvider.getPoint(throughput, tcr);
		}

		setCurrentPoint(point);

	}

	protected void setCurrentPoint(int topLevel, int nested)
	{
		int previousX = this.currentPoint.first;
		int previousY = this.currentPoint.second;
		this.currentPoint.first = topLevel;
		this.currentPoint.second = nested;

		if (topLevel > previousX)
		{
			topLevelSemaphore.release(topLevel - previousX);
		} else if (topLevel < previousX)
		{
			topLevelSemaphore.reducePermits(previousX - topLevel);
		}

		if (nested > previousY)
		{
			nestedSemaphore.release(nested - previousY);
		} else if (nested < previousY)
		{
			nestedSemaphore.reducePermits(previousY - nested);
		}
	}

	protected void setCurrentPoint(TuningPoint point)
	{
		setCurrentPoint(point.first, point.second);
	}

	@Override
	public Tunable newTunable()
	{
		return new ThreadState(ThreadState.RUNNABLE);
	}

	@SuppressWarnings("static-access")
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

	@SuppressWarnings("static-access")
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

	@Override
	protected abstract PointProvider createPointProvider();

}
