package jvstm.tuning.policy;

import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.Parameters;
import jvstm.tuning.TuningPoint;
import jvstm.tuning.policy.PointProvider.TuningRecord;
import jvstm.tuning.policy.PointProvider.TuningRoundInfo;
import jvstm.util.Pair;

public class F2C2 extends SemaphorePolicy
{

	static class F2C2PointProvider extends PointProvider
	{
		public enum Mode
		{
			explore, exploit
		}

		public class EndExplorationException extends RuntimeException
		{
			private static final long serialVersionUID = -2017417001025021520L;
		}

		protected Mode mode;
		// In F2C2 a round ends when we can't exponentially increase the point's
		// coordinates further because we've reached the maxThreads limit.
		protected boolean roundEnd;
		protected int exploredSamples = 0;
		protected int exploitedSamples = 0;
		private boolean firstRound = true;

		public F2C2PointProvider(PointBinder pointBinder)
		{
			super(-1, pointBinder);
			mode = Mode.explore;
			roundEnd = false;
		}

		@Override
		public TuningPoint getPoint(float previousThroughput, float previousTCR)
		{
			saveCurrentPoint(previousThroughput, previousTCR);

			TuningPoint point = null;
			if (mode == Mode.exploit)
			{
				// System.err.println("GetPoint(): Exploit");
				point = exploit();
			} else if (mode == Mode.explore)
			{
				// System.err.println("GetPoint(): Explore");
				point = explore(false);
			} else
			{
				throw new RuntimeException("F2C2PointProvider: Invalid mode, shouldn't happen. Fix me.");
			}

			currentRecord = new TuningRecord(point, -1, -1);
			currentRound.add(currentRecord);

			// System.err.println("GetPoint(): Result " + point);
			return point;
		}

		protected TuningPoint explore(boolean reset)
		{
			TuningPoint point = null;
			exploredSamples++;
			// System.err.println("ExploredSamples: " + exploredSamples);
			try
			{
				if (reset)
				{
					// [1,1] should always be valid, so no further checks needed
					point = new TuningPoint(1, 1);
				} else
				{
					point = getPointImpl();
				}
			} catch (EndExplorationException eee)
			{
				// System.err.println("Explore(): stopping");
				// enough exploration. Start exploiting the best point.
				mode = Mode.exploit;
				exploitedSamples = 0;
				currentRound = new TuningRoundInfo();
				info.add(currentRound);
				point = exploit();

			}

			return point;
		}

		protected TuningPoint exploit()
		{

			TuningPoint point = null;
			exploitedSamples++;
			// System.err.println("ExploitedSamples: " + exploitedSamples);
			if (exploitedSamples > exploredSamples)
			{
				// System.err.println("exploit(): Stopping");
				// enough exploitation. Start exploring again.
				mode = Mode.explore;
				exploredSamples = 0;
				currentRound = new TuningRoundInfo();
				info.add(currentRound);
				point = explore(true);

			} else
			{
				// exploit previous round's best point:
				point = info.get(info.size() - 2).getBest().getPoint();
			}
			return point;
		}

		@Override
		// In effect, this method returns a new point for EXPLORATION only.
		protected TuningPoint getPointImpl()
		{
			if (mode != Mode.explore)
			{
				throw new RuntimeException(
						"F2C2PointProvider: This doesn't happen. You're seeing a ghost. (Fix me, obviously)");
			}

			TuningPoint point = null;

			// Inc Nested exponentially:
			int x = currentRecord.getPoint().first;
			int y = currentRecord.getPoint().second * 2;
			point = new TuningPoint(x, y);

			if (!pointBinder.isBound(point))
			{
				// round end, inc top exponentially and set nested to 1.
				point.first *= 2;
				point.second = 1;

				if (!pointBinder.isBound(point))
				{
					// we've search the whole search space (exponentially).
					// Start exploiting.
					throw new EndExplorationException();

				}
			}
			return point;
		}

		@Override
		public boolean isRoundEnd()
		{
			return false;
		}

		@Override
		public TuningPoint getInitialPoint()
		{
			TuningPoint initial = new TuningPoint(1, 1);
			return initial;
		}
		
		@Override
		public boolean isFirstRound() {
			if(firstRound ) {
				firstRound = false;
				return true;
			}
			return firstRound;
		}
		
		@Override
		public void initRound(TuningPoint point)
		{
			// ignore round count and currentFixedPoint
			TuningRoundInfo nextRound = new TuningRoundInfo();
			info.add(nextRound);

			currentRecord = new TuningRecord(point, -1, -1);
			nextRound.add(currentRecord);
		}

		@Override
		protected Pair<Integer, TuningPoint> getPoint()
		{
			throw new UnsupportedOperationException();
		}

	}

	public F2C2(Controller controller)
	{
		super(controller);
		init();
	}

	@Override
	protected void init()
	{
		this.pointProvider = createPointProvider();
		TuningPoint initialConfig = pointProvider.getInitialPoint();

		topLevelSemaphore = new AdjustableSemaphore(initialConfig.first);
		int nested = initialConfig.first * initialConfig.second;
		nestedSemaphore = new AdjustableSemaphore(nested);
		currentPoint = new TuningPoint(initialConfig.first, nested);

	}

	@Override
	public void clearInternalData()
	{
		// nothing

	}
	
	@Override
	protected PointProvider createPointProvider()
	{
		return new F2C2PointProvider(pointBinder);
	}

}
