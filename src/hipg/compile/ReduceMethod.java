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

import myutils.StringUtils;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

public class ReduceMethod {

	private final Method method;
	private final int id;
	private String annotationClassName;

	public ReduceMethod(Method method, int id, String annotationClassName) {
		this.method = method;
		this.id = id;
		this.annotationClassName = annotationClassName;
	}

	public static final String checkReduceMethod(Method method) {
		if (method.getReturnType().equals(Type.VOID))
			return "Reduce method " + method.getName() + " returns void";
		Type[] argumentTypes = method.getArgumentTypes();
		if (argumentTypes == null || argumentTypes.length != 1)
			return "Reduce method " + method.getName() + " should have 1 argument";
		Type paramType = argumentTypes[0];
		Type returnType = method.getReturnType();
		if (!paramType.equals(returnType))
			return "Reduce method " + method.getName() + " has different parameter and return types";
		return null;
	}

	public final Method getMethod() {
		return method;
	}

	public final int getId() {
		return id;
	}

	public final String getAnnotationClassName() {
		return annotationClassName;
	}

	public final String getName() {
		return method.getName();
	}

	public final String getSynchronizerReduceMethodName() {
		return StringUtils.LowercaseFirstLetter(annotationClassName.substring(annotationClassName.lastIndexOf(".") + 1));
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean detail) {
		return method.getName() + (detail ? " (id=" + id + ",annotation=" + annotationClassName + ")" : "");
	}
}
