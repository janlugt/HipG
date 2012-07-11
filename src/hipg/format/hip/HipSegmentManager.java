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

package hipg.format.hip;

import java.util.ArrayList;

public class HipSegmentManager {

	private final ArrayList<HipSegment> segments;
	private final int segmentsPerWorker;
	private final int remainder;

	public HipSegmentManager(ArrayList<HipSegment> segments, int poolSize) {
		this.segments = segments;
		this.segmentsPerWorker = (int) Math.floor((double) segments.size() / (double) poolSize);
		this.remainder = segments.size() - segmentsPerWorker * poolSize;
	}

	public ArrayList<HipSegment> mySegments(int me) {
		ArrayList<HipSegment> mySegments = new ArrayList<HipSegment>();
		for (HipSegment s : segments)
			if (owner(s.getId()) == me)
				mySegments.add(s);
		return mySegments;
	}

	public int owner(HipSegment segment) {
		return owner(segment.getId());
	}

	public int owner(int segment) {
		if (segment < remainder * (1 + segmentsPerWorker))
			return segment / (segmentsPerWorker + 1);
		else
			return remainder + (segment - remainder * (1 + segmentsPerWorker)) / segmentsPerWorker;
	}

	public int offset(int node, int segment) {
		if (node < 0 || node > segments.get(segment).getStates()) {
			throw new RuntimeException("Node " + node + " requested from segment " + segment + ": "
					+ segments.get(segment));
		}
		int offset = 0;
		int owner = owner(segment);
		for (int s = 0; s < segment; s++) {
			if (owner(s) == owner) {
				offset += segments.get(s).getStates();
			}
		}
		return offset + node;
	}

}
