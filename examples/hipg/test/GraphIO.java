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

import myutils.ConversionUtils;
import myutils.MathUtils;
import hipg.Config;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;

public class GraphIO {

	public static double avgDegree(ExplicitGraph<Node> g) {
		long sum = 0;
		for (int i = 0; i < g.nodes(); i++)
			sum += g.node(i).outdegree();
		return (double) sum / (double) g.nodes();
	}

	public static double avgLocalDegree(ExplicitGraph<Node> g) {
		double sum = 0;
		long count = 0;
		for (int i = 0; i < g.nodes(); i++) {
			int dl = g.node(i).localOutdegree();
			int d = g.node(i).outdegree();
			if (d > 0) {
				sum += (double) dl / (double) d;
				count++;
			}
		}
		return (double) sum / (double) count;
	}

	public static double avgLocalInDegree(ExplicitGraph<Node> g) {
		double sum = 0;
		long count = 0;
		for (int i = 0; i < g.nodes(); i++) {
			int dl = g.node(i).localIndegree();
			int d = g.node(i).indegree();
			if (d > 0) {
				sum += (double) dl / (double) d;
				count++;
			}
		}
		return (double) sum / (double) count;
	}

	public static double avgInDegree(ExplicitGraph<Node> g) {
		long sum = 0;
		for (int i = 0; i < g.nodes(); i++)
			sum += g.node(i).indegree();
		return (double) sum / (double) g.nodes();
	}

	public static long localOutTransitionsExplicit(ExplicitGraph<?> g) {
		return g.getTransitions().getNumLocalTransitions();
	}

	public static long localInTransitionsExplicit(ExplicitGraph<?> g) {
		return g.getInTransitions().getNumLocalTransitions();
	}

	public static long remoteOutTransitionsExplicit(ExplicitGraph<?> g) {
		return g.getTransitions().getNumRemoteTransitions();
	}

	public static long remoteInTransitionsExplicit(ExplicitGraph<?> g) {
		return g.getInTransitions().getNumRemoteTransitions();
	}

	public static long localOutTransitions(ExplicitGraph<Node> g) {
		long sum = 0;
		for (int i = 0; i < g.nodes(); i++) {
			sum += g.node(i).localOutdegree();
		}
		return sum;
	}

	public static long localInTransitions(ExplicitGraph<Node> g) {
		long sum = 0;
		for (int i = 0; i < g.nodes(); i++)
			sum += g.node(i).localIndegree();
		return sum;
	}

	public static long remoteOutTransitions(ExplicitGraph<Node> g) {
		long sum = 0;
		for (int i = 0; i < g.nodes(); i++)
			sum += g.node(i).remoteOutdegree();
		return sum;
	}

	public static long remoteInTransitions(ExplicitGraph<Node> g) {
		long sum = 0;
		for (int i = 0; i < g.nodes(); i++)
			sum += g.node(i).remoteIndegree();
		return sum;
	}

	private static void print(String msg) {
		if (Runtime.getRank() == 0)
			System.err.println(msg);
	}

	private static void printAll(String msg) {
		System.err.println(Runtime.getRank() + ": " + msg);
	}

