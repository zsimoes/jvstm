package jvstm.tuning;

public interface Tunable {

	public int getState();

	public void tryRun();

	public void finish();

	public void setRunnable();

	public void setWaiting();
	
	public boolean isWaiting();

}