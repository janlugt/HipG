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
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;

import java.io.PrintStream;

import myutils.ConversionUtils;

public final class owctypa3 {

	private static final boolean vverbose = false;
	private static final boolean verbose = false;
	private static final boolean check121 = false;

	private static final boolean OUTDEGREE_OPTIM = false;
	private static final boolean RECOMPUTE_INDEGREE = true;
	private static final boolean START_NESTED_ALGS = true;
	private static final int NESTED_ALGS_DEPTH_LIMIT = 50;
	private static final boolean PARTIAL_COMPUTATION = false;
	private static final int PARTIAL_COMPUTATION_STATES_LIMIT = 10000;
	private static final boolean DISPLAY_PROGRESS = false;
	private static int MAX_STATES = 0;
	private static final int MAX_OWCTY_DEPTH = 50;

	private static int net_id, cluster_size;
	private static int max_owcty_depth;
	private static int nsccs;
	private static int scc_structure_size[];
	private static int scc_structure_count[];
	private static int ntrivsccs;
	private static final int global_indegree[][] = new int[MAX_OWCTY_DEPTH][];
	private static final int global_found[][] = new int[MAX_OWCTY_DEPTH][];
	private static final int prev_global_found[][] = new int[MAX_OWCTY_DEPTH][];
	private static final int global_visited[][] = new int[MAX_OWCTY_DEPTH][];

	private static final class my_queue_t {
		private int[] q;
		private int size;
		private int first;
		private int last;
		private int max_size;
		private int depth;
		private int type;

		private my_queue_t() {
		}

		private final void qinit() {
			first = 0;
			last = 0;
			size = 0;
		}

		private final int qpop(int id) {
			if (size <= 0) {
				fprintf(System.err, "qpop(, %d): Queue underflow %d   %d!\n", id, depth, type);
				fflush(System.err);
				throw new RuntimeException("Queue underflow " + depth + " " + type + " " + size + " " + max_size);
			}
			int ret = q[first];
			first = (first + 1) % max_size;
			size--;
			return ret;
		}

		private final void qpush(int elem, int id) {
			if (size >= max_size) {
				fprintf(System.err, "qpush(, %d): Queue overflow %d %d! (q size %d,q max size %d)\n", id, depth, type,
						size, max_size);
				fflush(System.err);
				throw new RuntimeException("Queue overflow: " + id + " " + " q size " + size + " < " + max_size);
			}
			q[last] = elem;
			last = (last + 1) % max_size;
			size++;
		}

		private final void qalloc(int max_size, int depth, int type) {
			this.q = new int[max_size];
			this.max_size = max_size;
			this.depth = depth;
			this.type = type;
		}
	};

	private enum alg_type_t {
		explorer, trimmer
	};

	public static interface SccNode extends Node {
		public void explore(alg_record_t ar);

		public void trim(alg_record_t ar);

		public void bwd(alg_record_t ar);

		public void fwd(alg_record_t ar);
	}

	public static final class SccLocalNode extends ExplicitLocalNode<SccNode> implements SccNode {

		public SccLocalNode(ExplicitGraph<SccNode> graph, int reference) {
			super(graph, reference);
		}

		public final void explore(alg_record_t ar) {
			if (prev_global_found[ar.depth] == null || prev_global_found[ar.depth][reference] == ar.getId()) {
				if (RECOMPUTE_INDEGREE && ar.depth != 0)
					global_indegree[ar.depth][reference]++;
				if (global_found[ar.depth][reference] == 0) {
					if (vverbose)
						System.out.println(Runtime.getRank() + ": explore alg=" + ar.getId() + " node=" + reference
								+ " global_visited=" + global_visited[ar.depth][reference] + " global_indegree="
								+ global_indegree[ar.depth][reference]);
					ar.queue_two.qpush(reference, 13);
					global_found[ar.depth][reference] = ar.iteration;
					for (int j = 0; hasNeighbor(j); j++)
						neighbor(j).explore(ar);
				}
			}
		}

