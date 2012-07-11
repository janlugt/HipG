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

package hipg.app;

import hipg.Config;
import hipg.Node;
import hipg.Reduce;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;
import myutils.ConversionUtils;
import myutils.MathUtils;
import myutils.system.MonitorThread;

/**
 * Creates an inverse of a directed graph.
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public class Bigraph {

	/** Bidi node interface. */
	public static interface BiNode extends Node {
		public void connect(Bidi bidi, int sourceOwner, int sourceId);
	}

	/** Bidi local node implementation. */
	public static class BiLocalNode<TNode extends BiNode> extends ExplicitLocalNode<TNode> implements BiNode {
		public BiLocalNode(ExplicitGraph<TNode> graph, int reference) {
			super(graph, reference);
		}

		/** Stores a new in-neighbor (sender is the source of the new edge). */
		public final void connect(final Bidi bidi, final int sourceOwner, final int sourceId) {
			connectCalls++;
			try {
				addInTransition(sourceOwner, sourceId);
			} catch (GraphCreationException e) {
				e.printStackTrace();
				throw new RuntimeException("Could not connect: " + e.getMessage(), e);
			}
		}
	}

	/** A HipG algorithm to compute a transpose (each edge is bi-directional). */
	public static class Bidi extends Synchronizer {
		/** The graph to compute the transpose of. */
		private final ExplicitGraph<BiNode> g;
		/** The number of global outgoing transitions. */
		private long globalOutTransitions = -1;
		/** The number of global incoming transitions. */
		private long globalInTransitions = -1;
		/** Current stage of the algorithm. */
		private BidiStage stage = BidiStage.NotStarted;
		/** True if results summary should be printed. */
		private final boolean printResultSummary;
		/** The monitor thread for the execution, prints the current stage every 30s. */
		private final MonitorThread monitor;
		/** Number of nodes that already send connections requests. */
		private int connected = 0;

		@SuppressWarnings("unchecked")
		public <TNode extends BiNode> Bidi(final ExplicitGraph<TNode> g, final boolean monitorExecution,
				final boolean printResultSummary) {
			this.g = (ExplicitGraph<BiNode>) g;
			this.printResultSummary = printResultSummary;
			this.monitor = monitorExecution ? new MonitorThread(30000, System.err, "Bidi") {
				public void print(final StringBuilder sb) {
					sb.append(stage.name());
					if (stage == BidiStage.ConnectStarted || stage == BidiStage.ConnectDone) {
						sb.append("connect: sent=" + connected + "/" + g.nodes() + ", recv=" + connectCalls);
					}
				}
			} : null;
		}

		/** Computes the global number of outgoing transitions. */
		@Reduce
		public long OutTransitions(long transitions) {
			for (int i = 0; i < g.nodes(); i++) {
				final ExplicitLocalNode<BiNode> n = g.node(i);
				transitions += n.outdegree();
			}
			return transitions;
		}

		/** Computes the global number of incoming transitions. */
		@Reduce
		public long InTransitions(long transitions) {
			for (int i = 0; i < g.nodes(); i++) {
				final ExplicitLocalNode<BiNode> n = g.node(i);
				transitions += n.indegree();
			}
			return transitions;
		}

		public final void run() {
			// Start the monitor.
			stage = BidiStage.InitStarted;
			if (monitor != null) {
				monitor.startMonitor();
			}
			// Initialize the data structures to hold the transpose.
			g.initTranspose(false, g.getTransitions().getNumLocalTransitions() * 8 / 7, g.getTransitions()
					.getNumRemoteTransitions() * 8 / 7);
			stage = BidiStage.InitDone;
			barrier();

			// Connect: each node sends a connect message to each neighbor.
			stage = BidiStage.ConnectStarted;
			for (connected = 0; connected < g.nodes(); ++connected) {
				final BiLocalNode<?> source = (BiLocalNode<?>) g.node(connected);
				for (int j = 0; source.hasNeighbor(j); j++) {
					source.neighbor(j).connect(this, source.owner(), source.reference());
				}
				if (connected % 10000 == 0) {
					Runtime.nice();
				}
			}
			stage = BidiStage.ConnectDone;
			barrier();

			// Finalize connections.
			stage = BidiStage.FinalizeStarted;
			g.getInTransitions().finish();
			globalInTransitions = InTransitions(0);
			globalOutTransitions = OutTransitions(0);
			stage = BidiStage.FinalizeDone;
			barrier();

			// Run garbage collector (gc).
			stage = BidiStage.GcStarted;
			System.gc();
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
			stage = BidiStage.GcDone;
			barrier();

			/* results */
			if (printResultSummary) {
				stage = BidiStage.PrintSummaryStarted;
				final double avgOut = MathUtils.round((double) globalOutTransitions / (double) g.getGlobalSize());
				final double avgIn = MathUtils.round((double) globalInTransitions / (double) g.getGlobalSize());
				info("Global out transitions = " + globalOutTransitions + ", avg = " + MathUtils.round3(avgOut));
				info("Global in transitions = " + globalInTransitions + ", avg = " + MathUtils.round3(avgIn));
			}
			stage = BidiStage.Finished;
			if (monitor != null) {
				monitor.stopMonitor();
			}
		}
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			// Print usage and exit.
			System.err.println(Bigraph.class.getName() + " <graph spec> [-print-summary] [-monitor-execution]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// Read options.
		boolean printSummary = false;
		boolean monitorExecution = false;
		for (int i = 2; i < args.length; ++i) {
			final String arg = args[i];
			if ("-print-summary".equals(arg)) {
				printSummary = true;
			} else if ("-no-print-summary".equals(arg)) {
				printSummary = false;
			} else if ("-monitor-execution".equals(arg)) {
				monitorExecution = true;
			} else if ("-no-monitor-execution".equals(arg)) {
				monitorExecution = false;
			} else {
				throw new RuntimeException("Unrecognized argument: " + arg);
			}
		}

		// Read graph.
		info("Reading graph in format " + args[0] + " " + args[1]);
		final long readStart = System.nanoTime();
		final ExplicitGraph<BiNode> g = hipg.format.GraphIO.read(BiLocalNode.class, BiNode.class, args[0], args[1],
				Config.POOLSIZE);
		final long readTime = System.nanoTime() - readStart;
		info("Graph read in " + ConversionUtils.ns2sec(readTime) + "s");

		// Compute transpose.
		final long bidiStart = System.nanoTime();
		Bidi bidi = new Bidi(g, monitorExecution, printSummary);
		Runtime.getRuntime().spawnAll(bidi);
		Runtime.getRuntime().barrier();
		final long bidiTime = System.nanoTime() - bidiStart;
		info("Bidi on " + Config.POOLSIZE + " took " + ConversionUtils.ns2sec(bidiTime) + "s");
	}

	private static void info(String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(msg);
			System.out.flush();
		}
	}

	/** List of stages of the algorithm (for debugging). */
	private static enum BidiStage {
		NotStarted,
		InitStarted,
		InitDone,
		ConnectStarted,
		ConnectDone,
		FinalizeStarted,
		FinalizeDone,
		GcStarted,
		GcDone,
		PrintSummaryStarted,
		Finished
	};

	/** Number of connects performed (for debugging). */
	private static long connectCalls = 0;

}
