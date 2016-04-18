package jvstm.tuning.policy;

import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.Parameters;
import jvstm.tuning.TuningPoint;

/**
 * 
 * @author Ze This class runs the full exploration and decision process (same as
 *         LinearGD4) but does not change the actual coordinates. Used to test
 *         overhead.
 */
public class FakeLinearGradientDescent extends LinearGradientDescent4
{

	public FakeLinearGradientDescent(Controller controller)
	{
		super(controller);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void init()
	{
		TuningPoint initialConfig = Parameters.initialConfig;
		if (initialConfig == null)
		{
			initialConfig = pointProvider.getInitialPoint();
		}

		topLevelSemaphore = new AdjustableSemaphore(initialConfig.first);
		int nested = initialConfig.first * initialConfig.second;
		nestedSemaphore = new AdjustableSemaphore(nested);

		currentPoint = new TuningPoint(initialConfig.first, nested);
		this.pointProvider = createPointProvider();
	}

	@Override
	protected void setCurrentPoint(int topLevel, int nested)
	{
		// do nothing
	}

}
