package jvstm.tuning.policy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jvstm.tuning.ThreadStatistics;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningContext;

public abstract class TuningPolicy {

	// Region fields
	protected Map<Long, TuningContext> topLevelContexts;
	protected Map<Long, TuningContext> nestedContexts;
	// the values passed to the following constructors are not used
	protected ThreadStatistics globalTopLevelStatistics = new ThreadStatistics(-1, 0);
	protected ThreadStatistics globalNestedStatistics = new ThreadStatistics(-1, 0);
	protected ThreadStatistics globalStatistics = new ThreadStatistics(-1, 0);
	// EndRegion

	protected AtomicInteger maxTopLevelThreads = new AtomicInteger(2);
	protected AtomicInteger maxNestedThreads = new AtomicInteger(2);
	protected AtomicInteger currentTopLevelThreads = new AtomicInteger(0);
	protected AtomicInteger currentNestedThreads = new AtomicInteger(0);

	public TuningPolicy(Map<Long, TuningContext> topLevelContexts, Map<Long, TuningContext> nestedContexts) {
		this.topLevelContexts = topLevelContexts;
		this.nestedContexts = nestedContexts;
	}

	public TuningPolicy() {
		this.topLevelContexts = new ConcurrentHashMap<Long, TuningContext>();
		this.nestedContexts = new ConcurrentHashMap<Long, TuningContext>();
	}

	public void reset(Map<Long, TuningContext> topLevelContexts, Map<Long, TuningContext> nestedContexts) {
		clearInternalData();
		this.topLevelContexts = topLevelContexts;
		this.nestedContexts = nestedContexts;
	}

	// to do: use lock?
	// does not cause memory consistency errors but is not thread-safe
	public void mergeStatistics() {
		for (TuningContext ctx : topLevelContexts.values()) {
			ctx.getStatistics().addTo(globalTopLevelStatistics);
			ctx.getStatistics().addTo(globalStatistics);
		}
		for (TuningContext ctx : nestedContexts.values()) {
			ctx.getStatistics().addTo(globalNestedStatistics);
			ctx.getStatistics().addTo(globalStatistics);
		}
	}
	
	//Transaction Commit Rate (TCR), the percentage of committed transactions out of all executed transactions in a	sample period
	public float getTCR(boolean resetStatistics) {
		float tcr = ((float)globalStatistics.getCommitCount()) / globalStatistics.getTransactionCount();
		
		if(Float.isNaN(tcr)) {
			tcr = 0;
		}
		
		if(resetStatistics) {
			for(TuningContext ctx : topLevelContexts.values()) {
				ctx.getStatistics().reset();
			}
			for(TuningContext ctx : nestedContexts.values()) {
				ctx.getStatistics().reset();
			}
			globalTopLevelStatistics.reset();
			globalNestedStatistics.reset();
			globalStatistics.reset();
		}
		
		return tcr;
	}
	
	public AtomicInteger getMaxNestedThreads() {
		return maxNestedThreads;
	}

	public AtomicInteger getMaxTopLevelThreads() {
		return maxTopLevelThreads;
	}

	public void registerContext(TuningContext context) {
		if (context.isNested()) {
			nestedContexts.put(context.getThreadId(), context);
		} else {
			topLevelContexts.put(context.getThreadId(), context);
		}
		context.setRegistered(true);
	}

	//TODO: deal with nesting level
	public TuningContext registerThread(long threadId, boolean nested) {
		
		Tunable newState = newTunable(nested);
		ThreadStatistics stats = new ThreadStatistics(threadId, -1);
		TuningContext ctx = new TuningContext(threadId, -1, newState, stats);
		registerContext(ctx);
		return ctx;
	}

	// clear any data generated using the statistics
	public abstract void clearInternalData();

	// run main algorithm to adjust <contexts>. if <mergePerThreadStatistics> is
	// true, merge all stats into <globalStatistics> before taking action
	public abstract void run(boolean mergePerThreadStatistics);

	// Create and return new tunable, in whatever state this policy requires
	public abstract Tunable newTunable(boolean nested);
	
	public abstract void finishTransaction(TuningContext t);
	
	public abstract void tryRunTransaction(TuningContext t);

}
