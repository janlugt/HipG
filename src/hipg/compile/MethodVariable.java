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

package hipg.compile;

import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.Type;

public class MethodVariable {
	private static int uniqId = 0;
	private final LocalVariable v;
	private final Type t;
	private final int id = uniqId++;

	public MethodVariable(LocalVariable v) {
		this.v = v;
		this.t = Type.getType(v.getSignature());
	}

	public boolean hasInScope(InstructionHandle ih) {
		final int position = ih.getPosition();
		final int start = v.getStartPC();
		final int end = v.getStartPC() + v.getLength();
		return (start <= position && position < end);
	}

	public LocalVariable getLocalVariable() {
		return v;
	}

	public String getName() {
		return v.getName();
	}

	public String getPersistentName() {
		return v.getName() + "_" + id;
	}

	public Type getType() {
		return t;
	}

	public int getIndex() {
		return v.getIndex();
	}

	public int getPersistentIndex(int classVariables) {
		return v.getIndex();
	}

	@Override
	public String toString() {
		return getName() + ":" + v.getSignature() + " (pc=" + v.getStartPC() + ".." + (v.getStartPC() + v.getLength())
				+ ")";
	}
}
