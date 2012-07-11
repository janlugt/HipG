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

package hipg.app.scc;

import hipg.Config;
import hipg.Reduce;
import hipg.app.Bigraph.BiLocalNode;
import hipg.app.Bigraph.BiNode;
import hipg.app.Bigraph.Bidi;
import hipg.app.scc.PNSSCompElements.PNSSCompElementsLister;
import hipg.app.utils.SccStructure;
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
import myutils.storage.set.LongHashSet;
import myutils.system.MonitorThread;
import myutils.tuple.pair.FastIntPair;

/**
 * Finds terminal strongly connected components in a graph (data-parallel version).
 * 
 * @author Ela Krepska, e.krepska@vu.nl
 */
public final class TSCCdc {
	/** True if the "owcty" optimization should be applied. */
	private static boolean ApplyOwcty = false;
	/** True if debugging information about execution of TSCC should be printed. */
	private static boolean ExecutionInfo = false;
	/** True if the pivot randomization should be applied. */
	private static boolean RandomizedPivots = true;
	private static Random randomPivotSelector = new Random(System.nanoTime());
	/** List of terminal SCCs. */
	private static final SccStructure SCCs = new SccStructure();
	/** List of trivial terminal SCCs (in case OWCTY optimization is applied). */
	private static final LongHashSet TrivialSCCs = new LongHashSet();

	/** TSCC node interface in the TSCC algorithm. */
	public static interface TSCCNode extends BiNode {
		public void fwd(final TSCCSynch tscc);

		public void cwd(final TSCCSynch tscc);

		public void bwd(final TSCCSynch tscc);

		public void owcty(final TSCCSynch tscc);

		public void deleteInEdge(final TSCCSynch tscc);
	}

	/** TSCC local node implementation. */
	public static class TSCCLocalNode extends BiLocalNode<TSCCNode> implements TSCCNode {
		/** True if and only if this node has been processed. */
		private boolean processed = false;
		/** Id of the component that this node belongs to. */
		private long sccId = 0;
		/** The number of incoming edges deleted. */
		private int deletedInEdges = 0;

		/**
		 * Helper flag. Zeroed during the forward reachability. During backward reachability, true value means that the
		 * backward reachability visited. During owcty, the true value means that the owcty has not visited.
		 */
		private boolean tmpVisited = false;

		public TSCCLocalNode(final ExplicitGraph<TSCCNode> graph, final int reference) {
			super(graph, reference);
		}

		/** Computes the set F within V of nodes forward reachable from the pivot. */
		public final void fwd(final TSCCSynch tscc) {
			fwdCalls++;
			// In V but not yet in F?
			if (!processed && this.sccId == tscc.Vid) {
				fwdActualCalls++;
				// "Unvisit" the node for sub-reachabilities.
				this.tmpVisited = false;
				// Store this node in F.
				this.sccId = tscc.id;
				tscc.F.enqueue(this);
				// Call fwd() on all neighbors.
				for (int j = 0; hasNeighbor(j); ++j) {
					neighbor(j).fwd(tscc);
				}
			}
		}

		/** Computes the set C of nodes backward reachable from the pivot, within F. */
		public final void cwd(final TSCCSynch tscc) {
			cwdCalls++;
			// In F and not yet processed?
			if (this.sccId == tscc.id && !processed) {
				cwdActualCalls++;
				// This node is processed, it belongs to the SCC of the pivot, terminal or not.
				processed = true;
				// Store this node in C.
				tscc.Csize++;
				// Call cwd() on all incoming neighbors.
				for (int j = 0; hasInNeighbor(j); ++j) {
					inNeighbor(j).cwd(tscc);
				}
			}
		}

