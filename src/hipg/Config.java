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

package hipg;

import ibis.util.TypedProperties;

/**
 * HipG config.
 * 
 * @author ela, ekr@cs.vu.nl
 */
public final class Config {

	static final TypedProperties properties = new TypedProperties();

	static {
		properties.loadFromClassPath("hipg.properties");
		properties.addProperties(System.getProperties());
	}

	public static String REPORT_FILE_BASE_NAME = properties.getProperty("hipg.reportFileBaseName", null);

	public static boolean CREATE_COMMUNICATION = properties.getBooleanProperty("hipg.createCommunication", true);;

	/** Internal redundant checks (for debugging). */
	public static final boolean ERRCHECK = properties.getBooleanProperty("hipg.errCheck", false);

	/** Internal fine debug information (for debuggging). */
	public static final boolean FINEDEBUG = properties.getBooleanProperty("hipg.fineDebug", false);

	public static final boolean STATISTICS = properties.getBooleanProperty("hipg.statistics", false);

	/** Internal fine timing information (for debugging). */
	public static final boolean TIMING = properties.getBooleanProperty("hipg.timing", false);

	/** Throughput printing (for debugging). */
	public static final boolean THROUGHPUT = properties.getBooleanProperty("hipg.throughput", false);

	/** Maximum number of synchronizers. */
	public static final int MAXSYNCHRONIZERS = properties.getIntProperty("hipg.maxSynchronizers", 1024 * 1024);

	/** Maximum number of graphs. */
	public static final int MAXGRAPHS = properties.getIntProperty("hipg.maxGraphs", 10);

	/** Maximum number of workers. */
	public static final int POOLSIZE = properties.getIntProperty("hipg.poolSize", 0);

	/** Message size (main buffer). */
	public static final int MESSAGE_BUF_SIZE = properties.getIntProperty("hipg.messageBufSize", 4 * 8 * 1024 * 1024);

	/**
	 * Preferred minimal message length, change if you know what you are doing. Message may still be shorter, when it is
	 * urgent to send them.
	 */
	public static final int PREFERRED_MINIMAL_MESSAGE_SIZE = properties.getIntProperty(
			"hipg.preferredMinimalMessageSize", 1024);
	public static final int SKIP_STEPS_BEFORE_SENDING_SMALL_MESSAGE = properties.getIntProperty(
			"hipg.skipStepsBeforeSendingSmallMessage", 50);
	public static final int YIELD_BEFORE_SENDING_SMALL_MESSAGE = properties.getIntProperty(
			"hipg.yieldBeforeSendingSmallMessage", -1);

	/** Number of preallocated send buffers. */
	public static final int INIT_SEND_BUFFERS = properties.getIntProperty("hipg.initSendBuffers", 50);

	/** Number of preallocated receive buffers. */
	public static final int INIT_RECV_BUFFERS = properties.getIntProperty("hipg.initRecvBuffers", 50);

	public static final int SYNCHRONIZER_QUEUE_CHUNK_SIZE = properties.getIntProperty(
			"hipg.synchronizerQueueChunkSize", 16 * 1024);

	public static final int SYNCHRONIZER_QUEUE_INITIAL_CHUNKS = properties.getIntProperty(
			"hipg.synchronizerQueueInitialChunks", 1);

	public static final int SYNCHRONIZER_QUEUE_MEM_CACHE_SIZE = properties.getIntProperty(
			"hipg.synchronizerQueueMemCacheSize", 0);

	public static final int MAX_METHODS_IMMEDIATE = properties.getIntProperty("hipg.maxMethodsImmediate", 100);

	public static final boolean OBJECT_SERIALIZATION = properties.getBooleanProperty("hipg.objectSerialization", true);
	
	public static final boolean FLUSH_BIGGEST = properties.getBooleanProperty("hipg.flushBiggest", false);

	private static void checkConfiguration() {
		if (POOLSIZE <= 0) {
			printConfiguration();
			throw new RuntimeException("Pool size not specified");
		}
	}

	public static int getSendBufferSize() {
		return Config.MESSAGE_BUF_SIZE / (Config.POOLSIZE - 1);
	}

	public static int getRecvBufferSize() {
		return Config.MESSAGE_BUF_SIZE;
	}

	public static int getNumReceiveBuffers() {
		return Config.INIT_RECV_BUFFERS;
	}

	public static int getNumSendBuffers() {
		return Config.INIT_SEND_BUFFERS * (Config.POOLSIZE - 1);
	}

	public static void printConfiguration() {
		System.err.println("Configuration:");
		System.err.println("    POOLSIZE                                = " + POOLSIZE);
		System.err.println("    ERRCHECK                                = " + ERRCHECK);
		System.err.println("    FINEDEBUG                               = " + FINEDEBUG);
		System.err.println("    FINE_TIMING                             = " + TIMING);
		System.err.println("    STATISTICS                              = " + STATISTICS);
		System.err.println("    THROUGHPUT                              = " + THROUGHPUT);
		System.err.println("    MESSAGE_BUF_SIZE                        = " + (MESSAGE_BUF_SIZE / 1024) + " KB");
		System.err.println("    INIT_SEND_BUFFERS                       = " + INIT_SEND_BUFFERS);
		System.err.println("    INIT_RECV_BUFFERS                       = " + INIT_RECV_BUFFERS);
		System.err.println("    SYNCHRONIZER_QUEUE_CHUNK_SIZE           = " + SYNCHRONIZER_QUEUE_CHUNK_SIZE);
		System.err.println("    SYNCHRONIZER_QUEUE_INITIAL_CHUNKS       = " + SYNCHRONIZER_QUEUE_INITIAL_CHUNKS);
		System.err.println("    SYNCHRONIZER_QUEUE_MEM_CACHE_SIZE       = " + SYNCHRONIZER_QUEUE_MEM_CACHE_SIZE);
		System.err.println("    MAX_METHODS_IMMEDIATE                   = " + MAX_METHODS_IMMEDIATE);
		System.err.println("    OBJECT_SERIALIZATION                    = " + OBJECT_SERIALIZATION);
		System.err.println("    PREFERRED_MINIMAL_MESSAGE_SIZE          = " + PREFERRED_MINIMAL_MESSAGE_SIZE);
		System.err.println("    SKIP_STEPS_BEFORE_SENDING_SMALL_MESSAGE = " + SKIP_STEPS_BEFORE_SENDING_SMALL_MESSAGE);
		System.err.println("    YIELD_BEFORE_SENDING_SMALL_MESSAGE      = " + YIELD_BEFORE_SENDING_SMALL_MESSAGE);
		System.err.println("    FLUSH_BIGGEST                           = " + FLUSH_BIGGEST);

		if (REPORT_FILE_BASE_NAME != null && !STATISTICS) {
			throw new RuntimeException("To enable reporting, you must set hipg.statistics!");
		}
	}

	static {
		checkConfiguration();
	}

}
