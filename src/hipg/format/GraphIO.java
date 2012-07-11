/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam.
 * Written by Ela Krepska e.l.krepska@vu.nl.
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
 *
 */

package hipg.format;

import hipg.Config;
import hipg.LocalNode;
import hipg.Node;
import hipg.format.SVCII.SVCIIReader;
import hipg.format.hip.HipReader;
import hipg.format.synthetic.BiLine;
import hipg.format.synthetic.BiRing;
import hipg.format.synthetic.Clique;
import hipg.format.synthetic.DegreeDistributionGraph;
import hipg.format.synthetic.HypercubePartition;
import hipg.format.synthetic.Lattice;
import hipg.format.synthetic.LatticeOfSubgraphs;
import hipg.format.synthetic.LimLon;
import hipg.format.synthetic.Line;
import hipg.format.synthetic.LineOfSubgraphs;
import hipg.format.synthetic.LmLmTn;
import hipg.format.synthetic.Partition;
import hipg.format.synthetic.RandomPartition;
import hipg.format.synthetic.RandomizedTree;
import hipg.format.synthetic.Ring;
import hipg.format.synthetic.RingWithShortcuts;
import hipg.format.synthetic.SlowErdos;
import hipg.format.synthetic.SyntheticGraph;
import hipg.format.synthetic.SyntheticGraphMaker;
import hipg.format.synthetic.Tree;
import hipg.format.synthetic.TreeOfSubgraphs;
import hipg.format.synthetic.petrinet.PetriNet;
import hipg.format.synthetic.petrinet.PetriNetStateSpace;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;
import hipg.runtime.Statistics;

import java.util.Random;
import java.util.Vector;

import myutils.StringUtils;
import myutils.probability.ApproximateBinomialDistribution;
import myutils.probability.BinomialDistribution;
import myutils.probability.DiscreteUniformDistribution;
import myutils.probability.LogNormalDistribution;
import myutils.probability.NormalDistribution;
import myutils.probability.ParetoDistribution;
import myutils.probability.ProbabilityDistribution;
import myutils.probability.UniformDistribution;
import myutils.system.MonitorThread;
import myutils.tuple.pair.IntPair;
import myutils.tuple.pair.LongPair;
import myutils.tuple.pair.Pair;
import myutils.tuple.quadruple.IntQuadruple;
import myutils.tuple.quintuple.Quintuple;
import myutils.tuple.triple.IntTriple;
import myutils.tuple.triple.Triple;

public class GraphIO {

	private static enum GenerationType {
		SIMPLE("Simple synthetic graphs"), CARTESIAN("Cartesian products of simple graphs"), COMBINATION(
				"Combinations of simple graphs"), RANDOM("Synthetic random graphs (n=#nodes, s=#seed)"), FROM_FILE(
				"Graphs read from files");

		private final String description;

		private GenerationType(String description) {
			this.description = description;
		}
	}

	private static enum SyntheticGraphType {
		// Simple graphs.
		BinTree("h", "Full binary tree", "h=height", GenerationType.SIMPLE),
		Tree("h:b", "Full tree", "h=height, b=branch", GenerationType.SIMPLE),
		RandomizedTree("h:seed", "Randomized binary tree", "h=height", GenerationType.SIMPLE),
		Line("n", "Linked list", "n=#nodes", GenerationType.SIMPLE),
		BiLine("n", "Bidirectional linked list", "n=#nodes", GenerationType.SIMPLE),
		Ring("n", "Directed ring/loop", "n=#nodes", GenerationType.SIMPLE),
		BiRing("n", "Bi-directional ring", "n=#nodes", GenerationType.SIMPLE),
		Clique("n", "Clique", "n=#nodes", GenerationType.SIMPLE),
		Lattice("rows:cols", "Line(rows) x Line(cols)", "", GenerationType.SIMPLE),
		LatticeLoopedRows("rows:cols", "Line(rows) x Loop(cols)", "", GenerationType.SIMPLE),
		LatticeLoopedCols("rows:cols", "Loop(rows) x Line(cols)", "", GenerationType.SIMPLE),
		LatticeLooped("rows:cols", "Loop(rows) x Loop(cols)", "", GenerationType.SIMPLE),

		// Cartesian products of simple graphs.
		LmLmTn("m:n", "LatticeLooped(m,m) x BinTree(n)", "", GenerationType.CARTESIAN),
		LimLon("m:n", "LatticeLooped(m,m) x Lattice(n)", "", GenerationType.CARTESIAN),