		public final void trim(alg_record_t ar) {
			if ((prev_global_found[ar.depth] == null || prev_global_found[ar.depth][reference] == ar.getId())
					&& global_found[ar.depth][reference] == ar.iteration) {
				if (vverbose)
					System.out.println(Runtime.getRank() + ": trim alg=" + ar.getId() + " node=" + reference
							+ " global_visited=" + global_visited[ar.depth][reference] + " global_indegree="
							+ global_indegree[ar.depth][reference]);
				if (global_visited[ar.depth][reference] == 0) {
					ar.queue_two.qpush(reference, 14);
					global_visited[ar.depth][reference] = 1;
				}
				if (global_indegree[ar.depth][reference] > 0) {
					global_indegree[ar.depth][reference]--;
					if (global_indegree[ar.depth][reference] == 0) {
						for (int j = 0; hasNeighbor(j); j++)
							neighbor(j).trim(ar);
					}
				}
			}
		}

		public final void bwd(alg_record_t ar) {
			if ((prev_global_found[ar.depth] == null || prev_global_found[ar.depth][reference] == ar.getId())
					&& global_found[ar.depth][reference] == ar.iteration) {
				if (global_visited[ar.depth][reference] == 0) {
					if (vverbose)
						System.out.println(Runtime.getRank() + ": bwd alg=" + ar.getId() + " node=" + reference
								+ " visited=" + global_visited[ar.depth][reference]);
					ar.queue_one.qpush(reference, 16);
					global_visited[ar.depth][reference] = 1;
					for (int j = 0; hasInNeighbor(j); j++)
						inNeighbor(j).bwd(ar);
				}
			}
		}

		public final void fwd(alg_record_t ar) {
			if ((prev_global_found[ar.depth] == null || prev_global_found[ar.depth][reference] == ar.getId())
					&& global_found[ar.depth][reference] == ar.iteration) {
				if (vverbose)
					System.out.println(Runtime.getRank() + ": fwd alg=" + ar.getId() + " node=" + reference
							+ " global_visited=" + global_visited[ar.depth][reference] + " global_indegree="
							+ global_indegree[ar.depth][reference]);
				if (global_visited[ar.depth][reference] == 0) {
					global_indegree[ar.depth][reference]--;
					if (global_indegree[ar.depth][reference] < 0) {
						printf("%d: R1NEGATIVE INDEGREE, depth = %d, s2 = %d\n", net_id, ar.depth, reference);
						fflush(System.out);
					}
					ar.queue_two.qpush(reference, 17);
					global_visited[ar.depth][reference] = 2;
				} else if (global_visited[ar.depth][reference] == 2) {
					global_indegree[ar.depth][reference]--;
					if (global_indegree[ar.depth][reference] < 0) {
						printf("%d: R2NEGATIVE INDEGREE, depth = %d, s2 = %d\n", net_id, ar.depth, reference);
						fflush(System.out);
					}

				}
			}
		}

	}

	public static final class alg_record_t extends hipg.runtime.Synchronizer {
		private final ExplicitGraph<SccNode> g;
		private final alg_type_t algtype;

		private boolean ihaveunexploredvertex;
		private int first_unexplored_idx;
		private int first_unexplored_queue_idx;
		private final int depth;
		private int iteration;
		private int subiteration;
		private int candidate_net_id;
		private int min_candidate_net_id;
		private int sum_qone_size;
		private int sum_qtwo_size;
		private int sum_qsource_size;
		private final my_queue_t source_queue;
		private final my_queue_t queue_one;
		private final my_queue_t queue_two;

		// private int update_fun = fun_sync_struct_none;

		public alg_record_t(ExplicitGraph<SccNode> g, alg_type_t algtype, int depth) {
			this.g = g;
			source_queue = new my_queue_t();
			queue_one = new my_queue_t();
			queue_two = new my_queue_t();
			this.depth = depth;
			this.algtype = algtype;
		}

		@Override
		public String toString() {
			return "id=" + getId() + "type=" + algtype.toString() + ",unexpl.vert=" + ihaveunexploredvertex + ",depth="
					+ depth + ",iteration=" + iteration + ",subiteration=" + subiteration;
		}

