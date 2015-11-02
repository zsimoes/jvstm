package jvstm.tuning.policy;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jvstm.Transaction;
import jvstm.tuning.Controller;
import jvstm.tuning.ThreadStatistics;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningContext;
import jvstm.tuning.TuningPoint;
import jvstm.tuning.Util;

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
		tcr, throughput, stub
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
	
	public DataStub getDataStub() {
		return dataStub;
	}

	protected void setMeasurementType()
	{
		String measr = Util.getSystemProperty("MeasurementType");
		if (measr == null)
		{
			measurementType = MeasurementType.throughput;
			return;
		}

		try
		{
			measurementType = MeasurementType.valueOf(measr);
		} catch (IllegalArgumentException i)
		{
			throw new RuntimeException("Invalid policy interval value: " + measr
					+ ". Use \"java -DMeasurementType=<mType>\", where mType is one of \"tcr\" or \"throughput\"");
		}

		if (measurementType == MeasurementType.stub)
		{
			dataStub = new DataStub();
			dataStub.setup();
		}

	}

	public class DataStub
	{
		protected Map<TuningPoint, Float> stubData = new HashMap<TuningPoint, Float>();
		protected TuningPoint optimum;
		protected boolean useFileSource = true;
		//for logging:
		protected List<Float> distances = new LinkedList<Float>();

		public float getMeasurement(TuningPoint point)
		{
			float result;
			if (useFileSource)
			{
				result = stubData.get(point);
			} else {
				result = getDistanceMeasurement(point);
			}
			distances.add(result);

			return result;
		}

		protected float getDistanceMeasurement(TuningPoint point)
		{
			//check cache:
			if(stubData.containsKey(point)) {
				return stubData.get(point);
			}
			
			float distance = (float) Math.sqrt(Math.pow((point.first - optimum.first), 2)
					+ Math.pow((point.second - optimum.second), 2));
			float result = 1000;
			// avoid division by zero - float are not precise, so we use a
			// threshold of 0.01
			if (distance > 0.01)
			{
				result = result / distance;
			} else
			{
				result *= 2;
			}
			//cache:
			stubData.put(point,  result);
			return result;
		}

		public void setup()
		{
			String source = Util.getSystemProperty("StubSource");
			String optimum;
			useFileSource = true;

			if (source == null)
			{
				optimum = Util.getSystemProperty("StubOptimum");
				if (optimum == null)
				{
					throw new RuntimeException(
							"Error: to use stub data you must provide either a data source file with "
									+ "-DStubSource=<path> or an optimum with -DStubOptimum=x,y");
				} else
				{

					String pointPattern = "(\\d+),(\\d+)";
					Pattern pattern = Pattern.compile(pointPattern);
					Matcher matcher = pattern.matcher(optimum);
					if (!matcher.matches())
					{
						throw new RuntimeException("Error: invalid optimum: " + optimum + ". Use -DStubOptimum=x,y");
					}
					this.optimum = new TuningPoint(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher
							.group(2)));
					useFileSource = false;
					return;
				}
			}

			// String test = "[22,1] {12120.0}";
			String pointPattern = "\\[(\\d+),(\\d+)\\]" + " \\{(\\d+(\\.\\d+)?)\\}";
			Pattern pattern = Pattern.compile(pointPattern);
			Matcher matcher = null;

			BufferedReader r = null;
			try
			{
				r = new BufferedReader(new FileReader(source));
				String point = null;
				boolean first = true;

				while ((point = r.readLine()) != null)
				{
					matcher = pattern.matcher(point);
					if (!matcher.matches())
					{
						r.close();
						throw new RuntimeException(
								"Error: to use stub data you must provide a data source file with -DStubSource=<path>");
					}
					int x = Integer.parseInt(matcher.group(1));
					int y = Integer.parseInt(matcher.group(2));
					float measure = Float.parseFloat(matcher.group(3));
					TuningPoint stubPoint = new TuningPoint(x, y);
					if (first)
					{

					} else
					{
						stubData.put(stubPoint, measure);
					}
				}

				r.close();
			} catch (FileNotFoundException e)
			{
				throw new RuntimeException("Error: Invalid data source. Could not open file \"" + source + "\"");
			} catch (IOException e)
			{
				throw new RuntimeException("Error: Invalid data source. Could not read file \"" + source + "\"");
			}

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

	public float getMeasurement(boolean resetStatistics)
	{
		if (measurementType == MeasurementType.throughput)
		{
			return getThroughput(resetStatistics);
		} else if (measurementType == MeasurementType.tcr)
		{
			return getTCR(resetStatistics);
		} else if (measurementType == MeasurementType.stub)
		{
			return getStubThroughput(resetStatistics);
		} else
		{
			// default
			return getThroughput(resetStatistics);
		}
	}

	// Transaction Commit Rate (TCR), the percentage of committed transactions
	// out of all executed transactions in a sample period
	public float getTCR(boolean resetStatistics)
	{
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
	
	public PointProvider getPointProvider() {
		return pointProvider;
	}

	public float getStubThroughput(boolean resetStatistics)
	{
		float result = dataStub.getMeasurement(this.getPointProvider().getCurrentTuningRecord().getPoint());
		System.err.println("    " + result);
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
