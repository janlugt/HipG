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

public class Clique implements SyntheticGraph {
	private final int n;

	public Clique(final int n) throws GraphCreationException {
		if (n <= 0) {
			throw new GraphCreationException("#nodes not postitive");
		}
		this.n = n;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		// Create all nodes.
		final long[] nodes = new long[n];
		for (int i = 0; i < n; ++i) {
			nodes[i] = maker.addNode();
		}
		final long root = nodes[0];

		// Create transitions from all to all.
		for (long source : nodes) {
			for (long target : nodes) {
				if (source != target) {
					maker.addTransition(source, target);
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
	public boolean canSynthetizeTranspose() {
		return true;
	}

	@Override
	public long estimateGlobalTransitions() {
		return n * (n - 1);
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return true;
	}

	@Override
	public String toString() {
		return "Clique(" + n + ")";
	}
}
