package jvstm.tuning.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jvstm.Transaction;
import jvstm.tuning.AdjustableSemaphore;
import jvstm.tuning.Controller;
import jvstm.tuning.Parameters;
import jvstm.tuning.Tunable;
import jvstm.tuning.TuningPoint;
import jvstm.util.Pair;

public class RRS extends SemaphorePolicy
{

	class RRSPointProvider extends PointProvider
	{

		protected List<TuningPoint> searchSpace;
		protected List<TuningPoint> exploitSearchSpace;
		protected int exploitSearchSpaceSize; // called 'ro' (greek letter) in
												// RRS paper
		protected List<Float> thresholdSet = new ArrayList<Float>();
		protected int sampleCount = 0; // Called 'i' in RRS paper
		protected int exploitSampleCount = 0; // Called 'j' in RRS paper
		protected boolean initExplore = true;
		// best point found after n samples
		protected TuningPoint x0;
		// save f(x0):
		protected float f0;
		// performance of x0 (i.e. f(x0), where f can be TCR, throughput, etc)
		protected float yR;
		/* explore params: r, p */
		// r-percentile
		protected float paramR = 0.1F;
		// confidence
		protected float paramP = 0.9F;
		/* end explore params */

		/* exploit params: q, v, c, st */
		protected float paramV = 0.1F;
		protected float paramQ = 0.9F;
		protected float paramC = 0.6F;
		protected int paramST = 1;

		protected float fl; // called 'fc' in RRS paper
		protected TuningPoint xl;
		/* end exploit params */
		protected int computedN;
		protected int computedL;
		protected boolean exploit = false;
		protected boolean initExploit = true;

		protected TuningPoint optimum;
		protected float optimumMeasurement = -1;

		public RRSPointProvider(PointBinder pointBinder)
		{
			// no round size needed
			super(-1, pointBinder);
			searchSpace = buildSearchSpace(pointBinder.getMaximum(), new TuningPoint(6, 6), 2);
			computedN = (int) ((Math.log(1 - paramP)) / (Math.log(1 - paramR)));
			computedL = (int) ((Math.log(1 - paramQ)) / (Math.log(1 - paramV)));

			currentRound = new TuningRoundInfo();
			info.add(currentRound);

		}

		protected List<TuningPoint> buildSearchSpace(int maximum, TuningPoint tuningPoint, int radius)
		{
			List<TuningPoint> searchSpace = new ArrayList<TuningPoint>();

			int xmax = tuningPoint.first + radius, xmin = tuningPoint.first - radius;
			int ymax = tuningPoint.second + radius, ymin = tuningPoint.second - radius;

			if (ymin < 1)
			{
				ymin = 1;
			}
			if (xmin < 1)
			{
				xmin = 1;
			}
			for (int x = xmin; x <= xmax; x++)
			{
				for (int y = ymin; y <= ymax; y++)
				{
					if (x * y > maximum)
					{
						break;
					}
					TuningPoint newPoint = new TuningPoint(x, y);
					searchSpace.add(newPoint);
				}
			}

			return searchSpace;
		}

		protected List<TuningPoint> buildSearchSpace(int maximum)
		{
			List<TuningPoint> searchSpace = new ArrayList<TuningPoint>();
			for (int x = 1; x <= maximum; x++)
			{
				for (int y = 1; y <= maximum; y++)
				{
					if (x * y > maximum)
					{
						break;
					}
					TuningPoint tp = new TuningPoint(x, y);
					searchSpace.add(tp);
				}
			}

			return searchSpace;
		}

		@Override
		protected TuningPoint getPointImpl()
		{
			Random rnd = new Random();
			int pos = rnd.nextInt(searchSpace.size());
			TuningPoint result = searchSpace.get(pos);
			return result;
		}

		protected TuningPoint getExploitPoint()
		{
			Random rnd = new Random();
			int pos = rnd.nextInt(exploitSearchSpace.size());
			TuningPoint result = exploitSearchSpace.get(pos);
			return result;
		}

