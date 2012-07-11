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

package hipg.graph;

import hipg.Node;
import myutils.storage.PairIterator;
import myutils.storage.map.UserHashMap;

public final class OnTheFlyHashMap<TNode extends Node> extends UserHashMap<byte[], TNode> implements
		OnTheFlyMap<byte[], TNode> {
	private final OnTheFlyHash hash;

	public OnTheFlyHashMap(OnTheFlyHash hash, int initialCapacity) {
		super(initialCapacity);
		this.hash = hash;
	}

	@Override
	public int hash(byte[] state) {
		return hash.id(state);
	}

	@Override
	public PairIterator<byte[], TNode> stateNodeIterator() {
		return super.iterator();
	}
}
