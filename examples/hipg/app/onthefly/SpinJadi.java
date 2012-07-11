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

package hipg.app.onthefly;

import hipg.Config;
import hipg.LocalNode;
import hipg.Node;
import hipg.Notification;
import hipg.Reduce;
import hipg.app.onthefly.OnTheFlyMAP.MAP;
import hipg.app.onthefly.OnTheFlyMAP.MAPLocalNode;
import hipg.app.onthefly.OnTheFlyMAP.MAPNode;
import hipg.app.onthefly.OnTheFlyVisitor.ReachedLocalNode;
import hipg.app.onthefly.OnTheFlyVisitor.ReachedNode;
import hipg.app.onthefly.OnTheFlyVisitor.Visitor;
import hipg.app.utils.PromelaReader;
import hipg.app.utils.PromelaTransitionStack;
import hipg.app.utils.PromelaTransitionStack.PromelaTransitionElement;
import hipg.format.GraphCreationException;
import hipg.graph.OnTheFlyDefaultHash;
import hipg.graph.OnTheFlyGraph;
import hipg.graph.OnTheFlyHash;
import hipg.graph.OnTheFlyLocalNode;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

import java.io.PrintStream;
import java.util.Vector;

import myutils.ConversionUtils;
import myutils.MathUtils;
import myutils.StringUtils;
import myutils.storage.PairIterator;
import spinja.concurrent.model.ConcurrentModel;
import spinja.exceptions.ValidationException;
import spinja.model.Condition;
import spinja.promela.model.NeverClaimModel;
import spinja.promela.model.PromelaTransition;
import spinja.util.ByteArrayStorage;

public class SpinJadi {

	private static final String NAME = SpinJadi.class.getSimpleName();
	private static final String VERSION = "1.0";
	private static final String DATE = "Mar-2011";

	static long EndTime = -1;

	/** Interface of a node/state. */
	public static interface SpinjaNode extends Node {
	}

	/* This is only to speed up neighbor search: stack of information about last taken transition and last found
	 * neighbor. It could be done per node, but then we waste memory on storing this information for all nodes, rather
	 * than just those which compute the successors. Anyway: be careful. This is not thread safe, and might be not
	 * efficient with a different neighbor access pattern. */
	static PromelaTransitionStack stack = new PromelaTransitionStack();
	static String exceptionWhenGettingSuccessor = null;

	/** Stored node/state. */
	public static class SpinjaLocalNode<SpinjaNodeT extends SpinjaNode> extends OnTheFlyLocalNode<SpinjaNodeT> {

		public SpinjaLocalNode(OnTheFlyGraph<SpinjaNodeT> graph, final byte[] state) {
			super(graph, state);
		}

		/** Gets a next neighbor. */
		protected byte[] getNeighbor(int index) {
			PromelaTransitionElement e = stack.peek();
			if (index == e.lastIndex) {
				// computed at previous call to this method
				return e.lastNeighbor;
			}
			if (e.lastIndex > index) {
				// restart computing, this in general should not happen if
				// neighbors are accessed all accessed one after the other
				e.reset();
			}
			// convert this state to model (needed because of nested methods)
			bytesToModel(state);
			// if (index == 0 &&
			// mainModel.conditionHolds(Condition.SHOULD_STORE_STATE)) {
			// atomicSteps++;
			// }
			do {
				// idea is this loop executes only once
				e.lastIndex++;
				e.lastTransition = mainModel.nextTransition(e.lastTransition);
			} while (e.lastIndex < index && e.lastTransition != null);
			if (e.lastTransition == null) {
				e.lastNeighbor = null;
				return null;
			}
			// take transition
			exceptionWhenGettingSuccessor = null;
			try {
				e.lastTransition.take();
			} catch (ValidationException ex) {
				exceptionWhenGettingSuccessor = "Exception when taking transition on model " + modelToString(state)
						+ " (message " + ex.getMessage() + ") reported at node " + id() + " by worker " + owner();
				return null;
			}
			e.lastNeighbor = modelToBytes();
			mainStateStored = e.lastNeighbor;
			// no need to undo because every time we want to take a transition,
			// we read in the state
			return e.lastNeighbor;
		}

		public boolean shouldStore() {
			return SpinJadi.isStoredState(state);
		}
	}

	public static abstract class SpinjaAlgorithm<TNode extends SpinjaNode> extends Synchronizer {

		/* Configuration of the search */

