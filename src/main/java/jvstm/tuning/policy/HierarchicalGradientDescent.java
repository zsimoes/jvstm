package jvstm.tuning.policy;

import jvstm.tuning.Controller;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningContext;

public class HierarchicalGradientDescent extends TuningPolicy {

	public HierarchicalGradientDescent(Controller controller)
	{
		super(controller);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void clearInternalData() {
		// TODO Auto-generated method stub

	}

	@Override
	public void run(boolean mergePerThreadStatistics) {
		// TODO Auto-generated method stub

	}

	@Override
	public Tunable newTunable() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void finishTransaction(TuningContext t) {
		// TODO Auto-generated method stub

	}

	@Override
	public void tryRunTransaction(TuningContext t, boolean nested) {
		// TODO Auto-generated method stub

	}

}
