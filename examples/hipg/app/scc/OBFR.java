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

package hipg.app.scc;

import hipg.BarrierAndReduce;
import hipg.Config;
import hipg.Node;
import hipg.Reduce;
import hipg.app.utils.Histogram;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.util.LinkedList;

import myutils.ConversionUtils;
import myutils.ObjectCache;

/**
 * The OBFR algorithm.
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public class OBFR {

	static final boolean verbose = false;
	static final boolean vverbose = false;

	static final long NOT_EXPLORED = ExplicitNodeReference.NULL_NODE;
	static final int ELIMINATED = -2;
	static final int NOT_SLICED = -1;
	static final long NULL_NODE = ExplicitNodeReference.NULL_NODE;
	static final int CHUNK_SIZE = 1024 * 4;

	static final ObjectCache<SccLocalNode[]> cache = new ObjectCache<SccLocalNode[]>(1024);
	private static final Histogram SCCs = new Histogram();

	public static interface SccNode extends Node {
		public void explore(Explorer explorer, long labelCh);

		public void trim(Slicer slicer);

		public void bwd(Slicer slicer);
	}

	public static class SccLocalNode extends ExplicitLocalNode<SccNode> implements SccNode {

		long chunk = NOT_EXPLORED;
		int level = NOT_SLICED;
		int indegree = 0;

		public SccLocalNode(ExplicitGraph<SccNode> graph, int reference) {
			super(graph, reference);
		}

		public void eliminate() {
			level = ELIMINATED;
		}

		public boolean isEliminated() {
			return level == ELIMINATED;
		}

		private final boolean in(long labelV, int levelV) {
			return chunk == labelV && level == levelV;
		}

		private final void print(Synchronizer s, String method, long labelV, int levelV) {
			System.out.println(reference() + "@" + owner() + " " + s.getId() + "@" + s.getOwner() + " " + method + " "
					+ setToString(labelV, levelV));
		}

		private void explore0(Explorer explorer, long labelCh) {
			chunk = labelCh;
			level = NOT_SLICED;
			explorer.sizeCh++;
			for (int j = 0; hasNeighbor(j); j++)
				neighbor(j).explore(explorer, labelCh);
		}

		public void explore(Explorer explorer, long labelCh) {
			if (in(explorer.labelV, explorer.levelV)) {
				if (vverbose)
					print(explorer, "explore", explorer.labelV, explorer.levelV);
				chunk = labelCh;
				level = NOT_SLICED;
				indegree++;
				explorer.sizeCh++;
				for (int j = 0; hasNeighbor(j); j++)
					neighbor(j).explore(explorer, labelCh);
			} else if (in(labelCh, NOT_SLICED)) {
				indegree++;
			}
		}

		public void trim(Slicer slicer) {
			if (in(slicer.labelV, NOT_SLICED)) {
				if (vverbose)
					print(slicer, "trim", slicer.labelV, NOT_SLICED);
				indegree--;
				if (indegree == 0) {
					eliminate();
					slicer.trivial();
					for (int j = 0; hasNeighbor(j); j++)
						neighbor(j).trim(slicer);
				} else {
					slicer.reached(this);
				}
			}
		}

		public void bwd(Slicer slicer) {
			if (in(slicer.labelV, NOT_SLICED)) {
				if (vverbose)
					print(slicer, "bwd", slicer.labelV, NOT_SLICED);
				indegree = 0;
				level = slicer.levelS;
				slicer.bwd(this);
				for (int j = 0; hasInNeighbor(j); j++) {
					inNeighbor(j).bwd(slicer);
				}
			}
		}
	}

	public static class Explorer extends hipg.runtime.Synchronizer {

		/** Graph being explored. */
		private final ExplicitGraph<SccNode> g;
		/** The chunk to be explored (local nodes). */
		private final LinkedList<SccLocalNode> V;
		/** Label of the chunk being explored. */
		private final long labelV;
		/** Level of the chunk being explored. */
		private final int levelV;
		/** Size of the current chunk being explored. */
		private int sizeCh;

		/** Number of chunks explored. */
		// private int chunks;

		public Explorer(ExplicitGraph<SccNode> g, long labelV, int levelV, LinkedList<SccLocalNode> V) {
			this.g = g;
			this.labelV = labelV;
			this.levelV = levelV;
			this.V = V;
		}

		@Reduce
		public long ComputePivot(long pivot) {
			if (pivot == NULL_NODE) {
				while (!V.isEmpty()) {
					SccLocalNode p = V.remove(0);
					if (p.in(labelV, levelV)) {
						return p.asReference();
					}
				}
			}
			return pivot;
		}

		@BarrierAndReduce
		public int GlobalChunkSize(int s) {
			return sizeCh + s;
		}

		@SuppressWarnings("unused")
		private void print(String msg) {
			if (verbose && Runtime.getRank() == 0)
				System.out.println(Runtime.getRank() + ": EXP " + name() + ": " + msg);
		}

		public void run() {
			while (true) {
				long pivotCh = ComputePivot(NULL_NODE);
				print("pivot set to " + pivotCh);
				if (pivotCh == NULL_NODE) {
					print("done (no pivot to explore)");
					return;
				}
				long labelCh = pivotCh + 1;
				print("Exploring set " + setToString(labelV, levelV) + " taint with "
						+ setToString(labelCh, NOT_SLICED) + " from pivot "
						+ ExplicitNodeReference.referenceToString(pivotCh));

				if (ExplicitNodeReference.isLocal(pivotCh))
					((SccLocalNode) g.node(ExplicitNodeReference.getId(pivotCh))).explore0(this, labelCh);

				int globalSliceSize = GlobalChunkSize(0);

				print("Found chunk " + setToString(labelCh, NOT_SLICED) + " in " + setToString(labelV, levelV)
						+ " of size " + globalSliceSize);

				sizeCh = 0;
				// chunks++;
				spawn(new Slicer(g, labelCh, pivotCh));
			}
		}
	}

	public static class Slicer extends Synchronizer {

		/** The graph to be sliced. */
		private final ExplicitGraph<SccNode> g;
		/** The label of the chunk to be sliced. */
		private final long labelV;
		/** Level of the current slice. */
		private int levelS;
		/** Set of reached nodes. */
		private LinkedList<SccLocalNode> Reached;
		/** Set of nodes reached by bwd. */
		private LinkedList<SccLocalNode> Bwd;
		/** Reached but not eliminated nodes. */
		private int reached;

		public Slicer(ExplicitGraph<SccNode> g, long labelV, long pivot) {
			this.g = g;
			this.labelV = labelV;
			this.levelS = 1;
			this.Reached = new LinkedList<SccLocalNode>();// CHUNK_SIZE, 0,
			// cache);
			this.Bwd = new LinkedList<SccLocalNode>();// CHUNK_SIZE, 0, cache);
			if (ExplicitNodeReference.isLocal(pivot))
				reached((SccLocalNode) g.node(ExplicitNodeReference.getId(pivot)));
		}

		public void trivial() {
			SCCs.add(1);
			reached--;
		}

		public void reached(SccLocalNode node) {
			Reached.add(node);
			reached++;
		}

		public void bwd(SccLocalNode node) {
			Bwd.add(node);
		}

		@BarrierAndReduce
		public int ComputeBSize(int s) {
			return s + Bwd.size();
		}

		@BarrierAndReduce
		public int ComputeReachedSize(int s) {
			return s + reached;
		}

		private void print(String msg) {
			if (verbose && Runtime.getRank() == 0)
				System.out.println(Runtime.getRank() + ": OBF " + name() + ": " + msg);
		}

		@Override
		public void run() {
			int globalReached = 1;
			while (true) {

				// messages: bwd
				print("reached " + globalReached);
				if (globalReached == 0) {
					return;
				}
				reached = 0;
				while (!Reached.isEmpty()) {
					SccLocalNode R = Reached.remove(0);
					if (!R.isEliminated())
						R.bwd(this);
				}
				int globalBSize = ComputeBSize(0);

				// messages: trim
				print("bwd " + globalBSize + " (reached " + globalReached + ")");
				if (globalBSize == 0) {
					return;
				} else if (globalReached == 1) {
					print("found scc of size " + globalBSize);
					SCCs.add(globalBSize);
				} else {
					print("spawn on bwd " + globalBSize);
					spawn(new Explorer(g, labelV, levelS, new LinkedList<SccLocalNode>(Bwd)));
				}

				levelS++;
				while (!Bwd.isEmpty()) {
					SccLocalNode B = Bwd.remove(0);
					for (int j = 0; B.hasNeighbor(j); j++)
						B.neighbor(j).trim(this);
				}
				globalReached = ComputeReachedSize(0);
			}
		}
	}

	private static void print(String msg) {
		if (Runtime.getRank() == 0)
			System.err.println(msg);
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			System.err.println(OBFR.class.getName() + " <graph>");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// read graph
		print("Reading graph of format " + args[1] + " from path " + args[0]);
		ExplicitGraph<SccNode> g = hipg.format.GraphIO.readUndirected(SccLocalNode.class, SccNode.class, args[1],
				args[0], Config.POOLSIZE);
		print("Graph read");

		// run OBFR-MP
		long start = System.currentTimeMillis();
		java.util.LinkedList<SccLocalNode> V = new java.util.LinkedList<SccLocalNode>(
		/* CHUNK_SIZE, g.nodes() / CHUNK_SIZE, cache */);
		for (int i = 0; i < g.nodes(); i++)
			V.add((SccLocalNode) g.node(i));
		Explorer explorer = new Explorer(g, NOT_EXPLORED, NOT_SLICED, V);
		Runtime.getRuntime().spawnAll(explorer);
		Runtime.getRuntime().barrier();
		long time = System.currentTimeMillis() - start;

		// print results
		print(SCCs.toString());
		print("OBFR took " + ConversionUtils.ns2sec(time) + "s");
	}

	private static String setToString(long labelV, int levelV) {
		return "(" + (labelV == NOT_EXPLORED ? "NOT-EXPLORED" : ExplicitNodeReference.referenceToString(labelV)) + ", "
				+ (levelV == NOT_SLICED ? "NOT-SLICED" : levelV) + ")";
	}

}