		/** If error should be ignored. */
		protected final boolean ignoreErrors;
		/** Number of errors to stop. */
		protected final int errorsToStop;
		/** Maximum search depth (-1 = infinite). */
		protected final int maxSearchDepth;
		/** If exceeding search depth should be considered an error. */
		protected final boolean exceedDepthIsError;
		/** Verbose level. */
		protected final int verbose;

		/* Search */
		/** The graph on which to compute. */
		protected final OnTheFlyGraph<TNode> g;
		/** Pivot state. */
		protected final byte[] pivot;
		/** If application stopped. */
		protected boolean stopped = false;
		/** Max depth reached. */
		protected int maxDepth = 0;

		/* Search results */
		/** Global execution time */
		protected long globalTime = -1;
		/** List of error messages. */
		protected Vector<String> errorMessages = new Vector<String>();
		/** List of warning messages. */
		protected Vector<String> warningMessages = new Vector<String>();
		/** Global number of matched nodes. */
		protected long globalMatched = -1;
		/** Global number of nodes stored. */
		protected long[] globalStored = null;
		/** Global number of nodes not stored. */
		protected long globalNotStored = -1;
		/** Global number of hash conflicts. */
		protected long globalHashConflicts = -1;
		/** Global max state length. */
		protected int globalMaxStateLen = -1;
		/** Global max depth. */
		protected int globalMaxDepth = -1;
		/** Global memory. */
		protected long globalMemory = -1;
		/** Global hash table length. */
		protected long globalHashtableLen = -1;
		/** Differently count atomic steps. */
		protected long globalRealAtomic = -1;

		public SpinjaAlgorithm(final OnTheFlyGraph<TNode> g, final byte[] pivot, final boolean ignoreErrors,
				final int errorsToStop, final int maxSearchDepth, final boolean exceedDepthError, final int verbose) {
			this.g = g;
			this.pivot = pivot;
			this.ignoreErrors = ignoreErrors;
			this.errorsToStop = errorsToStop;
			this.exceedDepthIsError = exceedDepthError;
			this.maxSearchDepth = maxSearchDepth;
			this.verbose = verbose;
		}

		protected void exceededDepth(String message) {
			if (exceedDepthIsError) {
				error(message);
			} else {
				warning(message);
			}
		}

		@Notification
		protected void warning(String warningMessage) {
			warningMessages.add(warningMessage);
		}

		@Notification
		protected void error(String errorMessage) {
			errorMessages.add(errorMessage);
			if (errorsToStop > 0 && errorMessages.size() >= errorsToStop) {
				stop();
			}
		}

		@Notification
		protected void stop() {
			stopped = true;
		}

		@Reduce
		protected long[] GlobalStoredNodes(long[] arr) {
			arr[Runtime.getRank()] = g.nodes();
			return arr;
		}

		@Reduce
		protected long GlobalNotStoredNodes(long s) {
			return s + g.getNotStoredNodesCount();
		}

		@Reduce
		protected long GlobalMatched(long s) {
			return s + (long) g.getMatchedNodesCount();
		}

		@Reduce
		public int GlobalMaxDepth(int maxDepth) {
			return (maxDepth > this.maxDepth ? maxDepth : this.maxDepth);
		}

		@Reduce
		protected int GlobalMaxStateLen(int maxLen) {
			PairIterator<byte[], LocalNode<TNode>> iter = g.map().stateNodeIterator();
			while (iter.hasNext()) {
				byte[] key = iter.next();
				if (key.length > maxLen) {
					maxLen = key.length;
				}
			}
			return maxLen;
		}

		@Reduce
		protected long GlobalMemory(long s) {
			final java.lang.Runtime r = java.lang.Runtime.getRuntime();
			return s + (r.totalMemory() - r.freeMemory());
		}

		@Reduce
		protected long GlobalHashConflicts(long s) {
			return s + g.map().conflicts();
		}

		@Reduce
		protected long GlobalHashtableLength(long s) {
			return s + g.map().capacity();
		}

		@Reduce
		protected long GlobalRealAtomics(long s) {
			return s + realAtomic;
		}

		public void printStats() {
		}
	}

	private static void versionAndCopyright(PrintStream out) {
		out.println("Running " + NAME + "-" + VERSION + " @ " + DATE);
		out.println("Copyright (c) 2011 Vrije Universiteit Amsterdam");
		out.println();
	}

	private static void usage(String errmsg) {
		usage(errmsg, -1);
	}

