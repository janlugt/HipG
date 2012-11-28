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

public class PageRankWithTermination {

	// Global variables
	private static final double D = 0.85, E = 0.001;

	// Interface for vertex
	public static interface MyNode extends Node {

		public void rank(Ranker ranker, double r);

		public void compute(Ranker ranker);

		public void send(Ranker ranker);
	}

	// Class for vertex
	public static final class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {
		// Local variables

		private double rank, ranksum = 0.0, diff, N;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		@Override
		public void rank(Ranker ranker, double r) {
			ranksum += r;
		}

		@Override
		public void compute(Ranker ranker) {
			double oldRank = rank;
			rank = (1 - D) / N + D * ranksum;
			ranksum = 0.0;
			diff = Math.abs(rank - oldRank);
		}

		@Override
		public void send(Ranker ranker) {
			for (int i = 0; hasNeighbor(i); i++) {
				neighbor(i).rank(ranker, rank / outdegree());
			}
		}
	}

	// Master thread
	public static class Ranker extends Synchronizer {

		private final ExplicitGraph<MyNode> g;

		public Ranker(ExplicitGraph<MyNode> g) {
			this.g = g;
		}

		@Reduce
		public double getGlobalDiffSum(double diffSum) {
			for (int i = 0; i < g.nodes(); i++) {
				diffSum += ((MyLocalNode) g.node(i)).diff;
			}
			return diffSum;
		}

		@Override
		public void run() {

			// initialization of vertices
			for (int i = 0; i < g.nodes(); i++) {
				((MyLocalNode) g.node(i)).rank = 1.0 / g.getGlobalSize();
				((MyLocalNode) g.node(i)).N = g.getGlobalSize();
				nice(i);
			}
			barrier();

			// master loop
			int step = 0;
			double diffSum = Double.POSITIVE_INFINITY;
			while (diffSum > E) {
				print("Step " + step);

				// worker step
				for (int i = 0; i < g.nodes(); i++) {
					((MyLocalNode) g.node(i)).send(this);
					nice(i);
				}
				barrier();

				// worker step
				for (int i = 0; i < g.nodes(); i++) {
					((MyLocalNode) g.node(i)).compute(this);
					nice(i);
				}
				barrier();

				// master step
				diffSum = getGlobalDiffSum(0.0);
				print("errorSum = " + diffSum);
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
			System.err.println(PageRankWithTermination.class.getName() + " <graph>");
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

		// Run PageRank.
		print("Starting Ranker");
		Ranker ranker = new Ranker(g);
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(ranker);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;

		// Print results.
		final long N = g.getGlobalSize();
		print("Computed ranks for graph with global size " + N);
		print("Ranker on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}
}
