package jvstm.tuning.policy;

import jvstm.Transaction;
import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.Parameters;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;
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
		public TuningPoint getPointImpl()
		{
			TuningPoint point = deltas[deltaIndex].applyTo(currentFixedPoint);
			incDeltaIndex();
			return point;
		}

		@Override
		public TuningPoint getInitialPoint()
		{
			return pointBinder.getMidPoint();
		}

	}

	protected AdjustableSemaphore topLevelSemaphore;
	protected AdjustableSemaphore nestedSemaphore;

	protected TuningPoint currentPoint;

	public LinearGradientDescent4(Controller controller)
	{
		super(controller);
		init();
	}

	protected void init()
	{
		TuningPoint initialConfig = Parameters.initialConfig;
		if (initialConfig == null)
		{
			initialConfig = pointProvider.getInitialPoint();
		}

		topLevelSemaphore = new AdjustableSemaphore(initialConfig.first);
		int nested = initialConfig.first * initialConfig.second;
		nestedSemaphore = new AdjustableSemaphore(nested);

		currentPoint = new TuningPoint(initialConfig.first, nested);
		this.pointProvider = createPointProvider();
	}

	@Override
	protected PointProvider createPointProvider()
	{
		return new LinearGDPointProvider(5, pointBinder);
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
			TuningPoint point = Parameters.initialConfig;
			pointProvider.initRound(point);

			// System.err.println("# GD4 FIRST ROUND: new fixed Point is " +
			// point);
			setCurrentPoint(point);
			return;
		}

		// save current point
		float throughput = getThroughput(true), tcr = getTCR(true);
		pointProvider.saveCurrentPoint(throughput, tcr);
		// System.err.println("# Measurement: " + measure);

		TuningPoint point = null;

		if (pointProvider.isRoundEnd())
		{
			point = pointProvider.initRound();
			/*
			 * System.err.println("# GD4 ROUND END: new fixed Point is " + point
			 * + System.lineSeparator() +
			 * "___________________________________________________");
			 */
		} else
		{
			// start round with best point from previous round:
			point = pointProvider.requestPoint(throughput, tcr);
			// System.err.println("GD4 round: new Point is " + point);
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
}
