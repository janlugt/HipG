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
import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisCreationFailedException;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.MessageUpcall;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.RegistryEventHandler;
import ibis.ipl.SendPort;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import myutils.IOUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Communication interface.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 */
public final class Communication implements RegistryEventHandler, MessageUpcall {
	/** Logging facilities. */
	private static final Logger logger = LoggerFactory.getLogger(Communication.class);
	private final String loggerPrefix;

	/** Ibis configuration. */
	private static final IbisCapabilities ibisCapabilities = new IbisCapabilities(IbisCapabilities.ELECTIONS_STRICT,
			IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED);

	private static final PortType portType = new PortType(PortType.COMMUNICATION_RELIABLE,
			PortType.CONNECTION_MANY_TO_ONE, Config.OBJECT_SERIALIZATION ? PortType.SERIALIZATION_OBJECT_IBIS
					: PortType.SERIALIZATION_DATA, PortType.RECEIVE_AUTO_UPCALLS);

	/** This ibis. */
	private final Ibis ibis;

	/** This ibis' identifier. */
	private final IbisIdentifier identifier;

	/** This ibis' name. */
	private final String name;

	/** This ibis' rank. */
	private final int rank;

	/** This ibis's rank proposal, established during joining. */
	private int rankProposal = -1;

	/** Send ports. */
	private final SendPort[] sendPorts = new SendPort[Config.POOLSIZE];

	/** Receive ports. */
	private final ReceivePort receivePort;

	/** Pool of participating ibises. */
	private final IbisIdentifier[] pool = new IbisIdentifier[Config.POOLSIZE];

	/** Pool size (to add new ibises). */
	private volatile int currentPoolSize;

	/** Current send messages. */
	private final VolatileMessage[] currentSendMessage = new VolatileMessage[Config.POOLSIZE];

	/** Current receive message. */
	private volatile FastMessage currentReceiveMessage = null;

	/** Full messages to send. */
	@SuppressWarnings("unchecked")
	private final BlockingQueue<FastMessage>[] fullMessagesToSend = (BlockingQueue<FastMessage>[]) new LinkedBlockingQueue[Config.POOLSIZE];

	/** Full messages received. */
	private final BlockingQueue<FastMessage> fullMessagesReceived = new LinkedBlockingQueue<FastMessage>();

	/** List of free messages for sending (smaller ones). */
	final BlockingQueue<FastMessage> freeMessagesToSend = new LinkedBlockingQueue<FastMessage>();

	/** List of free messages for receiving (larger ones). */
	final BlockingQueue<FastMessage> freeMessagesToReceive = new LinkedBlockingQueue<FastMessage>();

	/** Sender */
	private final Sender sender;

	/** Creates communication. */
	public Communication() throws IbisCreationFailedException {
		logger.debug("Creating communication");

		ibis = IbisFactory.createIbis(ibisCapabilities, this, portType);
		identifier = ibis.identifier();
		name = identifier.name();
		loggerPrefix = "(" + name + ") ";
		String rName = "to_" + name;
		try {
			receivePort = ibis.createReceivePort(portType, rName, this);
		} catch (Throwable t) {
			throw new RuntimeException("Could not create receivePort", t);
		}
		receivePort.enableConnections();
		ibis.registry().enableEvents();
		logger.debug(loggerPrefix + "Waiting for rank");
		awaitRankProposal();
		rank = rankProposal;
		logger.debug(loggerPrefix + "Got rank " + rank);
		logger.debug(loggerPrefix + "Communication created");

		awaitPool(Config.POOLSIZE);
		allocateBasicBuffers();
		allocateAdditionalBuffers();
		logger.debug(loggerPrefix + "Buffers initialized");
		if (Config.POOLSIZE > 1) {
			sender = new Sender(this);
			sender.start();
		} else {
			sender = null;
		}
		receivePort.enableMessageUpcalls();
		logger.debug(loggerPrefix + "Communication created");
	}

	public IbisIdentifier getIdentifier() {
		return identifier;
	}

	private IbisIdentifier getIdentifier(final int owner) {
		return pool[owner];
	}

	public String getName() {
		return name;
	}

	public int getRank() {
		return rank;
	}

	public int getPoolSize() {
		return Config.POOLSIZE;
	}

	private final FastMessage getMessage(final int rank) {
		return currentSendMessage[rank].message;
	}

