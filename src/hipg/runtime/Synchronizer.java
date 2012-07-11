/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam.
 * Written by Ela Krepska e.l.krepska@vu.nl.
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
 *
 */

package hipg.runtime;

import hipg.Config;
import hipg.LocalNode;
import hipg.Node;
import myutils.IOUtils;
import myutils.ObjectCache;
import myutils.storage.bigarray.BigByteQueue;
import myutils.storage.bigarray.BigQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronizer implementation.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 */
public abstract class Synchronizer implements hipg.Synchronizer {
	public static final int OWNER_ALL = -17;
	private static final int NOTSPECIFIED = -18;

	/** Logger. */
	private static final Logger logger = LoggerFactory.getLogger(Synchronizer.class);

	/** Logger's prefix containing synchronizer's id. */
	private String loggerPrefix;

	/** Execution mode. */
	private int executionMode = EXECUTION_UNSET;

	/** Id. */
	private int id = NOTSPECIFIED;

	/** Owner. */
	private int owner = NOTSPECIFIED;

	/** Father owner. */
	private int fatherOwner = NOTSPECIFIED;

	/** Father id. */
	private int fatherId = NOTSPECIFIED;

	/** Father execution mode. */
	private int fatherExecutionMode = EXECUTION_UNSET;

	/** Father's spawn number. */
	private int fatherSpawn = NOTSPECIFIED;

	/** Depth. */
	private int depth = 0;

	/** Program counter (which part of the run() method to run next). */
	protected int pc = 0;

	/** Maximum program counter. */
	protected int pcmax = 1;

	/**
	 * If valid. (Synchronizer object is invalid after spawned on a remote process).
	 */
	transient private boolean valid = true;

	/** Message counter (sent minus received) for termination detection. */
	transient private int mc;

	/** Color (black or white) for termination detection. */
	transient private byte color = Barrier.WHITE;

	/** Number of alive children. */
	transient protected int children = 0;

	/** Number of spawn operations performed. */
	transient private int spawns = 0;

	/** If father was informed about death. */
	transient private boolean synchronizerDoneConfirmed = false;

	/** Method invocation stack. */
	transient private BigByteQueue stack;
	transient private BigQueue<LocalNode<?>> nodes;

	/** Method invocation stack size. */
	transient volatile private int stackSize;

	/** Cache for the method invocation stack. */
	final static ObjectCache<byte[]> cache = new ObjectCache<byte[]>(Config.SYNCHRONIZER_QUEUE_MEM_CACHE_SIZE);
	final static ObjectCache<LocalNode<?>[]> nodeCache = new ObjectCache<LocalNode<?>[]>(
			Config.SYNCHRONIZER_QUEUE_MEM_CACHE_SIZE);

	/** Currently executed distributed protocols. */
	transient private Barrier barrier = null;
	transient private BarrierAndReduce barrierAndReduce = null;
	transient private Reduce reduce = null;
	transient private Notification notification = null;
	transient private boolean inSync = false;
	transient private int lastReduceProtocol = -1;

	/** Creates root synchronizer. */
	public Synchronizer() {
	}

	final void init() {
		lastReduceProtocol = -1;
		valid = true;
		mc = 0;
		spawns = 0;
		inSync = false;
		color = Barrier.WHITE;
		children = 0;
		stackSize = 0;
		loggerPrefix = "(" + Runtime.getRuntime().name() + ") " + name() + " ";
		if (owner == NOTSPECIFIED)
			throw new RuntimeException("Cannot initialize synchronizer " + name() + ": no owner");
	}

	final static BigByteQueue createStack() {
		return new BigByteQueue(Config.SYNCHRONIZER_QUEUE_CHUNK_SIZE, Config.SYNCHRONIZER_QUEUE_INITIAL_CHUNKS, cache);
	}

	final static BigQueue<LocalNode<?>> createNodeStack() {
		return new BigQueue<LocalNode<?>>(Config.SYNCHRONIZER_QUEUE_CHUNK_SIZE,
				Config.SYNCHRONIZER_QUEUE_INITIAL_CHUNKS, nodeCache);
	}

	final void initQueues(BigByteQueue stack, BigQueue<LocalNode<?>> nodes, int stackSize) {
		if (stack == null) {
			this.stack = createStack();
			this.nodes = createNodeStack();
		} else {
			this.stack = stack;
			this.nodes = nodes;
			this.stackSize = stackSize;
			receivedBasicMessages(stackSize);
		}
	}

	private final void setFather(Synchronizer father) {
		depth = father.depth + 1;
		fatherId = father.id;
		fatherOwner = father.getOwner();
		fatherExecutionMode = father.getExecutionMode();
		fatherSpawn = father.getSpawns();
	}

