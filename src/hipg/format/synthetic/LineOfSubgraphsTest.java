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

import org.junit.Test;

public class LineOfSubgraphsTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeSizeLine() throws GraphCreationException {
		new LineOfSubgraphs(-1, new Line(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateEmptyLine() throws GraphCreationException {
		new LineOfSubgraphs(0, new Clique(1));
	}

	@Test
	public void createsOneNodeTree() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(1, new Clique(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(1, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
		assertEquals(0, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsTwoNodeTree() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(2, new Clique(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(2, maker.totalNumNodes());
		assertEquals(1, maker.totalNumTransitions());
		assertEquals(1, tree.estimateGlobalTransitions());
		assertEquals(1, maker.get(0, 1));
	}

	@Test
	public void createsThreeNodeTree() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new Clique(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(3, maker.totalNumNodes());
		assertEquals(2, maker.totalNumTransitions());
		assertEquals(2, tree.estimateGlobalTransitions());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
	}

	@Test
	public void createsSevenNodeTree() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(7, new Line(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.totalNumNodes());
		assertEquals(6, maker.totalNumTransitions());
		assertEquals(6, tree.estimateGlobalTransitions());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 6));
	}

	@Test
	public void createsSmallLineOfRings() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new Ring(2));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(6, maker.totalNumNodes());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 2));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 4));

		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 4));

		assertEquals(8, maker.totalNumTransitions());
		assertEquals(8, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsSmallLineOfBiRings() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new BiRing(3));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(9, maker.totalNumNodes());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 0));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(2, 1));
		assertEquals(1, maker.get(0, 2));

		assertEquals(1, maker.get(3, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 3));
		assertEquals(1, maker.get(4, 3));
		assertEquals(1, maker.get(5, 4));
		assertEquals(1, maker.get(3, 5));

		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 8));
		assertEquals(1, maker.get(8, 6));
		assertEquals(1, maker.get(7, 6));
		assertEquals(1, maker.get(8, 7));
		assertEquals(1, maker.get(6, 8));

		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));

		assertEquals(20, maker.totalNumTransitions());
		assertEquals(20, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsSmallLineOfBiLines() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new BiLine(3));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(9, maker.totalNumNodes());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 1));
		assertEquals(1, maker.get(3, 4));
		assertEquals(1, maker.get(4, 3));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 4));
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 6));
		assertEquals(1, maker.get(7, 8));
		assertEquals(1, maker.get(8, 7));

		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));

		assertEquals(14, maker.totalNumTransitions());
		assertEquals(14, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsSmallLineOfLines() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new Line(3));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(9, maker.totalNumNodes());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(3, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 8));

		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));

		assertEquals(8, maker.totalNumTransitions());
		assertEquals(8, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsSmallLineOfCliques() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new Clique(3));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(9, maker.totalNumNodes());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 0));
		assertEquals(1, maker.get(2, 1));

		assertEquals(1, maker.get(3, 4));
		assertEquals(1, maker.get(3, 5));
		assertEquals(1, maker.get(4, 3));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 3));
		assertEquals(1, maker.get(5, 4));

		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(6, 8));
		assertEquals(1, maker.get(7, 6));
		assertEquals(1, maker.get(7, 8));
		assertEquals(1, maker.get(8, 6));
		assertEquals(1, maker.get(8, 7));

		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));

		assertEquals(20, maker.totalNumTransitions());
		assertEquals(20, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsSmallLineOfHorizontalLattices() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new Lattice(1, 2, true, false));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(6, maker.totalNumNodes());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 2));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 4));

		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 4));

		assertEquals(8, maker.totalNumTransitions());
		assertEquals(8, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsSmallLineOfVerticalLattices() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new Lattice(2, 1, false, true));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(6, maker.totalNumNodes());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 2));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 4));

		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 4));

		assertEquals(8, maker.totalNumTransitions());
		assertEquals(8, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsSmallLineOfLattices() throws GraphCreationException {
		LineOfSubgraphs tree = new LineOfSubgraphs(3, new Lattice(2, 3, true, false));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(18, maker.totalNumNodes());
		assertEquals(3 * 9 + 2, maker.totalNumTransitions());
		assertEquals(3 * 9 + 2, tree.estimateGlobalTransitions());

		assertEquals(1, maker.get(0, 6));
		assertEquals(1, maker.get(6, 12));

		for (int i = 0; i < 3; ++i) {
			int start = i * 6;
			assertEquals(1, maker.get(start + 0, start + 1));
			assertEquals(1, maker.get(start + 1, start + 2));
			assertEquals(1, maker.get(start + 2, start + 0));
			assertEquals(1, maker.get(start + 3, start + 4));
			assertEquals(1, maker.get(start + 4, start + 5));
			assertEquals(1, maker.get(start + 5, start + 3));
			assertEquals(1, maker.get(start + 0, start + 3));
			assertEquals(1, maker.get(start + 1, start + 4));
			assertEquals(1, maker.get(start + 2, start + 5));
		}
	}

	@Test
	public void testCanSynthetizeTranspose() throws GraphCreationException {
		assertTrue(new LineOfSubgraphs(3, new Line(2)).canSynthetizeTranspose());
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("Line(7) of " + (new Ring(10).toString()), new LineOfSubgraphs(7, new Ring(10)).toString());
	}
}
