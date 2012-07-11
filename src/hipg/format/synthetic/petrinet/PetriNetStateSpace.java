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

package hipg.format.synthetic.petrinet;

import hipg.Node;
import hipg.format.GraphCreationException;
import hipg.format.synthetic.AbstractSyntheticGraphMaker;
import hipg.format.synthetic.Partition;
import hipg.format.synthetic.SyntheticGraph;
import hipg.graph.ExplicitLocalNode;

import java.util.Iterator;

import myutils.storage.set.LongHashSet;

public class PetriNetStateSpace implements SyntheticGraph {
	private final PetriNet petriNet;
	private final byte granularity;
	private final long stateSpaceSize;

	public PetriNetStateSpace(PetriNet petriNet) {
		this.petriNet = petriNet;
		this.granularity = petriNet.getGranularity();
		this.stateSpaceSize = (long) Math.pow(petriNet.getGranularity(), petriNet.placeNum());
	}

	public PetriNet getPetriNet() {
		return petriNet;
	}

	@Override
	public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
			final AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
		/* create all nodes */
		final long sss = stateSpaceSize;
		for (long index = 0; index < sss; index++) {
			maker.addNode();
		}

		/* create all transitions */
		final int len = petriNet.placeNum();
		final int rank = maker.getRank();
		final byte[] marking = new byte[len];
		final byte[] nextMarking = new byte[len];
		final Partition partition = maker.getPartition();
		final LongHashSet addedTargets = new LongHashSet(1024);

		for (long index = 0; index < sss; index++) {
			final int srcOwner = partition.owner(index);
			if (srcOwner == rank) {
				final int srcId = partition.index(index);
				addedTargets.clear();

				/* create all transitions from marking based on index */
				nodeIndexToMarking(index, marking);
				System.arraycopy(marking, 0, nextMarking, 0, len);

				/* apply all transitions */
				final Iterator<PetriNetNode> transitions = petriNet.getTransitions();
				while (transitions.hasNext()) {
					// get next transition
					final PetriNetNode transition = transitions.next();
					// check if enabled
					if (petriNet.isEnabled(nextMarking, transition)) {
						// fire the transition and compute next state
						petriNet.fireEnabledTransition(nextMarking, transition);
						final long next = markingToNodeIndex(nextMarking);

						// add new state space transition
						if (!addedTargets.contains(next) && (!petriNet.biological() || index != next)) {
							addedTargets.insert(next);
							final int dstOwner = partition.owner(next);
							final int dstId = partition.index(next);
							maker.addTransition(srcOwner, srcId, dstOwner, dstId);
						}
						// reset next marking
						System.arraycopy(marking, 0, nextMarking, 0, len);
					}
				}

				/* applying all possible degradations */
				if (petriNet.biological()) {
					final Iterator<PetriNetNode> places = petriNet.getPlaces();
					while (places.hasNext()) {
						// get next place
						final PetriNetNode place = places.next();
						// check if degradation enabled
						if (petriNet.isDegradationEnabled(nextMarking, place)) {
							// compute next degraded transition
							petriNet.fireEnabledDegradation(nextMarking, place);
							final long next = markingToNodeIndex(nextMarking);
							// add new state space transition
							if (!addedTargets.contains(next) && (!petriNet.biological() || index != next)) {
								addedTargets.insert(next);
								final int dstOwner = partition.owner(next);
								final int dstId = partition.index(next);
								maker.addTransition(srcOwner, srcId, dstOwner, dstId);
							}
							// reset next marking
							System.arraycopy(marking, 0, nextMarking, 0, len);
						}
					}
				}
			}
		}

		return 0;
	}

	@Override
	public long estimateGlobalNodes() {
		return stateSpaceSize;
	}

	@Override
	public long estimateGlobalTransitions() {
		return stateSpaceSize * petriNet.placeNum();
	}

	@Override
	public boolean transitionsPerNodeCreatedSequentially() {
		return true;
	}

	@Override
	public boolean canSynthetizeTranspose() {
		return false;
	}

	public String[] getMarkingNames() {
		String[] names = new String[petriNet.placeNum()];
		for (int i = 0; i < names.length; i++) {
			names[i] = petriNet.getPlace(i).getName();
		}
		return names;
	}

	public final long markingToNodeIndex(final byte[] marking) {
		final int len = marking.length;
		long index = 0;
		for (int i = 0; i < len; i++) {
			index *= granularity;
			index += marking[i];
		}
		return index;
	}

	public final String markingToString(final byte[] marking) {
		final StringBuilder sb = new StringBuilder();
		sb.append("[");
		final int len = marking.length;
		for (int i = 0; i < len; ++i) {
			sb.append(petriNet.getPlace(i).getName());
			sb.append('=');
			sb.append(marking[i]);
			if (i + 1 < len) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}

	public void nodeIndexToMarking(final long index, final byte[] marking) {
		final int len = marking.length;
		long number = index;
		for (int i = len - 1; i >= 0; --i) {
			marking[i] = (byte) (number % granularity);
			number /= granularity;
		}
		if (number > 0) {
			throw new RuntimeException("Too small marking length");
		}
	}

	@Override
	public String toString() {
		return "StateSpace(" + petriNet.toString() + ")";
	}
}
