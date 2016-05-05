package jvstm.tuning;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jvstm.tuning.policy.PointProvider.TuningRecord;
import jvstm.tuning.policy.PointProvider.TuningRoundInfo;
import jvstm.tuning.policy.TuningPolicy;

public class StatisticsCollector
{
	public static final String headerJVSTMLog = "JVSTM Tuning Log";
	public static final String headerParameters = "## JVSTM Tuning Parameters";
	public static final String headerTuningRoundLogs = "## Tuning round logs:";
	public static final String headerTuningPath = "## Tuning Path:";
	public static final String headerThroughput = "## Throughput:";
	public static final String headerTCR = "## TCR:";
	public static final String headerDistance = "## Distance to optimum logs:";
	
	public static String logFile;
	public static int interval;
	ByteArrayOutputStream interceptedStdout;
	
	static {
		logFile = Parameters.logFile;
		interval = Parameters.interval;
	}
	
	private PrintWriter output;
	private long executionTime = -1;

	public StatisticsCollector()
	{
		if (Parameters.noStats)
		{
			return;
		}

		interceptedStdout = new ByteArrayOutputStream();
		System.setOut(new PrintStream(interceptedStdout));
		
		try
		{
			if (logFile == null)
			{
				throw new IOException();
			}
			output = new PrintWriter(logFile);
			this.logFile = new File(logFile).getAbsolutePath();
			System.err.println("Log file path: " + logFile);
		} catch (IOException e)
		{
			if (output != null)
			{
				output.close();
			}
			output = new PrintWriter(System.err);
			System.err.println("StatisticsCollector output defaulted to stderr "
					+ (logFile == null ? "(No log file name provided)" : "(invalid file name: - " + logFile + ")"));
		}

		// ShutdownHook to flush data into the log files:
		Runtime.getRuntime().addShutdownHook(new Thread(getShutdownHook()));
	}

	protected Runnable getShutdownHook()
	{
		return new Runnable()
		{

			@Override
			public void run()
			{
				assert (executionTime > 0);
				Controller.setEnabled(false, "StatisticsCollector ShutdownHook");
				TuningPolicy policy = Controller.instance().getPolicy();
				
				//Save intercepted output and print it to the default stdout:
				String interceptedOutput = interceptedStdout.toString();
				System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
				System.out.println(interceptedOutput);
				
				// Check if we're using a dummy policy:
				if (policy.getPointProvider() == null || policy.getPointProvider().getRoundList() == null)
				{
					return;
				} 
				
				List<TuningRoundInfo> info = policy.getPointProvider().getRoundList();
				/*
				 * Log Header
				 */
				output.println(headerJVSTMLog);
				output.println();
				output.println(headerParameters);
				output.println(Parameters.string());
				output.println();
				Date d = new Date();
				output.println("Timestamp: " + d.toString() + " (" + d.getTime() + ")");
				output.println();
				
				double throughputSum = 0f;
				Pattern executionTimePattern = Pattern.compile("(\\d+)\\s*");
				Matcher m = executionTimePattern.matcher(interceptedOutput);
				if(m.matches()) {
					output.println("ExecutionTime: " + m.group(1));
					float execSeconds = Integer.parseInt(m.group(1).trim()) / 1000f;
					for (TuningRoundInfo round : info)
					{
						for (TuningRecord record : round.getAlternatives())
						{
							throughputSum += record.getThroughput();
						}

					}
					output.println("TotalThroughput: " + throughputSum);
					throughputSum /= execSeconds;
					output.println(String.format("AverageThroughput: %.2f ops/s", throughputSum));
					
					System.err.println("Execution time from intercepted output saved to JVSTM log.");
				} else {
					System.err.println("output does not match expected format (execution time only) - not saved to log.");
				}
				
				/*
				 * 1. Get Tuning Rounds from the current policy.
				 */
				output.println();
				output.println(headerTuningRoundLogs);
				for (TuningRoundInfo round : info)
				{
					output.println(round.toString());
				}
				output.println();

				/*
				 * 2. Print tuning path for this session.
				 */
				output.println(headerTuningPath);
				for (TuningRoundInfo round : info)
				{
					for (TuningRecord record : round.getAlternatives())
					{
						output.print(record.getPoint().toString() + " ");
					}

				}
				output.println();
				/*
				 * 3. Print throughput for this session.
				 */
				output.println(headerThroughput);
				for (TuningRoundInfo round : info)
				{
					for (TuningRecord record : round.getAlternatives())
					{
						output.print(String.format("%.2f", record.getThroughput()) + " ");
					}

				}
				output.println();

				/*
				 * 4. Print TCR for this session.
				 */
				output.println(headerTCR);
				for (TuningRoundInfo round : info)
				{
					for (TuningRecord record : round.getAlternatives())
					{
						output.print(String.format("%.2f", record.getTcr()) + " ");
					}

				}
				output.println();

				if (Parameters.logDistances)
				{
					/*
					 * 5. Get distances to optimum, if we were using a stub
					 * optimum point.
					 */
					output.println(headerDistance);
					try
					{
						List<Float> distances = Controller.instance().getPolicy().getDataStub().getDistances();
						int i = 0;
						for (float distance : distances)
						{
							output.println((i++) + "," + String.format("%.2f", distance).replace(",", "."));
						}
					} catch (RuntimeException r)
					{

					}
					output.println();
				}
				output.println("## ");
				output.close();
				System.err.println("StatisticsCollector ShutdownHook finished execution - log file: " + logFile);
				Controller.setEnabled(true, "StatisticsCollector ShutdownHook");
			}
		};
	}

	public long getExecutionTime()
	{
		return executionTime;
	}

	public void setExecutionTime(long executionTime)
	{
		this.executionTime = executionTime;
	}

}
