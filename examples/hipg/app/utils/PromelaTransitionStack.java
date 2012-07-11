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

import myutils.ObjectCache;
import hipg.LocalNode;
import spinja.promela.model.PromelaTransition;

public class PromelaTransitionStack {

	private PromelaTransitionElement top = null;
	private final ObjectCache<PromelaTransitionElement> cache = new ObjectCache<PromelaTransitionElement>(1024 * 16);

	public PromelaTransitionStack() {
	}

	public void push(final LocalNode<?> node) {
		PromelaTransitionElement el = cache.get();
		if (el == null) {
			el = new PromelaTransitionElement();
		}
		el.reset();
		el.node = node;
		el.next = top;
		top = el;
	}

	public void pop() {
		if (top == null) {
			throw new RuntimeException("Popping from an empty stack");
		}
		cache.add(top);
		top = top.next;
	}

	public PromelaTransitionElement peek() {
		return top;
	}

	public int size() {
		int sz = 0;
		PromelaTransitionElement t = top;
		while (t != null) {
			sz++;
			t = t.next;
		}
		return sz;
	}

	public static final class PromelaTransitionElement {

		public LocalNode<?> node = null;
		public PromelaTransition lastTransition = null;
		public int lastIndex = -1;
		public byte[] lastNeighbor;

		private PromelaTransitionElement next;

		public PromelaTransitionElement() {
		}

		public void reset() {
			this.lastIndex = -1;
			this.lastTransition = null;
			this.lastNeighbor = null;
		}
	}

}
