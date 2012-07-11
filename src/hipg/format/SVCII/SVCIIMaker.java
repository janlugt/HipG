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

package hipg.format.SVCII;

import hipg.format.GraphCreationException;
import hipg.format.GraphMaker;
import hipg.graph.ExplicitNodeReference;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import myutils.IOUtils;
import myutils.Quicksort;
import myutils.Quicksort.QuicksortableArray;

/**
 * Creates large graphs in the SvcII format sequwntially (stores on disc).
 * 
 * @author Ela Krepska e.krepska@vu.nl
 */
public final class SVCIIMaker implements GraphMaker {

	private static final int MEMORY = 64 * 1024 * 1024;
	private static final boolean debug = false;
	private static final boolean trace = false;

	private final boolean verbose;
	private final boolean withLabels;
	private final String path;
	private final int segments;
	private final int[] globalStatesCount;
	private final int[][] globalTransitionsCount;
	private final int[][] transitionsCount;
	private final int[][][] sources;
	private final int[][][] destinations;
	private final int[][][] labels;
	private final Set<Integer> labelSet = new HashSet<Integer>();
	private int segmentRR = 0;
	private int lastseg = 0;
	private final int colocationCnt;
	private final boolean random;
	private final boolean[][] sortNeeded;
	private final int[][] lastTransition;

