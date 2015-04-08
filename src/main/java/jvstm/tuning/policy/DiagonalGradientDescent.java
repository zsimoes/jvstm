package jvstm.tuning.policy;

import jvstm.util.Pair;

public class DiagonalGradientDescent extends LinearGradientDescent {

	static {
		deltas = new Pair[8];
		deltas[0] = new Pair<Integer, Integer>(1, 0);
		deltas[1] = new Pair<Integer, Integer>(-1, 0);
		deltas[2] = new Pair<Integer, Integer>(0, 1);
		deltas[3] = new Pair<Integer, Integer>(0, -1);
		deltas[4] = new Pair<Integer, Integer>(1, 1);
		deltas[5] = new Pair<Integer, Integer>(1, -1);
		deltas[6] = new Pair<Integer, Integer>(-1, 1);
		deltas[7] = new Pair<Integer, Integer>(-1, -1);

		roundSize = deltas.length;
	}
	

}
