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

public class TreeOfSubgraphsTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeBranchTree() throws GraphCreationException {
		new TreeOfSubgraphs(2, -1, new Line(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotIncorrectBranchTree() throws GraphCreationException {
		new TreeOfSubgraphs(2, 1, new Line(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeHeightTree() throws GraphCreationException {
		new TreeOfSubgraphs(-1, 2, new Line(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNullSubgraphTree() throws GraphCreationException {
		new TreeOfSubgraphs(2, 2, null);
	}

	@Test
	public void createsOneNodeTree() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(0, 2, new Line(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(1, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
		assertEquals(0, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsThreeNodeTree() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(1, 3, new Line(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(4, maker.totalNumNodes());
		assertEquals(3, maker.totalNumTransitions());
		assertEquals(3, tree.estimateGlobalTransitions());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(0, 3));
	}

	@Test
	public void createsSevenNodeTree() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Line(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.totalNumNodes());
		assertEquals(6, maker.totalNumTransitions());
		assertEquals(6, tree.estimateGlobalTransitions());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
	}

	@Test
	public void createsSmallTreeOfTrivialRings() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Ring(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.totalNumNodes());
		assertEquals(13, maker.totalNumTransitions());
		assertEquals(13, tree.estimateGlobalTransitions());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
		assertEquals(1, maker.get(0, 0));
		assertEquals(1, maker.get(1, 1));
		assertEquals(1, maker.get(2, 2));
		assertEquals(1, maker.get(3, 3));
		assertEquals(1, maker.get(4, 4));
		assertEquals(1, maker.get(5, 5));
		assertEquals(1, maker.get(6, 6));
	}

	@Test
	public void createsSmallTreeOfRings() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Ring(2));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(14, maker.totalNumNodes());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 2));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 4));
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 6));
		assertEquals(1, maker.get(8, 9));
		assertEquals(1, maker.get(9, 8));
		assertEquals(1, maker.get(10, 11));
		assertEquals(1, maker.get(11, 10));
		assertEquals(1, maker.get(12, 13));
		assertEquals(1, maker.get(13, 12));

		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 4));
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(0, 8));
		assertEquals(1, maker.get(8, 10));
		assertEquals(1, maker.get(8, 12));

