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

import myutils.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distributed termination detection (Safra's algorithm) combined with a reduce operation.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 */
public final class BarrierAndReduce {
	/** Logging facilities. */
	public static final Logger logger = LoggerFactory.getLogger(BarrierAndReduce.class);
	private final String loggerPrefix;

	/** Color white. */
	public static final byte WHITE = 0;
	/** Color black. */
	public static final byte BLACK = 1;

	/** Barrier's issuer. */
	private final Synchronizer synchronizer;
	/** Count of initialized barriers. */
	private int initialized = 0;
	/** Count of finished barriers. */
	private int done = 0;
	/** Current reduce: reduce method. */
	private short reduceMethodId = -1;
	/** Current reduce: initial value. */
	private byte[] initialValue;
	/** Current reduce: result. */
	private byte[] result;
	/** Needs initialization. */
	private boolean needsInit = false;
	private boolean waitingForAnnounce = false;
	/** Stored token. */
	private int storedBarrier = -1;
	private int storedSum;
	private int storedMaster;

	public BarrierAndReduce(Synchronizer issuer) {
		this.synchronizer = issuer;
		this.loggerPrefix = "(" + Runtime.getCommunication().getName() + ") " + issuer.name() + " ";
	}

	public void set(short reduceMethodId, byte[] initialValue) {
		this.reduceMethodId = reduceMethodId;
		this.initialValue = initialValue;
		this.result = null;
		if (Config.STATISTICS) {
			Statistics.newBarrierAndReduce();
		}
	}

	public void init() {
		initialized++;
		if (Config.FINEDEBUG) {
			logger.info(loggerPrefix + "Init barrierAndReduce " + initialized);
		}
		if (!Runtime.getRuntime().hasCoworkers()
				|| (synchronizer.getExecutionMode() == Synchronizer.EXECUTION_ALL && !Runtime.getRuntime().isMe(0))) {
			return;
		}
		if (synchronizer.passive()) {
			needsInit = true;
		} else {
			doInit();
		}
	}

	private final void doInit() {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Initializing barrierAndReduce " + initialized + " with a fresh token");
		}
		if (Config.STATISTICS) {
			Statistics.barrierAndReduceInitialized();
		}
		Runtime.getCommunication().sendBarrierReduceToken(synchronizer.getOwner(), synchronizer.getId(), initialized,
				0, Runtime.getRank());
	}

	public void receivedAnnounce(byte[] result) {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Received announce for barrierAndReduce " + initialized + " with result "
					+ StringUtils.print(result));
		}
		assert (!isDone() && waitingForAnnounce);
		this.waitingForAnnounce = false;
		this.result = result;
		setDone();
	}

	public void receivedQuery(byte[] partialResult) {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Received query for barrierAndReduce " + initialized + " with partial result "
					+ StringUtils.print(partialResult));
		}

		if (waitingForAnnounce) {
			result = synchronizer.hipg_reduce(reduceMethodId, partialResult);

			if (Config.FINEDEBUG) {
				logger.debug(loggerPrefix + "Result of barrierAndReduce is " + StringUtils.print(result));
			}

			waitingForAnnounce = false;
			announceAll();
			setDone();
		} else {
			partialResult = synchronizer.hipg_reduce(reduceMethodId, partialResult);
			waitingForAnnounce = true;
			query(partialResult);
		}
	}

	public void received(int tokenBarrier, int tokenSum, int tokenMaster) {
		if (synchronizer.getExecutionMode() == Synchronizer.EXECUTION_OWNED && initialized < tokenBarrier) {
			assert (initialized + 1 == tokenBarrier);
			initialized = tokenBarrier;
		}
		if (!synchronizer.passive() || initialized < tokenBarrier) {
			/* store */
			store(tokenBarrier, tokenSum, tokenMaster);
		} else {
			if (synchronizer.color() == BarrierAndReduce.BLACK) {
				/* reinitialize */
				if (Config.FINEDEBUG) {
					logger.debug(loggerPrefix + "Token received at black node. " + "Reinitializing barrierAndReduce "
							+ tokenBarrier);
				}
				doInit();
			} else if (tokenMaster == Runtime.getRank()) {
				if (tokenSum + synchronizer.mc() == 0) {
					/* barrier done, do reduce */
					if (Config.FINEDEBUG) {
						logger.debug(loggerPrefix + "Returned token with " + "sum 0. BarrierAndReduce " + tokenBarrier
								+ " done");
					}
					if (synchronizer.getExecutionMode() == Synchronizer.EXECUTION_ALL
							|| (synchronizer.getExecutionMode() == Synchronizer.EXECUTION_OWNED && !Runtime
									.getRuntime().isMe(synchronizer.getOwner()))) {
						waitingForAnnounce = true;
						query(initialValue);
					}
				} else {
					/* reinitialize */
					if (Config.FINEDEBUG) {
						logger.debug(loggerPrefix + "Returned token for barrierAndReduce " + tokenBarrier
								+ " with sum " + (tokenSum + synchronizer.mc()));
					}
					doInit();
				}
			} else {
				/* forward */
				tokenSum += synchronizer.mc();
				if (Config.FINEDEBUG) {
					logger.debug(loggerPrefix + "Forwarding token for barrierAndReduce " + tokenBarrier + " with sum "
							+ tokenSum + " of master " + tokenMaster);
				}
				Runtime.getCommunication().sendBarrierReduceToken(synchronizer.getOwner(), synchronizer.getId(),
						tokenBarrier, tokenSum, tokenMaster);
			}
			synchronizer.whiten();
		}
	}

	public byte[] result() {
		return result;
	}

	public int initialized() {
		return initialized;
	}

	public int done() {
		return done;
	}

	public boolean isDone() {
		return done >= initialized;
	}

	public void setDone() {
		done++;
		if (Config.FINEDEBUG) {
			logger.info(loggerPrefix + "Done barrierAndReduce " + done);
		}
	}

	private void query(byte[] partialResult) {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Sending query for barrierAndReduce " + initialized + " with partial result "
					+ StringUtils.print(partialResult));
		}
		Runtime.getCommunication().sendBarrierReduceQueryToken(synchronizer.getOwner(), synchronizer.getId(),
				partialResult);
	}

	private void announceAll() {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Sending announce for barrierAndReduce " + initialized);
		}
		Runtime.getCommunication().sendBarrierReduceAnnounceAllToken(synchronizer, result);
	}

	public void store(int tokenBarrier, int tokenSum, int tokenMaster) {
		assert (storedBarrier < 0);
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Storing token for barrierAndReduce " + tokenBarrier + " with sum " + tokenSum
					+ ", master " + tokenMaster + " and partial result " + result + " (because synchronizer todo="
					+ synchronizer.todo() + ", initialized=" + initialized + ")");
		}
		if (Config.STATISTICS) {
			Statistics.barrierAndReducePosponing();
		}
		storedBarrier = tokenBarrier;
		storedSum = tokenSum;
		storedMaster = tokenMaster;
	}

	// assume: passive & !done
	public boolean progress() {

		if (needsInit) {
			needsInit = false;
			doInit();
			return true;
		}
		if (waitingForAnnounce)
			return false;
		if (!Runtime.getRuntime().hasCoworkers() || synchronizer.getExecutionMode() == Synchronizer.EXECUTION_ONE) {
			result = synchronizer.hipg_reduce(reduceMethodId, initialValue);
			setDone();
			return true;
		}
		if (storedBarrier < 0)
			return false;

		if (synchronizer.getExecutionMode() == Synchronizer.EXECUTION_ALL && initialized < storedBarrier)
			return false;

		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Restoring postponed token for barrierAndReduce " + storedBarrier
					+ " with sum " + storedSum + ", master " + storedMaster);
		}

		// remove stored token
		int barrier = storedBarrier;
		storedBarrier = -1;

		// process restored token
		received(barrier, storedSum, storedMaster);

		return true;
	}

	@Override
	public String toString() {
		return "BarrierReduce("
				+ "initialized="
				+ initialized
				+ ",done="
				+ done
				+ ","
				+ (isDone() ? "result" : "partialResult")
				+ "="
				+ result
				+ (storedBarrier < 0 ? ""
						: (",postponed=(" + storedBarrier + "," + storedSum + "," + storedMaster + ")"))
				+ ",needsInit=" + needsInit + ")";
	}

}
