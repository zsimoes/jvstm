package jvstm.tuning.policy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import jvstm.Transaction;
import jvstm.tuning.Controller;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;
import jvstm.tuning.Util;

public class ThroughtputMeasurementPolicy extends TuningPolicy
{

	BufferedWriter writer;
	protected int runCount;
	private int interval;
	private int fileNameCounter = 0;

	private String filename;
	private String folder;

	public ThroughtputMeasurementPolicy(Controller controller, int intervalMillis)
	{
		super(controller);
		this.interval = intervalMillis;
		init();

		filename = Util.getSystemProperty("filename");
		folder = Util.getSystemProperty("folder");

	}

	private void init()
	{
		try
		{
			System.err.println("--DEBUG THROUGHPUT: FILENAME: " + filename + "    FOLDER: " + folder);
			String path;
			if (filename == null)
			{
				path = "throughput" + fileNameCounter + ".data";
			} else
			{
				path = filename;
			}

			if (folder != null)
			{
				path = folder + "/" + path;
			}

			File f = new File(path);
			if (filename == null)
			{
				while (f.exists())
				{
					f = new File("throughput" + (++fileNameCounter) + ".data");
				}
			}
			System.err.println("ThroughputMeasurementPolicy output file: " + f.getAbsolutePath());
			writer = new BufferedWriter(new FileWriter(f));
			writer.write("interval: " + interval + "ms");
			writer.newLine();
		} catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		resetData();
	}

	@Override
	public void clearInternalData()
	{
		resetData();
		init();
	}

	protected void resetData()
	{
		runCount = 0;
	}

	@Override
	public void run(boolean mergePerThreadStatistics)
	{

		if (mergePerThreadStatistics)
		{
			mergeStatistics();
		}

		long throughput = getThroughput(true);

		try
		{
			writer.write("" + throughput);
			writer.newLine();
			writer.flush();
		} catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		// HERE save throughput

		runCount++;
	}

	@Override
	public Tunable newTunable()
	{
		return new ThreadState(ThreadState.RUNNABLE);
	}

	@Override
	public void finishTransaction(Transaction t, boolean nested)
	{
		t.getTuningContext().getThreadState().finish();
		t.getTuningContext().getThreadState().setRunnable(false);
	}

	@Override
	public void tryRunTransaction(Transaction t, boolean nested)
	{
		t.getTuningContext().getThreadState().tryRun();
	}
}
