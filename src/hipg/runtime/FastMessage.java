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
import ibis.ipl.ReadMessage;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

import java.io.IOException;

import myutils.IOUtils;
import myutils.ipc.FastContigMessageQueue;

/**
 * Message (buffer of final integers) with no locking.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 */
public final class FastMessage extends FastContigMessageQueue {

	/* message opcodes */

	/** Opcode: synchronizer barrier. */
	public static final int GLOBAL_BARRIER = -1001;
	/** Opcode: announce synchronizer barrier. */
	public static final int GLOBAL_BARRIER_ANNOUNCE = -1002;
	/** Opcode: synchronizer barrier. */
	public static final int BARRIER = -1003;
	/** Opcode: announce synchronizer barrier. */
	public static final int BARRIER_ANNOUNCE = -1004;
	/** Opcode: synchronizer reduce. */
	public static final int REDUCE = -1005;
	/** Opcode: synchronizer reduce announce. */
	public static final int REDUCE_ANNOUNCE = -1006;
	/** Opcode: synchronizer barrier-reduce. */
	public static final int BARRED = -1007;
	/** Opcode: synchronizer barrier-reduce announce. */
	public static final int BARRED_QUERY = -1008;
	/** Opcode: synchronizer barrier-reduce query answer. */
	public static final int BARRED_ANSWER = -1009;
	/** Opcode: synchronizer barrier-reduce announce. */
	public static final int BARRED_ANNOUNCE = -1010;
	/** Opcode: synchronizer notification method. */
	public static final int NOTIFICATION = -1011;
	/** Opcode: synchronizer notification reception acknowledgment. */
	public static final int NOTIFICATION_ACK = -1012;
	/** Opcode: global abort. */
	public static final int ABORT = -1014;
	/** Opcode: id message. */
	public static final int ID = -1015;
	/** Opcode: child done. */
	public static final int CHDONE = -1016;
	/** Opcode: ambassador delete. */
	public static final int ADEL = -1017;
	/** Opcode: test. */
	public static final int TEST = -1018;

	/** Opcode: synchronizer spawn (new synchronizer). */
	public static final int SSPAWN = -2000;
	/** Opcode: ambassador spawn. */
	public static final int ASPAWN = -2001;

	/** Message destination. */
	private SendPort sp;

	/**
	 * Creates a new message (allocates buffer).
	 */
	public FastMessage(final int bufsize) {
		super(bufsize);
		for (int i = 0; i < buf.length; ++i) {
			buf[i] = 17;
		}
	}

	public void set(SendPort sp) {
		this.sp = sp;
	}

	void addGlobalBarrierToken(final int position, final int length, final int barrier, final int sum, final int master) {
		if (Config.STATISTICS) {
			Statistics.sendingGlobalBarrierMessage(length);
		}
		IOUtils.write4Ints(GLOBAL_BARRIER, barrier, sum, master, buf, position);
		assert (length == IOUtils.INT_BYTES * 4);
		commitWrite(position + length);
	}

	void addGlobalBarrierAnnounceToken(final int position, final int length) {
		if (Config.STATISTICS) {
			Statistics.sendingGlobalBarrierMessage(length);
		}
		IOUtils.writeInt(GLOBAL_BARRIER_ANNOUNCE, buf, position);
		assert (length == IOUtils.INT_BYTES);
		commitWrite(position + length);
	}

	void addBarrierToken(final int position, final int length, final Synchronizer synchronizer, final int barrier,
			final int sum, final int master) {
		if (Config.STATISTICS) {
			Statistics.sendingBarrierMessage(length);
		}
		IOUtils.write6Ints(BARRIER, synchronizer.getOwner(), synchronizer.getId(), barrier, sum, master, buf, position);
		assert (length == IOUtils.INT_BYTES * 6);
		commitWrite(position + length);
	}

	void addBarrierAnnounceToken(final int position, final int length, final int synchronizerOwner,
			final int synchronizerId) {
		if (Config.STATISTICS) {
			Statistics.sendingBarrierMessage(length);
		}
		IOUtils.write3Ints(BARRIER_ANNOUNCE, synchronizerOwner, synchronizerId, buf, position);
		assert (length == IOUtils.INT_BYTES * 3);
		commitWrite(position + length);
	}

