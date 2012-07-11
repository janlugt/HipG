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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertTrue;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import myutils.storage.LongValuePairIterator;
import myutils.storage.map.LongToLongHashMap;

import org.junit.Test;

public class SccStructureTest {

	@Test
	public void addsComponents() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(2, 20);
		structure.addComponent(3, 30);
		assertEquals(1, structure.getComponent(1).getId());
		assertEquals(2, structure.getComponent(2).getId());
		assertEquals(3, structure.getComponent(3).getId());
		assertEquals(10, structure.getComponent(1).getSize());
		assertEquals(20, structure.getComponent(2).getSize());
		assertEquals(30, structure.getComponent(3).getSize());
		assertEquals(3, structure.numComponents());
		assertEquals(0, structure.totalNumTransitions());
		assertEquals(0, structure.numComponentsWithTransitions());

		LongValuePairIterator<Scc> iterator = structure.components();
		for (int i = 0; i < 3; ++i) {
			assertTrue(iterator.hasNext());
			final long id = iterator.next();
			final Scc component = iterator.value();
			assertNotNull(component);
			assertEquals(id, component.getId());
			if (id == 1L) {
				assertEquals(10, component.getSize());
			} else if (id == 2L) {
				assertEquals(20, component.getSize());
			} else {
				assertEquals(30, component.getSize());
			}
		}
		assertFalse(iterator.hasNext());
	}

	@Test(expected = RuntimeException.class)
	public void cannotAddComponentWithNegativeSize() {
		new SccStructure().addComponent(1, -1);
	}

	@Test(expected = RuntimeException.class)
	public void cannotAddComponentWithZeroSize() {
		new SccStructure().addComponent(1, 0);
	}

	@Test(expected = RuntimeException.class)
	public void cannotAddComponentTwice() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(1, 11);
	}

	@Test
	public void addsTransitions() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(2, 20);
		structure.addComponent(3, 30);
		assertTrue(structure.addTransition(1, 2));
		assertTrue(structure.addTransition(1, 3));
		assertFalse(structure.addTransition(2, 2));
		assertTrue(structure.hasTransition(1, 2));
		assertTrue(structure.hasTransition(1, 3));
		assertFalse(structure.hasTransition(1, 1));
		assertFalse(structure.hasTransition(2, 1));
		assertFalse(structure.hasTransition(2, 2));
		assertFalse(structure.hasTransition(2, 3));
		assertFalse(structure.hasTransition(3, 1));
		assertFalse(structure.hasTransition(3, 2));
		assertFalse(structure.hasTransition(3, 3));
		assertEquals(2, structure.totalNumTransitions());
		assertEquals(1, structure.numComponentsWithTransitions());
	}

	@Test(expected = RuntimeException.class)
	public void cannotCreateUndirectedEdges() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(2, 20);
		structure.addTransition(1, 2);
		structure.addTransition(2, 1);
	}

	@Test
	public void addsTransitionsResize() {
		SccStructure structure = new SccStructure();
		for (int i = 0; i < 100; ++i) {
			structure.addComponent(i, i + 10);
			structure.addTransition(0, i);
		}
		for (int i = 1; i < 100; ++i) {
			assertTrue(structure.hasTransition(0, i));
		}
		assertEquals(100, structure.numComponents());
		assertEquals(99, structure.totalNumTransitions());
		assertEquals(1, structure.numComponentsWithTransitions());
	}

	@Test
	public void recognizesTerminalComponents() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(2, 20);
		structure.addComponent(3, 30);
		structure.addComponent(4, 40);
		structure.addTransition(1, 2);
		structure.addTransition(1, 3);
		structure.addTransition(3, 4);
		structure.addTransition(3, 2);
		assertTrue(structure.isTerminal(structure.getComponent(4)));
		assertTrue(structure.isTerminal(structure.getComponent(2)));
		assertFalse(structure.isTerminal(structure.getComponent(1)));
		assertFalse(structure.isTerminal(structure.getComponent(3)));

		List<Scc> terminals = new LinkedList<Scc>();
		structure.getTerminalComponents(terminals);
		assertEquals(2, terminals.size());
		Scc component1 = terminals.get(0);
		Scc component2 = terminals.get(1);
		assertNotNull(component1);
		assertNotNull(component2);
		if (component1.getId() == 2) {
			assertEquals(4, component2.getId());
		} else {
			assertEquals(4, component1.getId());
			assertEquals(2, component2.getId());
		}
	}

	@Test
	public void combinesStructures() {
		SccStructure structure1 = new SccStructure();
		structure1.addComponent(1, 10);
		structure1.addComponent(2, 20);
		structure1.addComponent(3, 30);
		structure1.addComponent(4, 40);
		structure1.addComponent(6, 60);
		structure1.addTransition(1, 2);
		structure1.addTransition(2, 6);
		structure1.addTransition(3, 4);

		SccStructure structure2 = new SccStructure();
		structure2.addComponent(1, 10);
		structure2.addComponent(2, 20);
		structure2.addComponent(3, 30);
		structure2.addComponent(5, 50);
		structure2.addTransition(1, 2);
		structure2.addTransition(1, 3);
		structure2.addTransition(2, 5);

		structure1.combine(structure2);

		assertEquals(6, structure1.numComponents());
		assertEquals(3, structure1.numComponentsWithTransitions());
		assertEquals(5, structure1.totalNumTransitions());
		assertNotNull(structure1.getComponent(1));
		assertNotNull(structure1.getComponent(2));
		assertNotNull(structure1.getComponent(3));
		assertNotNull(structure1.getComponent(4));
		assertNotNull(structure1.getComponent(5));
		assertNotNull(structure1.getComponent(6));
		assertTrue(structure1.hasTransition(1, 2));
		assertTrue(structure1.hasTransition(1, 3));
		assertTrue(structure1.hasTransition(3, 4));
		assertTrue(structure1.hasTransition(2, 5));
		assertTrue(structure1.hasTransition(2, 6));

	}

	@Test(expected = RuntimeException.class)
	public void cannotCombineInconsistentStructures() {
		SccStructure structure1 = new SccStructure();
		structure1.addComponent(1, 10);
		SccStructure structure2 = new SccStructure();
		structure2.addComponent(1, 11);
		structure1.combine(structure2);
	}

	@Test
	public void serializesEmptyStructure() {
		SccStructure structure = new SccStructure();

		byte[] buf = new byte[1024];
		int offset = 15;

		for (int i = 0; i < buf.length; ++i) {
			buf[i] = (byte) -17;
		}

		final int length = structure.length();
		structure.write(buf, offset);
		assertEquals((byte) -17, buf[offset + length]);
		assertNotSame((byte) -17, buf[offset + length - 1]);

		SccStructure copy = new SccStructure();
		copy.read(buf, offset);

		assertEquals(copy.numComponents(), structure.numComponents());
		assertEquals(copy.numComponentsWithTransitions(), structure.numComponentsWithTransitions());
		assertEquals(copy.totalNumTransitions(), structure.totalNumTransitions());

	}

	@Test
	public void serializesStructureWithNoTransitions() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(2, 20);

		byte[] buf = new byte[1024];
		int offset = 15;

		for (int i = 0; i < buf.length; ++i) {
			buf[i] = (byte) -17;
		}

		final int length = structure.length();
		structure.write(buf, offset);
		assertEquals((byte) -17, buf[offset + length]);
		assertNotSame((byte) -17, buf[offset + length - 1]);

		SccStructure copy = new SccStructure();
		copy.read(buf, offset);

		assertEquals(copy.numComponents(), structure.numComponents());
		assertEquals(copy.numComponentsWithTransitions(), structure.numComponentsWithTransitions());
		assertEquals(copy.totalNumTransitions(), structure.totalNumTransitions());
		assertNotNull(copy.getComponent(1));
		assertNotNull(copy.getComponent(2));
	}

	@Test
	public void serializesStructure() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(2, 20);
		structure.addComponent(3, 30);
		structure.addComponent(4, 40);
		structure.addTransition(1, 2);
		structure.addTransition(1, 3);
		structure.addTransition(3, 2);
		structure.addTransition(3, 4);

		byte[] buf = new byte[1024];
		int offset = 5;

		for (int i = 0; i < buf.length; ++i) {
			buf[i] = (byte) -17;
		}

		final int length = structure.length();
		structure.write(buf, offset);
		assertEquals((byte) -17, buf[offset + length]);
		assertNotSame((byte) -17, buf[offset + length - 1]);

		SccStructure copy = new SccStructure();
		copy.read(buf, offset);

		assertEquals(copy.numComponents(), structure.numComponents());
		assertEquals(copy.numComponentsWithTransitions(), structure.numComponentsWithTransitions());
		assertEquals(copy.totalNumTransitions(), structure.totalNumTransitions());
		assertNotNull(copy.getComponent(1));
		assertNotNull(copy.getComponent(2));
		assertNotNull(copy.getComponent(3));
		assertNotNull(copy.getComponent(4));
		assertTrue(copy.hasTransition(1, 2));
		assertTrue(copy.hasTransition(1, 3));
		assertTrue(copy.hasTransition(3, 2));
		assertTrue(copy.hasTransition(3, 4));
	}

	@Test
	public void computesBySize() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(6, 30);
		structure.addComponent(2, 20);
		structure.addComponent(3, 10);
		structure.addComponent(4, 30);
		structure.addComponent(5, 30);

		LongToLongHashMap bySizeMap = structure.componentsBySizeMap();

		assertEquals(2, bySizeMap.get(10));
		assertEquals(1, bySizeMap.get(20));
		assertEquals(3, bySizeMap.get(30));
		assertEquals(-1, bySizeMap.get(100));
	}

	@Test
	public void testsToString() {
		SccStructure structure = new SccStructure();
		structure.addComponent(1, 10);
		structure.addComponent(2, 20);
		structure.addComponent(3, 10);

		String line1 = "Found 2 components of size 10\n";
		String line2 = "Found 1 components of size 20\n";
		String output = structure.toString();

		assertTrue(output.equals(line1 + line2) || output.equals(line2 + line1));
	}

	@Test
	public void minimalTestDot() {
		SccStructure structure = new SccStructure();

		structure.addComponent(1, 10);
		structure.addComponent(2, 20);
		structure.addComponent(3, 10);
		structure.addComponent(4, 20);

		structure.addTransition(1, 2);
		structure.addTransition(1, 3);
		structure.addTransition(3, 2);
		structure.addTransition(3, 4);

		String fileName = "/tmp/SccStructureTest.testDot." + System.nanoTime();
		structure.toDot(fileName, 100, 3, false);

		for (int i = 0; i < 3; ++i) {
			File file = new File(fileName + "-" + i + ".dot");
			assertTrue(file.exists());
		}

		for (int i = 0; i < 3; ++i) {
			File file = new File(fileName + "-" + i + ".dot");
			assertTrue(file.delete());
		}
	}
}
