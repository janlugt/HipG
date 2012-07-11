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

public class TreeTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeBranchTree() throws GraphCreationException {
		new Tree(2, -1);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotIncorrectBranchTree() throws GraphCreationException {
		new Tree(2, 1);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeHeightTree() throws GraphCreationException {
		new Tree(-1, 2);
	}

	@Test
	public void computesSize() throws GraphCreationException {
		assertEquals(1, new Tree(0, 2).estimateGlobalNodes());
		assertEquals(3, new Tree(1, 2).estimateGlobalNodes());
		assertEquals(7, new Tree(2, 2).estimateGlobalNodes());
		assertEquals(2047, new Tree(10, 2).estimateGlobalNodes());
		assertEquals(1, new Tree(0, 3).estimateGlobalNodes());
		assertEquals(4, new Tree(1, 3).estimateGlobalNodes());
		assertEquals(13, new Tree(2, 3).estimateGlobalNodes());
		assertEquals(88573, new Tree(10, 3).estimateGlobalNodes());

		assertEquals(0, new Tree(0, 2).estimateGlobalTransitions());
		assertEquals(2, new Tree(1, 2).estimateGlobalTransitions());
		assertEquals(6, new Tree(2, 2).estimateGlobalTransitions());
		assertEquals(2046, new Tree(10, 2).estimateGlobalTransitions());
		assertEquals(0, new Tree(0, 3).estimateGlobalTransitions());
		assertEquals(3, new Tree(1, 3).estimateGlobalTransitions());
		assertEquals(12, new Tree(2, 3).estimateGlobalTransitions());
		assertEquals(88572, new Tree(10, 3).estimateGlobalTransitions());
	}

	@Test
	public void canComputeTranspose() throws GraphCreationException {
		assertTrue(new Tree(10, 2).canSynthetizeTranspose());
	}

	@Test
	public void createsOneElementTree() throws GraphCreationException {
		Tree tree = new Tree(0, 2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
	}

	@Test
	public void createsThreeElementsTree() throws GraphCreationException {
		Tree tree = new Tree(1, 2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(3, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
		assertEquals(0, maker.get(1, 1));
		assertEquals(0, maker.get(2, 2));
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(0, 2));
	}

	@Test
	public void createsSevenElementsTree() throws GraphCreationException {
		Tree tree = new Tree(2, 2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(7, maker.numNodes());
		assertEquals(7, maker.totalNumNodes());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
	}

	@Test
	public void createsThirteenElementsTree() throws GraphCreationException {
		Tree tree = new Tree(2, 3);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(13, maker.numNodes());
		assertEquals(13, maker.totalNumNodes());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(1, 4));

		assertEquals(1, maker.get(0, 5));
		assertEquals(1, maker.get(5, 6));
		assertEquals(1, maker.get(5, 7));
		assertEquals(1, maker.get(5, 8));

		assertEquals(1, maker.get(0, 9));
		assertEquals(1, maker.get(9, 10));
		assertEquals(1, maker.get(9, 11));
		assertEquals(1, maker.get(9, 12));
	}

	@Test
	public void createsBiggerTree() throws GraphCreationException {
		Tree tree = new Tree(6, 3);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		tree.create(maker);
		assertEquals(1093, maker.numNodes());
		assertEquals(1093, maker.totalNumNodes());
		int nodes = maker.numNodes(), nodes0 = 0, nodes3 = 0;
		for (int i = 0; i < nodes; ++i) {
			int degree = 0;
			for (int j = 0; j < nodes; ++j) {
				degree += maker.get(i, j);
			}
			if (degree == 0) {
				nodes0++;
			} else {
				assertEquals(3, degree);
				nodes3++;
			}
		}
		assertEquals(729, nodes0);
		assertEquals(364, nodes3);
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("Tree(h=10, b=3)", new Tree(10, 3).toString());
		assertEquals("BinTree(h=10)", new Tree(10, 2).toString());
	}

}