	void addReduceToken(final int position, final int length, final int synchronizerOwner, final int synchronizerId,
			final int reduce, final short reduceMethodId, final byte[] result) {
		if (Config.STATISTICS) {
			Statistics.sendingReduceMessage(length);
		}
		IOUtils.write4Ints(REDUCE, synchronizerOwner, synchronizerId, reduce, buf, position);
		int tempPosition = position + IOUtils.INT_BYTES * 4;
		IOUtils.writeShort(reduceMethodId, buf, tempPosition);
		tempPosition += IOUtils.SHORT_BYTES;
		IOUtils.writeByteArray(result, buf, tempPosition);
		assert (length == 4 * IOUtils.INT_BYTES + IOUtils.SHORT_BYTES + IOUtils.bytesByteArray(result));
		commitWrite(position + length);
	}

	void addReduceAnnounceToken(final int position, final int length, final int synchronizerOwner,
			final int synchronizerId, final byte[] result, final int sender) {
		if (Config.STATISTICS) {
			Statistics.sendingReduceMessage(length);
		}
		IOUtils.write4Ints(REDUCE_ANNOUNCE, synchronizerOwner, synchronizerId, sender, buf, position);
		IOUtils.writeByteArray(result, buf, position + 4 * IOUtils.INT_BYTES);
		assert (length == 4 * IOUtils.INT_BYTES + IOUtils.bytesByteArray(result));
		commitWrite(position + length);
	}

	void addBarrierReduceToken(final int position, final int length, final int synchronizerOwner,
			final int synchronizerId, final int barrier, final int sum, final int master) {
		if (Config.STATISTICS) {
			Statistics.sendingBarrierReduceMessage(length);
		}
		IOUtils.write6Ints(BARRED, synchronizerOwner, synchronizerId, barrier, sum, master, buf, position);
		assert (length == 6 * IOUtils.INT_BYTES);
		commitWrite(position + length);
	}

	void addBarrierReduceQueryToken(final int position, final int length, final int synchronizerOwner,
			final int synchronizerId, byte[] result) {
		if (Config.STATISTICS) {
			Statistics.sendingBarrierReduceMessage(length);
		}
		IOUtils.write3Ints(BARRED_QUERY, synchronizerOwner, synchronizerId, buf, position);
		IOUtils.writeByteArray(result, buf, position + IOUtils.INT_BYTES * 3);
		assert (length == 3 * IOUtils.INT_BYTES + IOUtils.bytesByteArray(result));
		commitWrite(position + length);
	}

	void addBarrierReduceAnnounceToken(final int position, final int length, final Synchronizer synchronizer,
			final byte[] result) {
		if(Config.STATISTICS) {
		Statistics.sendingBarrierReduceMessage(length);
		}
		IOUtils.write3Ints(BARRED_ANNOUNCE, synchronizer.getOwner(), synchronizer.getId(), buf, position);
		IOUtils.writeByteArray(result, buf, position + IOUtils.INT_BYTES * 3);
		assert (length == IOUtils.INT_BYTES * 3 + IOUtils.bytesByteArray(result));
		commitWrite(position + length);
	}

	void addNotificationToken(final int position, final int length, final Synchronizer synchronizer,
			final short notificationMethodId, final byte[] result, final int id, final int issuer) {
		if (Config.STATISTICS) {
			Statistics.sendingNotificationMessage(length);
		}
		IOUtils.write5Ints(NOTIFICATION, synchronizer.getOwner(), synchronizer.getId(), id, issuer, buf, position);
		int tempPosition = position + IOUtils.INT_BYTES * 5;
		IOUtils.writeShort(notificationMethodId, buf, tempPosition);
		tempPosition += IOUtils.SHORT_BYTES;
		IOUtils.writeByteArray(result, buf, tempPosition);
		assert (length == IOUtils.INT_BYTES * 5 + IOUtils.SHORT_BYTES + IOUtils.bytesByteArray(result));
		commitWrite(position + length);
	}

