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
import hipg.Reduce;
import hipg.app.Bigraph.BiLocalNode;
import hipg.app.Bigraph.BiNode;
import hipg.app.Bigraph.Bidi;
import hipg.app.utils.Histogram;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.util.Iterator;
import java.util.Random;

import myutils.ConversionUtils;
import myutils.ObjectCache;
import myutils.storage.bigarray.BigQueue;

/**
 * The OBFR algorithm.
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public final class OBFRMP {
	/** True if debugging information about execution of TSCC should be printed. */
	private static boolean ExecutionInfo;
	/** True if the pivot randomization should be applied. */
	private static boolean RandomizedPivots;
	private static Random randomPivotSelector = new Random(System.nanoTime());
	/** List of terminal SCCs. */
	private static final Histogram SCCs = new Histogram();
	private static long LocalTrivialSCCs = 0;
	/** Reference to a null node. */
	private static final long NO_NODE = ExplicitNodeReference.NULL_NODE;
	/** Label of a non-chunked node. */
	static final long NOT_CHUNKED = 0;
	/** Level of a non-sliced node. */
	static final int NO_LEVEL = 0;
	/** Level of an eliminated node. */
	static final int ELIMINATED = -1;

	public static interface SccNode extends BiNode {
		public void explore(OBFRMPChunker chunker, long labelCh);

		public void trim(OBF slicer);

		public void bwd(OBF slicer);
	}

	public static class SccLocalNode extends BiLocalNode<SccNode> implements SccNode {
		/** Current indegree of a node, computed when the chunk is computed. */
		private int indegree = 0;
		/**
		 * The pair (label, level) identifies to which set the node belongs. Label is typically given by the chunker
		 * (for example the pivot's id). Level is set by OBF/slicer. Level is 'zeroed' during chunk computation. Label
		 * is zeroed during layer computation.
		 */
		private long label = NOT_CHUNKED;
		private int level = NO_LEVEL;

		public SccLocalNode(ExplicitGraph<SccNode> graph, int reference) {
			super(graph, reference);
		}

		/** Compute a chunk from this pivot. */
		private void explore0(OBFRMPChunker chunker, long chunkLabel) {
			exploreActualCalls++;
			exploreCalls++;
			label = chunkLabel;
			level = NO_LEVEL;
			chunker.chunkSize++;
			for (int j = 0; hasNeighbor(j); j++)
				neighbor(j).explore(chunker, chunkLabel);
		}

		public final void explore(OBFRMPChunker chunker, long chunkLabel) {
			exploreCalls++;
			// In V but not yet explored ?
			if (label == chunker.labelV && level == chunker.levelV) {
				exploreActualCalls++;
				label = chunkLabel;
				level = NO_LEVEL;
				indegree++;
				chunker.chunkSize++;
				for (int j = 0; hasNeighbor(j); j++)
					neighbor(j).explore(chunker, chunkLabel);
			} else if (label == chunkLabel && level == NO_LEVEL) {
				// In V and already explored.
				exploreActualIncIndegreeCalls++;
				indegree++;
			}
		}

		public final void trim(OBF obf) {
			trimCalls++;
			// In V?
			if (label == obf.labelV && level == NO_LEVEL) {
				trimActualCalls++;
				// Trim.
				indegree--;
				// All incoming edges are trimmed?
				if (indegree == 0) {
					// This vertex is eliminated from now on,
					// it is a trivial SCC.
					trimActualTrimCalls++;
					level = ELIMINATED;
					LocalTrivialSCCs++;
					for (int j = 0; hasNeighbor(j); j++) {
						neighbor(j).trim(obf);
					}
				} else {
					// Flag this node as reached. It may still be trimmed in this O-phase.
					obf.TrimmedOrEliminated.enqueue(this);
				}
			}
		}

		public final void bwd(OBF obf) {
			bwdCalls++;
			if (label == obf.labelV && level == NO_LEVEL) {
				bwdActualCalls++;
				indegree = 0;
				level = obf.level;
				obf.B.enqueue(this);
				for (int j = 0; hasInNeighbor(j); j++) {
					inNeighbor(j).bwd(obf);
				}
			}
		}
	}

	/**
	 * Partitions V into a set of rooted chunks. V is a subset of vertices of g. This synchronizer gets a list of
	 * vertices in V, and assumes each vertex in V is identified by (labelV, levelV).
	 */
	public static final class OBFRMPChunker extends Synchronizer {
		/** The entire graph. */
		private final ExplicitGraph<SccNode> g;
		/** The vertices in V - the set to be partitioned, or null if the entire graph is meant. */
		private final BigQueue<SccLocalNode> V;
		/** The label of elements of V. */
		private final long labelV;
		/** The level of elements of V. */
		private final int levelV;
		/** Which pivot to check next, in case V is null. */
		private int nextPivotIndex;
		/** The size of the chunk currently explored. */
		private int chunkSize;

		public OBFRMPChunker(final ExplicitGraph<SccNode> g, final BigQueue<SccLocalNode> V, final long labelV,
				final int levelV) {
			this.g = g;
			this.V = V;
			this.labelV = labelV;
			this.levelV = levelV;
		}

		@Reduce
		public long SelectPivot(long pivot) {
			// Pivot not selected so far?
			if (pivot == NO_NODE) {
				final long timeStart = System.nanoTime();
				// Look for the next pivot: select first not processed node.
				if (V == null) {
					// Start at a random node.
					if (RandomizedPivots && nextPivotIndex < g.nodes()) {
						// Look for the next pivot starting at a random node between
						// nextPivotIndex and g.nodes().
						int index = nextPivotIndex + randomPivotSelector.nextInt(g.nodes() - nextPivotIndex);
						if (index > nextPivotIndex) {
							while (index < g.nodes()) {
								final SccLocalNode node = (SccLocalNode) g.node(index++);
								if (node.label == labelV && node.level == levelV) {
									pivotSearchTime += System.nanoTime() - timeStart;
									return node.asReference();
								}
							}
						}
					}
					// Continue from the beginning.
					while (nextPivotIndex < g.nodes()) {
						final SccLocalNode node = (SccLocalNode) g.node(nextPivotIndex++);
						if (node.label == labelV && node.level == levelV) {
							pivotSearchTime += System.nanoTime() - timeStart;
							return node.asReference();
						}
					}
				} else {
					// Start at a random node.
					if (RandomizedPivots && !V.isEmpty()) {
						final int index = randomPivotSelector
								.nextInt((int) Math.min(V.size(), (long) Integer.MAX_VALUE));
						if (index > 0) {
							final long left = V.size() - index;
							Iterator<SccLocalNode> iter = V.iterator(index);
							for (long j = 0; j < left; ++j) {
								final SccLocalNode node = iter.next();
								if (node.label == labelV && node.level == levelV) {
									pivotSearchTime += System.nanoTime() - timeStart;
									return node.asReference();
								}
							}
						}
					}
					// Continue from the beginning.
					while (!V.isEmpty()) {
						final SccLocalNode node;
						try {
							node = V.dequeue();
						} catch (RuntimeException t) {
							if (V == null)
								System.err.println("Vnull");
							else
								System.err.println("Vsize = " + V.size() + " empty=" + V.isEmpty());
							throw t;
						}
						if (node.label == labelV && node.level == levelV) {
							pivotSearchTime += System.nanoTime() - timeStart;
							return node.asReference();
						}
					}
				}
				pivotSearchTime += System.nanoTime() - timeStart;
			}
			return pivot;
		}

		@BarrierAndReduce
		public long GlobalChunkSize(long size) {
			return chunkSize + size;
		}

		private void info(String msg) {
			if (ExecutionInfo && Runtime.getRank() == 0)
				System.out.println("chunker(" + name() + "): " + msg);
		}

		public void run() {
			while (true) {
				// Select global pivot as a root of the next chunk.
				final long pivotReference = SelectPivot(NO_NODE);

				if (pivotReference == NO_NODE) {
					info("done (no more pivots)");
					break;
				}

				// Explore new chunk using the pivot+1 as the label.
				final long chunkLabel = pivotReference + 1;
				final SccLocalNode pivot = (SccLocalNode) g.node(pivotReference);
				if (pivot != null) {
					pivot.explore0(this, chunkLabel);
				}

				// Compute chunk size.
				final long globalChunkSize = GlobalChunkSize(0);
				chunkSize = 0;
				if (ExecutionInfo) {
					info("found chunk F=" + setToString(chunkLabel, NO_LEVEL) + " of size " + globalChunkSize
							+ " in V=" + setToString(labelV, levelV) + " from pivot "
							+ ExplicitNodeReference.referenceToString(pivotReference));
				}

				// Spawn the slicer on the new chunk.
				spawn(new OBF(g, chunkLabel, pivotReference));
			}
		}
	}

	/**
	 * Partitions V into a set of layers: O-layer (owcty/trim), B-layer (bwd) and F-layer (fwd). V is a subset of
	 * vertices of g. This synchronizer gets a list of vertices in V, and assumes each vertex in V is identified by
	 * (labelV, levelV). The three phases are realized as two barriers: the first computes bwd of the current set, the
	 * second computes successors of the bwd and performs owcty trimming.
	 */
	public static final class OBF extends Synchronizer {
		/** The entire graph. */
		private final ExplicitGraph<SccNode> g;
		/** Pivot of the sliced chunk (null if not local). */
		private final SccLocalNode pivot;
		/** The label of the chunk to be sliced into layers. The level of V is NOT_SLICED. */
		private final long labelV;
		/** The set of vertices that are not trimmed during the O-phase. Might contain eliminated vertices. */
		private BigQueue<SccLocalNode> TrimmedOrEliminated = new BigQueue<SccLocalNode>(1024 * 32, 0, OBFRCache);
		/** The set of vertices that are reached during the B-phase. */
		private BigQueue<SccLocalNode> B = new BigQueue<SccLocalNode>(1024 * 32, 0, OBFRCache);
		/** Cache for the big queues. */
		private static final ObjectCache<SccLocalNode[]> OBFRCache = new ObjectCache<SccLocalNode[]>(1024);
		/** Current level. */
		private int level = NO_LEVEL + 1;
		/** Number of non-trimmed nodes. */
		private int nonTrimmed;

		public OBF(final ExplicitGraph<SccNode> g, final long labelV, final long globalPivotReference) {
			this.g = g;
			this.labelV = labelV;
			this.pivot = (SccLocalNode) g.node(globalPivotReference);
		}

		@BarrierAndReduce
		public long[] GlobalSizes(long[] sizes) {
			sizes[0] += B.size();
			sizes[1] += nonTrimmed;
			return sizes;
		}

		private void info(String msg) {
			if (ExecutionInfo && Runtime.getRank() == 0) {
				System.out.println("OBF(" + name() + "): " + msg);
				System.out.flush();
			}
		}

		long[] sizes;
		long globalBsize, globalNonTrimmed;

		@Override
		public void run() {
			// Trim the pivot.
			if (pivot != null) {
				// Artificially increase pivot's indegree, so that it can be reduced by one.
				pivot.indegree++;
				pivot.trim(this);
			}
			barrier();

			while (true) {
				// Run backward-reachability on all not-trimmed nodes.
				nonTrimmed = 0;
				while (!TrimmedOrEliminated.isEmpty()) {
					final SccLocalNode notTrimmedNode = TrimmedOrEliminated.dequeue();
					if (notTrimmedNode.level != ELIMINATED) {
						notTrimmedNode.bwd(this);
						nonTrimmed++;
						if (nonTrimmed % 10000 == 0) {
							Runtime.nice();
						}
					}
				}
				barrier();

				// Compute global size of B.
				sizes = GlobalSizes(new long[] { 0, 0 });
				globalBsize = sizes[0];
				globalNonTrimmed = sizes[1];
				info("|not trimmed| = " + globalNonTrimmed + ", |B|=" + globalBsize);
				if (globalBsize == 0) {
					info("done");
					return;
				} else if (globalNonTrimmed == 1) {
					info("found scc of size " + globalBsize);
					SCCs.add(globalBsize);
				} else {
					info("spawning chunker on B of size " + globalBsize);
					// Spawn chunker on (a copy of) B.
					spawn(new OBFRMPChunker(g, new BigQueue<SccLocalNode>(B), labelV, level));
				}
				level++;

				// Trim successors of B.
				while (!B.isEmpty()) {
					final SccLocalNode bwdNode = B.dequeue();
					for (int j = 0; bwdNode.hasNeighbor(j); ++j) {
						bwdNode.neighbor(j).trim(this);
					}
				}
				barrier();
			}
		}
	}

	public static final class GlobalSccs extends Synchronizer {
		long globalTrivialComponents;

		@BarrierAndReduce
		public long NumGlobalTrivialComponents(long sum) {
			return sum + LocalTrivialSCCs;
		}

		@Override
		public void run() {
			globalTrivialComponents = NumGlobalTrivialComponents(0);
		}
	}

	private static final ExplicitGraph<SccNode> readUndirectedGraph(String graphFormat, String graphFormatDetails,
			boolean monitorExecution, boolean executionInfo) {
		// Try reading graph and its transpose in one go.
		ExplicitGraph<SccNode> g = null;
		try {
			g = hipg.format.GraphIO.readUndirected(SccLocalNode.class, SccNode.class, graphFormat, graphFormatDetails,
					Config.POOLSIZE);
		} catch (GraphCreationException e) {
			try {
				// Reading graph and its transpose failed. Try reading only the graph.
				g = hipg.format.GraphIO.read(SccLocalNode.class, SccNode.class, graphFormat, graphFormatDetails,
						Config.POOLSIZE);

				// Compute graph's transpose.
				final Bidi bidi = new Bidi(g, monitorExecution, executionInfo);
				final long startTranspose = System.nanoTime();
				Runtime.getRuntime().spawnAll(bidi);
				Runtime.getRuntime().barrier();
				final long timeTranspose = System.nanoTime() - startTranspose;
				info("Graph's transpose computed in " + ConversionUtils.ns2sec(timeTranspose) + "s");

			} catch (GraphCreationException e1) {
				System.err.println("Could not read graph " + graphFormat + " " + graphFormatDetails + ": "
						+ e1.getMessage());
				System.exit(1);
			}
		}

		return g;
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			System.err.println(OBFRMP.class.getName() + " <graph spec>");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// Read options.
		boolean monitorExecution = false;
		for (int i = 2; i < args.length; i++) {
			final String arg = args[i];
			if ("-execution-info".equals(arg)) {
				ExecutionInfo = true;
			} else if ("-no-execution-info".equals(arg)) {
				ExecutionInfo = false;
			} else if ("-randomized-pivots".equals(arg)) {
				RandomizedPivots = true;
			} else if ("-no-randomized-pivots".equals(arg)) {
				RandomizedPivots = false;
			} else if ("-monitor-execution".equals(arg)) {
				monitorExecution = true;
			} else if ("-no-monitor-execution".equals(arg)) {
				monitorExecution = false;
			} else {
				throw new RuntimeException("Unrecognized argument: " + arg);
			}
		}
		info("Randomized-pivots=" + RandomizedPivots);

		// Read graph.
		info("Reading graph in format " + args[0] + " " + args[1]);
		final long startRead = System.nanoTime();
		final ExplicitGraph<SccNode> g = readUndirectedGraph(args[0], args[1], monitorExecution, ExecutionInfo);
		final long timeRead = System.nanoTime() - startRead;
		info("Graph of size " + g.getGlobalSize() + " read in " + ConversionUtils.ns2sec(timeRead) + "s");

		// Run OBFR-MP
		final long start = System.nanoTime();
		final OBFRMPChunker chunker = new OBFRMPChunker(g, null, NOT_CHUNKED, NO_LEVEL);
		Runtime.getRuntime().spawnAll(chunker);
		Runtime.getRuntime().barrier();
		final long time = System.nanoTime() - start;

		// Compute global SCCs.
		final GlobalSccs globalsccs = new GlobalSccs();
		Runtime.getRuntime().spawnAll(globalsccs);
		Runtime.getRuntime().barrier();

		// Print results.
		info(SCCs.toString("Found", "SCCs of size", null));
		if (globalsccs.globalTrivialComponents > 0)
			info("Found " + globalsccs.globalTrivialComponents + " SCCs of size 1");
		info("OBFRMP on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
		info("Statistics: " + statisticsToString());
	}

	private static final void info(final String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(msg);
			System.out.flush();
		}
	}

	private static final String setToString(final long labelV, final int levelV) {
		return "(" + (labelV == NOT_CHUNKED ? "NOT-EXPLORED" : ExplicitNodeReference.referenceToString(labelV)) + ", "
				+ (levelV == NO_LEVEL ? "NOT-SLICED" : levelV) + ")";
	}

	/** Statistics */
	private static long exploreCalls = 0, exploreActualCalls = 0, exploreActualIncIndegreeCalls = 0;
	private static long bwdCalls = 0, bwdActualCalls = 0;
	private static long trimCalls = 0, trimActualCalls = 0, trimActualTrimCalls = 0;
	private static long pivotSearchTime = 0;

	private static final String statisticsToString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("rank=" + Runtime.getRank() + ", ");
		sb.append("explore=" + exploreCalls + ">" + exploreActualCalls + ">" + exploreActualIncIndegreeCalls);
		sb.append("trim=" + trimCalls + ">" + trimActualCalls + ">" + trimActualTrimCalls);
		sb.append("bwd=" + bwdCalls + ">" + bwdActualCalls);
		sb.append("pivotSearchTime=" + ConversionUtils.ns2sec(pivotSearchTime) + "s");
		return sb.toString();
	}
}
