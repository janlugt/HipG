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

package hipg.graph;

import hipg.Node;
import hipg.format.GraphCreationException;

import java.util.Arrays;
import java.util.Random;

import junit.framework.Assert;

import myutils.storage.bigarray.BigArray;
import myutils.storage.bigarray.BigIntArray;

import org.junit.Test;

public class ExplicitJoinedTransitionsTest {

	private static final ExplicitGraph<Node> createNonOrdered(final int poolSize, final int nodes,
			final int outgoingLocal, final int outgoingRemote, final int incomingLocal, final int incomingRemote)
			throws GraphCreationException {
		Assert.assertTrue(poolSize >= 1);
		final boolean transpose = (incomingLocal + incomingRemote > 0);
		final ExplicitGraph<Node> graph = new ExplicitGraph<Node>(nodes, nodes * poolSize, false, outgoingLocal,
				outgoingRemote, transpose, false, outgoingRemote, outgoingLocal);
		for (int i = 0; i < nodes; i++) {
			graph.addNode(new ExplicitLocalNode<Node>(graph, graph.nextNodeId()));
		}

		int[] localCount = new int[nodes];
		int[] remoteCount = new int[nodes];
		int[] localInCount = new int[nodes];
		int[] remoteInCount = new int[nodes];

		Arrays.fill(localCount, 0);
		Arrays.fill(remoteCount, 0);
		Arrays.fill(localInCount, 0);
		Arrays.fill(remoteInCount, 0);

		final Random rand = new Random(System.nanoTime());

		for (int i = 0; i < outgoingLocal; i++) {
			final int sourceId = rand.nextInt(nodes);
			final ExplicitLocalNode<Node> source = graph.node(sourceId);
			Assert.assertNotNull("Node at " + sourceId + " is null", source);
			final int targetId = rand.nextInt(nodes);
			Assert.assertNotNull("Node at " + targetId + " is null", graph.node(targetId));
			source.addTransition(0, targetId);
			localCount[sourceId]++;
		}
		if (poolSize > 1) {
			for (int i = 0; i < outgoingRemote; i++) {
				final int sourceId = rand.nextInt(nodes);
				final ExplicitLocalNode<Node> source = graph.node(sourceId);
				Assert.assertNotNull(source);
				final int targetOwner = 1 + rand.nextInt(poolSize - 1);
				final int targetId = rand.nextInt(nodes);
				source.addTransition(targetOwner, targetId);
				remoteCount[sourceId]++;
			}
		}
		if (transpose) {
			for (int i = 0; i < incomingLocal; i++) {
				final int targetId = rand.nextInt(nodes);
				final ExplicitLocalNode<Node> target = graph.node(targetId);
				Assert.assertNotNull("Node at " + targetId + " is null", target);
				final int sourceId = rand.nextInt(nodes);
				Assert.assertNotNull("Node at " + sourceId + " is null", graph.node(sourceId));
				target.addInTransition(0, sourceId);
				localInCount[targetId]++;
			}
			if (poolSize > 1) {
				for (int i = 0; i < incomingRemote; i++) {
					final int targetId = rand.nextInt(nodes);
					final ExplicitLocalNode<Node> target = graph.node(targetId);
					Assert.assertNotNull(target);
					final int sourceOwner = 1 + rand.nextInt(poolSize - 1);
					final int sourceId = rand.nextInt(nodes);
					target.addInTransition(sourceOwner, sourceId);
					remoteInCount[targetId]++;
				}
			}
		}
		graph.finishCreation();

		Assert.assertEquals("Expecting " + outgoingLocal + " transitions", outgoingLocal, graph.getTransitions()
				.getNumLocalTransitions());
		Assert.assertEquals("Expecting " + outgoingRemote + " transitions", outgoingRemote, graph.getTransitions()
				.getNumRemoteTransitions());

		if (transpose) {
			Assert.assertEquals("Expecting " + incomingLocal + " transitions", incomingLocal, graph.getInTransitions()
					.getNumLocalTransitions());
			Assert.assertEquals("Expecting " + incomingRemote + " transitions", incomingRemote, graph
					.getInTransitions().getNumRemoteTransitions());
		}

		// additional checks on local transitions
		final BigArray<ExplicitLocalNode<Node>> localOutTransitions = graph.getTransitions().getLocalTransitions();
		final long numLocalOutTransitions = graph.getTransitions().getNumLocalTransitions();
		Assert.assertNotNull(localOutTransitions);
		Assert.assertEquals(outgoingLocal, numLocalOutTransitions);
		Assert.assertTrue(outgoingLocal <= localOutTransitions.size());
		for (int i = 0; i < numLocalOutTransitions; i++) {
			Assert.assertNotNull(localOutTransitions.get(i));
		}
		if (transpose) {
			final BigArray<ExplicitLocalNode<Node>> localInTransitions = graph.getInTransitions().getLocalTransitions();
			final long numLocalInTransitions = graph.getInTransitions().getNumLocalTransitions();
			Assert.assertNotNull(localInTransitions);
			Assert.assertEquals(incomingLocal, numLocalInTransitions);
			Assert.assertTrue(incomingLocal <= localInTransitions.size());
			for (int i = 0; i < numLocalInTransitions; i++) {
				Assert.assertNotNull(localInTransitions.get(i));
			}
		}

		// additional checks on remote transitions
		final BigIntArray remoteOutTransitions = graph.getTransitions().getRemoteTransitions();
		final long numRemoteOutTransitions = graph.getTransitions().getNumRemoteTransitions();
		Assert.assertNotNull(remoteOutTransitions);
		Assert.assertEquals(outgoingRemote, numRemoteOutTransitions);
		Assert.assertTrue(outgoingRemote <= remoteOutTransitions.size());
		for (int i = 0; i < numRemoteOutTransitions; i++) {
			Assert.assertNotNull(remoteOutTransitions.get(i));
		}
		if (transpose) {
			final BigIntArray remoteInTransitions = graph.getInTransitions().getRemoteTransitions();
			final long numRemoteInTransitions = graph.getInTransitions().getNumRemoteTransitions();
			Assert.assertNotNull(remoteInTransitions);
			Assert.assertEquals(incomingRemote, numRemoteInTransitions);
			Assert.assertTrue(incomingRemote <= remoteInTransitions.size());
			for (int i = 0; i < numRemoteInTransitions; i++) {
				Assert.assertNotNull(remoteInTransitions.get(i));
			}
		}

		// checks on nodes
		Assert.assertEquals(nodes, graph.nodes());
		for (int i = 0; i < nodes; i++) {
			ExplicitLocalNode<Node> node = graph.node(i);
			Assert.assertTrue(node.localOutdegree() >= 0);
			Assert.assertTrue(node.localNeighborsCount >= 0);
			Assert.assertTrue(node.localNeighborsStart >= 0);
			Assert.assertTrue(node.remoteNeighborsCount >= 0);
			Assert.assertTrue(node.remoteNeighborsStart >= 0);
			Assert.assertEquals(localCount[i], node.localNeighborsCount);
			Assert.assertEquals(remoteCount[i], node.remoteNeighborsCount);
			if (transpose) {
				Assert.assertTrue(node.localIndegree() >= 0);
				Assert.assertTrue(node.localInNeighborsCount >= 0);
				Assert.assertTrue(node.localInNeighborsStart >= 0);
				Assert.assertTrue(node.remoteInNeighborsCount >= 0);
				Assert.assertTrue(node.remoteInNeighborsStart >= 0);
				Assert.assertEquals(remoteInCount[i], node.remoteInNeighborsCount);
				Assert.assertEquals(localInCount[i], node.localInNeighborsCount);
			}
		}
		return graph;
	}