	void addNotificationAckToken(final int position, final int length, final Synchronizer synchronizer, final int id) {
		if (Config.STATISTICS) {
			Statistics.sendingNotificationMessage(length);
		}
		IOUtils.write4Ints(NOTIFICATION_ACK, synchronizer.getOwner(), synchronizer.getId(), id, buf, position);
		assert (length == 4 * IOUtils.INT_BYTES);
		commitWrite(position + length);
	}

	void addChildDoneMessage(final int position, final int length, final int synchronizerId) {
		if (Config.STATISTICS) {
			Statistics.sendingChildDoneMessage(length);
		}
		IOUtils.write2Ints(CHDONE, synchronizerId, buf, position);
		assert (length == 2 * IOUtils.INT_BYTES);
		commitWrite(position + length);
	}

	void addRemoveAmbassadorMessage(final int position, final int length, final Synchronizer synchronizer) {
		if (Config.STATISTICS) {
			Statistics.sendingRemoveAmbassadorMessage(length);
		}
		IOUtils.write3Ints(ADEL, synchronizer.getOwner(), synchronizer.getId(), buf, position);
		assert (length == 3 * IOUtils.INT_BYTES);
		commitWrite(position + length);
	}

	void addSynchronizerSpawnMessage(final Synchronizer synchronizer) {
		throw new RuntimeException("Sending objects not implemented!");
		// final int paramsPosition = paramAppend;
		// params[paramsPosition++] = SSPAWN;
		// params[paramsPosition++] = synchronizer;
		// commitParams(paramsPosition);
		// if (Config.COARSETIMING)
		// controlMessagesCount++;
	}

	void addAmbassadorSpawnMessage(final Synchronizer synchronizer) {
		throw new RuntimeException("Sending objects not implemented!");
		// final int paramsPosition = paramAppend;
		// params[paramsPosition++] = ASPAWN;
		// params[paramsPosition++] = synchronizer;
		// commitParams(paramsPosition);
		// if (Config.COARSETIMING)
		// controlMessagesCount++;
	}

	void addIdMessage(final int position, final int length, final int fatherId, final int fatherOwner,
			final int spawns, final int id) {
		if (Config.STATISTICS) {
			Statistics.sendingIdMessage(length);
		}
		IOUtils.write5Ints(ID, fatherId, fatherOwner, spawns, id, buf, position);
		assert (length == 5 * IOUtils.INT_BYTES);
		commitWrite(position + length);
	}

	void addTestMessage(final int position, final int length, final String msg) {
		if (Config.STATISTICS) {
			Statistics.sendingTestMessage(length);
		}
		IOUtils.writeInt(TEST, buf, position);
		IOUtils.writeString(msg, buf, position + IOUtils.INT_BYTES);
		assert (length == IOUtils.INT_BYTES + IOUtils.bytesString(msg));
		commitWrite(position + length);
	}

	void addAbortMessage(final int position, final int length, final int issuer, final String msg) {
		if (Config.STATISTICS) {
			Statistics.sendingAbortMessage(length);
		}
		IOUtils.write2Ints(ABORT, issuer, buf, position);
		IOUtils.writeString(msg, buf, position + IOUtils.INT_BYTES * 2);
		assert (length == IOUtils.INT_BYTES * 2 + IOUtils.bytesString(msg));
		commitWrite(position + length);
	}

	void flush() throws IOException {
		while (sizeInReader() > 0) {
			final int size = availableContigRead();
			if (size > 0) {
				if (Config.STATISTICS) {
					Statistics.startingFlush(size);
				}
				final int start = startContigRead();
				final WriteMessage message = sp.newMessage();
				message.writeInt(size);
				message.writeArray(buf, start, size);
				final long bytes = message.finish();
				commitRead(start + size);
				if (Config.STATISTICS) {
					Statistics.flushDone(bytes);
				}
			}
		}
	}

	public void append(final int position, final int addSize, ReadMessage readMessage) throws IOException,
			ClassNotFoundException {
		readMessage.readArray(buf, position, addSize);
		commitWrite(position + addSize);
	}
}
