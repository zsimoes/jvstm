package jvstm.tuning.policy;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jvstm.Transaction;
import jvstm.tuning.Controller;
import jvstm.tuning.Parameters;
import jvstm.tuning.ThreadStatistics;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningContext;
import jvstm.tuning.TuningPoint;

public abstract class TuningPolicy
{

	// Region fields
	protected Controller controller;
	protected PointProvider pointProvider;
	protected PointBinder pointBinder;
	// the values passed to the following constructors are not used
	protected ThreadStatistics globalTopLevelStatistics = new ThreadStatistics(-1);
	protected ThreadStatistics globalNestedStatistics = new ThreadStatistics(-1);
	protected ThreadStatistics globalStatistics = new ThreadStatistics(-1);
	protected MeasurementType measurementType;
	protected DataStub dataStub;

	// EndRegion

	public static enum MeasurementType
	{
		real, stub;
		
		public static List<String> names() {
		    MeasurementType[] measures = values();
		    List<String> names = new ArrayList<String>();

		    for (int i = 0; i < measures.length; i++) {
		        names.add(measures[i].name());
		    }

		    return names;
		}
		
	}

	public TuningPolicy(Controller controller)
	{
		this.controller = controller;
		this.pointBinder = controller.getPointBinder();
		setMeasurementType();
	}

	public void reset(Controller controller)
	{
		clearInternalData();
		this.controller = controller;
	}

	public DataStub getDataStub()
	{
		if (measurementType != MeasurementType.stub)
		{
			throw new RuntimeException(
					"TuningPolicy: DataStub disabled. Use \"-DMeasurementType=stub [-DLogDistances=true]\"");
		}
		return dataStub;
	}

	protected void setMeasurementType()
	{
		measurementType = Parameters.measurementType;
		if (measurementType == MeasurementType.stub)
		{
			dataStub = new DataStub();
		}
	}

	public class DataStub
	{
		protected Map<TuningPoint, Float> stubData = new HashMap<TuningPoint, Float>();
		protected TuningPoint optimum;
		protected boolean useStubFile = true;
		protected String stubFile;
		private boolean logDistances = true;
		// for caching distances:
		protected List<Float> distances = new LinkedList<Float>();

		public DataStub()
		{
			this.logDistances = Parameters.logDistances;
			this.optimum = Parameters.stubOptimum;
			this.stubFile = Parameters.stubFile;
			this.useStubFile = Parameters.useStubFile;

			// load stub data:
			if (useStubFile)
			{
				// String example = "[22,1] {12120.0}";
				// String example2 = "1 1 160000.0"
				String pointPattern1 = "\\[(\\d+),(\\d+)\\]" + " \\{(\\d+(\\.\\d+)?)\\}";
				String pointPattern2 = "(\\d+)\\s+(\\d+)\\s+(\\d+(\\.\\d+)?)";
				String pointPattern3 = "\\[(\\d+),(\\d+)\\]" + " \\{thr:\\s+(\\d+(\\.\\d+)?),tcr:\\s+(\\d+(\\.\\d+)?)\\}";
				Pattern pattern1 = Pattern.compile(pointPattern1);
				Pattern pattern2 = Pattern.compile(pointPattern2);
				Pattern pattern3 = Pattern.compile(pointPattern3);
				Pattern finalPattern = null;
				Matcher matcher = null;

				BufferedReader r = null;
				try
				{
					r = new BufferedReader(new FileReader(stubFile));
					String point = null;

					while ((point = r.readLine()) != null)
					{
						if (finalPattern == null)
						{
							Matcher matcher1 = pattern1.matcher(point);
							Matcher matcher2 = pattern2.matcher(point);
							Matcher matcher3 = pattern2.matcher(point);
							if (matcher1.matches())
							{
								finalPattern = pattern1;
							} else if (matcher2.matches())
							{
								finalPattern = pattern2;
							} else if (matcher3.matches())
							{
								finalPattern = pattern2;
							} else
							{
								r.close();
								throw new RuntimeException("Error: Invalid Stub file data format: " + point);
							}
							matcher = finalPattern.matcher(point);
						}
						matcher = matcher.reset(point);
						int x = Integer.parseInt(matcher.group(1));
						int y = Integer.parseInt(matcher.group(2));
						float measure = Float.parseFloat(matcher.group(3));
						TuningPoint stubPoint = new TuningPoint(x, y);
						stubData.put(stubPoint, measure);
					}

					r.close();
				} catch (FileNotFoundException e)
				{
					throw new RuntimeException("Error: Invalid data source. Could not open file \"" + stubFile + "\"");
				} catch (IOException e)
				{
					throw new RuntimeException("Error: Invalid data source. Could not read file \"" + stubFile + "\"");
				}
			}
		}

