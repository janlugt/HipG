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

package hipg.app.utils;

/** Represents a strongly-connected component (SCC). */
public final class Scc {
	private long size = -1;
	private long id = -1;
	private int depth = -1;

	public Scc(final long id, final long size) {
		this.id = id;
		this.size = size;
	}

	public Scc(Scc scc) {
		this.size = scc.size;
		this.depth = scc.depth;
		this.id = scc.id;
	}

	public boolean initialized() {
		return size >= 0;
	}

	public long getSize() {
		return size;
	}

	public void setSize(final long size) {
		this.size = size;
	}

	public long getId() {
		if (!initialized()) {
			throw new RuntimeException("Component not initialized");
		}
		return id;
	}

	public boolean isDepthSet() {
		return depth >= 0;
	}

	public int getDepth() {
		return depth;
	}

	public void setDepth(final int depth) {
		this.depth = depth;
	}

	@Override
	public String toString() {
		return "id=" + id + " size=" + size;
	}
}