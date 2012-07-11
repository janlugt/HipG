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
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;
import myutils.ConversionUtils;
import myutils.system.MonitorThread;

/**
 * Visit all nodes.
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public class VisitAll {

	private static long visitCalls = 0;
	private static final long NULL = ExplicitNodeReference.NULL_NODE;

	public static interface MyNode extends Node {
		public void visit(VisitAllSynchronizer synch);
	}

	public static class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {
		boolean visited = false;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		final public void visit(final VisitAllSynchronizer visitor) {
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

	public static class VisitAllSynchronizer extends Synchronizer {

		private final ExplicitGraph<MyNode> g;
		private int index = 0;
		private int visited = 0;
		private long globalVisited = 0;
		private int iter = 0;

		public VisitAllSynchronizer(ExplicitGraph<MyNode> g) {
			this.g = g;
		}

		@Reduce
		public long SelectPivot(long ref) {
			if (ref == NULL) {
				while (index < g.nodes()) {
					final MyLocalNode node = (MyLocalNode) g.node(index++);
					if (!node.visited) {
						return node.asReference();
					}
				}
			}
			return ref;
		}

		@Reduce
		public long GlobalVisited(long s) {
			return s + (long) visited;
		}

		public void run() {
			final MonitorThread visitMonitor = new MonitorThread(1000, System.err, "run") {
				public void print(StringBuilder sb) {
					sb.append("iter=" + iter + " visitCalls=" + visitCalls + " visited=" + visited + " / " + g.nodes());
				}
			}.startMonitor();
			while (true) {
				iter++;
				final long pivot = SelectPivot(NULL);
				if (pivot == NULL) {
					break;
				}
				final MyLocalNode node = (MyLocalNode) g.node(pivot);
				if (node != null) {
					node.visit(this);
				}
				barrier();
				globalVisited = GlobalVisited(0);
			}
			visitMonitor.stopMonitor();
		}
	}

	private static void print(final String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(msg);
			System.out.flush();
		}
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			System.err.println(VisitAll.class.getName() + " <graph>");
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

		// run visitor-all
		print("Starting VisitAll");
		final VisitAllSynchronizer visitAll = new VisitAllSynchronizer(graph);
		final long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(visitAll);
		Runtime.getRuntime().barrier();
		final long time = System.nanoTime() - start;

		// print results
		print("Visited " + visitAll.globalVisited + " nodes");
		print("VisitAll on " + Config.POOLSIZE + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}
}
