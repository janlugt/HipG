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

import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitLocalNode;

public class LineOfSubgraphs implements SyntheticGraph {
	private final int n;
	private final SyntheticGraph subGraph;

	public LineOfSubgraphs(final int n, final SyntheticGraph subGraph) throws GraphCreationException {
		if (n <= 0) {
			throw new GraphCreationException("Tree height must be non-negative");
		}
		if (subGraph == null) {
			throw new GraphCreationException("Subraph null");
		}
		this.n = n;
		this.subGraph = subGraph;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		return create(maker, 0);
	}

	private <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker, int depth) throws GraphCreationException {
		final long root = subGraph.create(maker);
		long left = root;
		for (int i = 1; i < n; ++i) {
			final long node = subGraph.create(maker);
			maker.addTransition(left, node);
			left = node;
		}
		return root;
	}

	@Override
	public boolean canSynthetizeTranspose() {
		return subGraph.canSynthetizeTranspose();
	}

	@Override
	public long estimateGlobalNodes() {
		return n * subGraph.estimateGlobalNodes();
	}

	@Override
	public long estimateGlobalTransitions() {
		return subGraph.estimateGlobalTransitions() * n + n - 1;
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return false;
	}

	@Override
	public String toString() {
		return "Line(" + n + ") of " + subGraph.toString();
	}
}
