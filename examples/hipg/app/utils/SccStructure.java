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

package hipg.app.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import myutils.IOUtils;
import myutils.Serializable;
import myutils.StringUtils;
import myutils.storage.LongPairIterator;
import myutils.storage.LongValuePairIterator;
import myutils.storage.map.LongKeyHashMap;
import myutils.storage.map.LongToLongHashMap;
import myutils.tuple.pair.FastIntPair;

/** Represents a structure of strongly-connected components in a graph. */
public final class SccStructure implements Serializable {
	/** Map of components by their id. */
	private final LongKeyHashMap<Scc> components = new LongKeyHashMap<Scc>();

	/**
	 * List of transitions between components. First value in each array is the number of the components in that array.
	 */
	private final LongKeyHashMap<long[]> transitions = new LongKeyHashMap<long[]>();

	/** The total number of transitions. */
	private int totalNumTransitions = 0;

	public SccStructure() {
	}

	/** Returns an iterator over all components. */
	public LongValuePairIterator<Scc> components() {
		return components.iterator();
	}

	/** Returns the number of stored components. */
	public int numComponents() {
		return components.size();
	}

	/** Returns an iterator over all transitions. */
	public LongValuePairIterator<long[]> transitions() {
		return transitions.iterator();
	}

	/** Returns the number of components that have at least one transition. */
	public int numComponentsWithTransitions() {
		return transitions.size();
	}

	/** Returns the total number of transitions stored. */
	public int totalNumTransitions() {
		return totalNumTransitions;
	}

	/** Adds a component with given id and size. */
	public Scc addComponent(final long id, final long size) {
		if (components.get(id) != null) {
			throw new RuntimeException("Component " + id + " already exists");
		}
		if (size <= 0) {
			throw new RuntimeException("Cannot create a component with non-positive size (" + size + ")");
		}
		assert (size > 0);
		final Scc newComponent = new Scc(id, size);
		components.put(id, newComponent);
		return newComponent;
	}

	/** Returns the component wit given id, or null is not present. */
	public Scc getComponent(final long id) {
		return components.get(id);
	}

	/**
	 * Adds a transition between two components. Returns true if a new transition was created.
	 */
	public boolean addTransition(final long sourceComponentId, final long targetComponentId) {
		if (sourceComponentId == targetComponentId) {
			return false;
		}
		if (hasTransition(targetComponentId, sourceComponentId)) {
			throw new RuntimeException("Cannot create a cycle in the quotient graph");
		}
		long[] targetIds = transitions.get(sourceComponentId);
		if (targetIds == null) {
			targetIds = new long[16];
			targetIds[0] = 0;
			transitions.put(sourceComponentId, targetIds);
		}
		final int numTargets = (int) targetIds[0];
		for (int i = 1; i <= numTargets; i++) {
			if (targetIds[i] == targetComponentId) {
				/* transition already exists */
				return false;
			}
		}
		if (numTargets + 1 == targetIds.length) {
			final int newNumTargets = Math.max(numTargets + 1, numTargets * 3 / 2);
			targetIds = Arrays.copyOf(targetIds, newNumTargets);
			transitions.put(sourceComponentId, targetIds);
		}
		targetIds[numTargets + 1] = targetComponentId;
		targetIds[0]++;
		totalNumTransitions++;
		return true;
	}

