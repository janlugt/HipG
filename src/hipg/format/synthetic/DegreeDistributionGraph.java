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

import myutils.probability.DiscreteDistribution;
import myutils.probability.ProbabilityDistribution;
import myutils.storage.set.LongHashSet;

/**
 * Graph with specific distribution of vertices.
 */
public class DegreeDistributionGraph implements SyntheticGraph {
	private static final int MAX_DEGREE = Short.MAX_VALUE;

	protected final long n;
	protected final ProbabilityDistribution degreeDistribution;
	protected final Random random;
	protected final boolean disallowMultipleTransitions;

	public DegreeDistributionGraph(final long n, final ProbabilityDistribution degreeDistribution, final Random random,
			final boolean disallowMultipleTransitions) {
		this.n = n;
		this.random = random;
		this.degreeDistribution = degreeDistribution;
		this.disallowMultipleTransitions = disallowMultipleTransitions;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		if (maker.hasTranspose()) {
			throw new GraphCreationException("Transpose not supported yet");
		}
		return createNoTranspose(maker);
	}

	private <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long createNoTranspose(
			AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		// Create all nodes.
		final long root = maker.addNode();
		for (long i = 1; i < n; i++) {
			maker.addNode();
		}

		// Get #nodes per rank.
		final int poolSize = maker.getPoolSize();
		final int[] allNumNodes = new int[poolSize];
		for (int rnk = 0; rnk < poolSize; ++rnk) {
			allNumNodes[rnk] = maker.numNodes(rnk);
		}
		final int rank = maker.getRank();
		final int numNodes = allNumNodes[rank];
		final long perRank = maker.totalNumNodes() / poolSize;

		// Create a map to store the transitions already created to disallow multiple transitions.
		final LongHashSet targets = (disallowMultipleTransitions ? new LongHashSet(1024 * 4) : null);

		// Create the probability distribution to pick random owners.
		final DiscreteDistribution owners = new DiscreteDistribution(0, allNumNodes, random);

		for (int srcId = 0; srcId < numNodes; ++srcId) {
			// Draw the degree according to the distribution.
			int outdegree;
			do {
				outdegree = (int) Math.round(degreeDistribution.sample());
				if (outdegree < 0 || outdegree > MAX_DEGREE) {
					System.err.println("Warning: Redrawing degree of " + srcId + "@" + rank + " (" + outdegree
							+ " drawn from " + degreeDistribution + ")");
				}
			} while (outdegree < 0 || outdegree > MAX_DEGREE);

			if (disallowMultipleTransitions && outdegree >= maker.totalNumNodes()) {
				if (outdegree > maker.totalNumNodes()) {
					System.err.println("Warning: Limiting degree of " + srcId + "@" + rank + " to "
							+ maker.totalNumNodes());
				}
				// Connect to all nodes.
				for (int dstOwner = 0; dstOwner < poolSize; dstOwner++) {
					for (int dstId = 0; dstId < maker.numNodes(dstOwner); dstId++) {
						maker.addTransition(rank, srcId, dstOwner, dstId);
					}
				}
			} else {
				// Connect to random out-degree targets.
				for (int i = 0; i < outdegree; i++) {
					// Draw random owner.
					int dstOwner = (int) Math.round(owners.sample());
					// Draw random node.
					int dstId = random.nextInt(allNumNodes[dstOwner]);
					// Check for uniqueness if necessary.
					if (disallowMultipleTransitions) {
						while (targets.contains(hash(dstOwner, dstId, perRank))) {
							// Draw again a random owner.
							dstOwner = (int) Math.round(owners.sample());
							// Draw again a random node.
							dstId = random.nextInt(allNumNodes[dstOwner]);
						}
						// Store this target.
						targets.insert(hash(dstOwner, dstId, perRank));
					}
					// Add the transition.
					maker.addTransition(rank, srcId, dstOwner, dstId);
				}
				if (disallowMultipleTransitions) {
					targets.clear();
				}
			}
		}
		return root;
	}

	private final long hash(int owner, int id, long perRank) {
		return (long) owner * perRank + id;
	}

	@Override
	public long estimateGlobalNodes() {
		return n;
	}

	@Override
	public long estimateGlobalTransitions() {
		return (long) Math.ceil((double) n * degreeDistribution.expected());
	}

	@Override
	public boolean canSynthetizeTranspose() {
		return false;
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return true;
	}

	@Override
	public String toString() {
		return "DegreeDistributionGraph(n=" + n + ", " + degreeDistribution.toString() + ")";
	}
}
