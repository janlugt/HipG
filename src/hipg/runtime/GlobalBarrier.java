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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distributed termination detection (Safra's algorithm) for the runtime.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 */
public final class GlobalBarrier {
	/** Logging facilities. */
	private static final Logger logger = LoggerFactory.getLogger(GlobalBarrier.class);
	private final String loggerPrefix;

	/** Color white. */
	public static final byte WHITE = 0;
	/** Color black. */
	public static final byte BLACK = 1;

	/** Barrier's issuer. */
	private final Runtime issuer;
	/** Count of initialized barriers. */
	private int initialized = 0;
	/** Count of finished barriers. */
	private int done = 0;
	/** Needs be reinitialized */
	private boolean needsInit = false;
	/** Stored token. */
	private int storedBarrier = -1;
	private int storedSum;
	private int storedMaster;

	public GlobalBarrier(Runtime issuer) {
		this.issuer = issuer;
		final Communication communication = Runtime.getCommunication();
		final String name = (communication == null ? "" : communication.getName());
		this.loggerPrefix = "(" + name + ") " + issuer.name() + " ";
		if (Config.STATISTICS) {
			Statistics.newGlobalBarrier();
		}
	}

	public void init() {
		initialized++;
		if (Config.FINEDEBUG) {
			logger.info(loggerPrefix + "Init global barrier " + initialized);
		}
		if (!Runtime.getRuntime().hasCoworkers() || (!Runtime.getRuntime().isMe(0))) {
			// not done yet because it may have invocation
			// records stored
			return;
		}

		if (issuer.passive()) {
			needsInit = true;
		} else {
			doInit();
		}
	}

	private void doInit() {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Initializing barrier " + initialized + " with a fresh token");
		}
		if (Config.STATISTICS) {
			Statistics.globalBarrierInitialized();
		}
		Runtime.getCommunication().sendGlobalBarrierToken(initialized, 0, Runtime.getRank());
	}

	public void receivedAnnounce() {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Received announce for barrier " + initialized);
		}
		assert (!isDone());
		setDone();
	}

	public void received(int tokenBarrier, int tokenSum, int tokenMaster) {
		if (!issuer.passive() || initialized < tokenBarrier) {
			/* store */
			store(tokenBarrier, tokenSum, tokenMaster);
		} else {
			if (issuer.color() == GlobalBarrier.BLACK) {
				/* reinitialize */
				if (Config.FINEDEBUG) {
					logger.debug(loggerPrefix + "Token received at black node. " + "Reinitializing barrier "
							+ tokenBarrier);
				}
				doInit();
			} else if (tokenMaster == Runtime.getRank()) {
				if (tokenSum + issuer.mc() == 0) {
					/* barrier done */
					if (Config.FINEDEBUG) {
						logger.debug(loggerPrefix + "Returned token with " + "sum 0. Barrier " + tokenBarrier + " done");
					}
					setDone();
					announceAll();
				} else {
					/* reinitialize */
					if (Config.FINEDEBUG) {
						logger.debug(loggerPrefix + "Returned " + "token for barrier " + tokenBarrier + " with sum "
								+ (tokenSum + issuer.mc()));
					}
					doInit();
				}
			} else {
				/* forward */
				tokenSum += issuer.mc();
				if (Config.FINEDEBUG) {
					logger.debug(loggerPrefix + "Forwarding " + "token for barrier " + tokenBarrier + " with sum "
							+ tokenSum + " of master " + tokenMaster);
				}
				Runtime.getCommunication().sendGlobalBarrierToken(tokenBarrier, tokenSum, tokenMaster);
			}
			issuer.whiten();
		}
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
			logger.info(loggerPrefix + "Done barrier " + done);
		}
	}

	private void announceAll() {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Sending announce for barrier " + initialized);
		}
		Runtime.getCommunication().sendGlobalBarrierAnnounceToken();
	}

	public void store(int tokenBarrier, int tokenSum, int tokenMaster) {
		assert (storedBarrier < 0);
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Storing token (" + tokenBarrier + "," + tokenSum + "," + tokenMaster + ")");
		}
		if (Config.STATISTICS) {
			Statistics.globalBarrierPostponed();
		}
		storedBarrier = tokenBarrier;
		storedSum = tokenSum;
		storedMaster = tokenMaster;
	}

	public boolean progress() {
		if (needsInit) {
			needsInit = false;
			doInit();
			return true;
		}
		if (!Runtime.getRuntime().hasCoworkers()) {
			if (!isDone())
				setDone();
			return true;
		}
		if (storedBarrier < 0)
			return false;
		if (initialized < storedBarrier)
			return false;

		if (Config.FINEDEBUG)
			logger.debug(loggerPrefix + "Restoring postponed token (" + storedBarrier + "," + storedSum + ","
					+ storedMaster + ")");

		// remove stored token
		int barrier = storedBarrier;
		storedBarrier = -1;

		// process restored token
		received(barrier, storedSum, storedMaster);
		return true;
	}

	@Override
	public String toString() {
		return "Barrier("
				+ "initialized="
				+ initialized
				+ ", done="
				+ done
				+ (storedBarrier < 0 ? ""
						: (", postponed=(" + storedBarrier + "," + storedSum + "," + storedMaster + ")"))
				+ ",needsInit=" + needsInit + ")";
	}

}
