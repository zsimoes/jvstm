package jvstm.tuning;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class AdjustableSemaphore extends Semaphore
{

	public static class InvalidException extends RuntimeException
	{

		public InvalidException()
		{
			super();
			// TODO Auto-generated constructor stub
		}

		public InvalidException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace)
		{
			super(message, cause, enableSuppression, writableStackTrace);
			// TODO Auto-generated constructor stub
		}

		public InvalidException(String message, Throwable cause)
		{
			super(message, cause);
			// TODO Auto-generated constructor stub
		}

		public InvalidException(String message)
		{
			super(message);
			// TODO Auto-generated constructor stub
		}

		public InvalidException(Throwable cause)
		{
			super(cause);
			// TODO Auto-generated constructor stub
		}

	}

	private volatile boolean invalid = false;

	private volatile int acquired = 0;

	public int getAcquired()
	{
		return acquired;
	}

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

	public void invalidate()
	{
		invalid = true;
	}
	
	public boolean isInvalid() {
		return invalid;
	}

	protected void checkInvalid()
	{
		if (invalid)
		{
			throw new InvalidException();
		}
	}

	//
	// Acquire
	//

	@Override
	public void acquire() throws InterruptedException
	{
		checkInvalid();
		super.acquire();
		acquired++;
	}

	@Override
	public void acquire(int permits) throws InterruptedException
	{
		checkInvalid();
		super.acquire(permits);
		acquired += permits;
	}

	@Override
	public void acquireUninterruptibly()
	{
		checkInvalid();
		super.acquireUninterruptibly();
		acquired++;
	}

	@Override
	public void acquireUninterruptibly(int permits)
	{
		checkInvalid();
		super.acquireUninterruptibly(permits);
		acquired += permits;
	}

	//
	// Release
	//

	@Override
	public void release()
	{
		super.release();
		acquired--;
	}

	@Override
	public void release(int permits)
	{
		super.release(permits);
		acquired -= permits;
	}

	//
	// Unsupported Operations:
	//

	@Override
	public boolean tryAcquire()
	{
		throw new UnsupportedOperationException();
		// return super.tryAcquire();
	}

	@Override
	public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException
	{
		throw new UnsupportedOperationException();
		// return super.tryAcquire(timeout, unit);
	}

	@Override
	public boolean tryAcquire(int permits)
	{
		throw new UnsupportedOperationException();
		// return super.tryAcquire(permits);
	}

	@Override
	public boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException
	{
		throw new UnsupportedOperationException();
		// return super.tryAcquire(permits, timeout, unit);
	}

}