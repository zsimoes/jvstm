package jvstm.tuning.policy;

import jvstm.tuning.Controller;


public class InterleavedGradientDescent extends IndependentGradientDescent
{

	public InterleavedGradientDescent(Controller controller)
	{
		super(controller);
		// TODO Auto-generated constructor stub
	}

	public static int[] deltas;

	static
	{
		deltas = new int[2];
		deltas[0] = 1;
		deltas[1] = -1;

		roundSize = deltas.length;
	}

	boolean changeX = false;

	@Override
	protected boolean GDNextRun()
	{
		// set the current point and max threads accordingly:
		int newTopLevel = currentFixedPoint.first;
		int newNested = currentFixedPoint.second;
		
		if (changeX)
		{
			newTopLevel = currentFixedPoint.first + deltas[runCount];
		} else
		{
			newNested = currentFixedPoint.second + deltas[runCount];
		}
		
		changeX = !changeX;
		
		if (newTopLevel < 1 || newNested < 1)
		{
			// this move would take us to a negative value. Return false to
			// ensure this run is repeated with other point.
			return false;
		}
		setCurrentPoint(newTopLevel, newNested);
		return true;
	}

}
