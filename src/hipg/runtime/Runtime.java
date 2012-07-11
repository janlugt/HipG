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
import hipg.Graph;
import hipg.LocalNode;
import hipg.Node;
import hipg.graph.ExplicitGraph;
import hipg.graph.OnTheFlyGraph;
import ibis.ipl.IbisCreationFailedException;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

import myutils.IOUtils;
import myutils.StringUtils;
import myutils.storage.bigarray.BigByteQueue;
import myutils.storage.bigarray.BigQueue;
import myutils.system.MonitorThread;
import myutils.system.TimeoutThread;
import myutils.tuple.pair.FinalIntPair;
import myutils.tuple.triple.FinalIntTriple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Runtime {
	/** Logging facilities. */
	private final static Logger logger = LoggerFactory.getLogger(Runtime.class);
	private final String loggerPrefix;
	/** Communication (singleton). */
	private static final Communication communication;
	/** Runtime. (singleton). */
	private static final Runtime runtime;

	/** Creates singleton runtime. */
	static {
		Communication aCommunication = null;
		if (Config.CREATE_COMMUNICATION) {
			try {
				aCommunication = new Communication();
			} catch (Throwable t) {
				aCommunication = null;
				System.err.println("Could not create communication: " + t.getMessage());
				t.printStackTrace();
				System.exit(1);
			}
		}
		communication = aCommunication;
		Runtime aRuntime = null;
		try {
			aRuntime = new Runtime();
		} catch (Throwable t) {
			aRuntime = null;
			System.err.println("Could not create runtime: " + t);
			t.printStackTrace();
			System.exit(1);
		}
		runtime = aRuntime;
		if (getRank() == 0) {
			Config.printConfiguration();
		}
	}

	/** Registered distributed graph instances. */
	@SuppressWarnings("unchecked")
	private final Graph<? extends Node> graphs[] = (Graph<? extends Node>[]) Array.newInstance(Graph.class,
			Config.MAXGRAPHS);
	private final int[] graphType = new int[Config.MAXGRAPHS];
	public static final int GRAPH_EXPLICIT = 0;
	public static final int GRAPH_ONTHEFLY = 1;

	/** Synchronizers owned by me. */
	private final Synchronizer[] synchronizers = new Synchronizer[Config.MAXSYNCHRONIZERS];

	/** Synchronizers owned by others. */
	private final Synchronizer[][] ambassadors = null;// new
	// Synchronizer[Config.MAXPOOL][];

	/** Count of my synchronizers. */
	private int synchronizersCount = 0;

	/** Count of ambassadors. */
	private final int[] ambassadorsCount = null;// new int[Config.MAXPOOL];

	/** Number of not done synchronizer */
	private int notDoneSynchronizersCount = 0;

	/** Not done synchronizers */
	private final Synchronizer[] notDoneSynchronizers;

	/** Map of received id's of synchronizers. */
	private final Map<FinalIntTriple, Object> ids = new HashMap<FinalIntTriple, Object>();

	/** Postponed messages (received before creation of a synchronizer) */
	private final Map<FinalIntPair, Object> allPostponedMessages = new HashMap<FinalIntPair, Object>();

	/** Count of spawned root synchronizers. */
	private int rootSpawns = 0;

	/** My rank. */
	private final int rank;

	/** The number of spawns sent minus received (for termination detection). */
	private long mc = 0;

	/** Global barriers. */
	private final GlobalBarrier globalBarrier;

	/** Color (for termination detection). */
	private byte color = Barrier.WHITE;

	/** If the runtime aborted. */
	private boolean aborted = false;
	private String abortedMessage = null;

	/** If the runtime closed. */
	private boolean closed = false;

	public static int immediateDepth = 0;

	/** Get singleton runtime. */
	public static final Runtime getRuntime() {
		return runtime;
	}

	public static final Communication getCommunication() {
		return communication;
	}

	public static final int getRank() {
		Communication communication = getCommunication();
		return (communication == null ? 0 : communication.getRank());
	}

	public static final int getPoolSize() {
		Communication communication = getCommunication();
		return (communication == null ? 0 : communication.getPoolSize());
	}

	public static final void nice() {
		runtime.beNice();
	}

	/** Creates runtime. */
	public Runtime() throws IbisCreationFailedException {
		if (communication == null) {
			// Under JUnit.
			rank = 0;
			loggerPrefix = "";
		} else {
			rank = communication.getRank();
			loggerPrefix = "(" + communication.getName() + ") ";
		}
		logger.debug(loggerPrefix + "Creating runtime");
		java.lang.Runtime.getRuntime().addShutdownHook(new TimeoutThread(new Shutdown(this), 20000));
		for (int i = 0; i < graphs.length; i++)
			graphs[i] = null;
		if (ambassadors != null) {
			for (int i = 0; i < ambassadors.length; i++)
				ambassadors[i] = null;
			for (int i = 0; i < ambassadorsCount.length; i++)
				ambassadorsCount[i] = 0;
		}
		globalBarrier = new GlobalBarrier(this);
		notDoneSynchronizers = (Synchronizer[]) Array.newInstance(Synchronizer.class, Config.MAXSYNCHRONIZERS);
		logger.debug(loggerPrefix + "Runtime created");
		if (Config.STATISTICS) {
			Statistics.saveMemoryUsage();
		}
	}

	public String getName() {
		return (communication == null ? "Runtime" : communication.getName());
	}

	/**
	 * Register a graph (done automatically by the framework). Return graph's id.
	 */
	public <TNode extends Node> short registerGraph(Graph<TNode> graph) {
		short handle = 0;
		while (graphs[handle] != null)
			handle++;
		if (handle >= Config.MAXGRAPHS)
			throw new RuntimeException("Maximum number of graphs exceeded");
		graphs[handle] = graph;
		if (graph instanceof ExplicitGraph<?>)
			graphType[handle] = GRAPH_EXPLICIT;
		else if (graph instanceof OnTheFlyGraph<?>)
			graphType[handle] = GRAPH_ONTHEFLY;
		else
			throw new RuntimeException("Unrecognized graph type: " + graph.getClass().getName());
		logger.debug(loggerPrefix + "Registering graph " + handle);
		return handle;
	}

	public String name() {
		return communication == null ? "" : communication.getName();
	}

	public boolean isMe(int rank) {
		return this.rank == rank;
	}

	public boolean isAll(int ps) {
		return (ps == communication.getPoolSize());
	}

	public boolean hasCoworkers() {
		return communication.getPoolSize() > 1;
	}

	public int getNext() {
		return communication.nextRank();
	}

	/** Gracefully aborts the entire execution. */
	public void abort(String msg) {
		if (!aborted) {
			aborted = true;
			logger.error(loggerPrefix + "Aborting");
			communication.sendAbortMessage(msg);
		}
	}

	/** Shutdown hook which gracefully closes the runtime. */
	private static class Shutdown extends Thread {
		private Runtime runtime = null;

		public Shutdown(Runtime r) {
			runtime = r;
		}

		@Override
		public void run() {
			runtime.dumpReportToFile();
			try {
				runtime.close();
			} catch (Throwable t) {
				logger.error(runtime.loggerPrefix + "Could not shutdown runtime: " + t);
			}
		}
	}

	private void dumpReportToFile() {
		if (Config.REPORT_FILE_BASE_NAME != null) {
			final String report = Statistics.getReport();
			final String reportFileName = Config.REPORT_FILE_BASE_NAME + "." + getRank() + ".txt";
			try {
				StringUtils.writeStringToFile(report, reportFileName);
			} catch (IOException e) {
				logger.error(loggerPrefix + "Could not write report to " + reportFileName + ": " + e.getMessage());
			}
		}
	}

	/** Awaits pool of a specified minimum size. */
	public void awaitPool(int size) {
		communication.awaitPool(size);
	}

	/** Gracefully closes the runtime. */
	public void close() {
		if (!closed) {
			closed = true;
			logger.debug(loggerPrefix + "Closing");

			// Hack: if each processor is printing something
			// they'll print at a roughly different moment.
			if (Config.STATISTICS) {
				if (!aborted) {
					try {
						Thread.sleep(300 * getRank());
					} catch (InterruptedException e) {
					}
				}
			}

			try {
				if (communication != null)
					communication.close();
			} catch (Throwable t) {
				logger.error(loggerPrefix + "Could not properly shutdown communication (aborted?" + aborted + "):"
						+ t.getMessage());
			}
		}
	}

	/**
	 * Access to the graph array.
	 */
	public Graph<?> getGraph(int graphId) {
		return graphs[graphId];
	}

	/** Retrieves synchronizer owned by this runtime. */
	// synchronizer might be null because:
	// (a) was not yet created but a message fot it arrived
	// (b) was already discarded and a child sent a messsage that it's done
	private final Synchronizer getSynchronizer(int synchronizerId) {
		return synchronizers[synchronizerId];
	}

	/**
	 * Retrieves synchronizer ambassador.
	 */
	private final Synchronizer getSynchronizerAmbassador(int synchronizerOwner, int synchronizerId) {
		return ambassadors[synchronizerOwner][synchronizerId];
	}

	/**
	 * Retrieves synchronizer, but if it's not there, does not create it.
	 */
	final Synchronizer getSynchronizer(int synchronizerOwner, int synchronizerId) {
		return ((synchronizerOwner == Synchronizer.OWNER_ALL || isMe(synchronizerOwner)) ? getSynchronizer(synchronizerId)
				: getSynchronizerAmbassador(synchronizerOwner, synchronizerId));
	}

	private static final class PostponedMessages {
		private final BigByteQueue queue = Synchronizer.createStack();
		private final BigQueue<LocalNode<?>> nodes = Synchronizer.createNodeStack();

		private int numTokens = 0;
		private int[] tokenTypes = new int[3];
		private Object[][] tokens = new Object[3][];

		public PostponedMessages() {
		}

		public BigQueue<LocalNode<?>> nodes() {
			return nodes;
		}

		public BigByteQueue queue() {
			return queue;
		}

		public int size() {
			return (int) nodes.size();
		}

		public int tokens() {
			int t = 0;
			for (int i = 0; i < tokens.length; i++) {
				if (tokens[i] != null) {
					t++;
				}
			}
			return t;
		}

		public void add(LocalNode<?> node, short methodId, byte[] buf, int position, int paramCount) {
			nodes.enqueue(node);
			IOUtils.writeShort(methodId, queue);
			for (int i = 0; i < paramCount; i++) {
				queue.enqueue(buf[position++]);
			}
		}

		public void addToken(int type, Object[] token) {
			numTokens++;
			tokens[numTokens] = token;
			tokenTypes[numTokens] = type;
		}
	}

	private void handlePostponedMessages(Synchronizer synchronizer) {
		final FinalIntPair postponedMessagedId = new FinalIntPair(synchronizer.getOwner(), synchronizer.getId());
		final PostponedMessages postponedMessages = (PostponedMessages) allPostponedMessages.get(postponedMessagedId);
		if (postponedMessages == null) {
			synchronizer.initQueues(null, null, 0);
		} else {
			if (Config.FINEDEBUG && logger.isDebugEnabled()) {
				logger.debug(loggerPrefix + "Handling " + allPostponedMessages.size() + " postponed messages and "
						+ postponedMessages.tokens() + " tokens for " + synchronizer.name());
			}
			synchronizer.initQueues(postponedMessages.queue(), postponedMessages.nodes(), postponedMessages.size());
			for (int i = 0; i < postponedMessages.tokens.length; i++) {
				if (postponedMessages.tokens[i] != null) {
					final Object[] token = postponedMessages.tokens[i];
					final int tokenType = postponedMessages.tokenTypes[i];
					switch (tokenType) {
					case FastMessage.BARRIER:
						synchronizer.receivedBarrierToken((Integer) token[0], (Integer) token[1], (Integer) token[2]);
						break;
					case FastMessage.REDUCE:
						synchronizer.receivedReduceToken((Integer) token[0], (Short) token[1], (byte[]) token[2]);
						break;
					case FastMessage.BARRED:
						synchronizer.receivedBarrierReduceToken((Integer) token[0], (Integer) token[1],
								(Integer) token[2]);
					}
				}
			}
			allPostponedMessages.remove(postponedMessagedId);
		}
	}

	public void postponeTokenMessage(final int synchronizerOwner, final int synchronizerId, final int tokenType,
			final Object[] token) {
		final FinalIntPair synchronizerPair = new FinalIntPair(synchronizerOwner, synchronizerId);
		PostponedMessages postponedMessage = (PostponedMessages) allPostponedMessages.get(synchronizerPair);
		if (postponedMessage == null) {
			postponedMessage = new PostponedMessages();
			allPostponedMessages.put(synchronizerPair, postponedMessage);
		}
		postponedMessage.addToken(tokenType, token);
		if (Config.STATISTICS) {
			Statistics.postponingToken();
		}
	}

	public void postponeUserMessage(final int synchronizerOwner, final int synchronizerId, final short methodId,
			final LocalNode<?> node, final byte buf[], final int position, final int paramCount) {
		final FinalIntPair synchronizerPair = new FinalIntPair(synchronizerOwner, synchronizerId);
		PostponedMessages postponedMessage = (PostponedMessages) allPostponedMessages.get(synchronizerPair);
		if (postponedMessage == null) {
			postponedMessage = new PostponedMessages();
			allPostponedMessages.put(synchronizerPair, postponedMessage);
		}
		postponedMessage.add(node, methodId, buf, position, paramCount);
		if (Config.STATISTICS) {
			Statistics.postponingUserMessage();
		}
	}

	/** Spawn a synchronizer here. */
	public void executeSpawn(Synchronizer synchronizer, boolean issuerIsRemote, boolean ambassadorsAlreadySent) {
		if (synchronizer.isRoot())
			rootSpawns++;
		synchronizer.init();
		assert (synchronizer.getExecutionMode() != Synchronizer.EXECUTION_UNSET);
		assert (synchronizer.isRoot() || synchronizer.getFatherExecutionMode() != Synchronizer.EXECUTION_UNSET);
		if (issuerIsRemote) {
			mc--;
			color = Barrier.BLACK;
		}
		// determine synchronizer's id
		int synchronizerId = -1;
		if (isMe(synchronizer.getMaster())) {
			synchronizerId = synchronizersCount++;
			if (synchronizersCount >= Config.MAXSYNCHRONIZERS)
				throw new RuntimeException("Maximal number of synchronizers " + Config.MAXSYNCHRONIZERS + " exceeded.");
		} else {
			final FinalIntTriple t = new FinalIntTriple(synchronizer.getFatherOwner(), synchronizer.getFatherId(),
					synchronizer.isRoot() ? getRootSpawns() : synchronizer.getFatherSpawn());
			Integer i = null;
			Object o = ids.get(t);
			if (o == null)
				ids.put(t, synchronizer);
			else {
				if (Config.ERRCHECK) {
					if (!(o instanceof Integer))
						throw new RuntimeException("Retrieved id for synchronizer " + t + " is not an integer: " + o);
				}
				i = (Integer) o;
			}
			if (i != null) {
				if (Config.FINEDEBUG && logger.isDebugEnabled())
					logger.debug(loggerPrefix + "Retrieved id " + i);
				synchronizerId = i;
			} else {
				if (Config.FINEDEBUG && logger.isDebugEnabled())
					logger.debug(loggerPrefix + "Registered for id on " + t);
			}
		}
		if (synchronizerId >= 0) {
			synchronizer.setId(synchronizerId);
			if (Config.FINEDEBUG && logger.isDebugEnabled())
				logger.debug(loggerPrefix + "New synchronizer " + synchronizer.name() + " (in executeSpawn()) "
						+ "with father " + synchronizer.fatherName());
			if (Config.ERRCHECK)
				if (synchronizers[synchronizerId] != null)
					throw new RuntimeException("Putting new synchronizer " + synchronizer.name() + " in place "
							+ synchronizerId + " where a synchronizer exists: " + synchronizers[synchronizerId].name());
			synchronizers[synchronizerId] = synchronizer;
			notDoneSynchronizers[notDoneSynchronizersCount++] = synchronizer;
			if (hasCoworkers()) {
				if (!ambassadorsAlreadySent) {
					if (Config.FINEDEBUG && logger.isDebugEnabled())
						logger.debug(loggerPrefix + "Sending ambassadors for synchronizer " + synchronizer.name());
					mc += communication.getPoolSize() - 1;
					communication.sendAmbassadorSpawnMessages(synchronizer);
				} else if (isMe(synchronizer.getMaster())) {
					if (Config.FINEDEBUG && logger.isDebugEnabled()) {
						logger.debug(loggerPrefix + "Sending id for " + "synchronizer " + synchronizer.name() + ": "
								+ synchronizer.getFatherOwner() + " " + synchronizer.getFatherId() + " "
								+ synchronizer.isRoot());
					}
					communication.sendIdMessages(synchronizer);
				}
			}
			handlePostponedMessages(synchronizer);
		}
	}

	public void synchronizerDone(Synchronizer synchronizer) {
		if (Config.ERRCHECK) {
			if (synchronizer == null)
				throw new RuntimeException("Error in executeSynchronizerDone(): synchronizer null");
		}
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Synchronizer " + synchronizer.name() + " done");

		// remove synchronizer
		if (Config.ERRCHECK) {
			if (synchronizer.getOwner() != Synchronizer.OWNER_ALL && !isMe(synchronizer.getOwner()))
				throw new RuntimeException("In synchronizerDone() removing synchronizer which does not belong to me? "
						+ synchronizer.name());
			if (synchronizers[synchronizer.getId()] == null)
				throw new RuntimeException("Removing synchronizer " + synchronizer.name() + " for the second time?");
			if (synchronizer.todo() > 0)
				throw new RuntimeException("Removing synchronizer " + synchronizer.name() + " who has "
						+ synchronizer.todo() + " todo");
		}
		synchronizer.removing(false);
		synchronizers[synchronizer.getId()] = null;

		// owned: remove ambassadors
		if (synchronizer.getExecutionMode() == Synchronizer.EXECUTION_OWNED) {
			communication.sendRemoveAmbassadorMessages(synchronizer);
		}

		// inform father
		if (!synchronizer.isRoot()) { // && isMe(synchronizer.getMaster())) {
			int destination = synchronizer.getFatherOwner();
			if (isMe(destination) || destination == Synchronizer.OWNER_ALL) {
				Synchronizer father = getSynchronizer(synchronizer.getFatherId());
				if (father != null)
					father.childDone();
			} else {
				communication.sendChildDoneMessage(destination, synchronizer.getFatherId());
			}
		}
	}

	private void handleChildDoneMessage(int synchronizerId) {
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Synchronizer " + synchronizerId + "^" + getRank() + "'s child is done");
		Synchronizer synchronizer = getSynchronizer(synchronizerId);
		if (synchronizer == null)
			throw new RuntimeException("Synchronizer " + synchronizerId + " at " + getRank()
					+ " done but seems already removed!!");
		if (synchronizer != null) {
			synchronizer.childDone();
		}
	}

	private void handleRemoveAmbassadorMessage(int synchronizerOwner, int synchronizerId) {
		if (Config.FINEDEBUG && logger.isDebugEnabled())
			logger.debug(loggerPrefix + "Ambassador for " + synchronizerId + "^" + synchronizerOwner + " done");
		Synchronizer synchronizer = getSynchronizerAmbassador(synchronizerOwner, synchronizerId);
		if (Config.ERRCHECK) {
			if (synchronizer == null)
				throw new RuntimeException(loggerPrefix + "Could not execute ambassador done " + synchronizerId + "^"
						+ synchronizerOwner + ": synchronizer not found");
			if (isMe(synchronizerOwner))
				throw new RuntimeException(loggerPrefix + "Removing ambassador at owner for synchronizer "
						+ synchronizerId + "^" + synchronizerOwner);
		}
		// remove ambassador
		ambassadors[synchronizerOwner][synchronizerId].removing(true);
		ambassadors[synchronizerOwner][synchronizerId] = null;
		if (synchronizerId == ambassadorsCount[synchronizerOwner] - 1) {
			do {
				ambassadorsCount[synchronizerOwner]--;
				synchronizerId--;
			} while (synchronizerId >= 0 && ambassadors[synchronizerOwner][synchronizerId] == null);
		}
	}

	// private void handleSynchronizerSpawnMessage(Synchronizer synchronizer) {
	// runtime.executeSpawn(synchronizer, /* issuer is remote */true, false);
	// }
	//
	// private void handleAmbassadorSpawnMessage(Synchronizer synchronizer) {
	// int synchronizerId = synchronizer.getId();
	// int synchronizerOwner = synchronizer.getOwner();
	//
	// if (Config.FINEDEBUG && logger.isDebugEnabled())
	// logger.debug(loggerPrefix + "Creating ambassador for "
	// + synchronizer.name() + " of "
	// + synchronizer.getClass().getSimpleName());
	//
	// if (Config.ERRCHECK) {
	// if (ambassadors[synchronizerOwner] != null
	// && ambassadorsCount[synchronizerOwner] > synchronizerId
	// && ambassadors[synchronizerOwner][synchronizerId] != null)
	// throw new RuntimeException("Synchronizer " + synchronizerId
	// + "^" + synchronizerOwner + " already exists on "
	// + getRank());
	// }
	//
	// mc--;
	// synchronizer.init();
	//
	// if (ambassadors[synchronizerOwner] == null)
	// ambassadors[synchronizerOwner] = new
	// Synchronizer[Config.MAXSYNCHRONIZERS];
	// ambassadors[synchronizerOwner][synchronizerId] = synchronizer;
	// if (synchronizerId >= ambassadorsCount[synchronizerOwner])
	// ambassadorsCount[synchronizerOwner] = synchronizerId + 1;
	// notDoneSynchronizers[notDoneSynchronizersCount++] = synchronizer;
	//
	// handlePostponedMessages(synchronizer);
	// }

	private void handleIdMessage(int fatherId, int fatherOwner, int spawns, int id) {
		final FinalIntTriple t = new FinalIntTriple(fatherOwner, fatherId, spawns);
		Synchronizer synchronizer = null;
		Object s = ids.get(t);
		if (Config.ERRCHECK) {
			if (s != null && !(s instanceof Synchronizer))
				throw new RuntimeException("Expected synchronizer but got " + synchronizer);
		}
		synchronizer = (Synchronizer) s;
		if (synchronizer != null) {
			ids.remove(t);
			synchronizer = (Synchronizer) s;
			synchronizer.setId(id);
			if (synchronizer.getOwner() == Synchronizer.OWNER_ALL) {
				if (Config.ERRCHECK)
					if (synchronizers[synchronizer.getId()] != null)
						throw new RuntimeException("Putting new synchronizer " + synchronizer.name()
								+ " in place where a synchronizer exists: "
								+ synchronizers[synchronizer.getId()].name());
				synchronizers[synchronizer.getId()] = synchronizer;
			} else {
				throw new RuntimeException("Spawn owned by all is not implemented yet");
			}
			if (Config.FINEDEBUG && logger.isDebugEnabled())
				logger.debug(loggerPrefix + "New synchronizer " + synchronizer.name()
						+ " (in handleIdMessage()) with father " + synchronizer.fatherName());

			notDoneSynchronizers[notDoneSynchronizersCount++] = synchronizer;
			handlePostponedMessages(synchronizer);

		} else {
			ids.put(t, id);

			if (Config.FINEDEBUG && logger.isDebugEnabled())
				logger.debug(loggerPrefix + "Synchronizer id " + id + " " + t + " -> stored");
		}
	}

	private void handleAbortMessage(int issuer) {
		System.err.println("Aborted by " + issuer);
		System.exit(1);
	}

	/** Executes a global barrier. */
	public void barrier() {
		if (aborted) {
			return;
		}
		if (Config.STATISTICS) {
			Statistics.startingRuntimeBarrier();
		}
		globalBarrier.init();
		final MonitorThread monitor = logger.isInfoEnabled() ? new MonitorThread(60000, System.err, "barrier") {
			public void print(StringBuilder sb) {
				sb.append(getStatus(true));
				dumpReportToFile();
			}
		}.startMonitor() : null;
		Error unexpectedError = null;
		if (Config.STATISTICS) {
			Statistics.saveMemoryUsage();
		}
		int consecutiveProcessNoMessages = 0;
		int barrierLoops = 0, barrierLoopsWithUserMessagesProcessed = 0, barrierLoopsWithYield = 0;
		try {
			while (!globalBarrier.isDone()) {
				barrierLoops++;
				if (Config.POOLSIZE <= 1) {
					processSynchronizers();
				} else {
					if (Config.STATISTICS) {
						Statistics.aboutToProcessMessages();
					}
					final int processedUserMessages = processMessages();
					if (Config.STATISTICS) {
						Statistics.processedMessages();
					}
					if (processedUserMessages == 0) {
						if (processSynchronizers() > 0) {
							communication.flushAll();
						} else {
							if (consecutiveProcessNoMessages < Config.SKIP_STEPS_BEFORE_SENDING_SMALL_MESSAGE) {
								if (consecutiveProcessNoMessages == Config.YIELD_BEFORE_SENDING_SMALL_MESSAGE) {
									barrierLoopsWithYield++;
									Thread.yield();
								}
								consecutiveProcessNoMessages++;
							} else {
								consecutiveProcessNoMessages = 0;
								if (Config.FLUSH_BIGGEST) {
									communication.flushBiggest();
								} else {
									communication.flushAll();
								}
							}
						}
					} else {
						barrierLoopsWithUserMessagesProcessed++;
						consecutiveProcessNoMessages = 0;
					}
				}
				checkGlobalBarrier();
				if (Config.POOLSIZE > 1) {
					communication.flushBig();
				}
			}
			communication.flushAll();
		} catch (Error t) {
			unexpectedError = t;
			logger.error(loggerPrefix + "Caught unexpected error at " + getRank() + ": " + t.getMessage(), t);
			abort(t.getClass().getName());
		} catch (Throwable t) {
			logger.error(loggerPrefix + "Caught unexpected exception at " + getRank() + ": " + t.getMessage(), t);
			abort(t.getClass().getName());
		}
		if (monitor != null) {
			monitor.stopMonitor();
		}
		if (Config.STATISTICS) {
			Statistics.doneRuntimeBarrier(barrierLoops, barrierLoopsWithUserMessagesProcessed, barrierLoopsWithYield);
		}
		if (unexpectedError != null) {
			throw unexpectedError;
		}
	}

	private final void beNice() {
		if (aborted) {
			return;
		}
		if (Config.STATISTICS) {
			Statistics.startingRuntimeNice();
		}
		Error unexpectedError = null;
		if (Config.POOLSIZE > 1) {
			try {
				if (Config.STATISTICS) {
					Statistics.aboutToProcessMessages();
				}
				final int processedUserMessages = processMessages();
				if (Config.STATISTICS) {
					Statistics.processedMessages();
				}

				if (processedUserMessages > 0) {
					communication.flushAll();
				} else {
					communication.flushBig();
				}
			} catch (Error t) {
				unexpectedError = t;
				logger.error(loggerPrefix + "Caught unexpected exception at " + getRank() + ": " + t.getMessage());
				abort(t.getClass().getName());
			}
		}
		if (Config.STATISTICS) {
			Statistics.doneRuntimeNice();
		}
		if (unexpectedError != null) {
			throw unexpectedError;
		}
	}

	private int processSynchronizers() {
		int processedRuns = 0;
		int i = 0;
		if (Config.STATISTICS) {
			Statistics.processingSynchronizers(notDoneSynchronizersCount);
		}
		while (i < notDoneSynchronizersCount) {
			final Synchronizer synchronizer = notDoneSynchronizers[i];
			if (synchronizer.todo() > 0) {
				// almost does not happen.
				synchronizer.processStack();
				i++;
			} else {
				if (synchronizer.processRuns()) {
					processedRuns++;
				}
				if (synchronizer.isDone()) {
					logger.debug(loggerPrefix + "Removing synchronizer " + synchronizer.name());
					notDoneSynchronizersCount--;
					if (i != notDoneSynchronizersCount) {
						notDoneSynchronizers[i] = notDoneSynchronizers[notDoneSynchronizersCount];
					}
					notDoneSynchronizers[notDoneSynchronizersCount] = null;
				} else {
					i++;
				}
			}
		}
		return processedRuns;
	}

	private final int processMessages() {
		int processedBytes = 0;
		FastMessage message;
		do {
			message = communication.getFullReceivedMessage();
			if (message != null) {
				processedBytes += processMessage(message);
				communication.recycleReceivedMessage(message);
			} else {
				message = communication.getCurrentReceiveMessage();
				if (message != null) {
					processedBytes += processMessage(message);
				}
			}
		} while (message != null);
		return processedBytes;
	}

	private final int processMessage(FastMessage message) {
		int processedBytes = 0;
		while (message.sizeInReader() > 0) {
			final int size = message.availableContigRead();
			if (size > 0) {
				final int start = message.startContigRead();
				doProcessMessage(message, start, size);
				message.commitRead(start + size);
				processedBytes += size;
			}
		}
		if (Config.STATISTICS) {
			Statistics.messageProcessed(processedBytes);
		}
		return processedBytes;
	}

	private final void doProcessMessage(FastMessage message, int position, int size) {
		final byte[] buf = message.buf;
		final int endPosition = position + size;

		while (position < endPosition) {
			final int opcode = IOUtils.readInt(buf, position);
			position += IOUtils.INT_BYTES;

			if (opcode >= 0 || opcode == Synchronizer.OWNER_ALL) {
				final int originalPosition = position;

				/* user message */
				final int synchOwner = opcode;
				final int synchId = IOUtils.readInt(buf, position);
				position += IOUtils.INT_BYTES;
				final short graphId = IOUtils.readShort(buf, position);
				position += IOUtils.SHORT_BYTES;

				if (Config.ERRCHECK) {
					if (graphId < 0 || graphId >= graphs.length || graphs[graphId] == null) {
						throw new RuntimeException("Graph of id " + graphId + " does not exist");
					}
					if (synchId < 0 || synchId >= synchronizers.length) {
						throw new RuntimeException("Synchronizer with id " + synchId + "@" + synchOwner
								+ " does not exist at " + getRank());
					}
				}

				final short methodId = IOUtils.readShort(buf, position);
				position += IOUtils.SHORT_BYTES;

				final Synchronizer synchronizer = synchronizers[synchId];
				final LocalNode<?> node;

				final int type = graphType[graphId];

				if (type == GRAPH_EXPLICIT) {
					final ExplicitGraph<?> graph = (ExplicitGraph<?>) graphs[graphId];
					final int target = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					node = graph.node(target);
				} else if (type == GRAPH_ONTHEFLY) {
					final OnTheFlyGraph<?> graph = (OnTheFlyGraph<?>) graphs[graphId];
					final byte[] state = IOUtils.readByteArray(buf, position);
					final int stateBytes = IOUtils.bytesByteArray(state);
					position += stateBytes;
					node = graph.node(state);
				} else {
					throw new RuntimeException("Unrecognized graph with id " + graphId + " and type " + type);
				}

				if (synchronizer != null) {
					immediateDepth = 0;
					synchronizer.receivedBasicMessage();
					position = node.hipg_execute(methodId, synchronizer, buf, position);
				} else {
					// postpone the message
					final int paramCount = node.hipg_parameters(methodId, buf, position);
					postponeUserMessage(synchOwner, synchId, methodId, node, buf, position, paramCount);
					position += paramCount;
				}
				if (Config.STATISTICS) {
					Statistics.receivingUserMessage(IOUtils.INT_BYTES + position - originalPosition);
				}

			} else {

				switch (opcode) {

				/* global barrier token */
				case FastMessage.GLOBAL_BARRIER: {
					final int barrier = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int sum = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int master = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingGlobalBarrierMessage(IOUtils.INT_BYTES * 4);
					}

					globalBarrier.received(barrier, sum, master);
					break;
				}
				/* global barrier announce token */
				case FastMessage.GLOBAL_BARRIER_ANNOUNCE: {
					if (Config.STATISTICS) {
						Statistics.receivingGlobalBarrierMessage(IOUtils.INT_BYTES);
					}

					globalBarrier.receivedAnnounce();
					break;
				}
				/* barrier token */
				case FastMessage.BARRIER: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int barrier = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int sum = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int master = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingBarrierMessage(IOUtils.INT_BYTES * 6);
					}

					final Synchronizer synchronizer = getSynchronizer(synchronizerOwner, synchronizerId);
					if (synchronizer != null) {
						synchronizer.receivedBarrierToken(barrier, sum, master);
					} else {
						postponeTokenMessage(synchronizerOwner, synchronizerId, FastMessage.BARRIER, new Object[] {
								barrier, sum, master });
					}
					break;
				}
				/* barrier announce token */
				case FastMessage.BARRIER_ANNOUNCE: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingBarrierMessage(IOUtils.INT_BYTES * 3);
					}

					getSynchronizer(synchronizerOwner, synchronizerId).receivedBarrierAnnounceToken();
					break;
				}
				/* reduce token */
				case FastMessage.REDUCE: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int reduce = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final short reduceMethodId = IOUtils.readShort(buf, position);
					position += IOUtils.SHORT_BYTES;
					final byte[] result = IOUtils.readByteArray(buf, position);
					final int resultBytes = IOUtils.bytesByteArray(result);
					position += resultBytes;
					if (Config.STATISTICS) {
						Statistics.receivingReduceMessage(IOUtils.INT_BYTES * 4 + IOUtils.SHORT_BYTES + resultBytes);
					}

					final Synchronizer synchronizer = getSynchronizer(synchronizerOwner, synchronizerId);
					if (synchronizer != null)
						synchronizer.receivedReduceToken(reduce, reduceMethodId, result);
					else {
						postponeTokenMessage(synchronizerOwner, synchronizerId, FastMessage.REDUCE, new Object[] {
								reduce, reduceMethodId, result });
					}
					break;
				}
				/* reduce announce token */
				case FastMessage.REDUCE_ANNOUNCE: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int sender = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final byte[] result = IOUtils.readByteArray(buf, position);
					final int resultBytes = IOUtils.bytesByteArray(result);
					position += resultBytes;
					if (Config.STATISTICS) {
						Statistics.receivingReduceMessage(IOUtils.INT_BYTES * 4 + resultBytes);
					}

					getSynchronizer(synchronizerOwner, synchronizerId).receivedReduceAnnounceToken(result, sender);
					break;
				}
				/* barrier-and-reduce token */
				case FastMessage.BARRED: {
					final int synchOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int barrier = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int sum = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int master = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingBarrierReduceMessage(IOUtils.INT_BYTES * 6);
					}

					final Synchronizer synchronizer = getSynchronizer(synchOwner, synchId);
					if (synchronizer != null) {
						synchronizer.receivedBarrierReduceToken(barrier, sum, master);
					} else {
						postponeTokenMessage(synchOwner, synchId, FastMessage.BARRED, new Object[] { barrier, sum,
								master });
					}
					break;
				}
				/* barrier-and-reduce query token */
				case FastMessage.BARRED_QUERY: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final byte[] result = IOUtils.readByteArray(buf, position);
					final int resultBytes = IOUtils.bytesByteArray(result);
					position += resultBytes;
					if (Config.STATISTICS) {
						Statistics.receivingBarrierReduceMessage(IOUtils.INT_BYTES * 3 + resultBytes);
					}

					getSynchronizer(synchronizerOwner, synchronizerId).receivedBarrierReduceQueryToken(result);
					break;
				}
				/* barrier-and-reduce announce token */
				case FastMessage.BARRED_ANNOUNCE: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final byte[] result = IOUtils.readByteArray(buf, position);
					final int resultBytes = IOUtils.bytesByteArray(result);
					position += resultBytes;
					if (Config.STATISTICS) {
						Statistics.receivingBarrierReduceMessage(IOUtils.INT_BYTES * 3 + resultBytes);
					}

					getSynchronizer(synchronizerOwner, synchronizerId).receivedBarrierReduceAnnounceToken(result);
					break;
				}
				/* notification */
				case FastMessage.NOTIFICATION: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int id = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int iss = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final short notificationMethodId = IOUtils.readShort(buf, position);
					position += IOUtils.SHORT_BYTES;
					final byte[] value = IOUtils.readByteArray(buf, position);
					final int valueBytes = IOUtils.bytesByteArray(value);
					position += valueBytes;
					if (Config.STATISTICS) {
						Statistics.receivingBarrierReduceMessage(IOUtils.INT_BYTES * 5 + IOUtils.SHORT_BYTES
								+ valueBytes);
					}

					getSynchronizer(synchronizerOwner, synchronizerId).receivedNotificationToken(notificationMethodId,
							value, id, iss);
					break;
				}
				/* notification ack */
				case FastMessage.NOTIFICATION_ACK: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int id = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingNotificationMessage(IOUtils.INT_BYTES * 4);
					}

					getSynchronizer(synchronizerOwner, synchronizerId).receivedNotificationAck(id);
					break;
				}
				/* synchronizer id */
				case FastMessage.ID: {
					final int fatherId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int fatherOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int spawns = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int id = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingIdMessage(IOUtils.INT_BYTES * 5);
					}

					handleIdMessage(fatherId, fatherOwner, spawns, id);
					break;
				}
				/* child done */
				case FastMessage.CHDONE: {
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingChildDoneMessage(IOUtils.INT_BYTES * 2);
					}

					handleChildDoneMessage(synchronizerId);
					break;
				}
				/* ambassador delete */
				case FastMessage.ADEL: {
					final int synchronizerOwner = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					final int synchronizerId = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingRemoveAmbassadorMessage(IOUtils.INT_BYTES * 3);
					}

					handleRemoveAmbassadorMessage(synchronizerOwner, synchronizerId);
					break;
				}
				/* test */
				case FastMessage.TEST: {
					final String test = IOUtils.readString(buf, position);
					final int testBytes = IOUtils.bytesString(test);
					position += testBytes;
					if (Config.STATISTICS) {
						Statistics.receivingTestMessage(IOUtils.INT_BYTES + testBytes);
					}

					logger.warn(loggerPrefix + "Test message: <" + test + ">");
					break;
				}
				/* global abort message */
				case FastMessage.ABORT: {
					final int issuer = IOUtils.readInt(buf, position);
					position += IOUtils.INT_BYTES;
					if (Config.STATISTICS) {
						Statistics.receivingAbortMessage(IOUtils.INT_BYTES * 2);
					}

					handleAbortMessage(issuer);
					break;
				}
				default: {
					throw new RuntimeException("Unknown message opcode: " + opcode);
				}
				}
			}
		}
	}

	public void spawnOne(Synchronizer synchronizer) {
		spawn(getRank(), synchronizer, Synchronizer.EXECUTION_ONE);
	}

	public void spawnOwned(Synchronizer synchronizer) {
		spawn(getRank(), synchronizer, Synchronizer.EXECUTION_OWNED);
	}

	public void spawnAll(Synchronizer synchronizer) {
		spawn(getRank(), synchronizer, Synchronizer.EXECUTION_ALL);
	}

	public void spawnOne(int destination, Synchronizer synchronizer) {
		spawn(destination, synchronizer, Synchronizer.EXECUTION_ONE);
	}

	public void spawnOwned(int destination, Synchronizer synchronizer) {
		spawn(destination, synchronizer, Synchronizer.EXECUTION_OWNED);
	}

	public void spawn(int destination, Synchronizer synchronizer, int executionMode) {
		if (executionMode == Synchronizer.EXECUTION_ALL)
			destination = 0;

		synchronizer.setExecutionMode(executionMode);
		synchronizer.setOwner(executionMode == Synchronizer.EXECUTION_ALL ? Synchronizer.OWNER_ALL : destination);

		if (synchronizer.isRoot() || synchronizer.getFatherExecutionMode() == Synchronizer.EXECUTION_ALL) {

			if (synchronizer.getExecutionMode() == Synchronizer.EXECUTION_ALL
					|| (synchronizer.getExecutionMode() == Synchronizer.EXECUTION_OWNED && !synchronizer.isRoot())) {

				// all -> all | owned
				executeSpawn(synchronizer, false, true);

			} else {

				// all -> one and root -> owned
				if (isMe(destination))
					executeSpawn(synchronizer, false, false);

			}

		} else {

			// one | owned | root -> one | owned | all

			if (isMe(destination)) {
				executeSpawn(synchronizer, false, false);
			} else {
				mc++;
				communication.sendSynchronizerSpawnMessage(destination, synchronizer);
				synchronizer.invalidate();
			}

		}
	}

	private void checkGlobalBarrier() {
		if (globalBarrier != null && passive() && !globalBarrier.isDone()) {
			globalBarrier.progress();
		}
	}

	public long mc() {
		return mc;
	}

	public byte color() {
		return color;
	}

	public void whiten() {
		color = Barrier.WHITE;
	}

	public boolean passive() {
		return (notDoneSynchronizersCount == 0);
	}

	public int toProcess() {
		int n = 0;
		for (int i = 0; i < synchronizersCount; i++)
			if (synchronizers[i] != null) {
				n += synchronizers[i].todo();
			}
		return n;
	}

	public final String getStatus(final boolean detail) {
		return getStatus(detail, detail ? 10 : 0, detail ? 10 : 0, detail ? 2 : 0);
	}

	public final String getStatus(final boolean detail, final int maxSynchronizersToPrint,
			final int maxAmbassadorsToPrint, final int maxAmbassadorsToPrintPerSource) {
		final StringBuilder sb = new StringBuilder();
		sb.append(loggerPrefix + name() + " ");
		sb.append("global-barrier=" + globalBarrier + " ");
		sb.append("passive=" + passive() + " ");
		sb.append("synchronizers-not-done: " + notDoneSynchronizersCount + " ");
		if (detail) {
			sb.append("{");
			for (int i = 0; i < notDoneSynchronizersCount; i++) {
				sb.append(notDoneSynchronizers[i].name());
				if (i + 1 < notDoneSynchronizersCount) {
					sb.append(" ");
				}
			}
			sb.append("} ");
		}

		int printedSynchronizers = 0;
		for (int i = 0; i < synchronizers.length && printedSynchronizers < maxSynchronizersToPrint; i++) {
			if (synchronizers[i] != null) {
				sb.append(synchronizers[i].name() + " ");
				sb.append(synchronizers[i].getStatus(detail) + " ");
				printedSynchronizers++;
			}
		}

		if (ambassadors != null) {
			sb.append("ambassadors-not-done: ");
			int allAmbassadorsCount = 0;
			for (int i = 0; i < communication.getPoolSize(); ++i) {
				allAmbassadorsCount += ambassadorsCount[i];
			}
			sb.append(allAmbassadorsCount + " ");
			if (detail) {
				sb.append("{");
				int printedAmbassadors = 0;
				for (int i = 0; i < communication.getPoolSize(); i++) {
					int printedAmbassadorsPerSource = 0;
					for (int j = 0; j < ambassadorsCount[i] && printedAmbassadors < maxAmbassadorsToPrint
							&& printedAmbassadorsPerSource < maxAmbassadorsToPrintPerSource; ++j) {
						if (ambassadors[i][j] != null) {
							printedAmbassadors++;
							printedAmbassadorsPerSource++;
							sb.append(ambassadors[i][j].getStatus(detail));
						}
					}
				}
				sb.append("} ");
			}
		}
		return sb.toString();
	}

	public int getRootSpawns() {
		return rootSpawns;
	}

	public static boolean incImmediateDepth() {
		if (immediateDepth < Config.MAX_METHODS_IMMEDIATE) {
			immediateDepth++;
			return true;
		}
		return false;
	}

	public static void decImmediateDepth() {
		if (immediateDepth > 0) {
			immediateDepth--;
		}
	}

	public boolean aborted() {
		return aborted;
	}

	public String getAbortedMessage() {
		return abortedMessage;
	}
}
