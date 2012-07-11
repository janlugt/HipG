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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ObjectType;

public class ClassRepository {
	public static final String NodeInterfaceName = "hipg.Node";
	public static final String RuntimeClassName = "hipg.runtime.Runtime";
	public static final String CommunicationClassName = "hipg.runtime.Communication";
	public static final String SynchronizerClassName = "hipg.runtime.Synchronizer";
	public static final String SynchronizerInterfaceName = "hipg.Synchronizer";
	public static final String LocalNodeClassName = "hipg.LocalNode";
	public static final String ExplicitLocalNodeClassName = "hipg.graph.ExplicitLocalNode";
	public static final String OnTheFlyLocalNodeClassName = "hipg.graph.OnTheFlyLocalNode";
	public static final String ExplicitGraphClassName = "hipg.graph.ExplicitGraph";
	public static final String OnTheFlyGraphClassName = "hipg.graph.OnTheFlyGraph";
	public static final String GraphInterfaceName = "hipg.Graph";
	public static final String SerializationClassName = "hipg.compile.Serialization";
	public static final String FastMessageClassName = "hipg.runtime.FastMessage";
	public static final String PairUtilsClassName = "myutils.tuple.pair.FastIntPair";
	public static final String IOUtilsClassName = "myutils.IOUtils";
	public static final String BigQueueClassName = "myutils.storage.bigarray.BigByteQueue";
	public static final String SerializableInterfaceName = "myutils.Serializable";

	public static final ObjectType RuntimeType = new ObjectType(RuntimeClassName);
	public static final ObjectType CommunicationType = new ObjectType(CommunicationClassName);
	public static final ObjectType SynchronizerType = new ObjectType(SynchronizerClassName);
	public static final ObjectType SynchronizerInterfaceType = new ObjectType(SynchronizerInterfaceName);
	public static final ObjectType BigQueueType = new ObjectType(BigQueueClassName);
	public static final ObjectType FastMessageType = new ObjectType(FastMessageClassName);
	public static final ObjectType LocalNodeType = new ObjectType(LocalNodeClassName);
	public static final ObjectType ExplicitGraphType = new ObjectType(ExplicitGraphClassName);
	public static final ObjectType OnTheFlyGraphType = new ObjectType(OnTheFlyGraphClassName);
	public static final ObjectType GraphType = new ObjectType(GraphInterfaceName);
	public static final ObjectType SerializableType = new ObjectType(SerializableInterfaceName);

	private static JavaClass NodeInterface;
	private static JavaClass LocalNodeClass;
	private static JavaClass SynchronizerClass;
	private static JavaClass SynchronizerInterface;
	private static JavaClass ExplicitLocalNodeClass;
	private static JavaClass OnTheFlyLocalNodeClass;
	private static JavaClass ExplicitGraphClass;
	private static JavaClass OnTheFlyGraphClass;
	private static JavaClass GraphInterface;
	private static JavaClass SerializableInterface;

	static void init() {
		try {
			NodeInterface = Repository.lookupClass(NodeInterfaceName);
			LocalNodeClass = Repository.lookupClass(LocalNodeClassName);
			SynchronizerClass = Repository.lookupClass(SynchronizerClassName);
			SynchronizerInterface = Repository.lookupClass(SynchronizerInterfaceName);
			ExplicitLocalNodeClass = Repository.lookupClass(ExplicitLocalNodeClassName);
			OnTheFlyLocalNodeClass = Repository.lookupClass(OnTheFlyLocalNodeClassName);
			ExplicitGraphClass = Repository.lookupClass(ExplicitGraphClassName);
			OnTheFlyGraphClass = Repository.lookupClass(OnTheFlyGraphClassName);
			GraphInterface = Repository.lookupClass(GraphInterfaceName);
			SerializableInterface = Repository.lookupClass(SerializableInterfaceName);
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static JavaClass getNodeInterface() {
		if (NodeInterface == null)
			init();
		return NodeInterface;
	}

	public static JavaClass getLocalNodeClass() {
		if (LocalNodeClass == null)
			init();
		return LocalNodeClass;
	}

	public static JavaClass getExplicitLocalNodeClass() {
		if (LocalNodeClass == null)
			init();
		return ExplicitLocalNodeClass;
	}

	public static JavaClass getExplicitGraphClass() {
		if (ExplicitGraphClass == null)
			init();
		return ExplicitGraphClass;
	}

	public static JavaClass getOnTheFlyLocalNodeClass() {
		if (LocalNodeClass == null)
			init();
		return OnTheFlyLocalNodeClass;
	}

	public static JavaClass getOnTheFlyGraphClass() {
		if (OnTheFlyGraphClass == null)
			init();
		return OnTheFlyGraphClass;
	}

	public static JavaClass getGraphInterface() {
		if (GraphInterface == null)
			init();
		return GraphInterface;
	}

	public static JavaClass getSynchronizerClass() {
		if (SynchronizerClass == null)
			init();
		return SynchronizerClass;
	}

	public static JavaClass getSynchronizerInterface() {
		if (SynchronizerInterface == null)
			init();
		return SynchronizerInterface;
	}

	public static JavaClass getSerializableInterface() {
		if (SerializableInterface == null)
			init();
		return SerializableInterface;
	}

	public static JavaClass lookupClass(ObjectType type) {
		return lookupClass(type.getClassName());
	}

	public static JavaClass lookupClass(String className) {
		try {
			return Repository.lookupClass(className);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
			return null;
		}
	}

	public static Class<?> forName(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
			return null;
		}
	}
}
