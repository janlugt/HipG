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

import java.util.Random;

import hipg.format.GraphCreationException;
import hipg.utils.TestUtils.TestSyntheticGraphMaker;

import myutils.test.TestUtils;

import org.junit.Test;

public class SlowErdosTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeSizeErdos() throws GraphCreationException {
		new SlowErdos(-1, 0.5, new Random(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeProbabilityErdos() throws GraphCreationException {
		new SlowErdos(10, -0.5, new Random(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateIncorrectProbabilityErdos() throws GraphCreationException {
		new SlowErdos(10, 1.1, new Random(1));
	}

	@Test
	public void cannotComputeTranspose() throws GraphCreationException {
		assertFalse(new SlowErdos(100, 0.5, new Random(1)).canSynthetizeTranspose());
	}

	@Test
	public void createsSmallErdos() throws GraphCreationException {
		SlowErdos erdos = new SlowErdos(100, 0.5, new Random(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(erdos);
		erdos.create(maker);
		assertEquals(100, maker.totalNumNodes());
		final int expectedNodes = 100;
		final int expectedTransitions = 100 * 100 / 2;
		assertEquals(expectedNodes, maker.numNodes());
		assertEquals(expectedNodes, maker.totalNumNodes());
		TestUtils.assertNearRelative(expectedTransitions, maker.totalNumTransitions(), 0.1);
		TestUtils.assertNearRelative(expectedTransitions, erdos.estimateGlobalTransitions(), 0.1);
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("Erdos(n=100, p=0.5)", new SlowErdos(100, 0.5, new Random(1)).toString());
	}
}
