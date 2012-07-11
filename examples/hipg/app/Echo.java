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

/**
 * Echo algorithm: starting from a selected pivot node, find the set of nodes
 * reachable from it.
 * 
 * 
 * @author ela -- ekr@cs.vu.nl
 * 
 */
public class Echo {

	public static interface MyNode extends Node {
		public void echo(EchoSynchronizer synch, long src);
	}

	public static class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {

		private int acks = -1;
		private long father = -1;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		public void init(EchoSynchronizer echo) {
			echo.echo++;
			acks = outdegree() + indegree();
			for (int j = 0; hasNeighbor(j); j++) {
				neighbor(j).echo(echo, asReference());
			}
			for (int j = 0; hasInNeighbor(j); j++) {
				inNeighbor(j).echo(echo, asReference());
			}
		}

		public void echo(EchoSynchronizer echo, long src) {
			System.err.println("echo at " + name() + " with acks = " + acks + " father=" + father);
			if (acks < 0) {
				echo.echo++;
				father = src;
				acks = outdegree() + indegree();
				for (int j = 0; hasNeighbor(j); j++) {
					if (neighborReference(j) != father) {
						neighbor(j).echo(echo, asReference());
					}
				}
				for (int j = 0; hasInNeighbor(j); j++) {
					if (inNeighborReference(j) != father) {
						inNeighbor(j).echo(echo, asReference());
					}
				}
			} else {
				acks--;
				if (acks == 0 && father >= 0) {
					graph.globalNode(father).echo(echo, 0);
				}
			}
		}
	}

	public static class EchoSynchronizer extends Synchronizer {
		private final MyLocalNode pivot;
		int echo = 0;
		private long globalEcho = 0;

		public EchoSynchronizer(MyLocalNode pivot) {
			this.pivot = pivot;
		}

		@Reduce
		public long GlobalEcho(long s) {
			return globalEcho + s;
		}

		@Override
		public void run() {
			if (pivot != null) {
				pivot.init(this);
			}
			barrier();
			globalEcho = GlobalEcho(0);
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
			System.err.println(Echo.class.getName() + " <graph>  [ <root id> <root owner> ]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// read graph
		print("Reading graph in format " + args[0] + " " + args[1]);
		final long readStart = System.nanoTime();
		final ExplicitGraph<MyNode> g = hipg.format.GraphIO.readUndirected(MyLocalNode.class, MyNode.class, args[0],
				args[1], Config.POOLSIZE);
		final long readTime = System.nanoTime() - readStart;
		print("Graph with " + g.getGlobalSize() + " nodes read in " + ConversionUtils.ns2sec(readTime) + "s");

		// determine pivot
		final long pivot;
		if (args.length >= 4) {
			pivot = ExplicitNodeReference.createReference(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
		} else if (g.root() != ExplicitNodeReference.NULL_NODE) {
			pivot = g.root();
		} else {
			pivot = ExplicitNodeReference.createReference(0, 0);
		}
		final MyLocalNode pivotNode = (MyLocalNode) g.node(pivot);

		// run echo
		print("Starting Echo at " + ExplicitNodeReference.referenceToString(pivot));
		final EchoSynchronizer echo = new EchoSynchronizer(pivotNode);
		final long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(echo);
		Runtime.getRuntime().barrier();
		final long time = System.nanoTime() - start;

		// print results
		print("Echoed " + echo.globalEcho + " nodes");
		print("Echo on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}
}