		// public final int propagate(int ss) {
		// // System.out.println(net_id + " propagate " + update_fun);
		// switch (update_fun) {
		// case fun_sync_struct_none:
		// break;
		// case fun_sync_struct_min_candidate:
		// process_sync_struct_min_candidate(ss);
		// break;
		// case fun_sync_struct_sum_qfwd:
		// process_sync_struct_sum_qfwd(ss);
		// break;
		// case fun_sync_struct_sum_qbwd:
		// process_sync_struct_sum_qbwd(ss);
		// break;
		// case fun_sync_struct_sum_qsource:
		// process_sync_struct_sum_qsource(ss);
		// break;
		// default:
		// throw new RuntimeException("Unknown sync struct function: "
		// + update_fun);
		// }
		// return ss;
		// }

		// @BarrierAndReduce2
		// private int updateBarr(int ss) {
		// return doUpdate(ss);
		// }
		//
		// @Reduce
		// private int updateRed(int ss) {
		// return doUpdate(ss);
		// }
		//
		// private int doUpdate(int ss) {
		// switch (update_fun) {
		// case fun_sync_struct_min_candidate:
		// return update_sync_struct_min_candidate(ss);
		// case fun_sync_struct_sum_qfwd:
		// return update_sync_struct_sum_qfwd(ss);
		// case fun_sync_struct_sum_qbwd:
		// return update_sync_struct_sum_qbwd(ss);
		// case fun_sync_struct_sum_qsource:
		// return update_sync_struct_sum_qsource(ss);
		// default:
		// throw new RuntimeException("Unknown sync struct function: "
		// + update_fun);
		// }
		// }

		@Reduce
		public final int update_sync_struct_min_candidate(int value) {
			// System.out.println(net_id + " update_sync_struct_min_candidate "
			// + candidate_net_id);
			return imin(value, candidate_net_id);
		}

		@BarrierAndReduce
		public final int update_sync_struct_sum_qfwd(int value) {
			// System.out.println(net_id + " update " + update_fun +
			// " with val "+ queue_one.size);
			return value + queue_one.size;
		}

		@BarrierAndReduce
		public final int update_sync_struct_sum_qbwd(int value) {
			// System.out.println(net_id + " update_sync_struct_sum_qbwd "
			// + +queue_two.size);
			return value + queue_two.size;
		}

		@BarrierAndReduce
		public final int update_sync_struct_sum_qsource(int value) {
			// System.out.println(net_id + " update " + update_fun +
			// " with val "+ source_queue.size);
			return value + source_queue.size;
		}

		private final void process_sync_struct_min_candidate(int value) {
			min_candidate_net_id = value;
		}

		private final void process_sync_struct_sum_qfwd(int value) {
			sum_qone_size = value;
		}

		private final void process_sync_struct_sum_qbwd(int value) {
			sum_qtwo_size = value;
		}

		private final void process_sync_struct_sum_qsource(int value) {
			sum_qsource_size = value;
		}

		int bwd_fill = 0;

