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

package hipg.utils;

import hipg.Config;
import hipg.Graph;
import hipg.Node;
import hipg.Synchronizer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class MethodCache<T> {

	public static final byte NOCHANGE = 0;
	public static final byte CHSYNCH = 1;
	public static final byte CHNODE = 2;

	private final static Class<?> SynchronizerInterface = Synchronizer.class;
	private final static Class<?> NodeClass = Node.class;
	private final static Class<?> GraphClass = Graph.class;

	private final Class<?> clazz;
	public final Method[] methods;
	private final byte[][] analysis;
	private final Constructor<? extends T> constructor;
	private final Method init;

	@SuppressWarnings("unchecked")
	public MethodCache(Class<? extends T> clazz, Class<?> Tclazz) {

		this.clazz = clazz;

		// copy
		methods = ReflectionUtils.getAllDeclaredMethods(clazz, Tclazz);

		// analyze translation of parameters for those methods
		analysis = new byte[methods.length][];
		for (int i = 0; i < analysis.length; i++) {
			Method method = methods[i];
			Class<?>[] types = method.getParameterTypes();
			boolean changed = false;
			if (types != null) {
				byte[] changes = new byte[types.length];
				for (int j = 0; j < types.length; j++) {
					Class<?> type = types[j];
					if (ReflectionUtils.implementsInterface(type, SynchronizerInterface)) {
						changes[j] = CHSYNCH;
						changed = true;
					} else if (ReflectionUtils.implementsInterface(type, NodeClass)) {
						changes[j] = CHNODE;
						changed = true;
					} else {
						changes[j] = NOCHANGE;
					}
				}
				if (changed)
					analysis[i] = changes;
			}
		}

		// find constructor
		if (ReflectionUtils.isSubclassOf(clazz, NodeClass)) {
			Constructor<?> constructor0 = null;

			// // find constructor with no parameters
			// try {
			// constructor0 = clazz.getConstructor(new Class<?>[0]);
			// } catch (Throwable t) {
			// throw new RuntimeException(
			// "Could not find a constructor parametrized with a graph in "
			// + clazz + ": " + t);
			// }

			// find constructor with one parameter whose type is subclass of
			// Graph
			Constructor<?>[] constructors = clazz.getDeclaredConstructors();
			if (constructors == null || constructors.length == 0)
				constructor0 = null;
			else {
				for (int i = 0; i < constructors.length; i++) {
					Constructor<?> c = constructors[i];
					Class<?>[] params = c.getParameterTypes();
					if (params == null || params.length != 1)
						continue;
					Class<?> param0 = params[0];
					if (ReflectionUtils.isSubclassOf(param0, GraphClass)) {
						if (constructor0 != null)
							throw new RuntimeException("Found two constructors parametrized with graph in " + clazz);
						constructor0 = c;
					}
				}
			}

			if (constructor0 == null)
				throw new RuntimeException("Could not find a constructor parametrized with a graph in " + clazz);
			try {
				constructor = (Constructor<? extends T>) constructor0;
			} catch (Throwable t) {
				throw new RuntimeException("Could not convert the constructor " + constructor0
						+ " to a required constructor parametrized with a graph: " + t.getMessage(), t);
			}
		} else {
			constructor = null;
		}

		// find init(father) method
		if (ReflectionUtils.implementsInterface(clazz, SynchronizerInterface)) {
			Method initMethod = null;
			for (Method method : clazz.getDeclaredMethods()) {
				if ("init".equals(method.getName())) {
					Class<?>[] parameterTypes = method.getParameterTypes();
					if (parameterTypes != null && parameterTypes.length == 1) {
						if (ReflectionUtils.implementsInterface(parameterTypes[0], SynchronizerInterface)) {
							if (initMethod != null) {
								throw new RuntimeException("Found more than one init(synchronizer) method in " + clazz);
							}
							initMethod = method;
						}
					}
				}
			}
			init = initMethod;
		} else {
			init = null;
		}
	}

	public byte[] getAnalysis(int index) {
		if (Config.ERRCHECK) {
			if (index >= methods.length || index < 0 || methods[index] == null)
				throw new RuntimeException("Could not access method " + index + " of class " + clazz.getName());
		}
		return analysis[index];
	}

	public Method get(int index) {
		if (Config.ERRCHECK) {
			if (index >= methods.length || index < 0 || methods[index] == null)
				throw new RuntimeException("Could not access method " + index + " of class " + clazz.getName());
		}
		return methods[index];
	}

	public Constructor<? extends T> constructor() {
		return constructor;
	}

	public Method init() {
		return init;
	}

	public int size() {
		return methods.length;
	}

	public Class<?> clazz() {
		return clazz;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Constructor: " + constructor + "; Methods: ");
		for (int i = 0; i < methods.length; i++) {
			sb.append(i + ":" + methods[i].getName());
			byte[] a = analysis[i];
			if (a != null && a.length > 0) {
				sb.append("[");
				for (int j = 0; j < a.length; j++) {
					switch (a[j]) {
					case NOCHANGE:
						sb.append("_");
						break;
					case CHNODE:
						sb.append("N");
						break;
					case CHSYNCH:
						sb.append("S");
						break;
					default:
						sb.append("?");
						break;
					}
				}
				sb.append("]");
			}
			if (i + 1 < methods.length)
				sb.append(", ");
		}
		return sb.toString();
	}

}
