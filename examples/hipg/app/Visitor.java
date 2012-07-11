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

import hipg.BarrierAndReduce;
import hipg.Config;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;
import myutils.ConversionUtils;
import myutils.MathUtils;
import myutils.system.MonitorThread;

/**
 * Visitor algorithm: starting from a selected pivot node, find the set of nodes reachable from it.
 * 
 * 
 * @author ela -- ekr@cs.vu.nl
 * 
 */
public class Visitor {

	public static long visitCalls = 0;

	public static interface MyNode extends Node {
		public void visit(VisitorSynchronizer synch);
	}

	public static class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {
		boolean visited = false;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		final public void visit(VisitorSynchronizer visitor) {
			visitCalls++;
			if (!visited) {
				visited = true;
				visitor.visited++;
				for (int i = 0; hasNeighbor(i); i++) {
					neighbor(i).visit(visitor);
				}
			}
		}
	}

	public static class VisitorSynchronizer extends Synchronizer {
		ExplicitGraph<MyNode> graph;
		private final MyLocalNode pivot;
		int visited = 0;
		private long globalVisited = 0;

		public VisitorSynchronizer(ExplicitGraph<MyNode> graph, MyLocalNode pivot) {
			this.graph = graph;
			this.pivot = pivot;
		}

		@BarrierAndReduce
		public long GlobalVisited(long s) {
			return s + (long) visited;
		}

		@Override
		public void run() {

			if (pivot != null) {
				pivot.visit(this);
			}
			globalVisited = GlobalVisited(0);

		}
	}

	private static void print(final String msg) {
		if (Runtime.getRank() == 0) {
			StringBuilder sb = new StringBuilder();
			sb.append(Runtime.getRank());
			sb.append(": ");
			sb.append(msg);
			System.out.println(msg.toString());
			System.out.flush();
		}
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			System.err.println(Visitor.class.getName() + " <graph>  [ <root id> <root owner> ]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// read graph
		print("Reading graph in format " + args[0] + " " + args[1]);
		final long readStart = System.nanoTime();
		final ExplicitGraph<MyNode> graph = hipg.format.GraphIO.read(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		final long readTime = System.nanoTime() - readStart;
		print("Graph with " + graph.getGlobalSize() + " nodes read in " + ConversionUtils.ns2sec(readTime) + "s");

		// determine pivot
		final long pivot;
		if (args.length >= 4) {
			pivot = ExplicitNodeReference.createReference(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
		} else if (graph.root() != ExplicitNodeReference.NULL_NODE) {
			pivot = graph.root();
		} else {
			pivot = ExplicitNodeReference.createReference(0, 0);
		}
		final MyLocalNode pivotNode = (MyLocalNode) graph.node(pivot);

		// run visitor
		print("Starting Visitor at " + ExplicitNodeReference.referenceToString(pivot));
		final VisitorSynchronizer visitor = new VisitorSynchronizer(graph, pivotNode);
		final MonitorThread monitor = new MonitorThread(60000, System.err, "visitor") {
			public void print(StringBuilder sb) {
				sb.append(Runtime.getRank() + " visited=" + visitor.visited + "/" + graph.nodes() + " ("
						+ MathUtils.proc(visitor.visited, graph.nodes()) + "%) visitCalls=" + visitCalls);
			}
		}.startMonitor();
		Runtime.getRuntime().barrier();
		final long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(visitor);
		Runtime.getRuntime().barrier();
		final long time = System.nanoTime() - start;
		monitor.stopMonitor();

		// print results
		print("Visited " + visitor.globalVisited + " nodes");
		print("Visitor on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
		print("Statistics: total visit calls = " + Visitor.visitCalls);
	}
}
