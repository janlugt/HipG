/**
 * Copyright (c) 2010, 2011 Vrije Universiteit Amsterdam.
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

import hipg.compile.Simulator.StackElement;

import java.util.ArrayList;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.ObjectType;

public class NotificationCall {

	private static int uniqId = 0;

	/** Class that the method containing this blocking call is in. */
	private final ClassGen cg;
	/** Handle of this blocking call. */
	private final InstructionHandle handle;
	/** The call. */
	private final InvokeInstruction invoke;
	/** Stack before the call. */
	private final ArrayList<StackElement> stack;
	/** Called notification method. */
	private final NotificationMethod notificationMethod;
	/** Unique id */
	private final int id = uniqId++;

	/** Creates a blocking call. */
	public NotificationCall(ClassGen cg, InstructionHandle handle, InvokeInstruction invoke,
			ArrayList<StackElement> stack, NotificationMethod notificationMethod) {
		this.cg = cg;
		this.handle = handle;
		this.invoke = invoke;
		this.stack = stack;
		this.notificationMethod = notificationMethod;
	}

	public InstructionHandle getHandle() {
		return handle;
	}

	public InvokeInstruction getInvoke() {
		return invoke;
	}

	public ArrayList<StackElement> getStack() {
		return stack;
	}

	public String getCalledMethodName() {
		return invoke.getMethodName(cg.getConstantPool());
	}

	public NotificationMethod getNotificationMethod() {
		return notificationMethod;
	}

	public int getId() {
		return id;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean detail) {
		StringBuilder sb = new StringBuilder();
		sb.append(invoke.getMethodName(cg.getConstantPool()));
		if (detail) {
			// print stack
			sb.append("[");
			sb.append("stack: ");
			if (stack.size() == 0) {
				sb.append("empty");
			} else {
				for (int i = 0; i < stack.size(); i++)
					sb.append(stack.get(i) + (i + 1 < stack.size() ? ", " : ""));
			}
		}
		return sb.toString();
	}

	public static NotificationMethod checkNonBlockingCall(Simulator simulator, InvokeInstruction invoke,
			ArrayList<NotificationMethod> notificationMethods) throws ClassNotFoundException {

		/*
		 * check if called on a synchronizer
		 */
		final String calledClassName = ((ObjectType) simulator.getReferenceType(invoke)).getClassName();
		final JavaClass calledClass = ClassRepository.lookupClass(calledClassName);
		if (!calledClass.instanceOf(ClassRepository.getSynchronizerInterface())) {
			return null;
		}

		/*
		 * check if the call refers to one of the notification methods
		 * identified in the target class
		 */
		final String methodName = simulator.getMethodName(invoke);
		NotificationMethod notificationMethod = null;
		for (NotificationMethod nm : notificationMethods) {
			Method method = nm.getMethod();
			if (method.getName().equals(methodName) && method.getSignature().equals(simulator.getSignature(invoke))) {
				if (notificationMethod != null) {
					throw new RuntimeException("Two notifications method fit the call to " + methodName + " on "
							+ calledClassName + " with signature " + method.getSignature());
				}
				notificationMethod = nm;
			}
		}
		return notificationMethod;
	}
}
