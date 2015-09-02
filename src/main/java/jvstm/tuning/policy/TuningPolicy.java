package jvstm.tuning.policy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jvstm.Transaction;
import jvstm.tuning.Controller;
import jvstm.tuning.ThreadStatistics;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningContext;
import jvstm.tuning.Util;
import jvstm.util.Pair;

public abstract class TuningPolicy
{

	// Region fields
	protected Controller controller;
	protected PointProvider pointProvider;
	protected PointBinder pointBinder;
	// the values passed to the following constructors are not used
	protected ThreadStatistics globalTopLevelStatistics = new ThreadStatistics(-1, 0);
	protected ThreadStatistics globalNestedStatistics = new ThreadStatistics(-1, 0);
	protected ThreadStatistics globalStatistics = new ThreadStatistics(-1, 0);
	protected MeasurementType measurementType;
	// EndRegion

	protected AtomicInteger maxTopLevelThreads = new AtomicInteger(2);
	protected AtomicInteger maxNestedThreads = new AtomicInteger(2);
	protected AtomicInteger currentTopLevelThreads = new AtomicInteger(0);
	protected AtomicInteger currentNestedThreads = new AtomicInteger(0);

	public static enum MeasurementType
	{
		tcr, throughput
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
		if (measurementType.equals("throughput"))
		{
			return getThroughput(resetStatistics);
		} else if (measurementType.equals("tcr"))
		{
			return getTCR(resetStatistics);
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

	public int getMaxNestedThreads()
	{
		return maxNestedThreads.get();
	}

	public int getMaxTopLevelThreads()
	{
		return maxTopLevelThreads.get();
	}

	public void registerContext(TuningContext context)
	{
		controller.getContexts().put(context.getThreadId(), context);
		context.setRegistered(true);
	}

	// TODO: deal with nesting level
	public TuningContext registerThread(long threadId)
	{

		Tunable newState = newTunable();
		ThreadStatistics stats = new ThreadStatistics(threadId, -1);
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

}
