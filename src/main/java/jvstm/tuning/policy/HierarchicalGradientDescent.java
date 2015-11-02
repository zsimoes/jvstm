package jvstm.tuning.policy;

import jvstm.tuning.Controller;
import jvstm.tuning.TuningPoint;
import jvstm.util.Pair;

public class HierarchicalGradientDescent extends LinearGradientDescent4
{

	// This class has the same behaviour as LinearGD, but different deltas.
	// LinearGD only takes horizontal/vertical jumps, while this one searches in
	// all directions

	static class HierarchicalGDPointProvider extends PointProvider
	{

		protected final Delta deltaX = new Delta(1, 0);
		protected int remainingPoints;
		protected boolean firstRound = true;
		// true is positive, false is negative:
		protected boolean direction;

		public HierarchicalGDPointProvider(PointBinder pointBinder)
		{
			super(-1, pointBinder);
			this.roundSize = 1000;
		}

		@Override
		public boolean isRoundEnd()
		{
			return remainingPoints <= 0;
		}

		@Override
		public boolean isFirstRound()
		{
			return firstRound;
		}

		/*
		 * Start a round with a preselected point.
		 */
		public void initRound(TuningPoint point)
		{
			System.err.println("----");
			if (firstRound)
			{
				firstRound = false;
			}
			assertValidPoint(point);

			currentFixedPoint = point;
			remainingPoints = getScanlineMax() - 1;

			currentRound = new TuningRoundInfo();
			info.add(currentRound);

			currentRecord = new TuningRecord(point, -1);
			currentRound.add(currentRecord);

			System.err.println(point.toString() + "(first)");
		}

		public TuningPoint initRound()
		{
			System.err.println("----");
			// debug
			assert currentRecord.measurement >= 0;

			// determine direction to follow: true is positive Y, false is
			// negative Y
			if (info.size() < 2)
			{
				// set direction to positive Y:
				direction = true;
			} else
			{
				TuningRoundInfo previousRound = info.get(info.size() - 2);
				float previousBestMeasure = previousRound.getBest().measurement;
				float currentBestMeasure = currentRound.getBest().measurement;
				// determine current direction:
				// if this direction lead to a worse measurement invert it:
				if (currentBestMeasure < previousBestMeasure)
				{
					direction = !direction;
				}
			}
			int nextY;
			if (direction == true)
			{
				// increment Y
				nextY = currentFixedPoint.second + 1;
				if (nextY > pointBinder.getMaximum())
				{
					// have reached the maximum? invert
					nextY = currentFixedPoint.second - 1;
					direction = false;
				}
				
			} else
			{
				// decrement Y
				nextY = currentFixedPoint.second - 1;
				if (nextY < 1)
				{
					// have reached the minimum? invert
					nextY = currentFixedPoint.second + 1;
					direction = true;
				}
			}
			// set X to 1
			currentFixedPoint = new TuningPoint(1, nextY);
			remainingPoints = getScanlineMax() - 1;

			TuningRoundInfo nextRound = new TuningRoundInfo();
			info.add(nextRound);
			currentRound = nextRound;

			currentRecord = new TuningRecord(currentFixedPoint, -1);
			currentRound.add(currentRecord);

			assertValidPoint(currentFixedPoint);
			System.err.println(currentFixedPoint.toString());

			if (isFirstRound())
			{
				firstRound = false;
			}

			return currentFixedPoint;
		}

		protected int getScanlineMax()
		{
			int currentY = currentFixedPoint.second;
			// x*y < MAX ==> maxX = MAX/currentY
			return pointBinder.getMaximum() / currentY;
		}

		@Override
		protected Pair<Integer, TuningPoint> getPoint()
		{
			TuningPoint point = doGetPoint();
			System.err.println(point.toString());
			// Hierarchical GD never tries to get a point more than once:
			return new Pair<Integer, TuningPoint>(1, point);
		}

		@Override
		public TuningPoint doGetPoint()
		{
			// save current record
			if (remainingPoints <= 0)
			{
				throw new RuntimeException("Debug: Hierarchical GD - ramining points < 0 - should not happen.");
			}
			remainingPoints--;
			currentFixedPoint = deltaX.applyTo(currentFixedPoint);
			// is the current point out of bounds? Set x to 1
			if (!pointBinder.isBound(currentFixedPoint))
			{
				currentFixedPoint.first = 1;
			}
			return currentFixedPoint;
		}

		@Override
		public TuningPoint getInitialPoint()
		{
			return pointBinder.getMidPoint();
		}

	}

	public HierarchicalGradientDescent(Controller controller)
	{
		super(controller);
	}

	@Override
	protected PointProvider createPointProvider()
	{
		return new HierarchicalGDPointProvider(pointBinder);
	}

}
