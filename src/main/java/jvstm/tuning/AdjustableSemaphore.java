package jvstm.tuning;

import java.util.concurrent.Semaphore;

public class AdjustableSemaphore extends Semaphore
{

	public AdjustableSemaphore(int permits)
	{
		super(permits);
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

}