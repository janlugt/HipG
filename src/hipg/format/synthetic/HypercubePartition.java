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

package hipg.format.synthetic;

public class HypercubePartition implements Partition {

	private final int length;
	private final int dimension;
	private final int poolSize;

	private final long stateSpaceSize;
	private final int perWorkerBase;
	private final int perWorkerRemainder;
	private final int perWorkerDivider;

	private final boolean fast;
	private final long poolSizeLog;
	private final long poolSizeMinus1;

	/** Creates partition of a hypercube [0,length]^dimension. */
	public HypercubePartition(final int length, final int dimension, final int poolSize) {
		if (length <= 0 || poolSize <= 0)
			throw new IllegalArgumentException("Pool size " + poolSize + ", length " + length);
		this.dimension = dimension;
		this.length = length;
		this.poolSize = poolSize;
		this.stateSpaceSize = (long) Math.pow(length, dimension);
		long pwb = stateSpaceSize / poolSize;
		if (pwb > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Too many states per node: " + pwb);
		}
		this.perWorkerBase = (int) pwb;
		this.perWorkerRemainder = (int) (stateSpaceSize - (long) poolSize * pwb);
		this.perWorkerDivider = perWorkerRemainder * (perWorkerBase + 1);
		this.poolSizeLog = (int) Math.round(Math.log(poolSize) / Math.log(2));
		this.poolSizeMinus1 = poolSize - 1;
		this.fast = ((1 << poolSizeLog) == poolSize);
	}

	public int owner(long n) {
		if (poolSize <= 1) {
			return 0;
		}
		if (fast) {
			return (int) (n & poolSizeMinus1);
		}
		if (perWorkerRemainder == 0) {
			return (int) (n / perWorkerBase);
		}
		if (n <= perWorkerDivider) {
			return (int) (n / (perWorkerBase + 1));
		} else {
			return perWorkerRemainder + (int) ((n - perWorkerDivider) / (perWorkerBase));
		}
	}

	public int index(long n) {
		if (poolSize <= 1) {
			return (int) n;
		}
		if (fast) {
			return (int) (n >> poolSizeLog);
		}
		if (perWorkerRemainder == 0) {
			return (int) (n % perWorkerBase);
		}
		if (n <= perWorkerDivider) {
			return (int) (n % (perWorkerBase + 1));
		} else {
			return (int) ((n - perWorkerDivider) % perWorkerBase);
		}
	}

	public long back(int owner, int index) {
		if (poolSize <= 1) {
			return index;
		}
		if (fast) {
			return (index << poolSizeLog) | owner;
		}
		if (perWorkerRemainder == 0) {
			return owner * perWorkerBase + index;
		}
		if (owner < perWorkerRemainder) {
			return owner * (1 + perWorkerBase) + index;
		} else {
			return perWorkerDivider + (owner - perWorkerRemainder) * perWorkerBase + index;
		}
	}

	public int getPoolSize() {
		return poolSize;
	}

	public int getDimension() {
		return dimension;
	}

	public int getLength() {
		return length;
	}

	public long getStateSpaceSize() {
		return stateSpaceSize;
	}

	public boolean isFast() {
		return fast;
	}

	public boolean noRemainder() {
		return perWorkerRemainder == 0;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Hypercube partition (");
		sb.append("poolSize=" + poolSize);
		sb.append(",");
		if (fast) {
			sb.append("fast");
		} else if (noRemainder()) {
			sb.append("noRemainder(perWorker=" + perWorkerBase + ")");
		} else {
			sb.append("perWorkerBase=" + perWorkerBase + ",perWorkerRemainder=" + perWorkerRemainder);
		}
		sb.append(")");
		return sb.toString();
	}

}
