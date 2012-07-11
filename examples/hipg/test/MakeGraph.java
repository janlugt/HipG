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

import hipg.format.GraphCreationException;
import hipg.format.GraphMaker;
import hipg.format.SVCII.SVCIIMaker;
import hipg.format.hip.HipMaker;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

import myutils.RandomPick;

public class MakeGraph {

	private static final Random random = new Random(System.currentTimeMillis());

	private static long makeLmLmTnGraph(GraphMaker maker, long[][] father, int m, int n, int depth)
			throws GraphCreationException {
		boolean hasChildren = (depth < n);
		long[][] layer = new long[m][m];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < m; j++) {
				long h = maker.addNode();
				layer[i][j] = h;
				if (father != null)
					maker.addTransition(father[i][j], h, 0);
				if (j > 0)
					maker.addTransition(layer[i][j - 1], h, 0);
				if (i > 0)
					maker.addTransition(layer[i - 1][j], h, 0);
				if (i == m - 1)
					maker.addTransition(layer[m - 1][j], layer[0][j], 0);
			}
			maker.addTransition(layer[i][m - 1], layer[i][0], 0);
		}
		long root = layer[0][0];
		if (hasChildren) {
			makeLmLmTnGraph(maker, layer, m, n, depth + 1);
			makeLmLmTnGraph(maker, layer, m, n, depth + 1);
		}
		return root;
	}

	private static long makeLimLon(GraphMaker maker, int m, int n) throws GraphCreationException {
		long root = 0;
		long[][][] layer = new long[m][n][n];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < m; j++) {
				for (int k = 0; k < n; k++) {
					for (int l = 0; l < n; l++) {
						long h = maker.addNode();
						if (i == 0 && j == 0 && k == 0 && l == 0)
							root = h;
						if (j > 0)
							maker.addTransition(layer[j - 1][k][l], h, 0);
						if (i > 0)
							maker.addTransition(layer[j][k][l], h, 0);
						if (k > 0)
							maker.addTransition(layer[j][k - 1][l], h, 0);
						if (l > 0)
							maker.addTransition(layer[j][k][l - 1], h, 0);
						if (k == n - 1)
							maker.addTransition(layer[j][k][l], layer[j][k][0], 0);
						if (l == n - 1)
							maker.addTransition(layer[j][k][l], layer[j][k][0], 0);
						layer[j][k][l] = h;
					}
				}
			}
		}
		return root;
	}

	private static long makeLmLmOTnGraph(GraphMaker maker, long father, boolean left, int m, int n, int depth)
			throws GraphCreationException {
		boolean hasChildren = (depth < n);
		boolean hasFather = (depth > 0);
		long[][] scc = new long[m][m];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < m; j++) {
				long h = maker.addNode();
				scc[i][j] = h;
				if (j > 0)
					maker.addTransition(scc[i][j - 1], h, 0);
				if (i > 0)
					maker.addTransition(scc[i - 1][j], h, 0);
				if (i == m - 1)
					maker.addTransition(scc[m - 1][j], scc[0][j], 0);
			}
			maker.addTransition(scc[i][m - 1], scc[i][0], 0);
		}
		long root = scc[0][0];
		if (hasFather) {
			if (left)
				maker.addTransition(father, root, 0);
			else
				maker.addTransition(root, father, 0);
		}
		if (hasChildren) {
			makeLmLmOTnGraph(maker, root, true, m, n, depth + 1);
			makeLmLmOTnGraph(maker, root, false, m, n, depth + 1);
		}
		return root;
	}

	private static long makeLmLmBTnGraph(final GraphMaker maker, final long father, final int m, final int n,
			final int depth) throws GraphCreationException {
		final boolean hasChildren = (depth < n);
		final boolean hasFather = (depth > 0);
		long[][] scc = new long[m][m];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < m; j++) {
				long h = maker.addNode();
				scc[i][j] = h;
				if (j > 0)
					maker.addTransition(scc[i][j - 1], h, 0);
				if (i > 0)
					maker.addTransition(scc[i - 1][j], h, 0);
				if (m > 1 && i == m - 1)
					maker.addTransition(scc[m - 1][j], scc[0][j], 0);
			}
			if (m > 1)
				maker.addTransition(scc[i][m - 1], scc[i][0], 0);
		}
		long root = scc[0][0];
		if (hasFather) {
			maker.addTransition(father, root, 0);
		}
		if (hasChildren) {
			makeLmLmBTnGraph(maker, root, m, n, depth + 1);
			makeLmLmBTnGraph(maker, root, m, n, depth + 1);
		}
		return root;
	}

	/**
	 * Creates Loop(m) || Loop (m) || Tree(n) graph where '||' denotes cartesian product. Tree(n) is a tree of height n,
	 * and so it has 2^{n+1}-1 nodes. The entire graph has 2^{n+1}^n SCCs, each of size (m+1)^2. The tree is the
	 * quotient graph.
	 * 
	 * 
	 * @param maker
	 *            Graph maker
	 * @param m
	 *            Parameter m
	 * @param n
	 *            Parameter n
	 * @return Root node
	 * @throws GraphCreationException
	 */
	public static long makeLmLmTnGraph(GraphMaker maker, int m, int n) throws GraphCreationException {
		return makeLmLmTnGraph(maker, null, m, n, 0);
	}

	/**
	 * Creates binary tree OrderedTree(n) graph where each node is Loop(m) || Loop (m) graph where '||' denotes
	 * cartesian product. OTree(n) is a tree of height n, and so it has 2^{n+1}-1 nodes. For each node, one son is
	 * outgoing, and one son is incoming. The entire graph has 2^{n+1}^n SCCs, each of size (m+1)^2. The tree is the
	 * quotient graph. Note that not only directions of sons and father are different but also there is a lot less
	 * wiring: only one per father-son edge, while in LmLmTn there are (m+1)^2.
	 * 
	 * @param maker
	 *            Graph maker
	 * @param m
	 *            Parameter m
	 * @param n
	 *            Parameter n
	 * @return Root node
	 * @throws GraphCreationException
	 */
	public static long makeLmLmOTnGraph(GraphMaker maker, int m, int n) throws GraphCreationException {
		return makeLmLmOTnGraph(maker, -1, false, m, n, 0);
	}

	/**
	 * Creates binary tree BTree(n) graph where each node is Loop(m) || Loop (m) graph where '||' denotes cartesian
	 * product. BTree(n) is a tree of height n, and so it has 2^{n+1}-1 nodes. The entire graph has 2^{n+1}^n SCCs, each
	 * of size (m+1)^2. Both sons are outgoing. The tree is the quotient graph. Note that not only directions of sons
	 * and father are different but also there is a lot less wiring: only one per father-son edge, while in LmLmTn there
	 * are (m+1)^2.
	 * 
	 * @param maker
	 *            Graph maker
	 * @param m
	 *            Parameter m
	 * @param n
	 *            Parameter n
	 * @return Root node
	 * @throws GraphCreationException
	 */
	public static long makeLmLmBTnGraph(GraphMaker maker, int m, int n) throws GraphCreationException {
		return makeLmLmBTnGraph(maker, 0, m, n, 0);
	}

	/**
	 * Creates Erdos-Renyi(n,p) graph, that is a graph with n nodes and an edge (n1,n2) created with probability p.
	 * 
	 * @param n
	 *            Number of nodes to create
	 * @param p
	 *            Probability of creation of an edge
	 * @param maker
	 *            Graph maker
	 * 
	 * @throws GraphCreationException
	 */
	public static long makeErdosRenyiGraph(GraphMaker maker, long n, double p) throws GraphCreationException {
		long root = 0L;
		for (long i = 0; i < n; i++) {
			long h = maker.addNode();
			if (i == 0)
				root = h;
		}
		for (int s = 0; s < maker.segments(); s++) {
			for (int i = 0; i < maker.nodes(s); i++) {
				for (int r = 0; r < maker.segments(); r++) {
					for (int j = 0; j < maker.nodes(r); j++) {
						if (random.nextDouble() <= p) {
							maker.addTransition(s, i, r, j, 0);
						}
					}
				}
			}
		}
		return root;
	}

	static abstract class DegreeDistribution {
		public abstract double density(double x);

		public abstract double sample();
	}

	private static final class PowerLaw extends DegreeDistribution {
		private final double alpha;
		private final double gamma;
		private final long n;
		private final int gd;
		private double[] distribution;

		public PowerLaw(double alpha, double gamma, long n) {
			this.gamma = gamma;
			this.alpha = alpha;
			this.n = n;

			// compute max
			int max = 1;
			while ((double) n * density(max) >= 1.0)
				max++;
			max--;

			// compute distribution
			distribution = new double[max + 1];
			long sum = 0;
			for (int i = 2; i <= max; i++) {
				distribution[i] = density(i) * (double) n;
				sum += distribution[i];
			}
			distribution[1] = n - sum;
			distribution[0] = 0;

			gd = computeGoodDivisor();
		}

		public double density(double x) {
			return alpha / Math.pow(x, gamma);
		}

		private int computeGoodDivisor() {
			int gd = 1024;
			while (n % gd != 0 && gd >= 2)
				gd--;
			if (gd <= 1)
				gd = 0;
			return gd;
		}

		@Override
		public double sample() {
			final long point;
			if (n <= (long) Integer.MAX_VALUE)
				point = (long) random.nextInt((int) n);
			else if (gd > 1 && n / gd < (long) Integer.MAX_VALUE) {
				int m = (int) (n / gd);
				int bucket = random.nextInt(gd);
				int p = random.nextInt(m);
				point = (long) bucket * (long) m + (long) p;
			} else
				throw new RuntimeException(this + " distribution " + "cannot handle " + n + " nodes");

			long sum = 0;
			int i = 1;
			while (sum < point && i < distribution.length)
				sum += distribution[i++];

			return i - 1;
		}

		@Override
		public String toString() {
			return "PowerLaw(" + alpha + ", " + gamma + ")(" + n + ")";
		}
	}

	private static final class Normal extends DegreeDistribution {
		private final double mean;
		private final double sigma;

		public Normal(double mean, double sigma) {
			this.mean = mean;
			this.sigma = sigma;
		}

		@Override
		public double density(double x) {
			double power = -(x - mean) * (x - mean) / 2.0 / sigma / sigma;
			double factor = Math.sqrt(Math.PI * 2) * sigma;
			return Math.exp(power) / factor;
		}

		@Override
		public double sample() {
			double u1 = random.nextDouble();
			double u2 = random.nextDouble();
			while (u1 == 0.0)
				u1 = random.nextDouble();
			double x = Math.sqrt(-2.0 * Math.log(u1)) * Math.sin(2.0 * Math.PI * u2);
			return mean + x * sigma;
		}

	}

	private static class LogNormal extends DegreeDistribution {

		private final double mean;
		private final double sigma;

		public LogNormal(double mean, double sigma) {
			this.mean = mean;
			this.sigma = sigma;
		}

		@Override
		public double density(double x) {
			double lnx = Math.log(x);
			double power = -(lnx - mean) * (lnx - mean) / 2 / sigma / sigma;
			double normfact = Math.sqrt(2 * Math.PI);
			return Math.exp(power) / normfact / sigma / x;
		}

		@Override
		public double sample() {
			double u1 = random.nextDouble();
			double u2 = random.nextDouble();
			while (u1 == 0.0)
				u1 = random.nextDouble();
			double x = Math.sqrt(-2.0 * Math.log(u1)) * Math.sin(2.0 * Math.PI * u2);
			return Math.exp(mean + x * sigma);
		}

		@Override
		public String toString() {
			return "LogNorm(" + mean + "," + sigma + ")";
		}
	}

	public static long makeGraphFromDegreeDistribution(GraphMaker maker, int n, DegreeDistribution distribution,
			boolean verbose) throws GraphCreationException {

		// create nodes
		long nodesPerSegmentL = n / maker.segments();
		if (nodesPerSegmentL > Integer.MAX_VALUE)
			throw new GraphCreationException("makePowerLawDistr cannot handle " + n + " nodes (" + nodesPerSegmentL
					+ " nodes per segment)");

		int nodesPerSegment = (int) (n / maker.segments());
		int remainingNodes = n - maker.segments() * nodesPerSegment;
		for (int s = 0; s < maker.segments(); s++) {
			for (int j = 0; j < nodesPerSegment; j++)
				maker.addNode(s);
		}
		for (int s = 0; s < remainingNodes; s++)
			maker.addNode(s);

		// create degree distribution
		long degsum = 0;
		int[] degree = new int[1024];
		Arrays.fill(degree, 0);
		for (int i = 0; i < n; i++) {
			int deg = (int) Math.round(distribution.sample());
			if (deg >= Short.MAX_VALUE) {
				System.err.println("Warn! Cut number of edges from " + deg + " to " + Short.MAX_VALUE);
				deg = Short.MAX_VALUE;
			}
			if (deg >= degree.length) {
				int oldLen = degree.length;
				int newLen = deg + 1024;
				degree = Arrays.copyOf(degree, newLen);
				Arrays.fill(degree, oldLen, newLen, 0);
			}
			degree[deg]++;
			degsum += deg;
		}
		double avgdeg = (double) degsum / (double) n;

		// create the graph
		int segmentSizes[] = new int[maker.segments()];
		for (int s = 0; s < segmentSizes.length; s++)
			segmentSizes[s] = maker.nodes(s);
		RandomPick pickDegree = new RandomPick(random, degree, true);
		RandomPick pickSegment = new RandomPick(random, segmentSizes, false);
		for (int srcSeg = 0; srcSeg < maker.segments(); srcSeg++) {
			int nodes = maker.nodes(srcSeg);
			for (int srcNde = 0; srcNde < nodes; srcNde++) {
				int deg = pickDegree.next();
				for (int i = 0; i < deg; i++) {
					int dstSeg = pickSegment.next();
					int dstNde = random.nextInt(maker.nodes(dstSeg));
					maker.addTransition(srcSeg, srcNde, dstSeg, dstNde, 0);
				}
			}
		}

		if (verbose)
			System.out.println("Created graph with average out degree " + avgdeg);

		return 0;
	}

	private static void usage() {
		System.err
				.println("Usage: " + MakeGraph.class.getSimpleName() + "  [-v] [-h]  <format> <path> <graph details>");
		System.err.println("where:");
		System.err.println("  <format> can be 'hip' or 'svcii <segments>'");
		System.err.println("  <graph details> can be one of the following:");
		System.err.println("    -erdos n p -- Erdos-Renyi(n,p)");
		System.err.println("    -LmLmTn|llt m n -- Loop(m)^2||Tree(n)");
		System.err.println("    -LimLon|lat m n -- Line(m)^2||Loop(n)^2");
		System.err.println("    -LmLmBTn m n|bintree n -- Loop(m)^2||BinTree(n)");
		System.err.println("    -LmLmOTn m n|ordtree n -- Loop(m)^2||OrdTree(n)");
		System.err.println("    -power n gam   - degree distr ~ 1/k^gam");
		System.err.println("    -lognorm n mi sig  - degree distr ~ log N(mi,sig)");
		System.err.println("    -norm n mi sig - degree distr ~ N (mi,sig)");
		System.exit(1);
	}

	public static void main(String[] args) throws GraphCreationException {

		/* options */
		boolean verbose = false;
		boolean help = false;
		String path = null;
		int choices = 0;

		/* format options */
		boolean format_svcii = false;
		boolean format_hip = false;
		int svcii_segments = -1;
		int formats = 0;

		/* erdos graph options */
		boolean erdos = false;
		long erdos_n = 0;
		double erdos_p = 0;

		/* L*L*T* options */
		boolean llt = false;
		boolean bintree = false;
		boolean ordtree = false;
		boolean lat = false;
		int llt_m = 0;
		int llt_n = 0;

		/* degree distribution */
		boolean power = false;
		boolean log = false;
		boolean normal = false;
		int distr_n = 0;
		DegreeDistribution distr = null;

		for (int i = 0; i < args.length; i++) {
			if (args[i].startsWith("-")) {
				if (args[i].equals("-v") || args[i].equals("-verbose")) {
					verbose = true;
				} else if (args[i].equals("-h") || args[i].equals("-help")) {
					help = true;
				} else if (args[i].equals("-erdos")) {
					erdos = true;
					choices++;
					erdos_n = readLong("erdos/n", args, ++i);
					erdos_p = readDouble("erdos/p", args, ++i);
					if (erdos_p == 0) {
						System.err.println("WARN: erdos#p=0");
					} else if (erdos_p > 1) {
						erdos_p /= (double) erdos_n * (double) erdos_n;
						System.err.println("WARN: p>1, treated as " + "number of exapected edges, " + "effectively p="
								+ erdos_p);
					}
				} else if (((args[i].equals("-LmLmTn") || args[i].equals("-llt")) && (llt = true))
						|| ((args[i].equals("-LmLmBTn") || args[i].equals("-bintree")) && (bintree = true))
						|| ((args[i].equals("-LmLmOTn") || args[i].equals("-ordtree")) && (ordtree = true))
						|| ((args[i].equals("-LimLon") || args[i].equals("-lat")) && (lat = true))) {
					choices++;
					if (args[i].equals("-bintree") || args[i].equals("-ordtree")) {
						llt_m = 0;
					} else {
						llt_m = readInt("llt/m", args, ++i);
					}
					llt_n = readInt("llt/n", args, ++i);
				} else if (args[i].equals("-power")) {
					power = true;
					choices++;
					distr_n = readInt("power/n", args, ++i);
					double gamma = readDouble("power/gamma", args, ++i);
					distr = new PowerLaw(1.0, gamma, distr_n);
				} else if (args[i].equals("-lognorm")) {
					choices++;
					log = true;
					distr_n = readInt("log/n", args, ++i);
					double mean = readDouble("log/mean", args, ++i);
					double sigma = readDouble("log/sigma", args, ++i);
					distr = new LogNormal(mean, sigma);
				} else if (args[i].equals("-normal")) {
					normal = true;
					choices++;
					distr_n = readInt("norm/n", args, ++i);
					double mean = readDouble("norm/mean", args, ++i);
					double sigma = readDouble("norm/sigma", args, ++i);
					distr = new Normal(mean, sigma);
				} else {
					System.err.println("Unrecognized graph type: " + args[i]);
					usage();
				}
			} else if (args[i].toLowerCase().equals("hip")) {
				format_hip = true;
				formats++;
			} else if (args[i].toLowerCase().equals("svcii") || args[i].toLowerCase().equals("svc-ii")
					|| args[i].toLowerCase().equals("svc-2") || args[i].toLowerCase().equals("svc2")) {
				format_svcii = true;
				formats++;
				svcii_segments = readInt("svcII/segments", args, ++i);
			} else if (path == null) {
				path = args[i];
			} else {
				System.err.println("Unrecognized argument " + path);
			}
		}

		/* check */
		if (help)
			usage();
		if (formats == 0) {
			System.err.println("No format specified");
			usage();
		}
		if (formats > 1) {
			System.err.println("Too many formats specified");
			usage();
		}
		if (path == null) {
			System.err.println("No path specified");
			usage();
		} else if (new File(path).exists()) {
			System.err.println("Graph " + path + " exists");
			usage();
		}
		if (choices == 0) {
			System.err.println("Graph type not chosen");
			usage();
		}
		if (choices > 1) {
			System.err.println("More than one graph type chosen");
			usage();
		}
		if (format_svcii) {
			if ((erdos && erdos_n % svcii_segments != 0) || (power && distr_n % svcii_segments != 0)) {
				System.err.println("n must be multiplicity of segments");
				usage();
			}
		}

		/* debug */

		String formatStr = (format_hip ? "HIP" : ("SVC-II#" + svcii_segments));
		String graphStr = null;
		if (erdos) {
			graphStr = "Erdos-Renyi" + "(" + erdos_n + ", " + erdos_p + ")";
		} else if (llt) {
			graphStr = "LmLmTn/llt(" + llt_m + ", " + llt_n + ")";
		} else if (bintree) {
			graphStr = "LmLmBTn/bintree(" + llt_m + ", " + llt_n + ")";
		} else if (ordtree) {
			graphStr = "LmLmOTn/ordtree(" + llt_m + ", " + llt_n + ")";
		} else if (lat) {
			graphStr = "LimLon/lattice(" + llt_m + ", " + llt_n + ")";
		} else if (power || log || normal) {
			graphStr = "Degree distribution ~ " + distr;
		} else {
			graphStr = "???";
		}
		System.out.println("Making graph:");
		System.out.println("	Path = " + path);
		System.out.println("	Format = " + formatStr);
		System.out.println("	Graph = " + graphStr);

		/* create graph maker */
		final GraphMaker maker;
		if (format_svcii) {
			maker = new SVCIIMaker(path, svcii_segments, verbose);
		} else if (format_hip) {
			maker = new HipMaker(path, verbose);
		} else {
			maker = null;
		}

		/* create the graph */
		final long startTime = System.nanoTime();
		long root = 0L;
		if (erdos) {
			double p = (erdos_p >= 0 && erdos_p <= 1 ? erdos_p : erdos_p / (double) erdos_n / (double) erdos_n);
			root = makeErdosRenyiGraph(maker, erdos_n, p);
		} else if (llt) {
			root = makeLmLmTnGraph(maker, llt_m + 1, llt_n);
		} else if (lat) {
			root = makeLimLon(maker, llt_m + 1, llt_n + 1);
		} else if (bintree) {
			root = makeLmLmBTnGraph(maker, llt_m + 1, llt_n);
		} else if (ordtree) {
			root = makeLmLmOTnGraph(maker, llt_m + 1, llt_n);
		} else if (power || log || normal) {
			root = makeGraphFromDegreeDistribution(maker, distr_n, distr, verbose);
		}
		maker.finish(root);
		long time = System.nanoTime() - startTime;

		/* debug */
		System.out.println("Graph created in " + ns2sec(time) + "s");
		System.out.println("States = " + maker.getGlobalStateCount() + " (" + maker.getGlobalStateCountMin() + " .. "
				+ maker.getGlobalStateCountMax() + ")");
		System.out.println("Transitions = " + maker.getGlobalTransitionsCount() + " ("
				+ maker.getGlobalTransitionsCountMin() + " .. " + maker.getGlobalTransitionsCountMax() + ")");
	}

	private static int readInt(String name, String[] args, int i) {
		if (i < 0 || i >= args.length) {
			System.err.println(name + " not specified");
			usage();
		}
		try {
			int k = Integer.parseInt(args[i]);
			if (k < 0) {
				System.err.println(name + " cannot be negative");
				usage();
			}
			return k;
		} catch (NumberFormatException e) {
			System.err.println(name + "(" + args[i] + ") could not be parsed");
			usage();
			return -1;
		}
	}

	private static long readLong(String name, String[] args, int i) {
		if (i < 0 || i >= args.length) {
			System.err.println(name + " not specified");
			usage();
		}
		try {
			long l = Long.parseLong(args[i]);
			if (l < 0) {
				System.err.println(name + " cannot be negative");
				usage();
			}
			return l;
		} catch (NumberFormatException e) {
			System.err.println(name + "(" + args[i] + ") could not be parsed");
			usage();
			return -1;
		}
	}

	private static double readDouble(String name, String[] args, int i) {
		if (i < 0 || i >= args.length) {
			System.err.println(name + " not specified");
			usage();
		}
		try {
			double d = Double.parseDouble(args[i]);
			if (d < 0) {
				System.err.println(name + " cannot be negative");
				usage();
			}
			return d;
		} catch (NumberFormatException e) {
			System.err.println(name + "(" + args[i] + ") could not be parsed");
			usage();
			return -1;
		}
	}

	public static final double ns2sec(long ms) {
		return round((double) ms / 1000000000.0);
	}

	public static final double round(double d) {
		return (double) Math.round(d * 1000.0d) / 1000.0;
	}

}
