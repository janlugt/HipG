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
import hipg.graph.ExplicitNodeReference;

public interface SyntheticGraph {

	public long NULL = ExplicitNodeReference.NULL_NODE;

	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			final AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException;

	public boolean canSynthetizeTranspose();

	public boolean transitionsPerNodeCreatedSequentially();

	public long estimateGlobalNodes();

	public long estimateGlobalTransitions();
}