		@Override
		public void run() {
			if (algtype == alg_type_t.explorer) {

				/* explorer */

				if (verbose && net_id == 0)
					System.out.println(net_id + ": Alg=" + getId() + " starting explorer father=" + getFatherId()
							+ " source size=" + source_queue.size);
				if (prev_global_found[depth] != null) {
					for (int i = 0; i < source_queue.size; i++) {
						int s1 = source_queue.qpop(3);
						source_queue.qpush(s1, 7);
						prev_global_found[depth][s1] = getId();
					}
				}

				while (true) {

					// search for vertex
					if (ihaveunexploredvertex && global_found[depth][first_unexplored_idx] != 0) {
						ihaveunexploredvertex = false;
					}
					while (!ihaveunexploredvertex && first_unexplored_queue_idx < source_queue.size) {
						first_unexplored_idx = source_queue.qpop(7);
						// System.out.println("checking first unexplored vertex: "
						// + ar.first_unexplored_idx);
						source_queue.qpush(first_unexplored_idx, 20);
						first_unexplored_queue_idx++;
						if (global_found[depth][first_unexplored_idx] == 0) {
							if (!PARTIAL_COMPUTATION || first_unexplored_queue_idx < PARTIAL_COMPUTATION_STATES_LIMIT) {
								if (!OUTDEGREE_OPTIM || depth > 0 || g.node(first_unexplored_idx).outdegree() > 0) {
									ihaveunexploredvertex = true;
									break;
								} else {
									ntrivsccs++;
									global_found[depth][first_unexplored_idx] = iteration;
								}
							}
						}
					}
					queue_one.qinit();
					queue_two.qinit();
					candidate_net_id = ihaveunexploredvertex ? net_id : cluster_size;
					// update(ar.ihaveunexploredvertex ? net_id : cluster_size);

					process_sync_struct_min_candidate(update_sync_struct_min_candidate(cluster_size));

					// onsynchronized: as_start
					if (min_candidate_net_id >= cluster_size) {
						// vertex not found
						if (verbose && net_id == 0)
							System.out.println(net_id + ": Alg=" + getId() + " no global vertex found, explorer done");
						return;
					}

					// vertex found
					// ar.queue_one.qinit();
					// ar.queue_two.qinit();
					// update_fun = fun_sync_struct_sum_qbwd;
					if (min_candidate_net_id == net_id) {
						if (verbose)
							System.out.println(net_id + ": Alg=" + getId() + " initializing explore from vertex "
									+ first_unexplored_idx);

						global_found[depth][first_unexplored_idx] = iteration;
						// ar.queue_one.qpush(ar.first_unexplored_idx, 1);
						queue_two.qpush(first_unexplored_idx, 1);
						// explore
						SccLocalNode s = (SccLocalNode) g.node(first_unexplored_idx);

						for (int j = 0; s.hasNeighbor(j); j++)
							s.neighbor(j).explore(this);
					}
					// compute size of the explored chunk
					// update(0);
					process_sync_struct_sum_qbwd(update_sync_struct_sum_qbwd(0));

					// onsynchronized: as_explore
					if (verbose && net_id == 0)
						System.out.println(net_id + ": Alg=" + getId() + " explored chunk of size " + sum_qtwo_size
								+ (check121 && sum_qtwo_size % 121 != 0 ? "!!!!!!!!!!" : ""));

					if (START_NESTED_ALGS && depth < NESTED_ALGS_DEPTH_LIMIT) {
						alg_record_t newar = new alg_record_t(g, alg_type_t.trimmer, depth + 1);
						newar.iteration = 1;
						// newar.update_fun = fun_sync_struct_sum_qsource;
						if (max_owcty_depth < newar.depth) {
							max_owcty_depth = newar.depth;
							prev_global_found[newar.depth] = new int[MAX_STATES];
							global_visited[newar.depth] = new int[MAX_STATES];
							global_found[newar.depth] = new int[MAX_STATES];
							global_indegree[newar.depth] = new int[MAX_STATES];
						}
						newar.source_queue.qalloc(sum_qtwo_size, newar.depth, 0);
						newar.source_queue.qinit();
						newar.queue_one.qalloc(sum_qtwo_size, newar.depth, 1);
						newar.queue_one.qinit();
						newar.queue_two.qalloc(sum_qtwo_size, newar.depth, 2);
						newar.queue_two.qinit();
						for (int i = 0; i < queue_two.size; i++) {
							int s1 = queue_two.qpop(100);
							queue_two.qpush(s1, 101);
							newar.source_queue.qpush(s1, 102);
							// prev_global_found[newar.depth][s1] =
							// newar.getId();
							global_found[newar.depth][s1] = newar.iteration;
							global_indegree[newar.depth][s1] = global_indegree[depth][s1];
						}
						if (min_candidate_net_id == net_id) {
							global_visited[newar.depth][first_unexplored_idx] = 1;
							if (global_indegree[newar.depth][first_unexplored_idx] == 0) {
								newar.queue_one.qpush(first_unexplored_idx, 104);
								newar.queue_two.qpush(first_unexplored_idx, 105);
							} else {
								newar.queue_two.qpush(first_unexplored_idx, 106);
							}
						}
						spawn(newar);

						iteration++;
						queue_one.qinit();
						queue_two.qinit();
						// update_fun = fun_sync_struct_min_candidate;
					}

				}
			} else {

				/* trimmer */

				int alg_id = getId();

				if (verbose && net_id == 0)
					System.out.println(net_id + ": Alg=" + getId() + " starting trimmer father=" + getFatherId());
				for (int i = 0; i < source_queue.size; i++) {
					int s1 = source_queue.qpop(100);
					source_queue.qpush(s1, 101);
					prev_global_found[depth][s1] = alg_id;
				}

				while (true) {
					while (queue_one.size > 0) {
						int s1 = queue_one.qpop(9);
						queue_two.qpush(s1, 1355354);
						if (global_indegree[depth][s1] == 0) {
							SccLocalNode s = (SccLocalNode) g.node(s1);
							for (int j = 0; s.hasNeighbor(j); j++)
								s.neighbor(j).trim(this);
						}
					}
					// compute sum_qsource
					// update(0);
					process_sync_struct_sum_qsource(update_sync_struct_sum_qsource(0));
					// as_trim: onsynchronized : synchronization after
					// ar.queue_one.qinit();
					if (DISPLAY_PROGRESS && alg_id > 0) {
						printf("%d: BEFOREBWD: alg_id = %d, qtwo_size = %d\n", net_id, alg_id, queue_two.size);
						fflush(System.out);
					}
					int trimmed = 0;
					int reached = 0;

					while (queue_two.size > 0) {
						int s1 = queue_two.qpop(1);
						if (global_indegree[depth][s1] == 0) {
							ntrivsccs++;
							trimmed++;
						} else {
							reached++;
							// ar.queue_one.qpush(s1, 6);
							// ar.queue_two.qpush(s1, 25);
							queue_one.qpush(s1, 25);
							SccLocalNode s = (SccLocalNode) g.node(s1);
							for (int j = 0; s.hasInNeighbor(j); j++)
								s.inNeighbor(j).bwd(this);

							if (DISPLAY_PROGRESS && alg_id > 0) {
								printf("%d: alg_id = %d, bwdseed = %d\n", net_id, alg_id, s1);
								fflush(System.out);
							}
						}
					}
					// ar.update_fun = sync_struct_fun.fun_sync_struct_sum_qbwd;
					// update_fun = fun_sync_struct_sum_qfwd;
					if (DISPLAY_PROGRESS) {
						printf("%d: BWD %d\n", net_id, alg_id);
						fflush(System.out);
					}
					// while (ar.queue_one.size > 0) {
					// int s1 = ar.queue_one.qpop(10);
					// ar.queue_two.qpush(s1, 25);
					// SccLocalNode s = (SccLocalNode) g.node(s1);
					// for (SccNode n : s.inNeighbors())
					// call_bwd(n, this);
					// }
					// compute sum_qbwd (queueu_two.size)
					// actually sum_qfwd (queue_one.size) after changing
					// update(0);
					process_sync_struct_sum_qfwd(update_sync_struct_sum_qfwd(0));

					int bwd_size = sum_qone_size;
					if (verbose && net_id == 0)
						System.out.println(net_id + ": Alg=" + getId() + " computed bwd of size " + bwd_size
								+ " (of chunk of size " + sum_qsource_size + ")");
					bwd_fill += bwd_size;
					if (check121 && bwd_size % 121 != 0)
						System.out.println(net_id + ":Alg=" + getId() + " bwd size " + bwd_size
								+ " is not multiplicity of 121 !!!!!!!!!!!!!!!!!!!!!!!!!! locall " + queue_one.size);
					// as_bwd: onsynchronized
					// if (ar.sum_qsource_size == ar.sum_qtwo_size) {
					if (sum_qsource_size == bwd_size) {
						nsccs++;
						int i = 0;
						while (scc_structure_count[i] != 0) {
							if (scc_structure_size[i] == sum_qsource_size) {
								scc_structure_count[i]++;
								break;
							}
							i++;
						}
						if (scc_structure_count[i] == 0) {
							scc_structure_size[i] = sum_qsource_size;
							scc_structure_count[i]++;
						}
						if (verbose)
							System.out.println(net_id + ": Alg=" + getId() + " found SCC of size " + sum_qsource_size);
						if (DISPLAY_PROGRESS) {
							printf("%d: %d DETECTED COMPONENT OF SIZE %d\n", net_id, alg_id, sum_qsource_size);
							fflush(System.out);
						}

						return;
					}

					if (START_NESTED_ALGS && depth < NESTED_ALGS_DEPTH_LIMIT && bwd_size > 0) {

						alg_record_t newar = new alg_record_t(g, alg_type_t.explorer, depth + 1);
						newar.iteration = 1;
						// newar.update_fun = fun_sync_struct_min_candidate;

						if (max_owcty_depth < newar.depth) {
							max_owcty_depth = newar.depth;
							prev_global_found[newar.depth] = new int[MAX_STATES];
							global_visited[newar.depth] = new int[MAX_STATES];
							global_found[newar.depth] = new int[MAX_STATES];
							global_indegree[newar.depth] = new int[MAX_STATES];

						}
						newar.source_queue.qalloc(bwd_size, newar.depth, 0);
						newar.source_queue.qinit();
						newar.queue_one.qalloc(bwd_size, newar.depth, 1);
						newar.queue_one.qinit();
						newar.queue_two.qalloc(bwd_size, newar.depth, 2);
						newar.queue_two.qinit();
						for (int i = 0; i < queue_one.size; i++) {
							int s1 = queue_one.qpop(3);
							queue_one.qpush(s1, 7);
							newar.source_queue.qpush(s1, 8);
							if (!RECOMPUTE_INDEGREE)
								global_indegree[newar.depth][s1] = global_indegree[depth][s1];
						}
						spawn(newar);
					}
					// ar.queue_one.qinit();
					// ar.queue_one.qinit();
					// ar.update_fun = sync_struct_fun.fun_sync_struct_sum_qfwd;
					// update_fun = fun_sync_struct_sum_qbwd;
					if (DISPLAY_PROGRESS) {
						printf("%d: FWD %d\n", net_id, alg_id);
						fflush(System.out);
					}
					// while (ar.queue_two.size > 0) {
					while (queue_one.size > 0) {
						// int s1 = ar.queue_two.qpop(12);
						int s1 = queue_one.qpop(12);
						SccLocalNode s = (SccLocalNode) g.node(s1);
						for (int j = 0; s.hasNeighbor(j); j++)
							s.neighbor(j).fwd(this);
					}
					// compute sum_qfwd (sum of queue_one.size)
					// actually sum_qbwd
					// update(0);

					process_sync_struct_sum_qbwd(update_sync_struct_sum_qbwd(0));

					// as_fwd: onsynchronized
					if (verbose && net_id == 0)
						System.out.println(net_id + ": Alg=" + getId() + " computed fwd of size " + sum_qtwo_size
								+ ". Progress " + bwd_fill + "/" + sum_qsource_size);
					if (sum_qtwo_size == 0) {
						if (bwd_fill != sum_qsource_size)
							System.out.println("!!!!! problem sum of found bwd sets " + bwd_fill
									+ " differs from original source size " + sum_qsource_size
									+ " when found empty fwd ");
						return;
					}
					int orig_qsize = queue_two.size;
					for (int i = 0; i < orig_qsize; i++) {
						int s1 = queue_two.qpop(4);
						if (global_indegree[depth][s1] == 0)
							queue_one.qpush(s1, 10);
						else
							queue_two.qpush(s1, 10235235);
						if (DISPLAY_PROGRESS && alg_id > 0) {
							printf("%d: alg_id = %d, NEW bwdseed = %d, qtwo_size = %d\n", net_id, alg_id, s1,
									queue_two.size);
							fflush(System.out);
						}
					}
					// update_fun = fun_sync_struct_none;
					if (DISPLAY_PROGRESS) {
						printf("%d: TRIM %d\n", net_id, alg_id);
						fflush(System.out);
					}
					subiteration++;
					barrier();
				}
			}
		}

	}

