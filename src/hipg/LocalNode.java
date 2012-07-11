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

package hipg;

import myutils.storage.bigarray.BigByteQueue;
import hipg.runtime.Runtime;

public abstract class LocalNode<TNode extends Node> implements Node {

	public LocalNode() {
	}

	abstract public boolean hasNeighbor(int index);

	abstract public TNode neighbor(int index);

	abstract public boolean isNeighborLocal(int index);

	abstract public int neighborOwner(int index);

	abstract public int neighborId(int index);

	public abstract String name();

	public boolean isLocal() {
		return true;
	}

	public int owner() {
		return Runtime.getRank();
	}

	public int hipg_parameters(final short methodId, final byte[] buf, int offset) {
		throw new RuntimeException("hipg_parameters() not defined on " + getClass().getSimpleName() + "! "
				+ "Did you apply the rewriter?");
	}

	public int hipg_execute(final short methodId, final hipg.Synchronizer synchronizer, final byte[] parameters,
			int offset) {
		throw new RuntimeException("hipg_execute(method,synchronizer,array " + "params,offset) not defined on "
				+ getClass().getSimpleName() + "! " + "Did you apply the rewriter?");
	}

	public void hipg_execute(final short methodId, final hipg.Synchronizer synchronizer, final BigByteQueue parameters) {
		throw new RuntimeException("hipg_execute(method,synchronizer,queue " + "of params) not defined on class "
				+ getClass().getSimpleName() + "! " + "Did you apply the rewriter?");
	}

}
