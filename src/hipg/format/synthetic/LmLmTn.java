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

/**
 * Cartesian product of graphs LatticeLoop(m) x BinTree(n). It is a logical tree such that each node of the logical tree
 * is a looped lattice of size m x m. Each node has thus two neighbors in the lattice and all nodes except the top
 * lattice have a single father node. The quotient graph is the tree.
 * 
 * @author Ela Krepska, e.krepska@vu.nl
 */
public class LmLmTn implements SyntheticGraph {
	/** Tree height. */
	private final int n;
	/** Size of the lattice in each tree node. */
	private final int m;

	public LmLmTn(final int m, final int n) throws GraphCreationException {
		if (m <= 0) {
			throw new GraphCreationException("Parameter m of LmLmTn must be positive");
		}
		if (n < 0) {
			throw new GraphCreationException("Parameter n of LmLmTn must be non-negative");
		}
		this.n = n;
		this.m = m;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		long[][] rootLayer = create(maker, 0);
		return rootLayer[0][0];
	}

	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long[][] create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker, int depth) throws GraphCreationException {
		final long[][] lattice = new long[m][m];
		for (int i = 0; i < m; ++i) {
			for (int j = 0; j < m; ++j) {
				lattice[i][j] = maker.addNode();
			}
		}
		final long[][] leftChild = (depth < n) ? create(maker, depth + 1) : null;
		final long[][] rightChild = (depth < n) ? create(maker, depth + 1) : null;
		for (int i = 0; i < m; ++i) {
			for (int j = 0; j < m; ++j) {
				final long node = lattice[i][j];
				maker.addTransition(node, lattice[i][(j + 1) % m]);
				maker.addTransition(node, lattice[(i + 1) % m][j]);
				if (depth < n) {
					maker.addTransition(node, leftChild[i][j]);
					maker.addTransition(node, rightChild[i][j]);
				}
			}
		}
		return lattice;
	}

	@Override
	public long estimateGlobalNodes() {
		final long latticeSize = (long) m * (long) m;
		final long treeNodes = (1L << (n + 1)) - 1L;
		return latticeSize * treeNodes;
	}

	@Override
	public long estimateGlobalTransitions() {
		final long latticeSize = (long) m * (long) m;
		final long treeNodes = (1L << (n + 1)) - 1L;
		return latticeSize * (treeNodes * 3 - 1);
	}

	@Override
	public boolean canSynthetizeTranspose() {
		return true;
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return true;
	}

	@Override
	public String toString() {
		return "LmLmTn(m=" + m + ", n=" + n + ")";
	}
}
