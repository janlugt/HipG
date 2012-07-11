/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam
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
 */

package hipg.app.scc;

import hipg.BarrierAndReduce;
import hipg.Config;
import hipg.Node;
import hipg.Reduce;
import hipg.app.utils.Histogram;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.util.Arrays;

import myutils.ConversionUtils;

public class OptimFB {

	private static final int SZ_FnB = 0;
	private static final int SZ_FmB = 1;
	private static final int SZ_BmF = 2;
	private static final int SZ_VmFmB = 3;
	private static final int PV_FmB = 4;
	private static final int PV_BmF = 5;
	private static final int PV_VmFmB = 6;
	private static final int NOT_VISITED = -1;
	private static final long NULL_PIVOT = ExplicitNodeReference.NULL_NODE;

	private static final boolean verbose = false;
	private static final boolean timing = true;
	private static long searchTime = 0L;
	private static Histogram SCCs = new Histogram();

	public static interface MyNode extends Node {
		public void fwd(OpFB fb);

		public void bwd(OpFB fb);
	}

	public static class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {
		private int labelF = NOT_VISITED, labelB = NOT_VISITED;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		public void fwd(OpFB fb) {
			if (labelF == fb.labelF && (labelB == fb.labelB || labelB == fb.newLabelB)) {
				labelF = fb.newLabelF;
				fb.Fsize++;
				if (labelB == fb.newLabelB) {
					fb.FnBsize++;
					if (fb.BmFpivot == this)
						fb.BmFpivot = null;
				} else {
					if (fb.FmBpivot == null)
						fb.FmBpivot = this;
					if (fb.VmFmBpivot == this)
						fb.VmFmBpivot = null;
					if (fb.VmFmBpivot == null) {
						for (int j = 0; hasInNeighbor(j); j++) {
							if (isNeighborLocal(j)) {
								MyLocalNode n = (MyLocalNode) localInNeighbor(j);
								if (n.labelF == fb.labelF && n.labelB == fb.labelB) {
									fb.VmFmBpivot = n;
									break;
								}
							}
						}
					}
				}
				for (int j = 0; hasNeighbor(j); j++)
					neighbor(j).fwd(fb);
			}
		}

		public void bwd(OpFB fb) {
			if ((labelF == fb.labelF || labelF == fb.newLabelF) && labelB == fb.labelB) {
				labelB = fb.newLabelB;
				fb.Bsize++;
				if (labelF != fb.labelF) {
					fb.FnBsize++;
					if (fb.FmBpivot == this)
						fb.FmBpivot = null;
				} else {
					if (fb.BmFpivot == null)
						fb.BmFpivot = this;
					if (fb.VmFmBpivot == this)
						fb.VmFmBpivot = null;
					if (fb.VmFmBpivot == null) {
						for (int j = 0; hasNeighbor(j); j++) {
							if (isNeighborLocal(j)) {
								MyLocalNode n = (MyLocalNode) localNeighbor(j);
								if (n.labelF == fb.labelF && n.labelB == fb.labelB) {
									fb.VmFmBpivot = n;
									break;
								}
							}
						}
					}
				}
				for (int j = 0; hasInNeighbor(j); j++)
					inNeighbor(j).bwd(fb);
			}
		}
	}

	public static class OpFB extends Synchronizer {

		private final ExplicitGraph<MyNode> g;
		private final int labelF, labelB;
		private long Fsize, Bsize, FnBsize;
		private final long Vsize;
		private int newLabelF, newLabelB;
		private final int rank = Runtime.getRank();
		private MyLocalNode FmBpivot, BmFpivot, VmFmBpivot;
		private final MyLocalNode pivot;

		public OpFB(ExplicitGraph<MyNode> g, int labelF, int labelB, long Vsize, long pivot) {
			this.g = g;
			this.labelF = labelF;
			this.labelB = labelB;
			this.Vsize = Vsize;
			if (ExplicitNodeReference.isLocal(pivot)) {
				this.pivot = (MyLocalNode) g.node(ExplicitNodeReference.getId(pivot));
				if (this.pivot.labelF != labelF || this.pivot.labelB != labelB)
					throw new RuntimeException("Pivot with labels " + this.pivot.labelF + "," + this.pivot.labelB
							+ " while expected " + labelF + "," + labelB);
			} else
				this.pivot = null;
		}

		@BarrierAndReduce
		public long[] GlobalSizesAndPivots(long[] sizesAndPivots) {

			// System.out.println(Runtime.getRank()
			// + ": pre GlobalSizesAndPivots: "
			// + Arrays.toString(sizesAndPivots));

			sizesAndPivots[SZ_FnB] += FnBsize;
			sizesAndPivots[SZ_FmB] += Fsize - FnBsize;
			sizesAndPivots[SZ_BmF] += Bsize - FnBsize;
			sizesAndPivots[SZ_VmFmB] += Vsize - (Fsize + Bsize - FnBsize);

			if (sizesAndPivots[PV_FmB] == NULL_PIVOT && FmBpivot != null)
				sizesAndPivots[PV_FmB] = FmBpivot.asReference();
			if (sizesAndPivots[PV_BmF] == NULL_PIVOT && BmFpivot != null)
				sizesAndPivots[PV_BmF] = BmFpivot.asReference();
			if (sizesAndPivots[PV_VmFmB] == NULL_PIVOT && VmFmBpivot != null)
				sizesAndPivots[PV_VmFmB] = VmFmBpivot.asReference();

			// System.out.println(Runtime.getRank()
			// + ": post GlobalSizesAndPivots: "
			// + Arrays.toString(sizesAndPivots));

			return sizesAndPivots;
		}

