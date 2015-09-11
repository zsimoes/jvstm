package jvstm.tuning;

import java.util.concurrent.Semaphore;

public class AdjustableSemaphore extends Semaphore
{
	private static final long serialVersionUID = 6465902012679612201L;

	public AdjustableSemaphore(int permits)
	{
		super(permits, true);
	}

	public AdjustableSemaphore(int permits, boolean fair)
	{
		super(permits, fair);
	}

	@Override
	public void reducePermits(int reduction)
	{
		super.reducePermits(reduction);
	}

	//
	// Unsupported Operations:
	//

	/*
	 * @Override public boolean tryAcquire() { throw new
	 * UnsupportedOperationException(); // return super.tryAcquire(); }
	 * 
	 * @Override public boolean tryAcquire(long timeout, TimeUnit unit) throws
	 * InterruptedException { throw new UnsupportedOperationException(); //
	 * return super.tryAcquire(timeout, unit); }
	 * 
	 * @Override public boolean tryAcquire(int permits) { throw new
	 * UnsupportedOperationException(); // return super.tryAcquire(permits); }
	 * 
	 * @Override public boolean tryAcquire(int permits, long timeout, TimeUnit
	 * unit) throws InterruptedException { throw new
	 * UnsupportedOperationException(); // return super.tryAcquire(permits,
	 * timeout, unit); }
	 */

}