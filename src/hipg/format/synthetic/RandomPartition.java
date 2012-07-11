/**
 * Copyright (c) 2010, 2011 Vrije Universiteit Amsterdam.
 * Written by Elzbieta Krepska, e.l.krepska@vu.nl.
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

package hipg.format.synthetic;

import java.util.Random;

public class RandomPartition implements Partition {

	private final Random rand;
	private final int poolSize;

	public RandomPartition(final int poolSize) {
		this.poolSize = poolSize;
		rand = new Random(poolSize * 17);
	}

	public int owner(long n) {
		if (poolSize == 1)
			return 0;
		return rand.nextInt(poolSize);
	}

	public int index(long n) {
		throw new RuntimeException("Operation not possible to implement");
	}

	public long back(int owner, int index) {
		throw new RuntimeException("Operation not possible to implement");
	}

	public int getPoolSize() {
		return poolSize;
	}

}
