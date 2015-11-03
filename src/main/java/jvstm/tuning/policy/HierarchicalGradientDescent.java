package jvstm.tuning.policy;

import jvstm.tuning.Controller;
import jvstm.tuning.TuningPoint;
import jvstm.util.Pair;

public class HierarchicalGradientDescent extends LinearGradientDescent4
{

	// HierarchicalGD

	static class HierarchicalGDPointProvider extends PointProvider
	{

		protected final Delta deltaY = new Delta(0, 1);
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
			// System.err.println("----");
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

			//System.err.println(point.toString() + "(first)");
		}

		public TuningPoint initRound()
		{
			//System.err.println("----");
			// debug
			assert currentRecord.measurement >= 0;

			// determine direction to follow: true is positive X, false is
			// negative X
			if (info.size() < 2)
			{
				// set direction to positive X:
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
			int nextX;
			if (direction == true)
			{
				// increment X
				nextX = currentFixedPoint.first + 1;
				if (nextX > pointBinder.getMaximum())
				{
					// have reached the maximum? invert
					nextX = currentFixedPoint.first - 1;
					direction = false;
				}

			} else
			{
				// decrement X
				nextX = currentFixedPoint.first - 1;
				if (nextX < 1)
				{
					// have reached the minimum? invert
					nextX = currentFixedPoint.first + 1;
					direction = true;
				}
			}
			// set X to 1
			currentFixedPoint = new TuningPoint(nextX, 1);
			remainingPoints = getScanlineMax() - 1;

			TuningRoundInfo nextRound = new TuningRoundInfo();
			info.add(nextRound);
			currentRound = nextRound;

			currentRecord = new TuningRecord(currentFixedPoint, -1);
			currentRound.add(currentRecord);

			assertValidPoint(currentFixedPoint);
			//System.err.println(currentFixedPoint.toString());

			if (isFirstRound())
			{
				firstRound = false;
			}

			return currentFixedPoint;
		}

		protected int getScanlineMax()
		{
			int currentX = currentFixedPoint.first;
			// x*y < MAX ==> maxY = MAX/currentX
			return (int) pointBinder.getMaximum() / currentX;
		}

		@Override
		protected Pair<Integer, TuningPoint> getPoint()
		{
			TuningPoint point = doGetPoint();
			//System.err.println(point.toString());
			// Hierarchical GD never tries to get a point more than once:
			return new Pair<Integer, TuningPoint>(1, point);
		}

		@Override
		public TuningPoint doGetPoint()
		{
			// save current record
			if (remainingPoints <= 0)
			{
				throw new RuntimeException("Debug: Hierarchical GD - remaining points < 0 - should not happen.");
			}
			remainingPoints--;
			currentFixedPoint = deltaY.applyTo(currentFixedPoint);
			// is the current point out of bounds? Set x to 1
			if (!pointBinder.isBound(currentFixedPoint))
			{
				currentFixedPoint.second = 1;
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