	@Test
	public void testMinimal() throws GraphCreationException {
		createNonOrdered(1, 10, 0, 0, 0, 0);
	}

	@Test
	public void testMinimal2() throws GraphCreationException {
		createNonOrdered(2, 10, 0, 0, 0, 0);
	}

	@Test
	public void testMinimal4() throws GraphCreationException {
		createNonOrdered(4, 10, 0, 0, 0, 0);
	}

	@Test
	public void testMinimalTranspose() throws GraphCreationException {
		createNonOrdered(1, 10, 0, 0, 0, 0);
	}

	@Test
	public void testMinimalTranspose2() throws GraphCreationException {
		createNonOrdered(2, 10, 0, 0, 0, 0);
	}

	@Test
	public void testMinimalTranspose4() throws GraphCreationException {
		createNonOrdered(4, 10, 0, 0, 0, 0);
	}

	@Test
	public void testSmall() throws GraphCreationException {
		createNonOrdered(1, 100, 297, 0, 0, 0);
	}

	@Test
	public void testSmall2() throws GraphCreationException {
		createNonOrdered(2, 100, 297, 573, 0, 0);
	}

	@Test
	public void testSmall4() throws GraphCreationException {
		createNonOrdered(4, 100, 297, 573, 0, 0);
	}

	@Test
	public void testSmallTranspose() throws GraphCreationException {
		createNonOrdered(1, 100, 297, 0, 535, 0);
	}

