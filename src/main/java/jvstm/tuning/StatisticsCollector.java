package jvstm.tuning;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

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
		super();
		this.tuningInterval = tuningInterval;
		// expected output file name format:
		String format = "<BASE_DIR>/<BENCHMARK_OUTPUT_FOLDER>/<JVSTM_BASENAME>-<POLICY>/<NAME>.data-t<NUMTHREADS>";
		String throughputPath;
		String tuningPath;

		if (dataOutputFile == null)
		{
			disabled = true;
			return;
			// throw new
			// RuntimeException("Invalid <output> system property. Use \"java -Doutput=<outputPath> (...)\"");
		} else
		{

			String[] fileParts = dataOutputFile.split("/");
			if (fileParts.length < 4)
			{
				throw new RuntimeException("Unexpected statistics output filename format: " + dataOutputFile
						+ " . Expected " + format);
			}

			String oldExt = fileParts[fileParts.length - 1];
			String throughputExt = oldExt.replace("data-t", throughputExtension + "-t");
			String tuningExt = oldExt.replace("data-t", tuningExtension + "-t");

			throughputPath = dataOutputFile.replace(oldExt, throughputExt);
			tuningPath = dataOutputFile.replace(oldExt, tuningExt);

			// System.err.println("Statistics Paths:");
			// System.err.println(throughputPath);
			// System.err.println(tuningPath);
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
			return;
		}
		throughputBuffer.append("" + throughput + System.getProperty("line.separator"));
	}

	public void recordFullThroughput(float throughput)
	{
		if (disabled)
		{
			System.err.println("STAT - throughput: " + throughput);
			return;
		}
		throughputBuffer.append("+" + throughput + System.getProperty("line.separator"));
	}

	public void recordTuningPoint(int topLevel, int nested)
	{
		if (disabled)
		{
			System.err.println("STAT - tuningPoint: " + topLevel + nested);
			return;
		}
		tuningBuffer.append("" + topLevel + " " + nested + System.getProperty("line.separator"));
	}

}