	private static final int imin(int a, int b) {
		return (a < b ? a : b);
	}

	private static final void printf(String format, Object... args) {
		System.out.printf(format, args);
	}

	private static final void fprintf(PrintStream stream, String format, Object... args) {
		stream.printf(format, args);
	}

	private static final void fflush(PrintStream stream) {
		stream.flush();
	}

	public static void main(String[] args) throws GraphCreationException {

		if (args.length < 2) {
			System.err.println(owctypa3.class.getName() + " <graph>");
			System.err.println("where graph can be specifiec as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}
		String format = args[0];
		String path = args[1];
		int poolSize = Config.POOLSIZE;

		/* read graph */
		if (Runtime.getRank() == 0)
			System.out.println("Reading graph " + path);
		long readingStart = System.nanoTime();
		final ExplicitGraph<SccNode> g = hipg.format.GraphIO.readUndirected(SccLocalNode.class, SccNode.class, format,
				path, poolSize);
		long readingTime = System.nanoTime() - readingStart;
		if (Runtime.getRank() == 0)
			System.out.println("Graph with " + g.getGlobalSize() + " nodes read in "
					+ ConversionUtils.ns2sec(readingTime) + "s");

		/* prepare OBFR-MP */
		net_id = Runtime.getRank();
		cluster_size = Runtime.getPoolSize();
		MAX_STATES = g.nodes();
		System.out.println("MAX_STATES = " + g.getGlobalSize());
		global_indegree[0] = new int[g.nodes()];
		printf("%d: states = %d\n", net_id, g.nodes());
		fflush(System.out);
		int transitions = 0;
		for (int i = 0; i < g.nodes(); i++)
			transitions += g.node(i).outdegree();
		printf("%d: transitions = %d\n", net_id, transitions);
		fflush(System.out);

		scc_structure_size = new int[MAX_STATES];
		scc_structure_count = new int[MAX_STATES];

		printf("%d: CE:\n", net_id);
		fflush(System.out);

		max_owcty_depth = 0;
		nsccs = 0;
		ntrivsccs = 0;

		global_visited[0] = new int[MAX_STATES];
		global_found[0] = new int[MAX_STATES];
		alg_record_t mainAlg = new alg_record_t(g, alg_type_t.explorer, 0);

		mainAlg.iteration = 1;
		// mainAlg.update_fun = fun_sync_struct_min_candidate;

		mainAlg.source_queue.qalloc(MAX_STATES, 0, 0);
		mainAlg.source_queue.qinit();

		mainAlg.queue_one.qalloc(MAX_STATES, 0, 1);
		mainAlg.queue_one.qinit();

		mainAlg.queue_two.qalloc(MAX_STATES, 0, 2);
		mainAlg.queue_two.qinit();

		for (int i = 0; i < g.nodes(); i++)
			mainAlg.source_queue.qpush(i, 18);

		if (DISPLAY_PROGRESS) {
			printf("%d: START 0\n", net_id);
			fflush(System.out);
		}

		/* global_indegree */
		for (int i = 0; i < g.nodes(); i++) {
			SccLocalNode n = (SccLocalNode) g.node(i);
			global_indegree[0][i] = n.indegree();
		}

		/* OBFR-MP */
		Runtime.getRuntime().barrier();
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(mainAlg);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;

		/* results */
		if (Runtime.getRank() == 0) {
			System.out.println("FB on " + Runtime.getPoolSize() + " processors done in " + ConversionUtils.ns2sec(time)
					+ "s");
			if (ntrivsccs > 0)
				System.out.println("Found " + ntrivsccs + " SCCs of size 1");
			for (int i = 0; i < scc_structure_count.length; i++) {
				if (scc_structure_count[i] > 0) {
					System.out.println("Found " + scc_structure_count[i] + " SCCs of size " + scc_structure_size[i]);
				}
			}
		}
	}

}
