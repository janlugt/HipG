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

public class HopDist {

	// Interface for vertex
	public static interface MyNode extends Node {

		public void found(PathFinder ranker, int neighborDist);
		public void check(PathFinder ranker);

	}

	// Class for vertex
	public static final class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {
		// Local variables
		private int dist, dist_nxt;
		private boolean updated, updated_nxt;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}
		
		@Override
		public void found(PathFinder ranker, int neighborDist) {
			if (neighborDist < dist && neighborDist < dist_nxt) {
				dist_nxt = neighborDist;
				updated_nxt = true;
			}
		}

		@Override
		public void check(PathFinder ranker) {
			for (int i = 0; hasNeighbor(i); i++) {
				neighbor(i).found(ranker, dist + 1);
			}
		}
	}

	// Master thread
	public static class PathFinder extends Synchronizer {

		private final ExplicitGraph<MyNode> g;
		private final MyLocalNode root;

		public PathFinder(ExplicitGraph<MyNode> g, MyLocalNode root) {
			this.g = g;
			this.root = root;
		}

		@Reduce
		public boolean existsUpdated(boolean updated) {
			for (int i = 0; i < g.nodes(); i++) {
				updated = updated || ((MyLocalNode) g.node(i)).updated;
			}
			return updated;
		}

		@Override
		public void run() {

			// initialization of vertices
			for (int i = 0; i < g.nodes(); i++) {
				MyLocalNode n = (MyLocalNode) g.node(i);
				n.dist = n == root ? 0 : Integer.MAX_VALUE;
				n.updated = n == root ? true : false;
				n.dist_nxt = n.dist;
				n.updated_nxt = n.updated;
				nice(i);
			}
			barrier();

			// master loop
			int step = 0;
			boolean fin = false;
			while (!fin) {
				fin = true;
				print("Step " + step);

				// worker step
				for (int i = 0; i < g.nodes(); i++) {
					MyLocalNode n = (MyLocalNode) g.node(i);
					if (n.updated) {
						n.check(this);
					}
					nice(i);
				}
				barrier();

				// worker step
				for (int i = 0; i < g.nodes(); i++) {
					MyLocalNode n = (MyLocalNode) g.node(i);
					n.dist = n.dist_nxt;
					n.updated = n.updated_nxt;
					n.updated_nxt = false;
					nice(i);
				}
				barrier();

				// master step
				fin = !existsUpdated(false);
				barrier();

				// master step
				step++;
			}
		}

		void nice(int i) {
			if (i % 10000 == 9999) {
				Runtime.nice();
			}
		}
	}

	private static void print(String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(Runtime.getRank() + ": " + msg);
			System.out.flush();
		}
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			// Print usage and exit.
			System.err.println(HopDist.class.getName() + " <graph> [ <root id> <root owner> ]");
			System.err.println("where graph can be specified as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// Read graph.
		print("Reading graph in format " + args[0] + " " + args[1]);
		long readStart = System.nanoTime();
		final ExplicitGraph<MyNode> g = hipg.format.GraphIO.read(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		long readTime = System.nanoTime() - readStart;
		print("Graph with " + g.getGlobalSize() + " nodes read in " + ConversionUtils.ns2sec(readTime) + "s");

		// run HopDist
		print("Starting HopDist");
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
		
		// Run HopDist.
		print("Starting HopDist");
		PathFinder ranker = new PathFinder(g, srcNode);
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(ranker);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;

		// Print results.
		final long N = g.getGlobalSize();
		print("Computed hopdists for graph with global size " + N);
		print("HopDist on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}
}
