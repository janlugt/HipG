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
import hipg.app.Bigraph.Bidi;
import hipg.app.scc.PNSSCompElements.PNSSCompElementsLister;
import hipg.app.scc.Quotient.LocalQuotientNode;
import hipg.app.scc.Quotient.QuotientComputer;
import hipg.app.scc.Quotient.QuotientNode;
import hipg.app.utils.Histogram;
import hipg.app.utils.SccStructure;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.util.Date;

import myutils.ConversionUtils;
import myutils.MathUtils;

public class CH {

	private static final boolean FullSCC = true;
	private static final boolean verbose = true;
	private static final boolean debug = true;
	private static boolean elements = true;
	private static long debugInterval = 1000;
	private static long GlobalSccTime = 0L;
	private static final long NULL = ExplicitNodeReference.NULL_NODE;
	private static final SccStructure LocalSCCStructure = FullSCC ? new SccStructure() : null;
	private static final Histogram LocalSCCCounter = FullSCC ? null : new Histogram();

	public static interface ChNode extends QuotientNode {

		public void color(CHSynch ch, long color);

		public void bwd(CHSynch ch, long color);

		public void computeSize(SccSizeComputerSynch counter, long src, long c);

		public void returnSize(SccSizeComputerSynch counter, long sz);

	}

	public static class ChLocalNode extends LocalQuotientNode<ChNode> implements ChNode {

		private long color;
		private boolean removed;
		private long father = NULL;
		private long size = 0;
		private short acks;

		public ChLocalNode(ExplicitGraph<ChNode> graph, int reference) {
			super(graph, reference);
		}

		public void init() {
			if (!removed) {
				color = asReference();
			}
		}

		public boolean color0(final CHSynch ch) {
			if (!removed) {
				for (int j = 0; hasNeighbor(j); j++) {
					neighbor(j).color(ch, color);
				}
			}
			return !removed;
		}

		public void color(final CHSynch ch, final long c) {
			if (!removed && c > color) {

				ch.changes++;

				if (debug) {
					if (ch.changes >= debugInterval) {
						System.err.println("On " + new Date() + " at " + Runtime.getRank() + " changes=" + ch.changes);
						debugInterval <<= 1;
					}
				}

				color = c;

				for (int j = 0; hasNeighbor(j); j++) {
					neighbor(j).color(ch, c);
				}
			}
		}

		public boolean isHead() {
			return asReference() == color;
		}

		public void head(final CHSynch ch) {
			removed = true;
			ch.removed++;
			for (int j = 0; hasInNeighbor(j); j++)
				inNeighbor(j).bwd(ch, color);
		}

		public void bwd(final CHSynch ch, final long c) {
			if (color == c && !removed) {
				removed = true;
				ch.removed++;
				for (int j = 0; hasInNeighbor(j); j++)
					inNeighbor(j).bwd(ch, color);
			}
		}

		public void initComputeSize(SccSizeComputerSynch counter) {
			if (isHead()) {
				size++;
				computeSize(counter, NULL, color);
			}
		}

		public void computeSize(SccSizeComputerSynch counter, final long src, final long c) {
			// System.err.println("compute size at " + name() + " from " +
			// name(src) + " color=" + name(color) + " c="
			// + name(c) + " src=" + name(src) + " father=" + name(father) +
			// " isHead=" + isHead() + " branch?"
			// + (color != c || father != NULL || (isHead() && src != NULL) ?
			// "first" : "second"));
			if (color != c || father != NULL || (isHead() && src != NULL)) {
				graph.globalNode(src).returnSize(counter, 0);
			} else {
				father = src;
				for (int j = 0; hasNeighbor(j); j++) {
					acks++;
				}
				if (acks == 0 && father != NULL) {
					graph.globalNode(father).returnSize(counter, size + 1);
				}
				for (int j = 0; hasNeighbor(j); j++) {
					neighbor(j).computeSize(counter, asReference(), c);
				}
			}
		}

		public void returnSize(SccSizeComputerSynch counter, final long sz) {
			// System.err.println(name() + " return size=" + size + " sz=" + sz
			// + " acks=" + acks + " to father="
			// + name(father));
			size += sz;
			acks--;
			if (acks == 0 && father != NULL) {
				graph.globalNode(father).returnSize(counter, size + 1);
			}
		}

		@Override
		public long getComponentId() {
			return color;
		}
	}

	public static class CHSynch extends Synchronizer {

		private final ExplicitGraph<ChNode> g;
		private long removed = 0;
		private long changes = 0;
		private long globalChanges;

		public CHSynch(ExplicitGraph<ChNode> g) {
			this.g = g;
		}

		@BarrierAndReduce
		public long GlobalRemoved(long s) {
			return s + removed;
		}

		@BarrierAndReduce
		public long GlobalChanges(long s) {
			return s + changes;
		}

		private void print(String msg) {
			if (verbose && Runtime.getRank() == 0)
				System.err.println(msg);
		}

