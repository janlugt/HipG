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

package hipg.format.synthetic.petrinet;

public class PetriNetNode {
	private static enum NodeType {
		PLACE, TRANSITION
	};

	final String name;
	final NodeType type;
	final int index;

	private PetriNetNode(final NodeType type, final String name, final int index) {
		this.type = type;
		this.name = name;
		this.index = index;
	}

	public String getName() {
		return name;
	}

	public NodeType getType() {
		return type;
	}

	public int getIndex() {
		return index;
	}

	public boolean isPlace() {
		return type == NodeType.PLACE;
	}

	public boolean isTransition() {
		return type == NodeType.TRANSITION;
	}

	public static PetriNetNode createPlace(String name, int placeId) {
		return new PetriNetNode(NodeType.PLACE, name, placeId);
	}

	public static PetriNetNode createTransition(int transitionId) {
		return new PetriNetNode(NodeType.TRANSITION, null, transitionId);
	}

	@Override
	public String toString() {
		if (isPlace()) {
			return "Place(" + name + ")";
		} else {
			return "Transition(" + index + ")";
		}
	}
}
