package jvstm.tuning;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jvstm.Transaction;
import jvstm.tuning.policy.PointBinder;
import jvstm.tuning.policy.TuningPolicy;

public class Controller implements Runnable
{

	// Region tuning fields
	private static TuningPolicy policy;
	private PointBinder pointBinder;

	private static boolean enabled = true;

	protected Map<Long, TuningContext> contexts;
	public Set<Transaction> started;

	private StatisticsCollector statisticsCollector;

	// EndRegion
	static
	{
		Parameters.setup();
	}

	// Region singleton
	private Controller()
	{

		statisticsCollector = new StatisticsCollector();

		contexts = new ConcurrentHashMap<Long, TuningContext>();

		// TODO - possible problem that leads to semaphores leaking permits.
		// From the Java documentation:
		// (in using newSetFromMap(ConcurrentHashMap)) "read access is fully
		// concurrent
		// to itself and the writing threads (but might not yet see the results
		// of the changes currently being written)". So, reading threads may not
		// be able to see newly registering transactions in other threads, which
		// may lead to replays (re-start()s). Test this.
		started = Collections.newSetFromMap(new ConcurrentHashMap<Transaction, Boolean>());

		this.pointBinder = new PointBinder(Parameters.maxThreads);
		if (!pointBinder.isBound(Parameters.initialConfig))
		{
			Parameters.printUsageAndExit("Initial Config outside allowed range for MaxThreads: " + Parameters.initialConfig);
		}

		selectPolicy();
	}

	public StatisticsCollector getStatisticsCollector()
	{
		return statisticsCollector;
	}

	public static boolean isEnabled()
	{
		return enabled;
	}

	public static void setEnabled(boolean isEnabled)
	{
		enabled = isEnabled;
		if (isEnabled)
		{
			policy.resetStatistics();
		}
	}
	
	public static void setEnabled(boolean isEnabled, String source)
	{
		enabled = isEnabled;
		System.err.println("Controller " + (isEnabled ? "enabled" : "disabled") + " by " + source);
		if (isEnabled)
		{
			policy.resetStatistics();
		}
	}

	private void selectPolicy()
	{
		try
		{
			Class<? extends TuningPolicy> c = Parameters.policy;
			policy = c.getConstructor(Controller.class).newInstance(this);
			System.err.println("Selected tuning policy: " + policy.getClass().getName());
		} catch (Exception e)
		{
			System.err.println("Exception selecting policy: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	public PointBinder getPointBinder()
	{
		return pointBinder;
	}

	public TuningPolicy getPolicy()
	{
		return policy;
	}

	private static boolean running = false;

	// Ensure instance is initialized only once.
	private static class InstanceHolder
	{
		private static final Controller instance = new Controller();
	}

	public static Controller instance()
	{
		return InstanceHolder.instance;
	}

	public static boolean isRunning()
	{
		return running;
	}

	protected static void setRunning(boolean isRunning)
	{
		running = isRunning;
	}

	// EndRegion

	public void registerContext(TuningContext context)
	{
		policy.registerContext(context);
	}

	public TuningContext registerThread(long threadId)
	{
		return policy.registerThread(threadId);
	}

	// Region tuning

	@Override
	public void run()
	{
		while (true)
		{
			try
			{
				Thread.sleep(Parameters.interval);
				if (enabled)
				{
					policy.run(true);
				} else
				{
					// System.err.println("Controller disabled");
				}
			} catch (InterruptedException e)
			{
			}
		}
	}

	public static void startThread()
	{
		if (isRunning())
			return;
		Thread controller = new Thread(instance());
		controller.setName("Tuning Controller Thread");
		controller.setDaemon(true);
		setRunning(true);
		controller.start();
	}

	public void finishTransaction(Transaction t, boolean isNested)
	{
		policy.finishTransaction(t, isNested);
	}

	public void tryRunTransaction(Transaction t, boolean isNested)
	{
		if (started.contains(t))
		{
			// System.out.println("REPLAY - " + t.getClass().getName());
			return;
		}
		started.add(t);

		policy.tryRunTransaction(t, isNested);
	}

	public Map<Long, TuningContext> getContexts()
	{
		return contexts;
	}

	// EndRegion

}
