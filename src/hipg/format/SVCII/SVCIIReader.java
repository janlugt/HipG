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

import hipg.Graph;
import hipg.LocalNode;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.utils.ReflectionUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import myutils.ConsoleProgress;
import myutils.ConversionUtils;
import myutils.IOUtils;
import myutils.IOUtils.BufferedMultiFileReader;
import myutils.MathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a graph in SVCII format.
 * 
 * @author ela, ekr@cs.vu.nl
 */
public class SVCIIReader {

	/** Logging facilities. */
	private static final Logger logger = LoggerFactory.getLogger(SVCIIReader.class);

	/** Reads a directed graph in SVC-II format. */
	public static <TNode extends Node> ExplicitGraph<TNode> read(
			@SuppressWarnings("rawtypes") final Class<? extends LocalNode> TLocalNodeClass,
			final Class<TNode> TNodeClass, final String path, final int rank, final boolean transpose,
			final boolean saveMemory) throws GraphCreationException {

		String loggerPrefix = "(Ibis " + rank + ") ";
		if (rank == 0)
			logger.info(loggerPrefix + "Reading SVC-II dir " + path);

		// read header
		int mySegment = rank;
		String headerPath = path + File.separator + "info";
		if (logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Reading SVC-II header " + headerPath);
		File headerFile = new File(headerPath);
		InputStream header = null;

		final int segmentCount;
		final int myStateCount;
		final int[] stateCount;
		final long rootSegment;
		final long rootOffset;
		long globalStateCount;
		long myTransitionCount;
		long myInTransitionCount;
		final int[][] transitionSizes;
		final boolean hasLabels;

		long startReadHeader = System.nanoTime();
		try {

			header = new BufferedInputStream(new FileInputStream(headerFile));

			// version
			long version = IOUtils.readInt(header);
			if (version != 31)
				throw new GraphCreationException("Incorrect version (" + version + ") for the SvcII format dir " + path);
			if (logger.isDebugEnabled())
				logger.debug(loggerPrefix + "Graph version: " + version);

			// user info
			String info = IOUtils.readShortString(header);
			if (logger.isDebugEnabled())
				logger.debug(loggerPrefix + "Info: " + info);

			// #segments
			long segmentCountLong = IOUtils.readInt(header);
			if (segmentCountLong > Integer.MAX_VALUE)
				throw new GraphCreationException("Attempt to create too many (" + segmentCountLong
						+ ") segments for the SvcII format dir " + path);
			segmentCount = (int) segmentCountLong;
			if (mySegment < 0 || mySegment >= segmentCount)
				throw new GraphCreationException("Requested segment " + mySegment + " is should be between 0 and "
						+ segmentCount + " (is " + mySegment + ") for the SvcII format dir " + path);
			if (logger.isDebugEnabled())
				logger.debug(loggerPrefix + "Segments: " + segmentCount);
			if (mySegment >= segmentCount)
				throw new GraphCreationException("Expected segment " + mySegment + " not present within "
						+ segmentCount + " segments for the SvcII format dir " + path);

			// segment root
			rootSegment = IOUtils.readInt(header);
			rootOffset = IOUtils.readInt(header);
			if (rootSegment > segmentCount)
				throw new GraphCreationException("Incorrect root segment (" + rootSegment
						+ ") for the SvcII format dir " + path);
			if (rootOffset > Integer.MAX_VALUE)
				throw new GraphCreationException("Incorrect root offset (" + rootOffset + ") for the SvcII format dir "
						+ path);
			if (logger.isDebugEnabled())
				logger.debug(loggerPrefix + "Root: in segment " + rootSegment + " with offset " + rootOffset);

			// #labels
			long labels = IOUtils.readInt(header);
			if (logger.isDebugEnabled())
				logger.debug(loggerPrefix + "Labels: " + labels);
			hasLabels = (labels > 1);
			if (hasLabels) {
				logger.warn(loggerPrefix + "Labels not supported yet, will be ignored");
			}

			// tau
			long tau = IOUtils.readInt(header);
			if (logger.isDebugEnabled())
				logger.debug(loggerPrefix + "Tau: " + tau);

			// dummy
			long dummy = IOUtils.readInt(header);
			if (logger.isDebugEnabled())
				logger.debug(loggerPrefix + "Dummy: " + dummy);

			// #states in segments
			stateCount = new int[segmentCount];
			globalStateCount = 0;
			for (int i = 0; i < segmentCount; i++) {
				long size = IOUtils.readInt(header);
				if (size > Integer.MAX_VALUE) {
					throw new GraphCreationException("Cannot handle more than " + Integer.MAX_VALUE + " nodes");
				}
				stateCount[i] = (int) size;
				globalStateCount += size;
				if (logger.isDebugEnabled())
					logger.debug(loggerPrefix + "States in segment " + i + ": " + size);
			}
			if (globalStateCount > Integer.MAX_VALUE) {
				logger.warn(loggerPrefix + "Too many nodes: " + globalStateCount);
				throw new GraphCreationException("Cannot handle more than " + Integer.MAX_VALUE + " nodes");
			}
			myStateCount = stateCount[mySegment];
			if (logger.isInfoEnabled())
				logger.info(loggerPrefix + "My states: " + myStateCount + " / " + globalStateCount + ": "
						+ MathUtils.proc(myStateCount, globalStateCount) + "%");

			// #transitions
			long globalTransitionCount = 0;
			transitionSizes = new int[segmentCount][segmentCount];
			myTransitionCount = 0;
			myInTransitionCount = 0;
			for (int i = 0; i < segmentCount; i++) {
				for (int j = 0; j < segmentCount; j++) {
					long t = IOUtils.readInt(header);
					if (t > Integer.MAX_VALUE) {
						throw new GraphCreationException("Too many transitions from segment " + i + " to " + j + ": "
								+ t + " while max int is " + Integer.MAX_VALUE);
					}
					transitionSizes[i][j] = (int) t;
					globalTransitionCount += t;
					if (i == mySegment) {
						myTransitionCount += t;
						if (logger.isDebugEnabled())
							logger.debug(loggerPrefix + "Transitions from " + mySegment + " to " + j + ": " + t);
					}
					if (j == mySegment) {
						myInTransitionCount += t;
					}
				}
			}

			if (logger.isInfoEnabled())
				logger.info(loggerPrefix + "My transitions: " + myTransitionCount + ", " + "all transitions: "
						+ globalTransitionCount + ", " + "percentage: "
						+ MathUtils.proc(myTransitionCount, globalTransitionCount) + "%");

		} catch (Throwable t) {
			throw new GraphCreationException("Could not read SvcII dir " + path + ": " + t.getMessage(), t);
		} finally {
			if (header != null)
				try {
					header.close();
				} catch (Throwable t) {
					logger.warn("Could not close header " + headerPath + ": " + t.getMessage());
				}
		}
		long timeReadHeader = System.nanoTime() - startReadHeader;

		if (logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Pre-created " + myStateCount + " nodes");

		/* check graph */
		long myLocalTransitionCountLong = transitionSizes[mySegment][mySegment];
		long myRemoteTransitionCountLong = myTransitionCount - myLocalTransitionCountLong;
		long myLocalInTransitionCountLong = transitionSizes[mySegment][mySegment];
		long myRemoteInTransitionCountLong = myInTransitionCount - myLocalInTransitionCountLong;
		if (myLocalInTransitionCountLong > Integer.MAX_VALUE)
			throw new GraphCreationException("Too many local in-transitions: " + myLocalInTransitionCountLong + " in "
					+ rank);
		if (myLocalTransitionCountLong > Integer.MAX_VALUE)
			throw new GraphCreationException("Too many local transitions: " + myLocalTransitionCountLong + " in "
					+ rank);
		if (myRemoteInTransitionCountLong > Integer.MAX_VALUE)
			throw new GraphCreationException("Too many remote in-transitions: " + myRemoteInTransitionCountLong
					+ " in " + rank);
		int myLocalTransitionCount = (int) myLocalTransitionCountLong;
		int myRemoteTransitionCount = (int) myRemoteTransitionCountLong;
		int myLocalInTransitionCount = (transpose ? (int) myLocalInTransitionCountLong : 0);
		int myRemoteInTransitionCount = (transpose ? (int) myRemoteInTransitionCountLong : 0);

		logger.debug(loggerPrefix + "Creating graph with local node " + TLocalNodeClass.getSimpleName()
				+ " and node interface " + TNodeClass + " with " + myStateCount + " local states out of "
				+ globalStateCount + " with root " + rootOffset + "@" + rootSegment + " and " + myLocalTransitionCount
				+ " local out transitions, " + myRemoteTransitionCount + " remote out transitions, "
				+ myLocalInTransitionCount + " local in transitions and " + myRemoteInTransitionCount
				+ " remote in transitions");

		/* create graph */
		final long root = ExplicitNodeReference.createReference((int) rootOffset, (int) rootSegment);
		ExplicitGraph<TNode> g = new ExplicitGraph<TNode>(myStateCount, globalStateCount, false,
				myLocalTransitionCount, myRemoteTransitionCount, transpose, false, myLocalInTransitionCount,
				myRemoteInTransitionCount);
		g.setRoot(root);

		/* create nodes */
		Constructor<TNode> constructor = findConstructor(TLocalNodeClass);
		final long startCreateNodes = System.nanoTime();
		createNodes(g, myStateCount, constructor, loggerPrefix);
		final long timeCreateNodes = System.nanoTime() - startCreateNodes;

		/* allocate and create transitions without keeping order and sort them
		 * later: faster but needs more memory */
		long timeAllocTrans = 0L;
		long timeCreateTrans = 0L;
		long startCreateTrans = System.nanoTime();
		allocateAndCreateTransitions(g, mySegment, segmentCount, stateCount, myLocalTransitionCount,
				myRemoteTransitionCount, transitionSizes, path, true, loggerPrefix);
		timeCreateTrans = System.nanoTime() - startCreateTrans;

		/* debug */
		if (logger.isInfoEnabled()) {
			logger.info(loggerPrefix + "Read directed graph " + path + ". Header read in "
					+ ConversionUtils.ns2sec(timeReadHeader) + "s, nodes created in "
					+ ConversionUtils.ns2sec(timeCreateNodes) + "s, transitions allocated in "
					+ ConversionUtils.ns2sec(timeAllocTrans) + "s, transitions created in "
					+ ConversionUtils.ns2sec(timeCreateTrans) + "s");
		}

		/* read transpose */
		if (transpose) {
			readTranspose(g, TLocalNodeClass, TNodeClass, path, rank, mySegment, segmentCount, myStateCount,
					stateCount, myLocalInTransitionCount, myRemoteInTransitionCount, transitionSizes, saveMemory,
					loggerPrefix);
		}
		logger.debug("Reading done");
		return g;
	}

	@SuppressWarnings("unchecked")
	private static <TNode extends Node> void createNodes(ExplicitGraph<TNode> g, int myStateCount,
			Constructor<TNode> constructor, String loggerPrefix) throws GraphCreationException {
		ConsoleProgress np = new ConsoleProgress(logger, loggerPrefix + "Creating nodes", myStateCount);
		for (int i = 0; i < myStateCount; i++) {
			try {
				g.addNode((ExplicitLocalNode<TNode>) constructor.newInstance(g, g.nextNodeId()));
			} catch (Throwable e) {
				throw new GraphCreationException("Could not create node with the constructor " + constructor + ": "
						+ e.getMessage(), e);
			}
			np.advance();
		}
		np.finish();
	}

	private static <TNode extends Node> void allocateAndCreateTransitions(ExplicitGraph<TNode> g, int mySegment,
			int segmentCount, int[] stateCount, int myLocalTransitionCount, int myRemoteTransitionsCount,
			int[][] transitionSizes, String path, boolean outgoing, String loggerPrefix) throws GraphCreationException {

		ConsoleProgress tpcreate = new ConsoleProgress(logger, loggerPrefix + "Creating "
				+ (outgoing ? "outgoing" : "incoming") + " transitions", myLocalTransitionCount
				+ myRemoteTransitionsCount);

		for (int segment = 0; segment < segmentCount; segment++) {
			int srcSegment = (outgoing ? mySegment : segment);
			int dstSegment = (outgoing ? segment : mySegment);
			String srcPath = path + File.separator + "src-" + srcSegment + "-" + dstSegment;
			String dstPath = path + File.separator + "dest-" + srcSegment + "-" + dstSegment;
			BufferedMultiFileReader reader = null;
			try {
				reader = new BufferedMultiFileReader(srcPath, dstPath);
				int[] buf = reader.getBuf();
				for (long t = 0; t < transitionSizes[srcSegment][dstSegment]; t++) {
					reader.readToBuf();
					int sourceId = (outgoing ? buf[0] : buf[1]);
					int targetId = (outgoing ? buf[1] : buf[0]);
					ExplicitLocalNode<TNode> sourceNode = g.node(sourceId);
					if (outgoing) {
						sourceNode.addTransition(dstSegment, targetId);
					} else {
						sourceNode.addInTransition(segment, targetId);
					}
					tpcreate.advance();
				}
			} catch (Throwable t) {
				throw new GraphCreationException("Could not read transitions from " + srcPath + " or " + dstPath + ": "
						+ t.getMessage(), t);
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (Throwable t) {
						logger.warn("Could not close src file " + srcPath + " or " + dstPath + ": " + t.getMessage());
					}
				}
			}
		}
		tpcreate.finish();

		if (outgoing) {
			g.getTransitions().finish();
		} else {
			g.getInTransitions().finish();
		}
	}

	private static <TNode extends Node> void readTranspose(final ExplicitGraph<TNode> g,
			final Class<?> TLocalNodeClass, final Class<TNode> TNodeClass, final String path, final int rank,
			final int mySegment, final int segmentCount, final int myStateCount, int[] stateCount,
			final int myLocalInTransitionCount, final int myRemoteInTransitionCount, final int[][] transitionSizes,
			boolean saveMemory, String loggerPrefix) throws GraphCreationException {

		if (rank == 0)
			logger.info(loggerPrefix + "Reading transpose of SVC-II dir " + path + ", segment " + mySegment);

		long timeAllocTrans = 0L;
		long timeCreateTrans = 0L;

		/* allocate and create incoming transitions without keeping order then
		 * sort later faster but needs more memory */
		long startCreateTrans = System.nanoTime();

		allocateAndCreateTransitions(g, mySegment, segmentCount, stateCount, myLocalInTransitionCount,
				myRemoteInTransitionCount, transitionSizes, path, false, loggerPrefix);

		timeCreateTrans = System.nanoTime() - startCreateTrans;

		/* debug */
		if (logger.isInfoEnabled())
			logger.info(loggerPrefix + "Finished reading transpose of " + path + ". Transitions allocated in "
					+ ConversionUtils.ns2sec(timeAllocTrans) + ", created in "
					+ ConversionUtils.ns2sec(timeCreateTrans));
	}

	@SuppressWarnings("unchecked")
	private static <TNode extends Node> Constructor<TNode> findConstructor(Class<?> TLocalNodeClass)
			throws GraphCreationException {
		Constructor<?> constructor = ReflectionUtils.findConstructor(TLocalNodeClass, Graph.class, int.class);
		if (constructor == null)
			throw new GraphCreationException("Could not find constructor for the node class "
					+ TLocalNodeClass.getName());
		Constructor<TNode> nodeConstructor = null;
		try {
			nodeConstructor = (Constructor<TNode>) constructor;
		} catch (Throwable t) {
			throw new GraphCreationException("Found constructor for the class " + TLocalNodeClass.getName()
					+ " is not a node constructor");
		}
		return nodeConstructor;
	}

}