	public final int getId() {
		if (id == NOTSPECIFIED)
			throw new RuntimeException("Synchronizer id is not " + "available before spawn!");
		return id;
	}

	final void setId(int id) {
		this.id = id;
		loggerPrefix = "(" + Runtime.getRuntime().name() + ") " + name() + " ";
	}

	public final int getFatherExecutionMode() {
		return fatherExecutionMode;
	}

	public final int getExecutionMode() {
		return executionMode;
	}

	final void setExecutionMode(int executionMode) {
		this.executionMode = executionMode;
	}

	public final int getOwner() {
		return owner;
	}

	public final int getMaster() {
		if (executionMode == EXECUTION_ALL)
			return 0;
		return owner;
	}

	public final Synchronizer getFather() {
		if (isRoot()) {
			return null;
		}
		return Runtime.getRuntime().getSynchronizer(getFatherOwner(), getFatherId());
	}

	public final int getFatherId() {
		return fatherId;
	}

	public final int getFatherOwner() {
		return fatherOwner;
	}

	final void setOwner(int owner) {
		if (!valid)
			invalid();
		this.owner = owner;
	}

	final void invalidate() {
		valid = false;
	}

	private final void invalid() {
		throw new RuntimeException("Synchronizer " + name() + " with father " + fatherName()
				+ " is not valid because it " + "has been spawned at a remote location");
	}

	public final int getSpawns() {
		return spawns;
	}

	public final int getFatherSpawn() {
		return fatherSpawn;
	}

	public final void spawn(int destination, hipg.Synchronizer Hsynchronizer, int executionMode) {
		Synchronizer synchronizer = (Synchronizer) Hsynchronizer;
		if (!valid)
			invalid();
		spawns++;
		synchronizer.setFather(this);
		if (executionMode == EXECUTION_ALL)
			destination = 0;
		children++;
		Runtime.getRuntime().spawn(destination, synchronizer, executionMode);
	}

	public final void spawn(hipg.Synchronizer synchronizer, int executionMode) {
		spawn(Runtime.getRank(), synchronizer, executionMode);
	}

	public final void spawn(Node node, hipg.Synchronizer synchronizer, int executionMode) {
		int dest = (node == null ? Runtime.getRank() : node.owner());
		spawn(dest, synchronizer, executionMode);
	}

	public final void spawn(int destination, hipg.Synchronizer synchronizer) {
		spawn(destination, synchronizer, executionMode);
	}

	public final void spawn(hipg.Synchronizer synchronizer) {
		spawn(Runtime.getRank(), synchronizer, executionMode);
	}

	public final void spawn(Node node, hipg.Synchronizer synchronizer) {
		int dest = (node == null ? Runtime.getRank() : node.owner());
		spawn(dest, synchronizer, executionMode);
	}

	public final void spawnOwned(hipg.Synchronizer synchronizer) {
		spawn(synchronizer, EXECUTION_OWNED);
	}

	public final void spawnOne(hipg.Synchronizer synchronizer) {
		spawn(Runtime.getRank(), synchronizer, EXECUTION_ONE);
	}

	public final void spawnAll(hipg.Synchronizer synchronizer) {
		spawn(0, synchronizer, EXECUTION_ALL);
	}

	public final void spawnOwned(Node node, hipg.Synchronizer synchronizer) {
		spawn(node, synchronizer, EXECUTION_OWNED);
	}

	public final void spawnOne(Node node, hipg.Synchronizer synchronizer) {
		spawn(node, synchronizer, EXECUTION_ONE);
	}

	public final void spawnOwned(int destination, hipg.Synchronizer synchronizer) {
		spawn(destination, synchronizer, EXECUTION_OWNED);
	}

	public final void spawnOne(int destination, hipg.Synchronizer synchronizer) {
		spawn(destination, synchronizer, EXECUTION_ONE);
	}

	public final BigByteQueue addMethodInvocation(LocalNode<?> node, short methodId) {
		nodes.enqueue(node);
		IOUtils.writeShort(methodId, stack);
		stackSize++;
		return stack;
	}

	/**
	 * Executes the synchronizer. Don't call it, use 'run()'.
	 */
	abstract public void run();

	/**
	 * Checks if the synchronizer is done. Execution of a (rewritten) synchronizer works as follows:
	 * {@code while (!isDone()) run();}
	 * 
	 * @return
	 */
	boolean isDone() {
		return pc >= pcmax;
	}

	final boolean hasChildren() {
		return children > 0;
	}

	public final int children() {
		return children;
	}

	public final boolean isRoot() {
		return (fatherOwner == NOTSPECIFIED);
	}

	public final int getDepth() {
		return depth;
	}

	private final void checkDone() {
		if (isDone() && !synchronizerDoneConfirmed) {
			synchronizerDoneConfirmed = true;
			if (Config.FINEDEBUG && logger.isDebugEnabled())
				logger.debug(loggerPrefix + "I just found out I'm done");
			Runtime.getRuntime().synchronizerDone(this);
		}
	}

