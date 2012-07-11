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

package hipg.graph;

import static junit.framework.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

public class OnTheFlyDefaultHashTest {

	private static final boolean DEBUG = false;

	private double Average(final int[] values) {
		double avg = 0;
		for (long count : values) {
			avg += count;
		}
		return avg / (double) values.length;
	}

	private double StandardDeviation(final double average, final int[] values) {
		long sum = 0;
		for (int count : values) {
			sum += ((double) count - average) * ((double) count - average);
		}
		return Math.sqrt((double) sum / (double) (values.length - 1));
	}

	static long count = 0;
	static long Count = 0;

	private static final void _testAll(final byte[] state, final int index, final int[] distrPosId,
			final int[] distrNegId, final int[] distrOwn, final OnTheFlyHash hash, final int poolSize) {
		final int len = state.length;
		for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
			state[index] = (byte) b;
			if (index == len - 1) {
				count++;
				Count++;
				final long id = hash.id(state);
				final int owner = hash.owner(state);
				assertTrue("owner is " + owner, owner >= 0 && owner < poolSize);
				distrOwn[(int) owner]++;
				if (id >= 0) {
					distrPosId[(int) (id % distrPosId.length)]++;
				}
				if (id <= 0) {
					distrNegId[(int) ((long) (-id) % distrNegId.length)]++;
				}
				if (count == 10000000) {
					count = 0;
					if (DEBUG) {
						System.out.print(Count + "   ");
						for (int i = 0; i < len; i++)
							System.out.print(state[i] + " ");
						System.out.println();
					}
				}
			} else {
				_testAll(state, index + 1, distrPosId, distrNegId, distrOwn, hash, poolSize);
			}
		}
	}

	private void _testRandom(final byte[] state, Random rand, final long iter, final int[] distrPosId,
			final int[] distrNegId, final int[] distrOwn, final OnTheFlyHash hash, final int poolSize) {
		for (long i = 0; i < iter; i++) {
			for (int j = 0; j < state.length; j++) {
				state[j] = (byte) (-127 + rand.nextInt() % 256);
			}
			final long id = hash.id(state);
			final int owner = hash.owner(state);
			assertTrue("owner is " + owner, owner >= 0 && owner < poolSize);
			distrOwn[(int) owner]++;
			if (id >= 0) {
				distrPosId[(int) (id % distrPosId.length)]++;
			}
			if (id <= 0) {
				distrNegId[(int) ((long) (-id) % distrNegId.length)]++;
			}
		}
	}

	private StringBuilder printArray(final int[] arr, final int max) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < arr.length && i < max; i++) {
			sb.append(arr[i] + " ");
		}
		if (arr.length > max) {
			sb.append("...");
		}
		return sb;
	}

	private void test(int length, int poolSize, int distrLen, boolean all) {

		count = 0;
		Count = 0;

		if (DEBUG) {
			System.out.println(" =========== ");
			System.out.println("length = " + length + ", poolSize = " + poolSize + ", distrLen = " + distrLen);
			System.out.flush();
		}

		OnTheFlyHash hash = new OnTheFlyDefaultHash(poolSize);
		int[] distrPosId = new int[distrLen];
		int[] distrNegId = new int[distrLen];
		int[] distrOwn = new int[poolSize];
		for (int i = 0; i < distrPosId.length; i++)
			distrPosId[i] = 0;
		for (int i = 0; i < distrNegId.length; i++)
			distrNegId[i] = 0;
		for (int i = 0; i < distrOwn.length; i++)
			distrOwn[i] = 0;

		if (all) {
			_testAll(new byte[length], 0, distrPosId, distrNegId, distrOwn, hash, poolSize);
		} else {
			Random rand = new Random(length);
			long iter = distrLen * 100;
			_testRandom(new byte[length], rand, iter, distrPosId, distrNegId, distrOwn, hash, poolSize);
		}

		double ownAvg = Average(distrOwn);
		double ownStDev = StandardDeviation(ownAvg, distrOwn);
		double posIdAvg = Average(distrPosId);
		double posIdStDev = StandardDeviation(posIdAvg, distrPosId);
		double negIdAvg = Average(distrNegId);
		double negIdStDev = StandardDeviation(negIdAvg, distrNegId);

		if (DEBUG) {
			System.out.println("own   = N(" + ownAvg + ", " + ownStDev + ") " + printArray(distrOwn, 20));
			System.out.println("id(+) = N(" + posIdAvg + ", " + posIdStDev + ") " + printArray(distrPosId, 20));
			System.out.println("id(-) = N(" + negIdAvg + ", " + negIdStDev + ") " + printArray(distrNegId, 20));
			System.out.flush();
		}

		final double c = 6.0;

		for (int i = 0; i < distrOwn.length; i++) {
			double min = Math.floor(ownAvg - c * ownStDev);
			double max = Math.ceil(ownAvg + c * ownStDev);
			double el = distrOwn[i];
			if (el < min || el > max) {
				if (DEBUG) {
					System.err.println("a: " + min + " " + el + " " + max);
				}
			}
			assertTrue(min <= el && el <= max);
		}
		for (int i = 0; i < distrPosId.length; i++) {
			double min = Math.floor(posIdAvg - c * posIdStDev);
			double max = Math.ceil(posIdAvg + c * posIdStDev);
			double el = distrPosId[i];
			if (el < min || el > max) {
				if (DEBUG) {
					System.err.println("b: " + min + " " + el + " " + max);
				}
			}
			assertTrue(min <= el && el <= max);
		}
		for (int i = 0; i < distrNegId.length; i++) {
			double min = Math.floor(negIdAvg - c * negIdStDev);
			double max = Math.ceil(negIdAvg + c * negIdStDev);
			double el = distrNegId[i];
			if (el < min || el > max) {
				if (DEBUG) {
					System.err.println("c: " + min + " " + el + " " + max);
				}
			}
			assertTrue(min <= el && el <= max);
		}
		if (DEBUG) {
			System.out.println("OK");
		}
	}

	@Test
	public void testLen1() {
		test(1, 10, 1 << 8, true);
	}

	@Test
	public void testLen2() {
		test(2, 10, 1 << 16, true);
	}

	@Test
	public void testLen3() {
		test(3, 10, 1 << 16, true);
	}

	// @Test
	// public void testLen4() {
	// test(4, 10, 1 << 24, true);
	// }

	@Test
	public void testLen5() {
		test(5, 10, 1 << 16, false);
	}

	@Test
	public void testLen6() {
		test(6, 10, 1 << 16, false);
	}

	@Test
	public void testLen7() {
		test(7, 10, 1 << 16, false);
	}

	@Test
	public void testLen8() {
		test(8, 10, 1 << 16, false);
	}

	@Test
	public void testLen9() {
		test(9, 10, 1 << 16, false);
	}

	@Test
	public void testLenLong() {
		test(10, 10, 1 << 16, false);
	}

}
