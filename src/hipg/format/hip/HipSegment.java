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

import java.io.FileNotFoundException;
import java.io.IOException;

import myutils.IOUtils.BufferedMultiFileReader;
import myutils.NetUtils;

public class HipSegment {

	private final int id;
	private final int states;
	private final int[] outTransitions;
	private final int[] inTransitions;
	private final String host;
	private final String dir;
	private final int format;
	private final int inFormat;

	public HipSegment(int id, int states, int[] outTransitions, int[] inTransitions, String host, String dir,
			int format, int inFormat) {
		this.id = id;
		this.states = states;
		this.outTransitions = outTransitions;
		this.inTransitions = inTransitions;
		this.host = host;
		this.dir = dir;
		this.format = format;
		this.inFormat = inFormat;
	}

	public int getId() {
		return id;
	}

	public int getStates() {
		return states;
	}

	public int[] getOutTransitions() {
		return outTransitions;
	}

	public int[] getInTransitions() {
		return inTransitions;
	}

	public String getHost() {
		return host;
	}

	public String getDir() {
		return dir;
	}

	public int getFormat() {
		return format;
	}

	public int getInFormat() {
		return inFormat;
	}

	public int getLocalOutTransitionsCount() {
		return outTransitions[getId()];
	}

	public int getRemoteOutTransitionsCount() {
		int sum = 0;
		for (int i = 0; i < outTransitions.length; i++)
			if (i != id)
				sum += outTransitions[i];
		return sum;
	}

	public int getOutTransitionsCount() {
		int sum = 0;
		for (int i = 0; i < outTransitions.length; i++)
			sum += outTransitions[i];
		return sum;
	}

	public int getInTransitionsCount() {
		int sum = 0;
		for (int i = 0; i < inTransitions.length; i++)
			sum += inTransitions[i];
		return sum;
	}

	@Override
	public String toString() {
		return "Segment(" + id + "): " + states + " states, " + getLocalOutTransitionsCount() + " local and "
				+ getRemoteOutTransitionsCount() + " remote transitions; " + " format = "
				+ HipHeader.formatToString(format) + "/" + HipHeader.formatToString(inFormat) + "; " + "located on "
				+ host + " in " + dir;
	}

	public void read(final TransitionHandler handler, final String path, final boolean transpose)
			throws GraphCreationException {

		if (host != null && host.length() > 0 && !host.equals("localhost") && !host.equals(NetUtils.GetHostName())) {
			throw new RuntimeException("Remote file locations " + "not supported yet!");
		}

		final int[] transitionCounts = (transpose ? inTransitions : outTransitions);
		final int transitions = (transpose ? getInTransitionsCount() : getOutTransitionsCount());

		final String srcPath1 = HipHeader.srcPath(path, id, transpose);
		final String dstPath1 = HipHeader.dstPath(path, id, transpose);

		final String locPath = (transpose ? dstPath1 : srcPath1);
		final String conPath = (transpose ? srcPath1 : dstPath1);

		final BufferedMultiFileReader reader;
		try {
			reader = new BufferedMultiFileReader(locPath, conPath);
		} catch (FileNotFoundException e) {
			throw new GraphCreationException(
					"Could not read files " + locPath + ", " + conPath + ": " + e.getMessage(), e);
		}

		final int t;
		try {
			if (format == HipHeader.FORMAT_NORMAL) {
				t = readNormal(reader, transitions, handler, transitionCounts);
			} else if (format == HipHeader.FORMAT_COMPACT) {
				t = readCompacted(reader, transitions, handler, transitionCounts);
			} else {
				throw new GraphCreationException("Format " + format + " of segment " + id + " not recognized");
			}
		} catch (Throwable e) {
			throw new GraphCreationException("Could not read " + locPath + ", " + conPath + ": " + e.getMessage(), e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Throwable tt) {
				}
			}
		}
		if (t != transitions) {
			throw new RuntimeException("Read " + t + " transitions " + "while expected " + transitions);
		}
	}

	private int readCompacted(BufferedMultiFileReader reader, int expectedTransitions, TransitionHandler handler,
			int[] transitionCounts) throws IOException, GraphCreationException {
		int[] buf = reader.getBuf();
		int currSeg = -1;
		int currTrans = 0;
		int src = -1, srcCount = 0;
		int t = 0;
		while (t < expectedTransitions) {
			if (currTrans == 0) {
				currSeg++;
				currTrans = transitionCounts[currSeg];
			}
			if (srcCount == 0) {
				if (!reader.readToBuf(true, false, false))
					throw new RuntimeException("Unexpected end of file " + " when only " + t + " out of "
							+ expectedTransitions + " transitions read");
				src = buf[0];
				if (!reader.readToBuf(true, false, false))
					throw new RuntimeException("Unexpected end of file " + " when only " + t + " out of "
							+ expectedTransitions + " transitions read");
				srcCount = buf[0];
			} else {
				t++;
				if (!reader.readToBuf(false, true, false))
					throw new RuntimeException("Unexpected end of file " + " when only " + t + " out of "
							+ expectedTransitions + " transitions read");
				handler.handle(id, src, currSeg, buf[1]);
				srcCount--;
				currTrans--;
			}
		}
		return t;
	}

	private int readNormal(final BufferedMultiFileReader reader, final int expectedTransitions,
			final TransitionHandler handler, final int[] transitionCounts) throws IOException, GraphCreationException {
		final int[] buf = reader.getBuf();
		int trans = 0, currSeg = 0, currTrans = transitionCounts[0];
		while (trans < expectedTransitions) {
			while (currTrans == 0) {
				currSeg++;
				if (currSeg >= transitionCounts.length) {
					throw new RuntimeException("Could not get segment " + currSeg + ", only " + trans
							+ " read while expected " + expectedTransitions);
				}
				currTrans = transitionCounts[currSeg];
			}
			if (!reader.readToBuf(true, true, false))
				throw new RuntimeException("Unexpected end of file " + "when only " + trans + " out of "
						+ expectedTransitions + " transitions read");
			trans++;
			currTrans--;
			handler.handle(id, buf[0], currSeg, buf[1]);
		}
		return trans;
	}

	public static interface TransitionHandler {
		public void handle(int locOwner, int locOffset, int conOwner, int conOffset) throws GraphCreationException;
	}
}