		// Trees of simple graphs.
		TreeOfLattices("H:n", "BinTree(H) of LatticeLooped(n, n)", "", GenerationType.COMBINATION),
		TreeOfLines("H:n", "BinTree(H) of Line(n)", "", GenerationType.COMBINATION),
		TreeOfBiLines("H:n", "BinTree(H) of BiLine(n)", "", GenerationType.COMBINATION),
		TreeOfCliques("H:n", "BinTree(H) of Clique(n)", "", GenerationType.COMBINATION),
		TreeOfRings("H:n", "BinTree(H) of Ring(n)", "", GenerationType.COMBINATION),
		TreeOfBiRings("H:n", "BinTree(H) of BiRing(n)", "", GenerationType.COMBINATION),
		TreeOfTrees("H:h:b", "BinTree(H) of Tree(h, b)", "", GenerationType.COMBINATION),

		// Lattices (not looped) of simple graphs.
		LatticeOfLattices("M:N:n", "Lattice(M, N) of LatticeLooped(n, n)", "", GenerationType.COMBINATION),
		LatticeOfLines("M:N:n", "Lattice(M, N) of Line(n)", "", GenerationType.COMBINATION),
		LatticeOfBiLines("M:N:n", "Lattice(M, N) of BiLine(n)", "", GenerationType.COMBINATION),
		LatticeOfCliques("M:N:n", "Lattice(M, N) of Clique(n)", "", GenerationType.COMBINATION),
		LatticeOfRings("M:N:n", "Lattice(M, N) of Ring(n)", "", GenerationType.COMBINATION),
		LatticeOfBiRings("M:N:n", "Lattice(M, N) of BiRing(n)", "", GenerationType.COMBINATION),
		LatticeOfTrees("M:N:h:b", "Lattice(M, N) of Tree(h, b)", "", GenerationType.COMBINATION),
		LatticeOneRowConnectedOfLattices(
				"M:N:n",
				"LatticeORC(M, N) of LatticeLooped(n, n)",
				"ORC = OneRowConnected",
				GenerationType.COMBINATION),

		// Lattice with M rows and N columns, such that the first row is connected (not looped) like a line,
		// the following rows are not connected at all and each collumn is connected (not looped).
		LatticeOneRowConnectedOfLines("M:N:n", "LatticeORC(M, N) of Line(n)", "", GenerationType.COMBINATION),
		LatticeOneRowConnectedOfBiLines("M:N:n", "LatticeORC(M, N) of BiLine(n)", "", GenerationType.COMBINATION),
		LatticeOneRowConnectedOfCliques("M:N:n", "LatticeORC(M, N) of Clique(n)", "", GenerationType.COMBINATION),
		LatticeOneRowConnectedOfRings("M:N:n", "LatticeORC(M, N) of Ring(n)", "", GenerationType.COMBINATION),
		LatticeOneRowConnectedOfBiRings("M:N:n", "LatticeORC(M, N) of BiRing(n)", "", GenerationType.COMBINATION),
		LatticeOneRowConnectedOfTrees("M:N:h:b", "LatticeORC(M, N) of Tree(h, b)", "", GenerationType.COMBINATION),

		// Lattice with M rows and N columns, such that the first row is connected and looped like a line,
		// the following rows are not connected at all and each column is connected (not looped).
		LatticeOneRowLoopedOfLattices(
				"M:N:n",
				"LatticeORL(M, N) of LatticeLooped(n, n)",
				"ORL = OneRowLooped",
				GenerationType.COMBINATION),
		LatticeOneRowLoopedOfLines("M:N:n", "LatticeORL(M, N) of Line(n)", "", GenerationType.COMBINATION),
		LatticeOneRowLoopedOfBiLines("M:N:n", "LatticeORL(M, N) of BiLine(n)", "", GenerationType.COMBINATION),
		LatticeOneRowLoopedOfCliques("M:N:n", "LatticeORL(M, N) of Clique(n)", "", GenerationType.COMBINATION),
		LatticeOneRowLoopedOfRings("M:N:n", "LatticeORL(M, N) of Ring(n)", "", GenerationType.COMBINATION),
		LatticeOneRowLoopedOfBiRings("M:N:n", "LatticeORL(M, N) of BiRing(n)", "", GenerationType.COMBINATION),
		LatticeOneRowLoopedOfTrees("M:N:h:b", "LatticeORL(M, N) of Tree(h, b)", "", GenerationType.COMBINATION),

