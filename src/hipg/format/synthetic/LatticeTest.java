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

public class LatticeTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeRowsTree() throws GraphCreationException {
		new Lattice(-1, 10, false, false);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreatezeroRowsTree() throws GraphCreationException {
		new Lattice(0, 10, false, false);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeColumnsTree() throws GraphCreationException {
		new Lattice(10, -10, false, false);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreatezeroColumnsTree() throws GraphCreationException {
		new Lattice(10, 0, false, false);
	}

	@Test
	public void createsOnebyOneLattice() throws GraphCreationException {
		Lattice lattice = new Lattice(1, 1, false, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
	}

	@Test
	public void createsOneByOneLoopedLattice() throws GraphCreationException {
		Lattice lattice = new Lattice(1, 1, true, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(2, maker.get(0, 0));
	}

	@Test
	public void createsSmallLattice() throws GraphCreationException {
		Lattice lattice = new Lattice(3, 4, false, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12, maker.numNodes());
		assertEquals(12, maker.totalNumNodes());
		assertEquals(12, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// row 0
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 3));
		// row 1
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 6));
		assertEquals(1, maker.get(6, 7));
		// row 2
		assertEquals(1, maker.get(8, 9));
		assertEquals(1, maker.get(9, 10));
		assertEquals(1, maker.get(10, 11));
		// column 0
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 8));
		// column 1
		assertEquals(1, maker.get(1, 5));
		assertEquals(1, maker.get(5, 9));
		// column 2
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(6, 10));
		// column 3
		assertEquals(1, maker.get(3, 7));
		assertEquals(1, maker.get(7, 11));

		assertEquals(17, maker.totalNumTransitions());
	}

	@Test
	public void createsSmallLatticeLoopedRows() throws GraphCreationException {
		Lattice lattice = new Lattice(3, 4, true, false);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12, maker.numNodes());
		assertEquals(12, maker.totalNumNodes());
		assertEquals(12, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// row 0
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 0));
		// row 1
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 6));
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 4));
		// row 2
		assertEquals(1, maker.get(8, 9));
		assertEquals(1, maker.get(9, 10));
		assertEquals(1, maker.get(10, 11));
		assertEquals(1, maker.get(11, 8));
		// column 0
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 8));
		// column 1
		assertEquals(1, maker.get(1, 5));
		assertEquals(1, maker.get(5, 9));
		// column 2
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(6, 10));
		// column 3
		assertEquals(1, maker.get(3, 7));
		assertEquals(1, maker.get(7, 11));

		assertEquals(20, maker.totalNumTransitions());
	}

	@Test
	public void createsSmallLatticeLoopedColumns() throws GraphCreationException {
		Lattice lattice = new Lattice(3, 4, false, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12, maker.numNodes());
		assertEquals(12, maker.totalNumNodes());
		assertEquals(12, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// row 0
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 3));
		// row 1
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 6));
		assertEquals(1, maker.get(6, 7));
		// row 2
		assertEquals(1, maker.get(8, 9));
		assertEquals(1, maker.get(9, 10));
		assertEquals(1, maker.get(10, 11));
		// column 0
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 8));
		assertEquals(1, maker.get(8, 0));
		// column 1
		assertEquals(1, maker.get(1, 5));
		assertEquals(1, maker.get(5, 9));
		assertEquals(1, maker.get(9, 1));
		// column 2
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(6, 10));
		assertEquals(1, maker.get(10, 2));
		// column 3
		assertEquals(1, maker.get(3, 7));
		assertEquals(1, maker.get(7, 11));
		assertEquals(1, maker.get(11, 3));

		assertEquals(21, maker.totalNumTransitions());
	}

	@Test
	public void createsSmallLatticeLoopedRowsAndColumns() throws GraphCreationException {
		Lattice lattice = new Lattice(3, 4, true, true);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12, maker.numNodes());
		assertEquals(12, maker.totalNumNodes());
		assertEquals(12, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// row 0
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 3));
		assertEquals(1, maker.get(3, 0));
		// row 1
		assertEquals(1, maker.get(4, 5));
		assertEquals(1, maker.get(5, 6));
		assertEquals(1, maker.get(6, 7));
		assertEquals(1, maker.get(7, 4));
		// row 2
		assertEquals(1, maker.get(8, 9));
		assertEquals(1, maker.get(9, 10));
		assertEquals(1, maker.get(10, 11));
		assertEquals(1, maker.get(11, 8));
		// column 0
		assertEquals(1, maker.get(0, 4));
		assertEquals(1, maker.get(4, 8));
		assertEquals(1, maker.get(8, 0));
		// column 1
		assertEquals(1, maker.get(1, 5));
		assertEquals(1, maker.get(5, 9));
		assertEquals(1, maker.get(9, 1));
		// column 2
		assertEquals(1, maker.get(2, 6));
		assertEquals(1, maker.get(6, 10));
		assertEquals(1, maker.get(10, 2));
		// column 3
		assertEquals(1, maker.get(3, 7));
		assertEquals(1, maker.get(7, 11));
		assertEquals(1, maker.get(11, 3));

		assertEquals(24, maker.totalNumTransitions());
	}

	@Test
	public void testCanSynthetizeTranspose() throws GraphCreationException {
		assertTrue(new Lattice(3, 4, false, false).canSynthetizeTranspose());
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("Lattice(loop=rows&cols, rows=3, columns=4)", new Lattice(3, 4, true, true).toString());
		assertEquals("Lattice(loop=rows, rows=3, columns=4)", new Lattice(3, 4, true, false).toString());
		assertEquals("Lattice(loop=cols, rows=3, columns=4)", new Lattice(3, 4, false, true).toString());
		assertEquals("Lattice(loop=none, rows=3, columns=4)", new Lattice(3, 4, false, false).toString());
	}
}
