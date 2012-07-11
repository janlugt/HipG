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

import hipg.format.synthetic.petrinet.PetriNetArc.ArcType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetriNet {
	private final String filePath;
	private final byte maxValue;
	private final boolean biological;

	private final Vector<PetriNetNode> places = new Vector<PetriNetNode>();
	private final Vector<PetriNetNode> transitions = new Vector<PetriNetNode>();
	private final Vector<PetriNetArc> arcs = new Vector<PetriNetArc>();

	private final Map<PetriNetNode, Vector<PetriNetArc>> outgoing = new HashMap<PetriNetNode, Vector<PetriNetArc>>();
	private final Map<PetriNetNode, Vector<PetriNetArc>> incoming = new HashMap<PetriNetNode, Vector<PetriNetArc>>();

	public PetriNet(final String filePath, final byte maxValue, final boolean degradation) {
		if (maxValue < 1 || (int) (maxValue + 1) > (int) Byte.MAX_VALUE) {
			throw new IllegalArgumentException(String.valueOf(maxValue));
		}
		this.filePath = filePath;
		this.maxValue = maxValue;
		this.biological = degradation;
	}

	public boolean biological() {
		return biological;
	}

	public int placeNum() {
		return places.size();
	}

	public String getFilePath() {
		return filePath;
	}

	public byte getMaxValue() {
		return maxValue;
	}

	public byte getGranularity() {
		return (byte) (maxValue + 1);
	}

	private PetriNetNode createTransition(int transitionId) {
		final PetriNetNode node = PetriNetNode.createTransition(transitionId);
		transitions.add(node);
		return node;
	}

	private PetriNetNode createPlace(String name, int placeId) {
		final PetriNetNode node = PetriNetNode.createPlace(name, placeId);
		places.add(node);
		return node;
	}

	private PetriNetArc createArc(PetriNetNode from, PetriNetNode to, boolean inhibition) {
		final PetriNetArc arc = new PetriNetArc(inhibition ? ArcType.INHIBITION : ArcType.ACTIVATION, from, to, 1);
		arcs.add(arc);
		Vector<PetriNetArc> out = outgoing.get(from);
		if (out == null) {
			out = new Vector<PetriNetArc>();
		}
		out.add(arc);
		outgoing.put(from, out);
		Vector<PetriNetArc> in = incoming.get(to);
		if (in == null) {
			in = new Vector<PetriNetArc>();
		}
		in.add(arc);
		incoming.put(to, in);
		return arc;
	}

	public Iterator<PetriNetNode> getTransitions() {
		return transitions.iterator();
	}

	public Iterator<PetriNetNode> getPlaces() {
		return places.iterator();
	}

	public PetriNetNode getTransition(final int index) {
		return transitions.get(index);
	}

	public PetriNetNode getPlace(final int index) {
		return places.get(index);
	}

	private final int getMarking(final PetriNetNode node, final byte[] marking) {
		return marking[node.getIndex()];
	}

	private final void updateMarking(final PetriNetNode node, final PetriNetArc arc, final byte[] marking) {
		final int index = node.getIndex();
		final int newValue = marking[index] + arc.sign() * arc.getWeight();
		if (0 <= newValue && newValue <= maxValue) {
			marking[index] = (byte) newValue;
		}
	}

	private final void decMarking(final PetriNetNode node, final byte[] marking) {
		final int index = node.getIndex();
		if (marking[index] > 0) {
			marking[index]--;
		}
	}

	private boolean preconditionEnabled(final byte[] marking, final PetriNetNode transition) {
		for (PetriNetArc arc : incoming.get(transition)) {
			final PetriNetNode place = arc.getSource();
			if (arc.isActivation()) {
				if (getMarking(place, marking) < arc.getWeight()) {
					return false;
				}
			} else {
				if (getMarking(place, marking) >= arc.getWeight()) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean postconditionEnabled(final byte[] marking, final PetriNetNode transition) {
		for (PetriNetArc arc : outgoing.get(transition)) {
			final PetriNetNode place = arc.getTarget();
			if (arc.isActivation()) {
				if (getMarking(place, marking) + arc.getWeight() > maxValue) {
					return false;
				}
			} else {
				if (getMarking(place, marking) - arc.getWeight() < 0) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean isEnabled(final byte[] marking, final PetriNetNode transition) {
		return transition.isTransition() && preconditionEnabled(marking, transition)
				&& (biological || postconditionEnabled(marking, transition));
	}

	public boolean fireEnabledTransition(final byte[] marking, final PetriNetNode transition) {
		if (!biological) {
			for (PetriNetArc arc : incoming.get(transition)) {
				updateMarking(arc.getSource(), arc, marking);
			}
		}
		for (PetriNetArc arc : outgoing.get(transition)) {
			updateMarking(arc.getTarget(), arc, marking);
		}
		return true;
	}

	public boolean isDegradationEnabled(final byte[] marking, final PetriNetNode place) {
		if (!biological || getMarking(place, marking) == 0) {
			return false;
		}
		final Vector<PetriNetArc> incomingArcs = incoming.get(place);
		if (incomingArcs != null) {
			for (PetriNetArc arc : incomingArcs) {
				if (arc.isActivation() && preconditionEnabled(marking, arc.getSource())) {
					return false;
				}
			}
		}
		return true;
	}

	public void fireEnabledDegradation(final byte[] marking, final PetriNetNode place) {
		decMarking(place, marking);
	}

	private void build(BufferedReader data) throws ParseException {
		final Map<String, PetriNetNode> xref = new HashMap<String, PetriNetNode>();
		String line = null;
		int lineNumber = 1;
		int placeId = 0;
		int transitionId = 0;
		final Pattern pattern = Pattern.compile(
				"(\\^?\\w+(?:\\s*&\\s*\\^?\\w+)*)\\s*-(>|\\|)\\s*(\\w+(?:\\s*&\\s*\\w+)*)", Pattern.CASE_INSENSITIVE);
		final Pattern splitter = Pattern.compile("\\s*&\\s*");
		try {
			while ((line = data.readLine()) != null) {
				final Matcher matcher = pattern.matcher(line);
				if (!matcher.matches()) {
					throw new ParseException("The line doesn't match the format", lineNumber);
				}
				final PetriNetNode transition = createTransition(transitionId++);
				String[] genes = splitter.split(matcher.group(1));
				for (String gene : genes) {
					final boolean negate = gene.charAt(0) == '^';
					gene = negate ? gene.substring(1) : gene;
					PetriNetNode node = xref.get(gene);
					if (node == null) {
						node = createPlace(gene, placeId++);
						xref.put(gene, node);
					}
					createArc(node, transition, negate);
				}
				final char type = matcher.group(2).charAt(0);
				genes = splitter.split(matcher.group(3));
				for (String gene : genes) {
					PetriNetNode node = xref.get(gene);
					if (node == null) {
						node = createPlace(gene, placeId++);
						xref.put(gene, node);
					}
					createArc(transition, node, type == '|');
				}
				lineNumber++;
			}
		} catch (IOException e) {
			throw new ParseException(e.getLocalizedMessage() + " (in line '" + line + "')", lineNumber);
		}
	}

	public static PetriNet parse(final byte maxValue, final String filePath, final boolean degradation) {
		final PetriNet petriNet = new PetriNet(filePath, maxValue, degradation);
		final BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(new File(filePath)));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		try {
			petriNet.build(reader);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}

		return petriNet;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("PetriNet(file=" + filePath + ", genes=[");
		for (int i = 0; i < places.size(); i++) {
			PetriNetNode place = places.get(i);
			sb.append(place.getName());
			if (i + 1 < places.size()) {
				sb.append(",");
			}
		}
		sb.append(", degradation=");
		sb.append(biological ? "yes" : "no");
		sb.append("])");
		return sb.toString();
	}

	public String toString(boolean detail) {
		final StringBuilder sb = new StringBuilder();
		sb.append(sb.toString());
		sb.append("[places=" + places.size() + ", transitions=" + transitions.size() + ", arcs=" + arcs.size() + "]");
		sb.append("\n");
		if (detail) {
			sb.append("places:\n");
			for (PetriNetNode place : places) {
				sb.append(place + " (index=" + place.getIndex() + ")\n");
			}
			sb.append("transitions:\n");
			for (PetriNetNode transition : transitions) {
				sb.append(transition + " (index=" + transition.getIndex() + ")\n");
			}
			for (PetriNetNode transition : transitions) {
				final Vector<PetriNetArc> in = incoming.get(transition);
				for (int i = 0; i < in.size(); i++) {
					PetriNetArc a = in.get(i);
					if (a.isInhibition()) {
						sb.append("~");
					}
					sb.append(a.getSource());
					if (i + 1 < in.size()) {
						sb.append(", ");
					}
				}
				sb.append(" --> ");
				final Vector<PetriNetArc> out = outgoing.get(transition);
				for (int i = 0; i < out.size(); i++) {
					PetriNetArc a = out.get(i);
					if (a.isInhibition()) {
						sb.append("~");
					}
					sb.append(a.getTarget());
					if (i + 1 < in.size()) {
						sb.append(", ");
					}
				}
				sb.append("\n");
			}
		}
		return sb.toString();
	}
}
