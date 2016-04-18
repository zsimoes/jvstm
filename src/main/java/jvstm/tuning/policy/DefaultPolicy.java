package jvstm.tuning.policy;

import jvstm.Transaction;
import jvstm.tuning.Controller;
import jvstm.tuning.Parameters;
import jvstm.tuning.ThreadState;
import jvstm.tuning.Tunable;

public class DefaultPolicy extends TuningPolicy
{

	public DefaultPolicy(Controller controller)
	{
		super(controller);
		init();
		pointBinder.setMaximum(Parameters.maxThreads);
		// TODO Auto-generated constructor stub
	}

	protected int runCount;

	private void init()
	{
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
		//nothing
	}

	@Override
	public void tryRunTransaction(Transaction t, boolean nested)
	{
		//nothing
	}

	@Override
	protected PointProvider createPointProvider()
	{
		throw new UnsupportedOperationException();
	}

}