		/*
		 * We have to override this because we need the measurements
		 */
		@Override
		public TuningPoint getPoint(float previousThroughput, float previousTCR)
		{
			if (currentRecord != null)
			{
				saveCurrentPoint(previousThroughput, previousTCR);
			}

			if (initExplore)
			{
				// enough initial samples?
				if (sampleCount == computedN)
				{
					initExplore = false;
					sampleCount = 0;
					yR = currentRound.getBest().throughput;
					thresholdSet.add(yR);
					x0 = currentRound.getBest().point;
					f0 = currentRound.getBest().throughput;
					currentRound = new TuningRoundInfo();
					info.add(currentRound);
				}
				TuningPoint initialSample = getPointImpl();
				sampleCount++;
				currentRecord = new TuningRecord(initialSample, previousThroughput, previousTCR);
				currentRound.add(currentRecord);
				//System.err.println("InitExplore" + sampleCount + ": " + initialSample);
				return initialSample;
			}

			if (exploit)
			{
				//System.err.println("Exploit!");
				if (initExploit)
				{
					initExploit = false;
					exploitSearchSpaceSize = (int) Math.ceil((paramR * pointBinder.getMaximum()));
					exploitSearchSpace = buildSearchSpace(pointBinder.getMaximum(), x0, exploitSearchSpaceSize);
					exploitSampleCount = 0;
					// Dup exploration data for exploitation phase:
					fl = f0;
					xl = new TuningPoint(x0.first, x0.second);
				}

				// Exploit search space is bigger than threshold paramST, so
				// keep exploiting
				if (exploitSearchSpaceSize > paramST)
				{
					if (previousThroughput > fl)
					{
						// Found a better point, re-align exploit search space
						//System.err.println("Re-align!");
						exploitSearchSpace = buildSearchSpace(pointBinder.getMaximum(), xl, exploitSearchSpaceSize);
						fl = previousThroughput;
						TuningPoint previousPoint = currentRecord.getPoint();
						xl = new TuningPoint(previousPoint.first, previousPoint.second);
						exploitSampleCount = 0;
						currentRound = new TuningRoundInfo();
						info.add(currentRound);
					}

					if (exploitSampleCount == computedL)
					{
						// Fail to find a better point, shrink the sample space
						//System.err.println("Shrink!");
						exploitSampleCount = 0;
						exploitSearchSpaceSize *= paramC;
						exploitSearchSpace = buildSearchSpace(pointBinder.getMaximum(), xl, exploitSearchSpaceSize);
						currentRound = new TuningRoundInfo();
						info.add(currentRound);
					}
					exploitSampleCount++;
					TuningPoint point = getExploitPoint();
					currentRecord = new TuningRecord(point, -1, -1);
					currentRound.add(currentRecord);
					//System.err.println("Exploit" + sampleCount + ": " + point);
					return point;

				} else
				{
					// We've reached the threshold, so go back to exploring.
					exploit = false;
					if (previousThroughput > optimumMeasurement)
					{
						optimum = xl;
						optimumMeasurement = fl;
					}
				}
			} // end if exploit

			TuningPoint point = getPointImpl();
			sampleCount++;
			// have we found a candidate?
			if (previousThroughput > yR)
			{
				initExploit = true;
				exploit = true;
				x0 = currentRecord.getPoint();
				f0 = currentRecord.getThroughput();
			}

			if (sampleCount == computedN)
			{
				// Update the exploitation threshold every n samples in the
				// parameter space
				float bestResult = currentRound.getBest().throughput;
				thresholdSet.add(bestResult);
				yR = average(thresholdSet);
				sampleCount = 0;
				currentRound = new TuningRoundInfo();
			}
			currentRecord = new TuningRecord(point, -1, -1);
			currentRound.add(currentRecord);

			//System.err.println("Explore " + sampleCount + ": " + point);
			return point;
		}

		private float average(List<Float> l)
		{
			float f = 0;
			for (Float sample : l)
			{
				f += sample;
			}
			return f / l.size();
		}

		/*
		 * This method was used in case retries were needed when sampling the
		 * search space. With RRS this doesn't happen.
		 * @see jvstm.tuning.policy.PointProvider#getPoint()
		 */
		@Override
		protected Pair<Integer, TuningPoint> getPoint()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public TuningPoint initRound()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void initRound(TuningPoint point)
		{
			// nothing
		}

		@Override
		public TuningPoint getInitialPoint()
		{
			return getPointImpl();
		}

		@Override
		public boolean isRoundEnd()
		{
			return false;
		}

	}

	public RRS(Controller controller)
	{
		super(controller);
		init();
	}

	protected void init()
	{
		this.pointProvider = createPointProvider();
		TuningPoint initialConfig = pointProvider.getInitialPoint();

		topLevelSemaphore = new AdjustableSemaphore(initialConfig.first);
		int nested = initialConfig.first * initialConfig.second;
		nestedSemaphore = new AdjustableSemaphore(nested);
		currentPoint = new TuningPoint(initialConfig.first, nested);
	}

	@Override
	public void run(boolean mergePerThreadStatistics)
	{
		if (mergePerThreadStatistics)
		{
			mergeStatistics();
		}

		float throughput = getThroughput(true), tcr = getTCR(true);
		if (!pointProvider.isFirstRound())
		{
			pointProvider.saveCurrentPoint(throughput, tcr);
		}

		TuningPoint point = null;

		if (pointProvider.isRoundEnd())
		{
			point = pointProvider.initRound();
		} else
		{
			point = pointProvider.getPoint(throughput, tcr);
		}

		setCurrentPoint(point);

	}

	@Override
	public void clearInternalData()
	{
		// nothing
	}

	@Override
	protected PointProvider createPointProvider()
	{
		return new RRSPointProvider(pointBinder);
	}

}
