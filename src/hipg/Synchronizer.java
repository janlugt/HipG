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

/**
 * Synchronizer interface.
 * 
 * @author ela, ekr@cs.vu.nl
 */
public interface Synchronizer {

	public static final int EXECUTION_ONE = -1000001;
	public static final int EXECUTION_ALL = -1000002;
	public static final int EXECUTION_OWNED = -1000003;
	public static final int EXECUTION_UNSET = -1000004;

	public int getId();

	public int getFatherExecutionMode();

	public int getExecutionMode();

	public int getOwner();

	public int getMaster();

	public int getFatherId();

	public int getFatherOwner();

	public String fatherName();

	public String name();

	public void spawn(int destination, Synchronizer synchronizer, int executionMode);

	public void spawn(Synchronizer synchronizer, int executionMode);

	public void spawn(Node node, Synchronizer synchronizer, int executionMode);

	public void spawn(int destination, Synchronizer synchronizer);

	public void spawn(Synchronizer synchronizer);

	public void spawn(Node node, Synchronizer synchronizer);

	public void spawnOwned(Synchronizer synchronizer);

	public void spawnOne(Synchronizer synchronizer);

	public void spawnAll(Synchronizer synchronizer);

	public void spawnOwned(Node node, Synchronizer synchronizer);

	public void spawnOne(Node node, Synchronizer synchronizer);

	public void spawnOwned(int destination, Synchronizer synchronizer);

	public void spawnOne(int destination, Synchronizer synchronizer);

	public void run();

	public void barrier();

}