	/**
	 * Creates a graph maker.
	 * 
	 * @param path
	 *            Directory where the graph will be created
	 * @param segments
	 *            The number of chunks the graph will be partitioned into
	 * @throws GraphCreationException
	 */
	public SVCIIMaker(String path, int segments, boolean verbose) throws GraphCreationException {
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
	public SVCIIMaker(String path, int segments, int colocationCnt, boolean verbose, boolean random, boolean withLabels)
			throws GraphCreationException {
		this.verbose = verbose;
		this.path = path;
		this.segments = segments;
		this.globalStatesCount = new int[segments];
		this.globalTransitionsCount = new int[segments][segments];
		this.transitionsCount = new int[segments][segments];
		int size = MEMORY / segments / segments / 3;
		this.sources = new int[segments][segments][size];
		this.destinations = new int[segments][segments][size];
		this.labels = (withLabels ? new int[segments][segments][size] : null);
		this.colocationCnt = colocationCnt;
		this.random = random;
		this.withLabels = withLabels;
		this.sortNeeded = new boolean[segments][segments];
		this.lastTransition = new int[segments][segments];
		for (int i = 0; i < lastTransition.length; i++)
			for (int j = 0; j < lastTransition[i].length; j++)
				lastTransition[i][j] = -1;
		start();
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
		if (id > Integer.MAX_VALUE / 2)
			sync(owner);
		if (trace)
			System.err.println("Added node (" + owner + "," + id + ")");
		return ExplicitNodeReference.createReference(id, owner);
	}

	private final Random rand = new Random(System.currentTimeMillis());

	/**
	 * Adds node to the graph while the actual segment is determined by the framework.
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
		if (id > Integer.MAX_VALUE / 2)
			sync(owner);
		if (trace)
			System.err.println("Added node (" + owner + "," + id + ")");
		return ExplicitNodeReference.createReference(id, owner);
	}

	/**
	 * Adds transitions to the graph between given nodes and with a given label.
	 * 
	 * @param from
	 *            The (handle of) source node of the new transition
	 * @param to
	 *            The (handle of) destination node of the new transition
	 * @param label
	 *            The label of the new transition
	 * @throws GraphCreationException
	 */
	public void addTransition(long from, long to, int label) throws GraphCreationException {
		int fromOwner = ExplicitNodeReference.getOwner(from);
		int fromId = ExplicitNodeReference.getId(from);
		int toOwner = ExplicitNodeReference.getOwner(to);
		int toId = ExplicitNodeReference.getId(to);
		addTransition(fromOwner, fromId, toOwner, toId, label);
	}

	/**
	 * Adds transitions to the graph between given nodes and with a given label.
	 * 
	 * @param fromOwner
	 *            The owner of the source node of the new transition
	 * @param fromId
	 *            The id of the source node of the new transition
	 * @param toOwner
	 *            The owner of the destination node of the new transition
	 * @param toId
	 *            The id of the destination node of the new transition
	 * @param label
	 *            The label of the new transition
	 * @throws GraphCreationException
	 */
	public void addTransition(int fromOwner, int fromId, int toOwner, int toId, int label)
			throws GraphCreationException {
		globalTransitionsCount[fromOwner][toOwner]++;
		int index = transitionsCount[fromOwner][toOwner]++;
		sources[fromOwner][toOwner][index] = fromId;
		destinations[fromOwner][toOwner][index] = toId;
		if (!sortNeeded[fromOwner][toOwner]) {
			if (fromId < lastTransition[fromOwner][toOwner])
				sortNeeded[fromOwner][toOwner] = true;
			lastTransition[fromOwner][toOwner] = fromId;
		}
		if (withLabels) {
			labels[fromOwner][toOwner][index] = label;
			if (!labelSet.contains(label))
				labelSet.add(label);
		}
		if (trace)
			System.err.println("Added transition (" + fromOwner + "," + fromId + ")->(" + toOwner + "," + toId + ")");
		if (transitionsCount[fromOwner][toOwner] >= sources[fromOwner][toOwner].length)
			sync(fromOwner);
	}

	/**
	 * Sync new transitions with disc.
	 * 
	 * @param segment
	 * @throws GraphCreationException
	 */
	private void sync(int segment) throws GraphCreationException {

		if (verbose)
			System.err.print("Sync segment " + segment + "..");

		for (int dsegment = 0; dsegment < segments; dsegment++) {
			int count = transitionsCount[segment][dsegment];

			String srcPath = path + File.separator + "src-" + segment + "-" + dsegment;
			String dstPath = path + File.separator + "dest-" + segment + "-" + dsegment;
			String labPath = path + File.separator + "label-" + segment + "-" + dsegment;

			if (debug)
				System.err.println("Writing " + count + " new transitions to " + srcPath + ", " + dstPath + ", "
						+ labPath + ", with labels ? " + withLabels);

			OutputStream src = null;
			OutputStream dst = null;
			OutputStream lab = null;
			try {
				src = reopenFileToAppend(srcPath);
				dst = reopenFileToAppend(dstPath);
				if (withLabels)
					lab = reopenFileToAppend(labPath);

				if (count > 0) {
					int[] newSrc = sources[segment][dsegment];
					int[] newDst = destinations[segment][dsegment];
					int[] newLab = (withLabels ? labels[segment][dsegment] : null);
					for (int i = 0; i < count; i++) {
						IOUtils.writeInt(newSrc[i], src);
						IOUtils.writeInt(newDst[i], dst);
						if (withLabels) {
							IOUtils.writeInt(newLab[i], lab);
						}
					}
				}

				transitionsCount[segment][dsegment] = 0;

			} catch (Throwable t) {
				throw new GraphCreationException("Could not create transitions from " + segment + " to " + dst + ": "
						+ t.getMessage(), t);
			} finally {
				if (src != null)
					try {
						src.close();
					} catch (Throwable t) {
						System.err.println("Could not close src file " + srcPath + ": " + t.getMessage());
					}
				if (dst != null)
					try {
						dst.close();
					} catch (Throwable t) {
						System.err.println("Could not close dst file " + dstPath + ": " + t.getMessage());
					}
				if (withLabels && lab != null)
					try {
						lab.close();
					} catch (Throwable t) {
						System.err.println("Could not close lab file " + labPath + ": " + t.getMessage());
					}
			}
		}
		if (verbose)
			System.err.println("OK");

	}

	/**
	 * Sort transitions on source.
	 * 
	 * @param segment
	 * @throws GraphCreationException
	 */
	private void sort(int segment) throws GraphCreationException {
		try {

			if (verbose)
				System.err.print("Sort segment " + segment + "..");

			/* update edges/transitions so that they are sorted */

			for (int dsegment = 0; dsegment < segments; dsegment++) {

				if (!sortNeeded[segment][dsegment]) {
					if (verbose)
						System.err.println("Fragment(" + segment + ", " + dsegment + ") already sorted");
					continue;
				}

				String srcPath = path + File.separator + "src-" + segment + "-" + dsegment;
				String dstPath = path + File.separator + "dest-" + segment + "-" + dsegment;
				String labPath = path + File.separator + "label-" + segment + "-" + dsegment;

				byte[] src = readFile(srcPath);
				if (src == null)
					throw new GraphCreationException("Could not read " + srcPath);

				byte[] dst = readFile(dstPath);
				if (dst == null)
					throw new GraphCreationException("Could not read " + dstPath);

				byte[] lab = null;
				if (withLabels) {
					lab = readFile(labPath);
					if (lab == null)
						throw new GraphCreationException("Could not read " + labPath);
				}

				if ((src.length != dst.length) || (lab != null && src.length != lab.length)
						|| (lab != null && dst.length != lab.length))
					throw new GraphCreationException("Files " + srcPath + " (len " + src.length + "), " + dstPath
							+ " (len " + dst.length + ")"
							+ (lab != null ? labPath + " (len " + lab.length + ") not the same length" : ""));

				int length = src.length / 4;

				if (debug)
					System.err.println("Sorting " + srcPath + ", read " + src.length + " bytes, ie " + length
							+ " transitions");

				if (length > 1)
					Quicksort.quicksort(new Tab(src, dst, lab));

				if (!writeFile(srcPath, src))
					throw new GraphCreationException("Could not write sorted file " + srcPath);
				if (!writeFile(dstPath, dst))
					throw new GraphCreationException("Could not write sorted file " + dstPath);
				if (lab != null)
					if (!writeFile(labPath, lab))
						throw new GraphCreationException("Could not write sorted file " + labPath);
				if (debug)
					System.err.println("Sorting " + srcPath + ", written " + src.length + " bytes");

			}
			if (verbose)
				System.err.println("OK");

		} catch (GraphCreationException gce) {
			throw new GraphCreationException("Could not sort: " + gce.getMessage());
		}
	}

	/**
	 * Write info/header file.
	 * 
	 * @param root
	 *            Root node
	 * @throws GraphCreationException
	 */
	private void info(long root) throws GraphCreationException {

		if (verbose)
			System.err.print("Writing metadata..");

		/* write header */
		String headerPath = path + File.separator + "info";
		File headerFile = new File(headerPath);
		OutputStream header = null;
		long globalStates = 0;
		long globalTransitions = 0;

		try {
			boolean ok = headerFile.createNewFile();
			if (!ok)
				throw new GraphCreationException("Info file " + headerPath + " already exists");
			header = new BufferedOutputStream(new FileOutputStream(headerFile));

			// write version
			int version = 31;
			IOUtils.writeInt(version, header);
			if (debug)
				System.err.println("Written " + "graph version: " + version);

			// write user info
			String info = "Generated by HipG";
			IOUtils.writeShortString(info, header);
			if (debug)
				System.err.println("Written info: " + info);

			// write #segments
			IOUtils.writeInt(segments, header);
			if (debug)
				System.err.println("Writen #segments: " + segments);

			// write root
			int rootSegment = ExplicitNodeReference.getOwner(root);
			int rootOffset = ExplicitNodeReference.getId(root);
			IOUtils.writeInt(rootSegment, header);
			IOUtils.writeInt(rootOffset, header);
			if (debug)
				System.err.println("Written root: in segment " + rootSegment + " with offset " + rootOffset);

			// write #labels
			int labelsCount = labelSet.size();
			IOUtils.writeInt(labelsCount, header);
			if (debug)
				System.err.println("Written labels: " + labelsCount);

			// tau
			int tau = -1;
			IOUtils.writeInt(tau, header);
			if (debug)
				System.err.println("Written tau: " + tau);

			// dummy
			int dummy = 0;
			IOUtils.writeInt(dummy, header);
			if (debug)
				System.err.println("Written dummy: " + dummy);

			// #states in segments
			for (int i = 0; i < segments; i++) {
				IOUtils.writeInt(globalStatesCount[i], header);
				globalStates += globalStatesCount[i];
			}
			if (debug)
				System.err.println("Written #states (" + globalStates + ")");

			// #transitions
			for (int i = 0; i < segments; i++) {
				for (int j = 0; j < segments; j++) {
					IOUtils.writeInt(globalTransitionsCount[i][j], header);
					globalTransitions += globalTransitionsCount[i][j];
				}
			}
			if (debug)
				System.err.println("Written #transitions (" + globalTransitions + ")");

		} catch (Throwable t) {
			throw new GraphCreationException("Could not write header of SvcII format dir " + path + ": "
					+ t.getMessage(), t);
		} finally {
			if (header != null)
				try {
					header.close();
				} catch (Throwable t1) {
					System.err.println("WARN: Could not close header: " + t1.getMessage());
				}
		}
		if (verbose)
			System.err.println("OK: segments=" + segments + " labels=" + labelSet.size() + " global states="
					+ globalStates + " global transitions=" + globalTransitions + " root="
					+ ExplicitNodeReference.referenceToString(root));
	}

	/**
	 * Init and check structure.
	 * 
	 * @throws GraphCreationException
	 */
	private void start() throws GraphCreationException {
		/* create directory */
		if (verbose)
			System.err.print("Creating directory " + path + "..");
		final File directory = new File(path);
		if (directory.exists()) {
			throw new GraphCreationException("Cannot write SvcII format dir " + path + ": Path exists");
		} else {
			try {
				if (!directory.mkdirs())
					throw new GraphCreationException("Could not create directory " + path);
			} catch (Throwable t) {
				throw new GraphCreationException("Could not create directory " + path + ": " + t.getMessage(), t);
			}
		}
		if (verbose)
			System.err.println("OK");
	}

	/**
	 * Finishes writing the graph: synchronized with disk, sorts if required, writes metadata, closes.
	 * 
	 * @param root
	 *            Root node
	 * @throws GraphCreationException
	 */
	public void finish(long root) throws GraphCreationException {

		for (int segment = 0; segment < segments; segment++)
			sync(segment);

		info(root);

		try {
			for (int segment = 0; segment < segments; segment++) {
				sort(segment);
			}
		} catch (Throwable t) {
			System.err.println("WARN: Sorting failed: " + t.getMessage() + " Abandoning sorting attempt");
			t.printStackTrace();
			System.err.println();
		}

		if (verbose) {
			System.err.println("Graph " + path + " created [states min=" + getGlobalStateCountMin() + ", max="
					+ getGlobalStateCountMax() + " and transitions min=" + getGlobalTransitionsCountMin() + ", max="
					+ getGlobalTransitionsCountMax() + "]");
		}
	}

	public long getGlobalStateCount() {
		long stateCount = 0;
		for (int s : globalStatesCount)
			stateCount += s;
		return stateCount;
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

	private static OutputStream reopenFileToAppend(String path) throws GraphCreationException {
		File file = new File(path);
		if (!file.exists())
			try {
				if (!file.createNewFile())
					throw new GraphCreationException("Could not create file " + path);
			} catch (Throwable t) {
				throw new GraphCreationException("Could not create file " + path + ": " + t.getMessage(), t);
			}
		FileOutputStream out;
		try {
			out = new FileOutputStream(file, true);
		} catch (FileNotFoundException e) {
			throw new GraphCreationException("Coule not open output stream for file " + path);
		}
		BufferedOutputStream bout = new BufferedOutputStream(out);
		return bout;
	}

	private static byte[] readFile(String path) {
		File file = new File(path);
		FileInputStream in;
		try {
			in = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			return null;
		}
		BufferedInputStream bin = new BufferedInputStream(in);
		long llength = file.length();
		if (llength > Integer.MAX_VALUE)
			return null;
		int length = (int) llength;
		byte[] array = new byte[length];
		int position = 0;
		try {
			while (position < length)
				position += bin.read(array, position, length - position);
		} catch (IOException e) {
			return null;
		}
		try {
			bin.close();
		} catch (IOException e) {
		}
		return array;
	}

	private static boolean writeFile(String path, byte[] array) {
		final File file = new File(path);
		final FileOutputStream out;
		try {
			out = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			return false;
		}
		BufferedOutputStream bin = null;
		try {
			bin = new BufferedOutputStream(out);
			bin.write(array);
		} catch (IOException e) {
			return false;
		} finally {
			if (bin != null)
				try {
					bin.close();
				} catch (Throwable t) {
				}
		}
		return true;
	}

	private final class Tab implements QuicksortableArray {

		private byte[] src;
		private byte[] dst;
		private byte[] lab;

		public Tab(byte[] src, byte[] dst, byte[] lab) {
			this.src = src;
			this.dst = dst;
			this.lab = lab;
		}

		@Override
		public int length() {
			return src.length / 4;
		}

		@Override
		public boolean less(int index1, int index2) {
			index1 <<= 2;
			index2 <<= 2;
			if (src[index1] < src[index2])
				return true;
			if (src[index1] == src[index2]) {
				if (src[index1 + 1] < src[index2 + 1])
					return true;
				if (src[index1 + 1] == src[index2 + 1]) {
					if (src[index1 + 2] < src[index2 + 2])
						return true;
					if (src[index1 + 2] == src[index2 + 2])
						return (src[index1 + 3] < src[index2 + 3]);
				}
			}
			return false;
		}

		@Override
		public void swap(int pos1, int pos2) {
			if (pos1 != pos2) {

				pos1 <<= 2;
				pos2 <<= 2;
				byte tmp;

				tmp = src[pos1];
				src[pos1] = src[pos2];
				src[pos2] = tmp;
				tmp = src[pos1 + 1];
				src[pos1 + 1] = src[pos2 + 1];
				src[pos2 + 1] = tmp;
				tmp = src[pos1 + 2];
				src[pos1 + 2] = src[pos2 + 2];
				src[pos2 + 2] = tmp;
				tmp = src[pos1 + 3];
				src[pos1 + 3] = src[pos2 + 3];
				src[pos2 + 3] = tmp;

				tmp = dst[pos1];
				dst[pos1] = dst[pos2];
				dst[pos2] = tmp;
				tmp = dst[pos1 + 1];
				dst[pos1 + 1] = dst[pos2 + 1];
				dst[pos2 + 1] = tmp;
				tmp = dst[pos1 + 2];
				dst[pos1 + 2] = dst[pos2 + 2];
				dst[pos2 + 2] = tmp;
				tmp = dst[pos1 + 3];
				dst[pos1 + 3] = dst[pos2 + 3];
				dst[pos2 + 3] = tmp;

				if (lab != null) {
					tmp = lab[pos1];
					lab[pos1] = lab[pos2];
					lab[pos2] = tmp;
					tmp = lab[pos1 + 1];
					lab[pos1 + 1] = lab[pos2 + 1];
					lab[pos2 + 1] = tmp;
					tmp = lab[pos1 + 2];
					lab[pos1 + 2] = lab[pos2 + 2];
					lab[pos2 + 2] = tmp;
					tmp = lab[pos1 + 3];
					lab[pos1 + 3] = lab[pos2 + 3];
					lab[pos2 + 3] = tmp;
				}
			}
		}

	}

}
