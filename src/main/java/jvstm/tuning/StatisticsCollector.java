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
			if (logFile == null)
			{
				throw new IOException();
			}
			output = new PrintWriter(logFile);
			this.logFilePath = new File(logFile).getAbsolutePath();
			System.err.println("Log file path: " + logFilePath);
		} catch (IOException e)
		{
			if (output != null)
			{
				output.close();
			}
			output = new PrintWriter(System.err);
			System.err.println("StatisticsCollector output defaulted to stderr "
					+ (logFile == null ? "" : "(invalid file name: - " + logFile + ")"));
		}

		// ShutdownHook to flush data into the log files:
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
		{

			@Override
			public void run()
			{
				Controller.setEnabled(false);
				System.err.println("Controller disabled by tuning shutdown hook");
				List<TuningRoundInfo> info = Controller.instance().getPolicy().getPointProvider().getRoundList();
				// log tuning rounds:
				output.println();
				output.println("PointProvider logs:");
				for (TuningRoundInfo round : info)
				{
					output.println(round.toString());
				}
				output.println();

				// log optimum distances:
				output.println("Distance to optimum logs:");
				List<Float> distances = Controller.instance().getPolicy().getDataStub().getDistances();
				int i = 0;
				for (float distance : distances)
				{
					output.println((i++) + "," + String.format("%.2f", distance).replace(",", "."));
				}
				output.println();
				output.close();
				System.err.println("StatisticsCollector ShutdownHook finished execution - log file: " + logFilePath);
			}
		}));
	}

}