	/**
	 * Returns true if the structure has a transition between the two components given by their id.
	 */
	public boolean hasTransition(final long sourceComponentId, final long targetComponentId) {
		/* No self-loops in the quotient graph. */
		if (sourceComponentId != targetComponentId) {
			final long[] targetIds = transitions.get(sourceComponentId);
			if (targetIds != null) {
				final int numTargets = (int) targetIds[0];
				for (int i = 1; i <= numTargets; ++i) {
					if (targetIds[i] == targetComponentId) {
						/* Found the target component. */
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Checks if a component is terminal (has no outgoing transitions). */
	public boolean isTerminal(final long componentId) {
		final long[] targetIds = transitions.get(componentId);
		return targetIds == null || targetIds.length == 0 || targetIds[0] == 0;
	}

	/** Checks if a component is terminal (has no outgoing transitions). */
	public boolean isTerminal(final Scc component) {
		return isTerminal(component.getId());
	}

	/**
	 * Adds all terminal components to the supplied list of components. Returns the number of components added.
	 */
	public int getTerminalComponents(List<Scc> terminalComponents) {
		final LongValuePairIterator<Scc> componentsIterator = components.iterator();
		int numTerminalComponents = 0;
		while (componentsIterator.hasNext()) {
			final long componentId = componentsIterator.next();
			final Scc component = componentsIterator.value();
			if (isTerminal(componentId)) {
				numTerminalComponents++;
				terminalComponents.add(component);
			}
		}
		return numTerminalComponents;
	}

	/** Returns a list of all terminal components in a list. */
	public Vector<Scc> getTerminalComponents() {
		final Vector<Scc> terminalComponents = new Vector<Scc>();
		getTerminalComponents(terminalComponents);
		return terminalComponents;
	}

	/**
	 * Combines (adds) another quotient graph (only components that do not exist here).
	 */
	public SccStructure combine(SccStructure structure) {
		if (structure == null) {
			return this;
		}

		/* combine components */
		final LongValuePairIterator<Scc> componentsIterator = structure.components.iterator();
		while (componentsIterator.hasNext()) {
			final long componentId = componentsIterator.next();
			final Scc newComponent = componentsIterator.value();
			final Scc thisComponent = components.get(componentId);
			if (thisComponent == null) {
				/* we don't have this component yet - we copy it */
				components.put(componentId, new Scc(newComponent));
			} else if (thisComponent.getSize() != newComponent.getSize()) {
				/* we have this component but it has different size - error */
				throw new RuntimeException("Cannot combine non-compatible components: Component " + componentId
						+ " exists with size " + thisComponent.getSize() + " while the new component with this id "
						+ "has size " + newComponent.getSize());
			}
		}

		/* combine transitions */
		final LongValuePairIterator<long[]> transitionsIterator = structure.transitions.iterator();
		while (transitionsIterator.hasNext()) {
			final long sourceId = transitionsIterator.next();
			final long[] targetIds = transitionsIterator.value();
			if (targetIds != null && targetIds.length > 0) {
				final int numTargets = (int) targetIds[0];
				for (int index = 1; index <= numTargets; index++) {
					/* add new transition (skip the transitions that already exist) */
					addTransition(sourceId, targetIds[index]);
				}
			}
		}

		return this;
	}

	/** Returns a map that maps a size to the number of components of that size. */
	public LongToLongHashMap componentsBySizeMap() {
		final long NullValue = -1;
		final LongToLongHashMap countComponentsBySize = new LongToLongHashMap(NullValue, 1);
		LongValuePairIterator<Scc> componentsIterator = components.iterator();
		while (componentsIterator.hasNext()) {
			componentsIterator.next();
			final Scc component = componentsIterator.value();
			final long componentSize = component.getSize();
			long numComponentsOfSize = countComponentsBySize.get(componentSize);
			if (numComponentsOfSize == NullValue) {
				numComponentsOfSize = 0;
			}
			countComponentsBySize.put(componentSize, numComponentsOfSize + 1);
		}
		return countComponentsBySize;
	}

	@Override
	public int length() {
		final int componentsLength = IOUtils.INT_BYTES + numComponents() * IOUtils.LONG_BYTES * 2;
		final int componentTransitionsLength = IOUtils.INT_BYTES + numComponentsWithTransitions()
				* (IOUtils.LONG_BYTES + IOUtils.INT_BYTES);
		final int transitionsLength = totalNumTransitions() * IOUtils.LONG_BYTES;
		return componentsLength + componentTransitionsLength + transitionsLength;
	}

	@Override
	public void write(final byte[] buf, int offset) {
		/* write components */
		final LongValuePairIterator<Scc> componentsIterator = components.iterator();
		IOUtils.writeInt(numComponents(), buf, offset);
		offset += IOUtils.INT_BYTES;
		while (componentsIterator.hasNext()) {
			/* for each component write (id, size) */
			final long componentId = componentsIterator.next();
			final long componentSize = componentsIterator.value().getSize();
			IOUtils.writeLong(componentId, buf, offset);
			offset += IOUtils.LONG_BYTES;
			IOUtils.writeLong(componentSize, buf, offset);
			offset += IOUtils.LONG_BYTES;
		}

		/* write transitions */
		IOUtils.writeInt(numComponentsWithTransitions(), buf, offset);
		offset += IOUtils.INT_BYTES;
		final LongValuePairIterator<long[]> transitionsIterator = transitions.iterator();
		while (transitionsIterator.hasNext()) {
			/* for each list of transitions write (source id, list of targets). */
			final long sourceComponentId = transitionsIterator.next();
			IOUtils.writeLong(sourceComponentId, buf, offset);
			offset += IOUtils.LONG_BYTES;
			final long[] targetIds = transitionsIterator.value();
			final int numTargets = (int) targetIds[0];
			IOUtils.writeInt(numTargets, buf, offset);
			offset += IOUtils.INT_BYTES;
			for (int i = 1; i < 1 + numTargets; ++i) {
				IOUtils.writeLong(targetIds[i], buf, offset);
				offset += IOUtils.LONG_BYTES;
			}
		}
	}

	@Override
	public void read(byte[] buf, int offset) {
		/* read components */
		final int numComponents = IOUtils.readInt(buf, offset);
		offset += IOUtils.INT_BYTES;
		for (int i = 0; i < numComponents; ++i) {
			/* read component's (id, size) */
			long componentId = IOUtils.readLong(buf, offset);
			offset += IOUtils.LONG_BYTES;
			long componentSize = IOUtils.readLong(buf, offset);
			offset += IOUtils.LONG_BYTES;
			/* create this component */
			components.put(componentId, new Scc(componentId, componentSize));
		}

		/* read transitions */
		final int numComponentsWithTransitions = IOUtils.readInt(buf, offset);
		offset += IOUtils.INT_BYTES;
		for (int i = 0; i < numComponentsWithTransitions; ++i) {
			/* read (source id, #targets, list of targets) */
			final long sourceComponentId = IOUtils.readLong(buf, offset);
			offset += IOUtils.LONG_BYTES;
			final int numTargets = IOUtils.readInt(buf, offset);
			offset += IOUtils.INT_BYTES;
			final long[] targetIds = new long[numTargets + 1];
			targetIds[0] = numTargets;
			for (int j = 1; j < numTargets + 1; ++j) {
				targetIds[j] = IOUtils.readLong(buf, offset);
				offset += IOUtils.LONG_BYTES;
			}
			transitions.put(sourceComponentId, targetIds);
			totalNumTransitions += numTargets;
		}
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(long trivialComponents) {
		final LongToLongHashMap componentsBySize = componentsBySizeMap();
		final StringBuilder sb = new StringBuilder();
		LongPairIterator iter = componentsBySize.iterator();
		if (!iter.hasNext() && trivialComponents <= 0) {
			sb.append("No components found");
		} else {
			boolean trivialPrinted = false;
			while (iter.hasNext()) {
				final long componentSize = iter.next();
				long componentCount = iter.value();
				if (componentSize == 1) {
					componentCount += trivialComponents;
					trivialPrinted = true;
				}
				sb.append("Found ");
				sb.append(componentCount);
				sb.append(" components of size ");
				sb.append(componentSize);
				if (iter.hasNext()) {
					sb.append("\n");
				}
			}
			if (!trivialPrinted && trivialComponents > 0) {
				sb.append("\nFound " + trivialComponents + " components of size 1");
			}
		}
		return sb.toString();
	}

	/**
	 * Outputs terminal components, together with nodes up to the given number of hops (depth from leaves). Stops
	 * outputting if more than the specified number of nodes were printed (graceful escape in case there is an
	 * unprintably large number of components).
	 */
	public void toDot(final String fileNameBase, final int maxPrintedNodes, final int maxDepthFromLeaves,
			final boolean divideLongId) {
		/* Compute depths for all components. */
		computeDepthsForAllComponents();

		int depthFromLeaves = 0;
		int size;
		do {
			/* output the dot file for this depth from leaves. */
			String fileName = fileNameBase + "-" + depthFromLeaves + ".dot";
			System.err.println("Writing terminal nodes (depth from leaves " + depthFromLeaves + ") to " + fileName);
			size = toDot(depthFromLeaves, fileName, divideLongId);
			System.err.println("(Written " + size + " nodes)");
			depthFromLeaves++;
		} while (depthFromLeaves < maxDepthFromLeaves && size < maxPrintedNodes);
	}

	/**
	 * Computes "reversed" depths for all scc nodes. Terminal nodes have depth 0. The depth of a node is its minimal
	 * distance to some terminal node. Works only for acyclic graphs.
	 * 
	 * Note: this implementation is inefficient but simple. The elegant recursive solution fails due to stack overflow.
	 */
	private void computeDepthsForAllComponents() {
		int componentsDone = 0, componentsDoneInIteration = 0;
		do {
			componentsDoneInIteration = 0;
			final LongValuePairIterator<Scc> componentsIterator = components.iterator();
			while (componentsIterator.hasNext()) {
				final long componentId = componentsIterator.next();
				final Scc component = componentsIterator.value();
				if (!component.isDepthSet()) {
					/* Depth not computed yet; try computing it. */
					final long[] targetIds = transitions.get(componentId);
					if (targetIds == null || targetIds.length == 0 || targetIds[0] == 0) {
						/* Terminal node: depth is 0. */
						component.setDepth(0);
						componentsDoneInIteration++;
					} else {
						/* Find minimal distance. */
						final int numTargets = (int) targetIds[0];
						boolean allDepthsSet = true;
						int minDepth = Integer.MAX_VALUE;
						for (int j = 1; j <= numTargets && allDepthsSet; ++j) {
							final long targetId = targetIds[j];
							final Scc target = components.get(targetId);
							if (!target.isDepthSet()) {
								allDepthsSet = false;
								/* not all depths were computed, we break now and will retry later. */
							} else {
								final int depth = target.getDepth();
								if (depth < minDepth) {
									minDepth = depth;
								}
							}
						}
						if (allDepthsSet) {
							/* we have minimal depth for all targets */
							component.setDepth(minDepth + 1);
							componentsDoneInIteration++;
						}
					}
				}
			}
			componentsDone += componentsDoneInIteration;
			System.err.println("computed " + componentsDone + " / " + numComponents());
		} while (componentsDone < numComponents() && componentsDoneInIteration > 0);

		if (componentsDone < numComponents()) {
			throw new RuntimeException("Not all depths computed. Graph cyclic?");
		}
	}

	/**
	 * Outputs this structure as a dot file.
	 * 
	 * @param divideLongId
	 */
	private int toDot(final int maxDepth, final String fileName, final boolean divideLongId) {
		/* compute #components to print and maximum size over all printed components (larger nodes will be represented
		 * with larger shapes) */
		long maxSize = 1;
		int printedComponents = 0;
		LongValuePairIterator<Scc> componentsIterator = components.iterator();
		while (componentsIterator.hasNext()) {
			componentsIterator.next();
			final Scc comp = componentsIterator.value();
			if (comp.getDepth() <= maxDepth) {
				printedComponents++;
				if (comp.getSize() > maxSize) {
					maxSize = comp.getSize();
				}
			}
		}

		/* write the structure, starting from terminal components */
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(new File(fileName)));
			out.write("digraph g { \n");

			/* write nodes */
			componentsIterator = components.iterator();
			while (componentsIterator.hasNext()) {
				componentsIterator.next();
				final Scc comp = componentsIterator.value();
				if (comp.getDepth() <= maxDepth) {
					out.write("   ");
					out.write(dotComponentName(comp.getId(), divideLongId));
					final long size = comp.getSize();
					if (size >= 0) {
						out.write(" [");
						writeDotParam("label", dotComponentLabel(comp, divideLongId), out, true);
						writeDotParam("shape", size == 1 ? "box" : "ellipse", out, true);
						writeDotParam("style", "filled", out, true);
						writeDotParam("fillcolor", dotComponentColor(comp, maxSize), out, false);
						out.write(" ]");
					}
					out.write(";\n");
				}
			}
			out.write("\n");

			/* write transitions */
			componentsIterator = components.iterator();
			while (componentsIterator.hasNext()) {
				final long compId = componentsIterator.next();
				final Scc comp = componentsIterator.value();
				if (comp.getDepth() <= maxDepth) {
					final long[] v = transitions.get(compId);
					if (v != null) {
						final int s1 = 1 + (int) v[0];
						for (int i = 1; i < s1; i++) {
							final Scc target = components.get(v[i]);
							out.write("   ");
							out.write(dotComponentName(comp.getId(), divideLongId));
							out.write(" -> ");
							out.write(dotComponentName(target.getId(), divideLongId));
							out.write(";\n");
						}
					}
				}
			}
			out.write("}\n");
		} catch (IOException e) {
			System.err.println("Warn: could not write to " + fileName + ": " + e.getMessage());
			System.err.println(toString());
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (Throwable t) {
				}
			}
		}

		return printedComponents;
	}

	/** Helper method to write a dot node parameter. */
	private void writeDotParam(String name, String value, BufferedWriter out, boolean hasNext) throws IOException {
		out.write(name);
		out.write("=\"");
		out.write(value);
		out.write("\"");
		if (hasNext) {
			out.write(", ");
		}
	}

	/** Returns a (unique) name of a node. */
	private String dotComponentName(final long componentId, final boolean divideLongId) {
		if (divideLongId) {
			return "SCC_" + FastIntPair.getFirst(componentId) + "_" + FastIntPair.getSecond(componentId);
		} else {
			return "SCC_" + componentId;
		}
	}

	/** Returns a (unique) name of a node. */
	private String dotComponentId(final long componentId, final boolean divideLongId) {
		if (divideLongId) {
			return "(" + FastIntPair.getFirst(componentId) + "," + FastIntPair.getSecond(componentId) + ")";
		} else {
			return String.valueOf(componentId);
		}
	}

	/**
	 * Returns a color of a dot node.
	 */
	public String dotComponentColor(Scc scc, long maxSize) {
		if (maxSize <= 1) {
			return "#FFFFFF";
		} else {
			final int hue = 255 - (int) Math.round((double) (scc.getSize() - 1) * 255.0 / (double) (maxSize - 1));
			return "#FF" + StringUtils.CharToHex((char) hue);
		}
	}

	/** Return a label of a dot node. */
	public String dotComponentLabel(final Scc scc, final boolean divideLongId) {
		final String sizeParameter = "size=" + scc.getSize();
		final String depthParameter = "depth=" + scc.getDepth();
		final String idParameter = "uid=" + dotComponentId(scc.getId(), divideLongId);
		return sizeParameter + "\\n" + depthParameter + "\\n" + idParameter;
	}

}
