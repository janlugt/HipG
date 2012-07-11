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

/**
 * Standard Erdos graph implementation. Goes through all pairs of vertices and creates an edge with given probability.
 * This implementation is easy and versatile but does not scale, see {@code FastErdos} and {@code ApproximateErdos}.
 * 
 * @author Ela Krepska, e.krepska@vu.nl
 */
public class SlowErdos implements SyntheticGraph {
	private final long n;
	private final double p;
	private final Random random;

	public SlowErdos(final long n, final double p, final Random rand) throws GraphCreationException {
		if (n < 0) {
			throw new GraphCreationException("Erdos graph size must be non-negative");
		}
		if (p < 0 || p > 1) {
			throw new GraphCreationException("Erdos graph parameter p must represent probability");
		}
		this.random = rand;
		this.n = n;
		this.p = p;
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
		final int numNodes = maker.numNodes(rank);
		for (int srcId = 0; srcId < numNodes; srcId++) {
			for (int dstOwner = 0; dstOwner < poolSize; dstOwner++) {
				final int dstNumNodes = maker.numNodes(dstOwner);
				for (int dstId = 0; dstId < dstNumNodes; dstId++) {
					if (random.nextDouble() < p) {
						maker.addTransition(rank, srcId, dstOwner, dstId);
					}
				}
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
		return (long) Math.ceil((double) n * p * (double) n);
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
		return "Erdos(n=" + n + ", p=" + p + ")";
	}
}
