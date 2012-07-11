/**
 * Copyright (c) 2010, 2011 Vrije Universiteit Amsterdam.
 * Written by Elzbieta Krepska, e.l.krepska@vu.nl.
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

import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitLocalNode;

import java.util.Random;

import myutils.probability.DiscreteDistribution;

/**
 * Standard Erdos graph implementation. Goes through all pairs of vertices and creates an edge with given probability.
 * This implementation is easy and versatile but does not scale, see {@code FastErdos} and {@code ApproximateErdos}.
 * 
 * @author Ela Krepska, e.krepska@vu.nl
 */
public class RingWithShortcuts implements SyntheticGraph {
	private final long n;
	private final int shortcuts;
	private final Random random;

	public RingWithShortcuts(final long n, final int shortcuts, final Random rand) throws GraphCreationException {
		if (n <= 0) {
			throw new GraphCreationException("n must be positive");
		}
		if (shortcuts < 0) {
			throw new GraphCreationException("#shortcuts must be non-negative");
		}
		this.random = rand;
		this.n = n;
		this.shortcuts = shortcuts;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		final long root = maker.addNode();
		for (long i = 1; i < n; i++) {
			maker.addNode();
		}
		final int rank = maker.getRank();
		final int poolSize = maker.getPoolSize();
		final int nextRank = poolSize == 1 ? rank : (rank + 1) % poolSize;
		final int[] numNodes = new int[poolSize];
		for (int owner = 0; owner < poolSize; ++owner) {
			numNodes[owner] = maker.numNodes(owner);
		}
		final int myNumNodes = numNodes[rank];
		final DiscreteDistribution owners = new DiscreteDistribution(0, numNodes, random);

		for (int srcId = 0; srcId < myNumNodes; srcId++) {
			for (int shortcut = 0; shortcut < shortcuts; ++shortcut) {
				int dstOwner = (int) Math.round(owners.sample());
				int dstId = random.nextInt(numNodes[dstOwner]);
				maker.addTransition(rank, srcId, dstOwner, dstId);
			}
			// link to the next node in the ring
			if (srcId + 1 < myNumNodes) {
				maker.addTransition(rank, srcId, rank, srcId + 1);
			} else {
				maker.addTransition(rank, srcId, nextRank, 0);
			}
		}
		return root;
	}

	@Override
	public long estimateGlobalNodes() {
		return n;
	}

	@Override
	public long estimateGlobalTransitions() {
		return n * shortcuts;
	}

	@Override
	public boolean canSynthetizeTranspose() {
		return false;
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return true;
	}

	@Override
	public String toString() {
		return "RingWithShortcuts(n=" + n + ", shortcuts=" + shortcuts + ")";
	}
}
