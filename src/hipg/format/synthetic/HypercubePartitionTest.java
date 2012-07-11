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

import java.util.Vector;

import junit.framework.Assert;

import org.junit.Test;

public class HypercubePartitionTest {

	private static final class Element {

		private long n;
		private int index;

		public Element(long n, int index) {
			super();
			this.n = n;
			this.index = index;
		}

		public long getN() {
			return n;
		}

		public int getIndex() {
			return index;
		}

	}

	@SuppressWarnings({ "unchecked" })
	private Vector<Element>[] create(HypercubePartition partition) {
		Vector<Element>[] map = new Vector[partition.getPoolSize()];
		for (int i = 0; i < partition.getPoolSize(); i++) {
			map[i] = new Vector<Element>();
		}
		for (long n = 0; n < partition.getStateSpaceSize(); n++) {
			int owner = partition.owner(n);
			int index = partition.index(n);
			map[owner].add(new Element(n, index));
		}
		return map;
	}

	private void testFast(HypercubePartition partition) {
		Assert.assertTrue(partition.isFast());
		final Vector<Element>[] v = create(partition);
		final int base = (int) (partition.getStateSpaceSize() / partition.getPoolSize());
		for (int owner = 0; owner < v.length; owner++) {
			Assert.assertNotNull(v[owner]);
			final int size = v[owner].size();
			Assert.assertTrue(size == base || size == base + 1);
			final Vector<Element> elements = v[owner];
			for (int j = 1; j < elements.size(); j++) {
				final Element prevElementx = elements.get(j - 1);
				final Element currElement = elements.get(j);
				Assert.assertEquals(prevElementx.getIndex() + 1, currElement.getIndex());
			}
			for (int j = 0; j < elements.size(); j++) {
				final Element element = elements.get(j);
				final long expectedN = element.getN();
				final long backN = partition.back(owner, element.getIndex());
				Assert.assertEquals(expectedN, backN);
			}
		}
	}

	private void testNoRemainder(HypercubePartition partition) {
		Assert.assertFalse(partition.isFast());
		Assert.assertTrue(partition.noRemainder());
		final Vector<Element>[] v = create(partition);
		final int base = (int) (partition.getStateSpaceSize() / partition.getPoolSize());
		for (int owner = 0; owner < v.length; owner++) {
			Assert.assertNotNull(v[owner]);
			final int size = v[owner].size();
			Assert.assertTrue(size == base);
			final Vector<Element> elements = v[owner];
			for (int j = 1; j < elements.size(); j++) {
				final Element prevElementx = elements.get(j - 1);
				final Element currElement = elements.get(j);
				Assert.assertEquals(prevElementx.getIndex() + 1, currElement.getIndex());
			}
			for (int j = 0; j < elements.size(); j++) {
				final Element element = elements.get(j);
				final long expectedN = element.getN();
				final long backN = partition.back(owner, element.getIndex());
				Assert.assertEquals(expectedN, backN);
			}
		}
	}

	private void testRemainder(HypercubePartition partition) {
		Assert.assertFalse(partition.isFast());
		Assert.assertFalse(partition.noRemainder());
		final Vector<Element>[] v = create(partition);
		final int base = (int) (partition.getStateSpaceSize() / partition.getPoolSize());
		for (int owner = 0; owner < v.length; owner++) {
			Assert.assertNotNull(v[owner]);
			final int size = v[owner].size();
			Assert.assertTrue(size == base || size == base + 1);
			final Vector<Element> elements = v[owner];
			for (int j = 1; j < elements.size(); j++) {
				final Element prevElementx = elements.get(j - 1);
				final Element currElement = elements.get(j);
				Assert.assertEquals(prevElementx.getIndex() + 1, currElement.getIndex());
			}
			for (int j = 0; j < elements.size(); j++) {
				final Element element = elements.get(j);
				final long expectedN = element.getN();
				final long backN = partition.back(owner, element.getIndex());
				Assert.assertEquals(expectedN, backN);
			}
		}
	}

	@Test
	public void testFast1() {
		testFast(new HypercubePartition(4, 5, 16));
	}

	@Test
	public void testFast2() {
		testFast(new HypercubePartition(3, 9, 32));
	}

	@Test
	public void testFast3() {
		testFast(new HypercubePartition(7, 4, 8));
	}

	@Test
	public void testNoRemainder1() {
		testNoRemainder(new HypercubePartition(6, 5, 18));
	}

	@Test
	public void testNoRemainder2() {
		testNoRemainder(new HypercubePartition(30, 4, 15));
	}

	@Test
	public void testNoRemainder3() {
		testNoRemainder(new HypercubePartition(17, 2, 17));
	}

	@Test
	public void testRemainder1() {
		testRemainder(new HypercubePartition(30, 4, 171));
	}

	@Test
	public void testRemainder2() {
		testRemainder(new HypercubePartition(5, 7, 17));
	}

	@Test
	public void testRemainder3() {
		testRemainder(new HypercubePartition(7, 4, 13));
	}

}
