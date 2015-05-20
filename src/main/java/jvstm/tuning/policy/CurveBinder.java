package jvstm.tuning.policy;

import jvstm.util.Pair;

public class CurveBinder
{
	private int maximum;
	private int maxX;
	private int maxY;
	private boolean independentMaximums;

	public CurveBinder(int maximum)
	{
		this.maximum = maximum;
		this.independentMaximums = false;
	}

	public CurveBinder(int maximum, int maxX, int maxY)
	{
		this.maximum = maximum;
		this.maxX = maxX;
		this.maxY = maxY;
		this.independentMaximums = true;
		throw new UnsupportedOperationException();
	}
	
	public Pair<Integer, Integer> getMidPoint(){
		Pair<Integer, Integer> result = new Pair<Integer, Integer>(1,1);
		while(isBound(result)) {
			result.first++;
			result.second++;
		}
		result.first--;
		result.second--;
		return result;
	}
	
	public boolean isBound(Pair<Integer, Integer> point) {
		return point.first * point.second < maximum;
	}

	protected Pair<Integer, Integer> constrain(Pair<Integer, Integer> target)
	{
		if (independentMaximums)
		{
			return constrainIndependent(target);
		}
		return constrainLinear(target);
	}

	protected Pair<Integer, Integer> constrainIndependent(Pair<Integer, Integer> target)
	{
		throw new UnsupportedOperationException();
	}

	protected Pair<Integer, Integer> constrainLinear(Pair<Integer, Integer> target)
	{
		int oldX = target.first, oldY = target.second;
		// Below axis?

		if (oldX < 1 && oldY < 1)
		{
			return new Pair<Integer, Integer>(1, 1);
		} else if (oldX < 1)
		{
			return new Pair<Integer, Integer>(1, oldY);
		} else if (oldY < 1)
		{
			return new Pair<Integer, Integer>(oldX, 1);
		}
		
		int product = oldX * oldY;
		if (product <= maximum)
		{
			// okay
			return target;
		}

		// find the closest bound point (i.e. inside the allowed product curve)
		int newX = maximum / oldY;
		int newY = maximum / oldX;
		int diffX = oldX - newX;
		int diffY = oldY - newY;

		if (diffX < diffY)
		{
			return new Pair<Integer, Integer>(newX, oldY);
		} else
		{
			return new Pair<Integer, Integer>(oldX, newY);
		}
	}

	public int getMaximum()
	{
		return maximum;
	}

	public int getMaxX()
	{
		return maxX;
	}

	public int getMaxY()
	{
		return maxY;
	}

	public boolean isIndependentMaximums()
	{
		return independentMaximums;
	}

}
