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
import hipg.Reduce;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;
import myutils.ConversionUtils;
import myutils.MathUtils;
import myutils.storage.bigarray.BigQueue;

/**
 * Breadth-first search started from a root/selected pivot node.
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public class BFS2 {

	public static interface MyNode extends Node {
		public void found(BFSSynch synch, int d);
	}

	public static class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {

		private int dist = -1;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		public void found(BFSSynch synch, int d) {
			if (dist < 0) {
				dist = d;
				synch.visited++;
				synch.Q.enqueue(this);
			}
		}
	}

	public static class BFSSynch extends Synchronizer {
		private final ExplicitGraph<MyNode> g;
		final BigQueue<MyLocalNode> Q;
		long localQsize;
		int visited = 0;

		public BFSSynch(ExplicitGraph<MyNode> g, MyLocalNode pivot) {
			this.g = g;
			this.Q = new BigQueue<MyLocalNode>(1024 * 64, 1024);
			if (pivot != null) {
				System.out.println("BFS from pivot " + pivot.name());
				pivot.found(this, 0);
			}
			localQsize = Q.size();
		}

		@BarrierAndReduce
		public long GlobalQSize(long s) {
			localQsize = Q.size();
			return s + localQsize;
		}

		@Override
		public void run() {
			int depth = 0;
			long qs;
			do {
				for (int i = 0; i < localQsize; i++) {
					MyLocalNode n = Q.dequeue();
					for (int j = 0; n.hasNeighbor(j); j++)
						n.neighbor(j).found(this, depth);
					if (i % 1000 == 0)
						Runtime.nice();
				}
				depth++;
				qs = GlobalQSize(0);
				if (Runtime.getRank() == 0)
					System.out.println("Layer of depth " + depth + " and size " + qs);
			} while (qs > 0);
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
			System.err.println(BFS2.class.getName() + " <graph> [ <root id> <root owner> ]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// read graph
		print("Reading graph in format " + args[0] + " " + args[1]);
		long readStart = System.nanoTime();
		final ExplicitGraph<MyNode> g = hipg.format.GraphIO.read(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		final long readTime = System.nanoTime() - readStart;
		print("Graph with " + g.getGlobalSize() + " nodes read in " + ConversionUtils.ns2sec(readTime) + "s");

		// run BFS
		print("Starting BFS");
		final long src;
		if (args.length >= 4) {
			src = ExplicitNodeReference.createReference(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
		} else if (g.root() != ExplicitNodeReference.NULL_NODE) {
			src = g.root();
		} else {
			src = ExplicitNodeReference.createReference(0, 0);
		}
		print("Using root " + ExplicitNodeReference.referenceToString(src));
		final MyLocalNode srcNode = (MyLocalNode) (ExplicitNodeReference.isLocal(src) && g.hasNode(src) ? g
				.node(ExplicitNodeReference.getId(src)) : null);
		final BFSSynch sssp = new BFSSynch(g, srcNode);
		final long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(sssp);
		Runtime.getRuntime().barrier();
		final long time = System.nanoTime() - start;

		// print results
		print("BFS on " + Config.POOLSIZE + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}
}
