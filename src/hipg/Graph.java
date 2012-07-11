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

import hipg.format.synthetic.Partition;
import hipg.format.synthetic.SyntheticGraph;
import hipg.runtime.Runtime;

public abstract class Graph<TNode extends Node> {

	/** Graph's id. */
	private final short graphId;

	/** Graph's partition. */
	private Partition partition;

	/** Graph's maker (if synthetic). */
	private SyntheticGraph syntheticGraph;

	/** Creates an empty graph. */
	public Graph() {
		this.graphId = Runtime.getRuntime().registerGraph(this);
	}

	/** Gets graph's id. */
	public short getId() {
		return graphId;
	}

	public Partition getPartition() {
		return partition;
	}

	public void setPartition(Partition partition) {
		this.partition = partition;
	}

	public SyntheticGraph getSyntheticGraph() {
		return syntheticGraph;
	}

	public void setSyntheticGraph(SyntheticGraph maker) {
		this.syntheticGraph = maker;
	}

	public boolean isSynthetic() {
		return syntheticGraph != null;
	}

	/** Local number of nodes (might be unavailable). */
	abstract public int nodes();

}