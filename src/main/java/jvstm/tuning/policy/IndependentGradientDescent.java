package jvstm.tuning.policy;

import jvstm.tuning.TuningContext;
import jvstm.util.Pair;

public class IndependentGradientDescent extends LinearGradientDescent {

	public static int[] deltas;
	
	static {
		deltas = new int[2];
		deltas[0] = 1;
		deltas[1] = -1;
		
		roundSize = deltas.length;
	}

	@Override
	protected boolean GDNextRun() {
		// set the current point and max threads accordingly:
		int newTopLevel = currentFixedPoint.first + deltas[runCount];
		int newNested = currentFixedPoint.second + deltas[runCount];
		if (newTopLevel < 1 || newNested < 1) {
			// this move would take us to a negative value. Return false to
			// ensure this run is repeated with other point.
			return false;
		}
		System.err.println("\t>>new run point: {" + newTopLevel + "," + newNested + "}");
		setCurrentPoint(newTopLevel, newNested);
		resumeWaitingThreads();
		return true;
	}

}
