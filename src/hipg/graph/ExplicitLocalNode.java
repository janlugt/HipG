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

import hipg.LocalNode;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.runtime.Runtime;

public class ExplicitLocalNode<TNode extends Node> extends LocalNode<TNode> {
	protected ExplicitGraph<TNode> graph;
	protected final int reference;

	/** Number of local neighbors. */
	int localNeighborsCount;
	/** Number of remote neighbors. */
	int remoteNeighborsCount;
	/** Number of local in-neighbors. */
	int localInNeighborsCount;
	/** Number of remote in-neighbors. */
	int remoteInNeighborsCount;

	/** Location of the first local neighbor. */
	long localNeighborsStart = -1;
	/** Location of the first remote neighbor. */
	long remoteNeighborsStart = -1;
	/** Location of the first local in-neighbor. */
	long localInNeighborsStart = -1;
	/** Location of the first remote in-neighbor. */
	long remoteInNeighborsStart = -1;

	public ExplicitLocalNode(ExplicitGraph<TNode> graph, int reference) {
		super();
		this.reference = reference;
		this.graph = graph;
	}

	public int reference() {
		return reference;
	}

	public long asReference() {
		return ExplicitNodeReference.createReference(this);
	}

	public String name(long ref) {
		return ExplicitNodeReference.referenceToString(ref);
	}

	public final String name() {
		return reference + "@" + Runtime.getRank();
	}

	public final int outdegree() {
		return localNeighborsCount + remoteNeighborsCount;
	}

	public final int localOutdegree() {
		return localNeighborsCount;
	}

	public final int remoteOutdegree() {
		return remoteNeighborsCount;
	}

	public final int indegree() {
		return localInNeighborsCount + remoteInNeighborsCount;
	}

	public final int localIndegree() {
		return localInNeighborsCount;
	}

	public final int remoteIndegree() {
		return remoteInNeighborsCount;
	}

	public final short graphId() {
		return graph.getId();
	}

	public final int neighborId(int index) {
		if (index < localNeighborsCount) {
			return graph.getLocalNeighbor(localNeighborsStart, index).reference();
		} else {
			return graph.getRemoteNeighborId(remoteNeighborsStart, index - localNeighborsCount);
		}
	}

	@Override
	public final int neighborOwner(int index) {
		if (index < localNeighborsCount) {
			return Runtime.getRank();
		} else {
			return graph.getRemoteNeighborOwner(remoteNeighborsStart, index - localNeighborsCount);
		}
	}

	public final long neighborReference(int index) {
		if (index < localNeighborsCount) {
			return graph.getLocalNeighbor(localNeighborsStart, index).asReference();
		} else {
			final int index2 = index - localNeighborsCount;
			final int id = graph.getRemoteNeighborId(remoteNeighborsStart, index2);
			final int owner = graph.getRemoteNeighborOwner(remoteNeighborsStart, index2);
			return ExplicitNodeReference.createReference(id, owner);
		}
	}

	@Override
	public final boolean hasNeighbor(int index) {
		return index < localNeighborsCount + remoteNeighborsCount;
	}

	public final boolean hasInNeighbor(int index) {
		return index < localInNeighborsCount + remoteInNeighborsCount;
	}

	@Override
	public final boolean isNeighborLocal(int index) {
		return index < localNeighborsCount;
	}

	public final boolean isInNeighborLocal(int index) {
		return index < localInNeighborsCount;
	}

	public final ExplicitLocalNode<TNode> localNeighbor(int index) {
		return graph.getLocalNeighbor(localNeighborsStart, index);
	}

	public final ExplicitLocalNode<TNode> localInNeighbor(int index) {
		return graph.getLocalInNeighbor(localInNeighborsStart, index);
	}

	public final int inNeighborOwner(int index) {
		if (index < localInNeighborsCount) {
			return Runtime.getRank();
		} else {
			return graph.getRemoteInNeighborOwner(remoteInNeighborsStart, index - localInNeighborsCount);
		}
	}

	public final int inNeighborId(int index) {
		if (index < localInNeighborsCount) {
			return graph.getLocalInNeighbor(localInNeighborsStart, index).reference();
		} else {
			return graph.getRemoteInNeighborId(remoteInNeighborsStart, index - localInNeighborsCount);
		}
	}

	public final long inNeighborReference(int index) {
		if (index < localInNeighborsCount) {
			return graph.getLocalInNeighbor(localInNeighborsStart, index).reference();
		} else {
			final int index2 = index - localInNeighborsCount;
			final int id = graph.getRemoteInNeighborId(remoteInNeighborsStart, index2);
			final int owner = graph.getRemoteInNeighborOwner(remoteInNeighborsStart, index2);
			return ExplicitNodeReference.createReference(id, owner);
		}
	}

	public final TNode neighbor(int index) {
		throw new UnsupportedOperationException("ExplicitLocalNode: TNode neighbor(index) should never be executed");
	}

	public final TNode inNeighbor(int index) {
		throw new UnsupportedOperationException("LocalNode: TNode inNeighbor(index) should never be executed");
	}

	// /
	// / graph creation
	// /

	public final long setLocalNeighborStart(final long localStart) {
		localNeighborsStart = localStart;
		return localStart + localNeighborsCount;
	}

	public final long setRemoteNeighborStart(final long remoteStart) {
		remoteNeighborsStart = 2 * remoteStart;
		return remoteStart + remoteNeighborsCount;
	}

	public final long setLocalInNeighborStart(final long localCount) {
		localInNeighborsStart = localCount;
		return localCount + localInNeighborsCount;
	}

	public final long setRemoteInNeighborStart(final long remoteCount) {
		remoteInNeighborsStart = 2 * remoteCount;
		return remoteCount + remoteInNeighborsCount;
	}

	public final void addTransition(final int owner, final int id) throws GraphCreationException {
		final boolean local = owner == hipg.runtime.Runtime.getRank();
		if (local) {
			if (localNeighborsCount == Integer.MAX_VALUE)
				throw new GraphCreationException("Cannot handle more than " + Integer.MAX_VALUE
						+ " local outgoing transitions per node");
			localNeighborsCount++;
			graph.getTransitions().addLocalTransition(this, graph.node(id));
		} else {
			if (remoteNeighborsCount == Integer.MAX_VALUE)
				throw new GraphCreationException("Cannot handle more than " + Integer.MAX_VALUE
						+ " remote outgoing transitions per node");
			remoteNeighborsCount++;
			graph.getTransitions().addRemoteTransition(this, owner, id);
		}
	}

	public final void addInTransition(final int sourceOwner, final int sourceId) throws GraphCreationException {
		final boolean local = sourceOwner == hipg.runtime.Runtime.getRank();
		if (local) {
			if (localInNeighborsCount == Integer.MAX_VALUE)
				throw new GraphCreationException("Cannot handle more than " + Integer.MAX_VALUE
						+ " local incoming transitions per node");
			localInNeighborsCount++;
			graph.getInTransitions().addLocalTransition(this, graph.node(sourceId));
		} else {
			if (remoteNeighborsCount == Integer.MAX_VALUE)
				throw new GraphCreationException("Cannot handle more than " + Integer.MAX_VALUE
						+ " remote incoming transitions per node");
			remoteInNeighborsCount++;
			graph.getInTransitions().addRemoteTransition(this, sourceOwner, sourceId);
		}
	}

}
