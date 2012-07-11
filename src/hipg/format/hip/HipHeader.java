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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import myutils.IOUtils;

public class HipHeader {

	private final static int MAGIC = 17;
	final static int FORMAT_NORMAL = 0;
	final static int FORMAT_COMPACT = 1;

	/** Version of the graph. */
	private final int magic;
	/** Info about the graph. */
	private final String info;
	/** Segment containing root. */
	private final int rootSegment;
	/** Root's position. */
	private final int rootOffset;
	/** Files locations. */
	private final ArrayList<HipSegment> segments;

	public HipHeader(String info, int rootSegment, int rootOffset, ArrayList<HipSegment> segments) {
		this.magic = MAGIC;
		this.info = info;
		this.rootSegment = rootSegment;
		this.rootOffset = rootOffset;
		this.segments = segments;
	}

	public HipHeader(String headerPath) throws GraphCreationException {

		InputStream header;
		try {
			header = new BufferedInputStream(new FileInputStream(headerPath));
		} catch (FileNotFoundException e) {
			throw new GraphCreationException("Header file " + headerPath + " not found: " + e.getMessage(), e);
		}

		String f = null;
		int s = 0;
		try {
			f = "magic";
			this.magic = checkVal(f, IOUtils.readInt(header), MAGIC);
			f = "info";
			this.info = IOUtils.readString(header);
			f = "#segments";
			int segmentsCount = checkPositive(f, IOUtils.readInt(header));
			f = "rootSegment";
			this.rootSegment = IOUtils.readInt(header);
			f = "rootOffset";
			this.rootOffset = IOUtils.readInt(header);
			f = "#hosts";
			int hostsCount = checkPositive(f, IOUtils.readInt(header));
			String[] hosts = new String[hostsCount];
			for (int i = 0; i < hostsCount; i++) {
				f = "host#" + i + "/" + hostsCount;
				hosts[i] = IOUtils.readString(header);
			}
			f = "#dirs";
			int dirsCount = checkPositive(f, IOUtils.readInt(header));
			String[] dirs = new String[dirsCount];
			for (int i = 0; i < dirsCount; i++) {
				f = "dir#" + i + "/" + dirsCount;
				dirs[i] = IOUtils.readString(header);
			}
			this.segments = new ArrayList<HipSegment>();
			for (int i = 0; i < segmentsCount; i++) {
				String pref = "segment#" + i + "/" + segmentsCount + "#";
				f = pref + "states";
				int states = checkNonNegative(f, IOUtils.readInt(header));
				f = "segment#" + i + "#localOutTransitionsLen";
				int[] outTransitions = new int[segmentsCount];
				for (int j = 0; j < outTransitions.length; j++) {
					f = "segment#" + i + "#outTransitions#" + j;
					outTransitions[j] = checkNonNegative(f, IOUtils.readInt(header));
				}
				int[] inTransitions = new int[segmentsCount];
				for (int j = 0; j < inTransitions.length; j++) {
					f = "segment#" + i + "#inTransitions#" + j;
					inTransitions[j] = checkNonNegative(f, IOUtils.readInt(header));
				}
				f = "segment#" + i + "#host";
				int host = checkNonNegative(f, IOUtils.readInt(header));
				if (host >= hosts.length)
					throw new GraphCreationException("Unknown host " + host + ". Known hosts are: "
							+ Arrays.toString(hosts));
				f = "segment#" + i + "#dir";
				int dir = checkNonNegative(f, IOUtils.readInt(header));
				if (dir >= dirs.length)
					throw new GraphCreationException("Unknown dir " + dir + ". Known dir are: " + Arrays.toString(dirs));
				f = "segment#" + i + "#format";
				int format = IOUtils.readInt(header);
				f = "segment#" + i + "#inFormat";
				int inFormat = IOUtils.readInt(header);
				HipSegment segment = new HipSegment(s++, states, outTransitions, inTransitions, hosts[host], dirs[dir],
						format, inFormat);
				this.segments.add(segment);
			}
		} catch (IOException e) {
			throw new GraphCreationException("Error when reading header file " + headerPath + ", field " + f + ": "
					+ e.getMessage(), e);
		}
	}

