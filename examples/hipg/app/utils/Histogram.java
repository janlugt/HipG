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

package hipg.app.utils;

import java.util.Arrays;

import myutils.IOUtils;
import myutils.Serializable;
import myutils.storage.LongPairIterable;
import myutils.storage.LongPairIterator;

public final class Histogram implements Serializable, LongPairIterable {

	private static final int SMALL_LEN = 1024 * 4;

	/**
	 * Small elements. Element <i>e</i>, where <i>e</i> &ge; 0 and <i>e</i> &lt; SMALL_LEN, is present iff
	 * smallElements[e] times.
	 */
	private final long[] smallElements = new long[SMALL_LEN];

	/**
	 * Big components. Component <i>e</it>, where <i>e</i> &gt; SMALL_LEN, is present iff there is an entry <i>j</i> in
	 * bigElements such that bigElements[j] = e and bigCounts[j] > 0. BigElements are sorted (for binary search).
	 */
	private long[] bigElements = null;
	private long[] bigCounts = null;
	private int bigLength = 0;

	/** The number of small unique elements. */
	private int smallUnique = 0;

	/** The number of small elements. */
	private int smallCount = 0;

	/** The number of all big elements. */
	private int bigCount = 0;

	/** The maximum count. */
	private long maxCount = 0;

	/** Creates an empty histogram. */
	public Histogram() {
	}

	/** Combines two histograms. */
	public Histogram add(Histogram otherHistogram) {
		if (otherHistogram == null) {
			return this;
		}
		LongPairIterator it = otherHistogram.iterator();
		while (it.hasNext()) {
			long size = it.next();
			long count = it.value();
			add(size, count);
		}
		return this;
	}

	/** Adds one element to the histogram. */
	public void add(long element) {
		add(element, 1);
	}

	/** Ads an element 'count' times to the histogram. */
	public void add(long element, long count) {
		if (element < 0) {
			throw new RuntimeException("Cannot add: Element < 0");
		}
		if (count < 0) {
			throw new RuntimeException("Cannot add: Count < 0");
		} else if (count == 0) {
			return;
		}
		if (element < SMALL_LEN) {
			final int e = (int) element;
			if (smallElements[e] == 0) {
				smallUnique++;
			}
			smallCount += count;
			smallElements[e] += count;
			if (smallElements[e] > maxCount) {
				maxCount = smallElements[e];
			}
		} else {
			addBig(element, count);
		}
	}

	/** Returns the number of all elements in the histogram. */
	public int count() {
		return smallCount + bigCount;
	}

	/** Returns the number of all unique elements in the histogram. */
	public int unique() {
		return smallUnique + bigLength;
	}

	/** Returns the number of specified element in the histogram. */
	public long count(long element) {
		if (element < SMALL_LEN) {
			return smallElements[(int) element];
		} else if (bigLength > 0) {
			int idx = Arrays.binarySearch(bigElements, 0, bigLength, element);
			if (idx >= 0) {
				return bigCounts[idx];
			}
		}
		return 0;
	}

	/** Returns the minimal element in the histogram. */
	public long minElement() {
		for (int i = 0; i < SMALL_LEN; i++) {
			if (smallElements[i] > 0) {
				return i;
			}
		}
		if (bigLength > 0) {
			return bigElements[0];
		}
		return -1;
	}

	/** Returns the maximal element in the histogram. */
	public long maxElement() {
		if (bigLength > 0) {
			return bigElements[bigLength - 1];
		}
		for (int i = SMALL_LEN - 1; i >= 0; i--) {
			if (smallElements[i] > 0) {
				return i;
			}
		}
		return -1;
	}

	public double avgElement() {
		final LongPairIterator elements = iterator();
		double gsum = 0.0d;
		double gcount = 0.0d;
		while (elements.hasNext()) {
			final long element = elements.next();
			final long count = elements.value();
			gsum += element * count;
			gcount += count;
		}
		if (gcount == 0) {
			return 0;
		}
		return gsum / gcount;
	}

	public double stdevElement(double avgElement) {
		final LongPairIterator elements = iterator();
		double gsum = 0.0d;
		double gcount = 0.0d;
		while (elements.hasNext()) {
			final long element = elements.next();
			final long count = elements.value();
			gsum += (element - avgElement) * (element - avgElement);
			gcount += count;
		}
		if (gcount <= 1) {
			return 0.0;
		}
		return Math.sqrt(gsum) / (double) (gcount - 1);
	}