		public void run() {
			int iter = 0;
			long globalRemoved = 0;
			do {

				print("  initializing, iteration " + iter);

				/* init colors */
				for (int i = 0; i < g.nodes(); i++) {
					((ChLocalNode) g.node(i)).init();
				}
				barrier();

				/* color nodes */
				print("  coloring, iteration " + iter);
				int started = 0;
				for (int i = 0; i < g.nodes(); i++) {
					ChLocalNode n = (ChLocalNode) g.node(i);
					if (n.color0(this))
						started++;
					if (started % 500 == 0)
						Runtime.nice();
				}
				barrier();

				/* compute bwd's */
				print("  discovering sccs, iteration " + iter);
				int heads = 0;
				for (int i = 0; i < g.nodes(); i++) {
					final ChLocalNode n = (ChLocalNode) g.node(i);
					if (!n.removed && n.isHead()) {
						n.head(this);
						heads++;
						if (heads % 500 == 0)
							Runtime.nice();
					}
				}
				barrier();
				iter++;

				globalRemoved = GlobalRemoved(0);

				print("removed " + MathUtils.proc(globalRemoved, g.getGlobalSize()) + " %");

			} while (globalRemoved < g.getGlobalSize());

			globalChanges = GlobalChanges(0);
		}
	}

	public static class SccSizeComputerSynch extends Synchronizer {

		private final ExplicitGraph<ChNode> graph;

		private Histogram GlobalSCCCounter;
		private SccStructure GlobalSCCStructure;

		public SccSizeComputerSynch(ExplicitGraph<ChNode> graph) {
			this.graph = graph;
		}

		@Reduce
		public Histogram GlobalSCCCounter(Histogram counter) {
			return LocalSCCCounter.add(counter);
		}

		@Reduce
		public SccStructure GlobalSCCStructure(SccStructure structure) {
			return LocalSCCStructure.combine(structure);
		}

		@Override
		public void run() {
			// compute size for each head
			for (int i = 0; i < graph.nodes(); i++) {
				ChLocalNode node = (ChLocalNode) graph.node(i);
				node.initComputeSize(this);
			}
			barrier();

			// count SCCs locally
			for (int i = 0; i < graph.nodes(); i++) {
				ChLocalNode node = (ChLocalNode) graph.node(i);
				if (node.isHead()) {
					if (FullSCC) {
						LocalSCCStructure.addComponent(node.color, node.size);
					} else {
						LocalSCCCounter.add(node.size);
					}
				}
			}
			barrier();

			// count SCCs globally
			if (FullSCC) {
				GlobalSCCStructure = GlobalSCCStructure(null);
			} else {
				GlobalSCCCounter = GlobalSCCCounter(null);
			}
		}
	}

	private static void print(String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(msg);
			System.out.flush();
		}
	}

	public static void main(String[] args) throws GraphCreationException {

		if (args.length < 2) {
			System.err.println(CH.class.getName() + " <graph>");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// read graph
		print("Reading graph in format " + args[0] + " " + args[1]);
		final long startRead = System.nanoTime();
		ExplicitGraph<ChNode> g = hipg.format.GraphIO.read(ChLocalNode.class, ChNode.class, args[0], args[1],
				Config.POOLSIZE);
		final long timeRead = System.nanoTime() - startRead;
		print("Graph read in " + ConversionUtils.ns2sec(timeRead) + "s, " + g.getGlobalSize() + " nodes (globally)");

		// compute transpose
		final Bidi bidi = new Bidi(g, true, true);
		final long startTranspose = System.nanoTime();
		Runtime.getRuntime().spawnAll(bidi);
		Runtime.getRuntime().barrier();
		final long timeTranspose = System.nanoTime() - startTranspose;
		print("Transpose computed in " + ConversionUtils.ns2sec(timeTranspose));

		// run CH
		print("Computing CH");
		final CHSynch ch = new CHSynch(g);
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(ch);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;
		print("CH done in " + ConversionUtils.ns2sec(time) + "s");

		// compute results
		print("Computing SCCs sizes");
		long startComputeSccSizes = System.nanoTime();
		final SccSizeComputerSynch count = new SccSizeComputerSynch(g);
		Runtime.getRuntime().spawnAll(count);
		Runtime.getRuntime().barrier();
		long timeComputeSccSizes = System.nanoTime() - startComputeSccSizes;
		print("SCC sizes computed in " + ConversionUtils.ns2sec(timeComputeSccSizes) + "s");
		print(FullSCC ? count.GlobalSCCStructure.toString() : count.GlobalSCCCounter.toString());

		// compute SCC
		if (FullSCC) {
			print("Computing SCC structure");
			final long startComputeSccStructure = System.nanoTime();
			final QuotientComputer q = new QuotientComputer(g, LocalSCCStructure);
			Runtime.getRuntime().spawnAll(q);
			Runtime.getRuntime().barrier();
			final long timeComputeSccStructure = System.nanoTime() - startComputeSccStructure;
			print("SCC structure computed in " + ConversionUtils.ns2sec(timeComputeSccStructure) + "s");

			if (Runtime.getRank() == 0) {
				final String sccStructurefileNameBase = "ch-sccs";
				print("Writing scc structure to files " + sccStructurefileNameBase + "-*.dot");
				q.GlobalSCCStructure.toDot(sccStructurefileNameBase, 3000, 10, true);
			}

			if (elements) {
				print("Computing terminal components");
				final PNSSCompElementsLister T = new PNSSCompElementsLister(g,
						q.GlobalSCCStructure.getTerminalComponents(), "ch-comp");
				final long startComputeTerminal = System.nanoTime();
				Runtime.getRuntime().spawnAll(T);
				Runtime.getRuntime().barrier();
				long timeComputeTerminal = System.nanoTime() - startComputeTerminal;
				print("Terminal SCCs computed in " + ConversionUtils.ns2sec(timeComputeTerminal) + "s");
			}
		}

		// print results
		print("Avg color change: " + MathUtils.round((double) ch.globalChanges / (double) g.getGlobalSize()));
		print("CH on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
		print("Computing SCC sizes took " + ConversionUtils.ns2sec(GlobalSccTime) + "s");
	}
}
