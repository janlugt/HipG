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

package hipg.graph;

import java.util.Arrays;

public class OnTheFlyDefaultHash implements OnTheFlyHash {

	private final int poolSize;

	public OnTheFlyDefaultHash(int poolSize) {
		this.poolSize = poolSize;
	}

	public int owner(byte[] state) {
		int h = Arrays.hashCode(state);
		if (h == Integer.MIN_VALUE) {
			h = Integer.MAX_VALUE;
		} else if (h < 0) {
			h = -h;
		}
		return h % poolSize;
	}

	public int id(byte[] state) {
		return Arrays.hashCode(state);
	}
}
