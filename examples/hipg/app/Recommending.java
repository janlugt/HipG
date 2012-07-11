/**
 * Copyright (c) 2010, 2011 Vrije Universiteit Amsterdam.
 * Written by Elzbieta Krepska, e.krepska@vu.nl.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package hipg.app;

import hipg.Config;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.util.Random;

import myutils.ConversionUtils;

public class Recommending {

	private static final Random random = new Random(System.nanoTime());
	private static final double P = 0.51;

	public static interface MyNode extends Node {
		public void recommend(Recommender recommender);
	}

	public static class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {

		private boolean used = false;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		final public void recommend(Recommender recommender) {
			if (!used && random.nextDouble() < P) {
				used = true;
				recommender.used++;
				for (int j = 0; hasNeighbor(j); j++) {
					neighbor(j).recommend(recommender);
				}
			}
		}
	}

	public static class Recommender extends Synchronizer {

		private final ExplicitGraph<MyNode> graph;
		private final int[] localPivots;
		private long used = 0;
		private long globalUsed = 0;

		public Recommender(ExplicitGraph<MyNode> graph, final int[] localPivots) {
			this.graph = graph;
			this.localPivots = localPivots;
		}

		public long GlobalUsed(long s) {
			return s + used;
		}

		public void run() {
			for (int pivot : localPivots) {
				((MyLocalNode) graph.node(pivot)).recommend(this);
			}
			barrier();
			globalUsed = GlobalUsed(0);
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
			System.err.println(Recommender.class.getName() + " <graph> [ <number of pivots, default 10> ]");
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
		print("Graph read in " + ConversionUtils.ns2sec(readTime) + "s, global size " + graph.getGlobalSize());

		// determine pivots
		final int numPivots = args.length > 2 ? Integer.parseInt(args[2]) : 10;
		int[] localPivots = new int[numPivots];
		for (int i = 0; i < localPivots.length; i++) {
			localPivots[i] = random.nextInt(graph.nodes());
		}

		// run recommender
		print("Starting recommender at " + numPivots + " pivots");
		final Recommender recommender = new Recommender(graph, localPivots);
		final long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(recommender);
		Runtime.getRuntime().barrier();
		final long time = System.nanoTime() - start;

		// print results
		print("The product was used " + recommender.globalUsed + " times");
		print("Recommender on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}

}
