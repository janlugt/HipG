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

package hipg.graph;

import hipg.Config;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.format.synthetic.AbstractSyntheticGraphMaker;
import myutils.Quicksort;
import myutils.storage.bigarray.BigArray;
import myutils.storage.bigarray.BigIntArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graph transitions.
 * 
 * @author Ela Krepska e.krepska@vu.nl
 * 
 * @param <TNode>
 */
public final class ExplicitJoinedTransitions<TNode extends Node> {
	/** Logging utilities. */
	private static final Logger logger = LoggerFactory.getLogger(AbstractSyntheticGraphMaker.class);
	private String loggerPrefix;

	/** Graph */
	private ExplicitGraph<TNode> graph;

	/** Am I a transpose. */
	private final boolean isTranspose;

	/** Configuration of the joint transitions. */
	private boolean ordered;

	/** Transitions outgoing to local nodes. */
	private BigArray<ExplicitLocalNode<TNode>> localTransitions;
	private BigIntArray localTransitionsSrc;
	private long numLocalTransitions;

	/** Transitions outgoing to remote nodes */
	private BigIntArray remoteTransitions;
	private BigIntArray remoteTransitionsDst;
	private long numRemoteTransitions;

	/** For ordered constructions. */
	ExplicitLocalNode<TNode> lastNodeWithAddedTransition = null;

	/** Status of the construction. */
	private boolean inCreation = true;

	ExplicitJoinedTransitions(ExplicitGraph<TNode> graph, final boolean isTranspose, final boolean ordered,
			final long estimateNumLocalTransitions, final long estimateNumRemoteTransitions) {
		this.graph = graph;
		this.isTranspose = isTranspose;
		init(ordered, estimateNumLocalTransitions, estimateNumRemoteTransitions);
	}

	/** Initializes arrays of transitions. */
	private void init(final boolean ordered, long estimateLocalTransitions, long estimateRemoteTransitions) {
		this.ordered = ordered;
		this.loggerPrefix = "";

		if (estimateLocalTransitions < 0 || estimateRemoteTransitions < 0) {
			throw new RuntimeException("Estimate of the number of transitions cannot be negative");
		}

		this.numLocalTransitions = 0;
		this.numRemoteTransitions = 0;

		this.localTransitionsSrc = ordered ? null : new BigIntArray(10240, 1).ensureCapacity(estimateLocalTransitions);
		this.localTransitions = createLocalNodeArray(estimateLocalTransitions);
		this.remoteTransitions = new BigIntArray(10240, 1).ensureCapacity(estimateRemoteTransitions * 2);
		this.remoteTransitionsDst = ordered ? null : new BigIntArray(10240, 1)
				.ensureCapacity(estimateRemoteTransitions);
	}

	public long getNumTransitions() {
		return (long) numLocalTransitions + (long) numRemoteTransitions;
	}

	public long getNumLocalTransitions() {
		return numLocalTransitions;
	}

	public long getNumRemoteTransitions() {
		return numRemoteTransitions;
	}

	BigArray<ExplicitLocalNode<TNode>> getLocalTransitions() {
		return localTransitions;
	}

	BigIntArray getRemoteTransitions() {
		return remoteTransitions;
	}

	ExplicitLocalNode<TNode> getLocalTransition(final long start, final int index) {
		if (Config.ERRCHECK && inCreation) {
			throw new RuntimeException("Cannot access graph: still in creation");
		}
		return localTransitions.get(start + index);
	}

	int getRemoteTransitionOwner(final long start, final int index) {
		if (Config.ERRCHECK && inCreation) {
			throw new RuntimeException("Cannot access graph: still in creation");
		}
		return remoteTransitions.get(start + (index << 1));
	}

	int getRemoteTransitionId(final long start, final int index) {
		if (Config.ERRCHECK && inCreation) {
			throw new RuntimeException("Cannot access graph: still in creation");
		}
		return remoteTransitions.get(start + (index << 1) + 1L);
	}

