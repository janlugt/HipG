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

import hipg.compile.Simulator.StackElement;

import java.util.ArrayList;

import myutils.tuple.pair.IntValuePair;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.Type;

public class BlockingCall {

	public static final int BCTYPE_SYNC = 0;
	public static final int BCTYPE_BARRIER = 1;
	public static final int BCTYPE_REDUCE = 2;
	public static final int BCTYPE_BARRED = 3;

	private static int uniqId = 0;

	/** Class that the method containing this blocking call is in. */
	private final ClassGen cg;
	/** Handle of this blocking call. */
	private final InstructionHandle handle;
	/** The call. */
	private final InvokeInstruction invoke;
	/** Stack before the call. */
	private final ArrayList<StackElement> stack;
	/** Variables active when the call occurs. */
	private final ArrayList<MethodVariable> vars;
	/** Kind of method (barrier, sync, reduce, barrierAndReduce). */
	private final int type;
	/** Reduce method, if kind is reduce or barrier with reduce. */
	private final ReduceMethod reduceMethod;
	/** Unique id */
	private final int id = uniqId++;

	/** Creates a blocking call. */
	public BlockingCall(ClassGen cg, InstructionHandle handle, InvokeInstruction invoke, ArrayList<StackElement> stack,
			ArrayList<MethodVariable> activeVariables, int blockingCallType, ReduceMethod reduceMethod) {
		this.cg = cg;
		this.handle = handle;
		this.invoke = invoke;
		this.stack = stack;
		this.vars = activeVariables;
		this.type = blockingCallType;
		this.reduceMethod = reduceMethod;
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

	public ArrayList<MethodVariable> getActiveVariables() {
		return vars;
	}

	public String getCalledMethodName() {
		return invoke.getMethodName(cg.getConstantPool());
	}

	public boolean isBarrier() {
		return type == BCTYPE_BARRIER;
	}

	public boolean isSync() {
		return type == BCTYPE_SYNC;
	}

	public boolean isReduceMethod() {
		return type == BCTYPE_REDUCE;
	}

	public boolean isReduceAndBarrier() {
		return type == BCTYPE_BARRED;
	}

	public ReduceMethod getReduceMethod() {
		return reduceMethod;
	}

	public int getBlockingCallId() {
		return id;
	}

	public final int getBlockingCallType() {
		return type;
	}

	public final int getPC() {
		return handle.getPosition();
	}

	public static IntValuePair<ReduceMethod> checkBlockingCall(Simulator simulator, InvokeInstruction invoke,
			ArrayList<ReduceMethod> reduceMethods) {
		String methodName = simulator.getMethodName(invoke);
		Type returnType = simulator.getReturnType(invoke);
		Type[] argumentTypes = simulator.getArgumentTypes(invoke);
		int arguments = (argumentTypes == null ? 0 : argumentTypes.length);
		// check if the call is to a barrier
		if (methodName.equals("barrier") && Type.VOID.equals(returnType) && arguments == 0) {
			return new IntValuePair<ReduceMethod>(BCTYPE_BARRIER, null);
		}
		// check if the call is to a sync
		if (methodName.equals("sync") && Type.VOID.equals(returnType) && arguments == 0) {
			return new IntValuePair<ReduceMethod>(BCTYPE_SYNC, null);
		}
		// check if the call is to a reduce/barrierAndReduce method
		// named by the user
		if (arguments == 1) {
			for (ReduceMethod reduceMethod : reduceMethods) {
				Method rm = reduceMethod.getMethod();
				if (rm.getName().equals(methodName) && rm.getReturnType().equals(returnType)
						&& rm.getArgumentTypes()[0].equals(argumentTypes[0])) {
					return new IntValuePair<ReduceMethod>(BCTYPE_REDUCE, reduceMethod);
				}
			}
		}
		return null;
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
			sb.append("; ");
			// print active variables
			sb.append("active vars: ");
			if (vars.size() == 0) {
				sb.append("none");
			} else {
				for (int i = 0; i < vars.size(); i++)
					sb.append(vars.get(i) + (i + 1 < vars.size() ? ", " : ""));
			}
			sb.append("]");
		}
		return sb.toString();
	}
}
