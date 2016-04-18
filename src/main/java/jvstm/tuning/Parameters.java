package jvstm.tuning;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jvstm.tuning.policy.DefaultPolicy;
import jvstm.tuning.policy.FakeLinearGradientDescent;
import jvstm.tuning.policy.FullGradientDescent;
import jvstm.tuning.policy.HierarchicalGradientDescent;
import jvstm.tuning.policy.LinearGradientDescent4;
import jvstm.tuning.policy.TuningPolicy;
import jvstm.tuning.policy.TuningPolicy.MeasurementType;

public final class Parameters
{
	public static boolean noStats = false;
	public static String logFile;

	public static int interval = 100;
	public static int maxThreads = Integer.MAX_VALUE;
	public static TuningPoint initialConfig;
	public static Class<? extends TuningPolicy> policy;

	public static MeasurementType measurementType;
	public static String stubFile;
	public static TuningPoint stubOptimum;
	public static boolean useStubFile = false;
	public static boolean logDistances;
	
	public static String contention;
	public static String aditionalBenchmarkInfo = "";

	private static Map<String, Class<? extends TuningPolicy>> policies;

	private Parameters()
	{
	}

	public static String string()
	{
		String nl = String.format("%n");
		StringBuilder params = new StringBuilder();

		params.append("  Policy: " + policy.getName() + nl);
		params.append("  LogFile: " + logFile + nl);
		params.append("  Interval: " + interval + nl);
		params.append("  MaxThreads: " + maxThreads + nl);
		params.append("  InitialConfig: " + initialConfig + nl);
		params.append("  MeasurementType: " + measurementType + nl);
		params.append("  Contention: " + contention + nl);
		if (measurementType == MeasurementType.stub)
		{
			params.append("  StubFile: " + stubFile + nl);
			params.append("  StubOptimum: " + stubOptimum + nl);
			params.append("  LogDistances: " + logDistances + nl);
		}

		if (aditionalBenchmarkInfo != null && aditionalBenchmarkInfo != "")
		{
			params.append("  Additional info from benchmark: " + aditionalBenchmarkInfo + nl);
		}
		params.append(nl);

		return params.toString();
	}

	public static String usage()
	{

		String nl = String.format("%n");
		String nlnl = nl + nl;

		StringBuilder usage = new StringBuilder();
		usage.append("JVSTM tuning options are passed via -Doption=value to the jvm. Options:" + nlnl);
		usage.append("  Policy - one of " + nl + "  \t" + policies.keySet() + nl);
		usage.append("  NoStats - boolean value, enables or disables statistics collection" + nl);
		usage.append("  LogFile - log file path Ignored if -DNoStats is set to true" + nl);
		usage.append("  Interval - interval between policy runs, integral value in milliseconds" + nl);
		usage.append(
				"  MaxThreads - maximum number of threads to use, ideally the same as the number of cores, integral value"
						+ nl);
		usage.append("  InitialConfig - initial tuning configuration - format: -DInitialConfig=x,y" + nl);
		usage.append("  MeasurementType - one of " + MeasurementType.names() + nl);
		usage.append("  Contention (optional) - arbitrary string describing benchmark contention type." + nl);
		usage.append("  \tIf the measurement type is \"stub\", the following options become available:" + nl);
		usage.append("  \tStubFile - path to file with stub data" + nl);
		usage.append(
				"  \tStubOptimum - stub optimum point for linear distance analysis - format: -DStubOptimum=x,y" + nl);
		usage.append("  \tLogDistances - boolean value" + nl);
		usage.append("  \tAditionalBenchmarkInfo - arbitrary string with aditional info" + nl);
		// usage.append(" " + nl);

		return usage.toString();
	}

	public static void printUsageAndExit(String error)
	{
		String usage = usage();

		System.err.println("Tuning configuration error: ");
		System.err.println(error);
		System.err.println();
		System.err.println(usage);

		System.exit(-1);

	}

	static
	{
		policies = new HashMap<String, Class<? extends TuningPolicy>>();
		policies.put("Default", DefaultPolicy.class);
		policies.put("LinearGD", LinearGradientDescent4.class);
		policies.put("FullGD", FullGradientDescent.class);
		policies.put("HierarchicalGD", HierarchicalGradientDescent.class);
		policies.put("FakeLinearGD", FakeLinearGradientDescent.class);
	}