		// Trees of simple graphs.
		LatticeLoopedOfLattices("M:N:n", "LatticeLooped(M, N) of LatticeLooped(n, n)", "", GenerationType.COMBINATION),
		LatticeLoopedOfLines("M:N:n", "LatticeLooped(M, N) of Line(n)", "", GenerationType.COMBINATION),
		LatticeLoopedOfBiLines("M:N:n", "LatticeLooped(M, N) of BiLine(n)", "", GenerationType.COMBINATION),
		LatticeLoopedOfCliques("M:N:n", "LatticeLooped(M, N) of Clique(n)", "", GenerationType.COMBINATION),
		LatticeLoopedOfRings("M:N:n", "LatticeLooped(M, N) of Ring(n)", "", GenerationType.COMBINATION),
		LatticeLoopedOfBiRings("M:N:n", "LatticeLooped(M, N) of BiRing(n)", "", GenerationType.COMBINATION),
		LatticeLoopedOfTrees("M:N:h:b", "LatticeLooped(M, N) of Tree(h, b)", "", GenerationType.COMBINATION),

		// Lines (not looped ring) of simple graphs.
		LineOfLattices("M:n", "Line(M, N) of LatticeLooped(n, n)", "", GenerationType.COMBINATION),
		LineOfLines("M:n", "Line(M, N) of Line(n)", "", GenerationType.COMBINATION),
		LineOfBiLines("M:n", "Line(M, N) of BiLine(n)", "", GenerationType.COMBINATION),
		LineOfCliques("M:n", "Line(M, N) of Clique(n)", "", GenerationType.COMBINATION),
		LineOfRings("M:n", "Line(M, N) of Ring(n)", "", GenerationType.COMBINATION),
		LineOfBiRings("M:n", "Line(M, N) of BiRing(n)", "", GenerationType.COMBINATION),
		LineOfTrees("M:h:b", "Line(M, N) of Tree(h, b)", "", GenerationType.COMBINATION),

		// Random graphs.
		RingWithShortcuts("n:m", "Ring(n), each node m random outgoing edges", "", GenerationType.RANDOM),
		LogN("n:mean:sigma:seed:multiple", "Degrees ~ LogN(mean, sigma)", "", GenerationType.RANDOM),
		Uniform("n:min:max:seed:multiple", "Degrees ~ U(min, max)", "", GenerationType.RANDOM),
		DiscreteUniform("n:min:max:seed:multiple", "Degrees ~ U{min, .., max}", "", GenerationType.RANDOM),
		Normal("n:mean:sigma:seed:multiple", "Degrees ~ N(mean, sigma)", "", GenerationType.RANDOM),
		Pareto("n:xm:alpha:seed:multiple", "Degrees ~ P(xm, alpha)", "", GenerationType.RANDOM),
		SlowErdos("n:p:seed", "Erdos(n,p) graph", "p=edge probability", GenerationType.RANDOM),
		FastErdos("n:p:seed", "Degrees ~ Bin(n,p)", "p=edge probability", GenerationType.RANDOM),
		ApproximateErdos("n:p:seed", "Degrees ~ ApproxBin(n, p)", "p=edge probability", GenerationType.RANDOM),

		// Graphs read from files.
		PetriNetStateSpace("maxVal:path", "State space of a Petri net", "", GenerationType.FROM_FILE),
		BioPetriNetStateSpace(
				"maxVal:path",
				"State space of a Petri net with degradation",
				"",
				GenerationType.FROM_FILE),
		SvcII("path", "Stored graph (SVC-II format)", "", GenerationType.FROM_FILE),
		Hip("path", "Stored graph (HIP format)", "", GenerationType.FROM_FILE);

		private final String parametersShortDescription, humanReadableDescription, parametersLongDescription;
		private GenerationType kind;

		private SyntheticGraphType(String parametersShortDescription, String humanReadableDescription,
				String parametersLongDescription, GenerationType kind) {
			this.parametersShortDescription = parametersShortDescription;
			this.humanReadableDescription = humanReadableDescription;
			this.parametersLongDescription = parametersLongDescription;
			this.kind = kind;
		}
	};