	private static void usage(String errmsg, int rank) {
		if (rank < 0 || rank == 0) {
			versionAndCopyright(System.err);
			if (errmsg != null) {
				System.err.println(errmsg);
			}
			System.err.println(NAME + " <spec> [ options ]");
			System.err.println("Where <spec> is a path to a promela file and most important options are:");
			System.err.println("  -E            ignore invalid end states (deadlocks)");
			System.err.println("  -A            ignore asserts");
			System.err.println("  -N            ignore any never claim");
			System.err.println("  -a            check for acceptance cycles");
			System.err.println("  -b            exceeding the depth limit is considered an error");
			System.err.println("  -c<num>       stop after at least <num> errors (never 0, default 1) (global)");
			System.err.println("  -m<num>       maximum search depth (not restricted 0, default 10000) (global)");
			System.err.println("  -w<num>       hash table size is 2^<num> ([3..30], default 21) (per worker)");
			System.err.println("Remaining options are:");
			System.err.println("  -v            prints the version number and exits");
			System.err.println("  -h            print this help message");
			System.err.println("  -v<num>       execution verbosity (no messages 0, some 1, a lot 2, even more 3)");
			System.err.println("  -keep         do not remove the model file generated by the spinja compiler");
			System.err.println("  -reuse        rather than generate the model, reuse the one generated previously");
			System.err.println("                (models are stored in the 'spinja' directory)");
		}
		System.exit(errmsg == null ? 0 : 1);
	}

