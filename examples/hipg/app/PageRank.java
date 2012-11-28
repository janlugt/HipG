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

import myutils.ConversionUtils;
import myutils.MathUtils;
import hipg.Config;
import hipg.Node;
import hipg.Reduce;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

/**
 * PageRank algorithm from Google. For a specified number of steps all nodes exchange their partial ranks.
 * 
 * @author E.Krepska -- ekr@cs.vu.nl
 */
public class PageRank {
	private static final double D = 0.85;

	public static interface MyNode extends Node {
		public void rank(Ranker ranker, double r);

		public void compute(Ranker ranker);

		public void send(Ranker ranker);
	}

	public static final class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {
		private double rank = 1.0, ranksum = 0.0;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		@Override
		public void rank(Ranker ranker, double r) {
			ranksum += r;
		}

		@Override
		public void compute(Ranker ranker) {
			rank = (1 - D) + D * ranksum;
			ranksum = 0.0;
		}

		@Override
		public void send(Ranker ranker) {
			for (int i = 0; hasNeighbor(i); i++) {
				neighbor(i).rank(ranker, rank / outdegree());
			}
		}
	}

	public static class Ranker extends Synchronizer {
		private final ExplicitGraph<MyNode> g;
		private final int steps;
		private double globalMinRank, globalMaxRank, globalSumRank;
		private long startTime;

		public Ranker(ExplicitGraph<MyNode> g, int steps) {
			this.g = g;
			this.steps = steps;
		}

		@Reduce
		public double[] RankStats(double[] stats) {
			double min = stats[0], max = stats[1], sum = stats[2];
			for (int i = 0; i < g.nodes(); i++) {
				final double rank = ((MyLocalNode) g.node(i)).rank;
				sum += rank;
				if (rank < min) {
					min = rank;
				}
				if (rank > max) {
					max = rank;
				}
			}
			stats[0] = min;
			stats[1] = max;
			stats[2] = sum;
			return stats;
		}

		@Override
		public void run() {
			for (int step = 0; step < steps; step++) {
				print("Step " + step);
				startTime = System.nanoTime();
				for (int i = 0; i < g.nodes(); i++) {
					((MyLocalNode) g.node(i)).send(this);
					if (i % 10000 == 9999) {
						Runtime.nice();
					}
				}
				barrier();
				print(" Send took: " + ConversionUtils.ns2sec(System.nanoTime() - startTime));
				startTime = System.nanoTime();
				for (int i = 0; i < g.nodes(); i++) {
					((MyLocalNode) g.node(i)).compute(this);
				}
				barrier();
				print(" Compute took: " + ConversionUtils.ns2sec(System.nanoTime() - startTime));
			}
			double[] stats = RankStats(new double[] { Double.MAX_VALUE, Double.MIN_VALUE, 0 });
			globalMinRank = stats[0];
			globalMaxRank = stats[1];
			globalSumRank = stats[2];
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
			System.err.println(PageRank.class.getName() + " <graph> [<steps>]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// Read arguments.
		final int steps = args.length > 2 ? Integer.parseInt(args[2]) : 30;

		// Read graph.
		print("Reading graph in format " + args[0] + " " + args[1]);
		long readStart = System.nanoTime();
		final ExplicitGraph<MyNode> g = hipg.format.GraphIO.read(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		long readTime = System.nanoTime() - readStart;
		print("Graph with " + g.getGlobalSize() + " nodes read in " + ConversionUtils.ns2sec(readTime) + "s");

		// Run PageRank.
		print("Starting Ranker");
		Ranker ranker = new Ranker(g, steps);
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(ranker);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;

		// Print results.
		final long N = g.getGlobalSize();
		final double min = MathUtils.round3(ranker.globalMinRank);
		final double max = MathUtils.round3(ranker.globalMaxRank);
		final double sum = MathUtils.round3(ranker.globalSumRank);
		final double avg = MathUtils.round3(ranker.globalSumRank / (double) N);
		print("Computed ranks in interval [" + min + ", " + max + "] with average " + avg + ", sum " + sum
				+ ", for graph with global size " + N);
		print("Ranker on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}
}
