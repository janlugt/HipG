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

import hipg.Graph;
import hipg.LocalNode;
import hipg.Node;
import hipg.runtime.Runtime;

import java.util.Iterator;

/**
 * Graph chunk: array of nodes.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 * @param <TNode>
 */
public final class ExplicitGraph<TNode extends Node> extends Graph<TNode> {

	/** The number of global nodes in the graph. */
	private long numGlobalNodes = -1;

	/** List of nodes in this graph. */
	private ExplicitLocalNode<TNode>[] nodes;
	private int numNodes;

	/** Outgoing transitions. */
	private ExplicitJoinedTransitions<TNode> outgoing;

	/** Incoming transitions. */
	private ExplicitJoinedTransitions<TNode> incoming;

	/** Graph's root node (might be unavailable) */
	private long root = ExplicitNodeReference.NULL_NODE;

	@SuppressWarnings("unchecked")
	public ExplicitGraph(final int localNodes, final long globalNodes, final boolean orderedTransitions,
			final long estimateLocalTransitions, final long estimateRemoteTransitions, final boolean hasTranspose,
			final boolean orderedInTransitions, final long estimateLocalInTransitions,
			final long estimateRemoteInTransitions) {
		this.nodes = new ExplicitLocalNode[localNodes];
		this.numNodes = 0;
		this.numGlobalNodes = globalNodes;
		this.outgoing = new ExplicitJoinedTransitions<TNode>(this, false, orderedTransitions, estimateLocalTransitions,
				estimateRemoteTransitions);
		this.incoming = (hasTranspose ? new ExplicitJoinedTransitions<TNode>(this, true, orderedInTransitions,
				estimateLocalInTransitions, estimateRemoteInTransitions) : null);
	}

	public final void initTranspose(final boolean orderedAdding, final long estimateLocalInTransitions,
			final long estimateRemoteInTransitions) {
		if (incoming != null) {
			throw new RuntimeException("Transpose already initialized");
		}
		incoming = new ExplicitJoinedTransitions<TNode>(this, true, orderedAdding, estimateLocalInTransitions,
				estimateRemoteInTransitions);
	}

	public final long root() {
		return root;
	}

	public final void setGlobalNodes(final long globalNodes) {
		this.numGlobalNodes = globalNodes;
	}

	public final void setRoot(final long root) {
		this.root = root;
	}

	public final TNode globalNode(final long node) {
		throw new UnsupportedOperationException("LocalNode: TNode node(reference) should never be executed");
	}

	public final boolean hasNode(long reference) {
		return hasNode(ExplicitNodeReference.getId(reference));
	}

	public final int nodes() {
		return numNodes;
	}

	public final long getGlobalSize() {
		return numGlobalNodes;
	}

	public final ExplicitLocalNode<TNode> node(int index) {
		return nodes[index];
	}

	public final ExplicitLocalNode<TNode> node(long globalReference) {
		int own = ExplicitNodeReference.getOwner(globalReference);
		if (own == Runtime.getRank()) {
			return node(ExplicitNodeReference.getId(globalReference));
		}
		return null;
	}

	public final boolean hasNode(int reference) {
		return reference >= 0 && reference <= nodes.length;
	}

	private final static int increaseLength(final int len, final boolean parity) {
		if (len < 1024) {
			return 1024;
		}
		int newLen = len * 5 / 4;
		if (newLen < 0) {
			newLen = Integer.MAX_VALUE;
		}
		if (len == newLen) {
			throw new RuntimeException("Cannot increase length from " + len);
		}
		if (parity && (newLen & 1) > 0) {
			newLen++;
		}
		return newLen;
	}

	private final void ensureLocalNodesSpace() {
		final int newLength = increaseLength(nodes.length, false);
		final ExplicitLocalNode<TNode>[] newNodes = createLocalNodesArray(newLength);
		if (newNodes == null) {
			throw new RuntimeException("Not enough space for " + newLength + " transitions");
		}
		System.arraycopy(nodes, 0, newNodes, 0, nodes.length);
		nodes = newNodes;
	}

	public final int nextNodeId() {
		return numNodes;
	}

	public final void addNode(ExplicitLocalNode<TNode> node) {
		if (numNodes >= nodes.length) {
			ensureLocalNodesSpace();
		}
		nodes[numNodes++] = node;
	}

	public final ExplicitLocalNode<TNode> getLocalNeighbor(long start, int index) {
		return outgoing.getLocalTransition(start, index);
	}

	public final int getRemoteNeighborId(long start, int index) {
		return outgoing.getRemoteTransitionId(start, index);
	}

	public final int getRemoteNeighborOwner(long start, int index) {
		return outgoing.getRemoteTransitionOwner(start, index);
	}

	public final ExplicitLocalNode<TNode> getLocalInNeighbor(long start, int index) {
		return incoming.getLocalTransition(start, index);
	}

	public final int getRemoteInNeighborId(long start, int index) {
		return incoming.getRemoteTransitionId(start, index);
	}

	public final int getRemoteInNeighborOwner(long start, int index) {
		return incoming.getRemoteTransitionOwner(start, index);
	}

	public final ExplicitJoinedTransitions<TNode> getTransitions() {
		return outgoing;
	}

	public final ExplicitJoinedTransitions<TNode> getInTransitions() {
		return incoming;
	}

	public final void finishCreation() {
		if (outgoing != null)
			outgoing.finish();
		if (incoming != null)
			incoming.finish();
	}

	public final Iterator<LocalNode<TNode>> iterator() {
		return new ExplicitGraphIterator<TNode>(this);
	}

	public static final class ExplicitGraphIterator<TNode extends Node> implements Iterator<LocalNode<TNode>> {

		private final ExplicitGraph<TNode> graph;
		private int index = 0;

		public ExplicitGraphIterator(ExplicitGraph<TNode> graph) {
			this.graph = graph;
		}

		@Override
		public boolean hasNext() {
			return index < graph.nodes();
		}

		@Override
		public ExplicitLocalNode<TNode> next() {
			return graph.node(index++);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	@SuppressWarnings("unchecked")
	private final ExplicitLocalNode<TNode>[] createLocalNodesArray(final int size) {
		return new ExplicitLocalNode[size];
	}

	public String toString() {
		return toString(0, true, true);
	}

	public String toString(int max, boolean out, boolean in) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nodes() && (max == 0 || i < max); i++) {
			sb.append(i);
			sb.append(": ");
			sb.append(node(i));
			sb.append("\n");
		}
		return sb.toString();
	}
}