	/** Returns new send buffer. */
	private static FastMessage allocateNewSendBuffer() {
		if (Config.STATISTICS) {
			Statistics.allocatedNewSendBuffer(Config.getSendBufferSize());
		}
		return new FastMessage(Config.getSendBufferSize());
	}

	/** Returns new receive buffer. */
	private static FastMessage allocateNewReceiveBuffer() {
		if (Config.STATISTICS) {
			Statistics.allocatedNewReceiveBuffer(Config.getRecvBufferSize());
		}
		return new FastMessage(Config.getRecvBufferSize());
	}

	/** Return a special (especially large) receive buffer. */
	private FastMessage getNewSpecialReceiveMessage(final int minCapacity) {
		for (FastMessage message : freeMessagesToReceive) {
			if (message.capacity() > minCapacity) {
				freeMessagesToReceive.remove(message);
				return message;
			}
		}
		if (Config.STATISTICS) {
			Statistics.allocatedNewReceiveBuffer(minCapacity);
		}
		return new FastMessage(minCapacity);
	}

	/** Return a special (especially large) send buffer. */
	private FastMessage getNewSpecialSendMessage(final int minCapacity) {
		for (FastMessage message : freeMessagesToSend) {
			if (message.capacity() > minCapacity) {
				freeMessagesToSend.remove(message);
				return message;
			}
		}
		if (Config.STATISTICS) {
			Statistics.allocatedNewReceiveBuffer(minCapacity);
		}
		return new FastMessage(minCapacity);
	}

	/** Retrieve or create a new message that has enough space. Returns the old message. */
	private FastMessage getNewSendMessage(final int dest, final int length, FastMessage oldMessage) {
		// Get free send message.
		FastMessage freeMessage = freeMessagesToSend.poll();
		if (freeMessage == null) {
			// No free messages, allocate new one.
			freeMessage = allocateNewSendBuffer();
		}
		// The free message is too small
		if (freeMessage.capacity() < length + 1) {
			// Return it and get a special message.
			freeMessagesToSend.add(freeMessage);
			freeMessage = getNewSpecialSendMessage(length + 1);
		}
		freeMessage.set(sendPorts[dest]);
		currentSendMessage[dest].message = freeMessage;
		fullMessagesToSend[dest].offer(oldMessage);
		return freeMessage;
	}

	/** Allocates all necessary send and receive buffers (the current messages and outgoing buffers). */
	private void allocateBasicBuffers() {
		if (Config.POOLSIZE > 1) {
			for (int dest = 0; dest < Config.POOLSIZE; dest++) {
				if (dest != rank) {
					currentSendMessage[dest] = new VolatileMessage(allocateNewSendBuffer());
					currentSendMessage[dest].message.set(sendPorts[dest]);
					fullMessagesToSend[dest] = new LinkedBlockingQueue<FastMessage>();
				}
			}
			currentReceiveMessage = allocateNewReceiveBuffer();
		}
	}

	/** Allocates additional buffers for sending and receiving. */
	private void allocateAdditionalBuffers() {
		if (Config.POOLSIZE > 1) {
			for (int i = 0; i < Config.getNumSendBuffers(); i++) {
				freeMessagesToSend.add(allocateNewSendBuffer());
			}
			for (int i = 0; i < Config.getNumReceiveBuffers(); i++) {
				freeMessagesToReceive.add(allocateNewReceiveBuffer());
			}
		}
	}

	/** Handles an upcall. */
	public void upcall(final ReadMessage readMessage) throws IOException, ClassNotFoundException {
		final int size = readMessage.readInt();
		if (Config.STATISTICS) {
			Statistics.upcallReceived(size);
		}
		assert (size > 0);
		int position = currentReceiveMessage.startContigWrite(size);
		if (position < 0) {
			// Current receive buffer full. Give it up and get a new buffer.
			fullMessagesReceived.offer(currentReceiveMessage);
			FastMessage freeMessage = freeMessagesToReceive.poll();
			if (freeMessage == null) {
				// No free receive buffer. Allocate a new one.
				freeMessage = allocateNewReceiveBuffer();
				if (Config.STATISTICS) {
					Statistics.upcallGoesToNewBuffer();
				}
			} else {
				if (Config.STATISTICS) {
					Statistics.upcallGoesToReclaimedBuffer();
				}
			}
			// Try again to write.
			position = freeMessage.startContigWrite(size);
			if (position < 0) {
				// This must be a special message, which is bigger than the receive buffer
				// (for example a reduce message). Return the message back to free messages.
				freeMessagesToReceive.add(freeMessage);
				freeMessage = getNewSpecialReceiveMessage(size + 1);
				position = currentReceiveMessage.startContigWrite(size);
				if (Config.STATISTICS) {
					Statistics.upcallGoesToSpecialBuffer();
				}
			}
			// Add the current message to full messages.
			currentReceiveMessage = freeMessage;
		}
		assert (position >= 0);
		currentReceiveMessage.append(position, size, readMessage);
		if (Config.STATISTICS) {
			Statistics.upcallProcessed(readMessage.bytesRead());
		}
	}

