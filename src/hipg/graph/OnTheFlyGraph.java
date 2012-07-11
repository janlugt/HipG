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

package hipg.graph;

import hipg.Graph;
import hipg.LocalNode;
import hipg.Node;
import hipg.utils.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.util.Arrays;

public class OnTheFlyGraph<TNode extends Node> extends MapGraph<TNode, byte[]> {

	/** Node class. */
	private final Class<?> TLocalNodeClass;

	/** Constructor to create nodes on-the-fly. */
	private final Constructor<TNode> nodeConstructor;

	/** Hash function used to hash states. */
	private final OnTheFlyHash hash;

	/** Number of nodes created but not stored. */
	private int nonStoredNodes = 0;

	/** Number of matched nodes. */
	private int matchedNodes = 0;

	public OnTheFlyGraph(OnTheFlyHash hash, Class<?> TLocalNodeClass, int hashTableSizeLog) {
		super(new OnTheFlyHashMap<LocalNode<TNode>>(hash, (1 << hashTableSizeLog)));
		if (TLocalNodeClass == null) {
			throw new NullPointerException();
		}
		this.TLocalNodeClass = TLocalNodeClass;
		this.hash = hash;
		this.nodeConstructor = findNodeConstructor(TLocalNodeClass, Graph.class, Object.class);
	}

	public OnTheFlyHash hash() {
		return hash;
	}

	public final OnTheFlyLocalNode<TNode> node(final byte[] state) {
		OnTheFlyLocalNode<TNode> node = (OnTheFlyLocalNode<TNode>) super.node(state);
		if (node == null) {
			node = createNode(state);
			if (node.shouldStore()) {
				super.addNode(state, node);
			} else {
				nonStoredNodes++;
			}
		} else {
			matchedNodes++;
		}
		return node;
	}

	public int getNotStoredNodesCount() {
		return nonStoredNodes;
	}

	public int getMatchedNodesCount() {
		return matchedNodes;
	}

	@SuppressWarnings("unchecked")
	private OnTheFlyLocalNode<TNode> createNode(byte[] state) {
		try {
			return (OnTheFlyLocalNode<TNode>) nodeConstructor.newInstance(this, state);
		} catch (Throwable e) {
			throw new RuntimeException("Cannot create node of class " + TLocalNodeClass.getName()
					+ " with the constructor: " + nodeConstructor + ": " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <TNode extends Node> Constructor<TNode> findNodeConstructor(Class<?> TLocalNodeClass,
			Class<?>... args) {
		Constructor<?> constructor = ReflectionUtils.findConstructor(TLocalNodeClass, args);
		if (constructor == null) {
			throw new RuntimeException("Could not find constructor " + "for the node class "
					+ TLocalNodeClass.getName() + " with arguments " + Arrays.deepToString(args));
		}
		try {
			return (Constructor<TNode>) constructor;
		} catch (Throwable t) {
			throw new RuntimeException("Found constructor for the class " + TLocalNodeClass.getName()
					+ " is not a node constructor");
		}
	}
}