		assertEquals(20, maker.totalNumTransitions());
		assertEquals(20, tree.estimateGlobalTransitions());
	}

	@Test
	public void createsSmallTreeOfTrivialBiRings() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new BiRing(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.totalNumNodes());
		assertEquals(20, maker.totalNumTransitions());
		assertEquals(20, tree.estimateGlobalTransitions());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
		assertEquals(2, maker.get(0, 0));
		assertEquals(2, maker.get(1, 1));
		assertEquals(2, maker.get(2, 2));
		assertEquals(2, maker.get(3, 3));
		assertEquals(2, maker.get(4, 4));
		assertEquals(2, maker.get(5, 5));
		assertEquals(2, maker.get(6, 6));
	}

	@Test
	public void createsSmallTreeOfBiRings() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new BiRing(2));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(14, maker.totalNumNodes());
		assertEquals(34, maker.totalNumTransitions());
		assertEquals(34, tree.estimateGlobalTransitions());

		assertEquals(2, maker.get(0, 1));
		assertEquals(2, maker.get(1, 0));
		assertEquals(2, maker.get(2, 3));
		assertEquals(2, maker.get(3, 2));
		assertEquals(2, maker.get(4, 5));
		assertEquals(2, maker.get(5, 4));
		assertEquals(2, maker.get(6, 7));
		assertEquals(2, maker.get(7, 6));
		assertEquals(2, maker.get(8, 9));
		assertEquals(2, maker.get(9, 8));
		assertEquals(2, maker.get(10, 11));
		assertEquals(2, maker.get(11, 10));
		assertEquals(2, maker.get(12, 13));
		assertEquals(2, maker.get(13, 12));

		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 4));
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(0, 8));
		assertEquals(1, maker.get(8, 10));
		assertEquals(1, maker.get(8, 12));
	}

	@Test
	public void createsSmallTreeOfTrivialLines() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Line(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.totalNumNodes());
		assertEquals(6, maker.totalNumTransitions());
		assertEquals(6, tree.estimateGlobalTransitions());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
	}

	@Test
	public void createsSmallTreeOfLines() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Line(2));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(14, maker.totalNumNodes());
		assertEquals(13, maker.totalNumTransitions());
		assertEquals(13, tree.estimateGlobalTransitions());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(8, 9));
		assertEquals(1, maker.get(10, 11));
		assertEquals(1, maker.get(12, 13));

		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 4));
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(0, 8));
		assertEquals(1, maker.get(8, 10));
		assertEquals(1, maker.get(8, 12));
	}

	@Test
	public void createsSmallTreeOfTrivialBiLines() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Line(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.totalNumNodes());
		assertEquals(6, maker.totalNumTransitions());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
	}

	@Test
	public void createsSmallTreeOfBiLines() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new BiLine(3));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(21, maker.totalNumNodes());
		assertEquals(34, maker.totalNumTransitions());
		assertEquals(34, tree.estimateGlobalTransitions());

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

		assertEquals(1, maker.get(9, 10));
		assertEquals(1, maker.get(10, 9));
		assertEquals(1, maker.get(10, 11));
		assertEquals(1, maker.get(11, 10));

		assertEquals(1, maker.get(12, 13));
		assertEquals(1, maker.get(13, 12));
		assertEquals(1, maker.get(13, 14));
		assertEquals(1, maker.get(14, 13));

		assertEquals(1, maker.get(15, 16));
		assertEquals(1, maker.get(16, 15));
		assertEquals(1, maker.get(16, 17));
		assertEquals(1, maker.get(17, 16));

		assertEquals(1, maker.get(18, 19));
		assertEquals(1, maker.get(19, 18));
		assertEquals(1, maker.get(19, 20));
		assertEquals(1, maker.get(19, 20));

		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));
		assertEquals(1, maker.get(3, 9));
		assertEquals(1, maker.get(0, 12));
		assertEquals(1, maker.get(12, 15));
		assertEquals(1, maker.get(12, 18));
	}

	@Test
	public void createsSmallTreeOfTrivialCliques() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Clique(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.totalNumNodes());
		assertEquals(6, maker.totalNumTransitions());
		assertEquals(6, tree.estimateGlobalTransitions());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
	}

	@Test
	public void createsSmallTreeOfCliques() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Clique(3));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(21, maker.totalNumNodes());
		assertEquals(48, maker.totalNumTransitions());
		assertEquals(48, tree.estimateGlobalTransitions());

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

		assertEquals(1, maker.get(9, 10));
		assertEquals(1, maker.get(9, 11));
		assertEquals(1, maker.get(10, 9));
		assertEquals(1, maker.get(10, 11));
		assertEquals(1, maker.get(11, 9));
		assertEquals(1, maker.get(11, 10));

		assertEquals(1, maker.get(12, 13));
		assertEquals(1, maker.get(12, 14));
		assertEquals(1, maker.get(13, 12));
		assertEquals(1, maker.get(13, 14));
		assertEquals(1, maker.get(14, 12));
		assertEquals(1, maker.get(14, 13));

		assertEquals(1, maker.get(15, 16));
		assertEquals(1, maker.get(15, 17));
		assertEquals(1, maker.get(16, 15));
		assertEquals(1, maker.get(16, 17));
		assertEquals(1, maker.get(17, 15));
		assertEquals(1, maker.get(17, 16));

		assertEquals(1, maker.get(18, 19));
		assertEquals(1, maker.get(18, 20));
		assertEquals(1, maker.get(19, 18));
		assertEquals(1, maker.get(19, 20));
		assertEquals(1, maker.get(20, 18));
		assertEquals(1, maker.get(20, 19));

		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));
		assertEquals(1, maker.get(3, 9));
		assertEquals(1, maker.get(0, 12));
		assertEquals(1, maker.get(12, 15));
		assertEquals(1, maker.get(12, 18));
	}

	@Test
	public void createsSmallTreeOfTrivialLattices() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Lattice(1, 1, false, false));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.totalNumNodes());
		assertEquals(6, maker.totalNumTransitions());
		assertEquals(6, tree.estimateGlobalTransitions());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
	}

	@Test
	public void createsSmallTreeOfHorizontalLattices() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Lattice(1, 2, true, false));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(14, maker.totalNumNodes());
		assertEquals(20, maker.totalNumTransitions());
		assertEquals(20, tree.estimateGlobalTransitions());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 2));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 4));
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 6));
		assertEquals(1, maker.get(8, 9));
		assertEquals(1, maker.get(9, 8));
		assertEquals(1, maker.get(10, 11));
		assertEquals(1, maker.get(11, 10));
		assertEquals(1, maker.get(12, 13));
		assertEquals(1, maker.get(13, 12));

		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 4));
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(0, 8));
		assertEquals(1, maker.get(8, 10));
		assertEquals(1, maker.get(8, 12));
	}

	@Test
	public void createsSmallTreeOfVerticalLattices() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Lattice(2, 1, false, true));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(14, maker.totalNumNodes());
		assertEquals(20, maker.totalNumTransitions());
		assertEquals(20, tree.estimateGlobalTransitions());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 2));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 4));
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 6));
		assertEquals(1, maker.get(8, 9));
		assertEquals(1, maker.get(9, 8));
		assertEquals(1, maker.get(10, 11));
		assertEquals(1, maker.get(11, 10));
		assertEquals(1, maker.get(12, 13));
		assertEquals(1, maker.get(13, 12));

		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 4));
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(0, 8));
		assertEquals(1, maker.get(8, 10));
		assertEquals(1, maker.get(8, 12));
	}

	@Test
	public void createsSmallTreeOfLattices() throws GraphCreationException {
		TreeOfSubgraphs tree = new TreeOfSubgraphs(2, 2, new Lattice(2, 3, true, false));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(42, maker.totalNumNodes());
		assertEquals(7 * 9 + 6, maker.totalNumTransitions());
		assertEquals(7 * 9 + 6, tree.estimateGlobalTransitions());

		assertEquals(1, maker.get(0, 6));
		assertEquals(1, maker.get(6, 12));
		assertEquals(1, maker.get(6, 18));
		assertEquals(1, maker.get(0, 24));
		assertEquals(1, maker.get(24, 30));
		assertEquals(1, maker.get(24, 36));

		for (int i = 0; i < 7; ++i) {
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
		assertTrue(new TreeOfSubgraphs(3, 4, new Line(2)).canSynthetizeTranspose());
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("BinTree(h=7) of " + (new Line(10).toString()), new TreeOfSubgraphs(7, 2, new Line(10)).toString());
		assertEquals("Tree(h=7, b=3) of " + (new Ring(10).toString()),
				new TreeOfSubgraphs(7, 3, new Ring(10)).toString());
	}
}
