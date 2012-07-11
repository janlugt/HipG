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

package hipg;

import hipg.runtime.Runtime;

import java.io.Serializable;

/**
 * Serializable graph reference.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 * @param <TNode>
 */
public final class GraphReference<TNode extends Node> implements Serializable {
	private static final long serialVersionUID = 1L;

	private short graphId;

	public GraphReference(short graphId) {
		this.graphId = graphId;
	}

	public GraphReference(Graph<TNode> g) {
		this(g.getId());
	}

	@SuppressWarnings("unchecked")
	public Graph<TNode> getGraph() {
		return (Graph<TNode>) Runtime.getRuntime().getGraph(graphId);
	}

	@Override
	public String toString() {
		return "Graph(" + graphId + ")";
	}

}
