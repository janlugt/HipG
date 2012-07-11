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
import hipg.app.Bigraph.BiLocalNode;
import hipg.app.Bigraph.BiNode;
import hipg.app.Bigraph.Bidi;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;
import myutils.BitUtils;
import myutils.ConversionUtils;
import myutils.MathUtils;
import myutils.storage.LongIterator;
import myutils.storage.set.LongHashSet;

public class TSCCch {
	/** True if colors are randomized (mangled ids). */
	private static boolean RandomizeColors = false;
	/** True if debugging information about execution of TSCC should be printed. */
	private static boolean ExecutionInfo = false;
	/** List of terminal SCCs. */
	private static LongHashSet NonTerminal = new LongHashSet();

	public static interface TSCCChNode extends BiNode {
		public void color(TSCCchSynch ch, long color);

		public void fwd(TSCCchSynch ch, long color);
	}

	public static class TSCCChLocalNode extends BiLocalNode<TSCCChNode> implements TSCCChNode {
		private long color;
		private boolean terminal = false;
		private boolean sent = false;

		public TSCCChLocalNode(ExplicitGraph<TSCCChNode> graph, int reference) {
			super(graph, reference);
		}

		public void init() {
			color = initialColor();
		}

		private long initialColor() {
			if (RandomizeColors) {
				return BitUtils.MangleBitsInLong(asReference());
			} else {
				return asReference();
			}
		}

		public boolean isHead() {
			return initialColor() == color;
		}

		public void color0(final TSCCchSynch ch) {
			if (!sent) {
				sent = true;
				for (int j = 0; hasInNeighbor(j); ++j) {
					inNeighbor(j).color(ch, color);
				}
			}
		}

		public void color(final TSCCchSynch ch, final long newColor) {
			if (newColor > color) {
				sent = true;
				ch.changes++;
				color = newColor;
				for (int j = 0; hasInNeighbor(j); ++j) {
					inNeighbor(j).color(ch, newColor);
				}
			}
		}

		public void fwd(final TSCCchSynch ch, final long fwdColor) {
			fwdCalls++;
			if (color == fwdColor) {
				if (!terminal) {
					fwdActualCalls++;
					terminal = true;
					for (int j = 0; hasNeighbor(j); j++) {
						neighbor(j).fwd(ch, fwdColor);
					}
				}
			} else {
				fwdTerminalCalls++;
				NonTerminal.insert(fwdColor);
			}
		}
	}

	public static class TSCCchSynch extends Synchronizer {
		private final ExplicitGraph<TSCCChNode> g;
		private long changes = 0;
		private long heads = 0;
		private long globalChanges;
		private long globalHeads;
		private LongHashSet globalNonTerminal;
		private long globalTSCCs;

		public TSCCchSynch(ExplicitGraph<TSCCChNode> g) {
			this.g = g;
		}

		@BarrierAndReduce
		public long GlobalChanges(long s) {
			return s + changes;
		}

		@BarrierAndReduce
		public long GlobalHeads(long s) {
			return s + heads;
		}

		@BarrierAndReduce
		public LongHashSet GlobalNonTerminal(LongHashSet set) {
			if (set == null || set.isEmpty()) {
				return NonTerminal;
			}
			if (NonTerminal.isEmpty()) {
				return set;
			}
			LongIterator iter = NonTerminal.iterator();
			while (iter.hasNext()) {
				set.insert(iter.next());
			}
			return set;
		}

		public void run() {
			// Init colors.
			info("initializing colors");
			for (int i = 0; i < g.nodes(); ++i) {
				final TSCCChLocalNode node = (TSCCChLocalNode) g.node(i);
				node.init();
			}
			barrier();

			// Color vertices.
			info("coloring");
			for (int i = 0; i < g.nodes(); ++i) {
				final TSCCChLocalNode n = (TSCCChLocalNode) g.node(i);
				n.color0(this);
				if (i % 1000 == 0) {
					Runtime.nice();
				}
			}
			barrier();

			// Compute backward reachability.
			info("components");
			for (int i = 0; i < g.nodes(); ++i) {
				final TSCCChLocalNode n = (TSCCChLocalNode) g.node(i);
				if (n.isHead()) {
					n.fwd(this, n.color);
					heads++;
					if (heads % 2000 == 0) {
						Runtime.nice();
					}
				}
			}
			barrier();

			globalChanges = GlobalChanges(0);
			globalHeads = GlobalHeads(0);
			globalNonTerminal = GlobalNonTerminal(null);
			globalTSCCs = globalHeads - globalNonTerminal.size();
		}
	}

	private static void info(String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(msg);
			System.out.flush();
		}
	}

	private static final ExplicitGraph<TSCCChNode> readUndirectedGraph(String graphFormat, String graphFormatDetails,
			boolean monitorExecution, boolean executionInfo) {
		// Try reading graph and its transpose in one go.
		ExplicitGraph<TSCCChNode> g = null;
		try {
			g = hipg.format.GraphIO.readUndirected(TSCCChLocalNode.class, TSCCChNode.class, graphFormat,
					graphFormatDetails, Config.POOLSIZE);
		} catch (GraphCreationException e) {
			try {
				// Reading graph and its transpose failed. Try reading only the graph.
				g = hipg.format.GraphIO.read(TSCCChLocalNode.class, TSCCChNode.class, graphFormat, graphFormatDetails,
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
			System.err.println(TSCCch.class.getName() + " <graph spec> [-execution-info] [-print-components]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// Parse options.
		for (int i = 2; i < args.length; i++) {
			final String arg = args[i];
			if ("-execution-info".equals(arg)) {
				ExecutionInfo = true;
			} else if ("-no-execution-info".equals(arg)) {
				ExecutionInfo = false;
			} else if ("-randomized-colors".equals(arg)) {
				RandomizeColors = true;
			} else if ("-no-randomized-colors".equals(arg)) {
				RandomizeColors = false;
			} else {
				throw new RuntimeException("Unrecognized argument: " + arg);
			}
		}
		info("RandomizeColors = " + RandomizeColors);

		// Read graph.
		info("Reading graph in format " + args[0] + " " + args[1]);
		final long startRead = System.nanoTime();
		final ExplicitGraph<TSCCChNode> g = readUndirectedGraph(args[0], args[1], false, ExecutionInfo);
		final long timeRead = System.nanoTime() - startRead;
		info("Graph of size " + g.getGlobalSize() + " read in " + ConversionUtils.ns2sec(timeRead) + "s");

		// Run TSCCch.
		info("Computing TSCCch");
		final TSCCchSynch ch = new TSCCchSynch(g);
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(ch);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;

		// Print results
		info("TSCCch on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
		info("Found " + ch.globalTSCCs + " TSCCs");

		// Print stats.
		info("Avg color change: " + MathUtils.round((double) ch.globalChanges / (double) g.getGlobalSize()));
		info("Statistics: heads=" + ch.globalHeads + ", " + getStatistics());
	}

	/* Statistics */
	private static String getStatistics() {
		final StringBuilder sb = new StringBuilder();
		sb.append("fwdCalls=" + fwdCalls + ">" + fwdActualCalls + "+" + fwdTerminalCalls);
		return sb.toString();
	}

	static long fwdCalls = 0, fwdActualCalls = 0, fwdTerminalCalls = 0;
}
