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

package hipg.app.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import junit.framework.Assert;

import myutils.storage.LongPairIterator;

import org.junit.Test;

public class HistogramTest {

	@Test
	public void testEmpty() {
		Histogram c = new Histogram();
		Assert.assertEquals(0, c.count());
		Assert.assertEquals(0, c.count(1));
		Assert.assertEquals(0, c.count(10));
		Assert.assertEquals(0, c.count(1000000));
	}

	@Test
	public void testOne1() {
		Histogram c = new Histogram();
		c.add(1);
		Assert.assertEquals(1, c.count());
		Assert.assertEquals(1, c.count(1));
		Assert.assertEquals(0, c.count(2));
		Assert.assertEquals(0, c.count(1000000));
	}

	@Test
	public void testOne2() {
		Histogram c = new Histogram();
		c.add(18);
		Assert.assertEquals(1, c.count());
		Assert.assertEquals(1, c.count(18));
		Assert.assertEquals(0, c.count(1));
		Assert.assertEquals(0, c.count(2));
		Assert.assertEquals(0, c.count(1000000));
	}

	@Test
	public void testOne3() {
		Histogram c = new Histogram();
		c.add(1818181818181L);
		Assert.assertEquals(1, c.count());
		Assert.assertEquals(1, c.count(1818181818181L));
		Assert.assertEquals(0, c.count(1));
		Assert.assertEquals(0, c.count(2));
		Assert.assertEquals(0, c.count(1000000));
	}

	@Test
	public void testTwo1() {
		Histogram c = new Histogram();
		c.add(1818181818181L);
		c.add(33);
		Assert.assertEquals(2, c.count());
		Assert.assertEquals(1, c.count(1818181818181L));
		Assert.assertEquals(1, c.count(33));
		Assert.assertEquals(0, c.count(1));
		Assert.assertEquals(0, c.count(2));
		Assert.assertEquals(0, c.count(1000000));
	}

	@Test
	public void testTwo2() {
		Histogram c = new Histogram();
		c.add(33);
		c.add(33);
		Assert.assertEquals(2, c.count());
		Assert.assertEquals(2, c.count(33));
		Assert.assertEquals(0, c.count(34));
		Assert.assertEquals(0, c.count(35));
		Assert.assertEquals(0, c.count(32));
		Assert.assertEquals(0, c.count(31));
		Assert.assertEquals(0, c.count(1));
		Assert.assertEquals(0, c.count(2));
		Assert.assertEquals(0, c.count(1000000));
	}

	@Test
	public void testTwo3() {
		Histogram c = new Histogram();
		c.add(33, 10);
		c.add(33, 100);
		c.add(10, 20);
		Assert.assertEquals(130, c.count());
		Assert.assertEquals(110, c.count(33));
		Assert.assertEquals(20, c.count(10));
		Assert.assertEquals(0, c.count(34));
		Assert.assertEquals(0, c.count(35));
		Assert.assertEquals(0, c.count(32));
		Assert.assertEquals(0, c.count(31));
		Assert.assertEquals(0, c.count(1));
		Assert.assertEquals(0, c.count(2));
		Assert.assertEquals(0, c.count(1000000));
	}

	@Test
	public void testTwo4() {
		Histogram c = new Histogram();
		c.add(1, 10);
		c.add(1, 100);
		c.add(10, 20);
		Assert.assertEquals(130, c.count());
		Assert.assertEquals(110, c.count(1));
		Assert.assertEquals(20, c.count(10));
		Assert.assertEquals(0, c.count(34));
		Assert.assertEquals(0, c.count(35));
		Assert.assertEquals(0, c.count(32));
		Assert.assertEquals(0, c.count(31));
		Assert.assertEquals(0, c.count(3));
		Assert.assertEquals(0, c.count(2));
		Assert.assertEquals(0, c.count(1000000));
	}

