package jvstm.tuning.policy;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import jvstm.tuning.TuningPoint;
import jvstm.util.Pair;

public abstract class PointProvider
{
	public static class TuningRecord
	{
		protected TuningPoint point;
		protected float throughput;
		protected float tcr;

		public TuningPoint getPoint()
		{
			return point;
		}

		public void setPoint(TuningPoint point)
		{
			this.point = point;
		}

		public float getThroughput()
		{
			return throughput;
		}

		public void setThroughput(float throughput)
		{
			this.throughput = throughput;
		}
		
		public float getTcr()
		{
			return tcr;
		}

		public void setTcr(float tcr)
		{
			this.tcr = tcr;
		}

		public TuningRecord(TuningPoint point, float throughput, float tcr)
		{
			this.point = point;
			this.throughput = throughput;
			this.tcr = tcr;
		}

		public String toStringThroughput()
		{
			return point.toString() + " {" + String.format("%.2f", throughput) + "}";
		}
		public String toStringTCR()
		{
			return point.toString() + " {" + String.format("%.2f", tcr) + "}";
		}
		
		public String toString()
		{
			return point.toString() + " {" + "thr: " + String.format("%.2f", throughput) + ",tcr: " + String.format("%.2f", tcr) + "}";
		}
	}

	public static class TuningRoundInfo
	{
		protected List<TuningRecord> alternatives;
		protected TuningRecord best;

		public List<TuningRecord> getAlternatives()
		{
			return alternatives;
		}

		public TuningRecord getBest()
		{
			if (best == null)
			{
				return new TuningRecord(null, Float.MIN_VALUE, Float.MIN_VALUE);
			}
			return best;
		}

		public void setBest(TuningRecord best)
		{
			this.best = best;
		}

		public void add(TuningRecord record)
		{

			alternatives.add(record);
			if (best == null)
			{
				best = record;
			} else if (record.throughput > best.throughput)
			{
				best = record;
			}
		}

		public TuningRoundInfo(int roundSize)
		{
			alternatives = new ArrayList<TuningRecord>(roundSize);
		}

		public TuningRoundInfo()
		{
			alternatives = new ArrayList<TuningRecord>();
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			for (TuningRecord rec : alternatives)
			{
				if (rec != null)
				{
					sb.append(rec.toStringThroughput() + " , ");
				}
			}
			return "BestPoint - " + best.toStringThroughput() + "  - Alts - " + sb.toString();
		}
	}

	public static class Delta extends Pair<Integer, Integer>
	{

		public Delta()
		{
			super();
		}

		public Delta(Integer first, Integer second)
		{
			super(first, second);
		}

		public TuningPoint applyTo(TuningPoint point)
		{
			return new TuningPoint(point.first + this.first, point.second + this.second);
		}

		@Override
		public String toString()
		{
			return "[" + first + "," + second + "]";
		}

	}

	protected List<TuningRoundInfo> info;
	protected TuningPoint currentFixedPoint;
	protected TuningRecord currentRecord;
	protected TuningRoundInfo currentRound;

	protected PointBinder pointBinder;
	protected volatile int roundSize;
	protected volatile int runCount;

	public PointProvider(int roundSize, PointBinder pointBinder)
	{
		this.roundSize = roundSize;
		this.pointBinder = pointBinder;
		if (roundSize > 0)
		{
			this.currentRound = new TuningRoundInfo(roundSize);
		} else
		{
			this.currentRound = new TuningRoundInfo();
		}
		this.info = new LinkedList<TuningRoundInfo>();
		this.currentRecord = null;
		// signal first round:
		runCount = -1;
	}
	
	public List<TuningRoundInfo> getRoundList() {
		return info;
	}

	public boolean isRoundEnd()
	{
		return (runCount % roundSize == 0);
	}

	public boolean isFirstRound()
	{
		return (runCount == -1);
	}