		@Reduce
		public long[] SearchPivots(long[] sizesAndPivots) {

			// System.out.println(Runtime.getRank() + ": pre SearchPivots: "
			// + Arrays.toString(sizesAndPivots));

			boolean needsFmB = (sizesAndPivots[SZ_FmB] > 0 && sizesAndPivots[PV_FmB] == NULL_PIVOT);
			boolean needsBmF = (sizesAndPivots[SZ_BmF] > 0 && sizesAndPivots[PV_BmF] == NULL_PIVOT);
			boolean needsVmFmB = (sizesAndPivots[SZ_VmFmB] > 0 && sizesAndPivots[PV_VmFmB] == NULL_PIVOT);

			if (needsFmB || needsBmF || needsVmFmB) {
				long start = (timing ? System.nanoTime() : 0L);
				for (int i = 0; i < g.nodes(); i++) {
					MyLocalNode n = (MyLocalNode) g.node(i);
					if (needsFmB && n.labelF == newLabelF && n.labelB == labelB) {
						needsFmB = false;
						sizesAndPivots[PV_FmB] = n.asReference();
						if (!needsFmB && !needsBmF && !needsVmFmB)
							break;
					}
					if (needsBmF && n.labelF == labelF && n.labelB == newLabelB) {
						needsBmF = false;
						sizesAndPivots[PV_BmF] = n.asReference();
						if (!needsFmB && !needsBmF && !needsVmFmB)
							break;
					}
					if (needsVmFmB && n.labelF == labelF && n.labelB == labelB) {
						needsVmFmB = false;
						sizesAndPivots[PV_VmFmB] = n.asReference();
						if (!needsFmB && !needsBmF && !needsVmFmB)
							break;
					}
				}
				if (timing) {
					searchTime += System.nanoTime() - start;
				}
			}

			// System.out.println(Runtime.getRank() + ": post SearchPivots: "
			// + Arrays.toString(sizesAndPivots));

			return sizesAndPivots;
		}

		private void print(String msg) {
			if (verbose) {
				System.out.println(rank + ": synchronizer " + name() + " [labelF=" + labelF + " labelB=" + labelB
						+ " newLabelF=" + newLabelF + " newLabelB=" + newLabelB + "] :: " + msg);
			}
		}

		public void run() {

			newLabelF = 2 * getId();
			newLabelB = newLabelF + 1;
			barrier();

			if (pivot != null) {
				print("starts on pivot " + pivot);
				pivot.fwd(this);
				pivot.bwd(this);
			}

			long[] sizes = new long[] { 0, 0, 0, 0, NULL_PIVOT, NULL_PIVOT, NULL_PIVOT };
			sizes = GlobalSizesAndPivots(sizes);

			if (pivot != null)
				print("Computed GlobalSizesAndPivots: " + Arrays.toString(sizes));

			if (pivot != null)
				print("found SCC of size " + sizes[SZ_FnB]);

			SCCs.add((int) sizes[SZ_FnB]);

			if ((sizes[SZ_FmB] > 0 && sizes[PV_FmB] == NULL_PIVOT)
					|| (sizes[SZ_BmF] > 0 && sizes[PV_BmF] == NULL_PIVOT)
					|| (sizes[SZ_VmFmB] > 0 && sizes[PV_VmFmB] == NULL_PIVOT)) {
				sizes = SearchPivots(sizes);
				if (pivot != null)
					print("SearchPivots: " + Arrays.toString(sizes));
			}

			if (sizes[SZ_FmB] > 0)
				spawn(new OpFB(g, newLabelF, labelB, Fsize - FnBsize, sizes[PV_FmB]));
			if (sizes[SZ_BmF] > 0)
				spawn(new OpFB(g, labelF, newLabelB, Bsize - FnBsize, sizes[PV_BmF]));
			if (sizes[SZ_VmFmB] > 0)
				spawn(new OpFB(g, labelF, labelB, Vsize - (Fsize + Bsize - FnBsize), sizes[PV_VmFmB]));
		}
	}

	private static void print(String msg) {
		if (Runtime.getRank() == 0)
			System.err.println(msg);
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			System.err.println(OpFB.class.getName() + " <graph>");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}
		// read graph
		ExplicitGraph<MyNode> g = hipg.format.GraphIO.readUndirected(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		print("Graph read");

		// run OptimFB
		OpFB fb = new OpFB(g, NOT_VISITED, NOT_VISITED, g.nodes(), g.root());
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(fb);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;

		// print results
		print(SCCs.toString());
		print("OptimFB on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
		if (timing)
			print("Search took " + ConversionUtils.ns2sec(searchTime) + "s");
	}

}
