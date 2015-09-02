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
		protected float measurement;

		public TuningPoint getPoint()
		{
			return point;
		}

		public void setPoint(TuningPoint point)
		{
			this.point = point;
		}

		public float getMeasurement()
		{
			return measurement;
		}

		public void setMeasurement(float measurement)
		{
			this.measurement = measurement;
		}

		public TuningRecord(TuningPoint point, float measurement)
		{
			this.point = point;
			this.measurement = measurement;
		}

		@Override
		public String toString()
		{
			return point.toString() + " {" + measurement + "}";
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
				return new TuningRecord(null, Float.MIN_VALUE);
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
			} else if (record.measurement > best.measurement)
			{
				best = record;
			}
		}

		public TuningRoundInfo(int roundSize)
		{
			alternatives = new ArrayList<TuningRecord>(roundSize);
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			for (TuningRecord rec : alternatives)
			{
				if (rec != null)
				{
					sb.append(rec.toString() + " , ");
				}
			}
			return "BestPoint - " + best + "  - alts - " + sb.toString();
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
		this.currentRound = new TuningRoundInfo(roundSize);
		this.info = new LinkedList<TuningRoundInfo>();
		this.currentRecord = null;
		// signal first round:
		runCount = -1;

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{

			@Override
			public void run()
			{
				System.err.println("PointProvider logs: " + System.lineSeparator());
				for (TuningRoundInfo round : info)
				{
					System.err.println(round.toString());
				}
				/*
				 * try { throughputLogFile.write(throughputBuffer.toString());
				 * throughputLogFile.close();
				 * tuningPathFile.write(tuningBuffer.toString());
				 * tuningPathFile.close(); System.err.println(
				 * "StatisticsCollector ShutdownHook finished execution."); }
				 * catch (IOException e) { e.printStackTrace(); }
				 */
			}
		}));
	}

	public boolean isRoundEnd()
	{
		return (runCount % roundSize == 0);
	}

	public boolean isFirstRound()
	{
		return (runCount == -1);
	}

	public void saveCurrentPoint(float measurement)
	{
		//System.err.println("PointProvider.saveCurrentPoint(measurement)");
		currentRecord.measurement = measurement;
		if (measurement > currentRound.getBest().getMeasurement())
		{
			currentRound.setBest(currentRecord);
			//System.err.println("Replaced best record: " + currentRecord);
		}
	}

	protected void incRunCount()
	{
		runCount++;
		runCount %= roundSize;
		//System.err.println("INC");
	}

	protected void incRunCount(int num)
	{
		runCount += num;
		runCount %= roundSize;
		//System.err.println("INC " + num);
	}

	/*
	 * Start a round with a preselected point.
	 */
	public void initRound(TuningPoint point)
	{
		//System.err.println("PointProvider.initRound(point)");
		// first round? set the counter right.
		if (runCount != -1)
		{
			incRunCount();
		} else
		{
			//System.err.println("inc'ed");
			runCount = 1;
		}

		assertValidPoint(point);

		currentFixedPoint = point;

		TuningRoundInfo nextRound = new TuningRoundInfo(roundSize);
		info.add(nextRound);

		currentRecord = new TuningRecord(point, -1);
		nextRound.add(currentRecord);
	}

	public TuningPoint initRound()
	{
		//System.err.println("PointProvider.initRound()");
		incRunCount();

		// debug
		assert currentRecord.measurement >= 0;

		TuningPoint point = selectBestPoint();

		TuningRoundInfo nextRound = new TuningRoundInfo(roundSize);
		info.add(nextRound);
		currentRound = nextRound;

		currentFixedPoint = point;
		currentRecord = new TuningRecord(point, -1);
		currentRound.add(currentRecord);

		assertValidPoint(point);

		return point;
	}

	private void assertValidPoint(TuningPoint point)
	{
		//System.err.println("PointProvider.assertValidPoint: " + point);
		int x = point.first, y = point.second;
		assert (x > 0 && x < 49 && y > 0 && y < 49);
	}

	public TuningPoint requestPoint(float previousPointMeasurement)
	{
		//System.err.println("PointProvider.requestPoint(measurement)");

		currentRecord.setMeasurement(previousPointMeasurement);

		Pair<Integer, TuningPoint> next = getPoint();

		if (next == null)
		{
			// round end.
			//System.err.println("Forced Round end!");
			initRound();
			next = getPoint();
		}

		int retries = next.first;
		assert (runCount + retries <= roundSize);
		incRunCount(retries);

		TuningPoint point = next.second;
		assertValidPoint(point);

		currentRecord = new TuningRecord(point, -1);
		currentRound.add(currentRecord);

		return point;
	}

	protected TuningPoint selectBestPoint()
	{
		//System.err.println("PointProvider.selectBestPoint()");
		return currentRound.getBest().getPoint();
	}

	// this method provides a valid point and the number of retries needed to
	// find it
	protected Pair<Integer, TuningPoint> getPoint()
	{
		TuningPoint point = null;
		int tries = 1;

		while (true)
		{
			point = getNextPoint();
			System.err.println("PointProvider.getPoint() iteration: " + point);

			if (pointBinder.isBound(point))
			{
				break;
			}

			if (tries + runCount >= roundSize)
			{
				// signal round end;
				return null;
			}

			tries++;

		}

		return new Pair<Integer, TuningPoint>(tries, point);
	}

	public abstract TuningPoint getNextPoint();
}
