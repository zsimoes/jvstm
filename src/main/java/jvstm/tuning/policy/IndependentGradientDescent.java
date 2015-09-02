package jvstm.tuning.policy;

import jvstm.tuning.Controller;


public class IndependentGradientDescent extends LinearGradientDescent3 {

	public IndependentGradientDescent(Controller controller)
	{
		super(controller);
		// TODO Auto-generated constructor stub
	}

	public static int[] deltas;
	
	static {
		deltas = new int[2];
		deltas[0] = 1;
		deltas[1] = -1;
		
		roundSize = deltas.length;
	}
	
	protected int bestX, bestY;

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
		setCurrentPoint(newTopLevel, newNested);
		return true;
	}
	
	@Override
	protected float GDSaveMeasurement() {
		float tcr = getMeasurement(true);
		if (tcr > bestTCR) {
			bestX = currentPoint.first;
			bestY = currentPoint.second;
			bestTCR = tcr;
		}

		return tcr;
	}
	
	@Override
	protected float GDEndRound(float tcr) {
		// set the current point and set max threads accordingly:
		setCurrentPoint(bestX, bestY);
		setCurrentFixedPoint(bestPoint);
		setPreviousBestPoint(bestPoint);
		
		float best = bestTCR;

		// reset count and bestTCR:
		resetData();
		return best;
	}

}