	void addLocalTransition(final ExplicitLocalNode<TNode> source, final ExplicitLocalNode<TNode> target)
			throws GraphCreationException {
		if (Config.ERRCHECK && !inCreation) {
			throw new RuntimeException("Cannot add transition: Graph not in creation");
		}
		if (ordered) {
			if (source != lastNodeWithAddedTransition) {
				if (isTranspose) {
					if (source.localInNeighborsStart >= 0 || source.remoteInNeighborsStart >= 0) {
						throw new GraphCreationException("In-Transitions not ordered as expected!");
					}
					source.setLocalInNeighborStart(numLocalTransitions);
					source.setRemoteInNeighborStart(numRemoteTransitions);
				} else {
					if (source.localNeighborsStart >= 0 || source.remoteNeighborsStart >= 0) {
						throw new GraphCreationException("Out-Transitions not ordered as expected");
					}
					source.setLocalNeighborStart(numLocalTransitions);
					source.setRemoteNeighborStart(numRemoteTransitions);
				}
				lastNodeWithAddedTransition = source;
			}
		}
		numLocalTransitions++;
		localTransitions.addBack(target);
		if (!ordered) {
			localTransitionsSrc.addBack(source.reference());
		}
	}

	void addRemoteTransition(final ExplicitLocalNode<TNode> source, final int remoteOwner, final int remoteId)
			throws GraphCreationException {
		if (Config.ERRCHECK && !inCreation) {
			throw new RuntimeException("Cannot add transition: Graph not in creation");
		}
		if (ordered) {
			if (source != lastNodeWithAddedTransition) {
				if (isTranspose) {
					if (source.localInNeighborsStart >= 0 || source.remoteInNeighborsStart >= 0) {
						throw new GraphCreationException("In-Transitions not ordered!");
					}
					source.setLocalInNeighborStart(numLocalTransitions);
					source.setRemoteInNeighborStart(numRemoteTransitions);
				} else {
					if (source.localNeighborsStart >= 0 || source.remoteNeighborsStart >= 0) {
						throw new GraphCreationException("Out-Transitions not ordered");
					}
					source.setLocalNeighborStart(numLocalTransitions);
					source.setRemoteNeighborStart(numRemoteTransitions);
				}
				lastNodeWithAddedTransition = source;
			}
		}
		numRemoteTransitions++;
		remoteTransitions.addBack(remoteOwner);
		remoteTransitions.addBack(remoteId);
		if (!ordered) {
			remoteTransitionsDst.addBack(source.reference);
		}
	}

	public void finish() {
		if (!inCreation) {
			throw new RuntimeException("Cannot finish: Graph not in creation");
		}
		inCreation = false;
		if (!ordered) {
			if (isTranspose) {
				prepareTranspose();
			} else {
				prepare();
			}
			sortAndRelease();
			ordered = true;
		}
		lastNodeWithAddedTransition = null;
		if (Config.ERRCHECK) {
			for (ExplicitLocalNode<TNode> node : localTransitions) {
				if (node == null) {
					throw new NullPointerException("Null node!");
				}
			}
		}
	}

	private void prepare() {
		long hl = 0, hr = 0;
		for (int i = 0; i < graph.nodes(); i++) {
			final ExplicitLocalNode<?> n = graph.node(i);
			hl = n.setLocalNeighborStart(hl);
			hr = n.setRemoteNeighborStart(hr);
		}
	}

	private void prepareTranspose() {
		long hl = 0, hr = 0;
		for (int i = 0; i < graph.nodes(); i++) {
			final ExplicitLocalNode<?> n = graph.node(i);
			hl = n.setLocalInNeighborStart(hl);
			hr = n.setRemoteInNeighborStart(hr);
		}
	}

	private void sortAndRelease() {
		logger.debug(loggerPrefix + "Sorting local transitions");
		Quicksort.quicksortWithFollowerObjectArray(localTransitionsSrc, localTransitions, numLocalTransitions);
		localTransitionsSrc = null;
		logger.debug(loggerPrefix + "Sorting remote transitions");
		Quicksort.quicksortWithFollowerTwoArray(remoteTransitionsDst, remoteTransitions, numRemoteTransitions);
		remoteTransitionsDst = null;
		logger.debug(loggerPrefix + "Sorting done");
	}

	private BigArray<ExplicitLocalNode<TNode>> createLocalNodeArray(final long size) {
		return new BigArray<ExplicitLocalNode<TNode>>(10240, 1).ensureCapacity(size);
	}
}