		public List<Float> getDistances()
		{
			if (!logDistances)
			{
				throw new RuntimeException(
						"DataStub: Distance logging disabled. Use \"-DMeasurementType=stub -DLogDistances=true\"");
			}
			return distances;
		}

		public float getMeasurement(TuningPoint point)
		{
			float result;
			if (useStubFile)
			{
				result = stubData.get(point);
			} else
			{
				result = getDistanceMeasurement(point);
				if (logDistances)
				{
					distances.add(getAbsoluteDistance(point));
				}
			}

			return result;
		}

		protected float getAbsoluteDistance(TuningPoint point)
		{
			float distance = (float) Math
					.sqrt(Math.pow((point.first - optimum.first), 2) + Math.pow((point.second - optimum.second), 2));
			return distance;
		}

		protected float getDistanceMeasurement(TuningPoint point)
		{
			// check cache:
			if (stubData.containsKey(point))
			{
				return stubData.get(point);
			}

			float distance = getAbsoluteDistance(point);
			float result = 1000;
			// avoid division by zero - float are not precise, so we use a
			// threshold of 0.01
			if (distance > 0.01)
			{
				result = result / distance;
			} else
			{
				// points that coincide with the optimum will have a value of
				// 2000, for easier reading.
				result *= 2;
			}
			// cache:
			stubData.put(point, result);
			return result;
		}
	}

	// to do: use lock?
	// does not cause memory consistency errors but is not thread-safe
	public void mergeStatistics()
	{
		for (TuningContext ctx : controller.getContexts().values())
		{
			ctx.getStatistics().addTo(globalTopLevelStatistics);
			ctx.getStatistics().addTo(globalStatistics);
		}
	}

	// Transaction Commit Rate (TCR), the percentage of committed transactions
	// out of all executed transactions in a sample period
	public float getTCR(boolean resetStatistics)
	{
		mergeStatistics();
		float tcr = ((float) globalStatistics.getCommitCount()) / globalStatistics.getTransactionCount();

		if (Float.isNaN(tcr))
		{
			tcr = 0;
		}

		if (resetStatistics)
		{
			resetStatistics();
		}

		return tcr;
	}

	public PointProvider getPointProvider()
	{
		return pointProvider;
	}

	public float getStubThroughput(boolean resetStatistics)
	{
		float result = dataStub.getMeasurement(this.getPointProvider().getCurrentTuningRecord().getPoint());
		return result;
	}

	// number of transactions started
	public long getThroughput(boolean resetStatistics)
	{

		mergeStatistics();
		long result = globalStatistics.getTransactionCount();

		if (resetStatistics)
		{
			resetStatistics();
		}

		return result;
	}

	public void resetStatistics()
	{
		for (TuningContext ctx : controller.getContexts().values())
		{
			ctx.getStatistics().reset();
		}
		globalTopLevelStatistics.reset();
		globalNestedStatistics.reset();
		globalStatistics.reset();
	}

	public void registerContext(TuningContext context)
	{
		controller.getContexts().put(context.getThreadId(), context);
		context.setRegistered(true);
	}

	public TuningContext registerThread(long threadId)
	{

		Tunable newState = newTunable();
		ThreadStatistics stats = new ThreadStatistics(threadId);
		TuningContext ctx = new TuningContext(threadId, -1, newState, stats);
		registerContext(ctx);
		return ctx;
	}

	// clear any data generated using the statistics
	public abstract void clearInternalData();

	// run main algorithm to adjust <contexts>. if <mergePerThreadStatistics> is
	// true, merge all stats into <globalStatistics> before taking action
	public abstract void run(boolean mergePerThreadStatistics);

	// Create and return new tunable, in whatever state this policy requires
	public abstract Tunable newTunable();

	public abstract void finishTransaction(Transaction t, boolean isNested);

	public abstract void tryRunTransaction(Transaction t, boolean isNested);

	public ThreadStatistics getGlobalStatistics()
	{
		return globalStatistics;
	}

	protected abstract PointProvider createPointProvider();

}
