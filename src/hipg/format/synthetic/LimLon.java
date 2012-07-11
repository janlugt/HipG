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

/**
 * Cartesian product of Line(m) x Loop(n) (Loop is a synonym for Ring). The graph has thus m^2 * n^2 nodes. Logically it
 * is a non-looped lattice such that each node of that lattice is a looped lattice. The quotient graph is the non-looped
 * lattice m x m and each SCC is the looped lattice n x n.
 * 
 * @author Ela Krepska, e.krepska@vu.nl
 */
public class LimLon implements SyntheticGraph {
	private final int m, n;

	public LimLon(final int m, final int n) throws GraphCreationException {
		if (n <= 0) {
			throw new GraphCreationException("Parameter n of LimLon must be positive");
		}
		if (m <= 0) {
			throw new GraphCreationException("Parameter m of LimLon must be positive");
		}
		this.m = m;
		this.n = n;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		// Create "backwall" a wall of nxn sccs that form the first row of the quotient graph.
		long[][][] backwall = new long[m][n][n];
		long[][][] nextwall = (m == 1 ? backwall : new long[m][n][n]);
		for (int mcol = 0; mcol < m; ++mcol) {
			long[][] scc = backwall[mcol];
			for (int nrow = 0; nrow < n; nrow++) {
				for (int ncol = 0; ncol < n; ncol++) {
					scc[nrow][ncol] = maker.addNode();
				}
			}
		}
		// Save root.
		final long root = backwall[0][0][0];

		// Create the remining walls, row by row (in the m x m lattice).
		for (int mrow = 0; mrow < m; ++mrow) {
			// Create new nextwall.
			if (mrow < m - 1) {
				for (int mcol = 0; mcol < m; ++mcol) {
					for (int nrow = 0; nrow < n; ++nrow) {
						for (int ncol = 0; ncol < n; ++ncol) {
							nextwall[mcol][nrow][ncol] = maker.addNode();
						}
					}
				}
			}
			for (int mcol = 0; mcol < m; ++mcol) {
				long[][] scc = backwall[mcol];
				long[][] rightScc = mcol == m - 1 ? null : backwall[mcol + 1];
				long[][] bottomScc = mrow == m - 1 ? null : nextwall[mcol];
				for (int nrow = 0; nrow < n; nrow++) {
					for (int ncol = 0; ncol < n; ncol++) {
						final long node = scc[nrow][ncol];
						// Interconnect the scc.
						maker.addTransition(node, scc[nrow][(ncol + 1) % n]);
						maker.addTransition(node, scc[(nrow + 1) % n][ncol]);
						// Connect from the bottom scc.
						if (bottomScc != null) {
							maker.addTransition(node, bottomScc[nrow][ncol]);
						}
						// Connect to from the left scc.
						if (rightScc != null) {
							maker.addTransition(node, rightScc[nrow][ncol]);
						}
					}
				}
			}
			// Swap bakckwall and nextwall.
			long[][][] tmpwall = nextwall;
			nextwall = backwall;
			backwall = tmpwall;
		}
		return root;
	}

	@Override
	public long estimateGlobalNodes() {
		final long n2 = n * n;
		final long m2 = m * m;
		return n2 * m2;
	}

	@Override
	public long estimateGlobalTransitions() {
		final long n2 = n * n;
		final long m2 = m << 1;
		final long m22 = m2 * (m2 - 1);
		return n2 * m22;
	}

	@Override
	public boolean canSynthetizeTranspose() {
		return true;
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return true;
	}

	public String toString() {
		return "LimLon(m=" + m + ", n=" + n + ")";
	}
}
