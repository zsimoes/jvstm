package jvstm.tuning.policy;

import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import jvstm.Transaction;
import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningContext;
import jvstm.tuning.TuningPoint;

public class LinearGradientDescent4 extends TuningPolicy
{

	/*
	 * This is a LinearGD implementation which uses semaphores to control the
	 * number of allowed nested and top-level transactions
	 */

	static class LinearGDPointProvider extends PointProvider
	{

		protected static final int linearGDSize = 4;
		protected final Delta[] deltas = new Delta[linearGDSize];
		protected int deltaIndex = 0;

		protected void incDeltaIndex()
		{
			deltaIndex++;
			deltaIndex %= linearGDSize;
		}

		public LinearGDPointProvider(int roundSize, PointBinder pointBinder)
		{
			super(roundSize, pointBinder);

			deltas[0] = new Delta(0, 1);
			deltas[1] = new Delta(0, -1);
			deltas[2] = new Delta(1, 0);
			deltas[3] = new Delta(-1, 0);
		}

		@Override
		public TuningPoint getNextPoint()
		{
			TuningPoint point = deltas[deltaIndex].applyTo(currentFixedPoint);
			incDeltaIndex();
			return point;
		}

	}

	protected float bestTCR;
	// protected static float tcrEpsilon = 0.01f;
	protected static final int roundSize = 5;

	protected AdjustableSemaphore topLevelSemaphore;
	protected AdjustableSemaphore nestedSemaphore;

	protected TuningPoint currentPoint;

	public LinearGradientDescent4(Controller controller)
	{
		super(controller);
		init();
	}

	private void init()
	{
		TuningPoint config = Controller.getInitialConfiguration();
		if (config == null)
		{
			pointBinder.getMidPoint();
		}

		maxTopLevelThreads = new AtomicInteger(config.first);
		maxNestedThreads = new AtomicInteger(config.second);

		if (topLevelSemaphore != null)
		{
			topLevelSemaphore.invalidate();
		}
		if (nestedSemaphore != null)
		{
			nestedSemaphore.invalidate();
		}
		topLevelSemaphore = new AdjustableSemaphore(maxTopLevelThreads.get());
		nestedSemaphore = new AdjustableSemaphore(maxNestedThreads.get());

		currentPoint = new TuningPoint(maxTopLevelThreads.get(), maxNestedThreads.get());
		this.pointProvider = new LinearGDPointProvider(5, pointBinder);
	}

	@Override
	public void clearInternalData()
	{
		init();
	}

	@Override
	public void run(boolean mergePerThreadStatistics)
	{
		if (mergePerThreadStatistics)
		{
			mergeStatistics();
		}

		if (pointProvider.isFirstRound())
		{
			TuningPoint point = Controller.getInitialConfiguration();
			pointProvider.initRound(point);

			System.err.println("# GD4 FIRST ROUND: new Point is " + point);
			setCurrentPoint(point);
			return;
		}

		// save current point
		float measure = getMeasurement(true);
		pointProvider.saveCurrentPoint(measure);

		TuningPoint point = null;

		if (pointProvider.isRoundEnd())
		{
			point = pointProvider.initRound();
			System.err.println("# GD4 ROUND END: new Point is " + point + System.lineSeparator()
					+ "___________________________________________________");
		} else
		{
			// start round with best point from previous round:
			point = pointProvider.requestPoint(measure);
			System.err.println("GD4 round: new Point is " + point);
		}

		setCurrentPoint(point);

	}

	protected void setCurrentPoint(int topLevel, int nested)
	{
		int previousX = this.currentPoint.first;
		int previousY = this.currentPoint.second;
		this.currentPoint.first = topLevel;
		this.currentPoint.second = nested;

		// this.currentTopLevelThreads.set(topLevel);
		// this.currentNestedThreads.set(nested);
		this.maxTopLevelThreads.set(topLevel);
		this.maxNestedThreads.set(nested);

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

	@Override
	public void finishTransaction(Transaction t, boolean nested)
	{
		if (nested)
		{
			nestedSemaphore.release();
			currentNestedThreads.decrementAndGet();
		} else
		{
			topLevelSemaphore.release();
			currentTopLevelThreads.decrementAndGet();
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
			currentNestedThreads.incrementAndGet();
		} else
		{
			topLevelSemaphore.acquireUninterruptibly();
			currentTopLevelThreads.incrementAndGet();
		}
		t.getTuningContext().getThreadState().tryRun();
	}
}
