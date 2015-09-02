package jvstm.tuning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jvstm.Transaction;
import jvstm.tuning.policy.LinearGradientDescent4;
import jvstm.tuning.policy.PointBinder;
import jvstm.tuning.policy.DefaultPolicy;
import jvstm.tuning.policy.DiagonalGradientDescent;
import jvstm.tuning.policy.IndependentGradientDescent;
import jvstm.tuning.policy.InterleavedGradientDescent;
import jvstm.tuning.policy.LinearGradientDescent3;
import jvstm.tuning.policy.ThroughtputMeasurementPolicy;
import jvstm.tuning.policy.TuningPolicy;
import jvstm.util.Pair;

public class Controller implements Runnable
{

	// Region tuning fields
	private TuningPolicy policy;
	private PointBinder pointBinder;

	private static boolean enabled = true;

	protected Map<Long, TuningContext> contexts;
	public Set<Transaction> started;

	private final int intervalMillis;
	private StatisticsCollector statisticsCollector;
	private static Map<String, Class<? extends TuningPolicy>> policies;
	private static TuningPoint initialConfig = new TuningPoint();

	// private static Map<String, Class<? extends TuningPolicy>>
	// tuningMechanisms;

	// EndRegion

	// Region singleton
	private Controller()
	{
		String outputPath = Util.getSystemProperty("output");
		try
		{
			String intervalProp = Util.getSystemProperty("interval");
			intervalMillis = Integer.parseInt(intervalProp);
		} catch (Exception e)
		{
			throw new RuntimeException("Invalid policy interval value. Use \"java -Dinterval=<milliseconds> ...\"");
		}

		statisticsCollector = new StatisticsCollector(outputPath, intervalMillis);

		contexts = new ConcurrentHashMap<Long, TuningContext>();
		started = new HashSet<Transaction>();

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
		policies.put("DiagonalGD", DiagonalGradientDescent.class);
		policies.put("IndependentGD", IndependentGradientDescent.class);
		policies.put("InterleavedGD", InterleavedGradientDescent.class);
		policies.put("HierarchicalGD", null);
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
			} /*
			 * else if (pol.equals("LinearGD")) { // select appropriate tuning
			 * mechanism. If absent, use normal // LinearGD String mechanism =
			 * Util.getSystemProperty("tuningMechanism"); if (mechanism != null)
			 * { Class<? extends TuningPolicy> mec =
			 * tuningMechanisms.get(mechanism); if (mec != null) { policy =
			 * mec.getConstructor(Controller.class).newInstance(this);
			 * System.err.println("Selected tuning mechanism: " +
			 * mechanism.getClass().getName()); } else { throw new
			 * RuntimeException
			 * ("jvstm.tuning.Controller (init) - Invalid tuning mechanism: " +
			 * mechanism + "." + System.getProperty("line.separator") +
			 * "Available policies are: " + tuningMechanisms.keySet()); } } }
			 */

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

	// TODO: change boolean to nestinglevel
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
				} else {
					System.err.println("Controller disabled");
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

	public static HashMap<Integer, Pair<String, TuningPoint>> starts = new HashMap<Integer, Pair<String, TuningPoint>>();
	public static volatile int i = 0;
	public final int DEBUGSTARTSTHRESHOLD = -1;

	public void finishTransaction(Transaction t, boolean isNested)
	{
		if (!isNested && starts.containsKey(t.hashCode()))
		{
			starts.get(t.hashCode()).second.second++;
		}
		policy.finishTransaction(t, isNested);
	}

	@SuppressWarnings("unused")
	public void tryRunTransaction(Transaction t, boolean isNested)
	{
		if (started.contains(t))
		{
			// System.out.println("REPLAY - " + t.getClass().getName());
			return;
		}
		started.add(t);

		// debug begins and finishes
		if (!isNested && DEBUGSTARTSTHRESHOLD > 0)
		{
			if (!starts.containsKey(t.hashCode()))
			{
				Pair<String, TuningPoint> p = new Pair<String, TuningPoint>(Transaction.current().getClass().getName(),
						new TuningPoint(0, 0));
				starts.put(t.hashCode(), p);
			}
			starts.get(t.hashCode()).second.first++;
		}
		if (++i > DEBUGSTARTSTHRESHOLD)
		{
			for (Pair<String, TuningPoint> val : starts.values())
			{
				TuningPoint p = val.second;
				if (p.first != p.second)
				{
					System.out.println(val);
				}
			}
		}
		policy.tryRunTransaction(t, isNested);
	}

	public int getMaxTopLevelThreads()
	{
		return policy.getMaxTopLevelThreads();
	}

	public int getMaxNestedThreads()
	{
		return policy.getMaxNestedThreads();
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
