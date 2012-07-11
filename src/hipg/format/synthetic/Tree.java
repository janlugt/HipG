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

import myutils.MathUtils;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitLocalNode;

/**
 * A graph representing a full tree with a specified height and branch factor (number of sons in a non-leaf node). The
 * number of nodes in the tres is branch^(height+1)-1.
 */
public class Tree implements SyntheticGraph {
	private final int height, branch;

	public Tree(final int height, final int branch) throws GraphCreationException {
		if (height < 0) {
			throw new GraphCreationException("Tree height must be non-negative");
		}
		if (branch < 2) {
			throw new GraphCreationException("Tree branch must be at least 2");
		}
		this.height = height;
		this.branch = branch;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		return create(maker, 0);
	}

	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker, int depth) throws GraphCreationException {
		final long node = maker.addNode();
		if (depth < height) {
			final long[] children = new long[branch];
			for (int i = 0; i < branch; i++) {
				children[i] = create(maker, depth + 1);
			}
			for (int i = 0; i < branch; i++) {
				maker.addTransition(node, children[i]);
			}
		}
		return node;
	}

	@Override
	public long estimateGlobalNodes() {
		long power = (long) Math.round(MathUtils.FastPower(branch, height + 1));
		return (power - 1) / (branch - 1);
	}

	@Override
	public long estimateGlobalTransitions() {
		return estimateGlobalNodes() - 1;
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
		if (branch == 2) {
			return "BinTree(h=" + height + ")";
		} else {
			return "Tree(h=" + height + ", b=" + branch + ")";
		}
	}
}