		/** Computes the set B of nodes backward reachable from the pivot. */
		public final void bwd(final TSCCSynch tscc) {
			bwdCalls++;
			boolean visiting = false;
			// In F? (possible that cwd processed this node already).
			if (this.sccId == tscc.id) {
				// Not yet visited by this bwd?
				if (!tmpVisited) {
					bwdActualFCalls++;
					// This node is in no terminal SCC.
					visiting = true;
					tmpVisited = true;
				}
			} else {
				// In V\F?
				if (!processed && this.sccId == tscc.Vid) {
					bwdActualVmFCalls++;
					visiting = true;
					processed = true;
					// This node is in no terminal SCC.
					this.sccId = 0;
					// Add this node to B\F.
					tscc.BmFsize++;
					if (ApplyOwcty) {
						// "Remove" all edges of this node.
						for (int j = 0; hasNeighbor(j); ++j) {
							neighbor(j).deleteInEdge(tscc);
						}
					}
				}
			}
			if (visiting) {
				// Call bwd() on all incoming neighbors.
				for (int j = 0; hasInNeighbor(j); ++j) {
					inNeighbor(j).bwd(tscc);
				}
			}
		}

		/** Deletes an incoming edge. */
		public final void deleteInEdge(TSCCSynch tscc) {
			deleteInEdgeCalls++;
			deletedInEdges++;
		}

		/** Initiates owcty */
		public final void owcty0(TSCCSynch tscc) {
			owctyCalls++;
			owctyActual0Calls++;
			tmpVisited = false;
			for (int j = 0; hasNeighbor(j); ++j) {
				neighbor(j).owcty(tscc);
			}
		}

		public final void owcty(TSCCSynch tscc) {
			owctyCalls++;
			// In F?
			if (this.sccId == tscc.id) {
				// In C?
				if (processed) {
					// First time owcty visits?
					if (tmpVisited) {
						owctyActualCCalls++;
						tmpVisited = false;
						for (int j = 0; hasNeighbor(j); ++j) {
							neighbor(j).owcty(tscc);
						}
					}
				} else {
					// In C\F.
					deletedInEdges++;
					// No edges left?
					if (deletedInEdges == indegree()) {
						owctyActualCmFCalls++;
						// Use the negated unique name of this node for scc id.
						sccId = -asReference();
						processed = true;
						// Add this node to Eliminated.
						tscc.Eliminated++;
						// Is this SCC terminal?
						if (outdegree() == 0) {
							// Report trivial terminal SCC.
							if (ExecutionInfo) {
								info("Found trivial terminal SCC of id " + sccId);
							}
							TrivialSCCs.insert(sccId);
						} else {
							for (int j = 0; hasNeighbor(j); ++j) {
								neighbor(j).owcty(tscc);
							}
						}
					}
				}
			}
		}
	}

	/** TSCC synchronizer. */
	public static final class TSCCSynch extends Synchronizer {
		/** The graph to decompose. */
		private final ExplicitGraph<TSCCNode> g;
		/** The subset V of the set of vertices to decompose. */
		private long Vid;
		/** The size of the set of vertices to process. */
		private long globalVsize;
		private BigQueue<TSCCLocalNode> V;
		/** Current iteration (used as SCC id). */
		private long id = 1;
		/** Index of the next pivot to consider. */
		private int nextPivotIndex = 0;
		/** Current size of the sets F, C, BmF, P. */
		private long Csize, Fsize, BmFsize, Eliminated;
		/** The queue to store elements of F. */
		private BigQueue<TSCCLocalNode> F = new BigQueue<TSCCLocalNode>(1024 * 32, 0, Fcache);
		/** Cache for the queue (helpful if the queue grows and shrinks many times). */
		private static final ObjectCache<TSCCLocalNode[]> Fcache = new ObjectCache<TSCCLocalNode[]>(1024);
		/** The total number of trivial (size 1) components. */
		private long globalTrivialComponents = -1;

		public TSCCSynch(ExplicitGraph<TSCCNode> g, final long Vid, final long Vsize, final BigQueue<TSCCLocalNode> V,
				final int nextPivotIndex) {
			this.g = g;
			this.Vid = Vid;
			this.V = V;
			this.globalVsize = Vsize;
			this.nextPivotIndex = nextPivotIndex;
		}

