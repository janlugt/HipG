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

import myutils.storage.PairIterable;
import myutils.storage.PairIterator;

/**
 * Simplified map interface for the use of MapGraph.
 * 
 * @author Ela Krepska e.krepska@vu.nl
 * 
 * @param <K>
 * @param <V>
 */
public interface OnTheFlyMap<K, V> extends PairIterable<K, V> {
	public V get(K key);

	public void put(K key, V value);

	public int size();

	public int capacity();

	public int conflicts();
	
	public PairIterator<K, V> stateNodeIterator();
}
