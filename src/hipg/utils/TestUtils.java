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

package hipg.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import myutils.tuple.pair.FastIntPair;
import hipg.Graph;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.format.synthetic.AbstractSyntheticGraphMaker;
import hipg.format.synthetic.Partition;
import hipg.format.synthetic.SyntheticGraph;
import hipg.graph.ExplicitLocalNode;

public class TestUtils {

	public static class TestSyntheticGraphMaker implements AbstractSyntheticGraphMaker<Node, ExplicitLocalNode<Node>> {
		private final int nodes;
		private int createdNodes = 0;
		private int[][] adjacencyMatrix;
		private int[] degrees;
		private int[] indegrees;
		private boolean transpose = false;
		private boolean checkOrder;
		private int lastAddedSrc = -1;

		public TestSyntheticGraphMaker(SyntheticGraph sg) {
			this(sg, (int) sg.estimateGlobalNodes());
		}

		public TestSyntheticGraphMaker(SyntheticGraph sg, int nodes) {
			this.nodes = nodes;
			this.adjacencyMatrix = new int[nodes][nodes];
			this.degrees = new int[nodes];
			this.indegrees = new int[nodes];
			for (int i = 0; i < nodes; ++i) {
				for (int j = 0; j < nodes; ++j) {
					adjacencyMatrix[i][j] = 0;
				}
			}
			checkOrder = sg.transitionsPerNodeCreatedSequentially();
		}

		public int get(int i, int j) {
			if (i < 0 || j < 0 || i >= createdNodes || j >= createdNodes) {
				return -1;
			}
			return adjacencyMatrix[i][j];
		}

		public int[] get(int i) {
			return adjacencyMatrix[i];
		}

		public int[][] get() {
			return adjacencyMatrix;
		}

		@Override
		public long addNode() {
			int id = createdNodes++;
			myutils.test.TestUtils.assertLe(createdNodes, nodes);
			return id;
		}

		@Override
		public void addTransition(long src, long dst) throws GraphCreationException {
			final int srcOwner = FastIntPair.getFirst(src);
			final int srcId = FastIntPair.getSecond(src);
			final int dstOwner = FastIntPair.getFirst(dst);
			final int dstId = FastIntPair.getSecond(dst);
			addTransition(srcOwner, srcId, dstOwner, dstId);
		}

		@Override
		public void addTransition(int srcOwner, int srcId, int dstOwner, int dstId) throws GraphCreationException {
			assert (srcOwner == 0);
			assert (dstOwner == 0);
			myutils.test.TestUtils.assertLt(srcId, nodes);
			myutils.test.TestUtils.assertLt(dstId, nodes);
			if (checkOrder && srcId != lastAddedSrc && degrees[srcId] > 0) {
				throw new RuntimeException("Incorrect order of adding: previously added transitions to " + srcId);
			}
			// System.err.println(srcId + " -> " + dstId);
			degrees[srcId]++;
			indegrees[dstId]++;
			adjacencyMatrix[srcId][dstId]++;
			lastAddedSrc = srcId;
		}

		@Override
		public Graph<Node> create(SyntheticGraph sg) throws GraphCreationException {
			return null;
		}

		@Override
		public int numNodes(int owner) {
			return (owner == 0 ? createdNodes : -1);
		}

		@Override
		public int numNodes() {
			return createdNodes;
		}

		@Override
		public long totalNumNodes() {
			return createdNodes;
		}

		@Override
		public int getRank() {
			return 0;
		}

		@Override
		public int getPoolSize() {
			return 1;
		}

		@Override
		public boolean hasTranspose() {
			return transpose;
		}

		public void setTranspose(boolean transpose) {
			this.transpose = transpose;
		}

		@Override
		public Partition getPartition() {
			return null;
		}

		public int degree(int srcId) {
			return degrees[srcId];
		}

		public int indegree(int srcId) {
			return indegrees[srcId];
		}

		public long totalNumTransitions() {
			int sum = 0;
			for (int i = 0; i < nodes; ++i) {
				sum += degree(i);
			}
			return sum;
		}

		public double averageDegree() {
			return (double) totalNumTransitions() / (double) nodes;
		}

		public double estimateDegreeStandardDeviation(double average) {
			double sum = 0;
			for (int i = 0; i < nodes; ++i) {
				int degree = degree(i);
				double diff = degree - average;
				sum += diff * diff;
			}
			return Math.sqrt(sum / (double) nodes);
		}

		public int multipleTransitions() {
			int multiples = 0;
			for (int i = 0; i < nodes; ++i) {
				for (int j = 0; j < nodes; ++j) {
					int t = adjacencyMatrix[i][j];
					if (t > 1) {
						multiples += t - 1;
					}
				}
			}
			return multiples;
		}

		public void toDot(String fileName) throws IOException {
			BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
			out.write("digraph g {\n");
			for (int i = 0; i < createdNodes; ++i) {
				for (int j = 0; j < createdNodes; ++j) {
					int edge = get(i, j);
					if (edge > 0) {
						out.write("node" + i + " -> node" + j + ";\n");
					}
				}
			}
			out.write("}\n");
			out.close();
		}
	}
}