	public static void main(String[] args) {
		boolean transpose = false;
		boolean print = false;
		boolean printNeigh = false;
		int max = 15;
		String path = null, format = null;
		int poolSize = Config.POOLSIZE;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-transpose")) {
				transpose = true;
			} else if (args[i].equals("-print")) {
				print = true;
			} else if (args[i].equals("-printNeigh")) {
				printNeigh = true;
			} else if (args[i].equals("-max")) {
				max = Integer.parseInt(args[i + 1]);
				i++;
			} else if (format == null) {
				format = args[i];
			} else if (path == null) {
				path = args[i];
			}
		}
		if (path == null || poolSize < 0 || format == null) {
			System.out.println(GraphIO.class.getSimpleName() + " [-transpose] "
					+ "[-print] [-printNeigh] <format> <path>");
			System.exit(1);
		}
		// read graph
		print("Reading graph");
		long readStart = System.nanoTime();
		final ExplicitGraph<Node> g;
		try {
			if (transpose) {
				g = hipg.format.GraphIO.readUndirected(ExplicitLocalNode.class, Node.class, format, path, poolSize);
			} else {
				g = hipg.format.GraphIO.read(ExplicitLocalNode.class, Node.class, format, path, poolSize);
			}
		} catch (GraphCreationException e) {
			System.err.println("Could not read graph " + path + ": " + e.getMessage());
			return;
		}
		long readTime = System.nanoTime() - readStart;
		print("Graph read in " + ConversionUtils.ns2sec(readTime) + "s");

		// compute graph info
		long globalSize = g.getGlobalSize();

		double nodeProc = MathUtils.round3(100.0 * (double) g.nodes() / (double) g.getGlobalSize());
		double avgOutDegree = MathUtils.round3(avgDegree(g));
		double avgLocalOutDegree = MathUtils.round3(avgLocalDegree(g));
		long localOutTransitions = localOutTransitions(g);
		long remoteOutTransitions = remoteOutTransitions(g);
		long outTransitions = localOutTransitions + remoteOutTransitions;
		double localOutProc = MathUtils.proc(localOutTransitions, outTransitions);
		long localOutTransitionsChk = localOutTransitionsExplicit(g);
		long remoteOutTransitionsChk = remoteOutTransitionsExplicit(g);
		if (localOutTransitions != localOutTransitionsChk) {
			throw new RuntimeException("Local out transitions not correct: " + localOutTransitions + "!="
					+ localOutTransitionsChk);
		}

		double avgInDegree = 0, avgLocalInDegree = 0, localInProc = 0;
		long localInTransitions = 0, remoteInTransitions = 0, inTransitions = 0;

		if (transpose) {
			avgInDegree = MathUtils.round3(avgInDegree(g));
			avgLocalInDegree = MathUtils.round3(avgLocalInDegree(g));
			localInTransitions = localInTransitions(g);
			remoteInTransitions = remoteInTransitions(g);
			inTransitions = localInTransitions + remoteInTransitions;
			localInProc = MathUtils.proc(localInTransitions, inTransitions);
			long localInTransitionsChk = localInTransitionsExplicit(g);
			long remoteInTransitionsChk = remoteInTransitionsExplicit(g);
			if (localInTransitions != localInTransitionsChk)
				throw new RuntimeException("Local in transitions not correct: " + localInTransitions + "!="
						+ localInTransitionsChk);
			if (remoteOutTransitions != remoteOutTransitionsChk)
				throw new RuntimeException("Remote out " + "transitions not correct: " + remoteOutTransitions + "!="
						+ remoteOutTransitionsChk);
			if (remoteInTransitions != remoteInTransitionsChk)
				throw new RuntimeException("Remote in transitions " + "not correct: " + remoteInTransitions + "!="
						+ remoteInTransitionsChk);
		}

		print("Graph " + path + " partitioned in " + poolSize + " chunks");

		try {
			Thread.sleep(500 * Runtime.getRank());
		} catch (InterruptedException e) {
		}

		double exp = MathUtils.round3(1.0 / (double) Runtime.getPoolSize());
		printAll("NODES: " + g.nodes() + " nodes"
				+ (globalSize >= 0 ? " out of " + globalSize + " (" + nodeProc + "%)" : ""));
		printAll("TR-OUT: " + outTransitions + " transitions: " + localOutTransitions + " local (" + localOutProc
				+ "%) and " + remoteOutTransitions + " remote (" + (100.0 - localOutProc) + "%), avg deg "
				+ avgOutDegree + ", avg proportion of " + "local neighbors " + avgLocalOutDegree + " (expected " + exp
				+ ")");

		if (transpose) {
			printAll("TR-INC: " + inTransitions + " transitions: " + localInTransitions + " local (" + localInProc
					+ "%) and " + remoteInTransitions + " remote (" + (100.0 - localInProc) + "%), avg deg "
					+ avgInDegree + ", avg proportion of " + "local neighbors " + avgLocalInDegree + " (expected "
					+ exp + ")");
		}

		if (print) {
			for (int i = 0; i < max && i < g.nodes(); i++) {
				ExplicitLocalNode<?> n = g.node(i);
				System.out.println(Runtime.getRank() + ": Node(" + i + ") = " + n.toString());
				if (printNeigh) {
					for (int j = 0; n.hasNeighbor(j); j++) {
						System.out.println("  " + n.name() + " -> " + n.neighborId(j) + "@" + n.neighborOwner(j));
					}
					if (transpose) {
						for (int j = 0; n.hasInNeighbor(j); j++) {
							System.out.println("  " + n.name() + " <- " + n.inNeighborId(j) + "@"
									+ n.inNeighborOwner(j));
						}
					}
				}
			}
			if (g.nodes() > max) {
				System.out.println("...");
			}
		}
		if (Runtime.getRank() == 0) {
			try {
				Thread.sleep(400 * Runtime.getPoolSize());
			} catch (InterruptedException e) {
			}
			System.err.println(Runtime.getRank() + ": Done");
		}
	}
}