	final void removing(boolean ambassador) {
		if (ambassador) {
			synchronizerDoneConfirmed = true;
			pc = pcmax;
		}
		stackSize = 0;
		stack.clear();
		nodes.clear();
	}

	private final boolean isAmbassador() {
		return executionMode == EXECUTION_OWNED && !Runtime.getRuntime().isMe(owner);
	}

	/** Returns true if run() was executed. */
	final boolean processRuns() {
		// check sync
		if (inSync && hasChildren())
			return false;
		// check barrier
		if (barrier != null && !barrier.isDone()) {
			if (Config.ERRCHECK) {
				if (!passive())
					throw new RuntimeException("Called barrier when issuer not passive: " + name());
			}
			barrier.progress();
			if (!barrier.isDone())
				return false;
		}
		// check barrierAndReduce
		if (barrierAndReduce != null && !barrierAndReduce.isDone()) {
			if (Config.ERRCHECK) {
				if (!passive())
					throw new RuntimeException("Called barrierAndReduce when issuer not passive: " + name());
			}
			barrierAndReduce.progress();
			if (!barrierAndReduce.isDone())
				return false;
		}
		// check reduce
		if (reduce != null && !reduce.isDone()) {
			if (executionMode == EXECUTION_ALL)
				reduce.progress();
			if (!reduce.isDone())
				return false;
		}
		// for ambassadors: we're done
		if (isAmbassador()) {
			return false;
		}
		// call 'run'
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Run " + pc + "/" + pcmax);
		run();
		checkDone();
		return true;
	}
	
	/**
	 * Waits for the competition of the computations.
	 */
	public final void barrier() {
		if (!valid)
			invalid();
		if (barrier == null)
			barrier = new Barrier(this);
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Starting barrier");
		barrier.init();
	}

	/**
	 * Waits for the competition of the computations and after it's done computes global state.
	 */
	public final void barrierAndReduce(final short reduceMethodId, final byte[] initialValue) {
		if (!valid)
			invalid();
		if (barrierAndReduce == null)
			barrierAndReduce = new BarrierAndReduce(this);
		barrierAndReduce.set(reduceMethodId, initialValue);
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Starting barrierAndReduce");
		lastReduceProtocol = 2;
		barrierAndReduce.init();
	}

	/**
	 * Initiates a reduce method with a given initial value.
	 */
	public final void reduce(final short reduceMethodId, final byte[] initialValue) {
		if (!valid)
			invalid();
		if (reduce == null)
			reduce = new Reduce(this);
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Starting reduce " + reduceMethodId);
		lastReduceProtocol = 0;
		reduce.set(reduceMethodId, initialValue);
		reduce.init();
	}

	/**
	 * Gets result of the last reduce method (valid only after the reduce method finished).
	 */
	protected final byte[] result() {
		if (lastReduceProtocol == 0)
			return reduce.result();
		else
			return barrierAndReduce.result();
	}

	/**
	 * Initiates a notification method with a given initial value.
	 */
	public final void notification(final short notificationMethodId, final byte[] initialValue) {
		if (!valid)
			invalid();
		if (notification == null)
			notification = new Notification(this);
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Starting notification  " + notificationMethodId);
		notification.init(notificationMethodId, initialValue);
	}

	/**
	 * Handles notification of a child being done.
	 */
	final void childDone() {
		if (!valid)
			invalid();
		children--;
		if (Config.ERRCHECK) {
			if (children < 0) {
				throw new RuntimeException("Synchronizer " + name() + " father=" + fatherName() + " children="
						+ children);
			}
		}
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Child done. Left children: " + children + ". Done " + pc + "/" + pcmax);
		if (inSync && !hasChildren()) {
			inSync = false;
		}
		checkDone();
	}

	final int processStack() {
		int processedStackElements = 0;
		while (stackSize > 0) {
			final short methodId = IOUtils.readShort(stack);
			final LocalNode<?> node = nodes.dequeue();
			processedStackElements++;
			Runtime.immediateDepth = 0;
			if (Config.ERRCHECK) {
				long origStackSize = -10;
				try {
					origStackSize = stack.size();
					node.hipg_execute(methodId, this, stack);
				} catch (RuntimeException e) {
					System.err.println("On " + Runtime.getRank() + " problem in method " + methodId + " on node "
							+ node.name() + " on synchronizer of type " + this.getClass().getSimpleName() + " of id "
							+ getId() + " when orig stack size: " + origStackSize + " and after: " + stack.size());
					throw e;
				}
			} else {
				if (node == null) {
					throw new RuntimeException("Got null node from queue!!! " + nodes.capacity() + " " + nodes.size()
							+ " " + stackSize + " - " + nodes.toString());
				}
				node.hipg_execute(methodId, this, stack);
			}
			stackSize--;
		}
		if (Config.STATISTICS) {
			Statistics.processedStack(processedStackElements);
		}
		return processedStackElements;
	}

