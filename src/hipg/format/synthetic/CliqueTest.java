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

public class CliqueTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateEmptyClique() throws GraphCreationException {
		new Clique(0);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeSizeClique() throws GraphCreationException {
		new Clique(-1);
	}

	@Test
	public void computesSize() throws GraphCreationException {
		assertEquals(100, new Clique(100).estimateGlobalNodes());
		assertEquals(100 * 99, new Clique(100).estimateGlobalTransitions());
	}

	@Test
	public void canComputeTranspose() throws GraphCreationException {
		assertTrue(new Clique(10).canSynthetizeTranspose());
	}

	@Test
	public void createsOneElementClique() throws GraphCreationException {
		Clique clique = new Clique(1);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(clique);
		clique.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
	}

	@Test
	public void createsTwoElementsClique() throws GraphCreationException {
		Clique clique = new Clique(2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(clique);
		clique.create(maker);
		assertEquals(2, maker.numNodes());
		assertEquals(2, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(1, 0));
		assertEquals(0, maker.get(1, 1));
	}

	@Test
	public void createsThreeElementsClique() throws GraphCreationException {
		Clique clique = new Clique(3);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(clique);
		clique.create(maker);
		assertEquals(3, maker.numNodes());
		assertEquals(3, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
		assertEquals(1, maker.get(0, 1));
		assertEquals(1, maker.get(0, 2));
		assertEquals(1, maker.get(1, 0));
		assertEquals(0, maker.get(1, 1));
		assertEquals(1, maker.get(1, 2));
		assertEquals(1, maker.get(2, 0));
		assertEquals(1, maker.get(2, 1));
		assertEquals(0, maker.get(2, 2));
	}

	@Test
	public void createsTenElementsClique() throws GraphCreationException {
		Clique clique = new Clique(10);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(clique);
		clique.create(maker);
		assertEquals(10, maker.numNodes());
		assertEquals(10, maker.totalNumNodes());
		for (int i = 0; i < 10; ++i) {
			for (int j = 0; j < 10; ++j) {
				assertEquals(i != j ? 1 : 0, maker.get(i, j));
			}
		}
	}

	@Test
	public void testToStclique() throws GraphCreationException {
		assertEquals("Clique(100)", new Clique(100).toString());
	}
}
