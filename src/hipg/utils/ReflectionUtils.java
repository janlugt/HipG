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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;

public class ReflectionUtils {

	/**
	 * Finds a constructor of a class with a single parameter extending another
	 * specified class.
	 * 
	 * @param Clazz
	 *            Class to search the constructor in
	 * @param parameterSuperClass
	 *            A superclass of a parameter
	 * 
	 * @return The constructor
	 */
	public static Constructor<?> findConstructor(Class<?> Clazz, Class<?>... ParameterSuperClasses) {
		int expectedParameterCount = (ParameterSuperClasses == null ? 0 : ParameterSuperClasses.length);
		Constructor<?>[] constructors = Clazz.getDeclaredConstructors();
		for (int i = 0; i < constructors.length; i++) {
			Constructor<?> constructor = constructors[i];
			Class<?>[] parameterTypes = constructor.getParameterTypes();
			if (parameterTypes == null && expectedParameterCount == 0
					|| parameterTypes.length == expectedParameterCount) {
				boolean paramOK = true;
				for (int p = 0; p < parameterTypes.length && paramOK; p++) {
					Class<?> expectedSuperClass = parameterTypes[p];
					if (!isSubclassOf(parameterTypes[p], expectedSuperClass)) {
						paramOK = false;
					}
				}
				if (paramOK) {
					return constructor;
				}
			}
		}
		return null;
	}

	public static boolean isSubclassOf(Class<?> subClass, Class<?> superClass) {
		do {
			if (subClass.equals(superClass))
				return true;
			subClass = subClass.getSuperclass();
		} while (subClass != null && !subClass.equals(Object.class));
		return false;
	}

	public static boolean implementsInterface(Class<?> subClass, Class<?> interfaceClass) {
		while (subClass != null && !subClass.equals(Object.class)) {
			Class<?>[] interfaces = subClass.getInterfaces();
			for (Class<?> interfce : interfaces) {
				if (isSubclassOf(interfce, interfaceClass))
					return true;
			}
			subClass = subClass.getSuperclass();
		}
		return false;
	}

	public static long getLong(int i, Object[] parameters, Method method) {
		long value;
		if (Config.ERRCHECK) {
			if (parameters == null || i < 0 || parameters[i] == null || i >= parameters.length)
				throw new RuntimeException("Could not convert parameter " + i + " ("
						+ (parameters == null ? "params null" : parameters[i]) + ")" + " of method " + method
						+ " to long");

			try {
				value = ((Long) parameters[i]).longValue();
			} catch (Throwable t) {
				throw new RuntimeException("Could not convert parameter " + i + "  of method " + method + " to long: "
						+ t.getMessage(), t);
			}
		} else {
			value = ((Long) parameters[i]).longValue();
		}
		return value;
	}

	private static class MethodComparator implements Comparator<Method> {
		public int compare(Method m1, Method m2) {
			String n1 = m1.getName();
			String n2 = m2.getName();
			int c = n1.compareTo(n2);
			if (c != 0)
				return c;
			int p1 = m1.getParameterTypes().length;
			int p2 = m2.getParameterTypes().length;
			if (p1 != p2)
				return (p1 - p2);
			String g1 = m1.toGenericString();
			String g2 = m2.toGenericString();
			int d = g1.compareTo(g2);
			if (d == 0) {
				throw new RuntimeException("The methods " + m1 + " and " + m2 + " seem equal..... Very unprobable "
						+ "but since it happened, this would be a bug. "
						+ "Rename one of the methods as a workaround. ");
			}
			return d;
		}
	}

	private static void reverse(Object[] array) {
		if (array == null || array.length <= 1)
			return;
		for (int i = 0; i < array.length / 2; i++) {
			Object t = array[i];
			array[i] = array[array.length - 1 - i];
			array[array.length - 1 - i] = t;
		}
	}

	public static Method[] getAllDeclaredMethods(Class<?> clazz, Class<?> superClazz) {
		Vector<Method> methods = new Vector<Method>();
		MethodComparator cmp = new MethodComparator();
		while (!clazz.equals(Object.class) && !clazz.equals(superClazz)) {
			Method[] declaredMethods = clazz.getDeclaredMethods();
			Arrays.sort(declaredMethods, cmp);
			reverse(declaredMethods);
			for (Method m : declaredMethods)
				methods.add(m);
			clazz = clazz.getSuperclass();
		}
		Method[] allDeclaredMethods = new Method[methods.size()];
		methods.toArray(allDeclaredMethods);
		reverse(allDeclaredMethods);
		return allDeclaredMethods;
	}

	public static Method findMethod(Class<?> clazz, String name, Type retType, int argCount) {
		Method[] methods = clazz.getMethods();
		Method foundMethod = null;
		if (methods != null) {
			for (Method method : methods) {
				if (method.getName().equals(name) && (retType.equals(method.getReturnType()))
						&& argCount == method.getParameterTypes().length) {
					if (foundMethod != null) {
						throw new RuntimeException("More than 1 method with name " + name + " return type " + retType
								+ " and " + argCount + "arguments found in class " + clazz.getName());
					}
					foundMethod = method;
				}
			}
		}
		if (foundMethod == null) {
			throw new RuntimeException("No method with name " + name + " and return type " + retType + " and "
					+ argCount + " arguments found in class " + clazz.getName());
		}
		return foundMethod;
	}
}
