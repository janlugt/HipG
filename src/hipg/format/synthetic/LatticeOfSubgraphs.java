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

public class LatticeOfSubgraphs implements SyntheticGraph {
	private final int rows, columns;
	private final int rowsFilled, columnsFilled;
	private final boolean loopRows, loopColumns;
	private final SyntheticGraph subGraph;

	public LatticeOfSubgraphs(final int rows, final int columns, final int rowsFilled, final int columnsFilled,
			final boolean loopRows, final boolean loopColumns, final SyntheticGraph subGraph)
			throws GraphCreationException {
		if (rows <= 0) {
			throw new GraphCreationException("#rows negative");
		}
		if (columns <= 0) {
			throw new GraphCreationException("#columns negative");
		}
		if (subGraph == null) {
			throw new GraphCreationException("Subraph null");
		}
		if (rowsFilled < 0) {
			throw new GraphCreationException("#rows filled negative");
		}
		if (rowsFilled > rows) {
			throw new GraphCreationException("#rows filled > #rows");
		}
		if (columnsFilled < 0) {
			throw new GraphCreationException("#columns filled negative");
		}
		if (columnsFilled > columns) {
			throw new GraphCreationException("#columns filled > #columns");
		}
		this.rows = rows;
		this.columns = columns;
		this.rowsFilled = rowsFilled;
		this.columnsFilled = columnsFilled;
		this.loopRows = loopRows;
		this.loopColumns = loopColumns;
		this.subGraph = subGraph;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		return create(maker, 0);
	}

	private <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker, int depth) throws GraphCreationException {
		// Create nodes.
		long[][] lattice = new long[rows][columns];
		for (int row = 0; row < rows; ++row) {
			for (int column = 0; column < columns; ++column) {
				lattice[row][column] = subGraph.create(maker);
			}
		}
		// Create east transitions.
		for (int row = 0; row < rowsFilled; ++row) {
			for (int column = 0; column < columns - 1; ++column) {
				maker.addTransition(lattice[row][column], lattice[row][column + 1]);
			}
			if (loopRows) {
				maker.addTransition(lattice[row][columns - 1], lattice[row][0]);
			}
		}
		// Create south transitions.
		for (int column = 0; column < columnsFilled; ++column) {
			for (int row = 0; row < rows - 1; ++row) {
				maker.addTransition(lattice[row][column], lattice[row + 1][column]);
			}
			if (loopColumns) {
				maker.addTransition(lattice[rows - 1][column], lattice[0][column]);
			}
		}
		return lattice[0][0];
	}

	@Override
	public boolean canSynthetizeTranspose() {
		return subGraph.canSynthetizeTranspose();
	}

	@Override
	public long estimateGlobalNodes() {
		return (long) rows * (long) columns * subGraph.estimateGlobalNodes();
	}

	@Override
	public long estimateGlobalTransitions() {
		final long eastTransitions = (long) rows * (long) (columns - 1);
		final long southTransitions = (long) (rows - 1) * (long) columns;
		long transitions = eastTransitions + southTransitions;
		if (loopRows) {
			transitions += rows;
		}
		if (loopColumns) {
			transitions += columns;
		}
		return transitions + (long) rows * (long) columns * subGraph.estimateGlobalTransitions();
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return false;
	}

	@Override
	public String toString() {
		final String latticeToString = "Lattice(loop="
				+ (loopRows ? (loopColumns ? "rows&cols" : "rows") : (loopColumns ? "cols" : "none")) + ", rows="
				+ rowsFilled + "/" + rows + ", columns=" + columnsFilled + "/" + columns + ")";
		return latticeToString + " of " + subGraph.toString();
	}
}
