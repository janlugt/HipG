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

/** A line graph with n nodes and (n-1) transitions. */
public class Line implements SyntheticGraph {
	private final long n;

	public Line(final long n) throws GraphCreationException {
		if (n <= 0) {
			throw new GraphCreationException("Line length must be positive");
		}
		this.n = n;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		long first = maker.addNode(), left = first;
		for (long i = 1; i < n; i++) {
			long next = maker.addNode();
			maker.addTransition(left, next);
			left = next;
		}
		return first;
	}

	@Override
	public long estimateGlobalNodes() {
		return n;
	}

	@Override
	public long estimateGlobalTransitions() {
		return n - 1L;
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
		return "Line(" + n + ")";
	}
}
