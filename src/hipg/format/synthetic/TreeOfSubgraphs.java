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

import myutils.MathUtils;
import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitLocalNode;

public class TreeOfSubgraphs implements SyntheticGraph {
	private final int height, branch;
	private final SyntheticGraph subGraph;

	public TreeOfSubgraphs(final int height, final int branch, final SyntheticGraph subGraph)
			throws GraphCreationException {
		if (height < 0) {
			throw new GraphCreationException("Tree height must be non-negative");
		}
		if (branch < 2) {
			throw new GraphCreationException("Tree branch must be at least 2");
		}
		if (subGraph == null) {
			throw new GraphCreationException("Subraph null");
		}
		this.height = height;
		this.branch = branch;
		this.subGraph = subGraph;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		return create(maker, 0);
	}

	private <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker, int depth) throws GraphCreationException {
		final long node = subGraph.create(maker);
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
	public boolean canSynthetizeTranspose() {
		return subGraph.canSynthetizeTranspose();
	}

	@Override
	public long estimateGlobalNodes() {
		final long power = (long) Math.round(MathUtils.FastPower(branch, height + 1));
		return (power - 1) * subGraph.estimateGlobalNodes() / (branch - 1);
	}

	@Override
	public long estimateGlobalTransitions() {
		final long power = (long) Math.round(MathUtils.FastPower(branch, height + 1));
		final long treeNodes = (power - 1) / (branch - 1);
		return treeNodes - 1 + treeNodes * subGraph.estimateGlobalTransitions();
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return false;
	}

	@Override
	public String toString() {
		final String treeToString;
		if (branch == 2) {
			treeToString = "BinTree(h=" + height + ")";
		} else {
			treeToString = "Tree(h=" + height + ", b=" + branch + ")";
		}
		return treeToString + " of " + subGraph.toString();
	}
}
