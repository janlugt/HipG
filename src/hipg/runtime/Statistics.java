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

package hipg.runtime;

import hipg.Config;

import java.util.Vector;

import myutils.ConversionUtils;
import myutils.MathUtils;
import myutils.ValueGraph;
import myutils.system.SystemUtils;
import myutils.system.ThreadSleeper;
import myutils.tuple.triple.FinalLongTriple;

public final class Statistics {

	private static void append(final StringBuilder sb, final String variableName, final long variableValue,
			final String prefix, final String delimiter) {
		append(sb, variableName, Long.toString(variableValue), prefix, delimiter);
	}

	private static void append(final StringBuilder sb, final String variableName, final double variableValue,
			final String prefix, final String delimiter) {
		append(sb, variableName, Double.toString(MathUtils.round3(variableValue)), prefix, delimiter);
	}

	private static void append(final StringBuilder sb, final String variableName, final String variableValue,
			final String prefix, final String delimiter) {
		sb.append(prefix);
		sb.append(variableName);
		sb.append(" = ");
		sb.append(variableValue);
		sb.append(delimiter);
	}

	private static void appendTable4(final StringBuilder sb, final String variableName, final long value1,
			final long value2, final long value3, final long value4, final String prefix, final String delimiter) {
		sb.append(prefix);
		sb.append(variableName);
		sb.append(" = ");
		sb.append(String.valueOf(value1));
		sb.append(" ");
		sb.append(String.valueOf(value2));
		sb.append(" ");
		sb.append(String.valueOf(value3));
		sb.append(" ");
		sb.append(String.valueOf(value4));
		sb.append(" ");
		sb.append(delimiter);
	}

	/* Runtime */

	private static Vector<Long> runtimeBarriersTime = new Vector<Long>();
	private static Vector<Integer> runtimeBarriersLoops = new Vector<Integer>();
	private static Vector<Integer> runtimeBarriersLoopsWithUserMessagesProcessed = new Vector<Integer>();
	private static Vector<Integer> runtimeBarriersLoopsWithYield = new Vector<Integer>();
	private static long numRuntimeNices = 0;
	private static long runtimeNicesTotalTime = 0, runtimeNicesStartTime = 0;

	public static void startingRuntimeBarrier() {
		runtimeBarriersTime.add(System.nanoTime());
	}

	public static void doneRuntimeBarrier(int barrierLoops, int barrierLoopsWithUserMessagesProcessed,
			int barrierLoopsWithYield) {
		final long startTime = runtimeBarriersTime.remove(runtimeBarriersTime.size() - 1);
		runtimeBarriersTime.add(System.nanoTime() - startTime);
		runtimeBarriersLoops.add(barrierLoops);
		runtimeBarriersLoopsWithUserMessagesProcessed.add(barrierLoopsWithUserMessagesProcessed);
		runtimeBarriersLoopsWithYield.add(barrierLoopsWithYield);
	}

	public static void startingRuntimeNice() {
		numRuntimeNices++;
		runtimeNicesStartTime = System.nanoTime();
	}

	public static void doneRuntimeNice() {
		runtimeNicesTotalTime = System.nanoTime() - runtimeNicesStartTime;
	}

