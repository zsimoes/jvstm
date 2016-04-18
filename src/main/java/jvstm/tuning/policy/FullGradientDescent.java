package jvstm.tuning.policy;

import jvstm.tuning.Controller;
import jvstm.tuning.TuningPoint;

public class FullGradientDescent extends LinearGradientDescent4
{

	// This class has the same behaviour as LinearGD, but different deltas.
	// LinearGD only takes horizontal/vertical jumps, while this one searches in
	// all directions
	
	static class FullGDPointProvider extends PointProvider
	{

		protected static final int fullGDSize = 8;
		protected final Delta[] deltas = new Delta[fullGDSize];
		protected int deltaIndex = 0;

		protected void incDeltaIndex()
		{
			deltaIndex++;
			deltaIndex %= fullGDSize;
		}

		public FullGDPointProvider(int roundSize, PointBinder pointBinder)
		{
			super(roundSize, pointBinder);

			deltas[0] = new Delta(0, 1);
			deltas[1] = new Delta(0, -1);
			deltas[2] = new Delta(1, 0);
			deltas[3] = new Delta(-1, 0);
			deltas[4] = new Delta(1, 1);
			deltas[5] = new Delta(1, -1);
			deltas[6] = new Delta(-1, 1);
			deltas[7] = new Delta(-1, -1);
		}

		@Override
		public TuningPoint getPointImpl()
		{
			TuningPoint point = deltas[deltaIndex].applyTo(currentFixedPoint);
			incDeltaIndex();
			return point;
		}
		
		@Override
		public TuningPoint getInitialPoint() {
			return pointBinder.getMidPoint();
		}

	}

	public FullGradientDescent(Controller controller)
	{
		super(controller);
	}
	
	@Override
	protected PointProvider createPointProvider() {
		return new FullGDPointProvider(9, pointBinder);
	}

}
