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

import hipg.runtime.Runtime;

import java.util.Date;
import java.util.Random;

public class Sleep {

	private static void synchronize(Random rand) {

		int sleepTime = rand.nextInt(10 * 1000);

		// desynchronizer: sleep for random time
		System.out.println("Sleeping for " + sleepTime + " ms ... ");
		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
		}

		// synchronize
		System.out.println("Will synchronize at " + (new Date()));
		Runtime.getRuntime().barrier();
		System.out.println("Synchronized at " + (new Date()));

	}

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("Usage: " + Sleep.class.getName() + " <poolSize>");
			System.exit(1);
		}
		int poolSize = Integer.parseInt(args[0]);
		Random rand = new Random(System.nanoTime());

		// init runtime
		System.out.println("Await pool of " + poolSize);
		hipg.runtime.Runtime.getRuntime().awaitPool(poolSize);

		for (int i = 0; i < 5; i++)
			synchronize(rand);

		System.out.println("Done");
	}
}