	@Test
	public void testSmallTranspose2() throws GraphCreationException {
		createNonOrdered(2, 100, 297, 145, 535, 355);
	}

	@Test
	public void testSmallTranspose4() throws GraphCreationException {
		createNonOrdered(4, 100, 297, 145, 535, 355);
	}

	@Test
	public void testMed() throws GraphCreationException {
		createNonOrdered(1, 100, 2907, 0, 0, 0);
	}

	@Test
	public void testMed2() throws GraphCreationException {
		createNonOrdered(2, 100, 2917, 5713, 0, 0);
	}

	@Test
	public void testMed4() throws GraphCreationException {
		createNonOrdered(4, 100, 2937, 5743, 0, 0);
	}

	@Test
	public void testMedTranspose() throws GraphCreationException {
		createNonOrdered(1, 100, 2297, 0, 535, 0);
	}

	@Test
	public void testMedTranspose2() throws GraphCreationException {
		createNonOrdered(2, 100, 2697, 1453, 535, 355);
	}

	@Test
	public void testMedTranspose4() throws GraphCreationException {
		createNonOrdered(4, 100, 2297, 1425, 535, 355);
	}

	private static final ExplicitGraph<Node> createOrdered(final int poolSize, final int nodes,
			final int outgoingLocal, final int outgoingRemote, final int incomingLocal, final int incomingRemote)
			throws GraphCreationException {

		Assert.assertTrue(poolSize >= 1);

		final boolean transpose = (incomingLocal + incomingRemote > 0);

		final ExplicitGraph<Node> graph = new ExplicitGraph<Node>(nodes, nodes * poolSize, true, outgoingLocal,
				outgoingRemote, transpose, false, outgoingRemote, outgoingLocal);

		for (int i = 0; i < nodes; i++) {
			graph.addNode(new ExplicitLocalNode<Node>(graph, graph.nextNodeId()));
		}

		int[] localCount = new int[nodes];
		int[] remoteCount = new int[nodes];
		int[] localInCount = new int[nodes];
		int[] remoteInCount = new int[nodes];

		Arrays.fill(localCount, 0);
		Arrays.fill(remoteCount, 0);
		Arrays.fill(localInCount, 0);
		Arrays.fill(remoteInCount, 0);

		final Random rand = new Random(System.nanoTime());

		{
			int avgLocalPerNode = (int) Math.ceil((double) outgoingLocal / (double) nodes);
			int avgRemotePerNode = (int) Math.ceil((double) outgoingRemote / (double) nodes);

			int addedRemoteNodes = 0;
			int addedLocalNodes = 0;

			for (int i = 0; i < nodes; i++) {
				final int sourceId = i;
				final ExplicitLocalNode<Node> source = graph.node(sourceId);
				Assert.assertNotNull(source);

				for (int j = 0; j < avgLocalPerNode; j++) {
					if (addedLocalNodes < outgoingLocal) {
						final int targetId = rand.nextInt(nodes);
						Assert.assertNotNull(graph.node(targetId));
						source.addTransition(0, targetId);
						localCount[sourceId]++;
						addedLocalNodes++;
					}
				}
				if (poolSize > 1) {
					for (int j = 0; j < avgRemotePerNode; j++) {
						if (addedRemoteNodes < outgoingRemote) {
							final int targetOwner = 1 + rand.nextInt(poolSize - 1);
							final int targetId = rand.nextInt(nodes);
							source.addTransition(targetOwner, targetId);
							remoteCount[sourceId]++;
							addedRemoteNodes++;
						}
					}
				}
			}

			while (addedLocalNodes < outgoingLocal) {
				final int sourceId = nodes - 1;
				final ExplicitLocalNode<Node> source = graph.node(sourceId);
				final int targetId = rand.nextInt(nodes);
				Assert.assertNotNull("Node at " + targetId + " is null", graph.node(targetId));
				source.addTransition(0, targetId);
				localCount[sourceId]++;
				addedLocalNodes++;
			}

			if (poolSize > 1) {
				while (addedRemoteNodes < outgoingRemote) {
					final int sourceId = nodes - 1;
					final ExplicitLocalNode<Node> source = graph.node(sourceId);
					final int targetOwner = 1 + rand.nextInt(poolSize - 1);
					final int targetId = rand.nextInt(nodes);
					source.addTransition(targetOwner, targetId);
					remoteCount[sourceId]++;
					addedRemoteNodes++;
				}
			}
		}

		if (transpose) {
			{
				int avgLocalPerNode = (int) Math.ceil((double) incomingLocal / (double) nodes);
				int avgRemotePerNode = (int) Math.ceil((double) incomingRemote / (double) nodes);

				int addedRemoteNodes = 0;
				int addedLocalNodes = 0;

				for (int i = 0; i < nodes; i++) {
					final int targetId = i;
					final ExplicitLocalNode<Node> target = graph.node(targetId);
					Assert.assertNotNull("Node at " + targetId + " is null", target);

					for (int j = 0; j < avgLocalPerNode; j++) {
						if (addedLocalNodes < incomingLocal) {
							final int sourceId = rand.nextInt(nodes);
							Assert.assertNotNull("Node at " + sourceId + " is null", graph.node(sourceId));
							target.addInTransition(0, sourceId);
							localInCount[targetId]++;
							addedLocalNodes++;
						}
					}
					if (poolSize > 1) {
						for (int j = 0; j < avgRemotePerNode; j++) {
							if (addedRemoteNodes < incomingRemote) {
								final int sourceOwner = 1 + rand.nextInt(poolSize - 1);
								final int sourceId = rand.nextInt(nodes);
								target.addInTransition(sourceOwner, sourceId);
								remoteInCount[targetId]++;
								addedRemoteNodes++;
							}
						}
					}
				}
				while (addedLocalNodes < incomingLocal) {
					final int targetId = nodes - 1;
					final ExplicitLocalNode<Node> target = graph.node(targetId);
					final int sourceId = rand.nextInt(nodes);
					Assert.assertNotNull("Node at " + sourceId + " is null", graph.node(sourceId));
					target.addInTransition(0, sourceId);
					localInCount[targetId]++;
					addedLocalNodes++;
				}

				if (poolSize > 1) {
					while (addedRemoteNodes < incomingRemote) {
						final int targetId = nodes - 1;
						final ExplicitLocalNode<Node> target = graph.node(targetId);
						final int sourceOwner = 1 + rand.nextInt(poolSize - 1);
						final int sourceId = rand.nextInt(nodes);
						target.addInTransition(sourceOwner, sourceId);
						remoteInCount[targetId]++;
						addedRemoteNodes++;
					}
				}
			}
		}
		graph.finishCreation();

		Assert.assertEquals("Expecting " + outgoingLocal + " transitions", outgoingLocal, graph.getTransitions()
				.getNumLocalTransitions());
		Assert.assertEquals("Expecting " + outgoingRemote + " transitions", outgoingRemote, graph.getTransitions()
				.getNumRemoteTransitions());

		if (transpose) {
			Assert.assertEquals("Expecting " + incomingLocal + " transitions", incomingLocal, graph.getInTransitions()
					.getNumLocalTransitions());
			Assert.assertEquals("Expecting " + incomingRemote + " transitions", incomingRemote, graph
					.getInTransitions().getNumRemoteTransitions());
		}

		/* checks */

		final BigArray<ExplicitLocalNode<Node>> localOutTransitions = graph.getTransitions().getLocalTransitions();
		final long numLocalOutTransitions = graph.getTransitions().getNumLocalTransitions();
		Assert.assertNotNull(localOutTransitions);
		Assert.assertEquals(outgoingLocal, numLocalOutTransitions);
		Assert.assertTrue(outgoingLocal <= localOutTransitions.size());
		for (int i = 0; i < numLocalOutTransitions; i++) {
			Assert.assertNotNull(localOutTransitions.get(i));
		}

		if (transpose) {
			final BigArray<ExplicitLocalNode<Node>> localInTransitions = graph.getInTransitions().getLocalTransitions();
			final long numLocalInTransitions = graph.getInTransitions().getNumLocalTransitions();
			Assert.assertNotNull(localInTransitions);
			Assert.assertEquals(incomingLocal, numLocalInTransitions);
			Assert.assertTrue(incomingLocal <= localInTransitions.size());
			for (int i = 0; i < numLocalInTransitions; i++) {
				Assert.assertNotNull(localInTransitions.get(i));
			}
		}

		// additional checks on remote transitions
		final BigIntArray remoteOutTransitions = graph.getTransitions().getRemoteTransitions();
		final long numRemoteOutTransitions = graph.getTransitions().getNumRemoteTransitions();
		Assert.assertNotNull(remoteOutTransitions);
		Assert.assertEquals(outgoingRemote, numRemoteOutTransitions);
		Assert.assertTrue(outgoingRemote <= remoteOutTransitions.size());
		for (int i = 0; i < numRemoteOutTransitions; i++) {
			Assert.assertNotNull(remoteOutTransitions.get(i));
		}

		if (transpose) {
			final BigIntArray remoteInTransitions = graph.getInTransitions().getRemoteTransitions();
			final long numRemoteInTransitions = graph.getInTransitions().getNumRemoteTransitions();
			Assert.assertNotNull(remoteInTransitions);
			Assert.assertEquals(incomingRemote, numRemoteInTransitions);
			Assert.assertTrue(incomingRemote <= remoteInTransitions.size());
			for (int i = 0; i < numRemoteInTransitions; i++) {
				Assert.assertNotNull(remoteInTransitions.get(i));
			}
		}

		// checks on nodes
		Assert.assertEquals(nodes, graph.nodes());
		for (int i = 0; i < nodes; i++) {
			ExplicitLocalNode<Node> node = graph.node(i);
			Assert.assertTrue(node.localOutdegree() >= 0);
			Assert.assertTrue(node.localNeighborsCount >= 0);
			if (node.localOutdegree() > 0) {
				Assert.assertTrue(node.localNeighborsStart >= 0);
			}
			Assert.assertTrue(node.remoteNeighborsCount >= 0);
			if (node.remoteOutdegree() > 0) {
				Assert.assertTrue(node.remoteNeighborsStart >= 0);
			}
			Assert.assertEquals(localCount[i], node.localNeighborsCount);
			Assert.assertEquals(remoteCount[i], node.remoteNeighborsCount);
			if (transpose) {
				Assert.assertTrue(node.localIndegree() >= 0);
				Assert.assertTrue(node.localInNeighborsCount >= 0);
				if (node.localIndegree() > 0) {
					Assert.assertTrue(node.localInNeighborsStart >= 0);
				}
				Assert.assertTrue(node.remoteInNeighborsCount >= 0);
				if (node.remoteIndegree() > 0) {
					Assert.assertTrue(node.remoteInNeighborsStart >= 0);
				}
				Assert.assertEquals(remoteInCount[i], node.remoteInNeighborsCount);
				Assert.assertEquals(localInCount[i], node.localInNeighborsCount);
			}
		}
		return graph;
	}

