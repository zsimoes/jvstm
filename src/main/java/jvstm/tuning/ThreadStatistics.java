package jvstm.tuning;

public class ThreadStatistics
{

	private volatile long transactionCount;
	private volatile long commitCount;
	private volatile long abortCount;
	private volatile long threadId;
	private volatile long nestingLevel;

	public ThreadStatistics(long threadId, int nestingLevel)
	{
		this.threadId = threadId;
		this.nestingLevel = nestingLevel;
	}

	public void reset()
	{
		transactionCount = 0;
		commitCount = 0;
		abortCount = 0;
		nestingLevel = 0;
	}

	public void reset(long threadId)
	{
		this.threadId = threadId;
		reset();
	}

	// to do: use lock?
	// does not cause memory consistency errors but is not thread-safe
	public void addTo(ThreadStatistics stat)
	{
		stat.transactionCount += this.transactionCount;
		stat.commitCount += this.commitCount;
		stat.abortCount += this.abortCount;
	}

	public long getThreadId()
	{
		return threadId;
	}

	public long getNestingLevel()
	{
		return nestingLevel;
	}

	protected void setNestingLevel(int nestingLevel)
	{
		this.nestingLevel = nestingLevel;
	}

	public long getTransactionCount()
	{
		return transactionCount;
	}

	public void setTransactionCount(int transactionCount)
	{
		this.transactionCount = transactionCount;
	}

	public void incTransactionCount()
	{
		transactionCount++;
	}

	public long getCommitCount()
	{
		return commitCount;
	}

	public void setCommitCount(int commitCount)
	{
		this.commitCount = commitCount;
	}

	public void incCommitCount()
	{
		commitCount++;
	}

	public long getAbortCount()
	{
		return abortCount;
	}

	public void setAbortCount(int abortCount)
	{
		this.abortCount = abortCount;
	}

	public void incAbortCount()
	{
		abortCount++;
	}

	@Override
	public String toString()
	{
		return "ThreadStatistics [transactionCount=" + transactionCount + ", commitCount=" + commitCount
				+ ", abortCount=" + abortCount + ", threadId=" + threadId + ", nestingLevel=" + nestingLevel + "]";
	}

}
