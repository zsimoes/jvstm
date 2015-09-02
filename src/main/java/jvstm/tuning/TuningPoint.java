package jvstm.tuning;

import jvstm.util.Pair;

public class TuningPoint extends Pair<Integer, Integer>
{

	public TuningPoint()
	{
		super();
	}

	public TuningPoint(Integer first, Integer second)
	{
		super(first, second);
	}
	
	@Override
	public String toString()
	{
		return "[" + first + "," + second + "]";
	}

}