	@Test
	public void testOrderedMinimal() throws GraphCreationException {
		createOrdered(1, 10, 0, 0, 0, 0);
	}

	@Test
	public void testOrderedMinimal2() throws GraphCreationException {
		createOrdered(2, 10, 0, 0, 0, 0);
	}

	@Test
	public void testOrderedMinimal4() throws GraphCreationException {
		createOrdered(4, 10, 0, 0, 0, 0);
	}

	@Test
	public void testOrderedMinimalTranspose() throws GraphCreationException {
		createOrdered(1, 10, 0, 0, 0, 0);
	}

	@Test
	public void testOrderedMinimalTranspose2() throws GraphCreationException {
		createOrdered(2, 10, 0, 0, 0, 0);
	}

	@Test
	public void testOrderedMinimalTranspose4() throws GraphCreationException {
		createOrdered(4, 10, 0, 0, 0, 0);
	}

	@Test
	public void testOrderedSmall() throws GraphCreationException {
		createOrdered(1, 100, 297, 0, 0, 0);
	}

	@Test
	public void testOrderedSmall2() throws GraphCreationException {
		createOrdered(2, 100, 297, 573, 0, 0);
	}

	@Test
	public void testOrderedSmall4() throws GraphCreationException {
		createOrdered(4, 100, 297, 573, 0, 0);
	}

