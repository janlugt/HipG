/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam.
 * Written by Ela Krepska e.l.krepska@vu.nl.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package hipg.graph;

import hipg.LocalNode;
import hipg.Node;
import hipg.runtime.Runtime;

public abstract class OnTheFlyLocalNode<TNode extends Node> extends LocalNode<TNode> {

	protected final OnTheFlyGraph<TNode> graph;
	protected final byte[] state;

	public OnTheFlyLocalNode(OnTheFlyGraph<TNode> graph, byte[] state) {
		if (state == null) {
			throw new NullPointerException();
		}
		this.graph = graph;
		this.state = state;
	}

	public String name() {
		return String.valueOf(id() + "@" + Runtime.getRank());
	}

	public final short graphId() {
		return graph.getId();
	}

	public int id() {
		return graph.hash().id(state);
	}

	public boolean hasNeighbor(int index) {
		return getNeighbor(index) != null;
	}

	abstract protected byte[] getNeighbor(int index);

	abstract public boolean shouldStore();

	public int neighborOwner(int index) {
		return graph.hash().owner(getNeighbor(index));
	}

	public int neighborId(int index) {
		return graph.hash().id(getNeighbor(index));
	}

	public final boolean isNeighborLocal(int index) {
		return neighborOwner(index) == Runtime.getRank();
	}

	public final OnTheFlyLocalNode<TNode> localNeighbor(int index) {
		return (OnTheFlyLocalNode<TNode>) graph.node(getNeighbor(index));
	}

	public final TNode neighbor(int index) {
		throw new UnsupportedOperationException(OnTheFlyLocalNode.class + "::neighbor(index) should never be executed. "
				+ "Did you run the rewriter?");
	}

	public int hashCode() {
		return graph.hash().id(state);
	}

	public byte[] getState() {
		return state;
	}

	public String toString() {
		return name();
	}
}
