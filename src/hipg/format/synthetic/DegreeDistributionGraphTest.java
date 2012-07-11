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

package hipg.format.synthetic;

import static org.junit.Assert.*;
import hipg.format.GraphCreationException;
import hipg.utils.TestUtils.TestSyntheticGraphMaker;

import java.io.PrintStream;
import java.util.Random;
import java.util.Vector;

import myutils.probability.ApproximateBinomialDistribution;
import myutils.probability.BinomialDistribution;
import myutils.probability.DiscreteDistribution;
import myutils.probability.DiscreteUniformDistribution;
import myutils.probability.LogNormalDistribution;
import myutils.probability.NormalDistribution;
import myutils.probability.ParetoDistribution;
import myutils.probability.UniformDistribution;
import myutils.test.TestUtils;

import org.junit.Test;

public class DegreeDistributionGraphTest {

	@Test
	public void testUniform() throws GraphCreationException {
		Random random = new Random(1);
		UniformDistribution degreeDistribution = new UniformDistribution(2.0, 10.0, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
	}

	@Test
	public void testUniformDisallowMultipleTransitions() throws GraphCreationException {
		Random random = new Random(1);
		UniformDistribution degreeDistribution = new UniformDistribution(2.0, 10.0, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
		assertEquals(0, maker.multipleTransitions());
	}

	@Test
	public void testNormal() throws GraphCreationException {
		Random random = new Random(1);
		NormalDistribution degreeDistribution = new NormalDistribution(3.0, 1.0, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
	}

	@Test
	public void testNormalDisallowMultipleTransitions() throws GraphCreationException {
		Random random = new Random(1);
		NormalDistribution degreeDistribution = new NormalDistribution(3.0, 1.0, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
		assertEquals(0, maker.multipleTransitions());
	}

	@Test
	public void testLogNormal() throws GraphCreationException {
		Random random = new Random(1);
		LogNormalDistribution degreeDistribution = new LogNormalDistribution(2.0, 1.0, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
	}

	@Test
	public void testLogNormalDisallowMultipleTransitions() throws GraphCreationException {
		Random random = new Random(1);
		LogNormalDistribution degreeDistribution = new LogNormalDistribution(2.0, 1.0, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
		assertEquals(0, maker.multipleTransitions());
	}

	@Test
	public void testPareto() throws GraphCreationException {
		Random random = new Random(1);
		ParetoDistribution degreeDistribution = new ParetoDistribution(1.0, 3, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
	}

	@Test
	public void testParetoDisallowMultipleTransitions() throws GraphCreationException {
		Random random = new Random(1);
		ParetoDistribution degreeDistribution = new ParetoDistribution(1.0, 3, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
		assertEquals(0, maker.multipleTransitions());
	}

	@Test
	public void testDiscreteUniform() throws GraphCreationException {
		Random random = new Random(1);
		DiscreteUniformDistribution degreeDistribution = new DiscreteUniformDistribution(10, 30, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
	}

	@Test
	public void testDiscreteUniformDisallowMultipleTransitions() throws GraphCreationException {
		Random random = new Random(1);
		DiscreteUniformDistribution degreeDistribution = new DiscreteUniformDistribution(10, 30, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
		assertEquals(0, maker.multipleTransitions());
	}

	@Test
	public void testDiscrete() throws GraphCreationException {
		Random random = new Random(1);
		DiscreteDistribution degreeDistribution = new DiscreteDistribution(10, new int[] { 10, 1, 5, 4 }, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
	}

	@Test
	public void testDiscreteDisallowMultipleTransitions() throws GraphCreationException {
		Random random = new Random(1);
		DiscreteDistribution degreeDistribution = new DiscreteDistribution(10, new int[] { 10, 1, 5, 4 }, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
		assertEquals(0, maker.multipleTransitions());
	}

	@Test
	public void testBinomial() throws GraphCreationException {
		Random random = new Random(1);
		BinomialDistribution degreeDistribution = new BinomialDistribution(10, 0.5, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
	}

	@Test
	public void testBinomialDisallowMultipleTransitions() throws GraphCreationException {
		Random random = new Random(1);
		BinomialDistribution degreeDistribution = new BinomialDistribution(10, 0.5, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
		assertEquals(0, maker.multipleTransitions());
	}

	@Test
	public void testApproximateBinomial() throws GraphCreationException {
		Random random = new Random(1);
		ApproximateBinomialDistribution degreeDistribution = new ApproximateBinomialDistribution(1000, 0.5, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
	}

	@Test
	public void testApproximateBinomialDisallowMultipleTransitions() throws GraphCreationException {
		Random random = new Random(1);
		ApproximateBinomialDistribution degreeDistribution = new ApproximateBinomialDistribution(1000, 0.5, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(1000, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		graph.create(maker);
		TestUtils.assertNearRelative(maker.totalNumTransitions(), graph.estimateGlobalTransitions(), 0.1);
		double actualAverageDegree = maker.averageDegree();
		double expectedAverageDegree = degreeDistribution.expected();
		TestUtils.assertNearRelative(expectedAverageDegree, actualAverageDegree, 0.1);
		double actualStandardDeviation = maker.estimateDegreeStandardDeviation(expectedAverageDegree);
		double expectedStandardDeviation = degreeDistribution.standardDeviation();
		TestUtils.assertNearRelative(expectedStandardDeviation, actualStandardDeviation, 0.1);
		assertEquals(0, maker.multipleTransitions());
	}

	private static final class MyPrintStream extends PrintStream {
		private final Vector<String> lines = new Vector<String>();

		public MyPrintStream() {
			super(System.err);
		}

		@Override
		public void println(String line) {
			lines.add(line);
		}

		public int numLines() {
			return lines.size();
		}

		public String getLine(int lineIndex) {
			return lines.get(lineIndex);
		}
	}

	@Test
	public void testTooBigDegrees() throws GraphCreationException {
		Random random = new Random(1);
		UniformDistribution degreeDistribution = new UniformDistribution(100, 200, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(10, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		MyPrintStream output = new MyPrintStream();
		System.setErr(output);
		graph.create(maker);
		assertEquals(10, output.numLines());
		for (int i = 0; i < 10; ++i) {
			assertEquals("Warning: Limiting degree of " + i + "@0 to 10", output.getLine(i));
		}
	}

	@Test
	public void testNegativeDegrees() throws GraphCreationException {
		Random random = new Random(1);
		UniformDistribution degreeDistribution = new UniformDistribution(-10, 10, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(10, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		MyPrintStream output = new MyPrintStream();
		System.setErr(output);
		graph.create(maker);
		int lines = output.numLines();
		for (int i = 0; i < lines; ++i) {
			assertTrue(output.getLine(i).startsWith("Warning: Redrawing degree of"));
		}
	}

	@Test
	public void testCanSynthetizeTranspose() throws GraphCreationException {
		assertFalse(new DegreeDistributionGraph(100, new NormalDistribution(0, 1, new Random(1)), new Random(2), false)
				.canSynthetizeTranspose());
	}

	@Test(expected = GraphCreationException.class)
	public void testCannotCreateTranspose() throws GraphCreationException {
		Random random = new Random(1);
		UniformDistribution degreeDistribution = new UniformDistribution(100, 200, random);
		DegreeDistributionGraph graph = new DegreeDistributionGraph(10, degreeDistribution, random, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(graph);
		maker.setTranspose(true);
		graph.create(maker);
	}

	@Test
	public void testToString() {
		NormalDistribution distribution = new NormalDistribution(0, 1, new Random(1));
		assertEquals("DegreeDistributionGraph(n=100, " + distribution.toString() + ")", new DegreeDistributionGraph(
				100, distribution, new Random(2), false).toString());
	}
}
