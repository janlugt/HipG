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

import myutils.tuple.pair.FastIntPair;
import hipg.runtime.Runtime;

public class ExplicitNodeReference {

	public static long NULL_NODE = FastIntPair.createPair(-1, -1);

	public static long createReference(ExplicitLocalNode<?> node) {
		return FastIntPair.createPair(node.reference(), node.owner());
	}

	public static long createReference(int id, int owner) {
		return FastIntPair.createPair(id, owner);
	}

	public static int getId(long reference) {
		return FastIntPair.getFirst(reference);
	}

	public static int getOwner(long reference) {
		return FastIntPair.getSecond(reference);
	}

	public static String referenceToString(long reference) {
		if (reference == NULL_NODE)
			return "nullnode";
		return FastIntPair.getFirst(reference) + "@" + FastIntPair.getSecond(reference);
	}

	public static boolean isLocal(long reference) {
		return getOwner(reference) == Runtime.getRank();
	}
}
