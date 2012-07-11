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

package hipg.test;

import hipg.Config;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import myutils.ConversionUtils;

public class CMVis {

	private final static int MAXX = 600;
	private final static int MAXY = 600;
	private final static long seed = System.nanoTime();
	private final static Random rand = new Random(seed);

	public static interface MyNode extends Node {
		public void send(Visualizer vis);

		public void position(Visualizer vis, float x, float y);

		public void compute(Visualizer vis);

		public void edge(Visualizer vis, int fromx, int fromy);
	}

	public static final class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {

		private float x = rand.nextFloat() * MAXX;
		private float y = rand.nextFloat() * MAXY;

		private float sumx = 0f;
		private float sumy = 0f;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		public void send(Visualizer vis) {
			for (int j = 0; hasNeighbor(j); j++)
				neighbor(j).position(vis, x, y);
			for (int j = 0; hasInNeighbor(j); j++)
				inNeighbor(j).position(vis, x, y);
		}

		public void position(Visualizer vis, float px, float py) {
			sumx += px;
			sumy += py;
		}

		public void compute(Visualizer vis) {
			float cmx = sumx / (float) (indegree() + outdegree());
			float cmy = sumy / (float) (indegree() + outdegree());
			x = (x + cmx) / 2f;
			y = (y + cmy) / 2f;
			sumx = 0f;
			sumy = 0f;
		}

		public void edge(Visualizer vis, int fromx, int fromy) {
			vis.graphics.drawLine(fromx, fromy, (int) x, (int) y);
		}

	}

	public static class Visualizer extends Synchronizer {

		private final ExplicitGraph<MyNode> g;
		private final BufferedImage image = new BufferedImage(MAXX, MAXY, BufferedImage.TYPE_BYTE_GRAY);
		private final Graphics2D graphics = image.createGraphics();
		private int[] finalImage = null;

		public Visualizer(ExplicitGraph<MyNode> g) {
			this.g = g;
		}

		private void print(String msg) {
			if (Runtime.getRank() == 0)
				System.err.println(msg);
		}

		private int[] CombineImage(int[] otherImage) {
			image.flush();
			int[] myImage = new int[MAXX * MAXY];
			for (int i = 0; i < MAXX; i++) {
				for (int j = 0; j < MAXY; j++) {
					int k = i * MAXX + j;
					myImage[k] = image.getRGB(i, j);
					if (otherImage != null)
						myImage[k] |= otherImage[k];
				}
			}
			return myImage;
		}

		private void drawPoints() {
			print("Drawing points");
			graphics.setColor(Color.black);
			for (int i = 0; i < g.nodes(); i++) {
				MyLocalNode n = (MyLocalNode) g.node(i);
				int x = (int) n.x;
				int y = (int) n.y;
				graphics.drawOval(x, y, 2, 2);
			}
			graphics.setColor(Color.lightGray);
		}

		private void drawEdges() {
			print("Drawing edges");
			for (int i = 0; i < g.nodes(); i++) {
				MyLocalNode n = (MyLocalNode) g.node(i);
				int x = (int) n.x;
				int y = (int) n.y;
				n.edge(this, x, y);
			}
		}

		@Override
		public void run() {
			for (int step = 0; step < 2; step++) {
				print("Step " + step);
				// send
				for (int i = 0; i < g.nodes(); i++) {
					((MyLocalNode) g.node(i)).send(this);
					if (i % 100 == 0)
						Runtime.nice();
				}
				barrier();
				// compute
				for (int i = 0; i < g.nodes(); i++)
					((MyLocalNode) g.node(i)).compute(this);
				barrier();
			}

			// draw points
			drawPoints();
			barrier();

			// draw edges
			drawEdges();
			barrier();

			// combine images
			print("Creating the image");
			finalImage = CombineImage(null);
		}
	}

	private static void print(String msg) {
		if (Runtime.getRank() == 0)
			System.err.println(msg);
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			System.out.println(CMVis.class.getName() + " <format> <path>");
			System.exit(1);
		}

		// read graph
		print("Reading graph");
		long readStart = System.nanoTime();
		final ExplicitGraph<MyNode> g = hipg.format.GraphIO.read(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		long readTime = System.nanoTime() - readStart;
		print("Graph read in " + ConversionUtils.ns2sec(readTime) + "s");

		// run PageRank
		print("Starting " + CMVis.class.getSimpleName());
		Visualizer vis = new Visualizer(g);
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(vis);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;
		print(CMVis.class.getSimpleName() + " done");

		// create image
		String path = "graph.bmp";
		String format = path.substring(path.lastIndexOf('.') + 1).toUpperCase();
		BufferedImage bImage = new BufferedImage(MAXX, MAXY, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < MAXX; x++) {
			for (int y = 0; y < MAXY; y++) {
				bImage.setRGB(x, y, vis.finalImage[x * MAXX + y]);
			}
		}
		try {
			ImageIO.write(bImage, format, new File(path));
		} catch (IOException e) {
			System.err.println("Could not write file " + path + " in format " + format + ": " + e.getMessage());
			System.exit(1);
		}

		// print results
		print(CMVis.class.getName() + " on " + Runtime.getPoolSize() + " processors took "
				+ ConversionUtils.ns2sec(time) + "s");

	}
}
