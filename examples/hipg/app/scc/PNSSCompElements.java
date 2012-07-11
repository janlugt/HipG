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

package hipg.app.scc;

import hipg.Node;
import hipg.Reduce;
import hipg.app.scc.Quotient.Quotientable;
import hipg.app.utils.Scc;
import hipg.format.synthetic.SyntheticGraph;
import hipg.format.synthetic.petrinet.PetriNetStateSpace;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Vector;

public class PNSSCompElements {

	public static final class PNSSCompElementsLister extends Synchronizer {

		private final PetriNetStateSpace pnss;
		private final String[] geneNames;
		private final int geneNameMaxLen;
		private final byte[] geneMarking;
		private final String fileNameBase;
		private final ExplicitGraph<? extends Node> graph;
		private final Vector<Scc> components;

		public PNSSCompElementsLister(final ExplicitGraph<? extends Node> graph, final Vector<Scc> components,
				final String fileNameBase) {
			this.graph = graph;
			this.components = components;
			this.fileNameBase = fileNameBase;
			final SyntheticGraph sg = graph.getSyntheticGraph();
			if (!(sg instanceof PetriNetStateSpace)) {
				System.err.println("synthetic graph not a PetriNet state space (" + sg.getClass().getName() + ")");
			}
			this.pnss = (PetriNetStateSpace) sg;
			this.geneNames = pnss.getMarkingNames();
			this.geneMarking = new byte[geneNames.length];
			int ml = 10;
			for (int i = 0; i < geneNames.length; i++) {
				if (geneNames[i].length() > ml) {
					ml = geneNames[i].length();
				}
			}
			this.geneNameMaxLen = ml + 1;
			if (graph.node(0) != null) {
				if (!(graph.node(0) instanceof Quotientable)) {
					throw new RuntimeException("Not quotientable");
				}
			}
		}

		@Reduce
		public long[] Elements(long[] elements) {
			final long componentId = elements[0];
			int componentSize = (int) elements[1];
			for (int i = 0; i < graph.nodes(); i++) {
				final ExplicitLocalNode<? extends Node> node = graph.node(i);
				final Quotientable q = (Quotientable) node;
				if (q.getComponentId() == componentId) {
					int index = 2 + componentSize;
					if (index < elements.length) {
						elements[index] = node.asReference();
						componentSize++;
					} else {
						System.err.println("cannot add all elements in component " + componentId + " of size "
								+ componentSize + " !! some bug in the decomposer??");
					}
				}
			}
			elements[1] = componentSize;
			return elements;
		}

		@Override
		public void run() {
			print("Will compute elements of " + components.size() + " components");
			for (int n = 0; n < components.size(); n++) {
				final Scc comp = components.get(n);
				final long id = comp.getId();
				final long size = comp.getSize();
				print("Computing elements of component " + id + " of size " + size);
				if (size + 2 > Integer.MAX_VALUE)
					throw new RuntimeException("Component " + id + " too big");
				final long[] empty = new long[2 + (int) size];
				empty[0] = id;
				empty[1] = 0;
				final long[] AllElements = Elements(empty);
				if (Runtime.getRank() == 0) {
					writeSCC(AllElements, fileNameBase + "-" + n + ".txt", id, size);
				}
			}
			print("Elements computed");
		}

		private static void print(Object msg) {
			System.out.print(Runtime.getRank() + ": ");
			System.out.println(msg);
			System.out.flush();
		}

		private void writeSCC(final long[] elements, final String fileName, final long compId, final long compSize) {

			System.err.println("Writing component compId=(" + compId + ") of size " + compSize + " to " + fileName);

			BufferedWriter out = null;
			try {
				out = new BufferedWriter(new FileWriter(new File(fileName)));

				/*
				 * write scc metadata
				 */
				out.write("# Created on:   " + new Date() + " by HipG\n");
				out.write("# Created from: " + pnss + "\n");
				out.write("# SCC id:       " + compId + "\n");
				out.write("# SCC size:     " + compSize + "\n");

				/*
				 * write gene names
				 */
				for (int i = 0; i < geneNames.length; i++) {
					writeVal(out, geneNames[i]);
				}
				out.write("\n");

				/*
				 * write elements in the SCC
				 */
				final int len = elements.length;
				for (int i = 2; i < len; i++) {
					final long e = elements[i];
					final int owner = ExplicitNodeReference.getOwner(e);
					final int id = ExplicitNodeReference.getId(e);
					final long n = graph.getPartition().back(owner, id);
					pnss.nodeIndexToMarking(n, geneMarking);
					for (int j = 0; j < geneMarking.length; j++) {
						writeVal(out, String.valueOf(geneMarking[j]));
					}
					out.write("\n");
				}

			} catch (Throwable t) {
				System.err.println("Writing to file " + fileName + " failed: " + t.getMessage());
				t.printStackTrace();
			} finally {
				if (out != null) {
					try {
						out.close();
					} catch (Throwable t1) {
					}
				}
			}
		}

		private void writeVal(BufferedWriter out, String s) throws IOException {
			if (s == null)
				s = "null";
			out.write(s);
			final int c = geneNameMaxLen - s.length();
			for (int j = 0; j < c; j++) {
				out.write(" ");
			}
		}

	}
}