	/** Adds a big element. */
	private void addBig(final long element, final long count) {
		int idx = 0;
		if (bigElements == null) {
			bigElements = new long[1024 * 4];
			bigCounts = new long[1024 * 4];
			bigLength = 0;
			bigElements[0] = element;
			bigCounts[0] = count;
			bigLength = 1;
		} else {
			idx = Arrays.binarySearch(bigElements, 0, bigLength, element);
			if (idx >= 0) {
				bigCounts[idx] += count;
			} else {
				idx = -(idx + 1);
				if (bigLength == bigElements.length) {
					final int newBigLength = bigElements.length * 3 / 2;
					bigElements = Arrays.copyOf(bigElements, newBigLength);
					bigCounts = Arrays.copyOf(bigCounts, newBigLength);
				}
				for (int i = bigLength - 1; i >= idx; i--) {
					bigElements[i + 1] = bigElements[i];
					bigCounts[i + 1] = bigCounts[i];
				}
				bigElements[idx] = element;
				bigCounts[idx] = count;
				bigLength++;
				bigCount += count;
			}
		}
		if (bigCounts[idx] > maxCount) {
			maxCount = bigCounts[idx];
		}
	}

	/**
	 * Returns enumeration over all the pairs (element, count), where (count &gt; 0) in the histogram.
	 */
	public LongPairIterator iterator() {
		return new HistogramIterator(this);
	}

	private static final class HistogramIterator implements LongPairIterator {
		final Histogram histogram;
		boolean small = true;
		int index = -1;
		long lastValue = 0;

		public HistogramIterator(Histogram histogram) {
			this.histogram = histogram;
		}

		private final void locateNextSmall() {
			while (index < SMALL_LEN && histogram.smallElements[index] == 0) {
				index++;
			}
			if (index == SMALL_LEN) {
				small = false;
				index = 0;
			}
		}

		@Override
		public long next() {
			if (small) {
				lastValue = histogram.smallElements[index];
				final long i = index;
				index++;
				locateNextSmall();
				return i;
			} else {
				lastValue = histogram.bigCounts[index];
				return histogram.bigElements[index++];
			}
		}

		@Override
		public boolean hasNext() {
			if (index < 0) {
				index++;
				locateNextSmall();
			}
			return small || index < histogram.bigLength;
		}

		@Override
		public long value() {
			return lastValue;
		}
	};

	/** Prints the histogram. */
	public String toString() {
		return toString("Found", "elements with count", null);
	}

	public String toString(String prefix, String infix, String suffix) {
		return toString(prefix, infix, suffix, 0);
	}

	/** Prints the histogram as a list of prefix+element+infix+value+suffix. */
	public String toString(String prefix, String infix, String suffix, int maxLines) {
		final StringBuilder sb = new StringBuilder();
		final long maxElement = maxElement();
		final int maxElementLength = (int) Math.ceil(Math.log(maxElement) / Math.log(10));
		final int maxCountLength = (int) Math.ceil(Math.log(maxCount) / Math.log(10));
		final LongPairIterator elements = iterator();
		int line = 0;
		while (elements.hasNext() && (maxLines <= 0 || line < maxLines)) {
			long element = elements.next();
			long count = elements.value();
			if (prefix != null) {
				sb.append(prefix);
				sb.append(" ");
			}
			final String countStr = String.valueOf(count);
			sb.append(countStr);
			int miss = maxCountLength - countStr.length();
			for (int i = 0; i < miss; i++) {
				sb.append(" ");
			}
			if (infix != null) {
				sb.append(" ");
				sb.append(infix);
			}
			sb.append(" ");
			final String elementStr = String.valueOf(element);
			sb.append(elementStr);
			miss = maxElementLength - elementStr.length();
			for (int i = 0; i < miss; i++) {
				sb.append(" ");
			}
			if (suffix != null) {
				sb.append(suffix);
			}
			if (elements.hasNext()) {
				sb.append("\n");
			}
			line++;
		}
		return sb.toString();
	}

	//
	// serialization
	//

	@Override
	public int length() {
		return IOUtils.INT_BYTES + unique() * 2 * IOUtils.LONG_BYTES;
	}

	@Override
	public void write(final byte[] buf, int offset) {
		IOUtils.writeInt(unique(), buf, offset);
		offset += IOUtils.INT_BYTES;
		for (int i = 0; i < SMALL_LEN; i++) {
			if (smallElements[i] > 0) {
				IOUtils.writeLong(i, buf, offset);
				offset += IOUtils.LONG_BYTES;
				IOUtils.writeLong(smallElements[i], buf, offset);
				offset += IOUtils.LONG_BYTES;
			}
		}
		for (int i = 0; i < bigLength; i++) {
			IOUtils.writeLong(bigElements[i], buf, offset);
			offset += IOUtils.LONG_BYTES;
			IOUtils.writeLong(bigCounts[i], buf, offset);
			offset += IOUtils.LONG_BYTES;
		}
	}

	@Override
	public void read(final byte[] buf, int offset) {
		final long count = IOUtils.readInt(buf, offset);
		offset += IOUtils.INT_BYTES;
		for (long i = 0; i < count; i++) {
			long size = IOUtils.readLong(buf, offset);
			offset += IOUtils.LONG_BYTES;
			long cnt = IOUtils.readLong(buf, offset);
			offset += IOUtils.LONG_BYTES;
			add(size, cnt);
		}
	}

}