	public static void main(String[] args) throws GraphCreationException {
		// parse arguments
		boolean onlyPrintVersion = false;
		boolean onlyPrintHelp = false;
		boolean ignoreErrors = false;
		boolean ignoreNeverClaim = false;
		boolean ignoreAssert = false;
		boolean checkAccept = false;
		boolean exceedDepthIsError = false;
		int errorsToStop = 1;
		int maxSearchDepth = 10000;
		int hashTableSizeLog = 21;
		int executionVerbosity = 0;
		String spec = null;
		boolean keepSpecification = false;
		boolean reuseSpecification = false;

		for (String arg : args) {
			if ("-E".equals(arg)) {
				ignoreErrors = true;
			} else if ("-A".equals(arg)) {
				ignoreAssert = true;
			} else if ("-N".equals(arg)) {
				ignoreNeverClaim = true;
			} else if ("-a".equals(arg)) {
				checkAccept = true;
			} else if ("-b".equals(arg)) {
				exceedDepthIsError = true;
			} else if (arg.startsWith("-c")) {
				String num = arg.substring(2);
				try {
					errorsToStop = Integer.parseInt(num);
				} catch (NumberFormatException ex) {
					usage("Could not parse number of errors: '" + num + "': " + ex.getMessage());
				}
			} else if (arg.startsWith("-m")) {
				String num = arg.substring(2);
				try {
					maxSearchDepth = Integer.parseInt(num);
				} catch (NumberFormatException ex) {
					usage("Could not parse max search depth: '" + num + "': " + ex.getMessage());
				}
			} else if (arg.equals("-v") || arg.equals("-version") || arg.equals("--version")) {
				onlyPrintVersion = true;
			} else if (arg.startsWith("-v")) {
				String num = arg.substring(2);
				try {
					executionVerbosity = Integer.parseInt(num);
				} catch (NumberFormatException ex) {
					usage("Could not parse execution verbosity: '" + num + "': " + ex.getMessage());
				}
				if (executionVerbosity < 0 || executionVerbosity > 3) {
					usage("Incorrect execution verbosity (" + executionVerbosity + ")");
				}
			} else if (arg.startsWith("-w")) {
				String num = arg.substring(2);
				try {
					hashTableSizeLog = Integer.parseInt(num);
				} catch (NumberFormatException ex) {
					usage("Could not parse hash table log size: '" + num + "': " + ex.getMessage());
				}
				if (hashTableSizeLog < 3 || hashTableSizeLog > 30) {
					usage("Incorrect hash table size log (" + hashTableSizeLog + ")");
				}
			} else if ("-keep".equals(arg)) {
				keepSpecification = true;
			} else if ("-reuse".equals(arg)) {
				reuseSpecification = true;
			} else if ("-help".equals(arg) || "-h".equals(arg) || "--help".equals(arg)) {
				onlyPrintHelp = true;
			} else if (spec == null) {
				spec = arg;
			} else {
				throw new RuntimeException("Unrecognized option: " + arg);
			}
		}
		Runtime.getRuntime().awaitPool(Config.POOLSIZE);
		if (onlyPrintHelp) {
			usage(null, Runtime.getRank());
		}
		if (Runtime.getRank() == 0) {
			versionAndCopyright(System.out);
		}
		if (onlyPrintVersion) {
			System.exit(0);
		}
		if (spec == null) {
			usage("No promela file specified", Runtime.getRank());
		}

		// read model
		if (Runtime.getRank() == 0)
			System.out.println("Reading promela spec from file " + spec);
		mainModel = PromelaReader.readPromela(spec, keepSpecification, reuseSpecification, ignoreNeverClaim,
				ignoreAssert);

		final boolean hasNeverClaim = (mainModel instanceof NeverClaimModel);

		if (hasNeverClaim) {
			ignoreErrors = true;
		}

		// wait for all workers
		Runtime.getRuntime().awaitPool(Config.POOLSIZE);
		Runtime.getRuntime().barrier();

		// print info about the search
		if (Runtime.getRank() == 0) {
			System.out.println();
			System.out.println("Full statespace search for:");
			System.out.print("        never claim             ");
			System.out.println(hasNeverClaim ? "+" : "-");
			System.out.print("        assertion violations    ");
			System.out.println(ignoreAssert ? "-" : "+");
			System.out.print("        accepting cycles        ");
			System.out.println(checkAccept ? "+" : "-");
			System.out.print("        invalid end states      ");
			System.out.println(ignoreErrors ? ("-" + (hasNeverClaim ? " (disabled by never-claim)" : "")) : "+");
			System.out.println();
			System.out.println("Executing search with " + Config.POOLSIZE + " workers");
			System.out.println();
			System.out.flush();
		}

		// init the graph and algorithm
		final OnTheFlyHash hash = new OnTheFlyDefaultHash(Config.POOLSIZE);
		byte[] root = modelToBytes();
		root = (hash.owner(root) == Runtime.getRank() ? root : null);
		mainStateStored = root;
		final SpinjaAlgorithm<?> algo;
		if (checkAccept) {
			final OnTheFlyGraph<MAPNode> g = new OnTheFlyGraph<MAPNode>(hash, MAPLocalNode.class, hashTableSizeLog);
			algo = new MAP(g, root, ignoreErrors, errorsToStop, maxSearchDepth, exceedDepthIsError, executionVerbosity);
		} else {
			final OnTheFlyGraph<ReachedNode> g = new OnTheFlyGraph<ReachedNode>(hash, ReachedLocalNode.class,
					hashTableSizeLog);
			algo = new Visitor(g, root, ignoreErrors, errorsToStop, maxSearchDepth, exceedDepthIsError,
					executionVerbosity);
		}
		boolean searchCompleted = false;
		boolean outOfMemory = false;
		Throwable unexpThr = null;
		final long StartTime = System.currentTimeMillis();
		try {
			Runtime.getRuntime().spawnAll(algo);
			Runtime.getRuntime().barrier();
			searchCompleted = true;
		} catch (OutOfMemoryError t) {
			outOfMemory = true;
		} catch (Throwable t) {
			unexpThr = t;
			t.printStackTrace();
		} finally {
			if (EndTime < 0) {
				EndTime = System.currentTimeMillis();
			}
		}

		// print results
		if (Runtime.getRank() == 0) {

			// reported warnings and errors
			if (outOfMemory)
				System.out.println("Maximum memory reached");
			if (unexpThr != null)
				System.out.println("Unexpected exception " + unexpThr.getClass().getSimpleName() + ": "
						+ unexpThr.getMessage());
			if (!searchCompleted)
				System.out.println("Warning: Search not completed!");
			for (String errorMessage : algo.errorMessages)
				System.out.println(SpinJadi.class.getSimpleName() + " error: " + errorMessage);
			for (String warningMessage : algo.warningMessages)
				System.out.println(SpinJadi.class.getSimpleName() + " warning: " + warningMessage);
			System.out.println();

			// algorithm statistics
			long[] stored = algo.globalStored;
			double average = MathUtils.Average(stored);
			double stdev = MathUtils.StDeviation(stored, average);
			long globalStored = MathUtils.Sum(stored);
			System.err.printf("State-vector %d byte, depth reached %d, errors: %d\n", algo.globalMaxStateLen,
					algo.globalMaxDepth, algo.errorMessages.size());
			System.out.printf("%8d states, stored\n", globalStored);
			System.out.printf("%8d states, matched\n", algo.globalMatched);
			System.out.printf("%8d transitions (= stored+matched)\n", globalStored + algo.globalMatched);
			System.out.printf("%8d atomic steps\n", algo.globalNotStored);
			if (hasNeverClaim && checkAccept) {
				System.out.printf("%8d real atomic\n", algo.globalRealAtomic);
			}
			algo.printStats();
			System.out.println();

			// store statistics
			double ghtLenLog = Math.log(algo.globalHashtableLen) / Math.log(2.0);
			System.out.printf("hash conflicts: %d (resolved)\n", algo.globalHashConflicts);
			System.out.printf("global hash table length: 2^ %.1f\n", ghtLenLog);
			System.out.println("global not stored nodes: " + algo.globalNotStored);
			System.out.printf("states per worker: %.2f +/- %.2f\n", average, stdev);
			System.out.println();

			// search statistics
			final double gmem = algo.globalMemory / (1024.0 * 1024.0 * 1024.0);
			final double gmemPerWorker = gmem / (double) Runtime.getPoolSize();
			final long gt = EndTime - StartTime;
			System.out.printf("%.1f global memory usage, %.1f per worker [GB]\n", gmem, gmemPerWorker);
			System.out.println();
			System.out.printf(SpinJadi.class.getSimpleName() + ": elapsed time %.2f seconds on %d processors\n",
					ConversionUtils.ms2sec(gt), Runtime.getPoolSize());
			System.out.printf(SpinJadi.class.getSimpleName() + ": rate %8.1f states/second\n",
					(globalStored / ConversionUtils.ms2sec(gt)));
			System.out.flush();
		}
	}

