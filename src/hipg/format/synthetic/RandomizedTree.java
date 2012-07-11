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

import java.util.Random;

public final class RandomizedTree implements SyntheticGraph {
	private final long n;
	private final Random random;

	public RandomizedTree(final long n, Random random) throws GraphCreationException {
		if (n <= 0) {
			throw new GraphCreationException("Tree size must be positive");
		}
		this.n = n;
		this.random = random;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			final AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		final long root = maker.addNode();
		create(maker, root, n - 1);
		return root;
	}

	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> void create(
			final AbstractSyntheticGraphMaker<TNode, TLocalNode> maker, final long father, long nodes)
			throws GraphCreationException {
		if (nodes == 1) {
			maker.addTransition(father, maker.addNode());
		} else if (nodes > 1) {
			final long left = maker.addNode();
			final long right = maker.addNode();
			maker.addTransition(father, left);
			maker.addTransition(father, right);
			nodes -= 2;

			// Divide nodes between the children.
			double division = random.nextDouble();
			final long leftNodes = (long) Math.round(division * (double) nodes);
			final long rightNodes = nodes - leftNodes;
			if (leftNodes > 0) {
				create(maker, left, leftNodes);
			}
			if (rightNodes > 0) {
				create(maker, right, rightNodes);
			}
		}
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
	public long estimateGlobalNodes() {
		return n;
	}

	@Override
	public long estimateGlobalTransitions() {
		return n - 1;
	}

	@Override
	public String toString() {
		return "RandomizedTree(" + n + ")";
	}
}