	public void write(String headerPath) throws GraphCreationException {
		/* create host structure */
		Vector<String> hosts = new Vector<String>();
		for (HipSegment segment : segments) {
			if (!hosts.contains(segment.getHost()))
				hosts.add(segment.getHost());
		}
		/* create dirs structure */
		Vector<String> dirs = new Vector<String>();
		for (HipSegment segment : segments) {
			if (!dirs.contains(segment.getDir()))
				dirs.add(segment.getDir());
		}
		/* open header file */
		OutputStream header;
		try {
			header = new BufferedOutputStream(new FileOutputStream(headerPath));
		} catch (FileNotFoundException e) {
			throw new GraphCreationException("File " + headerPath + " not found when writing? " + e.getMessage(), e);
		}
		/* write data to header */
		String f = null;
		try {
			f = "magic";
			IOUtils.writeInt(magic, header);
			f = "info";
			IOUtils.writeString(info, header);
			f = "segments";
			IOUtils.writeInt(segments.size(), header);
			f = "rootSegment";
			IOUtils.writeInt(rootSegment, header);
			f = "rootOffset";
			IOUtils.writeInt(rootOffset, header);
			f = "#hosts";
			IOUtils.writeInt(hosts.size(), header);
			for (int i = 0; i < hosts.size(); i++) {
				f = "host#" + i + "/" + hosts.size();
				IOUtils.writeString(hosts.get(i), header);
			}
			f = "#dirs";
			IOUtils.writeInt(dirs.size(), header);
			for (int i = 0; i < dirs.size(); i++) {
				f = "dir#" + i + "/" + dirs.size();
				IOUtils.writeString(dirs.get(i), header);
			}
			for (int i = 0; i < segments.size(); i++) {
				String pref = "segment#" + i + "/" + segments.size() + "#";
				HipSegment segment = segments.get(i);
				f = pref + "states";
				IOUtils.writeInt(segment.getStates(), header);
				int[] outTransitions = segment.getOutTransitions();
				for (int j = 0; j < outTransitions.length; j++) {
					f = pref + "outTransitions#" + j;
					IOUtils.writeInt(outTransitions[j], header);
				}
				int[] inTransitions = segment.getInTransitions();
				for (int j = 0; j < inTransitions.length; j++) {
					f = pref + "inTransitions#" + j;
					IOUtils.writeInt(inTransitions[j], header);
				}
				f = pref + "host";
				int host = hosts.indexOf(segment.getHost());
				IOUtils.writeInt(host, header);
				f = pref + "dir";
				int dir = dirs.indexOf(segment.getDir());
				IOUtils.writeInt(dir, header);
				f = pref + "format";
				IOUtils.writeInt(segment.getFormat(), header);
				f = pref + "inFormat";
				IOUtils.writeInt(segment.getInFormat(), header);
			}
		} catch (IOException e) {
			throw new GraphCreationException("Error when writeing header file " + headerPath + ", field " + f + ": "
					+ e.getMessage(), e);
		} finally {
			if (header != null) {
				try {
					header.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public final int getVersion() {
		return magic;
	}

	public int getRootSegment() {
		return rootSegment;
	}

	public int getRootOffset() {
		return rootOffset;
	}

	public final String getInfo() {
		return info;
	}

	public long getGlobalStateCount() {
		long sum = 0;
		for (HipSegment segment : segments)
			sum += segment.getStates();
		return sum;
	}

	public long getGlobalOutTransitions() {
		long sum = 0;
		for (HipSegment segment : segments) {
			int[] outTransitions = segment.getOutTransitions();
			for (int t : outTransitions) {
				sum += t;
			}
		}
		return sum;
	}

	public long getGlobalInTransitions() {
		long sum = 0;
		for (HipSegment segment : segments) {
			int[] inTransitions = segment.getInTransitions();
			for (int t : inTransitions) {
				sum += t;
			}
		}
		return sum;
	}

	public HipSegment getSegment(int segment) {
		return segments.get(segment);
	}

	public ArrayList<HipSegment> getSegments() {
		return segments;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Graph '" + info + "' with root " + rootOffset + "@" + rootSegment
				+ " with segments: \n");
		for (HipSegment seg : segments) {
			sb.append("   ");
			sb.append(seg.toString());
			sb.append("\n");
		}
		return sb.toString();
	}

	private static final int checkVal(String f, int s, long expected) throws GraphCreationException {
		if (s != expected)
			throw new GraphCreationException("Incorrect field " + f + ": " + s);
		return s;
	}

	private static final int checkPositive(String f, int s) throws GraphCreationException {
		if (s <= 0)
			throw new GraphCreationException("Incorrect field " + f + ": " + s);
		return s;
	}

	private static final int checkNonNegative(String f, int s) throws GraphCreationException {
		if (s < 0)
			throw new GraphCreationException("Incorrect field " + f + ": " + s);
		return s;
	}

	public static String srcPath(String path, int segment, boolean transpose) {
		return path + File.separator + (transpose ? "in-" : "") + "src-" + segment;
	}

	public static String dstPath(String path, int segment, boolean transpose) {
		return path + File.separator + (transpose ? "in-" : "") + "dst-" + segment;
	}

	public static String ownPath(String path, int segment, boolean transpose) {
		return path + File.separator + (transpose ? "in-" : "") + "own-" + segment;
	}

	public static String headerPath(String path) {
		return path + File.separator + "info";
	}

	public static String formatToString(int format) {
		switch (format) {
		case FORMAT_NORMAL:
			return "Normal";
		case FORMAT_COMPACT:
			return "Compact";
		default:
			return "UNRECOGNIZED";
		}
	}
}
