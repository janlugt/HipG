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

public class LimLonTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateEmptyNLimLon() throws GraphCreationException {
		new LimLon(10, 0);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeNSizeLimLon() throws GraphCreationException {
		new LimLon(10, -1);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateEmptyMLimLon() throws GraphCreationException {
		new LimLon(0, 10);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeMSizeLimLon() throws GraphCreationException {
		new LimLon(-1, 10);
	}

	@Test
	public void computesSize() throws GraphCreationException {
		assertEquals(16, new LimLon(1, 4).estimateGlobalNodes());
		assertEquals(32, new LimLon(1, 4).estimateGlobalTransitions());

		assertEquals(9, new LimLon(3, 1).estimateGlobalNodes());
		assertEquals(30, new LimLon(3, 1).estimateGlobalTransitions());

		assertEquals(36, new LimLon(3, 2).estimateGlobalNodes());
		assertEquals(120, new LimLon(3, 2).estimateGlobalTransitions());
	}

	@Test
	public void canComputeTranspose() throws GraphCreationException {
		assertTrue(new LimLon(3, 4).canSynthetizeTranspose());
	}

	@Test
	public void createsOneElementLimLon() throws GraphCreationException {
		LimLon limlon = new LimLon(1, 1);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(limlon);
		limlon.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(2, maker.totalNumTransitions());
		assertEquals(2, maker.get(0, 0));
	}

	@Test
	public void createsVerySmallLatticeLimLon() throws GraphCreationException {
		LimLon limlon = new LimLon(2, 1);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(limlon);
		limlon.create(maker);
		assertEquals(4, maker.numNodes());
		assertEquals(4, maker.totalNumNodes());
		assertEquals((int) limlon.estimateGlobalTransitions(), maker.totalNumTransitions());
		assertEquals(2, maker.get(0, 0));
		assertEquals(2, maker.get(1, 1));
		assertEquals(2, maker.get(2, 2));
		assertEquals(2, maker.get(3, 3));
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(1, 3));
	}

	@Test
	public void createsSmallLatticeLimLon() throws GraphCreationException {
		LimLon limlon = new LimLon(3, 1);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(limlon);
		limlon.create(maker);
		assertEquals(9, maker.numNodes());
		assertEquals(9, maker.totalNumNodes());
		assertEquals((int) limlon.estimateGlobalTransitions(), maker.totalNumTransitions());

		// first row
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));

		// second row
		assertEquals(1, maker.get(3, 4));
		assertEquals(1, maker.get(4, 5));

		// third row
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 8));

		// first column
		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));

		// second column
		assertEquals(1, maker.get(1, 4));
		assertEquals(1, maker.get(4, 7));

		// third column
		assertEquals(1, maker.get(2, 5));
		assertEquals(1, maker.get(5, 8));

		// self loops
		assertEquals(2, maker.get(0, 0));
		assertEquals(2, maker.get(1, 1));
		assertEquals(2, maker.get(2, 2));
		assertEquals(2, maker.get(3, 3));
		assertEquals(2, maker.get(4, 4));
		assertEquals(2, maker.get(5, 5));
		assertEquals(2, maker.get(6, 6));
		assertEquals(2, maker.get(7, 7));
		assertEquals(2, maker.get(8, 8));
	}

	@Test
	public void createsVerySmallDeepLatticeLimLon() throws GraphCreationException {
		LimLon limlon = new LimLon(1, 2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(limlon);
		limlon.create(maker);
		assertEquals(4, maker.numNodes());
		assertEquals(4, maker.totalNumNodes());
		assertEquals((int) limlon.estimateGlobalTransitions(), maker.totalNumTransitions());

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
	public void createsSmallDeepLatticeLimLon() throws GraphCreationException {
		LimLon limlon = new LimLon(1, 3);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(limlon);
		limlon.create(maker);
		assertEquals(9, maker.numNodes());
		assertEquals(9, maker.totalNumNodes());
		assertEquals((int) limlon.estimateGlobalTransitions(), maker.totalNumTransitions());

		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 0));

		assertEquals(1, maker.get(3, 4));
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 3));

		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 8));
		assertEquals(1, maker.get(8, 6));

		assertEquals(1, maker.get(0, 3));
		assertEquals(1, maker.get(3, 6));
		assertEquals(1, maker.get(6, 0));

		assertEquals(1, maker.get(1, 4));
		assertEquals(1, maker.get(4, 7));
		assertEquals(1, maker.get(7, 1));

		assertEquals(1, maker.get(2, 5));
		assertEquals(1, maker.get(5, 8));
		assertEquals(1, maker.get(8, 2));
	}

	@Test
	public void createsSmallLimLon() throws GraphCreationException {
		LimLon limlon = new LimLon(3, 2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(limlon);
		limlon.create(maker);
		assertEquals(9 * 4, maker.numNodes());
		assertEquals((int) limlon.estimateGlobalTransitions(), maker.totalNumTransitions());

		// test internal matrix transitions
		int checkedTransitions = 0;
		for (int i = 0; i < 9; ++i) {
			assertEquals(1, maker.get(4 * i + 0, 4 * i + 1));
			assertEquals(1, maker.get(4 * i + 1, 4 * i + 0));

			assertEquals(1, maker.get(4 * i + 0, 4 * i + 2));
			assertEquals(1, maker.get(4 * i + 2, 4 * i + 0));

			assertEquals(1, maker.get(4 * i + 1, 4 * i + 3));
			assertEquals(1, maker.get(4 * i + 3, 4 * i + 1));

			assertEquals(1, maker.get(4 * i + 2, 4 * i + 3));
			assertEquals(1, maker.get(4 * i + 3, 4 * i + 2));

			checkedTransitions += 8;
		}

		// test tree transitions
		for (int i = 0; i < 36; ++i) {
			int right = i + 4;
			if (right < 36) {
				if ((i / 4) % 3 != 2) {
					assertEquals(1, maker.get(i, right));
					checkedTransitions++;
				}
			}
			int down = i + 4 * 3;
			if (down < 36) {
				assertEquals(1, maker.get(i, down));
				checkedTransitions++;
			}
		}

		assertEquals(maker.totalNumTransitions(), checkedTransitions);
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("LimLon(m=20, n=10)", new LimLon(20, 10).toString());
	}
}
