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

public class PetriNetArc {

	public enum ArcType {
		ACTIVATION, INHIBITION
	};

	private final ArcType type;
	private final PetriNetNode source;
	private final PetriNetNode target;
	private final int weight;

	public PetriNetArc(final ArcType type, final PetriNetNode from, final PetriNetNode to, final int weight) {
		this.type = type;
		this.source = from;
		this.target = to;
		this.weight = weight;
	}

	public ArcType getType() {
		return type;
	}

	public boolean isActivation() {
		return type == ArcType.ACTIVATION;
	}

	public boolean isInhibition() {
		return type == ArcType.INHIBITION;
	}
	
	public int sign() {
		return (type == ArcType.ACTIVATION ? 1 : -1);
	}

	public PetriNetNode getSource() {
		return source;
	}

	public PetriNetNode getTarget() {
		return target;
	}

	public int getWeight() {
		return weight;
	}

	@Override
	public String toString() {
		return (isActivation() ? "-->" : "--|") + " " + target.toString();
	}
}
