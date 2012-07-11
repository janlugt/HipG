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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import myutils.ConversionUtils;
import myutils.MathUtils;
import hipg.Config;
import hipg.Node;
import hipg.format.GraphIO;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;

/**
 * Prints some information about a graph: average in/out-degree, and a small subset of nodes.
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public class GraphInfo {

	private static void printGraphSummary(ExplicitGraph<Node> g, boolean transpose, int printMax) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < g.nodes() && i < printMax; i++) {
			ExplicitLocalNode<Node> n = g.node(i);
			sb.append("   Node(" + i + "): out(" + n.outdegree() + ") = ");
			for (int j = 0; j < n.outdegree() && j < printMax; j++) {
				if (j > 0) {
					sb.append(" ");
				}
				sb.append(n.neighborId(j));
				if (Config.POOLSIZE > 1) {
					sb.append("@");
					sb.append(n.neighborOwner(j));
				}
			}
			if (n.outdegree() > printMax) {
				sb.append("...");
			}
			if (transpose) {
				sb.append(", in(" + n.indegree() + ") = ");
				for (int j = 0; j < n.indegree() && j < printMax; j++) {
					if (j > 0) {
						sb.append(" ");
					}
					sb.append(n.inNeighborId(j));
					if (Config.POOLSIZE > 1) {
						sb.append("@");
						sb.append(n.inNeighborOwner(j));
					}
				}
				if (n.indegree() > printMax) {
					sb.append("...");
				}
			}
			sb.append("\n");
		}
		printAll(sb.toString());
	}

	private static void graphToDot(ExplicitGraph<Node> g, String fileName, boolean withTranspose) throws IOException {
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(fileName));
			if (Runtime.getRank() == 0) {
				out.write("digraph g {\n");
			}
			for (int index = 0; index < g.nodes(); ++index) {
				final ExplicitLocalNode<Node> node = g.node(index);
				final String name = String.valueOf(index)
						+ (Config.POOLSIZE == 1 ? "" : String.valueOf(Runtime.getRank()));
				for (int j = 0; j < node.outdegree(); ++j) {
					final String neighborName = String.valueOf(node.neighborId(j))
							+ (Config.POOLSIZE == 1 ? "" : String.valueOf(node.neighborOwner(j)));
					out.write(name + " -> " + neighborName + ";\n");
				}
				if (withTranspose) {
					for (int j = 0; j < node.indegree(); ++j) {
						final String neighborName = String.valueOf(node.inNeighborId(j))
								+ (Config.POOLSIZE == 1 ? "" : String.valueOf(node.neighborOwner(j)));
						out.write(name + " -> " + neighborName + " [color=red];\n");
					}
				}
			}
			if (Runtime.getRank() == Config.POOLSIZE - 1) {
				out.write("}\n");
			}
		} catch (IOException e) {
			System.err.println("Could not write file " + fileName + ": " + e.getMessage());
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			// Print usage and exit.
			System.err.println(GraphInfo.class.getName() + " <graph> [-transpose] [-print <max>]"
					+ " [-dot <file>] [-dot-transpose <file>]");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		boolean transpose = false;
		boolean print = false;
		int printMax = 20;
		String dotFile = null;
		String transposeDotFile = null;
		for (int i = 2; i < args.length; i++) {
			final String arg = args[i];
			if ("-transpose".equals(arg)) {
				transpose = true;
			} else if ("-print".equals(arg)) {
				print = true;
				++i;
				if (i < args.length) {
					printMax = Integer.parseInt(args[i]);
				} else {
					throw new RuntimeException("No print max specified");
				}
			} else if ("-dot".equals(arg)) {
				++i;
				if (i < args.length) {
					dotFile = args[i];
				} else {
					throw new RuntimeException("No dot file name specified");
				}
			} else if ("-dot-with-transpose".equals(arg)) {
				++i;
				if (i < args.length) {
					transposeDotFile = args[i];
				} else {
					throw new RuntimeException("Not dot file name specified");
				}
			} else {
				throw new RuntimeException("Unrecognized argument: " + args[i]);
			}
		}

		// read graph
		print("Reading graph in format " + args[0] + " " + args[1]);
		final long readStart = System.nanoTime();
		@SuppressWarnings("unchecked")
		final ExplicitGraph<Node> g = (ExplicitGraph<Node>) hipg.format.GraphIO.read(ExplicitLocalNode.class,
				Node.class, args[0], args[1], transpose, Config.POOLSIZE);
		final long readTime = System.nanoTime() - readStart;
		print("Graph read in " + ConversionUtils.ns2sec(readTime) + "s");

		// print info about the graph
		print("Global number of nodes " + g.getGlobalSize());
		try {
			Thread.sleep(100 * Runtime.getRank());
		} catch (InterruptedException e) {
		}
		printAll("Number of nodes: " + g.nodes());

		long transitions = 0;
		long inTransitions = 0;
		long sumOutdegree = 0;
		long sumIndegree = 0;
		for (int i = 0; i < g.nodes(); i++) {
			int outdegree = g.node(i).outdegree();
			int indegree = g.node(i).indegree();
			sumOutdegree += outdegree;
			sumIndegree += indegree;
			transitions += outdegree;
			inTransitions += indegree;
		}

		printAll("Number of transitions: " + transitions);
		printAll("Average out-degree: " + MathUtils.round3((double) sumOutdegree / (double) g.nodes()));

		if (transpose) {
			printAll("Number of incoming transitions: " + inTransitions);
			printAll("Average in-degree: " + MathUtils.round3((double) sumIndegree / (double) g.nodes()));
		}

		if (print) {
			printGraphSummary(g, transpose, printMax);
		}

		if (dotFile != null) {
			if (Config.POOLSIZE > 1) {
				dotFile += String.valueOf(Runtime.getRank());
			}
			dotFile += ".dot";
			printAll("Dumping the graph to " + dotFile);
			try {
				graphToDot(g, dotFile, false);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (transposeDotFile != null) {
			if (Config.POOLSIZE > 1) {
				dotFile += String.valueOf(Runtime.getRank());
			}
			transposeDotFile += ".dot";
			printAll("Dumping transposed graph to " + transposeDotFile);
			try {
				graphToDot(g, transposeDotFile, true);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void print(final String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(msg);
			System.out.flush();
		}
	}

	private static void printAll(final String msg) {
		System.out.print(Runtime.getRank());
		System.out.print(": ");
		System.out.println(msg);
		System.out.flush();
	}
}
