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

import hipg.Graph;

import hipg.Node;
import hipg.runtime.Runtime;
import hipg.format.GraphCreationException;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.utils.ReflectionUtils;

import java.lang.reflect.Constructor;

import myutils.system.MonitorThread;

public final class SyntheticGraphMaker<TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> implements
		AbstractSyntheticGraphMaker<TNode, TLocalNode> {
	private final Class<TLocalNode> TLocalNodeClass;
	private final Constructor<TLocalNode> LocalNodeConstructor;
	private final ExplicitGraph<TNode> graph;
	private final Partition partition;
	private final boolean transpose;
	private final int rank;
	private final int poolSize;
	private long numGlobalNodes = 0L;
	private final int[] numNodes;
	private final long[][] numTransitions;

	private SyntheticGraphMaker(final Class<TLocalNode> TLocalNodeClass, final Partition partition,
			final boolean transpose, final int rank, final int poolSize, final long estimateNodes,
			final long estimateAllTransitions, final boolean orderedAdding) throws GraphCreationException {

		this.TLocalNodeClass = TLocalNodeClass;
		this.LocalNodeConstructor = findConstructor();

		this.partition = partition;
		this.rank = rank;
		this.poolSize = poolSize;
		this.transpose = transpose;
		this.numNodes = new int[poolSize];
		this.numTransitions = new long[poolSize][poolSize];

		double localNodesEstimate = (double) estimateNodes / (double) poolSize;
		double outgoingTransitionsEstimate = (double) estimateAllTransitions / (double) poolSize;
		double localTransitionsEstimate = (double) outgoingTransitionsEstimate / (double) poolSize;
		double remoteTransitionsEstimate = 2.0 * (double) outgoingTransitionsEstimate * (double) (poolSize - 1)
				/ (double) (poolSize);

		if (localNodesEstimate > (double) Integer.MAX_VALUE - 1) {
			throw new GraphCreationException("Cannot create graph with " + estimateNodes + " of nodes on pool "
					+ poolSize);
		}
		if (localTransitionsEstimate > (double) Integer.MAX_VALUE - 1) {
			throw new GraphCreationException("Cannot create graph with " + localTransitionsEstimate
					+ " of local transitions on pool " + poolSize);
		}
		if (remoteTransitionsEstimate > (double) Integer.MAX_VALUE - 1) {
			throw new GraphCreationException("Cannot create graph with " + remoteTransitionsEstimate
					+ " of remote transitions on pool " + poolSize);
		}

		localNodesEstimate *= 1.1;
		localTransitionsEstimate *= 1.1;
		remoteTransitionsEstimate *= 1.1;

		localNodesEstimate += 1024;
		localTransitionsEstimate += 1024;
		remoteTransitionsEstimate += 1024;

		if (localNodesEstimate > (double) Integer.MAX_VALUE) {
			localNodesEstimate = Integer.MAX_VALUE;
		}
		if (localTransitionsEstimate > (double) Integer.MAX_VALUE) {
			localTransitionsEstimate = Integer.MAX_VALUE;
		}
		if (remoteTransitionsEstimate > (double) Integer.MAX_VALUE) {
			remoteTransitionsEstimate = Integer.MAX_VALUE;
		}

		if (rank == 0) {
			System.out.println("Estimated global #nodes = " + estimateNodes + " and #transitions = "
					+ estimateAllTransitions);
			System.out.flush();
		}

		this.graph = new ExplicitGraph<TNode>((int) Math.round(localNodesEstimate), estimateNodes, orderedAdding,
				(long) Math.round(localTransitionsEstimate), (long) Math.round(remoteTransitionsEstimate), transpose,
				false, (long) Math.round(localTransitionsEstimate), (long) Math.round(remoteTransitionsEstimate));
	}

	@Override
	public ExplicitGraph<TNode> create(SyntheticGraph sg) throws GraphCreationException {
		final MonitorThread monitor = new MonitorThread(10000, System.err, "maker") {
			public void print(StringBuilder sb) {
				final int nd = numNodes(rank) / 1000;
				final int tr = (int) (numTransitions(rank) / 1000000);
				sb.append(Runtime.getRank() + " allocated " + nd + "*10^3 nodes and " + tr + "*10^6 transitions");
			}
		}.startMonitor();
		final long root = sg.create(this);
		graph.setRoot(root);
		graph.finishCreation();
		monitor.stopMonitor();
		return graph;
	}

	public boolean hasTranspose() {
		return transpose;
	}

	public int getPoolSize() {
		return poolSize;
	}

	public Partition getPartition() {
		return partition;
	}

	public int getRank() {
		return rank;
	}

	@Override
	public long addNode() {
		final int owner = partition.owner(numGlobalNodes++);
		final int id = numNodes[owner]++;
		if (rank == owner) {
			final TLocalNode node = createNode();
			graph.addNode(node);
		}
		return ExplicitNodeReference.createReference(id, owner);
	}

	@Override
	public void addTransition(final long src, final long dst) throws GraphCreationException {
		addTransition(ExplicitNodeReference.getOwner(src), ExplicitNodeReference.getId(src),
				ExplicitNodeReference.getOwner(dst), ExplicitNodeReference.getId(dst));
	}

	@Override
	public void addTransition(final int srcOwner, final int srcId, final int dstOwner, final int dstId)
			throws GraphCreationException {
		numTransitions[srcOwner][dstOwner]++;
		if (rank == srcOwner) {
			graph.node(srcId).addTransition(dstOwner, dstId);
		}
		if (transpose && rank == dstOwner) {
			graph.node(dstId).addInTransition(srcOwner, srcId);
		}
	}

	public long numTransitions(int from, int to) {
		return numTransitions[from][to];
	}

	public long numTransitions(int from) {
		long s = 0;
		for (long t : numTransitions[from]) {
			s += t;
		}
		return s;
	}

	public long numTransitions() {
		return numTransitions(rank);
	}

	public long numGlobalTransitions(int from) {
		long s = 0;
		for (long[] tr : numTransitions) {
			for (long t : tr) {
				s += t;
			}
		}
		return s;
	}

	public int numNodes() {
		return numNodes[rank];
	}

	public int numNodes(int rank) {
		return numNodes[rank];
	}

	public long totalNumNodes() {
		return numGlobalNodes;
	}

	private final TLocalNode createNode() {
		try {
			return (TLocalNode) LocalNodeConstructor.newInstance(graph, graph.nextNodeId());
		} catch (Throwable e) {
			throw new RuntimeException("Could not create node with constructor " + LocalNodeConstructor + ": "
					+ e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private Constructor<TLocalNode> findConstructor() throws GraphCreationException {
		final Constructor<?> constructor = ReflectionUtils.findConstructor(TLocalNodeClass, Graph.class, int.class);
		if (constructor == null) {
			throw new GraphCreationException("Could not find constructor for " + TLocalNodeClass.getName());
		}
		try {
			return (Constructor<TLocalNode>) constructor;
		} catch (Throwable t) {
			throw new GraphCreationException("Constructor " + constructor + " is not a node constructor");
		}
	}

	public static <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> ExplicitGraph<TNode> create(
			SyntheticGraph sg, final Partition partition, final Class<TLocalNode> TLocalNodeClass,
			final boolean transpose, final int rank, final int poolSize, final boolean orderedAdding)
			throws GraphCreationException {
		if (!sg.canSynthetizeTranspose() && transpose) {
			throw new GraphCreationException("Cannot synthesise transpose");
		}
		final SyntheticGraphMaker<TNode, TLocalNode> maker = new SyntheticGraphMaker<TNode, TLocalNode>(
				TLocalNodeClass, partition, transpose, rank, poolSize, sg.estimateGlobalNodes(),
				sg.estimateGlobalTransitions(), orderedAdding);
		return maker.create(sg);
	}
}
