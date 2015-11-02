package jvstm.tuning.policy;

import jvstm.tuning.Controller;
import jvstm.tuning.TuningPoint;


public class IndependentGradientDescent extends LinearGradientDescent4 {

	static class IndependentGDPointProvider extends PointProvider {

		public IndependentGDPointProvider(int roundSize, PointBinder pointBinder)
		{
			super(roundSize, pointBinder);
			// TODO Auto-generated constructor stub
		}

		@Override
		protected TuningPoint doGetPoint()
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public TuningPoint getInitialPoint()
		{
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	public IndependentGradientDescent(Controller controller)
	{
		super(controller);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected PointProvider createPointProvider() {
		return new IndependentGDPointProvider(-1, pointBinder);
	}

}