		/** Selects a global pivot. */
		@Reduce
		public final long SelectedPivot(long pivot) {
			// Pivot not selected so far?
			if (pivot == NULL) {
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
								final TSCCLocalNode node = (TSCCLocalNode) g.node(index++);
								if (!node.processed && node.sccId == Vid) {
									pivotSearchTime += System.nanoTime() - timeStart;
									return node.asReference();
								}
							}
						}
					}
					// Continue from the beginning.
					while (nextPivotIndex < g.nodes()) {
						final TSCCLocalNode node = (TSCCLocalNode) g.node(nextPivotIndex++);
						if (!node.processed && node.sccId == Vid) {
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
							Iterator<TSCCLocalNode> iter = V.iterator(index);
							for (long j = 0; j < left; ++j) {
								final TSCCLocalNode node = iter.next();
								if (!node.processed && node.sccId == Vid) {
									pivotSearchTime += System.nanoTime() - timeStart;
									return node.asReference();
								}
							}
						}
					}
					// Continue from the beginning.
					while (!V.isEmpty()) {
						final TSCCLocalNode node = V.dequeue();
						if (!node.processed && node.sccId == Vid) {
							pivotSearchTime += System.nanoTime() - timeStart;
							return node.asReference();
						}
					}
				}
				pivotSearchTime += System.nanoTime() - timeStart;
			}
			return pivot;
		}

		/** Computes sizes of all sets (F, C, B\F, P - in that order) in one reduce operation. */
		@Reduce
		public final long[] GlobalSizes(long[] sizes) {
			sizes[0] += Fsize;
			sizes[1] += Csize;
			sizes[2] += BmFsize;
			sizes[3] += Eliminated;
			return sizes;
		}

		/** Computes the total number of discovered trivial (size 1) SCCs. */
		@Reduce
		public long NumGlobalTrivialComponents(long sum) {
			return sum + TrivialSCCs.size();
		}

		private long startTime;
		private BigQueue<TSCCLocalNode> newF;

		/** Performs the TSCC algorithm. */
		public void run() {
			// We will uniquely identify SCCs by pairs of unique synchronizer's id and a consecutive "iteration" count.
			id = FastIntPair.createPair(getId(), 1);
			while (true) {
				/* Select global pivot in V (V = set of not processed nodes). */
				final long pivotReference = SelectedPivot(NULL);
				if (pivotReference == NULL) {
					break;
				}
				if (ExecutionInfo) {
					info(FastIntPair.toString(Vid) + " Pivot found: "
							+ ExplicitNodeReference.referenceToString(pivotReference));
				}
				Csize = Eliminated = BmFsize = 0;

				/* Compute the set F, subset of V, of nodes forward-reachable from the pivot. */
				final TSCCLocalNode pivot = (TSCCLocalNode) g.node(pivotReference);
				startTime = System.nanoTime();
				if (pivot != null) {
					pivot.fwd(this);
				}
				barrier();
				if (ExecutionInfo) {
					info(FastIntPair.toString(Vid) + " F took " + ConversionUtils.ns2sec(System.nanoTime() - startTime));
				}
				Fsize = F.size();

				/* Compute the set C, subset of F, of nodes backward-reachable from the pivot. This is an SCC of the
				 * pivot. */
				startTime = System.nanoTime();
				if (pivot != null) {
					pivot.cwd(this);
				}
				/* In parallel, compute the set B, subset of V, of nodes backward reachable from F. */
				final long fsize = F.size();
				for (long k = 0; k < fsize; ++k) {
					TSCCLocalNode node = F.dequeue();
					F.enqueue(node);
					node.bwd(this);
					if (k % 10000 == 9999) {
						Runtime.nice();
					}
				}
				barrier();
				if (ExecutionInfo) {
					info(FastIntPair.toString(Vid) + " C&B took "
							+ ConversionUtils.ns2sec(System.nanoTime() - startTime) + "s");
				}

				/* Compute the set P, within F, of nodes without predecessors (starting from nodes 1-hop forward from C. */
				if (ApplyOwcty) {
					startTime = System.nanoTime();
					if (pivot != null) {
						pivot.owcty0(this);
					}
					barrier();
					if (ExecutionInfo) {
						info(FastIntPair.toString(Vid) + " OWCTY took "
								+ ConversionUtils.ns2sec(System.nanoTime() - startTime) + "s");
					}
				}

				/* Set next component id. */
				id++;

				/* Compute global sizes of F, C, B\F, P. */
				final long[] globalSizes = GlobalSizes(new long[] { 0, 0, 0, 0 });
				final long globalFsize = globalSizes[0];
				final long globalCsize = globalSizes[1];
				final long globalBmFsize = globalSizes[2];
				final long globalEliminated = globalSizes[3];

				if (ExecutionInfo) {
					info(FastIntPair.toString(Vid) + " Iteration " + FastIntPair.toString(id - 1) + ": !processed="
							+ (globalVsize - globalCsize - globalBmFsize) + "; |F|=" + globalFsize + ", |C|="
							+ globalCsize + ", |Eliminated|=" + globalEliminated + ", |B\\F|=" + globalBmFsize
							+ ", statistics: " + statisticsToString());
				}
				globalVsize -= globalFsize + globalBmFsize;

				/* If (F == C) report a *terminal* SCC. Enough to check size, as C is subset of F. */
				if (globalFsize == globalCsize) {
					SCCs.addComponent(id - 1, globalFsize);
					if (ExecutionInfo) {
						info(FastIntPair.toString(Vid) + " Found SCC of size " + globalFsize + ", id "
								+ FastIntPair.toString(id - 1));
					}
					F.clear();
				} else {
					if (globalVsize == 0) {
						Vid = id - 1;
						globalVsize = globalFsize - globalCsize;
						newF = V != null ? V : new BigQueue<TSCCLocalNode>(1024 * 32, 0, Fcache);
						V = F;
						F = newF;
						barrier();
						F.clear();
					} else {
						spawn(new TSCCSynch(g, id - 1, globalFsize - globalCsize, F, nextPivotIndex));
						F = new BigQueue<TSCCLocalNode>(1024 * 32, 0, Fcache);
					}
				}
			}
			globalTrivialComponents = NumGlobalTrivialComponents(0);
		}
	}

	private static final ExplicitGraph<TSCCNode> readUndirectedGraph(String graphFormat, String graphFormatDetails,
			boolean monitorExecution, boolean executionInfo) {
		// Try reading graph and its transpose in one go.
		ExplicitGraph<TSCCNode> g = null;
		try {
			g = hipg.format.GraphIO.readUndirected(TSCCLocalNode.class, TSCCNode.class, graphFormat,
					graphFormatDetails, Config.POOLSIZE);
		} catch (GraphCreationException e) {
			try {
				// Reading graph and its transpose failed. Try reading only the graph.
				g = hipg.format.GraphIO.read(TSCCLocalNode.class, TSCCNode.class, graphFormat, graphFormatDetails,
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
			// Print usage information and exit.
			System.err.println(TSCCdc.class.getName() + " <graph spec> [-no-owcty] [-execution-info]"
					+ " [-PNSS-elements] [-PNSS-file <file>]");
			System.err.println("where PNSS = Petri Net state space and ");
			System.err.println("a graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// Read options.
		boolean pnssElements = false;
		boolean monitorExecution = false;
		String pnssElementsFileNameBase = "tscc-comp";
		for (int i = 2; i < args.length; i++) {
			final String arg = args[i];
			if ("-PNSS-elements".equals(arg)) {
				pnssElements = true;
			} else if ("-PNSS-file".equals(arg) && i + 1 < args.length) {
				pnssElementsFileNameBase = args[++i];
			} else if ("-no-owcty".equals(arg)) {
				ApplyOwcty = false;
			} else if ("-owcty".equals(arg)) {
				ApplyOwcty = true;
			} else if ("-execution-info".equals(arg)) {
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
		info("OWCTY=" + ApplyOwcty);
		info("Randomized-pivots=" + RandomizedPivots);
		info("Elements=" + pnssElements);

		// Read graph.
		info("Reading graph in format " + args[0] + " " + args[1]);
		final long startRead = System.nanoTime();
		final ExplicitGraph<TSCCNode> g = readUndirectedGraph(args[0], args[1], monitorExecution, ExecutionInfo);
		final long timeRead = System.nanoTime() - startRead;
		info("Graph of size " + g.getGlobalSize() + " read in " + ConversionUtils.ns2sec(timeRead) + "s");

		// Initialize TSCC monitor (every 30s).
		final MonitorThread monitor = monitorExecution ? new MonitorThread(30000, System.err, "TSCC") {
			public void print(StringBuilder sb) {
				sb.append(".. TSCC: " + statisticsToString());
			}
		}.startMonitor() : null;

		// Run TSCCdc.
		final TSCCSynch tscc = new TSCCSynch(g, 0, g.getGlobalSize(), null, 0);
		final long startTscc = System.nanoTime();
		Runtime.getRuntime().spawnAll(tscc);
		Runtime.getRuntime().barrier();
		final long timeTSCC = System.nanoTime() - startTscc;
		if (monitor != null) {
			monitor.stopMonitor();
		}

		// Compute and print to file elements of TSCCs.
		if (pnssElements) {
			info("Computing elements of terminal components of a Petri net state space");
			final PNSSCompElementsLister elements = new PNSSCompElementsLister(g, SCCs.getTerminalComponents(),
					pnssElementsFileNameBase);
			final long startElements = System.nanoTime();
			Runtime.getRuntime().spawnAll(elements);
			Runtime.getRuntime().barrier();
			final long timeElements = System.nanoTime() - startElements;
			info("Elements of TSCCs computed in " + ConversionUtils.ns2sec(timeElements) + "s");
		}

		// Print results of TSCCdc.
		info(SCCs.toString(tscc.globalTrivialComponents));
		info("TSCCdc on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(timeTSCC) + "s");
		info("Statistics: " + statisticsToString());
	}

	private static final void info(String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(msg);
			System.out.flush();
		}
	}

	/** Statistics */
	private static long fwdCalls = 0, fwdActualCalls = 0;
	private static long cwdCalls = 0, cwdActualCalls = 0;
	private static long bwdCalls = 0, bwdActualFCalls = 0, bwdActualVmFCalls = 0;
	private static long owctyCalls = 0, owctyActual0Calls = 0, owctyActualCCalls = 0, owctyActualCmFCalls = 0;
	private static long deleteInEdgeCalls = 0;
	private static long pivotSearchTime = 0;

	/** Null node */
	private static final long NULL = ExplicitNodeReference.NULL_NODE;

	private static final String statisticsToString() {
		final StringBuilder sb = new StringBuilder();
		final long bwdActualCalls = bwdActualFCalls + bwdActualVmFCalls;
		final long owctyActualCalls = owctyActual0Calls + owctyActualCCalls + owctyActualCmFCalls;
		sb.append("rank=" + Runtime.getRank() + ", ");
		sb.append("fwd=" + fwdCalls + ">" + fwdActualCalls + ", ");
		sb.append("cwd=" + cwdCalls + ">" + cwdActualCalls + ", ");
		sb.append("bwd=" + bwdCalls + ">" + bwdActualCalls);
		sb.append("(F=" + bwdActualFCalls + "+V\\F=" + bwdActualVmFCalls + "), ");
		sb.append("owcty=" + owctyCalls + ">" + owctyActualCalls);
		sb.append("(0=" + owctyActual0Calls + "+C=" + owctyActualCCalls + "+C\\F=" + owctyActualCmFCalls + "), ");
		sb.append("deleteInEdge=" + deleteInEdgeCalls + ", ");
		sb.append("pivotSearchTime=" + ConversionUtils.ns2sec(pivotSearchTime) + "s");
		return sb.toString();
	}
}