	@Test
	public void testOrderedSmallTranspose() throws GraphCreationException {
		createOrdered(1, 100, 297, 0, 535, 0);
	}

	@Test
	public void testOrderedSmallTranspose2() throws GraphCreationException {
		createOrdered(2, 100, 297, 145, 535, 355);
	}

	@Test
	public void testOrderedSmallTranspose4() throws GraphCreationException {
		createOrdered(4, 100, 297, 145, 535, 355);
	}

	@Test
	public void testOrderedMed() throws GraphCreationException {
		createOrdered(1, 100, 2907, 0, 0, 0);
	}

	@Test
	public void testOrderedMed2() throws GraphCreationException {
		createOrdered(2, 100, 2917, 5713, 0, 0);
	}

	@Test
	public void testOrderedMed4() throws GraphCreationException {
		createOrdered(4, 100, 2937, 5743, 0, 0);
	}

	@Test
	public void testOrderedMedTranspose() throws GraphCreationException {
		createOrdered(1, 100, 2297, 0, 535, 0);
	}

	@Test
	public void testOrderedMedTranspose2() throws GraphCreationException {
		createOrdered(2, 100, 2697, 1453, 535, 355);
	}

	@Test
	public void testOrderedMedTranspose4() throws GraphCreationException {
		createOrdered(4, 100, 2297, 1425, 535, 355);
	}
}
