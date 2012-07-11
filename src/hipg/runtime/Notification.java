/**
 * Copyright (c) 2010, 2011 Vrije Universiteit Amsterdam.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hipg.Config;

public class Notification {

	/** Logging facilities. */
	private static final Logger logger = LoggerFactory.getLogger(Notification.class);
	private final String loggerPrefix;

	private final Synchronizer issuer;

	private final int[] acks = (Config.POOLSIZE <= 1 ? null : new int[1024]);
	private int firstUsed = 0;
	private int firstFree = 0;
	private int used = 0;

	public Notification(final Synchronizer issuer) {
		this.issuer = issuer;
		this.loggerPrefix = "(" + Runtime.getCommunication().getName() + ") " + issuer.name() + " ";
		if (Config.STATISTICS) {
			Statistics.newNotification();
		}
	}

	public void init(final short notificationMethodId, final byte[] value) {
		this.issuer.hipg_notify(notificationMethodId, value);
		if (Runtime.getRuntime().hasCoworkers()) {
			int id = newId();
			Runtime.getCommunication().sendNotification(issuer, notificationMethodId, value, id);
			if (Config.FINEDEBUG) {
				logger.info(loggerPrefix + "initializing notification " + id);
			}
		}
	}

	public void received(final short notificationMethodId, final byte[] value, final int id, final int notifIssuer) {
		this.issuer.hipg_notify(notificationMethodId, value);
		Runtime.getCommunication().sendNotificationAck(issuer, notifIssuer, id);
		if (Config.FINEDEBUG) {
			logger.info(loggerPrefix + "received notification " + id + " from " + notifIssuer);
		}
	}

	public void receivedAck(final int id) {
		if (Config.FINEDEBUG) {
			logger.info(loggerPrefix + "received ack for " + id + " while expecting " + acks[id] + " acks");
		}
		freeId(id);
	}

	private final int newId() {
		if (used == acks.length) {
			logger.warn("too many warnings! not waiting for acknowledgements");
			return -1;
		} else {
			int id = firstFree++;
			used++;
			if (firstFree == acks.length) {
				firstFree = 0;
			}
			acks[id] = Config.POOLSIZE - 1;
			return id;
		}
	}

	private final void freeId(final int id) {
		if (Config.ERRCHECK) {
			if (acks[id] <= 0) {
				throw new RuntimeException("Too many acks for id " + id + " at synchronizer " + issuer.name() + " at "
						+ Runtime.getRank());
			}
		}
		acks[id]--;
		if (acks[id] == 0) {
			used--;
			if (id == firstUsed) {
				if (used == 0) {
					firstUsed = firstFree;
				} else {
					do {
						firstUsed++;
						if (firstUsed == acks.length)
							firstUsed = 0;
					} while (acks[firstUsed] == 0);
				}
			}
		}
	}
}