	public TuningRecord getCurrentTuningRecord() {
		return currentRecord;
	}
	
	public void saveCurrentPoint(float throughput, float tcr)
	{
		// System.err.println("PointProvider.saveCurrentPoint(measurement)");
		currentRecord.throughput = throughput;
		currentRecord.tcr = tcr;
		if (throughput > currentRound.getBest().getThroughput())
		{
			currentRound.setBest(currentRecord);
			// System.err.println("Replaced best record: " + currentRecord);
		}
	}

	protected void incRunCount()
	{
		runCount++;
		runCount %= roundSize;
		// System.err.println("INC");
	}

	protected void incRunCount(int num)
	{
		runCount += num;
		runCount %= roundSize;
		// System.err.println("INC " + num);
	}

	/*
	 * Start a round with a preselected point. 
	 * JVSTM's tuning "init" (i.e. the first round) round should use this method, possibly with an preset initial configuration.
	 */
	public void initRound(TuningPoint point)
	{
		// first round? set the counter to 1.
		if (runCount != -1)
		{
			incRunCount();
		} else
		{
			runCount = 1;
		}

		assertValidPoint(point);

		currentFixedPoint = point;

		TuningRoundInfo nextRound = new TuningRoundInfo(roundSize);
		info.add(nextRound);

		currentRecord = new TuningRecord(point, -1, -1);
		nextRound.add(currentRecord);
	}

	/*
	 * Start a round: use the previous round's best point as a starting point.
	 */
	public TuningPoint initRound()
	{
		// System.err.println("PointProvider.initRound()");
		incRunCount();

		// debug
		assert currentRecord.throughput >= 0;

		TuningPoint bestPoint = selectBestPoint();

		TuningRoundInfo nextRound = new TuningRoundInfo(roundSize);
		info.add(nextRound);
		currentRound = nextRound;

		currentFixedPoint = bestPoint;
		currentRecord = new TuningRecord(bestPoint, -1, -1);
		currentRound.add(currentRecord);

		assertValidPoint(bestPoint);

		return bestPoint;
	}

	protected void assertValidPoint(TuningPoint point)
	{
		assert this.pointBinder.isBound(point);
	}

	public TuningPoint requestPoint(float previousThroughput, float previousTCR)
	{
		currentRecord.setThroughput(previousThroughput);
		currentRecord.setTcr(previousTCR);

		// <retries, Point>
		Pair<Integer, TuningPoint> next = getPoint();

		if (next == null)
		{
			// round end.
			next = new Pair<Integer, TuningPoint>(1, initRound());
			// next = getPoint();
			if (next.second == null)
			{
				throw new RuntimeException("repeated round end - shouldn't happen");
			}
		}

		int retries = next.first;
		assert (runCount + retries <= roundSize);
		incRunCount(retries);

		TuningPoint point = next.second;
		assertValidPoint(point);

		currentRecord = new TuningRecord(point, -1, -1);
		currentRound.add(currentRecord);

		return point;
	}

	// this method provides a valid point and the number of retries needed to
	// find it, without exceeding the round limit, or null if there are no valid points left this round.
	protected Pair<Integer, TuningPoint> getPoint()
	{
		TuningPoint point = null;
		int tries = 1;

		//try to find a valid point left in this round:
		while (true)
		{
			point = getPointImpl();
			// System.err.println("PointProvider.getPoint() iteration: " +
			// point);

			if (pointBinder.isBound(point))
			{
				//valid point found
				break;
			}

			if (tries + runCount >= roundSize)
			{
				// signal round end - no valid points found.
				return null;
			}

			tries++;

		}

		return new Pair<Integer, TuningPoint>(tries, point);
	}

	protected TuningPoint selectBestPoint()
	{
		// System.err.println("PointProvider.selectBestPoint()");
		return currentRound.getBest().getPoint();
	}

	protected abstract TuningPoint getPointImpl();

	public abstract TuningPoint getInitialPoint();
}
