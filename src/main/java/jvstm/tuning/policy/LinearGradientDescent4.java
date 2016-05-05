package jvstm.tuning.policy;

import jvstm.Transaction;
import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.Parameters;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningPoint;

public class LinearGradientDescent4 extends SemaphorePolicy
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

	public LinearGradientDescent4(Controller controller)
	{
		super(controller);
	}

	protected void init()
	{
		this.pointProvider = createPointProvider();
		TuningPoint initialConfig = Parameters.initialConfig;
		if (initialConfig == null)
		{
			initialConfig = pointProvider.getInitialPoint();
		}

		topLevelSemaphore = new AdjustableSemaphore(initialConfig.first);
		int nested = initialConfig.first * initialConfig.second;
		nestedSemaphore = new AdjustableSemaphore(nested);

		currentPoint = new TuningPoint(initialConfig.first, nested);
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
}
