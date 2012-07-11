/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam.
 * Written by Ela Krepska e.l.krepska@vu.nl.
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
 *
 */

package hipg.format.hip;

import hipg.format.GraphCreationException;
import hipg.format.GraphMaker;
import hipg.graph.ExplicitNodeReference;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

import myutils.ConversionUtils;
import myutils.Quicksort;
import myutils.Quicksort.QuicksortableArray;

/**
 * Creates large graphs in the Hip format sequentially (stores on disc).
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public final class HipMaker implements GraphMaker {

	private static final String sep = File.separator;

	private final boolean verbose;
	private final boolean transpose;
	private final String path;
	private final int segments;
	private final int[] globalStatesCount;
	private final int[][] globalTransitionsCount;
	private final int[] transitionsCount, inTransitionsCount;
	private final byte[][] sources, indestinations;
	private final byte[][] destowners, insrcowners;
	private final byte[][] destinations, insources;
	private int segmentRR = 0;
	private int lastseg = 0;
	private final int colocationCnt;
	private final boolean random;
	private int addedTransitions = 0;
	private final Random rand = new Random(System.currentTimeMillis());

	public HipMaker(String path, boolean verbose) throws GraphCreationException {
		this(path, 1024, verbose);
	}

	public HipMaker(String path, int segments, boolean verbose) throws GraphCreationException {
		this(path, segments, 1, verbose, true, false);
	}

	/**
	 * Creates a graph maker
	 * 
	 * @param path
	 *            Directory where the graph will be created
	 * @param segments
	 *            The number of chunks the graph will be partitioned into
	 * @param sort
	 *            If the transitions should be sorted after creation
	 * @param verbose
	 *            Switch verbose mode
	 * @throws GraphCreationException
	 */
	public HipMaker(String path, int segments, int colocationCnt, boolean verbose, boolean random, boolean bels)
			throws GraphCreationException {
		this.transpose = true;
		this.verbose = verbose;
		this.path = path;
		this.segments = segments;
		this.globalStatesCount = new int[segments];
		this.globalTransitionsCount = new int[segments][segments];
		this.transitionsCount = new int[segments];
		if (transpose) {
			this.inTransitionsCount = new int[segments];
		} else {
			this.inTransitionsCount = null;
		}
		this.colocationCnt = colocationCnt;
		this.random = random;
		byte[][][] tab = determineMaxMemory(segments);
		debug("Determined memory size: " + tab[0][0].length);
		this.sources = tab[0];
		this.destowners = tab[1];
		this.destinations = tab[2];
		if (transpose) {
			this.indestinations = tab[3];
			this.insrcowners = tab[4];
			this.insources = tab[5];
		} else {
			this.indestinations = null;
			this.insrcowners = null;
			this.insources = null;
		}
		start();
	}

	private byte[][][] determineMaxMemory(int segments) throws GraphCreationException {
		final int sizeof_int = 4;
		final long totalMemory = Runtime.getRuntime().totalMemory();
		final int perSegmentMemory = (int) ((double) totalMemory / (double) (segments));
		final int cnt = (transpose ? 6 : 3);
		final int startSize = (int) ((double) perSegmentMemory / (double) cnt / (double) sizeof_int);
		byte[][][] tab = null;
		for (int size = startSize; size >= 1; size--) {
			try {
				tab = new byte[cnt][segments][size * sizeof_int];
				if (tab != null) {
					verbose("Allocated " + ConversionUtils.bytes2GB(3 * segments * size * sizeof_int)
							+ "GB for transitions");
					return tab;
				}
			} catch (Throwable t) {
				System.err.println(t.getMessage());
			}
		}
		throw new GraphCreationException("Could not create graph: not enough memory");
	}

	public int segments() {
		return segments;
	}

	public int nodes(int segment) {
		return globalStatesCount[segment];
	}

	public int transitions(int segment, int destSegment) {
		return globalTransitionsCount[segment][destSegment];
	}

	public String path() {
		return path;
	}

	/**
	 * Adds node to a segment and returns its "handle".
	 * 
	 * @param segment
	 *            Segment to add the node to
	 * @return Handle to the newly created node.
	 * 
	 * @throws GraphCreationException
	 */
	public long addNode(int segment) throws GraphCreationException {
		final int id = globalStatesCount[segment]++;
		final int owner = segment;
		if (id > Integer.MAX_VALUE / 2) {
			sync(owner, false);
			if (transpose)
				sync(owner, true);
		}
		return ExplicitNodeReference.createReference(id, owner);
	}

	/**
	 * Adds node to the graph while the actual segment is determined by the
	 * framework.
	 * 
	 * @return Handle to the newly created node
	 * @throws GraphCreationException
	 */
	public long addNode() throws GraphCreationException {
		final int owner;
		if (random)
			owner = rand.nextInt(segments);
		else {
			owner = segmentRR;
			lastseg++;
			if (lastseg >= colocationCnt) {
				lastseg = 0;
				segmentRR++;
				if (segmentRR >= segments)
					segmentRR = 0;
			}
		}
		final int id = globalStatesCount[owner]++;
		if (id > Integer.MAX_VALUE / 2) {
			sync(owner, false);
			if (transpose)
				sync(owner, true);
		}
		return ExplicitNodeReference.createReference(id, owner);
	}

	public void addTransition(final long from, final long to, final int label) throws GraphCreationException {
		int fromOwner = ExplicitNodeReference.getOwner(from);
		int fromId = ExplicitNodeReference.getId(from);
		int toOwner = ExplicitNodeReference.getOwner(to);
		int toId = ExplicitNodeReference.getId(to);
		addTransition(fromOwner, fromId, toOwner, toId, label);
	}

	public void addTransition(final int fromOwner, final int fromId, final int toOwner, final int toId, final int label)
			throws GraphCreationException {
		globalTransitionsCount[fromOwner][toOwner]++;
		int index = transitionsCount[fromOwner]++;
		index <<= 2;
		decomposeInt(fromId, sources[fromOwner], index);
		decomposeInt(toOwner, destowners[fromOwner], index);
		decomposeInt(toId, destinations[fromOwner], index);
		if (index + 4 >= sources[fromOwner].length)
			sync(fromOwner, false);
		if (transpose) {
			int tindex = inTransitionsCount[toOwner]++;
			tindex <<= 2;
			decomposeInt(toId, indestinations[toOwner], tindex);
			decomposeInt(fromId, insources[toOwner], tindex);
			decomposeInt(fromOwner, insrcowners[toOwner], tindex);
			if (tindex + 4 >= indestinations[toOwner].length)
				sync(toOwner, true);
		}
		if (verbose && ++addedTransitions >= 1000000) {
			addedTransitions = 0;
			verbose("... States = " + getGlobalStateCount() + " Transitions = " + getGlobalTransitionsCount());
		}
	}

	private void sync(final int segment, final boolean transpose) throws GraphCreationException {
		final String srcPath = HipHeader.srcPath(path, segment, transpose);
		final String dstPath = HipHeader.dstPath(path, segment, transpose);
		final String ownPath = HipHeader.ownPath(path, segment, transpose);
		final int count;
		if (transpose) {
			count = inTransitionsCount[segment];
			int len = (count << 2);
			appendFile(dstPath, indestinations[segment], len);
			appendFile(ownPath, insrcowners[segment], len);
			appendFile(srcPath, insources[segment], len);
			inTransitionsCount[segment] = 0;
		} else {
			count = transitionsCount[segment];
			int len = (count << 2);
			appendFile(srcPath, sources[segment], len);
			appendFile(ownPath, destowners[segment], len);
			appendFile(dstPath, destinations[segment], len);
			transitionsCount[segment] = 0;
		}
	}

	private void compact(int segment, boolean transpose) throws GraphCreationException {

		final String srcPath1 = HipHeader.srcPath(path, segment, transpose);
		final String dstPath1 = HipHeader.dstPath(path, segment, transpose);
		final String ownPath = HipHeader.ownPath(path, segment, transpose);

		final String locPath = (transpose ? dstPath1 : srcPath1);
		final String conPath = (transpose ? srcPath1 : dstPath1);

		verboseStart("Compacting segment " + segment + (transpose ? " transposed" : ""));

		try {
			// read
			long start = System.nanoTime();

			byte[] loc = readFile(locPath);
			byte[] own = readFile(ownPath);
			byte[] con = readFile(conPath);

			verboseIns("read", start);

			// check
			if (loc.length != con.length || con.length != own.length) {
				throw new GraphCreationException("Files " + locPath + ", " + ownPath + " and " + conPath
						+ " differ in length");
			}

			// sort
			start = System.nanoTime();
			Quicksort.quicksort(new QuicksortableArrayOfIntPairs(loc, own, con), null);
			verboseIns("sort", start);

			// double check
			int prvown = -1, eqcount = 1, prvloc = -1;
			for (int i = 0; i < own.length; i += 4) {
				final int curown = composeInt(own, i);
				final int curloc = composeInt(loc, i);
				if (curown == prvown) {
					eqcount++;
					if (prvloc > curloc) {
						throw new RuntimeException("Sorting did not work! " + "Found node " + prvloc + " before "
								+ curloc + " within segment " + curown);
					}
				} else if (curown < prvown) {
					throw new RuntimeException("Sorting did not work! " + "Found owner " + prvown + " before " + curown);
				} else if (i > 0) {
					final int expecting = (transpose ? globalTransitionsCount[prvown][segment]
							: globalTransitionsCount[segment][prvown]);
					if (expecting != eqcount) {
						throw new RuntimeException("Bug in HipMaker? Found " + eqcount
								+ " transitions after sorting while " + "expecting " + expecting);
					}
					for (int o = prvown + 1; o < curown; o++) {
						int expecting2 = (transpose ? globalTransitionsCount[o][segment]
								: globalTransitionsCount[segment][o]);
						if (expecting2 != 0) {
							throw new RuntimeException("Bug in HipMaker? Found " + eqcount
									+ " transitions after sorting " + "while " + "expecting " + expecting2);
						}
					}
					eqcount = 1;
				}
				prvown = curown;
				prvloc = curloc;
			}

			// delete 'own'
			start = System.nanoTime();
			deleteFile(ownPath);
			verboseIns("own", start);
			own = null;

			// rewrite 'con' (dst-like)
			start = System.nanoTime();
			deleteFile(conPath);
			writeFile(conPath, con);
			verboseIns("dst", start);
			con = null;

			// rewrite 'loc' (src-like)
			start = System.nanoTime();
			deleteFile(locPath);
			writeFile(locPath, loc);
			verboseIns("src", start);
			loc = null;

		} catch (GraphCreationException gce) {
			throw new GraphCreationException("Could not compact: " + gce.getMessage(), gce);
		}
		verboseDone();
	}

	private void info(long root) throws GraphCreationException {
		debug("Creating header");
		String headerPath = path + sep + "info";
		ArrayList<HipSegment> segments = new ArrayList<HipSegment>();
		for (int i = 0; i < this.segments; i++) {
			int[] outTransitions = new int[this.segments];
			int[] inTransitions = new int[this.segments];
			for (int j = 0; j < this.segments; j++) {
				outTransitions[j] = globalTransitionsCount[i][j];
				inTransitions[j] = globalTransitionsCount[j][i];
			}
			segments.add(new HipSegment(i, globalStatesCount[i], outTransitions, inTransitions, "localhost", path,
					HipHeader.FORMAT_NORMAL, HipHeader.FORMAT_NORMAL));
		}
		HipHeader header = new HipHeader("Generated by HipG", ExplicitNodeReference.getOwner(root),
				ExplicitNodeReference.getId(root), segments);
		header.write(headerPath);
	}

	private void start() throws GraphCreationException {
		verbose("Creating directory " + path);
		final File directory = new File(path);
		if (directory.exists()) {
			throw new GraphCreationException("Cannot write to dir " + path + ": Path exists");
		} else {
			try {
				if (!directory.mkdirs())
					throw new GraphCreationException("Could not create directory " + path);
			} catch (Throwable t) {
				throw new GraphCreationException("Could not create directory " + path + ": " + t.getMessage(), t);
			}
		}
	}

	/**
	 * Finishes writing the graph: synchronized with disk, sorts if required,
	 * writes metadata, closes.
	 * 
	 * @param root
	 *            Root node
	 * @throws GraphCreationException
	 */
	public void finish(long root) throws GraphCreationException {

		// last sync
		verboseStart("Last sync");
		for (int segment = 0; segment < segments; segment++) {
			sync(segment, false);
		}
		if (transpose) {
			for (int segment = 0; segment < segments; segment++) {
				sync(segment, true);
			}
		}
		verboseDone();

		// compact
		verboseStart("Compacting");
		for (int segment = 0; segment < segments; segment++) {
			compact(segment, false);
		}
		if (transpose) {
			for (int segment = 0; segment < segments; segment++) {
				compact(segment, true);
			}
		}
		verboseDone();

		// metadata
		verboseStart("Info (root=" + ExplicitNodeReference.referenceToString(root) + ")");
		info(root);
		verboseDone();
	}

	public long getGlobalStateCount() {
		long stateCount = 0;
		for (int s : globalStatesCount)
			stateCount += s;
		return stateCount;
	}

	public int getGlobalTransitionsCount(int segment, boolean transpose) {
		int c = 0;
		for (int s = 0; s < segments; s++) {
			int src = (transpose ? s : segment);
			int dst = (transpose ? segment : s);
			c += globalTransitionsCount[src][dst];
		}
		return c;
	}

	public long getGlobalTransitionsCount() {
		long transCount = 0;
		for (int[] gt : globalTransitionsCount)
			for (int s : gt)
				transCount += s;
		return transCount;
	}

	public long getGlobalStateCountMin() {
		long stateCount = -1;
		for (int s : globalStatesCount)
			if (stateCount < 0 || s < stateCount)
				stateCount = s;
		return stateCount;
	}

	public long getGlobalStateCountMax() {
		long stateCount = -1;
		for (int s : globalStatesCount)
			if (stateCount < 0 || s > stateCount)
				stateCount = s;
		return stateCount;
	}

	public long getGlobalTransitionsCountMin() {
		long transCount = -1;
		for (int[] gt : globalTransitionsCount)
			for (int s : gt)
				if (transCount < 0 || s < transCount)
					transCount = s;
		return transCount;
	}

	public long getGlobalTransitionsCountMax() {
		long transCount = -1;
		for (int[] gt : globalTransitionsCount)
			for (int s : gt)
				if (transCount < 0 || s > transCount)
					transCount = s;
		return transCount;
	}

	/* **************************
	 * 
	 * HELPERS
	 * 
	 * ************************** */

	private void closeStream(InputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (Throwable t) {
				debug("Could not close stream: " + t.getMessage());
			}
		}
	}

	private void closeStream(OutputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (Throwable t) {
				debug("Could not close stream: " + t.getMessage());
			}
		}
	}

	private OutputStream openFileToAppend(String path) throws GraphCreationException {
		return openFile(path, true);
	}

	@SuppressWarnings("unused")
	private OutputStream openFileToRewrite(String path) throws GraphCreationException {
		return openFile(path, false);
	}

	private OutputStream openFile(String path, boolean append) throws GraphCreationException {
		debug("Opening file " + path + " to append");
		File file = new File(path);
		if (!file.exists()) {
			try {
				if (!file.createNewFile())
					throw new GraphCreationException("Could not create file " + path);
			} catch (Throwable t) {
				throw new GraphCreationException("Could not create file " + path + ": " + t.getMessage(), t);
			}
		}
		FileOutputStream out;
		try {
			out = new FileOutputStream(file, append);
		} catch (FileNotFoundException e) {
			throw new GraphCreationException("Coule not open output stream for file " + path);
		}
		return new BufferedOutputStream(out);
	}

	private InputStream openFileToFastRead(String path) throws GraphCreationException {
		debug("Opening file " + path + " to read");
		File file = new File(path);
		FileInputStream in;
		try {
			in = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			throw new GraphCreationException("Coule not open output stream for file " + path);
		}
		return in;
	}

	private byte[] readFile(String path) throws GraphCreationException {
		InputStream in = openFileToFastRead(path);
		debug("Reading " + path);
		long llength = new File(path).length();
		if (llength > Integer.MAX_VALUE)
			return null;
		int length = (int) llength;
		byte[] array = new byte[length];
		int size = 0;
		try {
			while (size < length) {
				int n = in.read(array, size, length - size);
				if (n <= 0)
					throw new GraphCreationException("Could not read file " + path + ": content ended at " + size
							+ " while length " + length);
				size += n;
			}
		} catch (IOException e) {
			throw new GraphCreationException("Could not read file " + path + ": " + e.getMessage(), e);
		} finally {
			closeStream(in);
		}
		return array;
	}

	private static final int composeInt(byte[] buf, int offset) {
		int v = ((int) buf[offset++]) & 0x000000FF;
		v <<= 8;
		v |= (((int) buf[offset++]) & 0x000000FF);
		v <<= 8;
		v |= (((int) buf[offset++]) & 0x000000FF);
		v <<= 8;
		v |= (((int) buf[offset]) & 0x000000FF);
		return v;
	}

	private static final boolean equalsInt(byte[] buf, int offset) {
		return buf[offset] == buf[offset - 4] && buf[offset + 1] == buf[offset - 4 + 1]
				&& buf[offset + 2] == buf[offset - 4 + 2] && buf[offset + 3] == buf[offset - 4 + 3];
	}

	private static final void decomposeInt(int v, byte buf[]) {
		buf[3] = (byte) (v & 0x000000FF);
		v >>= 8;
		buf[2] = (byte) (v & 0x000000FF);
		v >>= 8;
		buf[1] = (byte) (v & 0x000000FF);
		v >>= 8;
		buf[0] = (byte) (v & 0x000000FF);
	}

	private static final void decomposeInt(int v, byte buf[], int offset) {
		offset += 3;
		buf[offset--] = (byte) (v & 0x000000FF);
		v >>= 8;
		buf[offset--] = (byte) (v & 0x000000FF);
		v >>= 8;
		buf[offset--] = (byte) (v & 0x000000FF);
		v >>= 8;
		buf[offset] = (byte) (v & 0x000000FF);
	}

	@SuppressWarnings("unused")
	private boolean appendCompactedFile(String srcPath, String newPath, int states) throws GraphCreationException {
		debug("Copying and compacting " + srcPath + " to " + newPath);
		final InputStream in = openFileToFastRead(srcPath);
		final OutputStream out = openFileToAppend(newPath);
		final byte[] tmp = new byte[] { 0, 0, 0, 0 };
		final byte[] buf = new byte[1024 * 8];
		int n, size = 0, c = 0, state, lastState = -1;
		try {
			while (true) {
				n = in.read(buf, size, buf.length - size);
				if (n <= 0)
					break;
				size += n;
				int i = 0;
				for (i = 0; i + 3 < size; i += 4) {
					state = composeInt(buf, i);
					if (i == 0 || state == lastState) {
						c++;
					} else {
						decomposeInt(lastState, tmp);
						out.write(tmp);
						decomposeInt(c, tmp);
						out.write(tmp);
						c = 1;

					}
					lastState = state;
				}
				size -= i;
			}
			// write last state
			if (lastState >= 0) {
				decomposeInt(lastState, tmp);
				out.write(tmp);
				decomposeInt(c, tmp);
				out.write(tmp);
			}
		} catch (IOException e) {
			throw new GraphCreationException("Could not write to file " + srcPath + " to " + newPath + ": "
					+ e.getMessage(), e);
		} finally {
			closeStream(in);
			closeStream(out);
		}
		return true;
	}

	@SuppressWarnings("unused")
	private void compactAndWriteFile_saveMemory(int segment, byte[] array, String path) throws GraphCreationException {
		debug("SAVE MEM: Compacting array and writing it to a file " + path);
		OutputStream out = openFileToAppend(path);
		final byte[] zero = new byte[] { (byte) 0, (byte) 0, (byte) 0, (byte) 0 };
		final byte[] buf = new byte[4];
		try {
			int position = 0;
			for (int dsegment = 0; dsegment < segments; dsegment++) {
				int states = globalStatesCount[segment];
				int transitions = globalTransitionsCount[segment][dsegment];
				if (transitions == 0) {
					for (int i = 0; i < states; i++) {
						out.write(zero);
					}
				} else {
					int first = composeInt(array, position);
					for (int i = 0; i < first; i++)
						out.write(zero);
					int c = 1, last = composeInt(array, position);
					position += 4;
					for (int t = 1; t < transitions; t++, position += 4) {
						if (equalsInt(array, position)) {
							c++;
						} else {
							decomposeInt(c, buf);
							out.write(buf);
							c = 1;
							int curr = composeInt(array, position);
							if (last >= 0) {
								for (int j = last + 1; j < curr; j++)
									out.write(zero);
							}
							last = curr;
						}
					}
					decomposeInt(c, buf);
					out.write(buf);
					for (int i = last + 1; i < states; i++)
						out.write(zero);
				}
			}
		} catch (IOException e) {
			throw new GraphCreationException("Could not write compacted file " + path + ": " + e.getMessage(), e);
		} finally {
			closeStream(out);
		}

	}

	@SuppressWarnings("unused")
	private boolean compactAndWriteFile(int segment, byte[] array, String path) throws GraphCreationException {
		debug("Compacting array and writing it to a file " + path);

		// compute the number of all states in the segment
		int states = globalStatesCount[segment];

		// create resulting buffer
		byte[] buf = null;
		try {
			buf = new byte[states * segments * 4];
		} catch (Throwable t) {
			return false;
		}

		// fill the compacted buffer
		int positionArr = 0;
		int positionCct = 0;
		for (int dsegment = 0; dsegment < segments; dsegment++) {
			int transitions = (transpose ? globalTransitionsCount[dsegment][segment]
					: globalTransitionsCount[segment][dsegment]);
			if (transitions == 0) {
				for (int i = 0; i < states; i++) {
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
				}
			} else {
				int first = composeInt(array, positionArr);
				for (int i = 0; i < first; i++) {
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
				}
				int c = 1, last = first;
				positionArr += 4;
				for (int t = 1; t < transitions; t++, positionArr += 4) {
					if (equalsInt(array, positionArr)) {
						c++;
					} else {
						decomposeInt(c, buf, positionCct);
						positionCct += 4;
						c = 1;
						int curr = composeInt(array, positionArr);
						for (int j = last + 1; j < curr; j++) {
							buf[positionCct++] = 0;
							buf[positionCct++] = 0;
							buf[positionCct++] = 0;
							buf[positionCct++] = 0;
						}
						last = curr;
					}
				}
				decomposeInt(c, buf, positionCct);
				positionCct += 4;
				for (int i = last + 1; i < states; i++) {
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
					buf[positionCct++] = 0;
				}
			}
		}

		// write out the compacted segment to a file
		OutputStream out = null;
		try {
			out = openFileToAppend(path);
			out.write(buf);
		} catch (IOException e) {
			throw new GraphCreationException("Could not write compacted file " + path + ": " + e.getMessage(), e);
		} finally {
			closeStream(out);
		}

		return true;
	}

	private void appendFile(String path, byte[] array, int len) throws GraphCreationException {
		debug("Appending array to file " + path);
		OutputStream bin = openFileToAppend(path);
		try {
			bin.write(array, 0, len);
		} catch (IOException e) {
			throw new GraphCreationException("Could not write to file " + path + ": " + e.getMessage(), e);
		} finally {
			closeStream(bin);
		}
	}

	private void writeFile(String path, byte[] array) throws GraphCreationException {
		debug("Writing array to file " + path);
		OutputStream bin = openFileToAppend(path);
		try {
			bin.write(array);
		} catch (IOException e) {
			throw new GraphCreationException("Could not write to file " + path + ": " + e.getMessage(), e);
		} finally {
			closeStream(bin);
		}
	}

	private void deleteFile(String path) throws GraphCreationException {
		debug("Deleting " + path);
		final File file = new File(path);
		boolean success = false;
		Throwable t = null;
		try {
			success = file.delete();
		} catch (Throwable t1) {
			t = t1;
		}
		if (!success)
			throw new GraphCreationException("Could not delete file " + path + (t != null ? (t.getMessage()) : ""), t);
	}

	private final class QuicksortableArrayOfIntPairs implements QuicksortableArray {

		private final byte[] loc;
		private final byte[] own;
		private final byte[] con;

		public QuicksortableArrayOfIntPairs(final byte[] loc, final byte[] own, final byte[] con) {
			this.loc = loc;
			this.own = own;
			this.con = con;
		}

		public int length() {
			return loc.length / 4;
		}

		public boolean less(int i, int j) {
			i <<= 2;
			j <<= 2;
			int oi = composeInt(own, i);
			int oj = composeInt(own, j);
			if (oi < oj)
				return true;
			if (oi > oj)
				return false;
			int si = composeInt(loc, i);
			int sj = composeInt(loc, j);
			if (si < sj)
				return true;
			if (si > sj)
				return false;
			int di = composeInt(con, i);
			int dj = composeInt(con, j);
			return di < dj;
		}

		private final void _swap4(final byte[] arr, final int i, final int j) {
			byte tmp = arr[i];
			arr[i] = arr[j];
			arr[j] = tmp;
			tmp = arr[i + 1];
			arr[i + 1] = arr[j + 1];
			arr[j + 1] = tmp;
			tmp = arr[i + 2];
			arr[i + 2] = arr[j + 2];
			arr[j + 2] = tmp;
			tmp = arr[i + 3];
			arr[i + 3] = arr[j + 3];
			arr[j + 3] = tmp;
		}

		public void swap(int i, int j) {
			i <<= 2;
			j <<= 2;
			_swap4(loc, i, j);
			_swap4(own, i, j);
			_swap4(con, i, j);
		}

	}

	private LinkedList<String> actions = new LinkedList<String>();
	private LinkedList<Long> times = new LinkedList<Long>();
	private LinkedList<Boolean> broken = new LinkedList<Boolean>();

	private void debug(String msg) {
		// verbose(msg);
	}

	private void verbose(String msg) {
		if (verbose) {
			if (broken.size() > 0 && !broken.getLast()) {
				broken.removeLast();
				broken.addLast(true);
				System.err.println();
			}
			System.err.println(msg);
		}
	}

	private void verboseStart(String action) {
		if (verbose) {
			System.err.print(action + " .. ");
			System.err.flush();
			actions.add(action);
			times.add(System.nanoTime());
			broken.add(false);
		}
	}

	private void verboseDone() {
		if (verbose) {
			if (times.size() == 0) {
				System.err.println("done");
				System.err.println("WARN! incorrect verbosity setup");
			} else {
				long time = System.nanoTime() - times.getLast();
				if (broken.getLast()) {
					System.err.print(actions.getLast() + " ");
				}
				System.err.println("done in " + ConversionUtils.ns2sec(time) + "s");
				broken.removeLast();
				times.removeLast();
				actions.removeLast();
			}
		}
	}

	private void verboseIns(String action, long start) {
		if (verbose) {
			System.err.print("(" + action + " in " + ConversionUtils.ns2sec(System.nanoTime() - start) + "s)");
		}
	}

}