	public static void setup()
	{

		/*
		 * Policy
		 */

		String pol = Util.getSystemProperty("Policy");
		if (pol == null)
		{
			System.err.println("Policy System Property not found - defaulting to LinearGD.");
			policy = LinearGradientDescent4.class;
		} else
		{
			policy = policies.get(pol);

			if (policy == null)
			{
				printUsageAndExit("jvstm.tuning.Parameters (init) - Invalid Policy: " + pol + "." + String.format("%n")
						+ "Available policies are: " + policies.keySet());
			}

		}

		/*
		 * No Stats
		 */
		String noStatsProp = Util.getSystemProperty("NoStats");
		noStats = Boolean.parseBoolean(noStatsProp);
		if (noStats == false)
		{
			/*
			 * Log file and policy interval
			 */
			logFile = Util.getSystemProperty("LogFile");
			String intervalProp = Util.getSystemProperty("Interval");
			try
			{
				interval = Integer.parseInt(intervalProp);
			} catch (NumberFormatException e)
			{
				printUsageAndExit("Invalid policy interval value. Use \"java -DInterval=<milliseconds> ...\"");
			}
			
			/*
			 * Measurement (statistics) Type
			 */

			String measr = Util.getSystemProperty("MeasurementType");
			if (measr == null)
			{
				measurementType = MeasurementType.real;
				return;
			}

			try
			{
				measurementType = MeasurementType.valueOf(measr);
			} catch (IllegalArgumentException i)
			{
				printUsageAndExit("Invalid policy interval value: " + measr
						+ ". Use \"java -DMeasurementType=<mType>\", where mType is one of" + MeasurementType.values());
			}
			
			contention = Util.getSystemProperty("Contention");

			if (measurementType != MeasurementType.stub)
			{
				logDistances = false;
				useStubFile = false;
				stubFile = null;
				stubOptimum = null;
			} else
			{
				/*
				 * Stub Data
				 */

				logDistances = Boolean.parseBoolean(Util.getSystemProperty("LogDistances"));
				stubFile = Util.getSystemProperty("StubFile");
				String stubOptimumProp = Util.getSystemProperty("StubOptimum");

				if (stubFile == null && stubOptimumProp != null)
				{
					useStubFile = false;
					String pointPattern = "(\\d+),(\\d+)";
					Pattern pattern = Pattern.compile(pointPattern);
					Matcher matcher = pattern.matcher(stubOptimumProp);
					if (!matcher.matches())
					{
						printUsageAndExit("Error: invalid optimum: " + stubOptimumProp + ". Use -DStubOptimum=x,y");
					}
					stubOptimum = new TuningPoint(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
					return;
				} else if (stubFile != null && stubOptimumProp == null)
				{
					useStubFile = true;
					// check for file existence.
					File stub = new File(stubFile);
					if (!(stub.exists() && stub.isFile()))
					{
						printUsageAndExit("Error: Cannot access stub file " + stub.getAbsolutePath());
					}
					// Further reading and parsing should be done by the Stub
					// classes.
				} else
				{
					printUsageAndExit(
							"Error: to use stub data you must provide either a data source file (in the appropriate format: {<x> <y> <val>\n}+ ) with "
									+ "-DStubFile=<path> or an optimum with -DStubOptimum=x,y");
				}
			}
			
		}

		/*
		 * Maximum cpus
		 */
		String maxThreadsProp = Util.getSystemProperty("MaxThreads");
		try
		{
			maxThreads = Integer.parseInt(maxThreadsProp);
		} catch (NumberFormatException n)
		{
			printUsageAndExit("Invalid maxThreads value. Use \"java -DmaxThreads=<MaxThreads> ...\"");
		}

		/*
		 * Initial Config
		 */

		String initialConfigProp = Util.getSystemProperty("InitialConfig");
		if (initialConfigProp == null)
		{
			initialConfig = null;
		} else
		{
			initialConfig = new TuningPoint();
			String[] splitMaxThreads = initialConfigProp.split(",");
			if (splitMaxThreads.length != 2)
			{
				printUsageAndExit(
						"Invalid initialConfig value. Use \"java -DInitialConfig=<max_topLevel>,<max_nested> ...\"");
			}
			try
			{
				initialConfig.first = Integer.parseInt(splitMaxThreads[0]);
				initialConfig.second = Integer.parseInt(splitMaxThreads[1]);
			} catch (NumberFormatException n)
			{
				printUsageAndExit(
						"Invalid initialConfig value. Use \"java -DInitialConfig=<max_topLevel>,<max_nested> ...\"");
			}
		}
		
		/*
		 * Contention (optional)
		 */
		
		contention = Util.getSystemProperty("Contention");
		aditionalBenchmarkInfo = Util.getSystemProperty("AditionalBenchmarkInfo");

		

	}
}