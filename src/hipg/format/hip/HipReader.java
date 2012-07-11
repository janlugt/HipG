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

import hipg.Config;

import hipg.Graph;
import hipg.LocalNode;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.format.hip.HipSegment.TransitionHandler;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.utils.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;

import myutils.ConsoleProgress;
import myutils.ConversionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HipReader {

	/** Logging facilities. */
	private static final Logger logger = LoggerFactory.getLogger(HipReader.class);

	/** Reads a directed graph in Hip format. */
	public static <TNode extends Node> ExplicitGraph<TNode> read(
			@SuppressWarnings("rawtypes") final Class<? extends LocalNode> TLocalNodeClass,
			final Class<TNode> TNodeClass, final String path, final int rank, final int poolSize,
			final boolean transpose) throws GraphCreationException {

		final String loggerPrefix = "(Ibis " + rank + ") ";
		if (rank == 0)
			logger.info(loggerPrefix + "Reading hip dir " + path);

		if (!new File(path).exists()) {
			throw new RuntimeException(path + " does not exist");
		}

		/* read header */
		final HipHeader header = new HipHeader(HipHeader.headerPath(path));

		/* determine which segments to obtain */
		final ArrayList<HipSegment> segments = header.getSegments();
		final HipSegmentManager manager = new HipSegmentManager(segments, poolSize);
		final ArrayList<HipSegment> mySegments = manager.mySegments(Runtime.getRank());

		if (rank == 0 && logger.isDebugEnabled())
			logger.debug(loggerPrefix + header);

		logger.info(loggerPrefix + "Getting segments " + mySegments.get(0).getId() + " .. "
				+ mySegments.get(mySegments.size() - 1).getId());

		/* determine part of the graph to obtain */
		long globalStates = header.getGlobalStateCount();
		long myStatesLong = 0;
		long myLocalOutTransitionCountLong = 0;
		long myRemoteOutTransitionCountLong = 0;
		long myLocalInTransitionCountLong = 0;
		long myRemoteInTransitionCountLong = 0;

		for (HipSegment segment : mySegments) {
			myStatesLong += segment.getStates();
			int[] outTransitions = segment.getOutTransitions();
			for (HipSegment dsegment : segments) {
				int id = dsegment.getId();
				if (manager.owner(dsegment) == rank)
					myLocalOutTransitionCountLong += outTransitions[id];
				else
					myRemoteOutTransitionCountLong += outTransitions[id];
			}
			int[] inTransitions = segment.getInTransitions();
			for (HipSegment ssegment : segments) {
				int id = ssegment.getId();
				if (manager.owner(ssegment) == rank) {
					myLocalInTransitionCountLong += inTransitions[id];
				} else {
					myRemoteInTransitionCountLong += inTransitions[id];
				}
			}
		}
		if (myStatesLong > Integer.MAX_VALUE)
			throw new GraphCreationException("Cannot handle more than " + Integer.MAX_VALUE + "states per node");
		final int myStates = (int) myStatesLong;

		if (myLocalOutTransitionCountLong > Integer.MAX_VALUE || myRemoteOutTransitionCountLong > Integer.MAX_VALUE
				|| myLocalInTransitionCountLong > Integer.MAX_VALUE
				|| myRemoteInTransitionCountLong > Integer.MAX_VALUE) {
			throw new GraphCreationException("Cannot handle more than " + Integer.MAX_VALUE + " transitions");
		}

		int myLocalOutTransitionCount = (int) myLocalOutTransitionCountLong;
		int myRemoteOutTransitionCount = (int) myRemoteOutTransitionCountLong;
		int myLocalInTransitionCount = (transpose ? (int) myLocalInTransitionCountLong : 0);
		int myRemoteInTransitionCount = (transpose ? (int) myRemoteInTransitionCountLong : 0);

		/* create graph */
		logger.debug(loggerPrefix + "Creating graph with " + myStates + " local states out of " + globalStates
				+ " global states and " + myLocalOutTransitionCount + " local outgoing transitions, "
				+ myRemoteOutTransitionCount + " remote outgoing transitions (altogether "
				+ (myLocalOutTransitionCount + myRemoteOutTransitionCount) + " outgoing transitions) , "
				+ myLocalInTransitionCount + " local incoming transitions, and " + myRemoteInTransitionCount
				+ " remote incoming transitions (altogether " + (myLocalInTransitionCount + myRemoteInTransitionCount)
				+ " incoming transitions)");
		/* set root */
		int rootOwner = manager.owner(header.getRootSegment());
		int rootOffset = manager.offset(header.getRootOffset(), header.getRootSegment());
		long root = ExplicitNodeReference.createReference(rootOffset, rootOwner);

		ExplicitGraph<TNode> g = new ExplicitGraph<TNode>(myStates, globalStates, false, myLocalOutTransitionCount,
				myRemoteOutTransitionCount, transpose, false, myLocalInTransitionCount, myRemoteInTransitionCount);

		g.setRoot(root);

		/* create nodes */
		Constructor<TNode> constructor = findConstructor(TLocalNodeClass);
		long startCreateNodes = System.nanoTime();
		createNodes(g, myStates, constructor, loggerPrefix);
		long timeCreateNodes = System.nanoTime() - startCreateNodes;
		logger.debug(loggerPrefix + "Created nodes in " + ConversionUtils.ns2sec(timeCreateNodes) + "s");

		/* read transitions */
		MyTransitionHandler<TNode> outHandler = new MyTransitionHandler<TNode>(g, manager, false,
				myLocalOutTransitionCount, myRemoteOutTransitionCount);
		final long startRead = System.nanoTime();
		for (HipSegment segment : mySegments) {
			segment.read(outHandler, path, false);
		}
		final long timeRead = System.nanoTime() - startRead;
		logger.debug(loggerPrefix + "Read segments in " + ConversionUtils.ns2sec(timeRead) + "s");
		long startFinish = System.nanoTime();
		g.getTransitions().finish();
		long timefinish = System.nanoTime() - startFinish;
		logger.debug(loggerPrefix + "Finished in " + ConversionUtils.ns2sec(timefinish) + "s");

		/* read transposed transitions */
		if (transpose) {
			MyTransitionHandler<TNode> inHandler = new MyTransitionHandler<TNode>(g, manager, true,
					myLocalInTransitionCount, myRemoteInTransitionCount);
			g.initTranspose(false, myLocalInTransitionCount, myRemoteInTransitionCount);
			final long startReadTranspose = System.nanoTime();
			for (HipSegment segment : mySegments) {
				segment.read(inHandler, path, true);
			}
			final long timeReadTranspose = System.nanoTime() - startReadTranspose;
			logger.debug(loggerPrefix + "Read transposed segments in " + ConversionUtils.ns2sec(timeReadTranspose)
					+ "s");
			final long startFinishTranspose = System.nanoTime();
			g.getInTransitions().finish();
			final long timeFinishTranspose = System.nanoTime() - startFinishTranspose;
			logger.debug(loggerPrefix + "Finished transposed chunk in " + ConversionUtils.ns2sec(timeFinishTranspose)
					+ "s");
		}
		logger.debug(loggerPrefix + "Reading done");
		return g;
	}

	private static final class MyTransitionHandler<TNode extends Node> implements TransitionHandler {

		private final ExplicitGraph<TNode> g;
		private final HipSegmentManager manager;
		private final boolean transpose;

		public MyTransitionHandler(final ExplicitGraph<TNode> g, final HipSegmentManager manager, boolean transpose,
				final int myLocalTransitionCount, final int myRemoteTransitionsCount) {
			this.g = g;
			this.manager = manager;
			this.transpose = transpose;
		}

		@Override
		public void handle(final int locOwner, final int locOffset, final int conOwner, final int conOffset)
				throws GraphCreationException {

			if (Config.ERRCHECK) {
				final int absLocOwner = manager.owner(locOwner);
				if (absLocOwner != Runtime.getRank()) {
					throw new RuntimeException("Error when adding a node: ranks do not match. That's a bug.");
				}
			}

			final int absLocOffset = manager.offset(locOffset, locOwner);
			final int absConOwner = manager.owner(conOwner);
			final int absConOffset = manager.offset(conOffset, conOwner);
			// final boolean local = (absLocOwner == absConOwner);
			final ExplicitLocalNode<TNode> node;

			try {
				node = g.node(absLocOffset);
			} catch (Throwable t) {
				throw new GraphCreationException("Node " + locOffset + " not found in segment " + locOwner);
			}

			if (transpose) {
				// node.allocateInTransition(local);
				node.addInTransition(absConOwner, absConOffset);
			} else {
				// node.allocateTransition(local);
				node.addTransition(absConOwner, absConOffset);
			}
			// final ExplicitJoinedTransitions<TNode> transitions = (transpose ?
			// g.getInTransitions() : g.getTransitions());
			// if (local) {
			// transitions.addLocalTransition(absLocOffset,
			// g.node(absConOffset));
			// } else {
			// transitions.addRemoteTransition(absLocOffset, absConOwner,
			// absConOffset);
			// }
		}
	}

	@SuppressWarnings("unchecked")
	private static <TNode extends Node> void createNodes(ExplicitGraph<TNode> g, int myStateCount,
			Constructor<TNode> constructor, String loggerPrefix) throws GraphCreationException {
		ConsoleProgress progress = new ConsoleProgress(logger, loggerPrefix + "Creating nodes", myStateCount);
		for (int i = 0; i < myStateCount; i++) {
			try {
				g.addNode((ExplicitLocalNode<TNode>) constructor.newInstance(g, g.nextNodeId()));
			} catch (Throwable e) {
				throw new GraphCreationException("Could not create node " + "with the constructor " + constructor
						+ ": " + e.getMessage(), e);
			}
			progress.advance();
		}
		progress.finish();
	}

	@SuppressWarnings("unchecked")
	private static <TNode extends Node> Constructor<TNode> findConstructor(Class<?> TLocalNodeClass)
			throws GraphCreationException {
		Constructor<?> constructor = ReflectionUtils.findConstructor(TLocalNodeClass, Graph.class, int.class);
		if (constructor == null)
			throw new GraphCreationException("Could not find constructor " + "for the node class "
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
