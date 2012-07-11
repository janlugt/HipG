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
import hipg.app.scc.Quotient.LocalQuotientNode;
import hipg.app.scc.Quotient.QuotientNode;
import hipg.app.scc.Quotient.Quotientable;
import hipg.app.utils.Histogram;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;
import myutils.ConversionUtils;
import myutils.ObjectCache;
import myutils.storage.bigarray.BigQueue;
import myutils.tuple.pair.FastIntPair;

public class FB {

	private static final int NOT_VISITED = -1;
	private static final int CHUNK_SIZE = 1024 * 4;
	private static final ObjectCache<MyLocalNode[]> cache = new ObjectCache<MyLocalNode[]>(1024);
	private static final Histogram SCCs = new Histogram();

	final public static String pad(String str, int pad) {
		StringBuilder sb = new StringBuilder(str);
		while (sb.length() < pad)
			sb.append(" ");
		return sb.toString();
	}

	public static interface MyNode extends QuotientNode {
		public void fwd(FBSynchronizer fb);

		public void bwd(FBSynchronizer fb);
	}

	public static class MyLocalNode extends LocalQuotientNode<MyNode> implements MyNode, Quotientable {
		private int labelF = NOT_VISITED, labelB = NOT_VISITED;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		public void fwd(FBSynchronizer fb) {
			if (labelF == fb.labelF && (labelB == fb.labelB || labelB == fb.newLabelB)) {
				labelF = fb.newLabelF;
				fb.F.enqueue(this);
				if (labelB != fb.labelB)
					fb.FnBsize++;
				for (int j = 0; hasNeighbor(j); j++)
					neighbor(j).fwd(fb);
			}
		}

		public void bwd(FBSynchronizer fb) {
			if ((labelF == fb.labelF || labelF == fb.newLabelF) && labelB == fb.labelB) {
				labelB = fb.newLabelB;
				fb.B.enqueue(this);
				if (labelF != fb.labelF)
					fb.FnBsize++;
				for (int j = 0; hasInNeighbor(j); j++)
					inNeighbor(j).bwd(fb);
			}
		}

		public long getComponentId() {
			return FastIntPair.createPair(labelF, labelB);
		}
	}

	public static class FBSynchronizer extends Synchronizer {

		private final ExplicitGraph<MyNode> g;
		private final BigQueue<MyLocalNode> V, F, B;
		private final int labelF, labelB;
		private int FnBsize = 0;
		private int newLabelF, newLabelB;

		// private static int completed = 0;

		public FBSynchronizer(ExplicitGraph<MyNode> g, BigQueue<MyLocalNode> V, int labelF, int labelB) {
			this.g = g;
			this.V = V;
			this.labelF = labelF;
			this.labelB = labelB;
			this.F = new BigQueue<MyLocalNode>(CHUNK_SIZE, 0, cache);
			this.B = new BigQueue<MyLocalNode>(CHUNK_SIZE, 0, cache);
		}

		@Reduce
		public long SelectPivot(long p) {
			if (p == ExplicitNodeReference.NULL_NODE && !V.isEmpty())
				p = V.dequeue().asReference();
			return p;
		}

		@BarrierAndReduce
		public int GlobalFnBSize(int s) {
			return s + FnBsize;
		}

		@SuppressWarnings("unused")
		private void print(String msg) {
			if (Runtime.getRank() == 0) {
				System.out.println(getId() + " " + msg + " [ labelF=" + labelF + " labelB=" + labelB + " newLabelF="
						+ newLabelF + " newLabelB=" + newLabelB + " ]");
			}
		}

		public void run() {
			newLabelF = 2 * getId();
			newLabelB = newLabelF + 1;
			barrier();
			// print("starts");

			long pivot = SelectPivot(ExplicitNodeReference.NULL_NODE);
			if (pivot == ExplicitNodeReference.NULL_NODE)
				return;
			// print("selected pivot " +
			// ExplicitNodeReference.referenceToString(pivot));

			if (ExplicitNodeReference.isLocal(pivot)) {
				((MyLocalNode) g.node(ExplicitNodeReference.getId(pivot))).fwd(this);
				((MyLocalNode) g.node(ExplicitNodeReference.getId(pivot))).bwd(this);
			}
			int globalFnBSize = GlobalFnBSize(0);

			// completed += FnBsize;

			// print("found SCC of size " + globalFnBSize + " from pivot "
			// + ExplicitNodeReference.referenceToString(pivot) + " completed "
			// + completed);

			if (Runtime.getRank() == 0) {
				SCCs.add(globalFnBSize);
			}

			filter(F, newLabelF, labelB);
			filter(B, labelF, newLabelB);
			filter(V, labelF, labelB);

			spawn(new FBSynchronizer(g, F, newLabelF, labelB));
			spawn(new FBSynchronizer(g, B, labelF, newLabelB));
			spawn(new FBSynchronizer(g, V, labelF, labelB));

			// print("done");
		}
	}

	private static void print(String msg) {
		if (Runtime.getRank() == 0)
			System.err.println(msg);
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			System.err.println(FB.class.getName() + " <graph>");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// read graph
		print("Reading graph in format " + args[0] + " " + args[1]);
		ExplicitGraph<MyNode> g = hipg.format.GraphIO.readUndirected(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		print("Graph read");

		// run FB
		BigQueue<MyLocalNode> V = new BigQueue<MyLocalNode>(CHUNK_SIZE, g.nodes() / CHUNK_SIZE, cache);
		for (int i = 0; i < g.nodes(); i++)
			V.enqueue((MyLocalNode) g.node(i));
		FBSynchronizer fb = new FBSynchronizer(g, V, NOT_VISITED, NOT_VISITED);
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(fb);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;

		// print results
		print(SCCs.toString());
		print("FB on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}

	private static BigQueue<MyLocalNode> filter(BigQueue<MyLocalNode> Q, int f, int b) {
		long sz = Q.size();
		while (sz-- > 0) {
			MyLocalNode n = Q.dequeue();
			if (n.labelF == f && n.labelB == b)
				Q.enqueue(n);
		}
		return Q;
	}

}
