package jvstm.tuning;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadStatistics {

	private AtomicInteger transactionCount;
	private AtomicInteger commitCount;
	private AtomicInteger abortCount;
	private long threadId;
	private int nestingLevel;
	
	public ThreadStatistics(long threadId, int nestingLevel) {
		transactionCount = new AtomicInteger(0);
		commitCount = new AtomicInteger(0);
		abortCount = new AtomicInteger(0);
		this.threadId = threadId;
		this.nestingLevel = nestingLevel;
	}
	
	public void reset() {
		transactionCount.set(0);
		commitCount.set(0);
		abortCount.set(0);
		nestingLevel = 0;
	}
	
	public void reset(long threadId) {
		this.threadId = threadId;
		reset();
	}
	
	
	//to do: use lock?
	//does not cause memory consistency errors but is not thread-safe
	public void addTo(ThreadStatistics stat) {
		stat.transactionCount.addAndGet(this.transactionCount.get());
		stat.commitCount.addAndGet(this.commitCount.get());
		stat.abortCount.addAndGet(this.abortCount.get());
	}
	
	public long getThreadId() {
		return threadId;
	}
	
	public int getNestingLevel() {
		return nestingLevel;
	}

	protected void setNestingLevel(int nestingLevel) {
		this.nestingLevel = nestingLevel;
	}

	public int getTransactionCount() {
		return transactionCount.get();
	}
	public void setTransactionCount(int transactionCount) {
		this.transactionCount.set(transactionCount);
	}
	public void incTransactionCount() {
		transactionCount.incrementAndGet();
	}
	
	public int getCommitCount() {
		return commitCount.get();
	}
	public void setCommitCount(int commitCount) {
		this.commitCount.set(commitCount);;
	}
	public void incCommitCount() {
		commitCount.incrementAndGet();
	}
	
	public int getAbortCount() {
		return abortCount.get();
	}
	public void setAbortCount(int abortCount) {
		this.abortCount.set(abortCount);
	}
	public void incAbortCount() {
		abortCount.incrementAndGet();
	}
	
	
}
