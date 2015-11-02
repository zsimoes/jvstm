package jvstm.tuning;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import jvstm.tuning.policy.PointProvider.TuningRoundInfo;

public class StatisticsCollector
{
	private boolean disabled = false;

	private PrintWriter output;
	private String logFilePath;
	@SuppressWarnings("unused")
	private int tuningInterval;

	public StatisticsCollector(String logFile, int tuningInterval)
	{
		this.tuningInterval = tuningInterval;
		try
		{
			output = new PrintWriter(logFile);
			this.logFilePath = new File(logFile).getAbsolutePath();
			System.err.println("Log file path: " + logFilePath);
		} catch (IOException e)
		{
			if (output != null)
			{
				output.close();
			}
			disabled = true;
			System.err.println("Statistics Collector Disabled (invalid file pattern: - " + logFile + ")");
		}

		// ShutdownHook to flush data into the log files:
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{

			@Override
			public void run()
			{
				List<TuningRoundInfo> info = Controller.instance().getPolicy().getPointProvider().getRoundList();
				if (disabled)
				{
					// print logs to stderr:
					System.err.println("PointProvider logs:");
					for (TuningRoundInfo round : info)
					{
						System.err.println(round.toString());
					}
					return;
				}
				// else:
				for (TuningRoundInfo round : info)
				{
					output.println(round.toString());
				}

				output.close();
				System.err.println("StatisticsCollector ShutdownHook finished execution - log file: " + logFilePath);
			}
		}));
	}

}