	@Test
	public void testTwo5() {
		Histogram c = new Histogram();
		c.add(20000, 10);
		c.add(20000, 100);
		c.add(20001, 20);
		Assert.assertEquals(130, c.count());
		Assert.assertEquals(110, c.count(20000));
		Assert.assertEquals(20, c.count(20001));
		Assert.assertEquals(0, c.count(34));
		Assert.assertEquals(0, c.count(35));
		Assert.assertEquals(0, c.count(32));
		Assert.assertEquals(0, c.count(31));
		Assert.assertEquals(0, c.count(1));
		Assert.assertEquals(0, c.count(2));
		Assert.assertEquals(0, c.count(1000000));
	}

	private void test(int numValues) {
		Histogram c = new Histogram();
		Random rand = new Random();
		Map<Long, Long> map = new HashMap<Long, Long>();
		long sumC = 0;
		for (int i = 0; i < numValues; i++) {
			long size = (i == 0 ? 1 : (i < 1000 ? rand.nextInt(1024 * 4) : rand.nextLong()));
			if (size < 0) {
				size = (size == Long.MIN_VALUE ? Long.MAX_VALUE : -size);
			} else if (size == 0) {
				size = 1;
			}
			long count = 1 + rand.nextInt(10000);
			Long mapC = map.get(size);
			if (mapC == null) {
				mapC = new Long(0);
			}
			mapC += count;
			sumC += count;
			map.put(size, mapC);
			c.add(size, count);
			Assert.assertEquals(mapC.longValue(), c.count(size));
			Assert.assertEquals(sumC, c.count());
		}
	}

	@Test
	public void testSmall() {
		test(10);
	}

	@Test
	public void testMed() {
		test(10000);
	}

	@Test
	public void testBig() {
		test(50000);
	}

	private void testSerialization(int numValues) {
		Histogram c = new Histogram();
		Random rand = new Random(System.nanoTime());
		for (int i = 0; i < numValues; i++) {
			long size = (i == 0 ? 1 : (i < 1000 ? rand.nextInt(1024 * 4) : rand.nextLong()));
			if (size < 0) {
				size = (size == Long.MIN_VALUE ? Long.MAX_VALUE : -size);
			} else if (size == 0) {
				size = 1;
			}
			long count = 1 + rand.nextInt(10000);
			c.add(size, count);
		}

		final int length = c.length();
		byte[] buf = new byte[length];
		c.write(buf, 0);
		Histogram d = new Histogram();
		d.read(buf, 0);
		byte[] buf2 = new byte[length];
		d.write(buf2, 0);
		for (int i = 0; i < length; i++) {
			Assert.assertEquals(buf[i], buf2[i]);
		}
	}

	@Test
	public void testSerialization1() {
		testSerialization(10);
	}

	@Test
	public void testSerialization2() {
		testSerialization(100);
	}

	@Test
	public void testSerialization3() {
		testSerialization(10000);
	}

	private void testEnumeration(int numValues) {
		Histogram c = new Histogram();
		Random rand = new Random(2);
		Map<Long, Long> map = new HashMap<Long, Long>();
		for (int i = 0; i < numValues; i++) {
			long size = (i == 0 ? 1 : (i < 1000 ? rand.nextInt(1024 * 4) : rand.nextLong()));
			if (size < 0) {
				size = (size == Long.MIN_VALUE ? Long.MAX_VALUE : -size);
			} else if (size == 0) {
				size = 1;
			}
			long count = 1 + rand.nextInt(10000);
			c.add(size, count);
			Long cmap = map.get(size);
			if (cmap == null) {
				cmap = new Long(0);
			}
			cmap += count;
			map.put(size, cmap);
		}
		int mapSize = map.size();
		LongPairIterator it = c.iterator();
		int values = 0;
		while (it.hasNext()) {
			long size = it.next();
			long value = it.value();
			Assert.assertTrue("should contain size " + size, map.containsKey(size));
			Assert.assertTrue(c.count(size) == value);
			values++;
			map.remove(size);
		}
		Assert.assertEquals(mapSize, values);
		Assert.assertTrue(map.isEmpty());
	}

	@Test
	public void testEnumeration1() {
		testEnumeration(10);
	}

	@Test
	public void testEnumeration2() {
		testEnumeration(100);
	}

	@Test
	public void testEnumeration3() {
		testEnumeration(10000);
	}

	@Test
	public void testEnumeration4() {
		testEnumeration(2);
	}
}
