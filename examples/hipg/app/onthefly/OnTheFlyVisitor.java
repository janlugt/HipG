/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam
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

import hipg.app.onthefly.SpinJadi.SpinjaAlgorithm;
import hipg.app.onthefly.SpinJadi.SpinjaLocalNode;
import hipg.app.onthefly.SpinJadi.SpinjaNode;
import hipg.graph.OnTheFlyGraph;
import hipg.runtime.Runtime;

public class OnTheFlyVisitor {

	public static interface ReachedNode extends SpinjaNode {
		public void visit(Visitor algo, int depth);
	}

	/** Reachability in the node. */
	public static class ReachedLocalNode extends SpinjaLocalNode<ReachedNode> implements ReachedNode {

		private boolean visited = false;

		public ReachedLocalNode(OnTheFlyGraph<ReachedNode> graph, byte[] state) {
			super(graph, state);
		}

		public void visit(Visitor algo, int depth) {
			if (algo.stopped)
				return;
			if (depth > algo.maxDepth)
				algo.maxDepth = depth;
			if (!visited) {
				visited = true;
				SpinJadi.bytesToModel(state);
				SpinJadi.stack.push(this);
				// check error
				if (!algo.ignoreErrors && !SpinJadi.isEndState(state) && !hasNeighbor(0)) {
					algo.error("invalid end state (at depth " + depth + ", reported by worker " + owner() + ")");
				}
				if (algo.verbose > 1) {
					System.err.println(name() + " visit at depth " + depth);
				}
				// visit successors
				if (algo.maxSearchDepth > 0 && depth > algo.maxSearchDepth && hasNeighbor(0)) {
					algo.exceededDepth("exceeded maximum depth (at depth " + depth + ", reported by worker "
							+ Runtime.getRank() + ")");
					SpinJadi.stack.pop();
					return;
				}
				for (int i = 0; hasNeighbor(i) && !algo.stopped; i++) {
					if (algo.verbose > 2) {
						SpinJadi.bytesToModel(state);
						System.err.println("-- " + id() + " -> " + neighborId(i) + "@" + neighborOwner(i));
					}
					neighbor(i).visit(algo, depth + 1);
					if (SpinJadi.exceptionWhenGettingSuccessor != null) {
						algo.error(SpinJadi.exceptionWhenGettingSuccessor);
					}
				}
				SpinJadi.stack.pop();
			}
		}
	}

	public static class Visitor extends SpinjaAlgorithm<ReachedNode> {

		public Visitor(final OnTheFlyGraph<ReachedNode> g, final byte[] pivot, final boolean ignoreErrors,
				final int errorsToStop, final int maxSearchDepth, final boolean exceedDepthError, final int verbose) {
			super(g, pivot, ignoreErrors, errorsToStop, maxSearchDepth, exceedDepthError, verbose);
		}

		public void run() {
			if (pivot != null && Runtime.getRuntime().isMe(g.hash().owner(pivot))) {
				((ReachedLocalNode) g.node(pivot)).visit(this, 0);
			}
			barrier();
			SpinJadi.EndTime = System.currentTimeMillis();
			globalStored = GlobalStoredNodes(new long[Runtime.getPoolSize()]);
			globalNotStored = GlobalNotStoredNodes(0);
			globalMatched = GlobalMatched(0);
			globalMaxStateLen = GlobalMaxStateLen(-1);
			globalMaxDepth = GlobalMaxDepth(-1);
			globalMemory = GlobalMemory(0);
			globalHashConflicts = GlobalHashConflicts(0);
			globalHashtableLen = GlobalHashtableLength(0);
			globalRealAtomic = GlobalRealAtomics(0);
		}
	}

}
