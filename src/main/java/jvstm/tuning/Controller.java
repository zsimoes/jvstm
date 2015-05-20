package jvstm.tuning;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jvstm.tuning.policy.CurveBinder;
import jvstm.tuning.policy.DefaultPolicy;
import jvstm.tuning.policy.DiagonalGradientDescent;
import jvstm.tuning.policy.IndependentGradientDescent;
import jvstm.tuning.policy.InterleavedGradientDescent;
import jvstm.tuning.policy.LinearGradientDescent;
import jvstm.tuning.policy.LinearGradientDescent2;
import jvstm.tuning.policy.LinearGradientDescent3;
import jvstm.tuning.policy.ThroughtputMeasurementPolicy;
import jvstm.tuning.policy.TuningPolicy;

public class Controller implements Runnable
{

	// Region tuning fields
	private TuningPolicy policy;
	private CurveBinder curveBinder;

	protected Map<Long, TuningContext> contexts;

	private final int intervalMillis = 100;
	private StatisticsCollector statisticsCollector;
	private static Map<String, Class<? extends TuningPolicy>> policies;
	private static Map<String, Class<? extends TuningPolicy>> tuningMechanisms;

	// EndRegion

	// Region singleton
	private Controller()
	{
		String outputPath = Util.getSystemProperty("output");

		statisticsCollector = new StatisticsCollector(outputPath, intervalMillis);

		contexts = new ConcurrentHashMap<Long, TuningContext>();

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

		this.curveBinder = new CurveBinder(maxThreads);

		selectPolicy();
	}

	static
	{

		policies = new HashMap<String, Class<? extends TuningPolicy>>();
		policies.put("LinearGD", LinearGradientDescent3.class);
		policies.put("DiagonalGD", DiagonalGradientDescent.class);
		policies.put("IndependentGD", IndependentGradientDescent.class);
		policies.put("InterleavedGD", InterleavedGradientDescent.class);
		policies.put("HierarchicalGD", null);
		policies.put("Throughput", ThroughtputMeasurementPolicy.class);
		policies.put("Default", DefaultPolicy.class);

		tuningMechanisms = new HashMap<String, Class<? extends TuningPolicy>>();

		tuningMechanisms.put("Semaphore", LinearGradientDescent3.class);
		tuningMechanisms.put("WaitingQueue", LinearGradientDescent2.class);
		tuningMechanisms.put("ProducerConsumer", LinearGradientDescent.class);
	}

	public StatisticsCollector getStatisticsCollector()
	{
		return statisticsCollector;
	}

	private void selectPolicy()
	{
		try
		{

			String pol = Util.getSystemProperty("policy");
			if (pol == null)
			{
				System.err.println("Policy System Property not found - defaulting to LinearGD.");
				policy = new LinearGradientDescent2(this);
				return;
			} else if (pol.equals("LinearGD"))
			{
				// select appropriate tuning mechanism. If absent, use normal
				// LinearGD
				String mechanism = Util.getSystemProperty("tuningMechanism");
				if (mechanism != null)
				{
					Class<? extends TuningPolicy> mec = tuningMechanisms.get(mechanism);
					if (mec != null)
					{
						policy = mec.getConstructor(Controller.class).newInstance(this);
						System.err.println("Selected tuning mechanism: " + mechanism.getClass().getName());
					} else
					{
						throw new RuntimeException("jvstm.tuning.Controller (init) - Invalid tuning mechanism: "
								+ mechanism + "." + System.getProperty("line.separator") + "Available policies are: "
								+ tuningMechanisms.keySet());
					}
				}
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

	public CurveBinder getCurveBinder()
	{
		return curveBinder;
	}

	private static Controller instance;
	private static boolean running = false;

	public static Controller instance()
	{
		if (instance == null)
		{
			instance = new Controller();
		}
		return instance;
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
				policy.run(true);

				float throughput = policy.getThroughput(true);
				int topLevel = policy.getMaxTopLevelThreads();
				int nested = policy.getMaxNestedThreads();

				statisticsCollector.recordThroughput(throughput);
				statisticsCollector.recordTuningPoint(topLevel, nested);
				// System.err.println("Recorded throughput and tuning path: ");
				// System.err.println("\t" + throughput + "\t(" + topLevel + ","
				// + nested + ")");
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

	public void finishTransaction(TuningContext t)
	{
		policy.finishTransaction(t);
	}

	public void tryRunTransaction(TuningContext t, boolean isNested)
	{
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

	// EndRegion

}
