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

public class LineTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateEmptyLine() throws GraphCreationException {
		new Line(0);
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeSizeLine() throws GraphCreationException {
		new Line(-1);
	}

	@Test
	public void computesSize() throws GraphCreationException {
		assertEquals(100L, new Line(100).estimateGlobalNodes());
		assertEquals(99L, new Line(100).estimateGlobalTransitions());
	}

	@Test
	public void canComputeTranspose() throws GraphCreationException {
		assertTrue(new Line(10).canSynthetizeTranspose());
	}

	@Test
	public void createsOneElementLine() throws GraphCreationException {
		Line line = new Line(1);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(line);
		line.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
	}

	@Test
	public void createsTwoElementsLine() throws GraphCreationException {
		Line line = new Line(2);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(line);
		line.create(maker);
		assertEquals(2, maker.numNodes());
		assertEquals(2, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
		assertEquals(1, maker.get(0, 1));
		assertEquals(0, maker.get(1, 0));
		assertEquals(0, maker.get(1, 1));
	}

	@Test
	public void createsTenElementsLine() throws GraphCreationException {
		Line line = new Line(10);
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(line);
		line.create(maker);
		assertEquals(10, maker.numNodes());
		assertEquals(10, maker.totalNumNodes());
		for (int i = 0; i < 10; ++i) {
			for (int j = 0; j < 10; ++j) {
				assertEquals(j == i + 1 ? 1 : 0, maker.get(i, j));
			}
		}
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("Line(100)", new Line(100).toString());
	}
}
