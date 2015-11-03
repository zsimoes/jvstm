package jvstm.tuning;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jvstm.Transaction;
import jvstm.tuning.policy.DefaultPolicy;
import jvstm.tuning.policy.FullGradientDescent;
import jvstm.tuning.policy.HierarchicalGradientDescent;
import jvstm.tuning.policy.IndependentGradientDescent;
import jvstm.tuning.policy.LinearGradientDescent4;
import jvstm.tuning.policy.PointBinder;
import jvstm.tuning.policy.ThroughtputMeasurementPolicy;
import jvstm.tuning.policy.TuningPolicy;

public class Controller implements Runnable
{

	// Region tuning fields
	private static TuningPolicy policy;
	private PointBinder pointBinder;

	private static boolean enabled = true;

	protected Map<Long, TuningContext> contexts;
	public Set<Transaction> started;

	private final int intervalMillis;
	private StatisticsCollector statisticsCollector;
	private static Map<String, Class<? extends TuningPolicy>> policies;
	private static TuningPoint initialConfig = new TuningPoint();

	// EndRegion

	// Region singleton
	private Controller()
	{
		String logFile = Util.getSystemProperty("logFile");
		try
		{
			String intervalProp = Util.getSystemProperty("interval");
			intervalMillis = Integer.parseInt(intervalProp);
		} catch (Exception e)
		{
			throw new RuntimeException("Invalid policy interval value. Use \"java -Dinterval=<milliseconds> ...\"");
		}

		statisticsCollector = new StatisticsCollector(logFile, intervalMillis);

		contexts = new ConcurrentHashMap<Long, TuningContext>();

		// TODO - possible problem that leads to sempahores leaking permits.
		// From the Java documentation:
		// (in using newSetFromMap(ConcurrentHashMap)) "read access is fully
		// concurrent
		// to itself and the writing threads (but might not yet see the results
		// of the changes currently being written)". So, reading threads may not
		// be able to see newly registering transactions in other threads, which
		// may lead to replays (re-start()s). Test this.
		started = Collections.newSetFromMap(new ConcurrentHashMap<Transaction, Boolean>());

		String m = Util.getSystemProperty("maxThreads");
		if (m == null)
		{
			throw new RuntimeException("Invalid maxThreads value. Use \"java -DmaxThreads=<max_threads> ...\"");
		}
		int maxThreads;
		try
		{
			maxThreads = Integer.parseInt(m);
		} catch (NumberFormatException n)
		{
			throw new RuntimeException("Invalid maxThreads value. Use \"java -DmaxThreads=<max_threads> ...\"");
		}

		this.pointBinder = new PointBinder(maxThreads);

		m = Util.getSystemProperty("initialConfig");
		if (m == null)
		{
			initialConfig = null;
		} else
		{
			String[] mm = m.split(",");
			if (mm.length != 2)
			{
				throw new RuntimeException(
						"Invalid initialConfig value. Use \"java -DinitialConfig=<max_topLevel>,<max_nested> ...\"");
			}
			try
			{
				initialConfig.first = Integer.parseInt(mm[0]);
				initialConfig.second = Integer.parseInt(mm[1]);
				if (!pointBinder.isBound(initialConfig))
				{
					throw new NumberFormatException("Values outside allowed range.");
				}
			} catch (NumberFormatException n)
			{
				throw new RuntimeException(
						"Invalid initialConfig value. Use \"java -DinitialConfig=<max_topLevel>,<max_nested> ...\"");
			}
		}

		selectPolicy();
	}

	static
	{

		policies = new HashMap<String, Class<? extends TuningPolicy>>();
		policies.put("LinearGD", LinearGradientDescent4.class);
		policies.put("FullGD", FullGradientDescent.class);
		policies.put("IndependentGD", IndependentGradientDescent.class);
		policies.put("HierarchicalGD", HierarchicalGradientDescent.class);
		policies.put("Throughput", ThroughtputMeasurementPolicy.class);
		policies.put("Default", DefaultPolicy.class);

		// tuningMechanisms = new HashMap<String, Class<? extends
		// TuningPolicy>>();

		// tuningMechanisms.put("Semaphore", LinearGradientDescent3.class);
		// tuningMechanisms.put("WaitingQueue", LinearGradientDescent2.class);
		// tuningMechanisms.put("ProducerConsumer",
		// LinearGradientDescent.class);
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
		System.err.println("Controller " + (isEnabled ? "enabled" : "disabled"));
		if (isEnabled)
		{
			policy.resetStatistics();
		}
	}

	private void selectPolicy()
	{
		try
		{

			String pol = Util.getSystemProperty("policy");
			if (pol == null)
			{
				System.err.println("Policy System Property not found - defaulting to LinearGD.");
				policy = new LinearGradientDescent4(this);
				return;
			}

			Class<? extends TuningPolicy> c = policies.get(pol);
			if (c == null)
			{
				throw new RuntimeException("jvstm.tuning.Controller (init) - Invalid Policy: " + pol + "."
						+ System.lineSeparator() + "Available policies are: " + policies.keySet());
			}

			if (pol.equals("Throughput"))
			{
				// special case for thoghput measurement policy
				policy = c.getConstructor(Controller.class).newInstance(this, this.intervalMillis);
			} else
			{
				policy = c.getConstructor(Controller.class).newInstance(this);
			}
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
				Thread.sleep(intervalMillis);
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

	public static TuningPoint getInitialConfiguration()
	{
		return initialConfig;
	}

	// EndRegion

}
