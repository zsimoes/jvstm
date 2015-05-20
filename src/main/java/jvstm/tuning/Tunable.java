package jvstm.tuning;

public interface Tunable
{

	public int getState();

	public void tryRun();

	public void finish();

	public void setRunnable(boolean notify);

	public void notifyTunable();

	public void setWaiting();

	public boolean isWaiting();

}