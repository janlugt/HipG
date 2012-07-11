/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package hipg.compile;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import myutils.IOUtils;

import org.apache.bcel.generic.Type;
import org.junit.Test;

public class SerializationTest {

	@Test
	public void testExplicitWrite() {
		final Random rand = new Random(System.nanoTime());
		final int origOffset = 11;
		final byte[] buf = new byte[1024];
		for (int i = 0; i < 1000; i++) {
			final int synchOwner = rand.nextInt(1000);
			final int synchId = rand.nextInt(1000);
			final short graphId = (short) rand.nextInt(100);
			final short methodId = (short) rand.nextInt(100);
			final int target = rand.nextInt();
			int offset = origOffset;
			Serialization.writeExplicitUserMessage(buf, offset, synchOwner, synchId, graphId, methodId, target);
			int copySynchOwner = IOUtils.readInt(buf, offset);
			offset += IOUtils.INT_BYTES;
			assertEquals(synchOwner, copySynchOwner);
			int copySynchId = IOUtils.readInt(buf, offset);
			offset += IOUtils.INT_BYTES;
			assertEquals(synchId, copySynchId);
			short copyGraphId = IOUtils.readShort(buf, offset);
			offset += IOUtils.SHORT_BYTES;
			assertEquals(graphId, copyGraphId);
			short copyMethodId = IOUtils.readShort(buf, offset);
			offset += IOUtils.SHORT_BYTES;
			assertEquals(methodId, copyMethodId);
			int copyTarget = IOUtils.readInt(buf, offset);
			offset += IOUtils.INT_BYTES;
			assertEquals(target, copyTarget);
		}
	}

	@Test
	public void testPrimitiveTypeLen() {
		assertEquals(3, Serialization.staticTypeSizeInBytesLog(Type.LONG));
		assertEquals(3, Serialization.staticTypeSizeInBytesLog(Type.DOUBLE));
		assertEquals(2, Serialization.staticTypeSizeInBytesLog(Type.INT));
		assertEquals(2, Serialization.staticTypeSizeInBytesLog(Type.FLOAT));
		assertEquals(1, Serialization.staticTypeSizeInBytesLog(Type.CHAR));
		assertEquals(1, Serialization.staticTypeSizeInBytesLog(Type.SHORT));
		assertEquals(0, Serialization.staticTypeSizeInBytesLog(Type.BOOLEAN));
		assertEquals(0, Serialization.staticTypeSizeInBytesLog(Type.BYTE));
	}
}
