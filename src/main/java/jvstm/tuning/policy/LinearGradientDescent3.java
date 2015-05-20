package jvstm.tuning.policy;

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

public class LinearGradientDescent3 extends TuningPolicy
{

	/*
	 * This is a LinearGD implementation which uses semaphores to control the
	 * number of allowed nested and top-level transactions
	 */

	public static Pair<Integer, Integer>[] deltas;

	// Java doesn't allow generic arrays, or generic array initializers, so we
	// fill the deltas in the static constructor
	static
	{
		deltas = new Pair[4];
		deltas[0] = new Pair<Integer, Integer>(1, 0);
		deltas[1] = new Pair<Integer, Integer>(-1, 0);
		deltas[2] = new Pair<Integer, Integer>(0, 1);
		deltas[3] = new Pair<Integer, Integer>(0, -1);

		roundSize = deltas.length;
	}

	protected Pair<Integer, Integer> bestPoint;
	protected Pair<Integer, Integer> previousBestPoint;
	protected Pair<Integer, Integer> currentPoint;
	protected Pair<Integer, Integer> currentFixedPoint;
	protected float bestTCR;
	protected static float tcrEpsilon = 0.01f;
	protected static int roundSize;
	protected int runCount;

	protected AdjustableSemaphore topLevelSemaphore;
	protected AdjustableSemaphore nestedSemaphore;

	public LinearGradientDescent3(Controller controller)
	{
		super(controller);
		init();
	}

	private void init()
	{
		firstRound = true;
		Pair<Integer, Integer> mean = curveBinder.getMidPoint();
		maxNestedThreads = new AtomicInteger(mean.first);
		maxTopLevelThreads = new AtomicInteger(mean.second);

		topLevelSemaphore = new AdjustableSemaphore(maxTopLevelThreads.get());
		nestedSemaphore = new AdjustableSemaphore(maxNestedThreads.get());

		bestPoint = new Pair<Integer, Integer>(this.maxTopLevelThreads.get(), this.maxNestedThreads.get());
		previousBestPoint = new Pair<Integer, Integer>(this.maxTopLevelThreads.get(), this.maxNestedThreads.get());
		currentFixedPoint = new Pair<Integer, Integer>(this.maxTopLevelThreads.get(), this.maxNestedThreads.get());
		currentPoint = new Pair<Integer, Integer>(this.maxTopLevelThreads.get(), this.maxNestedThreads.get());
		resetData();
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
		bestTCR = 0f;
	}

	private boolean firstRound = true;

	@Override
	public void run(boolean mergePerThreadStatistics)
	{

		boolean done = false;
		while (!done)
		{

			firstRound = false;

			if (mergePerThreadStatistics)
			{
				mergeStatistics();
			}

			float tcr = GDSaveTCR();

			if (runCount % roundSize == 0)
			{
				GDEndRound(tcr);
			}

			done = GDNextRun();

			runCount++;
		}
	}

	protected float GDSaveTCR()
	{
		float tcr = getMeasurement(true);
		if (tcr > bestTCR)
		{
			setBestPoint(currentPoint.first, currentPoint.second);
			bestTCR = tcr;
		}

		return tcr;
	}

	// used to alternate between increasing top-level and nested threads
	private boolean incX = true;

	protected void GDEndRound(float tcr)
	{
		// have we stalled the system?
		if ((tcr < tcrEpsilon) && bestPoint.equals(previousBestPoint))
		{
			// alternate between increasing nested or top-level
			int deltaX = (incX ? deltas[0].first : deltas[2].first);
			int deltaY = (incX ? deltas[0].second : deltas[2].second);
			incX = !incX;
			bestPoint.first = currentFixedPoint.first + deltaX;
			bestPoint.second = currentFixedPoint.second + deltaY;
		}
		// set the current point and set max threads accordingly:
		setCurrentPoint(bestPoint);
		setCurrentFixedPoint(bestPoint);
		setPreviousBestPoint(bestPoint);

		// reset count and bestTCR:
		resetData();
	}

	protected boolean GDNextRun()
	{
		// set the current point and max threads accordingly:
		int newTopLevel = currentFixedPoint.first + deltas[runCount].first;
		int newNested = currentFixedPoint.second + deltas[runCount].second;
		if (newTopLevel < 1 || newNested < 1)
		{
			// this move would take us to a negative value. Return false to
			// ensure this run is repeated with other point.
			return false;
		}

		setCurrentPoint(newTopLevel, newNested);
		return true;
	}

	protected void setBestPoint(int topLevel, int nested)
	{
		this.bestPoint.first = topLevel;
		this.bestPoint.second = nested;
	}

	protected void setPreviousBestPoint(Pair<Integer, Integer> point)
	{
		this.previousBestPoint.first = point.first;
		this.previousBestPoint.second = point.second;
	}

	protected void setCurrentFixedPoint(Pair<Integer, Integer> point)
	{
		this.currentFixedPoint.first = point.first;
		this.currentFixedPoint.second = point.second;
	}

	protected void setCurrentPoint(int topLevel, int nested)
	{
		int previousX = this.currentPoint.first;
		int previousY = this.currentPoint.second;
		this.currentPoint.first = topLevel;
		this.currentPoint.second = nested;
		this.currentTopLevelThreads.set(topLevel);
		this.currentNestedThreads.set(nested);
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

	protected void setCurrentPoint(Pair<Integer, Integer> point)
	{
		setCurrentPoint(point.first, point.second);
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
