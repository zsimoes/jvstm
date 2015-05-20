package jvstm.tuning;

public class ThreadState implements Tunable
{

	public static final int WAITING = 0;
	public static final int RUNNABLE = 1;
	public static final int RUNNING = 2;

	private volatile int state;
	private boolean waiting = false;

	public ThreadState(int state)
	{
		if (state < WAITING || state > RUNNING)
		{
			throw new RuntimeException("ThreadState(): Invalid initial state");
		}
		this.state = state;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jvstm.tuning.Tunable#getState()
	 */
	@Override
	public int getState()
	{
		return state;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jvstm.tuning.Tunable#tryRun()
	 */
	@Override
	public synchronized void tryRun()
	{
		if (state == WAITING)
		{
			// System.err.println(" !-!-! ThreadState.tryRun(): thread deactivated, waiting for notify()");
			this.waiting = true;
			while (state != RUNNABLE)
			{
				try
				{
					this.wait();
				} catch (InterruptedException e)
				{
					// ignore
				}
			}
			state = RUNNING;
			this.waiting = false;
			// System.err.println(" !-!-! ThreadState.tryRun(): notified, running now.");
		} else if (state == RUNNABLE)
		{
			state = RUNNING;
		}
	}

	@Override
	public boolean isWaiting()
	{
		return waiting;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jvstm.tuning.Tunable#finish()
	 */
	@Override
	public synchronized void finish()
	{
		// Nothing.
		// In this particular class, the tuning policy dictates the state after
		// finishing a tx.
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jvstm.tuning.Tunable#setRunnable()
	 */
	@Override
	public synchronized void setRunnable(boolean notify)
	{

		this.state = RUNNABLE;
		this.waiting = false;
		if (notify)
		{
			this.notify();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jvstm.tuning.Tunable#setWaiting()
	 */
	@Override
	public synchronized void setWaiting()
	{
		this.state = WAITING;
		this.waiting = true;
	}

	@Override
	public String toString()
	{
		return "ThreadState [state=" + state + ", waiting=" + waiting + "]";
	}

	@Override
	public synchronized void notifyTunable()
	{
		this.notify();
	}

}