	@Override
	public String toString() {
		return id + "^" + owner;
	}

	final void receivedBasicMessage() {
		mc--;
		color = Barrier.BLACK;
	}

	private final void receivedBasicMessages(int count) {
		if (count > 0) {
			mc -= count;
			color = Barrier.BLACK;
		}
	}

	final void sendingBasicMessage() {
		mc++;
	}

	final void receivedBarrierToken(final int barrier, final int sum, final int color) {
		if (this.barrier == null) {
			this.barrier = new Barrier(this);
		}
		this.barrier.received(barrier, sum, color);
	}

	final void receivedBarrierAnnounceToken() {
		this.barrier.receivedAnnounce();
	}

	final void receivedReduceToken(final int reduce, final short reduceMethodId, final byte[] result) {
		if (this.reduce == null)
			this.reduce = new Reduce(this);
		this.reduce.received(reduce, reduceMethodId, result);
	}

	final void receivedReduceAnnounceToken(final byte[] result, final int sender) {
		this.reduce.receivedAnnounce(result, sender);
	}

	final void receivedReduceContinueToken() {
		this.reduce.receivedContinue();
	}

	final void receivedBarrierReduceToken(final int barrier, final int sum, final int master) {
		if (this.barrierAndReduce == null)
			this.barrierAndReduce = new BarrierAndReduce(this);
		this.barrierAndReduce.received(barrier, sum, master);
	}

	final void receivedBarrierReduceQueryToken(final byte[] partialResult) {
		this.barrierAndReduce.receivedQuery(partialResult);
	}

	final void receivedBarrierReduceAnnounceToken(final byte[] result) {
		this.barrierAndReduce.receivedAnnounce(result);
	}

	final void receivedNotificationToken(final short notificationMethodId, final byte[] value, final int id,
			final int issuer) {
		if (this.notification == null)
			this.notification = new Notification(this);
		this.notification.received(notificationMethodId, value, id, issuer);
	}

	final void receivedNotificationAck(final int id) {
		this.notification.receivedAck(id);
	}

	final boolean passive() {
		// return targets.isEmpty();
		return stackSize == 0;
	}

	final byte color() {
		return color;
	}

	final void whiten() {
		color = Barrier.WHITE;
	}

	/** Waits until this synchronizer and it's children are all done. */
	protected final void sync() {
		if (!valid)
			invalid();
		if (children > 0 && executionMode != Synchronizer.EXECUTION_ONE) {
			if (Config.ERRCHECK) {
				if (inSync)
					throw new RuntimeException("Sync in synchronizer " + name() + " already started!");
			}
			inSync = true;
		}
	}

	public String getStatus() {
		return getStatus(false);
	}

	public String getStatus(boolean detail) {
		if (!valid) {
			return "invalid";
		}
		final StringBuilder sb = new StringBuilder();
		if (detail) {
			sb.append("executionMode=");
			if (executionMode == EXECUTION_ALL) {
				sb.append("all");
			} else if (executionMode == EXECUTION_OWNED) {
				sb.append("own");
			} else if (executionMode == EXECUTION_ONE) {
				sb.append("one");
			} else {
				sb.append("???");
			}
			sb.append(" ");
		}
		sb.append("todo=" + todo() + " ");
		sb.append("children=" + children + " ");
		sb.append("spawns=" + spawns + " ");
		sb.append("run=" + (isAmbassador() ? "amb" : (pc + "/" + pcmax)) + " ");

		if (detail) {
			sb.append("mc=" + mc + " ");
			sb.append("father=" + fatherName() + " ");
			sb.append("barrier=" + barrier + " ");
			sb.append("reduce=" + (reduce == null ? "null" : reduce) + " ");
			sb.append("barr&red=" + (barrierAndReduce == null ? "null" : barrierAndReduce) + " ");
			sb.append("insync=" + inSync);
		}

		return sb.toString();
	}

	public String fatherName() {
		if (isRoot())
			return "root";
		return fatherId + "^" + fatherOwner;
	}

	public String name() {
		return id + "^" + owner;
	}

	protected final int todo() {
		return stackSize;
	}

	public final int mc() {
		return mc;
	}

	public byte[] hipg_reduce(final short reduceMethodId, final byte[] param) {
		throw new RuntimeException("hipg_reduce() not defined. Did you use the rewriter?");
	}

	public void hipg_notify(final short notificationMethodId, final byte[] param) {
		throw new RuntimeException("hipg_notify() not defined. Did you use the rewriter?");
	}

}
