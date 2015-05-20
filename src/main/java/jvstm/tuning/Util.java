package jvstm.tuning;

public class Util
{

	// stupid java
	public static String getSystemProperty(String key)
	{
		String value = System.getProperty(key);
		if (value == null)
		{
			value = System.getenv(key);
		}
		return value;
	}

	
	
}
