package jvstm.tuning;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import jvstm.tuning.policy.DefaultPolicy;
import jvstm.util.Pair;

public class StatisticsCollector
{

	private Writer throughputLogFile;
	private StringBuilder throughputBuffer = new StringBuilder();
	private Writer tuningPathFile;
	private StringBuilder tuningBuffer = new StringBuilder();

	public static final String throughputExtension = "throughput";
	public static final String tuningExtension = "tuningpath";
	private boolean disabled = false;

	private int tuningInterval;

	public StatisticsCollector(String dataOutputFile, int tuningInterval)
	{
		this.tuningInterval = tuningInterval;
		// expected output file name format:
		String format = "<BASE_DIR>/<BENCHMARK_OUTPUT_FOLDER>/<JVSTM_BASENAME>-<POLICY>/<NAME>.data-t<NUMTHREADS>";
		String throughputPath;
		String tuningPath;

		if (dataOutputFile == null)
		{
			disabled = true;
			System.err.println("Statistics Collector Disabled");
			return;
			// throw new
			// RuntimeException("Invalid <output> system property. Use \"java -Doutput=<outputPath> (...)\"");
		} else
		{

			/*
			 * String[] fileParts = dataOutputFile.split("/"); if
			 * (fileParts.length < 4) { throw new
			 * RuntimeException("Unexpected statistics output filename format: "
			 * + dataOutputFile + " . Expected " + format); }
			 * 
			 * String oldExt = fileParts[fileParts.length - 1]; String
			 * throughputExt = oldExt.replace("data-t", throughputExtension +
			 * "-t"); String tuningExt = oldExt.replace("data-t",
			 * tuningExtension + "-t");
			 * 
			 * throughputPath = dataOutputFile.replace(oldExt, throughputExt);
			 * tuningPath = dataOutputFile.replace(oldExt, tuningExt);
			 */

			throughputPath = dataOutputFile + ".throughput";
			tuningPath = dataOutputFile + ".tuning";

			System.err.println("Statistics Paths:");
			System.err.println(throughputPath);
			System.err.println(tuningPath);
		}

		try
		{
			this.throughputLogFile = new FileWriter(throughputPath);
			this.tuningPathFile = new FileWriter(tuningPath);

		} catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		throughputBuffer.append("Throughput Log - Measurement Interval: " + tuningInterval
				+ System.getProperty("line.separator"));
		tuningBuffer.append("Tuning Paths Log - Measurement Interval: " + tuningInterval
				+ System.getProperty("line.separator"));

		// ShutdownHook to flush data into the log files:
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{

			@Override
			public void run()
			{
				try
				{
					throughputLogFile.write(throughputBuffer.toString());
					throughputLogFile.close();
					tuningPathFile.write(tuningBuffer.toString());
					tuningPathFile.close();
					System.err.println("StatisticsCollector ShutdownHook finished execution.");
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}));
	}

	public void recordThroughput(float throughput)
	{
		if (disabled)
		{
			System.err.println("STAT - throughput: " + throughput);
			System.err.println("CONTROL - permits - "
					+ ((DefaultPolicy) Controller.instance().getPolicy()).topLevelSemaphore.availablePermits() + "   "
					+ +((DefaultPolicy) Controller.instance().getPolicy()).nestedSemaphore.availablePermits());
			return;
		}
		throughputBuffer.append("" + throughput + System.getProperty("line.separator"));
	}

	public void recordFullThroughput(float throughput)
	{
		if (disabled)
		{
			// System.err.println("STAT - throughput: " + throughput);
			return;
		}
		throughputBuffer.append("+" + throughput + System.getProperty("line.separator"));
	}

	public void recordTuningPoint(Pair<Integer, Integer> point)
	{
		if (disabled)
		{
			// System.err.println("STAT - tuningPoint: " + point.first + ", " +
			// point.second);
			return;
		}
		tuningBuffer.append("" + point.first + " " + point.second + System.getProperty("line.separator"));
	}

	public void recordTuningPoint(Pair<Integer, Integer> point, float measurement,
			Pair<Integer, Integer>[] alternatives, float[] alternativeMeasurements)
	{
		if (disabled)
		{
			// System.err.println("STAT - tuningPoint: " + point.first + ", " +
			// point.second);
			return;
		}
		if (alternatives.length != alternativeMeasurements.length)
		{
			throw new RuntimeException("recordTuningPoint: different array lengths");
		}
		tuningBuffer.append("Point [" + point.first + "," + point.second + "] {" + measurement
				+ "}\t -- alternatives were: ");
		for (int i = 0; i < alternatives.length; i++)
		{
			if (alternatives[i] == null)
			{
				continue;
			}
			tuningBuffer.append("[" + alternatives[i].first + "," + alternatives[i].second + "] {"
					+ alternativeMeasurements[i] + "} , \t");
		}
		tuningBuffer.append(System.getProperty("line.separator"));
	}

}
