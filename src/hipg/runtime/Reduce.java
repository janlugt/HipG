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

import java.util.Arrays;

import myutils.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distributed reduce operation.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 */
public final class Reduce {

	/** Logging utilities. */
	private static final Logger logger = LoggerFactory.getLogger(Reduce.class);
	private final String loggerPrefix;

	/** Reduce issuer. */
	private final Synchronizer issuer;
	/** Initialized reduce. */
	private int initialized = 0;
	/** Finished reduce. */
	private int done = 0;
	/** Current reduce: reduce method. */
	private short reduceMethodId = -1;
	/** Current reduce: initial value. */
	private byte[] initialValue;
	/** Current reduce: result. */
	private byte[] result;
	/** Owner. */
	private final boolean owner;
	/** Stored token. */
	private int storedReduce = -1;
	private short storedReduceMethodId;
	private byte[] storedResult;

	public Reduce(Synchronizer issuer) {
		this.issuer = issuer;
		this.owner = Runtime.getCommunication().getRank() == (issuer.getMaster());
		this.loggerPrefix = "(" + Runtime.getCommunication().getName() + ") " + issuer.name() + " ";
		if (Config.STATISTICS) {
			Statistics.newReduce();
		}
	}

	public void set(short reduceMethodId, byte[] initialValue) {
		this.reduceMethodId = reduceMethodId;
		this.initialValue = initialValue;
		this.result = null;
	}

	public void init() {
		initialized++;
		if (Config.STATISTICS) {
			Statistics.reduceInitialized();
		}
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Initializing reduce " + initialized);
		}

		if (!owner) {
			return;
		}

		byte[] firstResult = null;
		if (initialValue != null)
			firstResult = Arrays.copyOf(initialValue, initialValue.length);

		firstResult = issuer.hipg_reduce(reduceMethodId, firstResult);

		if (!Runtime.getRuntime().hasCoworkers() || issuer.getExecutionMode() == Synchronizer.EXECUTION_ONE) {
			result = firstResult;
			setDone();
			return;
		}

		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Initializing reduce with fresh token with first result "
					+ StringUtils.print(firstResult));
		}

		Runtime.getCommunication().sendReduceToken(issuer.getOwner(), issuer.getId(), initialized, reduceMethodId,
				firstResult);
	}

	public void receivedAnnounce(byte[] result, int sender) {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Received announce");
		}
		this.result = result;
		setDone();
	}

	public void receivedContinue() {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Received continue");
		}
		setDone();
	}

	public void received(int reduce, short reduceMethodId, byte[] result) {
		if (issuer.getExecutionMode() == Synchronizer.EXECUTION_ALL && initialized < reduce) {
			store(reduce, reduceMethodId, result);
		} else {
			if (owner) {
				returned(reduce, reduceMethodId, result);
			} else {
				forward(reduce, reduceMethodId, result);
			}
		}
	}

	private void returned(int reduce, int reduceMethodId, byte[] result) {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Returned reduce " + reduce + " with result " + StringUtils.print(result));
		}
		this.result = result;
		if (issuer.getExecutionMode() == Synchronizer.EXECUTION_ALL) {
			announce();
			setDone();
		}
	}

	private void forward(int reduce, short reduceMethodId, byte[] result) {
		result = issuer.hipg_reduce(reduceMethodId, result);
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Forwarding reduce token for reduce " + reduce + " with partial result "
					+ StringUtils.print(result));
		}
		Runtime.getCommunication().sendReduceToken(issuer.getOwner(), issuer.getId(), reduce, reduceMethodId, result);
	}

	private void announce() {
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Sending announce");
		}
		Runtime.getCommunication().sendReduceAnnounceToken(issuer.getOwner(), issuer.getId(), result);
	}

	private void setDone() {
		done++;
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Reduce " + done + " done with result " + StringUtils.print(result));
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

	public void store(int reduce, short reduceMethodId, byte[] result) {
		if (Config.ERRCHECK) {
			if (storedReduce >= 0)
				throw new RuntimeException("Cannot store token in reduce " + initialized + " owned by " + issuer.name()
						+ " as last token not consumed.");
		}
		if (Config.FINEDEBUG) {
			logger.debug(loggerPrefix + "Storing token for reduce " + reduce + " since current reduce is "
					+ initialized);
		}
		if (Config.STATISTICS) {
			Statistics.reducePosponing();
		}
		storedReduce = reduce;
		storedReduceMethodId = reduceMethodId;
		storedResult = result;
	}

	public boolean progress() {
		if (storedReduce >= 0 && initialized >= storedReduce) {
			if (Config.FINEDEBUG)
				logger.debug(loggerPrefix + "Restoring postponed token");
			int reduce = storedReduce;
			byte[] result = storedResult;
			storedReduce = -1;
			storedResult = null;
			received(reduce, storedReduceMethodId, result);
			return true;
		}
		return false;
	}

	public String toString() {
		return "Reduce(initialized="
				+ initialized
				+ ",done="
				+ done
				+ (isDone() ? (",result=" + StringUtils.print(result)) : "")
				+ (storedReduce < 0 ? "" : (", postponed=(" + storedReduce + "," + storedReduceMethodId + ","
						+ StringUtils.print(storedResult) + ")")) + ")";
	}

}
