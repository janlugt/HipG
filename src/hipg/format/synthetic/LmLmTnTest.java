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

public class LmLmTnTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeNSizeLmLmTn() throws GraphCreationException {
		new LmLmTn(10, -1);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateEmptyMLmLmTn() throws GraphCreationException {
		new LmLmTn(0, 10);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeMSizeLmLmTn() throws GraphCreationException {
		new LmLmTn(-1, 10);
	}

	@Test
	public void computesSize() throws GraphCreationException {
		assertEquals(31, new LmLmTn(1, 4).estimateGlobalNodes());
		assertEquals(92, new LmLmTn(1, 4).estimateGlobalTransitions());

		assertEquals(9, new LmLmTn(3, 0).estimateGlobalNodes());
		assertEquals(18, new LmLmTn(3, 0).estimateGlobalTransitions());

		assertEquals(12, new LmLmTn(2, 1).estimateGlobalNodes());
		assertEquals(32, new LmLmTn(2, 1).estimateGlobalTransitions());

		assertEquals(63, new LmLmTn(3, 2).estimateGlobalNodes());
		assertEquals(180, new LmLmTn(3, 2).estimateGlobalTransitions());
	}

	@Test
	public void canComputeTranspose() throws GraphCreationException {
		assertTrue(new LmLmTn(3, 4).canSynthetizeTranspose());
	}

	@Test
	public void createsOneElementLmLmTn() throws GraphCreationException {
		LmLmTn lmlmtn = new LmLmTn(1, 0);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lmlmtn);
		lmlmtn.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(2, maker.totalNumTransitions());
		assertEquals(2, maker.get(0, 0));
	}

	@Test
	public void createsVerySmallLatticeLmLmTn() throws GraphCreationException {
		LmLmTn lmlmtn = new LmLmTn(2, 0);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lmlmtn);
		lmlmtn.create(maker);
		assertEquals(4, maker.numNodes());
		assertEquals(4, maker.totalNumNodes());
		assertEquals((int) lmlmtn.estimateGlobalTransitions(), maker.totalNumTransitions());
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 2));
		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 0));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(3, 1));
	}

	@Test
	public void createsSmallLatticeLmLmTn() throws GraphCreationException {
		LmLmTn lmlmtn = new LmLmTn(3, 0);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lmlmtn);
		lmlmtn.create(maker);
		assertEquals(9, maker.numNodes());
		assertEquals(9, maker.totalNumNodes());
		assertEquals((int) lmlmtn.estimateGlobalTransitions(), maker.totalNumTransitions());

		// first row
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 0));

		// second row
		assertEquals(1, maker.get(3, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 3));

		// third row
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 8));
		assertEquals(1, maker.get(8, 6));

		// first column
		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));
		assertEquals(1, maker.get(6, 0));

		// second column
		assertEquals(1, maker.get(1, 4));
		assertEquals(1, maker.get(4, 7));
		assertEquals(1, maker.get(7, 1));

		// third column
		assertEquals(1, maker.get(2, 5));
		assertEquals(1, maker.get(5, 8));
		assertEquals(1, maker.get(8, 2));
	}

	@Test
	public void createsVerySmallTreeLmLmTn() throws GraphCreationException {
		LmLmTn lmlmtn = new LmLmTn(1, 1);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lmlmtn);
		lmlmtn.create(maker);
		assertEquals(3, maker.numNodes());
		assertEquals(3, maker.totalNumNodes());
		assertEquals((int) lmlmtn.estimateGlobalTransitions(), maker.totalNumTransitions());
		assertEquals(2, maker.get(0, 0));
		assertEquals(2, maker.get(1, 1));
		assertEquals(2, maker.get(2, 2));
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(0, 2));
	}

	@Test
	public void createsSmallTreeLmLmTn() throws GraphCreationException {
		LmLmTn lmlmtn = new LmLmTn(1, 2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lmlmtn);
		lmlmtn.create(maker);
		assertEquals(7, maker.numNodes());
		assertEquals(7, maker.totalNumNodes());
		assertEquals((int) lmlmtn.estimateGlobalTransitions(), maker.totalNumTransitions());
		assertEquals(2, maker.get(0, 0));
		assertEquals(2, maker.get(1, 1));
		assertEquals(2, maker.get(2, 2));
		assertEquals(2, maker.get(3, 3));
		assertEquals(2, maker.get(4, 4));
		assertEquals(2, maker.get(5, 5));
		assertEquals(2, maker.get(6, 6));
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(1, 3));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(4, 6));
	}

	@Test
	public void createsSmallLmLmTn() throws GraphCreationException {
		LmLmTn lmlmtn = new LmLmTn(3, 2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lmlmtn);
		lmlmtn.create(maker);
		assertEquals(9 * 7, maker.numNodes());
		assertEquals((int) lmlmtn.estimateGlobalTransitions(), maker.totalNumTransitions());

		// test internal matrix transitions
		int checkedTransitions = 0;
		for (int i = 0; i < 7; ++i) {
			assertEquals(1, maker.get(9 * i + 0, 9 * i + 1));
			assertEquals(1, maker.get(9 * i + 1, 9 * i + 2));
			assertEquals(1, maker.get(9 * i + 2, 9 * i + 0));

			assertEquals(1, maker.get(9 * i + 3, 9 * i + 4));
			assertEquals(1, maker.get(9 * i + 4, 9 * i + 5));
			assertEquals(1, maker.get(9 * i + 5, 9 * i + 3));

			assertEquals(1, maker.get(9 * i + 6, 9 * i + 7));
			assertEquals(1, maker.get(9 * i + 7, 9 * i + 8));
			assertEquals(1, maker.get(9 * i + 8, 9 * i + 6));

			assertEquals(1, maker.get(9 * i + 0, 9 * i + 3));
			assertEquals(1, maker.get(9 * i + 3, 9 * i + 6));
			assertEquals(1, maker.get(9 * i + 6, 9 * i + 0));

			assertEquals(1, maker.get(9 * i + 1, 9 * i + 4));
			assertEquals(1, maker.get(9 * i + 4, 9 * i + 7));
			assertEquals(1, maker.get(9 * i + 7, 9 * i + 1));

			assertEquals(1, maker.get(9 * i + 2, 9 * i + 5));
			assertEquals(1, maker.get(9 * i + 5, 9 * i + 8));
			assertEquals(1, maker.get(9 * i + 8, 9 * i + 2));

			checkedTransitions += 18;
		}

		// test tree transitions
		for (int i = 9; i < 63; ++i) {
			int j = i / 9, k = i % 9;
			int fatherj = (j == 1 || j == 4 ? 0 : (j == 2 || j == 3 ? 1 : 4));
			int father = fatherj * 9 + k;
			assertEquals(1, maker.get(father, i));
			checkedTransitions++;
		}
		assertEquals(maker.totalNumTransitions(), checkedTransitions);
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("LmLmTn(m=20, n=10)", new LmLmTn(20, 10).toString());
	}
}
