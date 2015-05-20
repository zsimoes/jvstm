package jvstm.tuning;

/*
 * This class serves as a container for statistics and tunable state for each transactional thread
 */
public class TuningContext
{

	boolean registered;
	private long threadId;
	private int nestingLevel;
	private ThreadStatistics statistics;
	private Tunable threadState;

	public TuningContext(long threadId, int nestingLevel)
	{
		registered = false;
		this.threadId = threadId;
		this.nestingLevel = nestingLevel;
		this.statistics = new ThreadStatistics(threadId, nestingLevel);
		this.threadState = new ThreadState(ThreadState.RUNNABLE);
	}

	public TuningContext(long threadId, int nestingLevel, Tunable state)
	{
		this(threadId, nestingLevel);
		this.threadState = state;
	}

	public TuningContext(long threadId, int nestingLevel, Tunable state, ThreadStatistics statistics)
	{
		this(threadId, nestingLevel, state);
		this.statistics = statistics;
	}

	public ThreadStatistics getStatistics()
	{
		return statistics;
	}

	public void setStatistics(ThreadStatistics statistics)
	{
		this.statistics = statistics;
	}

	public Tunable getThreadState()
	{
		return threadState;
	}

	public void setThreadState(Tunable threadState)
	{
		this.threadState = threadState;
	}

	public boolean isNested()
	{
		return (nestingLevel > 0);
	}

	public Long getThreadId()
	{
		return threadId;
	}

	public boolean isRegistered()
	{
		return registered;
	}

	public void setRegistered(boolean registered)
	{
		this.registered = registered;
	}

	@Override
	public String toString()
	{
		return "TuningContext [registered=" + registered + ", threadId=" + threadId + ", nestingLevel=" + nestingLevel
				+ ", statistics=" + statistics + ", threadState=" + threadState + "]";
	}

}
