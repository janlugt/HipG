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

import hipg.Config;
import hipg.Graph;
import hipg.LocalNode;
import hipg.Node;

public abstract class MapGraph<TNode extends Node, TState> extends Graph<TNode> {

	/** Map containing the states, hashed over a given hash function. */
	private OnTheFlyMap<TState, LocalNode<TNode>> nodes;

	public MapGraph(OnTheFlyMap<TState, LocalNode<TNode>> nodes) {
		this.nodes = nodes;
	}

	public int nodes() {
		return nodes.size();
	}

	public LocalNode<TNode> node(TState state) {
		return nodes.get(state);
	}

	public OnTheFlyMap<TState, LocalNode<TNode>> map() {
		return nodes;
	}

	public void addNode(TState state, LocalNode<TNode> node) {
		if (Config.ERRCHECK) {
			if (nodes.get(state) != null) {
				throw new RuntimeException("Attempt to add a state that already exists");
			}
		}
		nodes.put(state, node);
	}

}