	@SuppressWarnings("unchecked")
	public static <TNode extends Node> ExplicitGraph<TNode> read(
			@SuppressWarnings("rawtypes") final Class<? extends LocalNode> TLocalNodeClass,
			final Class<TNode> TNodeClass, String format, String details, final int poolSize)
			throws GraphCreationException {
		return read(TLocalNodeClass, TNodeClass, format, details, false, poolSize);
	}

	@SuppressWarnings("unchecked")
	public static <TNode extends Node> ExplicitGraph<TNode> readUndirected(
			@SuppressWarnings("rawtypes") final Class<? extends LocalNode> TLocalNodeClass,
			final Class<TNode> TNodeClass, String format, final String details, final int poolSize)
			throws GraphCreationException {
		return read(TLocalNodeClass, TNodeClass, format, details, true, poolSize);
	}

	public static <TNode extends Node, TLocalNode extends LocalNode<TNode>> ExplicitGraph<TNode> read(
			final Class<TLocalNode> TLocalNodeClass, final Class<TNode> TNodeClass, final String format,
			final String formatDetail, final boolean transpose, final int poolSize) throws GraphCreationException {
		if (format == null) {
			throw new RuntimeException("Format null");
		}
		final int rank = Runtime.getRank();
		final ExplicitGraph<TNode> g;

		if (format.equals("svc-ii") || format.equals("svcii")) {
			g = SVCIIReader.read(TLocalNodeClass, TNodeClass, formatDetail, rank, transpose, true);
		} else if (format.equals("hip")) {
			g = HipReader.read(TLocalNodeClass, TNodeClass, formatDetail, rank, Runtime.getPoolSize(), transpose);
		} else {
			SyntheticGraph sg = null;
			Partition partition = null;
			SyntheticGraphType graphType = SyntheticGraphType.valueOf(format);
			switch (graphType) {

			case BinTree: {
				final int height = readInt(formatDetail);
				sg = new Tree(height, 2);
				break;
			}

			case Tree: {
				final IntPair params = read2Ints(formatDetail);
				final int height = params.getFirst();
				final int branch = params.getSecond();
				sg = new Tree(height, branch);
				break;
			}

			case RandomizedTree: {
				final LongPair params = read2Longs(formatDetail);
				final long n = params.getFirst();
				final long seed = params.getSecond();
				sg = new RandomizedTree(n, new Random(seed));
				break;
			}

			case Line: {
				final long n = readLong(formatDetail);
				sg = new Line(n);
				break;
			}

			case BiLine: {
				final long n = readLong(formatDetail);
				sg = new BiLine(n);
				break;
			}

			case Ring: {
				final long n = readLong(formatDetail);
				sg = new Ring(n);
				break;
			}

			case BiRing: {
				final long n = readLong(formatDetail);
				sg = new BiRing(n);
				break;
			}

			case LmLmTn: {
				final IntPair params = read2Ints(formatDetail);
				final int m = params.getFirst();
				final int n = params.getSecond();
				sg = new LmLmTn(m, n);
				break;
			}

			case LimLon: {
				final IntPair params = read2Ints(formatDetail);
				final int m = params.getFirst();
				final int n = params.getSecond();
				sg = new LimLon(m, n);
				break;
			}

			case Clique: {
				final int n = readInt(formatDetail);
				sg = new Clique(n);
				break;
			}

			case Lattice: {
				final IntPair params = read2Ints(formatDetail);
				final int rows = params.getFirst();
				final int cols = params.getSecond();
				sg = new Lattice(rows, cols, false, false);
				break;
			}

			case LatticeLoopedRows: {
				final IntPair params = read2Ints(formatDetail);
				final int rows = params.getFirst();
				final int cols = params.getSecond();
				sg = new Lattice(rows, cols, true, false);
				break;
			}

			case LatticeLoopedCols: {
				final IntPair params = read2Ints(formatDetail);
				final int rows = params.getFirst();
				final int cols = params.getSecond();
				sg = new Lattice(rows, cols, false, true);
				break;
			}

			case LatticeLooped: {
				final IntPair params = read2Ints(formatDetail);
				final int rows = params.getFirst();
				final int cols = params.getSecond();
				sg = new Lattice(cols, rows, true, true);
				break;
			}

			case TreeOfLattices: {
				final IntPair params = read2Ints(formatDetail);
				final int H = params.getFirst();
				final int n = params.getSecond();
				sg = new TreeOfSubgraphs(H, 2, new Lattice(n, n, true, true));
				break;
			}

			case TreeOfLines: {
				final IntPair params = read2Ints(formatDetail);
				final int H = params.getFirst();
				final int n = params.getSecond();
				sg = new TreeOfSubgraphs(H, 2, new Line(n));
				break;
			}

			case TreeOfBiLines: {
				final IntPair params = read2Ints(formatDetail);
				final int H = params.getFirst();
				final int n = params.getSecond();
				sg = new TreeOfSubgraphs(H, 2, new BiLine(n));
				break;
			}

			case TreeOfCliques: {
				final IntPair params = read2Ints(formatDetail);
				final int H = params.getFirst();
				final int n = params.getSecond();
				sg = new TreeOfSubgraphs(H, 2, new Clique(n));
				break;
			}

			case TreeOfRings: {
				final IntPair params = read2Ints(formatDetail);
				final int H = params.getFirst();
				final int n = params.getSecond();
				sg = new TreeOfSubgraphs(H, 2, new Ring(n));
				break;
			}

			case TreeOfBiRings: {
				final IntPair params = read2Ints(formatDetail);
				final int H = params.getFirst();
				final int n = params.getSecond();
				sg = new TreeOfSubgraphs(H, 2, new BiRing(n));
				break;
			}

			case TreeOfTrees: {
				final IntTriple params = read3Ints(formatDetail);
				final int H = params.getFirst();
				final int h = params.getSecond();
				final int b = params.getThird();
				sg = new TreeOfSubgraphs(H, 2, new Tree(h, b));
				break;
			}

			case LatticeOfLattices: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, false, false, new Lattice(n, n, true, true));
				break;
			}

			case LatticeOfLines: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, false, false, new Line(n));
				break;
			}

			case LatticeOfBiLines: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, false, false, new BiLine(n));
				break;
			}

			case LatticeOfCliques: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, false, false, new Clique(n));
				break;
			}

			case LatticeOfRings: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, false, false, new Ring(n));
				break;
			}

			case LatticeOfBiRings: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, false, false, new BiRing(n));
				break;
			}

			case LatticeOfTrees: {
				final IntQuadruple params = read4Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int h = params.getThird();
				final int b = params.getFourth();
				sg = new LatticeOfSubgraphs(M, N, M, N, false, false, new Tree(h, b));
				break;
			}

			case LatticeOneRowConnectedOfLattices: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, false, true, new Lattice(n, n, true, true));
				break;
			}

			case LatticeOneRowConnectedOfLines: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, false, true, new Line(n));
				break;
			}

			case LatticeOneRowConnectedOfBiLines: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, false, true, new BiLine(n));
				break;
			}

			case LatticeOneRowConnectedOfCliques: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, false, true, new Clique(n));
				break;
			}

			case LatticeOneRowConnectedOfRings: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, false, true, new Ring(n));
				break;
			}

			case LatticeOneRowConnectedOfBiRings: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, false, true, new BiRing(n));
				break;
			}

			case LatticeOneRowConnectedOfTrees: {
				final IntQuadruple params = read4Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int h = params.getThird();
				final int b = params.getFourth();
				sg = new LatticeOfSubgraphs(M, N, 1, N, false, true, new Tree(h, b));
				break;
			}

			case LatticeOneRowLoopedOfLattices: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, true, false, new Lattice(n, n, true, true));
				break;
			}

			case LatticeOneRowLoopedOfLines: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, true, false, new Line(n));
				break;
			}

			case LatticeOneRowLoopedOfBiLines: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, true, false, new BiLine(n));
				break;
			}

			case LatticeOneRowLoopedOfCliques: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, true, false, new Clique(n));
				break;
			}

			case LatticeOneRowLoopedOfRings: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, true, false, new Ring(n));
				break;
			}

			case LatticeOneRowLoopedOfBiRings: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, 1, N, true, false, new BiRing(n));
				break;
			}

			case LatticeOneRowLoopedOfTrees: {
				final IntQuadruple params = read4Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int h = params.getThird();
				final int b = params.getFourth();
				sg = new LatticeOfSubgraphs(M, N, 1, N, true, false, new Tree(h, b));
				break;
			}

			case LatticeLoopedOfLattices: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, true, true, new Lattice(n, n, true, true));
				break;
			}

			case LatticeLoopedOfLines: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, true, true, new Line(n));
				break;
			}

			case LatticeLoopedOfBiLines: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, true, true, new BiLine(n));
				break;
			}

			case LatticeLoopedOfCliques: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, true, true, new Clique(n));
				break;
			}

			case LatticeLoopedOfRings: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, true, true, new Ring(n));
				break;
			}

			case LatticeLoopedOfBiRings: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int n = params.getThird();
				sg = new LatticeOfSubgraphs(M, N, M, N, true, true, new BiRing(n));
				break;
			}

			case LatticeLoopedOfTrees: {
				final IntQuadruple params = read4Ints(formatDetail);
				final int M = params.getFirst();
				final int N = params.getSecond();
				final int h = params.getThird();
				final int b = params.getFourth();
				sg = new LatticeOfSubgraphs(M, N, M, N, true, true, new Tree(h, b));
				break;
			}

			case LineOfLattices: {
				final IntPair params = read2Ints(formatDetail);
				final int M = params.getFirst();
				final int n = params.getSecond();
				sg = new LineOfSubgraphs(M, new Lattice(n, n, true, true));
				break;
			}

			case LineOfLines: {
				final IntPair params = read2Ints(formatDetail);
				final int M = params.getFirst();
				final int n = params.getSecond();
				sg = new LineOfSubgraphs(M, new Line(n));
				break;
			}

			case LineOfBiLines: {
				final IntPair params = read2Ints(formatDetail);
				final int M = params.getFirst();
				final int n = params.getSecond();
				sg = new LineOfSubgraphs(M, new BiLine(n));
				break;
			}

			case LineOfCliques: {
				final IntPair params = read2Ints(formatDetail);
				final int M = params.getFirst();
				final int n = params.getSecond();
				sg = new LineOfSubgraphs(M, new Clique(n));
				break;
			}

			case LineOfRings: {
				final IntPair params = read2Ints(formatDetail);
				final int M = params.getFirst();
				final int n = params.getSecond();
				sg = new LineOfSubgraphs(M, new Ring(n));
				break;
			}

			case LineOfBiRings: {
				final IntPair params = read2Ints(formatDetail);
				final int M = params.getFirst();
				final int n = params.getSecond();
				sg = new LineOfSubgraphs(M, new BiRing(n));
				break;
			}

			case LineOfTrees: {
				final IntTriple params = read3Ints(formatDetail);
				final int M = params.getFirst();
				final int h = params.getSecond();
				final int b = params.getThird();
				sg = new LineOfSubgraphs(M, new Tree(h, b));
				break;
			}

			case RingWithShortcuts: {
				final Triple<Integer, Integer, Long> params = read2IntsAndOptionalLong(formatDetail);
				final int n = params.getFirst();
				final int shortcuts = params.getSecond();
				final long seed = params.getThird() == null ? 1 : params.getThird().longValue();
				sg = new RingWithShortcuts(n, shortcuts, new Random(seed));
				break;
			}

			case LogN:
			case DiscreteUniform:
			case Uniform:
			case Pareto:
			case Normal:
			case FastErdos:
			case ApproximateErdos: {
				final Quintuple<Long, Double, Double, Long, Boolean> params = readLongAndTwoDoublesAndOptionalBool(formatDetail);
				final long n = params.getFirst();
				final double param1 = params.getSecond();
				final double param2 = params.getThird();
				final long seed = (params.getFourth() != null ? params.getFourth().longValue() : 1);
				final boolean disallowMultipleEdges = (params.getFifth() != null ? params.getFifth().booleanValue()
						: false);
				final Random random = new Random(seed);
				final ProbabilityDistribution degreeDistribution;

				switch (graphType) {
				case LogN:
					degreeDistribution = new LogNormalDistribution(param1, param2, random);
					break;
				case DiscreteUniform:
					degreeDistribution = new UniformDistribution(param1, param2, random);
					break;
				case Uniform:
					if (!isInt(param1) || !isInt(param2)) {
						throw new RuntimeException("Parameters of the uniform distribution should be integer");
					}
					degreeDistribution = new DiscreteUniformDistribution(asInt(param1), asInt(param2), random);
					break;
				case Pareto:
					degreeDistribution = new ParetoDistribution(param1, param2, random);
					break;
				case Normal:
					degreeDistribution = new NormalDistribution(param1, param2, random);
					break;
				case FastErdos:
					if (!isInt(param1)) {
						throw new GraphCreationException("FastErdos: n must be integer");
					}
					degreeDistribution = new BinomialDistribution(asInt(param1), param2, random);
					break;
				default:
				case ApproximateErdos:
					if (!isInt(param1)) {
						throw new GraphCreationException("FastErdos: n must be integer");
					}
					degreeDistribution = new ApproximateBinomialDistribution(asInt(param1), param2, random);
					break;
				}
				sg = new DegreeDistributionGraph(n, degreeDistribution, random, disallowMultipleEdges);
				break;
			}

			case SlowErdos: {
				final Triple<Long, Double, Long> params = readLongAndDouble(formatDetail);
				final long n = params.getFirst();
				final double p = (params.getSecond()) / (double) n;
				final long seed = (params.getThird() == null ? 1 : params.getThird());
				sg = new SlowErdos(n, p, new Random(seed));
				break;
			}

			case PetriNetStateSpace:
			case BioPetriNetStateSpace: {
				final boolean degradation = graphType == SyntheticGraphType.BioPetriNetStateSpace;
				final Pair<Byte, String> params = readByteAndString(formatDetail);
				final byte maxValue = params.getFirst();
				final String path = params.getSecond();
				final PetriNet petriNet = PetriNet.parse(maxValue, path, degradation);
				sg = new PetriNetStateSpace(petriNet);
				partition = new HypercubePartition(maxValue + 1, petriNet.placeNum(), poolSize);
			}
			}

			if (rank == 0) {
				System.out.println("Creating " + sg + (transpose ? ("(transposed)") : ""));
				System.out.flush();
			}
			if (partition == null) {
				partition = new RandomPartition(poolSize);
			}
			@SuppressWarnings("unchecked")
			Class<ExplicitLocalNode<TNode>> LocNodeCl = (Class<ExplicitLocalNode<TNode>>) TLocalNodeClass;
			if (transpose && !sg.canSynthetizeTranspose()) {
				throw new GraphCreationException("Cannot synthesize a transpose of " + sg);
			}

			g = SyntheticGraphMaker.create(sg, partition, LocNodeCl, transpose, rank, poolSize,
					sg.transitionsPerNodeCreatedSequentially());
			g.setPartition(partition);
			g.setSyntheticGraph(sg);
		}

		final MonitorThread monitor = new MonitorThread(1000, System.err, "GraphIO::gc") {
			public void print(StringBuilder sb) {
				sb.append("gc");
			}
		}.startMonitor();

		System.gc();
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}
		monitor.stopMonitor();

		Runtime.getRuntime().awaitPool(poolSize);
		Runtime.getRuntime().barrier();

		if (Config.STATISTICS) {
			Statistics.saveGraphMemoryUsage();
		}

		return g;
	}

	public static String formatSpecificationMessage() {
		int maxNameLength = 0, maxShortParameterLength = 0;
		for (SyntheticGraphType type : SyntheticGraphType.values()) {
			maxNameLength = Math.max(maxNameLength, type.name().length());
			maxShortParameterLength = Math.max(maxShortParameterLength, type.parametersShortDescription.length());
		}
		final String prefix = "  ";
		StringBuilder sb = new StringBuilder();
		for (GenerationType kind : GenerationType.values()) {
			sb.append(kind.description);
			sb.append(":\n");
			for (SyntheticGraphType type : SyntheticGraphType.values()) {
				if (type.kind == kind) {
					sb.append(prefix);
					sb.append(type.name());
					sb.append(StringUtils.Spaces(maxNameLength - type.name().length()));
					sb.append(' ');
					sb.append(type.parametersShortDescription);
					sb.append(StringUtils.Spaces(maxShortParameterLength - type.parametersShortDescription.length()));
					sb.append("  -- ");
					sb.append(type.humanReadableDescription);
					if (type.parametersLongDescription != null && type.parametersLongDescription.trim().length() > 0) {
						sb.append(" (");
						sb.append(type.parametersLongDescription);
						sb.append(")");
					}
					sb.append("\n");
				}
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	private static final byte readByte(String str) {
		try {
			return Byte.parseByte(str);
		} catch (NumberFormatException nfe) {
			throw new RuntimeException("Cannot parse int number '" + str + "'");
		}
	}

	private static final int readInt(String str) {
		try {
			return Integer.parseInt(str);
		} catch (NumberFormatException nfe) {
			throw new RuntimeException("Cannot parse int number '" + str + "'");
		}
	}

	private static final long readLong(String str) {
		try {
			return Long.parseLong(str);
		} catch (NumberFormatException nfe) {
			throw new RuntimeException("Cannot parse long number '" + str + "'");
		}
	}

	private static final double readDouble(String str) {
		try {
			return Double.parseDouble(str);
		} catch (NumberFormatException nfe) {
			throw new RuntimeException("Cannot parse double number '" + str + "'");
		}
	}

	private static final boolean readBool(String str) {
		try {
			return Boolean.parseBoolean(str);
		} catch (NumberFormatException nfe) {
			throw new RuntimeException("Cannot parse double number '" + str + "'");
		}
	}

	private static final IntPair read2Ints(String str) {
		Vector<String> vals = cut(str, ':');
		if (vals == null || vals.size() != 2) {
			throw new RuntimeException("Incorrect number of numbers when parsing " + str);
		}
		return new IntPair(readInt(vals.get(0)), readInt(vals.get(1)));
	}

	private static final LongPair read2Longs(String str) {
		Vector<String> vals = cut(str, ':');
		if (vals == null || vals.size() != 2) {
			throw new RuntimeException("Incorrect number of numbers when parsing " + str);
		}
		return new LongPair(readLong(vals.get(1)), readLong(vals.get(0)));
	}

	private static final IntTriple read3Ints(String str) {
		Vector<String> vals = cut(str, ':');
		if (vals == null || vals.size() != 3) {
			throw new RuntimeException("Incorrect number of numbers when parsing " + str);
		}
		return new IntTriple(readInt(vals.get(0)), readInt(vals.get(1)), readInt(vals.get(2)));
	}

	private static final IntQuadruple read4Ints(String str) {
		Vector<String> vals = cut(str, ':');
		if (vals == null || vals.size() != 4) {
			throw new RuntimeException("Incorrect number of numbers when parsing " + str);
		}
		return new IntQuadruple(readInt(vals.get(0)), readInt(vals.get(1)), readInt(vals.get(2)), readInt(vals.get(3)));
	}

	private static final Pair<Byte, String> readByteAndString(String str) {
		Vector<String> vals = cut(str, ':');
		if (vals == null || vals.size() != 2) {
			throw new RuntimeException("Incorrect number of numbers when parsing " + str);
		}
		return new Pair<Byte, String>(readByte(vals.get(0)), vals.get(1));
	}

	private static final Triple<Long, Double, Long> readLongAndDouble(String str) {
		Vector<String> vals = cut(str, ':');
		if (vals == null || vals.size() < 2 || vals.size() > 3) {
			throw new RuntimeException("Incorrect number of numbers when parsing " + str);
		}
		return new Triple<Long, Double, Long>(readLong(vals.get(0)), readDouble(vals.get(1)),
				vals.size() > 2 ? readLong(vals.get(2)) : null);
	}

	private static final Triple<Integer, Integer, Long> read2IntsAndOptionalLong(String str) {
		Vector<String> vals = cut(str, ':');
		if (vals == null || vals.size() < 2 || vals.size() > 3) {
			throw new RuntimeException("Incorrect number of numbers when parsing " + str);
		}
		return new Triple<Integer, Integer, Long>(readInt(vals.get(0)), readInt(vals.get(1)),
				vals.size() > 2 ? readLong(vals.get(2)) : null);
	}

	private static final Quintuple<Long, Double, Double, Long, Boolean> readLongAndTwoDoublesAndOptionalBool(String str) {
		Vector<String> vals = cut(str, ':');
		if (vals == null || (vals.size() != 3 && vals.size() != 4 && vals.size() != 5)) {
			throw new RuntimeException("Incorrect number of numbers when parsing " + str);
		}
		return new Quintuple<Long, Double, Double, Long, Boolean>(readLong(vals.get(0)), readDouble(vals.get(1)),
				readDouble(vals.get(2)), vals.size() > 3 ? readLong(vals.get(3)) : null,
				vals.size() > 4 ? readBool(vals.get(4)) : null);
	}

	private static Vector<String> cut(String str, char ch) {
		if (str == null)
			return null;
		Vector<String> result = new Vector<String>();
		int lasti = -1;
		for (int i = 0; i <= str.length(); i++) {
			char c = (i == str.length() ? ch : str.charAt(i));
			if (c == ch) {
				if (i > lasti + 1) {
					result.add(str.substring(lasti + 1, i));
				}
				lasti = i;
			}
		}
		return result;
	}

	private static boolean isInt(double d) {
		return Math.abs(d - (double) (int) d) < 0.000000001;
	}

	private static int asInt(double d) {
		return (int) Math.round(d);
	}
}
