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

import static org.junit.Assert.assertEquals;
import hipg.format.GraphCreationException;
import hipg.utils.TestUtils.TestSyntheticGraphMaker;

import java.util.Random;

import myutils.test.TestUtils;

import org.junit.Test;

public class RandomizedTreeTest {

	@Test(expected = GraphCreationException.class)
	public void cannotCreateNegativeSizeTree() throws GraphCreationException {
		new RandomizedTree(-1, new Random(1));
	}

	@Test(expected = GraphCreationException.class)
	public void cannotCreateZeroSizeTree() throws GraphCreationException {
		new RandomizedTree(0, new Random(1));
	}

	@Test
	public void createsOneElementTree() throws GraphCreationException {
		RandomizedTree tree = new RandomizedTree(1, new Random(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree);
		assertEquals(1, tree.estimateGlobalNodes());
		tree.create(maker);
		assertEquals(1, maker.numNodes());
		assertEquals(1, maker.totalNumNodes());
		assertEquals(0, maker.get(0, 0));
	}

	@Test
	public void createsConstantLevelSizeTree() throws GraphCreationException {
		RandomizedTree tree = new RandomizedTree(20, new Random(1));
		TestSyntheticGraphMaker maker = new TestSyntheticGraphMaker(tree, 5000);
		assertEquals(20, tree.estimateGlobalNodes());
		tree.create(maker);
		for (int node = 0; node < 20; ++node) {
			TestUtils.assertLe(maker.degree(node), 2);
		}
	}
}