	public static void getRuntimeReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "rank", Runtime.getRank(), prefix, delimiter);
		append(sb, "poolSize", Runtime.getPoolSize(), prefix, delimiter);
		append(sb, "numRuntimeBarriers", runtimeBarriersTime.size(), prefix, delimiter);
		for (int i = 0; i < runtimeBarriersTime.size(); ++i) {
			final String time = i < runtimeBarriersTime.size() ? String.valueOf(ConversionUtils
					.ns2sec(runtimeBarriersTime.get(i))) : "?";
			final String loops = i < runtimeBarriersLoops.size() ? String.valueOf(runtimeBarriersLoops.get(i)) : "?";
			final String loopsWithUserMessagesProcessed = i < runtimeBarriersLoopsWithUserMessagesProcessed.size() ? String
					.valueOf(runtimeBarriersLoopsWithUserMessagesProcessed.get(i)) : "?";
			final String loopsWithYield = i < runtimeBarriersLoopsWithYield.size() ? String
					.valueOf(runtimeBarriersLoopsWithYield.get(i)) : "?";
			append(sb, "runtimeBarrier-" + i + ":Time", time, prefix, delimiter);
			append(sb, "runtimeBarrier-" + i + ":Loops", loops, prefix, delimiter);
			append(sb, "runtimeBarrier-" + i + ":LoopsWithUserMessagesProcessed", loopsWithUserMessagesProcessed,
					prefix, delimiter);
			append(sb, "runtimeBarrier-" + i + ":LoopsWithYield", loopsWithYield, prefix, delimiter);
		}
		append(sb, "numRuntimeNices", numRuntimeNices, prefix, delimiter);
		append(sb, "runtimeNicesTotalTime", ConversionUtils.ns2sec(runtimeNicesTotalTime), prefix, delimiter);
	}

	/* Global barrier */
	private static long totalNumGlobalBarriers = 0;
	private static long globalBarrierInitializations = 0;
	private static long globalBarrierPostpones = 0;

	public static void newGlobalBarrier() {
		totalNumGlobalBarriers++;
	}

	public static void globalBarrierInitialized() {
		globalBarrierInitializations++;
	}

	public static void globalBarrierPostponed() {
		globalBarrierPostpones++;
	}

	public static void getGlobalBarriersReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "totalNumGlobalBarriers", totalNumGlobalBarriers, prefix, delimiter);
		append(sb, "globalBarrierInitializations", globalBarrierInitializations, prefix, delimiter);
		append(sb, "globalBarrierPostpones", globalBarrierPostpones, prefix, delimiter);
	}

	/* Sender. */
	private static long senderTotalSleepTime = 0;
	private static long senderStartSleepTime = -1;
	private static long senderNumFlushesFull = 0;

	public static void senderGoingToSleep() {
		if (Config.TIMING) {
			senderStartSleepTime = System.nanoTime();
		}
	}

	public static void senderWakingUp() {
		if (Config.TIMING) {
			senderTotalSleepTime += System.nanoTime() - senderStartSleepTime;
		}
	}

	public static void senderFlushFull() {
		senderNumFlushesFull++;
	}

	public static void getSenderReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "senderSleep", ConversionUtils.ns2sec(senderTotalSleepTime), prefix, delimiter);
		append(sb, "senderNumFlushesFull", senderNumFlushesFull, prefix, delimiter);
	}

	/* Connection statistics. */

	private static long openedConnections = 0;
	private static long openedConnectionsFailedAttempts = 0;

	public static void openedConnection() {
		openedConnections++;
	}

	public static void openeConnectionsFailed() {
		openedConnectionsFailedAttempts++;
	}

	public static void getOpenConnectionsReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "openedConnections", openedConnections, prefix, delimiter);
		append(sb, "openedConnectionsFailedAttempts", openedConnectionsFailedAttempts, prefix, delimiter);
	}

	/* Send and receive buffers. */

	private static long allocatedSendBuffers = 0;
	private static long allocatedSendBuffersTotalLength = 0;
	private static long allocatedReceiveBuffers = 0;
	private static long allocatedReceiveBuffersTotalLength = 0;

	public static void allocatedNewSendBuffer(final int size) {
		allocatedSendBuffers++;
		allocatedSendBuffersTotalLength += size;
	}

	public static void allocatedNewReceiveBuffer(final int size) {
		allocatedReceiveBuffers++;
		allocatedReceiveBuffersTotalLength += size;
	}

	public static void getAllocatedBuffersReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "allocatedSendBuffers", allocatedSendBuffers, prefix, delimiter);
		append(sb, "allocatedSendBuffersTotalLength",
				ConversionUtils.bytes2MB(allocatedSendBuffersTotalLength) + " MB", prefix, delimiter);
		append(sb, "allocatedReceiveBuffers", allocatedReceiveBuffers, prefix, delimiter);
		append(sb, "allocatedReceiveBuffersTotalLength", ConversionUtils.bytes2MB(allocatedReceiveBuffersTotalLength)
				+ " MB", prefix, delimiter);
	}

	/* Sent and received messages. */

	private static long sentUserMessages = 0, sentUserMessagesTotalLength = 0;
	private static long receivedUserMessages = 0, receivedUserMessagesTotalLength = 0;
	private static long sentGlobalBarrierMessages = 0, sentGlobalBarrierMessagesTotalLength = 0;
	private static long receivedGlobalBarrierMessages = 0, receivedGlobalBarrierMessagesTotalLength = 0;
	private static long sentBarrierMessages = 0, sentBarrierMessagesTotalLength = 0;
	private static long receivedBarrierMessages = 0, receivedBarrierMessagesTotalLength = 0;
	private static long sentReduceMessages = 0, sentReduceMessagesTotalLength = 0;
	private static long receivedReduceMessages = 0, receivedReduceMessagesTotalLength = 0;
	private static long sentBarrierReduceMessages = 0, sentBarrierReduceMessagesTotalLength = 0;
	private static long receivedBarrierReduceMessages = 0, receivedBarrierReduceMessagesTotalLength = 0;
	private static long sentNotificationMessages = 0, sentNotificationMessagesTotalLength = 0;
	private static long receivedNotificationMessages = 0, receivedNotificationMessagesTotalLength = 0;
	private static long sentIdMessages = 0, sentIdMessagesTotalLength = 0;
	private static long receivedIdMessages = 0, receivedIdMessagesTotalLength = 0;
	private static long sentChildDoneMessages = 0, sentChildDoneMessagesTotalLength = 0;
	private static long receivedChildDoneMessages = 0, receivedChildDoneMessagesTotalLength = 0;
	private static long sentRemoveAmbassadorMessages = 0, sentRemoveAmbassadorMessagesTotalLength = 0;
	private static long receivedRemoveAmbassadorMessages = 0, receivedRemoveAmbassadorMessagesTotalLength = 0;
	private static long sentTestMessages = 0, sentTestMessagesTotalLength = 0;
	private static long receivedTestMessages = 0, receivedTestMessagesTotalLength = 0;
	private static long sentAbortMessages = 0, sentAbortMessagesTotalLength = 0;
	private static long receivedAbortMessages = 0, receivedAbortMessagesTotalLength = 0;

	public static void gettingUserMessage(final int size) {
		sentUserMessages++;
		sentUserMessagesTotalLength += size;
	}

	public static void receivingUserMessage(final int size) {
		receivedUserMessages++;
		receivedUserMessagesTotalLength += size;
	}

	public static void sendingGlobalBarrierMessage(final int size) {
		sentGlobalBarrierMessages++;
		sentGlobalBarrierMessagesTotalLength += size;
	}

	public static void receivingGlobalBarrierMessage(final int size) {
		receivedGlobalBarrierMessages++;
		receivedGlobalBarrierMessagesTotalLength += size;
	}

	public static void sendingBarrierMessage(final int size) {
		sentBarrierMessages++;
		sentBarrierMessagesTotalLength += size;
	}

	public static void receivingBarrierMessage(final int size) {
		receivedBarrierMessages++;
		receivedBarrierMessagesTotalLength += size;
	}

	public static void sendingReduceMessage(final int size) {
		sentBarrierMessages++;
		sentBarrierMessagesTotalLength += size;
	}

	public static void receivingReduceMessage(final int size) {
		receivedBarrierMessages++;
		receivedBarrierMessagesTotalLength += size;
	}

	public static void sendingBarrierReduceMessage(final int size) {
		sentBarrierReduceMessages++;
		sentBarrierReduceMessagesTotalLength += size;
	}

	public static void receivingBarrierReduceMessage(final int size) {
		receivedBarrierReduceMessages++;
		receivedBarrierReduceMessagesTotalLength += size;
	}

	public static void sendingNotificationMessage(final int size) {
		sentNotificationMessages++;
		sentNotificationMessagesTotalLength += size;
	}

	public static void receivingNotificationMessage(final int size) {
		receivedNotificationMessages++;
		receivedNotificationMessagesTotalLength += size;
	}

	public static void sendingIdMessage(final int size) {
		sentIdMessages++;
		sentIdMessagesTotalLength += size;
	}

	public static void receivingIdMessage(final int size) {
		receivedIdMessages++;
		receivedIdMessagesTotalLength += size;
	}

	public static void sendingChildDoneMessage(final int size) {
		sentChildDoneMessages++;
		sentChildDoneMessagesTotalLength += size;
	}

	public static void receivingChildDoneMessage(final int size) {
		receivedChildDoneMessages++;
		receivedChildDoneMessagesTotalLength += size;
	}

	public static void sendingRemoveAmbassadorMessage(final int size) {
		sentRemoveAmbassadorMessages++;
		sentRemoveAmbassadorMessagesTotalLength += size;
	}

	public static void receivingRemoveAmbassadorMessage(final int size) {
		receivedRemoveAmbassadorMessages++;
		receivedRemoveAmbassadorMessagesTotalLength += size;
	}

	public static void sendingTestMessage(final int size) {
		sentTestMessages++;
		sentTestMessagesTotalLength += size;
	}

	public static void receivingTestMessage(final int size) {
		receivedTestMessages++;
		receivedTestMessagesTotalLength += size;
	}

	public static void sendingAbortMessage(final int size) {
		sentAbortMessages++;
		sentAbortMessagesTotalLength += size;
	}

	public static void receivingAbortMessage(final int size) {
		receivedAbortMessages++;
		receivedAbortMessagesTotalLength += size;
	}

	public static void getMessagesReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "", "sent sentLength received receivedLength", prefix, delimiter);
		appendTable4(sb, "userMessages", sentUserMessages, sentUserMessagesTotalLength, receivedUserMessages,
				receivedUserMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "globalBarrierMessages", sentGlobalBarrierMessages, sentGlobalBarrierMessagesTotalLength,
				receivedGlobalBarrierMessages, receivedGlobalBarrierMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "barrierMessages", sentBarrierMessages, sentBarrierMessagesTotalLength,
				receivedBarrierMessages, receivedBarrierMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "reduceMessages", sentReduceMessages, sentReduceMessagesTotalLength, receivedReduceMessages,
				receivedReduceMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "barrierReduceMessages", sentBarrierReduceMessages, sentBarrierReduceMessagesTotalLength,
				receivedBarrierReduceMessages, receivedBarrierReduceMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "notificationMessages", sentNotificationMessages, sentNotificationMessagesTotalLength,
				receivedNotificationMessages, receivedNotificationMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "idMessages", sentIdMessages, sentIdMessagesTotalLength, receivedIdMessages,
				receivedIdMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "childDoneMessages", sentChildDoneMessages, sentChildDoneMessagesTotalLength,
				receivedChildDoneMessages, receivedChildDoneMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "removeAmbassadorMessages", sentRemoveAmbassadorMessages,
				sentRemoveAmbassadorMessagesTotalLength, receivedRemoveAmbassadorMessages,
				receivedRemoveAmbassadorMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "testMessages", sentTestMessages, sentTestMessagesTotalLength, receivedTestMessages,
				receivedTestMessagesTotalLength, prefix, delimiter);
		appendTable4(sb, "abortMessages", sentAbortMessages, sentAbortMessagesTotalLength, receivedAbortMessages,
				receivedAbortMessagesTotalLength, prefix, delimiter);
	}

	/* Sent and received bytes. */

	// Sent bytes
	private static long numFlushes = 0;
	private static long bytesFlushed = 0;
	private static long logicalBytesFlushed = 0;
	private static long flushTime = 0;
	private static long flushTimeStartTime = 0;

	// Received bytes
	private static long numUpcalls = 0;
	private static long bytesReceived = 0;
	private static long logicalBytesReceived = 0;
	private static long logicalBytesProcessed = 0;
	private static long upcallTime = 0;
	private static long upcallStartTime = 0;

	// Memory obtained to store an upcall.
	private static long upcallGoesToNewBuffer;
	private static long upcallGoesToReclaimedBuffer;
	private static long upcallGoesToSpecialBuffer;

	public static void startingFlush(final int size) {
		numFlushes++;
		logicalBytesFlushed += size;
		if (Config.TIMING) {
			flushTimeStartTime = System.nanoTime();
		}
	}

	public static void flushDone(final long size) {
		bytesFlushed += size;
		if (Config.TIMING) {
			flushTime += System.nanoTime() - flushTimeStartTime;
		}
	}

	public static void upcallReceived(final int size) {
		numUpcalls++;
		logicalBytesReceived += size;
		if (Config.TIMING) {
			upcallStartTime = System.nanoTime();
		}
	}

	public static void upcallGoesToNewBuffer() {
		upcallGoesToNewBuffer++;
	}

	public static void upcallGoesToReclaimedBuffer() {
		upcallGoesToReclaimedBuffer++;
	}

	public static void upcallGoesToSpecialBuffer() {
		upcallGoesToSpecialBuffer++;
	}

	public static void upcallProcessed(final long bytes) {
		bytesReceived += bytes;
		if (Config.TIMING) {
			upcallTime += System.nanoTime() - upcallStartTime;
		}
	}

	public static void messageProcessed(final int size) {
		logicalBytesProcessed += size;
	}

	public static void getBytesReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "numFlushes", numFlushes, prefix, delimiter);
		append(sb, "numUpcalls", numUpcalls, prefix, delimiter);
		append(sb, "bytesSent", bytesFlushed, prefix, delimiter);
		append(sb, "bytesReceived", bytesReceived, prefix, delimiter);
		append(sb, "logicalBytesToSend", logicalBytesFlushed, prefix, delimiter);
		append(sb, "logicalBytesReceived", logicalBytesReceived, prefix, delimiter);
		append(sb, "logicalBytesProcessed", logicalBytesProcessed, prefix, delimiter);
		append(sb, "flushTime", ConversionUtils.ns2sec(flushTime), prefix, delimiter);
		append(sb, "upcallTime", ConversionUtils.ns2sec(upcallTime), prefix, delimiter);
		append(sb, "avgUserMessagesPerUpcall", (double) sentUserMessages / (double) numUpcalls, prefix, delimiter);
		append(sb, "avgBytesPerUpcall", (double) logicalBytesReceived / (double) numUpcalls, prefix, delimiter);
		append(sb, "avgBytesPerUserMessage", (double) logicalBytesReceived / (double) receivedUserMessages, prefix,
				delimiter);
		append(sb, "upcallGoesToCurrent", numUpcalls - upcallGoesToNewBuffer - upcallGoesToReclaimedBuffer
				- upcallGoesToSpecialBuffer, prefix, delimiter);
		append(sb, "upcallGoesToNewBuffer", upcallGoesToNewBuffer, prefix, delimiter);
		append(sb, "upcallGoesToReclaimedBuffer", upcallGoesToReclaimedBuffer, prefix, delimiter);
		append(sb, "upcallGoesToSpecialBuffer", upcallGoesToSpecialBuffer, prefix, delimiter);
	}

	/* Postponed messages. */

	private static long postponedUserMessages = 0;
	private static long postponedMessages = 0;
	private static long postponedTokens = 0;

	public static void postponingUserMessage() {
		postponedUserMessages++;
	}

	public static void postponingMessage() {
		postponedMessages++;
	}

	public static void postponingToken() {
		postponedTokens++;
	}

	public static void getPostponedMessagesReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "postponedUserMessages", postponedUserMessages, prefix, delimiter);
		append(sb, "postponedMessages", postponedMessages, prefix, delimiter);
		append(sb, "postponedTokens", postponedTokens, prefix, delimiter);
	}

	/* Calls from runtime to processSynchronizers. */

	private static long processSynchronizersNumCalls = 0;
	private static long processSynchronizersNotDoneSynchronizersSum = 0;

	public static void processingSynchronizers(int numNotDoneSynchronizers) {
		processSynchronizersNumCalls++;
		processSynchronizersNotDoneSynchronizersSum += numNotDoneSynchronizers;
	}

	public static void getProcessedSynchronizersReport(final StringBuilder sb, final String prefix,
			final String delimiter) {
		append(sb, "processSynchronizersNumCalls", synchronizersProcessStackNumCalls, prefix, delimiter);
		append(sb, "processSynchronizersNotDoneSynchronizersSum", (double) processSynchronizersNotDoneSynchronizersSum
				/ (double) processSynchronizersNumCalls, prefix, delimiter);
	}

	/* Calls from runtime to processMessages. */

	private static long processMessagesNumCalls = 0;
	private static long processMessagesTimeStart = 0;
	private static long processMessagesTime = 0;

	public static void aboutToProcessMessages() {
		processMessagesTimeStart = System.nanoTime();
	}

	public static void processedMessages() {
		processMessagesNumCalls++;
		processMessagesTime += System.nanoTime() - processMessagesTimeStart;
	}

	public static void getProcessedMessagesReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "processMessagesNumCalls", processMessagesNumCalls, prefix, delimiter);
		append(sb, "processMessagesTime", ConversionUtils.ns2sec(processMessagesTime), prefix, delimiter);
	}

	/* memory */

	private static long memoryUpdatesCount = 0;

	private static long maxTotalMemory = 0;
	private static long maxUsedMemory = 0;
	private static long graphUsedMemory = 0;
	private static long maxMemory = 0;

	private static boolean realMemoryUnAvailable = true;
	private static long realTotalMemoryUnixMin = -1;
	private static long realTotalMemoryUnixMax = -1;
	private static long realUsedMemoryUnixMin = -1;
	private static long realUsedMemoryUnixMax = -1;
	private static long realFreeMemoryUnixMin = -1;
	private static long realFreeMemoryUnixMax = -1;

	private static void updateRealMemory() {
		if (!realMemoryUnAvailable) {
			final FinalLongTriple memInfo = SystemUtils.getMemoryInfoUnix();
			if (memInfo != null) {
				final long totalMem = memInfo.getFirst();
				final long usedMem = memInfo.getSecond();
				final long freeMem = memInfo.getThird();
				if (totalMem < realTotalMemoryUnixMin || realTotalMemoryUnixMin < 0) {
					realTotalMemoryUnixMin = totalMem;
				}
				if (totalMem > realTotalMemoryUnixMax || realTotalMemoryUnixMax < 0) {
					realTotalMemoryUnixMax = totalMem;
				}
				if (usedMem < realUsedMemoryUnixMin || realUsedMemoryUnixMin < 0) {
					realUsedMemoryUnixMin = usedMem;
				}
				if (usedMem > realUsedMemoryUnixMax || realUsedMemoryUnixMax < 0) {
					realUsedMemoryUnixMax = usedMem;
				}
				if (freeMem < realFreeMemoryUnixMin || realFreeMemoryUnixMin < 0) {
					realFreeMemoryUnixMin = freeMem;
				}
				if (freeMem > realFreeMemoryUnixMax || realFreeMemoryUnixMax < 0) {
					realFreeMemoryUnixMax = freeMem;
				}
			} else {
				realMemoryUnAvailable = true;
			}
		}
	}

	public static void saveGraphMemoryUsage() {
		memoryUpdatesCount++;

		long currFree = java.lang.Runtime.getRuntime().freeMemory();
		long currTotal = java.lang.Runtime.getRuntime().totalMemory();
		long currMax = java.lang.Runtime.getRuntime().maxMemory();
		long currUsed = currTotal - currFree;

		if (currMax > maxMemory)
			maxMemory = currMax;
		if (currUsed > graphUsedMemory)
			graphUsedMemory = currUsed;

		updateRealMemory();
	}

	public static void saveMemoryUsage() {
		memoryUpdatesCount++;

		long currFree = java.lang.Runtime.getRuntime().freeMemory();
		long currTotal = java.lang.Runtime.getRuntime().totalMemory();
		long currMax = java.lang.Runtime.getRuntime().maxMemory();
		long currUsed = currTotal - currFree;

		if (currMax > maxMemory)
			maxMemory = currMax;
		if (currTotal > maxTotalMemory)
			maxTotalMemory = currTotal;
		if (currUsed > maxUsedMemory)
			maxUsedMemory = currUsed;

		updateRealMemory();
	}

	public static void getMemoryReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "memoryUpdatesCount", memoryUpdatesCount, prefix, delimiter);
		append(sb, "maxMemory", MathUtils.round2(ConversionUtils.bytes2GB(maxMemory)) + " GB", prefix, delimiter);
		append(sb, "maxTotalMemory", MathUtils.round2(ConversionUtils.bytes2GB(maxTotalMemory)) + " GB", prefix,
				delimiter);
		append(sb, "maxUsedMemory", MathUtils.round2(ConversionUtils.bytes2GB(maxUsedMemory)) + " GB", prefix,
				delimiter);
		append(sb, "graphUsedMemory", MathUtils.round2(ConversionUtils.bytes2GB(graphUsedMemory)) + " GB", prefix,
				delimiter);
		append(sb, "realMemoryUnAvailable", realMemoryUnAvailable ? "true" : "false", prefix, delimiter);
		append(sb, "realTotalMemoryUnixMin",
				MathUtils.round2(ConversionUtils.bytes2GB(realTotalMemoryUnixMin)) + " GB", prefix, delimiter);
		append(sb, "realTotalMemoryUnixMax",
				MathUtils.round2(ConversionUtils.bytes2GB(realTotalMemoryUnixMax)) + " GB", prefix, delimiter);
		append(sb, "realUsedMemoryUnixMin", MathUtils.round2(ConversionUtils.bytes2GB(realUsedMemoryUnixMin)) + " GB",
				prefix, delimiter);
		append(sb, "realUsedMemoryUnixMax", MathUtils.round2(ConversionUtils.bytes2GB(realUsedMemoryUnixMax)) + " GB",
				prefix, delimiter);
		append(sb, "realFreeMemoryUnixMin", MathUtils.round2(ConversionUtils.bytes2GB(realFreeMemoryUnixMin)) + " GB",
				prefix, delimiter);
		append(sb, "realFreeMemoryUnixMax", MathUtils.round2(ConversionUtils.bytes2GB(realFreeMemoryUnixMax)) + " GB",
				prefix, delimiter);
	}

	/* Synchronizers */

	// Barriers.
	private static long totalNumBarriers = 0;
	private static long barrierInitializations = 0;
	private static long barrierPostpones = 0;

	// Reduces.
	private static long totalNumReduces = 0;
	private static long reduceInitializations = 0;
	private static long reducePostpones = 0;

	// Barrier&Reduces.
	private static long totalNumBarrierAndReduces = 0;
	private static long barrierAndReduceInitializations = 0;
	private static long barrierAndReducePostpones = 0;

	// Notifications.
	private static long totalNumNotifications = 0;

	// Stack
	private static long synchronizersProcessStackNumCalls = 0;
	private static long synchronizersProcessStackSum = 0;

	public static void newBarrier() {
		totalNumBarriers++;
	}

	public static void barrierInitialized() {
		barrierInitializations++;
	}

	public static void barrierPostponed() {
		barrierPostpones++;
	}

	public static void newReduce() {
		totalNumReduces++;
	}

	public static void reduceInitialized() {
		reduceInitializations++;
	}

	public static void reducePosponing() {
		reducePostpones++;
	}

	public static void newBarrierAndReduce() {
		totalNumBarrierAndReduces++;
	}

	public static void barrierAndReduceInitialized() {
		barrierAndReduceInitializations++;
	}

	public static void barrierAndReducePosponing() {
		barrierAndReducePostpones++;
	}

	public static void newNotification() {
		totalNumNotifications++;
	}

	public static void processedStack(int numElements) {
		synchronizersProcessStackSum += numElements;
		synchronizersProcessStackNumCalls++;
	}

	public static void getSynchronizerReport(final StringBuilder sb, final String prefix, final String delimiter) {
		append(sb, "totalNumBarriers", totalNumBarriers, prefix, delimiter);
		append(sb, "barrierInitializations", barrierInitializations, prefix, delimiter);
		append(sb, "barrierPospones", barrierPostpones, prefix, delimiter);

		append(sb, "totalNumReduces", totalNumReduces, prefix, delimiter);
		append(sb, "reduceInitializations", reduceInitializations, prefix, delimiter);
		append(sb, "reducePospones", reducePostpones, prefix, delimiter);

		append(sb, "totalNumBarrierAndReduces", totalNumBarrierAndReduces, prefix, delimiter);
		append(sb, "barrierAndReduceInitializations", barrierAndReduceInitializations, prefix, delimiter);
		append(sb, "barrierAndReducePospones", barrierAndReducePostpones, prefix, delimiter);

		append(sb, "totalNumNotifications", totalNumNotifications, prefix, delimiter);

		append(sb, "synchronizersProcessStackNumCalls", synchronizersProcessStackNumCalls, prefix, delimiter);
		append(sb, "synchronizersProcessStackAvg", (double) synchronizersProcessStackSum
				/ (double) synchronizersProcessStackNumCalls, prefix, delimiter);
	}

	/** Returns the entire report. */
	public static String getReport() {
		final String prefix = "   ", delimiter = "\n";
		final StringBuilder sb = new StringBuilder();
		sb.append("Runtime:" + delimiter);
		try {
			getRuntimeReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Global barrier: " + delimiter);
		try {
			getGlobalBarriersReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Sender: " + delimiter);
		try {
			getSenderReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Connections:" + delimiter);
		try {
			getOpenConnectionsReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Allocated buffers: " + delimiter);
		try {
			getAllocatedBuffersReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Messages: " + delimiter);
		try {
			getMessagesReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Bytes: " + delimiter);
		try {
			getBytesReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Postponed messages: " + delimiter);
		try {
			getPostponedMessagesReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Memory: " + delimiter);
		try {
			getMemoryReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Processed synchronizers: " + delimiter);
		try {
			getProcessedSynchronizersReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Processed messages: " + delimiter);
		try {
			getProcessedMessagesReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		sb.append("Synchronizers:" + delimiter);
		try {
			getSynchronizerReport(sb, prefix, delimiter);
		} catch (Throwable t) {
			sb.append("exception: " + t.getMessage());
		}
		return sb.toString();
	}

	/* Throughput. */

	private static ThroughputLogger throughputLogger;
	private static ValueGraph bytesSentGraph, bytesReceivedGraph;

	public static void startLoggingThroughput() {
		if (Config.THROUGHPUT) {
			bytesSentGraph = new ValueGraph();
			bytesReceivedGraph = new ValueGraph();
			throughputLogger = new ThroughputLogger();
		}
	}

	public static void stopLoggingThroughput() {
		if (throughputLogger != null) {
			throughputLogger.close();
		}
	}

	private static class ThroughputLogger extends Thread {
		private volatile boolean end = false;

		public void close() {
			end = true;
		}

		public void run() {
			while (!end) {
				bytesSentGraph.addValue(bytesFlushed);
				bytesReceivedGraph.addValue(bytesReceived);
				if (!end) {
					ThreadSleeper.sleepMs(500);
				}
			}
		}
	}

	public static String getSentBytesThroughput() {
		return bytesSentGraph.toString();
	}

	public static String getReceivedBytesThroughput() {
		return bytesReceivedGraph.toString();
	}
}
