/**
 * Copyright (c) 2010, 2011 Vrije Universiteit Amsterdam.
 * Written by Elzbieta Krepska, e.krepska@vu.nl.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package hipg.format.synthetic;

import hipg.Graph;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitLocalNode;

public interface AbstractSyntheticGraphMaker<TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> {

	/** Adds a node to the new graph. Returns the node's id. */
	public long addNode();

	/** Adds a transition to the new graph. */
	public void addTransition(final long src, final long dst) throws GraphCreationException;

	/** Adds a transition to the new graph. */
	public void addTransition(final int srcOwner, final int srcId, final int dstOwner, final int dstId)
			throws GraphCreationException;

	public Graph<TNode> create(SyntheticGraph sg) throws GraphCreationException;

	/**
	 * Returns the number of nodes currently added to the given owner.
	 */
	public int numNodes(int owner);

	/** Returns the number of nodes currently added locally. */
	public int numNodes();

	/** Returns the total number of nodes currently added. */
	public long totalNumNodes();

	/**
	 * Returns the rank of this process. The rank is the first half of the vertex identifier.
	 */
	public int getRank();

	/**
	 * Returns the number of processes that participate in creation of the graph. The ranks must be smaller.
	 */
	public int getPoolSize();

	public boolean hasTranspose();

	public Partition getPartition();

}