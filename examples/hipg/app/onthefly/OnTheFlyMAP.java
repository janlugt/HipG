/**
 * Copyright (c) 2010, 2011 Vrije Universiteit Amsterdam.
 * Written by Elzbieta Krepska, e.krepska@vu.nl.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

/**
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package hipg.app.onthefly;

import hipg.BarrierAndReduce;
import hipg.Config;
import hipg.LocalNode;
import hipg.app.onthefly.SpinJadi.SpinjaAlgorithm;
import hipg.app.onthefly.SpinJadi.SpinjaLocalNode;
import hipg.app.onthefly.SpinJadi.SpinjaNode;
import hipg.graph.OnTheFlyGraph;
import hipg.runtime.Runtime;
import myutils.storage.PairIterator;
import myutils.tuple.pair.FastIntPair;

public class OnTheFlyMAP {

	private static final long BOTTOM = -2;
	private static final long NIL = -1;

	private static int accepting = 0;

	public static interface MAPNode extends SpinjaNode {
		public void map(final MAP algo, final int depth, final long propag);
	}

	public static final class MAPLocalNode extends SpinjaLocalNode<MAPNode> implements MAPNode {

		/** Number of times the node has been visited. */
		private int visits = 0;
		/** True if state was initially accepting and was not never shrinked. */
		private boolean accepting;
		/** True a state is in the current shrink set. */
		// private boolean shrink = false;
		/** Current minimal predecessor (BOTTOM=not visited; NIL=visited w/ NIL) */
		private long map = BOTTOM;
		private long maxpropag = BOTTOM;
		/** Unique id of this node. */
		private final int uniqId = nextUniqId++;
		private static int nextUniqId = 0;

		public MAPLocalNode(final OnTheFlyGraph<MAPNode> graph, final byte[] state) {
			super(graph, state);
			this.accepting = SpinJadi.isAcceptState(state);
			if (accepting) {
				OnTheFlyMAP.accepting++;
			}
			if (!SpinJadi.isStoredState(state)) {
				SpinJadi.realAtomic++;
			}
		}

		private final long uniqId() {
			return FastIntPair.createPair(Runtime.getRank(), uniqId);
		}

		public void map(final MAP algo, final int depth, long propag) {
			if (algo.stopped)
				return;
			if (depth > algo.maxDepth)
				algo.maxDepth = depth;
			if (visits == 0 && accepting)
				algo.accepting++;

			visits++;

			if (uniqId() == propag) {
				// cycle found
				algo.acceptingCycle = true;
				algo.error("acceptance cycle detected (at depth " + depth + ", reported by worker " + owner() + ")");

			} else if (propag > map) {
				// propagate new map value
				if (algo.verbose > 1) {
					System.err.println(uniqId + " " + name() + " map " + map + ", propag " + propag + ", visits "
							+ visits + ", acc=" + accepting);
				}
				map = propag;

				// if accepting, shrink or de-shrink
				if (accepting) {
					if (map < uniqId()) {
						propag = uniqId();
						// shrink = true;
						// } else {
						// shrink = false;
					}
				}

				// propagate new value
				if (propag > maxpropag) {
					maxpropag = propag;

					SpinJadi.bytesToModel(state);
					SpinJadi.stack.push(this);
					if (algo.maxSearchDepth > 0 && depth > algo.maxSearchDepth && hasNeighbor(0)) {
						algo.exceededDepth("exceeded maximum depth (at depth " + depth + ", reported by worker "
								+ Runtime.getRank() + ")");
						SpinJadi.stack.pop();
						return;
					}
					for (int i = 0; hasNeighbor(i) && !algo.stopped && maxpropag == propag; i++) {
						if (algo.verbose > 2) {
							SpinJadi.bytesToModel(state);
							System.err.println("-- " + id() + " -> " + neighborId(i) + "@" + neighborOwner(i));
						}

						neighbor(i).map(algo, depth + 1, propag);

						if (SpinJadi.exceptionWhenGettingSuccessor != null) {
							algo.error(SpinJadi.exceptionWhenGettingSuccessor);
						}
					}
					SpinJadi.stack.pop();
				}
			}
		}

		public boolean shouldStore() {
			return true;
		}
	}

	public static final class MAP extends SpinjaAlgorithm<MAPNode> {

		/** Accepting nodes left. */
		private int accepting = 0;
		/** Global number of accepting nodes. */
		private long globalAccepting = 0L;
		/** True when accepting cycle found locally. */
		private boolean acceptingCycle = false;
		/** True when accepting cycle found globally. */
		private boolean globalAcceptingCycle = false;

		public MAP(final OnTheFlyGraph<MAPNode> g, final byte[] pivot, final boolean ignoreErrors,
				final int errorsToStop, final int maxSearchDepth, final boolean exceedDepthError, final int verbose) {
			super(g, pivot, ignoreErrors, errorsToStop, maxSearchDepth, exceedDepthError, verbose);
		}

		@BarrierAndReduce
		public long GlobalAcceptingLeft(long s) {
			return s + this.accepting;
		}

		public long GlobalAccepting(long s) {
			return s + OnTheFlyMAP.accepting;
		}

		@BarrierAndReduce
		public boolean GlobalAcceptingCycle(boolean b) {
			return b | acceptingCycle;
		}

		private void print(String msg, boolean force) {
			if (verbose > 0 && (force || Runtime.getRank() == 0)) {
				System.err.println(msg);
			}
		}

		public void run() {
			int iter = 0;
			do {
				iter++;
				print("Start iteration " + iter, false);
				/* Run MAP. */
				if (pivot != null) {
					((MAPLocalNode) g.node(pivot)).map(this, 0, NIL);
				}
				/* Compute global answer. */
				globalAcceptingCycle = GlobalAcceptingCycle(false);
				print("Global accepting cycle found ? " + globalAcceptingCycle, false);
				if (globalAcceptingCycle)
					break;
				accepting = 0;

				// if Entry is a generic type, the location of variables in
				// synchronizer rewriter does not work!!! Bug in BCEL....!
				PairIterator<byte[], LocalNode<MAPNode>> iterator = g.map().stateNodeIterator();
				while (iterator.hasNext()) {
					iterator.next();
					final MAPLocalNode node = (MAPLocalNode) iterator.value();
					node.map = BOTTOM;
					node.maxpropag = BOTTOM;
					// if (node.shrink)
					// node.accepting = false
					// else
					// accepting++;
					if (node.accepting) {
						if (node.map < node.uniqId()) {
							node.accepting = false;
						} else {
							accepting++;
						}
					}
				}
				/* Compute global number of accepting states. */
				globalAccepting = GlobalAcceptingLeft(0);
				print("Iteration " + iter + ", " + globalAccepting + " accepting nodes left", false);
			} while (globalAccepting > 0);
			barrier();
			SpinJadi.EndTime = System.currentTimeMillis();
			globalStored = GlobalStoredNodes(new long[Config.POOLSIZE]);
			globalNotStored = GlobalNotStoredNodes(0);
			globalMatched = GlobalMatched(0);
			globalMaxStateLen = GlobalMaxStateLen(-1);
			globalMaxDepth = GlobalMaxDepth(-1);
			globalMemory = GlobalMemory(0);
			globalHashConflicts = GlobalHashConflicts(0);
			globalHashtableLen = GlobalHashtableLength(0);
			globalRealAtomic = GlobalRealAtomics(0);
			globalAccepting = GlobalAccepting(0);
		}

		public void printStats() {
			System.out.printf("%8d accepting states found\n", globalAccepting);
		}
	}

}
