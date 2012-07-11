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

public class LatticeOfSubgraphsTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeRowsLattice() throws GraphCreationException {
		new LatticeOfSubgraphs(-1, 10, 1, 1, false, false, new Line(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreatezeroRowsLattice() throws GraphCreationException {
		new LatticeOfSubgraphs(0, 10, 1, 1, false, false, new Line(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeColumnsLattice() throws GraphCreationException {
		new LatticeOfSubgraphs(10, -10, 1, 1, false, false, new Line(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateZeroColumnsLattice() throws GraphCreationException {
		new LatticeOfSubgraphs(10, 0, 1, 1, false, false, new Line(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNullSubgraphLattice() throws GraphCreationException {
		new LatticeOfSubgraphs(10, 10, 1, 1, false, false, null);
	}

	@Test
	public void createsOnebyOneLattice() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(1, 1, 1, 1, false, false, new Line(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
	}

	@Test
	public void createsOneByOneLoopedLattice() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(1, 1, 1, 1, true, true, new Line(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(2, maker.get(0, 0));
	}

	@Test
	public void createsSmallLattice() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, false, false, new Clique(1));
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
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, false, new Line(1));
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
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, false, true, new Line(1));
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
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new Line(1));
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
	public void createsLatticeOfLines() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new Line(3));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12 * 3, maker.numNodes());
		assertEquals(12 * 3, maker.totalNumNodes());
		assertEquals(12 * 3, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// lattice transitions
		assertEquals(1, maker.get(0 * 3, 1 * 3));
		assertEquals(1, maker.get(1 * 3, 2 * 3));
		assertEquals(1, maker.get(2 * 3, 3 * 3));
		assertEquals(1, maker.get(3 * 3, 0 * 3));
		assertEquals(1, maker.get(4 * 3, 5 * 3));
		assertEquals(1, maker.get(5 * 3, 6 * 3));
		assertEquals(1, maker.get(6 * 3, 7 * 3));
		assertEquals(1, maker.get(7 * 3, 4 * 3));
		assertEquals(1, maker.get(8 * 3, 9 * 3));
		assertEquals(1, maker.get(9 * 3, 10 * 3));
		assertEquals(1, maker.get(10 * 3, 11 * 3));
		assertEquals(1, maker.get(11 * 3, 8 * 3));
		assertEquals(1, maker.get(0 * 3, 4 * 3));
		assertEquals(1, maker.get(4 * 3, 8 * 3));
		assertEquals(1, maker.get(8 * 3, 0 * 3));
		assertEquals(1, maker.get(1 * 3, 5 * 3));
		assertEquals(1, maker.get(5 * 3, 9 * 3));
		assertEquals(1, maker.get(9 * 3, 1 * 3));
		assertEquals(1, maker.get(2 * 3, 6 * 3));
		assertEquals(1, maker.get(6 * 3, 10 * 3));
		assertEquals(1, maker.get(10 * 3, 2 * 3));
		assertEquals(1, maker.get(3 * 3, 7 * 3));
		assertEquals(1, maker.get(7 * 3, 11 * 3));
		assertEquals(1, maker.get(11 * 3, 3 * 3));

		// line transitions
		for (int i = 0; i < 12; ++i) {
			int start = i * 3;
			assertEquals(1, maker.get(start + 0, start + 1));
			assertEquals(1, maker.get(start + 1, start + 2));
		}

		assertEquals(24 + 2 * 12, maker.totalNumTransitions());
	}

	@Test
	public void createsLatticeOfBiLines() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new BiLine(3));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12 * 3, maker.numNodes());
		assertEquals(12 * 3, maker.totalNumNodes());
		assertEquals(12 * 3, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// lattice transitions
		assertEquals(1, maker.get(0 * 3, 1 * 3));
		assertEquals(1, maker.get(1 * 3, 2 * 3));
		assertEquals(1, maker.get(2 * 3, 3 * 3));
		assertEquals(1, maker.get(3 * 3, 0 * 3));
		assertEquals(1, maker.get(4 * 3, 5 * 3));
		assertEquals(1, maker.get(5 * 3, 6 * 3));
		assertEquals(1, maker.get(6 * 3, 7 * 3));
		assertEquals(1, maker.get(7 * 3, 4 * 3));
		assertEquals(1, maker.get(8 * 3, 9 * 3));
		assertEquals(1, maker.get(9 * 3, 10 * 3));
		assertEquals(1, maker.get(10 * 3, 11 * 3));
		assertEquals(1, maker.get(11 * 3, 8 * 3));
		assertEquals(1, maker.get(0 * 3, 4 * 3));
		assertEquals(1, maker.get(4 * 3, 8 * 3));
		assertEquals(1, maker.get(8 * 3, 0 * 3));
		assertEquals(1, maker.get(1 * 3, 5 * 3));
		assertEquals(1, maker.get(5 * 3, 9 * 3));
		assertEquals(1, maker.get(9 * 3, 1 * 3));
		assertEquals(1, maker.get(2 * 3, 6 * 3));
		assertEquals(1, maker.get(6 * 3, 10 * 3));
		assertEquals(1, maker.get(10 * 3, 2 * 3));
		assertEquals(1, maker.get(3 * 3, 7 * 3));
		assertEquals(1, maker.get(7 * 3, 11 * 3));
		assertEquals(1, maker.get(11 * 3, 3 * 3));

		// line transitions
		for (int i = 0; i < 12; ++i) {
			int start = i * 3;
			assertEquals(1, maker.get(start + 0, start + 1));
			assertEquals(1, maker.get(start + 1, start + 2));
			assertEquals(1, maker.get(start + 1, start + 0));
			assertEquals(1, maker.get(start + 2, start + 1));
		}

		assertEquals(24 + 4 * 12, maker.totalNumTransitions());
	}

	@Test
	public void createsLatticeOfRings() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new Ring(4));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12 * 4, maker.numNodes());
		assertEquals(12 * 4, maker.totalNumNodes());
		assertEquals(12 * 4, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// lattice transitions
		assertEquals(1, maker.get(0 * 4, 1 * 4));
		assertEquals(1, maker.get(1 * 4, 2 * 4));
		assertEquals(1, maker.get(2 * 4, 3 * 4));
		assertEquals(1, maker.get(3 * 4, 0 * 4));
		assertEquals(1, maker.get(4 * 4, 5 * 4));
		assertEquals(1, maker.get(5 * 4, 6 * 4));
		assertEquals(1, maker.get(6 * 4, 7 * 4));
		assertEquals(1, maker.get(7 * 4, 4 * 4));
		assertEquals(1, maker.get(8 * 4, 9 * 4));
		assertEquals(1, maker.get(9 * 4, 10 * 4));
		assertEquals(1, maker.get(10 * 4, 11 * 4));
		assertEquals(1, maker.get(11 * 4, 8 * 4));
		assertEquals(1, maker.get(0 * 4, 4 * 4));
		assertEquals(1, maker.get(4 * 4, 8 * 4));
		assertEquals(1, maker.get(8 * 4, 0 * 4));
		assertEquals(1, maker.get(1 * 4, 5 * 4));
		assertEquals(1, maker.get(5 * 4, 9 * 4));
		assertEquals(1, maker.get(9 * 4, 1 * 4));
		assertEquals(1, maker.get(2 * 4, 6 * 4));
		assertEquals(1, maker.get(6 * 4, 10 * 4));
		assertEquals(1, maker.get(10 * 4, 2 * 4));
		assertEquals(1, maker.get(3 * 4, 7 * 4));
		assertEquals(1, maker.get(7 * 4, 11 * 4));
		assertEquals(1, maker.get(11 * 4, 3 * 4));

		// line transitions
		for (int i = 0; i < 12; ++i) {
			int start = i * 4;
			assertEquals(1, maker.get(start + 0, start + 1));
			assertEquals(1, maker.get(start + 1, start + 2));
			assertEquals(1, maker.get(start + 2, start + 3));
			assertEquals(1, maker.get(start + 3, start + 0));
		}

		assertEquals(24 + 4 * 12, maker.totalNumTransitions());
	}

	@Test
	public void createsLatticeOfBiRings() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new BiRing(4));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12 * 4, maker.numNodes());
		assertEquals(12 * 4, maker.totalNumNodes());
		assertEquals(12 * 4, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// lattice transitions
		assertEquals(1, maker.get(0 * 4, 1 * 4));
		assertEquals(1, maker.get(1 * 4, 2 * 4));
		assertEquals(1, maker.get(2 * 4, 3 * 4));
		assertEquals(1, maker.get(3 * 4, 0 * 4));
		assertEquals(1, maker.get(4 * 4, 5 * 4));
		assertEquals(1, maker.get(5 * 4, 6 * 4));
		assertEquals(1, maker.get(6 * 4, 7 * 4));
		assertEquals(1, maker.get(7 * 4, 4 * 4));
		assertEquals(1, maker.get(8 * 4, 9 * 4));
		assertEquals(1, maker.get(9 * 4, 10 * 4));
		assertEquals(1, maker.get(10 * 4, 11 * 4));
		assertEquals(1, maker.get(11 * 4, 8 * 4));
		assertEquals(1, maker.get(0 * 4, 4 * 4));
		assertEquals(1, maker.get(4 * 4, 8 * 4));
		assertEquals(1, maker.get(8 * 4, 0 * 4));
		assertEquals(1, maker.get(1 * 4, 5 * 4));
		assertEquals(1, maker.get(5 * 4, 9 * 4));
		assertEquals(1, maker.get(9 * 4, 1 * 4));
		assertEquals(1, maker.get(2 * 4, 6 * 4));
		assertEquals(1, maker.get(6 * 4, 10 * 4));
		assertEquals(1, maker.get(10 * 4, 2 * 4));
		assertEquals(1, maker.get(3 * 4, 7 * 4));
		assertEquals(1, maker.get(7 * 4, 11 * 4));
		assertEquals(1, maker.get(11 * 4, 3 * 4));

		// line transitions
		for (int i = 0; i < 12; ++i) {
			int start = i * 4;
			assertEquals(1, maker.get(start + 0, start + 1));
			assertEquals(1, maker.get(start + 1, start + 2));
			assertEquals(1, maker.get(start + 2, start + 3));
			assertEquals(1, maker.get(start + 3, start + 0));
			assertEquals(1, maker.get(start + 1, start + 0));
			assertEquals(1, maker.get(start + 2, start + 1));
			assertEquals(1, maker.get(start + 3, start + 2));
			assertEquals(1, maker.get(start + 0, start + 3));
		}

		assertEquals(24 + 8 * 12, maker.totalNumTransitions());
	}

	@Test
	public void createsLatticeOfCliques() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new Clique(4));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12 * 4, maker.numNodes());
		assertEquals(12 * 4, maker.totalNumNodes());
		assertEquals(12 * 4, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// lattice transitions
		assertEquals(1, maker.get(0 * 4, 1 * 4));
		assertEquals(1, maker.get(1 * 4, 2 * 4));
		assertEquals(1, maker.get(2 * 4, 3 * 4));
		assertEquals(1, maker.get(3 * 4, 0 * 4));
		assertEquals(1, maker.get(4 * 4, 5 * 4));
		assertEquals(1, maker.get(5 * 4, 6 * 4));
		assertEquals(1, maker.get(6 * 4, 7 * 4));
		assertEquals(1, maker.get(7 * 4, 4 * 4));
		assertEquals(1, maker.get(8 * 4, 9 * 4));
		assertEquals(1, maker.get(9 * 4, 10 * 4));
		assertEquals(1, maker.get(10 * 4, 11 * 4));
		assertEquals(1, maker.get(11 * 4, 8 * 4));
		assertEquals(1, maker.get(0 * 4, 4 * 4));
		assertEquals(1, maker.get(4 * 4, 8 * 4));
		assertEquals(1, maker.get(8 * 4, 0 * 4));
		assertEquals(1, maker.get(1 * 4, 5 * 4));
		assertEquals(1, maker.get(5 * 4, 9 * 4));
		assertEquals(1, maker.get(9 * 4, 1 * 4));
		assertEquals(1, maker.get(2 * 4, 6 * 4));
		assertEquals(1, maker.get(6 * 4, 10 * 4));
		assertEquals(1, maker.get(10 * 4, 2 * 4));
		assertEquals(1, maker.get(3 * 4, 7 * 4));
		assertEquals(1, maker.get(7 * 4, 11 * 4));
		assertEquals(1, maker.get(11 * 4, 3 * 4));

		// line transitions
		for (int i = 0; i < 12; ++i) {
			int start = i * 4;
			assertEquals(1, maker.get(start + 0, start + 1));
			assertEquals(1, maker.get(start + 0, start + 2));
			assertEquals(1, maker.get(start + 0, start + 3));
			assertEquals(1, maker.get(start + 1, start + 0));
			assertEquals(1, maker.get(start + 1, start + 2));
			assertEquals(1, maker.get(start + 1, start + 3));
			assertEquals(1, maker.get(start + 2, start + 0));
			assertEquals(1, maker.get(start + 2, start + 1));
			assertEquals(1, maker.get(start + 2, start + 3));
			assertEquals(1, maker.get(start + 3, start + 0));
			assertEquals(1, maker.get(start + 3, start + 1));
			assertEquals(1, maker.get(start + 3, start + 2));
		}

		assertEquals(24 + 12 * 12, maker.totalNumTransitions());
	}

	@Test
	public void createsLatticeOfBinTrees() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new Tree(2, 2));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12 * 7, maker.numNodes());
		assertEquals(12 * 7, maker.totalNumNodes());
		assertEquals(12 * 7, lattice.estimateGlobalNodes());
		assertEquals(lattice.estimateGlobalTransitions(), maker.totalNumTransitions());

		// lattice transitions
		assertEquals(1, maker.get(0 * 7, 1 * 7));
		assertEquals(1, maker.get(1 * 7, 2 * 7));
		assertEquals(1, maker.get(2 * 7, 3 * 7));
		assertEquals(1, maker.get(3 * 7, 0 * 7));
		assertEquals(1, maker.get(4 * 7, 5 * 7));
		assertEquals(1, maker.get(5 * 7, 6 * 7));
		assertEquals(1, maker.get(6 * 7, 7 * 7));
		assertEquals(1, maker.get(7 * 7, 4 * 7));
		assertEquals(1, maker.get(8 * 7, 9 * 7));
		assertEquals(1, maker.get(9 * 7, 10 * 7));
		assertEquals(1, maker.get(10 * 7, 11 * 7));
		assertEquals(1, maker.get(11 * 7, 8 * 7));
		assertEquals(1, maker.get(0 * 7, 4 * 7));
		assertEquals(1, maker.get(4 * 7, 8 * 7));
		assertEquals(1, maker.get(8 * 7, 0 * 7));
		assertEquals(1, maker.get(1 * 7, 5 * 7));
		assertEquals(1, maker.get(5 * 7, 9 * 7));
		assertEquals(1, maker.get(9 * 7, 1 * 7));
		assertEquals(1, maker.get(2 * 7, 6 * 7));
		assertEquals(1, maker.get(6 * 7, 10 * 7));
		assertEquals(1, maker.get(10 * 7, 2 * 7));
		assertEquals(1, maker.get(3 * 7, 7 * 7));
		assertEquals(1, maker.get(7 * 7, 11 * 7));
		assertEquals(1, maker.get(11 * 7, 3 * 7));

		// line transitions
		for (int i = 0; i < 12; ++i) {
			int start = i * 7;
			assertEquals(1, maker.get(start + 0, start + 1));
			assertEquals(1, maker.get(start + 1, start + 2));
			assertEquals(1, maker.get(start + 1, start + 3));
			assertEquals(1, maker.get(start + 0, start + 4));
			assertEquals(1, maker.get(start + 4, start + 5));
			assertEquals(1, maker.get(start + 4, start + 6));
		}

		assertEquals(24 + 6 * 12, maker.totalNumTransitions());
	}

	@Test
	public void createsLatticeOfLatticesOfCliques() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new LatticeOfSubgraphs(2, 2, 2, 2,
				true, true, new Clique(5)));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12 * 4 * 5, maker.numNodes());
		assertEquals(12 * 4 * 5, maker.totalNumNodes());
		assertEquals(12 * 4 * 5, lattice.estimateGlobalNodes());
		assertEquals(24 + 12 * (8 + 4 * (5 * 4)), lattice.estimateGlobalTransitions());
		assertEquals(24 + 12 * (8 + 4 * (5 * 4)), maker.totalNumTransitions());
	}

	@Test
	public void createsLatticeOfTreesOfCliques() throws GraphCreationException {
		LatticeOfSubgraphs lattice = new LatticeOfSubgraphs(3, 4, 3, 4, true, true, new TreeOfSubgraphs(5, 2,
				new Clique(3)));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(lattice);
		lattice.create(maker);
		assertEquals(12 * 63 * 3, maker.numNodes());
		assertEquals(12 * 63 * 3, maker.totalNumNodes());
		assertEquals(12 * 63 * 3, lattice.estimateGlobalNodes());
		assertEquals(24 + 12 * (62 + 63 * (3 * 2)), lattice.estimateGlobalTransitions());
		assertEquals(24 + 12 * (62 + 63 * (3 * 2)), maker.totalNumTransitions());
	}

	@Test
	public void testCanSynthetizeTranspose() throws GraphCreationException {
		assertTrue(new LatticeOfSubgraphs(3, 4, 3, 4, false, false, new Line(2)).canSynthetizeTranspose());
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("Lattice(loop=none, rows=5/7, columns=1/2) of " + (new Line(10).toString()),
				new LatticeOfSubgraphs(7, 2, 5, 1, false, false, new Line(10)).toString());
		assertEquals("Lattice(loop=rows, rows=5/7, columns=1/2) of " + (new Ring(10).toString()),
				new LatticeOfSubgraphs(7, 2, 5, 1, true, false, new Ring(10)).toString());
		assertEquals("Lattice(loop=cols, rows=5/7, columns=1/2) of " + (new Ring(10).toString()),
				new LatticeOfSubgraphs(7, 2, 5, 1, false, true, new Ring(10)).toString());
		assertEquals("Lattice(loop=rows&cols, rows=5/7, columns=1/2) of " + (new Ring(10).toString()),
				new LatticeOfSubgraphs(7, 2, 5, 1, true, true, new Ring(10)).toString());
	}
}
