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
import hipg.Reduce;
import hipg.app.Bigraph.BiLocalNode;
import hipg.app.Bigraph.BiNode;
import hipg.app.Bigraph.Bidi;
import hipg.app.utils.Histogram;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import myutils.ConversionUtils;

/**
 * Computes histogram (distribution) of the outgoing / incoming degree of nodes.
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public class DegreeHistogram {

	private static final class Histogrammer extends Synchronizer {

		private final ExplicitGraph<BiNode> graph;
		private final Histogram outHistogram;
		private final Histogram inHistogram;

		private Histogram globalOutHistogram;
		private Histogram globalInHistogram;

		public Histogrammer(ExplicitGraph<BiNode> graph, final boolean transpose) {
			this.graph = graph;
			this.outHistogram = new Histogram();
			this.inHistogram = transpose ? new Histogram() : null;
		}

		@Reduce
		public Histogram GlobalOutHistogram(Histogram h) {
			return outHistogram.add(h);
		}

		@Reduce
		public Histogram GlobalInHistogram(Histogram h) {
			return inHistogram.add(h);
		}

		@Override
		public void run() {
			for (int i = 0; i < graph.nodes(); i++) {
				final ExplicitLocalNode<BiNode> node = graph.node(i);
				final int outdegree = node.outdegree();
				outHistogram.add(outdegree);
				if (inHistogram != null) {
					final int indegree = node.indegree();
					inHistogram.add(indegree);
				}
			}
			barrier();
			globalOutHistogram = GlobalOutHistogram(null);
			barrier();
			if (inHistogram != null) {
				globalInHistogram = GlobalInHistogram(null);
			}
		}
	}

	private static void printHistogram(final Histogram histogram, final String description) {
		print("Histogram " + description + " computed:");
		print(histogram.toString("Found", "nodes with degree", null, 10));
		final String filePath = description.replace(" ", ".") + ".dat";
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(new File(filePath)));
			out.write("# === " + description + " === \n");
			out.write("# historgram written on " + new Date() + " by HipG\n");
			out.write("# min = " + histogram.minElement() + "\n");
			out.write("# max element = " + histogram.maxElement() + "\n");
			final double avg = histogram.avgElement();
			out.write("# max element = " + avg + "\n");
			out.write("# stdev element = " + histogram.stdevElement(avg) + "\n");
			out.write("# unique elements = " + histogram.unique() + "\n");
			out.write("# all elements = " + histogram.count() + "\n");
			out.write("# count degree \n");
			out.write(histogram.toString(null, null, null));
			out.write("\n");
			print("File " + filePath + " written OK");
		} catch (IOException e) {
			print("Could not write to file: " + filePath + ": " + e.getMessage());
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (Throwable t) {
				}
			}
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
			System.err.println(DegreeHistogram.class.getName() + " <graph> [-transpose] [<file name>]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		boolean transpose = false;
		String fileNameBase = null;
		for (int i = 2; i < args.length; i++) {
			if (args[i].equals("-transpose")) {
				transpose = true;
			} else if (fileNameBase == null) {
				fileNameBase = args[i];
			} else {
				throw new RuntimeException("Unrecognized argument: " + args[i]);
			}
		}
		if (fileNameBase == null) {
			fileNameBase = "degree";
		}

		// read graph
		print("Reading graph in format " + args[0] + " " + args[1]);
		final long readStart = System.nanoTime();
		final ExplicitGraph<BiNode> graph = hipg.format.GraphIO.read(BiLocalNode.class, BiNode.class, args[0], args[1],
				Config.POOLSIZE);
		final long readTime = System.nanoTime() - readStart;
		print("Graph with " + graph.getGlobalSize() + " nodes read in " + ConversionUtils.ns2sec(readTime) + "s");

		// compute transpose
		if (transpose) {
			final Bidi bidi = new Bidi(graph, true, true);
			final long startBidi = System.nanoTime();
			Runtime.getRuntime().spawnAll(bidi);
			Runtime.getRuntime().barrier();
			final long timeBidi = System.nanoTime() - startBidi;
			print("Computing transpose took " + ConversionUtils.ns2sec(timeBidi) + "s");
		}

		// run histogrammer
		print("Computing degree histogram");
		final Histogrammer h = new Histogrammer(graph, transpose);
		final long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(h);
		Runtime.getRuntime().barrier();
		final long time = System.nanoTime() - start;

		// print results
		printHistogram(h.globalOutHistogram, fileNameBase + ".out");
		if (transpose) {
			printHistogram(h.globalInHistogram, fileNameBase + ".in");
		}
		print("Histogrammer on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}
}