	/** Flushes all "big" messages. */
	void flushBig() {
		if (sender != null && fullMessagesToSend != null) {
			sender.synchRequestBig();
			// sender.requestBig();
		}
	}

	/** Flushes all "big" messages. */
	void flushBiggest() {
		if (sender != null && fullMessagesToSend != null) {
			sender.synchRequestBiggest();
			// sender.requestBig();
		}
	}

	/** Flushes all messages. */
	void flushAll() {
		if (sender != null && fullMessagesToSend != null) {
			sender.synchRequestAll();
			// sender.requestAll();
		}
	}

	public FastMessage getFullSendMessage(final int dest) {
		if (fullMessagesToSend == null || fullMessagesToSend[dest] == null) {
			return null;
		}
		return fullMessagesToSend[dest].poll();
	}

	public FastMessage getCurrentSendMessage(final int dest) {
		if (currentSendMessage != null && currentSendMessage[dest] != null
				&& currentSendMessage[dest].message.sizeInWriter() > 0) {
			return currentSendMessage[dest].message;
		}
		return null;
	}

	void recycleSentMessage(final FastMessage message) {
		message.clear();
		freeMessagesToSend.add(message);
	}

	public FastMessage getFullReceivedMessage() {
		return fullMessagesReceived.poll();
	}

	public FastMessage getCurrentReceiveMessage() {
		if (currentReceiveMessage.sizeInReader() > 0) {
			return currentReceiveMessage;
		}
		return null;
	}

	void recycleReceivedMessage(final FastMessage message) {
		message.clear();
		freeMessagesToReceive.add(message);
	}

	public int previousRank() {
		if (Config.POOLSIZE == 1) {
			return 0;
		}
		if (rank == 0) {
			return Config.POOLSIZE - 1;
		}
		return rank - 1;
	}

	public int nextRank() {
		if (Config.POOLSIZE == 1) {
			return 0;
		}
		if (rank == Config.POOLSIZE - 1) {
			return 0;
		}
		return rank + 1;
	}

