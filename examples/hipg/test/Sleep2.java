/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam
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
 */

package hipg.test;

import hipg.Config;
import hipg.runtime.Runtime;

import java.util.Date;
import java.util.Random;
import java.util.Vector;

public class Sleep2 {

	private static final Random rand = new Random(System.nanoTime());

	public static class SleepSynchronizer extends hipg.runtime.Synchronizer {
		private final Vector<Date> times = new Vector<Date>();

		public SleepSynchronizer() {
		}

		@Override
		public void run() {
			for (int k = 0; k < 10; k++) {

				// sleep
				int sleepTime = rand.nextInt(15 * 1000);
				System.out.println("Sleeping for " + sleepTime + " ms ... ");
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
				}

				// synchronizer
				System.out.println(new Date() + ": call barrier");
				barrier();
				Date d = new Date();
				times.add(d);
				System.out.println(d + ": barrier executed");
			}

		}

	}

	public static void main(String[] args) {
		// await pool
		System.out.println("Await pool of " + Config.POOLSIZE);
		hipg.runtime.Runtime.getRuntime().awaitPool(Config.POOLSIZE);

		// run
		SleepSynchronizer ss = new SleepSynchronizer();
		Runtime.getRuntime().spawnAll(ss);
		Runtime.getRuntime().barrier();

		// print results
		System.out.println("Synchronization times:");
		for (int i = 0; i < ss.times.size(); i++)
			System.out.println("   " + ss.times.get(i));
	}
}
