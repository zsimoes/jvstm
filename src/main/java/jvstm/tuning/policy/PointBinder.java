package jvstm.tuning.policy;

import jvstm.tuning.TuningPoint;

public class PointBinder
{
	private int maximum;
	private int maxX;
	private int maxY;
	private boolean independentMaximums;

	public PointBinder(int maximum)
	{
		this.maximum = maximum;
		this.independentMaximums = false;
	}

	public PointBinder(int maximum, int maxX, int maxY)
	{
		this.maximum = maximum;
		this.maxX = maxX;
		this.maxY = maxY;
		this.independentMaximums = true;
		throw new UnsupportedOperationException();
	}

	public TuningPoint getMidPoint()
	{
		TuningPoint result = new TuningPoint(1, 1);
		while (isBound(result))
		{
			result.first++;
			result.second++;
		}
		result.first--;
		result.second--;
		return result;
	}

	public boolean isBound(int top, int nested)
	{
		return top * nested < maximum && top > 0 && nested > 0;
	}

	public boolean isBound(TuningPoint point)
	{
		return isBound(point.first, point.second);
	}

	protected TuningPoint constrain(TuningPoint target)
	{
		if (independentMaximums)
		{
			return constrainIndependent(target);
		}
		return constrainLinear(target);
	}

	protected TuningPoint constrainIndependent(TuningPoint target)
	{
		throw new UnsupportedOperationException();
	}

	protected TuningPoint constrainLinear(TuningPoint target)
	{
		int oldX = target.first, oldY = target.second;
		// Below axis?

		if (oldX < 1 && oldY < 1)
		{
			return new TuningPoint(1, 1);
		} else if (oldX < 1)
		{
			return new TuningPoint(1, oldY);
		} else if (oldY < 1)
		{
			return new TuningPoint(oldX, 1);
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
			return new TuningPoint(newX, oldY);
		} else
		{
			return new TuningPoint(oldX, newY);
		}
	}

	public int getMaximum()
	{
		return maximum;
	}

	public void setMaximum(int max)
	{
		maximum = max;
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
