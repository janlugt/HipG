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

import java.util.Arrays;

import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitLocalNode;

/**
 * Lattice.
 * 
 * @author Ela Krepska e.krepska@vu.nl
 */
public class Lattice implements SyntheticGraph {
	private final int rows, columns;
	private final boolean loopRows, loopColumns;

	public Lattice(final int rows, final int columns, final boolean loopRows, final boolean loopColumns)
			throws GraphCreationException {
		if (rows <= 0) {
			throw new GraphCreationException("#rows negative");
		}
		if (columns <= 0) {
			throw new GraphCreationException("#columns negative");
		}
		this.rows = rows;
		this.columns = columns;
		this.loopRows = loopRows;
		this.loopColumns = loopColumns;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		// Create the first row and no transitions in it.
		final long[] firstRow = new long[columns];
		for (int col = 0; col < columns; ++col) {
			firstRow[col] = maker.addNode();
		}

		// Create row-wise.
		long[] currentRow = Arrays.copyOf(firstRow, columns);
		long[] nextRow = new long[columns];
		for (int row = 0; row < rows - 1; ++row) {
			// Create next row.
			for (int col = 0; col < columns; ++col) {
				nextRow[col] = maker.addNode();
			}
			// Create transitions in the current row.
			for (int col = 0; col < columns - 1; ++col) {
				maker.addTransition(currentRow[col], currentRow[col + 1]);
				maker.addTransition(currentRow[col], nextRow[col]);
			}
			if (loopRows) {
				maker.addTransition(currentRow[columns - 1], currentRow[0]);
			}
			maker.addTransition(currentRow[columns - 1], nextRow[columns - 1]);
			// Swap next and current.
			long[] tmpRow = nextRow;
			nextRow = currentRow;
			currentRow = tmpRow;
		}

		// Create transitions in the last row.
		for (int col = 0; col < columns - 1; ++col) {
			maker.addTransition(currentRow[col], currentRow[col + 1]);
			if (loopColumns) {
				maker.addTransition(currentRow[col], firstRow[col]);
			}
		}
		if (loopRows) {
			maker.addTransition(currentRow[columns - 1], currentRow[0]);
		}
		if (loopColumns) {
			maker.addTransition(currentRow[columns - 1], firstRow[columns - 1]);
		}

		return firstRow[0];
	}

	@Override
	public long estimateGlobalNodes() {
		return (long) rows * (long) columns;
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
		return transitions;
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
		return "Lattice(loop=" + (loopRows ? (loopColumns ? "rows&cols" : "rows") : (loopColumns ? "cols" : "none"))
				+ ", rows=" + rows + ", columns=" + columns + ")";
	}
}
