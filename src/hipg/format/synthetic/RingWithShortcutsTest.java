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

import java.util.Random;

import myutils.test.TestUtils;

import org.junit.Test;

public class RingWithShortcutsTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeSizeRingWithShortcuts() throws GraphCreationException {
		new RingWithShortcuts(-1, 1, new Random(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateZeroSizeRingWithShortcuts() throws GraphCreationException {
		new RingWithShortcuts(0, 1, new Random(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeShortcuts() throws GraphCreationException {
		new RingWithShortcuts(10, -1, new Random(1));
	}

	@Test
	public void createsSmallRingWithShortcuts() throws GraphCreationException {
		RingWithShortcuts ringWithShortcuts = new RingWithShortcuts(100, 10, new Random(System.nanoTime()));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(ringWithShortcuts);
		ringWithShortcuts.create(maker);
		assertEquals(100, maker.totalNumNodes());
		assertEquals(100, maker.numNodes());
		for (int src = 0; src < maker.numNodes(); ++src) {
			assertEquals(11, maker.degree(src));
		}
		TestUtils.assertNearRelative(1000, maker.totalNumTransitions(), 0.1);
		TestUtils.assertNearRelative(1000, ringWithShortcuts.estimateGlobalTransitions(), 0.1);
	}

	@Test
	public void createsSmallRingWithShortcutsTestingIndegrees() throws GraphCreationException {
		int shortcuts = 1000;
		RingWithShortcuts ringWithShortcuts = new RingWithShortcuts(10, shortcuts, new Random(System.nanoTime()));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(ringWithShortcuts);
		ringWithShortcuts.create(maker);
		assertEquals(10, maker.totalNumNodes());
		for (int src = 0; src < maker.numNodes(); ++src) {
			TestUtils.assertNearRelative(shortcuts, maker.indegree(src), 0.1);
		}
	}

	@Test
	public void testToString() throws GraphCreationException {
		assertEquals("RingWithShortcuts(n=100, shortcuts=10)", new RingWithShortcuts(100, 10, new Random(1)).toString());
	}
}
