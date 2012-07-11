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

import java.io.IOException;

/**
 * Sender thread.
 * 
 * @author ela, ekr@cs.vu.nl
 * 
 */
public final class Sender extends Thread {
	private volatile boolean done = false;
	private volatile boolean finished = false;
	private final Communication communication;
	private volatile int requestsAll = 0;
	private volatile int requestsBig = 0;
	private final int rank;

	public Sender(Communication communication) {
		this.communication = communication;
		this.rank = communication.getRank();
		setName("Sender");
		setDaemon(true);
	}

	@Override
	public void run() {
		int countAll = 0;
		int countBig = 0;
		int dest = -1;
		try {
			while (!done) {
				/* wait for requests */
				if (Config.STATISTICS) {
					Statistics.senderGoingToSleep();
				}
				synchronized (this) {
					requestsBig -= countBig;
					requestsAll -= countAll;
					while (requestsAll == 0 && requestsBig == 0 && !done) {
						wait();
					}
					countAll = requestsAll;
					countBig = requestsBig;
				}
				if (Config.STATISTICS) {
					Statistics.senderWakingUp();
				}
				if (done) {
					break;
				}
				// Send full messages.
				for (dest = 0; dest < Config.POOLSIZE; dest++) {
					if (dest != rank) {
						FastMessage message;
						while ((message = communication.getFullSendMessage(dest)) != null) {
							if (Config.STATISTICS) {
								Statistics.senderFlushFull();
							}
							message.flush();
							communication.recycleSentMessage(message);
						}
					}
				}
				// Send smaller messages.
				for (dest = 0; dest < Config.POOLSIZE; dest++) {
					if (dest != rank) {
						final FastMessage message = communication.getCurrentSendMessage(dest);
						if (message != null) {
							if (requestsAll > 0 || message.sizeInReader() > Config.PREFERRED_MINIMAL_MESSAGE_SIZE) {
								message.flush();
							}
						}
					}
				}
			}
		} catch (IOException e) {
			communication.handleCouldNotCommunicate(dest, e);
		} catch (InterruptedException e) {
		} finally {
			synchronized (this) {
				finished = true;
				notifyAll();
			}
		}
	}

	synchronized public void requestAll() {
		requestsAll++;
		notify();
	}

	synchronized public void requestBig() {
		requestsBig++;
		notify();
	}

	public void synchRequestAll() {
		int dest = -1;

		try {

			// Send full messages.
			for (dest = 0; dest < Config.POOLSIZE; dest++) {
				if (dest != rank) {
					FastMessage message;
					while ((message = communication.getFullSendMessage(dest)) != null) {
						if (Config.STATISTICS) {
							Statistics.senderFlushFull();
						}
						message.flush();
						communication.recycleSentMessage(message);
					}
				}
			}

			// Send current messages.
			final int poolSize = Runtime.getPoolSize();
			for (dest = 0; dest < poolSize; dest++) {
				if (dest != rank) {
					final FastMessage message = communication.getCurrentSendMessage(dest);
					if (message != null) {
						message.flush();
					}
				}
			}

		} catch (IOException e) {
			communication.handleCouldNotCommunicate(dest, e);
		}
	}

	public void synchRequestBig() {
		int dest = -1;

		try {

			// Send full messages.
			for (dest = 0; dest < Config.POOLSIZE; dest++) {
				if (dest != rank) {
					FastMessage message;
					while ((message = communication.getFullSendMessage(dest)) != null) {
						if (Config.STATISTICS) {
							Statistics.senderFlushFull();
						}
						message.flush();
						communication.recycleSentMessage(message);
					}
				}
			}

			// Send large-enough current messages.
			final int poolSize = Runtime.getPoolSize();
			for (dest = 0; dest < poolSize; dest++) {
				if (dest != rank) {
					final FastMessage message = communication.getCurrentSendMessage(dest);
					if (message != null) {
						final int messageSize = message.sizeInReader();
						if (messageSize >= Config.PREFERRED_MINIMAL_MESSAGE_SIZE) {
							message.flush();
						}
					}
				}
			}

		} catch (IOException e) {
			communication.handleCouldNotCommunicate(dest, e);
		}
	}

	public void synchRequestBiggest() {
		int dest = -1;

		try {

			// Send full messages.
			for (dest = 0; dest < Config.POOLSIZE; dest++) {
				if (dest != rank) {
					FastMessage message;
					while ((message = communication.getFullSendMessage(dest)) != null) {
						if (Config.STATISTICS) {
							Statistics.senderFlushFull();
						}
						message.flush();
						communication.recycleSentMessage(message);
					}
				}
			}

			// Send biggest current message.
			final int poolSize = Runtime.getPoolSize();
			int biggestDest = -1;
			int biggestSize = -1;
			for (dest = 0; dest < poolSize; dest++) {
				if (dest != rank) {
					final FastMessage message = communication.getCurrentSendMessage(dest);
					if (message != null) {
						final int messageSize = message.sizeInReader();
						if (biggestSize < 0 || biggestSize < messageSize) {
							biggestSize = messageSize;
							biggestDest = dest;
						}
					}
				}
			}
			if (biggestSize > 0) {
				communication.getCurrentSendMessage(biggestDest).flush();
			}

		} catch (IOException e) {
			communication.handleCouldNotCommunicate(dest, e);
		}
	}

	public void close() {
		synchronized (this) {
			done = true;
			notifyAll();
			while (!finished) {
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}

}
