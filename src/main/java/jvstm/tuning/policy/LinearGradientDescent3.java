package jvstm.tuning.policy;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import jvstm.Transaction;
import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;
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

	protected AtomicInteger maxTopLevelThreads = new AtomicInteger(0);
	protected AtomicInteger maxNestedThreads = new AtomicInteger(0);
	protected AtomicInteger currentTopLevelThreads = new AtomicInteger(0);
	protected AtomicInteger currentNestedThreads = new AtomicInteger(0);

	protected AdjustableSemaphore topLevelSemaphore;
	protected AdjustableSemaphore nestedSemaphore;

	public LinearGradientDescent3(Controller controller)
	{
		super(controller);
		init();
	}

	private void init()
	{
		Pair<Integer, Integer> config = Controller.getInitialConfiguration();
		if (config == null)
		{
			pointBinder.getMidPoint();
		}

		maxTopLevelThreads = new AtomicInteger(config.first);
		maxNestedThreads = new AtomicInteger(config.second);
		currentTopLevelThreads = new AtomicInteger(config.first);
		currentNestedThreads = new AtomicInteger(config.second);

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

	@Override
	public void run(boolean mergePerThreadStatistics)
	{

		boolean done = false;
		while (!done)
		{
			if (mergePerThreadStatistics)
			{
				mergeStatistics();
			}

			float measurement = GDSaveMeasurement();
			Pair<Integer, Integer> current = new Pair<Integer, Integer>(currentPoint.first, currentPoint.second);

			if (runCount % roundSize == 0)
			{
				float best = GDEndRound(measurement);

				alternativeMeasurements[runCount % roundSize] = measurement;
				alternatives[runCount % roundSize] = current;

				controller.getStatisticsCollector().recordTuningPoint(currentPoint, best, alternatives,
						alternativeMeasurements);
				Arrays.fill(alternatives, null);
				Arrays.fill(alternativeMeasurements, 0l);

				runCount++;
				return;
			}

			done = GDNextRun();

			if (done)
			{
				alternativeMeasurements[runCount % roundSize] = measurement;
				alternatives[runCount % roundSize] = current;
			}

			runCount++;
		}

	}

	private Pair<Integer, Integer>[] alternatives = new Pair[4];
	private float[] alternativeMeasurements = new float[4];

	protected float GDSaveMeasurement()
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

	protected float GDEndRound(float tcr)
	{
		// have we stalled the system?
		/*
		 * if ((tcr < tcrEpsilon) && bestPoint.equals(previousBestPoint)) { //
		 * alternate between increasing nested or top-level int deltaX = (incX ?
		 * deltas[0].first : deltas[2].first); int deltaY = (incX ?
		 * deltas[0].second : deltas[2].second); incX = !incX; bestPoint =
		 * curveBinder.constrain(new Pair<Integer,
		 * Integer>(currentFixedPoint.first, currentFixedPoint.second +
		 * deltaY)); }
		 */
		// set the current point and set max threads accordingly:
		setCurrentPoint(bestPoint);
		setCurrentFixedPoint(bestPoint);
		setPreviousBestPoint(bestPoint);

		float res = bestTCR;
		// System.err.println("\t" + currentPoint);
		// reset count and bestTCR:
		resetData();
		return res;
	}

	protected boolean GDNextRun()
	{
		// set the current point and max threads accordingly:
		int newTopLevel = currentFixedPoint.first + deltas[runCount].first;
		int newNested = currentFixedPoint.second + deltas[runCount].second;
		if (!pointBinder.isBound(newTopLevel, newNested))
		{
			// this move would take us to an invalid value. Return false to
			// ensure this run is repeated with other point.
			return false;
		}

		setCurrentPoint(newTopLevel, newNested);
		// System.err.println(currentPoint);
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