	/** Blocks until the rank is proposed. */
	private void awaitRankProposal() {
		if (rankProposal < 0) {
			logger.debug(loggerPrefix + "Awaiting rank proposal");
			while (rankProposal < 0) {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
				}
			}
			logger.debug(loggerPrefix + "Got rank " + rankProposal);
		}
	}

	/** Blocks until the pool is of the specified size. */
	public void awaitPool(final int size) {
		logger.debug(loggerPrefix + "Awaiting pool of size " + size);
		synchronized (this) {
			while (currentPoolSize < size) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
		}
		logger.debug(loggerPrefix + "Pool size of " + size + " reached");
	}

	/** Handles an election result. */
	public void electionResult(String election, IbisIdentifier result) {
		assert (false);
	}

	/** Connects this Ibis to all other Ibises. */
	private void connect(final IbisIdentifier joiningIbis, final int joiningRank, final int maxAttempts) {
		final String name = joiningIbis.name();
		boolean success = false;
		int attempts = maxAttempts;
		Throwable lastThrown = null;
		do {
			try {
				attempts--;
				final String sName = "to_" + name;
				final SendPort sp = ibis.createSendPort(portType);
				logger.debug(loggerPrefix + "Connecting to " + name);
				sp.connect(joiningIbis, sName, 200, true);
				logger.debug(loggerPrefix + "Connected to " + name);
				pool[joiningRank] = joiningIbis;
				sendPorts[joiningRank] = sp;
				success = true;
				if (Config.STATISTICS) {
					Statistics.openedConnection();
				}
			} catch (Throwable t) {
				if (Config.STATISTICS) {
					Statistics.openeConnectionsFailed();
				}
				lastThrown = t;
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
		} while ((maxAttempts == 0 || attempts < maxAttempts) && !success);
		if (!success) {
			logger.error(loggerPrefix + "Could not connect to newly joined " + name + ": " + lastThrown.getMessage(),
					lastThrown);
			Runtime.getRuntime().abort("Could not connect to " + name);
		}
	}

	/** Handles a joined Ibis. */
	public void joined(IbisIdentifier joiningIbis) {
		final boolean itsme = (joiningIbis.compareTo(ibis.identifier()) == 0);
		final int joiningRank;
		synchronized (this) {
			joiningRank = currentPoolSize;
		}
		logger.debug(loggerPrefix + " Joined by " + joiningIbis + (itsme ? " (It's me)" : ""));
		if (!itsme) {
			connect(joiningIbis, joiningRank, 10);
		}
		synchronized (this) {
			currentPoolSize++;
			if (itsme) {
				rankProposal = joiningRank;
			}
			notify();
		}
	}

	/** Handles a leaving Ibis. */
	synchronized public void left(IbisIdentifier leavingIbis) {
		logger.debug(loggerPrefix + "Ibis " + leavingIbis + "left ");
	}

	/** Handles a dead Ibis. */
	synchronized public void died(IbisIdentifier dyingIbis) {
		logger.warn(loggerPrefix + "Ibis " + dyingIbis + " died");
	}

	/** Handles a signal from an Ibis. */
	public void gotSignal(String signal, IbisIdentifier signallingIbis) {
		logger.error(loggerPrefix + "Unexpected signal " + signal + " from " + signallingIbis);
	}

	/** Handles pool closed. */
	public void poolClosed() {
		logger.warn(loggerPrefix + "Pool closed");
	}

	/** Handles pool terminated. */
	public void poolTerminated(IbisIdentifier signallingIbis) {
		logger.warn(loggerPrefix + "Pool terminated, signalled by " + signallingIbis.name());
	}

	/** Handles not being able to communicate with an Ibis. */
	void handleCouldNotCommunicate(final int dest, Throwable t) {
		IbisIdentifier id = getIdentifier(dest);
		logger.warn(loggerPrefix + "Could not communicate with " + id + ": " + t, t);
		t.printStackTrace();
		try {
			ibis.registry().maybeDead(id);
		} catch (IOException e) {
			logger.warn(loggerPrefix + "Failed " + "maybeDead: " + e.getMessage(), e);
		}
	}

	/** Closes this communication. */
	public void close() {
		logger.debug(loggerPrefix + "Closing communication");
		try {
			if (sender != null) {
				sender.close();
			}
		} catch (Throwable t) {
			logger.warn(loggerPrefix + "Could not close sender: " + t.getMessage());
		}
		if (ibis != null) {
			try {
				ibis.end();
			} catch (Throwable e) {
				logger.warn(loggerPrefix + "Could not close ibis: " + e.getMessage());
			}
		}
		logger.debug(loggerPrefix + "Communication closed");
	}

	public FastMessage getUserMessage(final int dest, final Synchronizer synchronizer, final int paramCount) {
		final int length = IOUtils.INT_BYTES * 3 + IOUtils.SHORT_BYTES * 2 + paramCount;
		synchronizer.sendingBasicMessage();
		if (Config.STATISTICS) {
			Statistics.gettingUserMessage(length);
		}
		FastMessage m = getMessage(dest);
		int position = m.startContigWrite(length);
		if (position < 0) {
			m = getNewSendMessage(dest, length, m);
			position = m.startContigWrite(length);
			assert (position >= 0);
		}
		m.position = position;
		return m;
	}

	void sendGlobalBarrierToken(final int barrier, final int sum, final int master) {
		final int length = IOUtils.INT_BYTES * 4;
		final int dest = nextRank();
		FastMessage m = getMessage(dest);
		int position = m.startContigWrite(length);
		if (position < 0) {
			m = getNewSendMessage(dest, length, m);
			position = m.startContigWrite(length);
			assert (position >= 0);
		}
		m.addGlobalBarrierToken(position, length, barrier, sum, master);
	}

	void sendGlobalBarrierAnnounceToken() {
		final int length = IOUtils.INT_BYTES;
		for (int dest = 0; dest < Config.POOLSIZE; dest++) {
			if (dest != rank) {
				FastMessage m = getMessage(dest);
				int position = m.startContigWrite(length);
				if (position < 0) {
					m = getNewSendMessage(dest, length, m);
					position = m.startContigWrite(length);
					assert (position >= 0);
				}
				m.addGlobalBarrierAnnounceToken(position, length);
			}
		}
	}

	void sendBarrierToken(final Synchronizer synchronizer, final int barrier, final int sum, final int master) {
		final int length = IOUtils.INT_BYTES * 6;
		final int dest = nextRank();
		FastMessage m = getMessage(dest);
		int position = m.startContigWrite(length);
		if (position < 0) {
			m = getNewSendMessage(dest, length, m);
			position = m.startContigWrite(length);
			assert (position >= 0);
		}
		m.addBarrierToken(position, length, synchronizer, barrier, sum, master);
	}

	void sendBarrierAnnounceToken(final Synchronizer synchronizer) {
		final int length = IOUtils.INT_BYTES * 3;
		for (int dest = 0; dest < Config.POOLSIZE; dest++) {
			if (dest != rank) {
				FastMessage m = getMessage(dest);
				int position = m.startContigWrite(length);
				if (position < 0) {
					m = getNewSendMessage(dest, length, m);
					position = m.startContigWrite(length);
					assert (position >= 0);
				}
				m.addBarrierAnnounceToken(position, length, synchronizer.getOwner(), synchronizer.getId());
			}
		}
	}

	void sendReduceToken(final int issuerOwner, final int issuerId, final int reduce, final short reduceMethodId,
			final byte[] result) {
		final int length = IOUtils.INT_BYTES * 4 + IOUtils.SHORT_BYTES + IOUtils.bytesByteArray(result);
		final int dest = nextRank();
		FastMessage m = getMessage(dest);
		int position = m.startContigWrite(length);
		if (position < 0) {
			m = getNewSendMessage(dest, length, m);
			position = m.startContigWrite(length);
			assert (position >= 0);
		}
		m.addReduceToken(position, length, issuerOwner, issuerId, reduce, reduceMethodId, result);
	}

	void sendReduceAnnounceToken(final int issuerOwner, final int issuerId, final byte[] result) {
		final int length = IOUtils.INT_BYTES * 4 + IOUtils.bytesByteArray(result);
		for (int dest = 0; dest < Config.POOLSIZE; dest++)
			if (dest != rank) {
				FastMessage m = getMessage(dest);
				int position = m.startContigWrite(length);
				if (position < 0) {
					m = getNewSendMessage(dest, length, m);
					position = m.startContigWrite(length);
					assert (position >= 0);
				}
				m.addReduceAnnounceToken(position, length, issuerOwner, issuerId, result, getRank());
			}
	}

	void sendBarrierReduceToken(final int issuerOwner, final int issuerId, final int barrier, final int sum,
			final int master) {
		final int length = IOUtils.INT_BYTES * 6;
		final int dest = nextRank();
		FastMessage m = getMessage(dest);
		int position = m.startContigWrite(length);
		if (position < 0) {
			m = getNewSendMessage(dest, length, m);
			position = m.startContigWrite(length);
			assert (position >= 0);
		}
		m.addBarrierReduceToken(position, length, issuerOwner, issuerId, barrier, sum, master);
	}

	void sendBarrierReduceQueryToken(final int issuerOwner, final int issuerId, final byte[] result) {
		final int length = IOUtils.INT_BYTES * 3 + IOUtils.bytesByteArray(result);
		final int dest = nextRank();
		FastMessage m = getMessage(dest);
		int position = m.startContigWrite(length);
		if (position < 0) {
			m = getNewSendMessage(dest, length, m);
			position = m.startContigWrite(length);
			assert (position >= 0);
		}
		m.addBarrierReduceQueryToken(position, length, issuerOwner, issuerId, result);
	}

	void sendBarrierReduceAnnounceAllToken(final Synchronizer synchronizer, final byte[] result) {
		final int length = IOUtils.INT_BYTES * 3 + IOUtils.bytesByteArray(result);
		for (int dest = 0; dest < Config.POOLSIZE; dest++) {
			if (dest != rank) {
				FastMessage m = getMessage(dest);
				int position = m.startContigWrite(length);
				if (position < 0) {
					m = getNewSendMessage(dest, length, m);
					position = m.startContigWrite(length);
					assert (position >= 0);
				}
				m.addBarrierReduceAnnounceToken(position, length, synchronizer, result);
			}
		}
	}

	void sendNotification(final Synchronizer synchronizer, final short notificationMethodId, final byte[] value,
			final int id) {
		final int length = IOUtils.INT_BYTES * 5 + IOUtils.SHORT_BYTES + IOUtils.bytesByteArray(value);
		for (int dest = 0; dest < Config.POOLSIZE; dest++) {
			if (dest != rank) {
				FastMessage m = getMessage(dest);
				int position = m.startContigWrite(length);
				if (position < 0) {
					m = getNewSendMessage(dest, length, m);
					position = m.startContigWrite(length);
					assert (position >= 0);
				}
				m.addNotificationToken(position, length, synchronizer, notificationMethodId, value, id,
						Runtime.getRank());
			}
		}
	}

	void sendNotificationAck(final Synchronizer synchronizer, final int dest, final int id) {
		final int length = IOUtils.INT_BYTES * 4;
		FastMessage m = getMessage(dest);
		int position = m.startContigWrite(length);
		if (position < 0) {
			m = getNewSendMessage(dest, length, m);
			position = m.startContigWrite(length);
			assert (position >= 0);
		}
		m.addNotificationAckToken(position, length, synchronizer, id);
	}

	void sendSynchronizerSpawnMessage(final int dest, final Synchronizer synchronizer) {
		// getMessage(dest).addSynchronizerSpawnMessage(synchronizer);
	}

	void sendAmbassadorSpawnMessages(final Synchronizer synchronizer) {
		// for (int dest = 0; dest < Config.POOLSIZE; dest++) {
		// if (dest != rank) {
		// getMessage(dest).addAmbassadorSpawnMessage(synchronizer);
		// }
		// }
	}

	void sendIdMessages(final Synchronizer synchronizer) {
		final int fatherId = synchronizer.getFatherId();
		final int fatherOwner = synchronizer.getFatherOwner();
		final int spawns = synchronizer.isRoot() ? Runtime.getRuntime().getRootSpawns() : synchronizer.getFatherSpawn();
		final int id = synchronizer.getId();
		final int length = IOUtils.INT_BYTES * 5;
		for (int dest = 0; dest < Config.POOLSIZE; dest++) {
			if (dest != rank) {
				FastMessage m = getMessage(dest);
				int position = m.startContigWrite(length);
				if (position < 0) {
					m = getNewSendMessage(dest, length, m);
					position = m.startContigWrite(length);
					assert (position >= 0);
				}
				m.addIdMessage(position, length, fatherId, fatherOwner, spawns, id);
			}
		}
	}

	void sendChildDoneMessage(final int dest, final int fatherId) {
		final int length = IOUtils.INT_BYTES * 2;
		FastMessage m = getMessage(dest);
		int position = m.startContigWrite(length);
		if (position < 0) {
			m = getNewSendMessage(dest, length, m);
			position = m.startContigWrite(length);
			assert (position >= 0);
		}
		m.addChildDoneMessage(position, length, fatherId);
	}

	void sendRemoveAmbassadorMessages(final Synchronizer synchronizer) {
		final int length = IOUtils.INT_BYTES * 3;
		for (int dest = 0; dest < Config.POOLSIZE; dest++) {
			if (dest != rank) {
				FastMessage m = getMessage(dest);
				int position = m.startContigWrite(length);
				if (position < 0) {
					m = getNewSendMessage(dest, length, m);
					position = m.startContigWrite(length);
					assert (position >= 0);
				}
				m.addRemoveAmbassadorMessage(position, length, synchronizer);
			}
		}
	}

	void sendAbortMessage(String msg) {
		final int length = IOUtils.INT_BYTES * 2 + IOUtils.bytesString(msg);
		for (int dest = 0; dest < Config.POOLSIZE; dest++) {
			if (dest != rank) {
				FastMessage m = getMessage(dest);
				int position = m.startContigWrite(length);
				if (position < 0) {
					m = getNewSendMessage(dest, length, m);
					position = m.startContigWrite(length);
					assert (position >= 0);
				}
				if (m != null) {
					m.addAbortMessage(position, length, rank, msg);
				}
			}
		}
	}

	/**
	 * Try desperately to free some memory to finish gracefully after we got an out-of-memory error (do not care about
	 * result of the computation).
	 */
	public void desperateFree() {
		freeMessagesToSend.clear();
		freeMessagesToReceive.clear();
		for (int i = 0; i < fullMessagesToSend.length; i++) {
			fullMessagesToSend[i] = null;
		}
		currentReceiveMessage.clear();
		for (int i = 0; i < currentSendMessage.length; i++) {
			currentSendMessage[i].message.clear();
		}
	}

	/** A volatile message. */
	private static final class VolatileMessage {
		volatile FastMessage message;

		public VolatileMessage(FastMessage m) {
			message = m;
		}

	}
}
