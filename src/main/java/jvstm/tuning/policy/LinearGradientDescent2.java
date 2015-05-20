package jvstm.tuning.policy;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import jvstm.tuning.Controller;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningContext;
import jvstm.util.Pair;

public class LinearGradientDescent2 extends TuningPolicy
{

	/*
	 * This is a LinearGD implementation which uses a thread-safe wait queue for
	 * nested and top-level waiting threads
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

	protected Queue<TuningContext> topLevelWaitQueue;
	protected Queue<TuningContext> nestedWaitQueue;

	public LinearGradientDescent2(Controller controller)
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

		System.err.println("MEAN: " + mean);
		System.exit(0);

		topLevelWaitQueue = new ConcurrentLinkedQueue<TuningContext>();
		nestedWaitQueue = new ConcurrentLinkedQueue<TuningContext>();
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

	@Override
	public void run(boolean mergePerThreadStatistics)
	{

		boolean done = false;
		while (!done)
		{

			if (runCount < roundSize && firstRound)
			{
				// skip the first round
				runCount++;
				return;
			}

			if (runCount == roundSize && firstRound)
			{
				// we've skipped some rounds to achieve the workload's natural
				// size. Set the current state to this size, and explore from
				// here on.
				Pair<Integer, Integer> current = new Pair<Integer, Integer>(controller.getContexts().size(),
						controller.getContexts().size());
				System.err.println("Policy: skipped " + roundSize + "runs, changing from (" + maxTopLevelThreads.get()
						+ "," + maxNestedThreads.get() + ") to " + current);
				setCurrentPoint(current);
				setCurrentFixedPoint(current);
				firstRound = false;
			}

			if (mergePerThreadStatistics)
			{
				mergeStatistics();
			}

			float tcr = GDSaveTCR();

			if (runCount % roundSize == 0)
			{
				// HERE save X and Y independently (independent comparisons and
				// setX(), setY())
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
			// System.err.println("----> Stalled, increasing " + (incX ?
			// "top-level" : "nested") + " threads; " + topLevelWaitQueue.size()
			// + " toplevel waiting, " + nestedWaitQueue.size() +
			// " nested waiting.");
			bestPoint.first = currentFixedPoint.first + deltaX;
			bestPoint.second = currentFixedPoint.second + deltaY;
		}
		// System.err.println("----> New best Point with TCR " + bestTCR + ": "
		// + bestPoint);
		// set the current point and set max threads accordingly:
		setCurrentPoint(bestPoint);
		setCurrentFixedPoint(bestPoint);
		setPreviousBestPoint(bestPoint);

		// Note: previously I wasn't clearing <bestTCR> to account fot the
		// previous point's performance, but since this GD moves only one point
		// in any direction we'll eventually scan the previous point again, and
		// get it's TCR

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
		// System.err.println("\t>>new run point: {" + newTopLevel + "," +
		// newNested + "}");
		setCurrentPoint(newTopLevel, newNested);
		resumeWaitingThreads();
		return true;
	}

	protected void resumeWaitingThreads()
	{
		synchronized (this)
		{
			// are there threads waiting?
			while (!nestedWaitQueue.isEmpty())
			{
				if (this.maxNestedThreads.get() - this.currentNestedThreads.get() <= 0)
				{
					// no free slots, bail
					break;
				}
				// otherwise, resume a waiting thread
				TuningContext ctx = nestedWaitQueue.poll();
				ctx.getThreadState().setRunnable(true);
			}
		}
		// are there threads waiting?
		if (!topLevelWaitQueue.isEmpty() && (this.maxTopLevelThreads.get() - this.currentTopLevelThreads.get() > 0))
		{
			// System.err.println("->-> Replenishing " +
			// (this.maxTopLevelThreads.get() -
			// this.currentTopLevelThreads.get()));
		}
		synchronized (this)
		{
			while (!topLevelWaitQueue.isEmpty())
			{
				if (this.maxTopLevelThreads.get() - this.currentTopLevelThreads.get() <= 0)
				{
					// no free slots, bail
					break;
				}
				// otherwise, resume a waiting thread
				TuningContext ctx = topLevelWaitQueue.poll();
				ctx.getThreadState().setRunnable(true);
			}
		}
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
		this.currentPoint.first = topLevel;
		this.currentPoint.second = nested;
		this.currentTopLevelThreads.set(topLevel);
		this.currentNestedThreads.set(nested);
		this.maxTopLevelThreads.set(topLevel);
		this.maxNestedThreads.set(nested);
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
		synchronized (this)
		{
			boolean setWaiting = false;
			if (t.isNested())
			{
				currentNestedThreads.decrementAndGet();
				setWaiting = (this.currentNestedThreads.get() >= this.maxNestedThreads.get());
			} else
			{
				currentTopLevelThreads.decrementAndGet();
				setWaiting = (this.currentTopLevelThreads.get() >= this.maxTopLevelThreads.get());
			}
			if (setWaiting)
			{
				t.getThreadState().setWaiting();
			}
		}
		t.getThreadState().finish();
		resumeWaitingThreads();
	}

	@Override
	public void tryRunTransaction(TuningContext t, boolean nested)
	{
		boolean setWaiting = false;
		synchronized (this)
		{
			if (nested)
			{
				setWaiting = (this.currentNestedThreads.get() >= this.maxNestedThreads.get());
				if (!setWaiting)
				{
					currentNestedThreads.incrementAndGet();
				} else
				{
					this.nestedWaitQueue.add(t);
				}
			} else
			{
				setWaiting = (this.currentTopLevelThreads.get() >= this.maxTopLevelThreads.get());
				if (!setWaiting)
				{
					currentTopLevelThreads.incrementAndGet();
				} else
				{
					this.topLevelWaitQueue.add(t);
				}
			}
			if (setWaiting)
			{
				t.getThreadState().setWaiting();
			} else
			{
				t.getThreadState().setRunnable(true);
			}
		}
		t.getThreadState().tryRun();
		if (setWaiting)
		{
			if (nested)
			{
				currentNestedThreads.incrementAndGet();
			} else
			{
				currentTopLevelThreads.incrementAndGet();
			}
		}
	}
}
