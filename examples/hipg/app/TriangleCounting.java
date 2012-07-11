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
import hipg.Reduce;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;
import myutils.ConversionUtils;
import myutils.IOUtils;
import myutils.Serializable;

public class TriangleCounting {

	public static interface MyNode extends Node {
		public void hop1(TriangleCounter s, int srcId, int srcOwner);

		public void hop2(TriangleCounter s, int srcId, int srcOwner);

		public void triangle(TriangleCounter s);
	}

	public static class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {

		private int triangles = 0;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		final public void init(TriangleCounter c) {
			for (int i = 0; hasNeighbor(i); i++) {
				neighbor(i).hop1(c, reference(), owner());
			}
		}

		public void hop1(TriangleCounter c, final int srcId, final int srcOwner) {
			for (int i = 0; hasNeighbor(i); i++) {
				neighbor(i).hop2(c, reference(), owner());
			}
		}

		public void hop2(TriangleCounter c, final int srcId, final int srcOwner) {
			for (int i = 0; hasNeighbor(i); i++) {
				final int dstId = neighborId(i);
				final int dstOwner = neighborOwner(i);
				if (srcId == dstId && srcOwner == dstOwner) {
					triangle(c);
				}
			}
		}

		public void triangle(TriangleCounter c) {
			triangles++;
		}
	}

	public static class TriangleCounter extends Synchronizer {

		private ExplicitGraph<MyNode> graph;
		private int maxTrianglesCount;
		private TopTriangleCounts globalMaxTriangles;

		public TriangleCounter(ExplicitGraph<MyNode> graph, int maxTrianglesCount) {
			this.graph = graph;
			this.maxTrianglesCount = maxTrianglesCount;
		}

		@Reduce
		public TopTriangleCounts MaxTriangles(TopTriangleCounts maxTriangles) {
			if (maxTriangles == null) {
				maxTriangles = new TopTriangleCounts(maxTrianglesCount);
			}
			for (int i = 0; i < graph.nodes(); i++) {
				maxTriangles.consider((MyLocalNode) graph.node(i));
			}
			return maxTriangles;
		}

		public void run() {
			for (int i = 0; i < graph.nodes(); i++) {
				MyLocalNode node = (MyLocalNode) graph.node(i);
				node.init(this);
				if (i % 500 == 0)
					Runtime.nice();
			}
			barrier();
			globalMaxTriangles = MaxTriangles(null);
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
			System.err.println(TriangleCounter.class.getName() + " <graph>  [ <max top triangles, default 10> ]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}
		int topTrianglesCount = args.length > 2 ? Integer.parseInt(args[2]) : 10;

		// read graph
		print("Reading graph in format " + args[0] + " " + args[1]);
		final long readStart = System.nanoTime();
		final ExplicitGraph<MyNode> graph = hipg.format.GraphIO.read(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		final long readTime = System.nanoTime() - readStart;
		print("Graph read in " + ConversionUtils.ns2sec(readTime) + "s with " + graph.getGlobalSize());

		// run triangle counter
		print("Starting triangle counter");
		final TriangleCounter triangleCounter = new TriangleCounter(graph, topTrianglesCount);
		final long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(triangleCounter);
		Runtime.getRuntime().barrier();
		final long time = System.nanoTime() - start;

		// print results
		print(triangleCounter.globalMaxTriangles.toString());
		print("TriangleCounter on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}

	public final static class TopTriangleCounts implements Serializable {
		private long[] nodes = null;
		private int[] triangles = null;

		public TopTriangleCounts() {
		}

		public TopTriangleCounts(int maxTrianglesCount) {
			this.nodes = new long[maxTrianglesCount];
			this.triangles = new int[maxTrianglesCount];
			for (int i = 0; i < triangles.length; i++) {
				triangles[i] = -1;
			}
		}

		public void consider(MyLocalNode node) {
			final int t = node.triangles;
			if (t > triangles[0]) {
				int idx = 0;
				while (idx + 1 < triangles.length && triangles[idx + 1] < t) {
					triangles[idx] = triangles[idx + 1];
					nodes[idx] = nodes[idx + 1];
					idx++;
				}
				triangles[idx] = t;
				nodes[idx] = node.asReference();
			}
		}

		@Override
		public int length() {
			return IOUtils.bytesIntArray(triangles) + IOUtils.bytesLongArray(nodes);
		}

		@Override
		public void write(byte[] buf, int offset) {
			IOUtils.writeLongArray(nodes, buf, offset);
			offset += IOUtils.bytesLongArray(nodes);
			IOUtils.writeIntArray(triangles, buf, offset);
		}

		@Override
		public void read(byte[] buf, int offset) {
			nodes = IOUtils.readLongArray(buf, offset);
			offset += IOUtils.bytesLongArray(nodes);
			triangles = IOUtils.readIntArray(buf, offset);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			for (int i = triangles.length - 1; i >= 0; i--) {
				if (triangles[i] >= 0) {
					final String node = ExplicitNodeReference.referenceToString(nodes[i]);
					sb.append("Node " + node + " has " + triangles[i] + " triangles\n");
				}
			}
			return sb.toString();
		}
	}

	// keep it
	@SuppressWarnings("unused")
	private static TopTriangleCounts initTopTriangleCounts = new TopTriangleCounts(0);

}
