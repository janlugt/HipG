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

package hipg.format;

public interface GraphMaker {

	public int segments();

	public int nodes(int segment);

	public int transitions(int segment, int destSegment);

	public String path();

	public long addNode(int segment) throws GraphCreationException;

	public long addNode() throws GraphCreationException;

	public void addTransition(long from, long to, int label) throws GraphCreationException;

	public void addTransition(int fromOwner, int fromId, int toOwner, int toId, int label) throws GraphCreationException;

	public void finish(long root) throws GraphCreationException;

	public long getGlobalStateCount();

	public long getGlobalTransitionsCount();

	public long getGlobalStateCountMin();

	public long getGlobalStateCountMax();

	public long getGlobalTransitionsCountMin();

	public long getGlobalTransitionsCountMax();

}