	//
	// mainModel stuff
	//

	/** 'Current' model used to compute next transition. */
	static ConcurrentModel<PromelaTransition> mainModel;

	/** Helper object for transforming state to/from the current model. */
	static final ByteArrayStorage mainState = new ByteArrayStorage();

	/** Helps minimize number of translations between state and model. */
	static byte[] mainStateStored = null;

	public static int realAtomic = 0;

	/** Converts byte array state into the current model. */
	static final void bytesToModel(final byte[] state) {
		if (state != mainStateStored) {
			mainState.setBuffer(state);
			mainStateStored = state;
			if (!mainModel.decode(mainState)) {
				throw new RuntimeException("Decode of state " + myutils.StringUtils.ByteArrayToHex(state) + " failed");
			}
		}
	}

	/** Converts the current model into (returned) byte array. */
	static final byte[] modelToBytes() {
		byte[] state = new byte[mainModel.getSize()];
		mainState.setBuffer(state);
		mainModel.encode(mainState);
		mainState.setBuffer(mainStateStored);
		return state;
	}

	static final boolean isStoredState(final byte[] state) {
		bytesToModel(state);
		return mainModel.conditionHolds(Condition.SHOULD_STORE_STATE);
	}

	static final boolean isEndState(final byte[] state) {
		bytesToModel(state);
		return mainModel.conditionHolds(Condition.END_STATE);
	}

	static final boolean isAcceptState(final byte[] state) {
		bytesToModel(state);
		return mainModel.conditionHolds(Condition.ACCEPT_STATE);
	}

	/** Returns the current state (model) as string. */
	static final String modelToString(final byte[] state) {
		bytesToModel(state);
		boolean shouldStore = mainModel.conditionHolds(Condition.SHOULD_STORE_STATE);
		boolean endState = mainModel.conditionHolds(Condition.END_STATE);
		boolean accState = mainModel.conditionHolds(Condition.ACCEPT_STATE);
		StringBuilder sb = new StringBuilder();
		sb.append("state=" + StringUtils.ByteArrayToHex(state) + " ");
		sb.append("transitions=" + countTransitions(state) + " ");
		sb.append("shouldStore=" + (shouldStore ? "Y" : "N") + " ");
		sb.append("endState=" + (endState ? "Y" : "N") + " ");
		sb.append("accState=" + (accState ? "Y" : "N"));
		sb.append(" ");
		sb.append(mainModel.toString().replace("\n", " "));
		return sb.toString();
	}

	/** Counts number of outgoing transitions in the current state/model. */
	static final int countTransitions(byte[] state) {
		bytesToModel(state);
		PromelaTransition last = null;
		int n = 0;
		while (true) {
			last = mainModel.nextTransition(last);
			if (last == null)
				break;
			n++;
		}
		return n;
	}
}
