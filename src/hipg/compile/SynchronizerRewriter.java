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

import hipg.BarrierAndReduce;
import hipg.Notification;
import hipg.Reduce;
import hipg.compile.Simulator.StackElement;
import hipg.runtime.Runtime;
import hipg.utils.BCELUtils;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import myutils.tuple.pair.IntValuePair;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ARRAYLENGTH;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.IADD;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.NOP;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.SWAP;
import org.apache.bcel.generic.TABLESWITCH;
import org.apache.bcel.generic.Type;

public class SynchronizerRewriter {

	private static final String tab = "  ";
	private static final String ttab = tab + tab;
	private static final String uniqPrefix = "_hipg_";
	private static final String pcVarName = "pc";
	private static final String pcmaxVarName = "pcmax";
	private static final String stackVarName = "stack_";
	private static final String methodVarName = "local_";
	private static final ArrayType bufType = new ArrayType(Type.BYTE, 1);

	/** HipGCC compiler. */
	private final HipGCC hipGCC;

	/** Creates a synchronizer rewriter. */
	public SynchronizerRewriter(HipGCC hipGCC) {
		this.hipGCC = hipGCC;
	}

	/**
	 * Checks if a class should be rewritten by this rewriter.
	 * 
	 * @throws ClassNotFoundException
	 */
	public static boolean isSynchronizerClass(JavaClass cl) throws ClassNotFoundException {
		return !cl.isInterface() && !cl.isAbstract() && cl.instanceOf(ClassRepository.getSynchronizerClass());
	}

	/**
	 * Rewrites a synchronizer class.
	 * 
	 * @param cl
	 *            The synchronizer class
	 * @param NodeInterfaces
	 *            Node interfaces to consider
	 * @return Rewritten class
	 */
	public RewrittenSynchronizerClass process(final JavaClass cla, final ClassGen cg) {
		final String className = cg.getClassName();
		final ConstantPoolGen cpg = cg.getConstantPool();
		hipGCC.verbose("rewriting synchronizer class " + className);

		/* find run() method */
		final Method run = findRun(cg.getMethods());
		if (run == null) {
			hipGCC.error("run() not found in " + className);
		}
		final MethodGen runG = new MethodGen(run, className, cg.getConstantPool());

		/* find reduce methods */
		final ArrayList<ReduceMethod> reduceMethods = locateReduceMethods(cla);
		hipGCC.verbose(tab + "located reduce methods: " + BCELUtils.printList(reduceMethods.iterator()));

		/* find notification methods */
		final ArrayList<NotificationMethod> notificationMethods = locateNotificationMethods(cla);
		hipGCC.verbose(tab + "located notification methods: " + BCELUtils.printList(notificationMethods.iterator()));

		/* find all local variables */
		final VariablesInfo varInfo = new VariablesInfo(cg);

		/* find all blocking and non-blocking calls (notification) */
		final Map<Method, MethodInfo> methodsInfo = new HashMap<Method, MethodInfo>();
		for (Method method : cg.getMethods()) {
			if (method.getCode() != null && !method.isStatic()) {
				final MethodGen mg = (method == run ? runG : new MethodGen(method, className, cpg));
				final ArrayList<MethodVariable> vars = locateMethodVariables(mg, cg);
				final CallLocator locator = new CallLocator(mg, cla, cg, vars, reduceMethods, notificationMethods);
				locator.execute();
				final ArrayList<NotificationCall> nonBlockingCalls = locator.getNonBlockingCalls();
				final ArrayList<BlockingCall> blockingCalls = locator.getBlockingCalls();
				final MethodInfo methodInfo = new MethodInfo(mg, vars, nonBlockingCalls, blockingCalls);
				methodsInfo.put(method, methodInfo);
				hipGCC.verbose(tab + "in method " + method.getName() + " located:");
				hipGCC.verbose(ttab + "all local variables: " + BCELUtils.printList(vars.iterator()));
				hipGCC.verbose(ttab + "blocking calls:" + BCELUtils.printList(blockingCalls.iterator()));
				hipGCC.verbose(ttab + "non-blocking calls:" + BCELUtils.printList(nonBlockingCalls.iterator()));
			}
		}

		/* make sure other methods do not contain blocking calls */
		final MethodInfo runInfo = methodsInfo.get(run);
		final ArrayList<MethodVariable> variables = runInfo.vars;
		final ArrayList<BlockingCall> blockingCalls = runInfo.blockingCalls;
		final ArrayList<NotificationCall> nonBlockingCalls = runInfo.nonBlockingCalls;

		/* find all returns in run() */
		final ArrayList<InstructionHandle> returns = locateReturns(runG, cg);

		//
		// now start changing the class
		//
		final InstructionFactory fc = new InstructionFactory(cg);

		hipGCC.verbose(tab + "rewriting run() method: breaking into " + (blockingCalls.size() + 1) + " parts");

		/* create variables */
		createVariables(cg, variables, blockingCalls, varInfo);

		/* rewrite returns */
		rewriteReturns(runG, cg, fc, returns, blockingCalls.size());

		/* rewrite non-blocking calls */
		rewriteNonBlockingCalls(runG, cg, fc, nonBlockingCalls);

		/* rewrite blocking calls */
		final InstructionHandle[] handles = rewriteBlockingCalls(runG, cg, fc, variables, reduceMethods, blockingCalls,
				varInfo);

		/* create pc table */
		if (handles != null && handles.length > 0) {
			insertPCtableswitch(runG, cg, fc, handles);
		}

		/* create hipg_reduce method */
		final MethodGen hipgReduce = createHipgReduce(cg, fc, reduceMethods);

		/* create hipg_notify method */
		final MethodGen hipgNotify = createHipgNotify(cg, fc, notificationMethods);

		/* rewrite other methods */
		for (Method method : cg.getMethods()) {
			if (method != run && !method.isStatic() && method.getCode() != null) {
				final MethodInfo methodInfo = methodsInfo.get(method);
				if (methodInfo.blockingCalls.size() > 0) {
					hipGCC.error("Blocking calls only in run() but a call to "
							+ blockingCalls.get(0).getCalledMethodName() + " found in " + method.getName());
					return null;
				}
				if (methodInfo.nonBlockingCalls.size() > 0) {
					methodInfo.modified = true;
					rewriteNonBlockingCalls(methodInfo.mg, cg, fc, methodInfo.nonBlockingCalls);
				}
			}
		}

		/* replace old code with the new one */
		runG.setMaxLocals();
		runG.setMaxStack();
		cg.replaceMethod(run, runG.getMethod());

		hipgReduce.setMaxLocals();
		hipgReduce.setMaxStack();
		cg.addMethod(hipgReduce.getMethod());

		hipgNotify.setMaxLocals();
		hipgNotify.setMaxStack();
		cg.addMethod(hipgNotify.getMethod());

		for (Method method : methodsInfo.keySet()) {
			MethodInfo methodInfo = methodsInfo.get(method);
			if (methodInfo.modified) {
				methodInfo.mg.setMaxLocals();
				methodInfo.mg.setMaxStack();
				cg.replaceMethod(method, methodInfo.mg.getMethod());
			}
		}

		return new RewrittenSynchronizerClass(reduceMethods, notificationMethods, variables, blockingCalls,
				nonBlockingCalls, returns, cg);
	}

	private MethodGen createHipgReduce(ClassGen cg, InstructionFactory fc, ArrayList<ReduceMethod> reduceMethods) {

		hipGCC.verbose(ttab + "creating hipg_reduce");

		ConstantPoolGen cpg = cg.getConstantPool();
		InstructionList il = new InstructionList();
		int n = reduceMethods.size();
		InstructionHandle[] targets = new InstructionHandle[n];
		String className = cg.getClassName();

		/* create method */
		MethodGen mg = new MethodGen(Constants.ACC_PUBLIC | Constants.ACC_FINAL, bufType, new Type[] { Type.SHORT,
				bufType }, new String[] { "methodId", "buf", }, "hipg_reduce", cg.getClassName(), il, cpg);

		int freeVarIndex = 1;
		int methodIdIndex = freeVarIndex;
		freeVarIndex += Type.SHORT.getSize();
		int bufIndex = freeVarIndex;
		freeVarIndex += bufType.getSize();
		int positionIndex = freeVarIndex;
		freeVarIndex += Type.INT.getSize();
		int varIndex = freeVarIndex;

		il.append(BCELUtils.println("err", "1", cpg, cg, fc));

		for (int i = 0; i < reduceMethods.size(); i++) {

			ReduceMethod reduceMethod = reduceMethods.get(i);

			Type varType = reduceMethod.getMethod().getReturnType();

			/* store position := 0 */
			// stack: empty
			targets[reduceMethod.getId()] = il.append(new PUSH(cpg, 0));
			il.append(InstructionFactory.createStore(Type.INT, positionIndex));
			il.append(InstructionFactory.createThis());

			/* de-serialize parameter */
			// stack: this
			Serialization.createReadFromBuf(varType, bufIndex, positionIndex, fc, cg, il);
			// stack: this param

			/* execute reduce method */
			// stack: this param
			il.append(fc.createInvoke(className, reduceMethod.getName(), varType, new Type[] { varType },
					Constants.INVOKEVIRTUAL));
			// stack: result
			il.append(InstructionFactory.createStore(varType, varIndex));
			// stack: empty

			/* position = 0 */
			il.append(new PUSH(cpg, 0));
			il.append(InstructionFactory.createStore(Type.INT, positionIndex));

			/* create new array for the result */
			il.append(InstructionFactory.createLoad(varType, varIndex));
			Serialization.getRequiredBufferSizeToStoreType(varType, cg, fc, il);
			il.append(fc.createNewArray(Type.BYTE, (short) 1));
			il.append(InstructionFactory.createStore(bufType, bufIndex));

			/* serialize result */
			// stack: empty
			// il.append(InstructionFactory.createLoad(varType, varIndex));
			Serialization.createWriteToBufFromStack(varType, bufIndex, positionIndex, il, fc, cg);
			// stack: empty

			/* return result */
			il.append(InstructionFactory.createLoad(bufType, bufIndex));
			il.append(InstructionFactory.createReturn(bufType));

		}

		final InstructionList throwIl = BCELUtils.createThrowRuntimeException(fc, cpg, "Unrecognized reduce method ",
				methodIdIndex);
		final InstructionHandle defaultTarget = il.append(throwIl);

		/* switch method id */
		int[] match = new int[n];
		for (int i = 0; i < n; i++) {
			match[i] = i;
		}
		if (n == 0) {
			match = new int[] { 0 };
			targets = new InstructionHandle[] { defaultTarget };
		}
		TABLESWITCH tableswitch = new TABLESWITCH(match, targets, defaultTarget);
		il.insert(tableswitch);
		il.insert(InstructionFactory.createLoad(Type.SHORT, methodIdIndex));

		throwIl.dispose();

		return mg;
	}

	private MethodGen createHipgNotify(ClassGen cg, InstructionFactory fc,
			ArrayList<NotificationMethod> notificationMethods) {

		hipGCC.verbose(ttab + "createing hipg_notify");

		ConstantPoolGen cpg = cg.getConstantPool();
		InstructionList il = new InstructionList();
		int n = notificationMethods.size();
		InstructionHandle[] targets = new InstructionHandle[n];
		String className = cg.getClassName();

		/* create method */
		MethodGen mg = new MethodGen(Constants.ACC_PUBLIC | Constants.ACC_FINAL, Type.VOID, new Type[] { Type.SHORT,
				bufType }, new String[] { "methodId", "buf", }, "hipg_notify", cg.getClassName(), il, cpg);

		int freeVarIndex = 1;
		int methodIdIndex = freeVarIndex;
		freeVarIndex += Type.SHORT.getSize();
		int bufIndex = freeVarIndex;
		freeVarIndex += bufType.getSize();
		int positionIndex = freeVarIndex;
		freeVarIndex += Type.INT.getSize();

		for (int i = 0; i < notificationMethods.size(); i++) {

			NotificationMethod notificationMethod = notificationMethods.get(i);

			/* store position := 0 */
			// stack: empty
			targets[notificationMethod.getId()] = il.append(new PUSH(cpg, 0));
			il.append(InstructionFactory.createStore(Type.INT, positionIndex));
			il.append(InstructionFactory.createThis());
			// stack: this

			/* de-serialize parameters */
			// stack: this
			Type[] types = notificationMethod.getMethod().getArgumentTypes();
			for (int j = 0; j < types.length; j++) {
				Type varType = types[j];
				Serialization.createReadFromBuf(varType, bufIndex, positionIndex, fc, cg, il);
			}
			// stack: this x0 x1 .. xk

			/* execute notification method */
			// stack: this x0 x1 .. xk
			il.append(fc.createInvoke(className, notificationMethod.getName(), Type.VOID, types,
					Constants.INVOKEVIRTUAL));
			// stack: empty
			il.append(InstructionFactory.createReturn(Type.VOID));
		}

		final InstructionList throwIl = BCELUtils.createThrowRuntimeException(fc, cpg, "Unrecognized notify method ",
				methodIdIndex);
		final InstructionHandle defaultTarget = il.append(throwIl);

		/* switch method id */
		int[] match = new int[n];
		for (int i = 0; i < n; i++) {
			match[i] = i;
		}
		if (n == 0) {
			match = new int[] { 0 };
			targets = new InstructionHandle[] { defaultTarget };
		}
		TABLESWITCH tableswitch = new TABLESWITCH(match, targets, defaultTarget);
		il.insert(tableswitch);
		il.insert(InstructionFactory.createLoad(Type.SHORT, methodIdIndex));

		throwIl.dispose();

		return mg;
	}

	private void createVariables(ClassGen cg, ArrayList<MethodVariable> variables,
			ArrayList<BlockingCall> blockingCalls, VariablesInfo varInfo) {

		hipGCC.verbose(ttab + "creating variables");

		ConstantPoolGen cpg = cg.getConstantPool();

		/* create persistent local variables */
		for (MethodVariable var : variables) {
			Type type = var.getType();
			String name = uniqPrefix + methodVarName + var.getPersistentName();
			int num = varInfo.getNewLocalVariable(type);
			cg.addField(new FieldGen(Constants.ACC_PRIVATE, type, name, cpg).getField());
			hipGCC.verbose(ttab + tab + "created variable " + name + " at " + num + " of type " + type);
		}

		/* create stack variables */
		for (int i = 0; i < blockingCalls.size(); i++) {
			BlockingCall bc = blockingCalls.get(i);
			ArrayList<StackElement> stack = bc.getStack();
			for (int j = 0; j < stack.size() - (bc.isBarrier() || bc.isSync() ? 0 : 1); j++) {
				Type type = stack.get(j).getType();
				String name = uniqPrefix + stackVarName + bc.getBlockingCallId() + "_" + j;
				int num = varInfo.getNewStackVariable(type);
				cg.addField(new FieldGen(Constants.ACC_PRIVATE, type, name, cpg).getField());
				hipGCC.verbose(ttab + tab + "created variable " + name + " at " + num + " of type " + type);
			}
		}
	}

	private void rewriteNonBlockingCalls(MethodGen mg, ClassGen cg, InstructionFactory fc,
			ArrayList<NotificationCall> nonBlockingCalls) {

		hipGCC.verbose(ttab + "rewriting non-blocking calls (notifications) for method " + mg.getName());

		int freeVarIndex = mg.getMaxLocals();
		final int bufIndex = freeVarIndex++;
		final int positionIndex = freeVarIndex++;
		for (int i = 0; i < nonBlockingCalls.size(); i++) {
			NotificationCall nc = nonBlockingCalls.get(i);
			hipGCC.verbose(ttab + "rewriting a non-blocking call to " + nc.getCalledMethodName());
			rewriteNonBlockingCall(mg, cg, fc, nc, bufIndex, positionIndex);
		}
	}

	private void rewriteNonBlockingCall(MethodGen mg, ClassGen cg, InstructionFactory fc, NotificationCall nc,
			int bufIndex, int positionIndex) {

		InstructionList il = new InstructionList();
		ConstantPoolGen cpg = cg.getConstantPool();
		NotificationMethod nm = nc.getNotificationMethod();
		InstructionHandle start = null;

		// assume synchronizer (s) an eventual notification parameters (x0 x1 ..
		// xk) are on stack

		/* create byte[] buffer with all parameters */

		// static estimate buffer size (consider only primitive types)
		final Type[] types = nm.getMethod().getArgumentTypes();
		int initParamSize = 0;
		for (int i = 0; i < types.length; i++) {
			Type t = types[i];
			if (Serialization.isPrimitive(t)) {
				initParamSize += Serialization.staticTypeSizeInBytes(t);
			}
		}

		// consider last arguments if it is an object
		boolean doAdd = false;
		if (types.length > 0) {
			Type t = types[types.length - 1];
			if (!Serialization.isPrimitive(t)) {
				doAdd = true;
				Serialization.getRequiredBufferSizeToStoreType(t, cg, fc, il);

				// if (Serialization.isString(t)) {
				// il.append(InstructionFactory.createDup(t.getSize()));
				// Serialization.getStringLength(t, cg, fc, il);
				// Serialization.getNrOfBytesNeededToStoreString(t, il, cg, fc);
				// } else if (Serialization.isArrayOfPrimitiveType(t)) {
				// il.append(InstructionFactory.createDup(t.getSize()));
				// Serialization.getArrayLength(t, cg, fc, il);
				// Serialization.getNrOfBytesNeededToStorePrimitiveTypeArray(t,
				// il, cg, fc);
				// }

			}
		}

		// stack: s x0 x1 .. xk
		il.append(new PUSH(cpg, initParamSize));
		if (doAdd) {
			il.append(new IADD());
		}

		// stack: s x0 x1 .. xk sz
		il.append(fc.createNewArray(Type.BYTE, (short) 1));
		il.append(InstructionFactory.createStore(Type.OBJECT, bufIndex));
		il.append(new PUSH(cpg, 0));
		il.append(InstructionFactory.createStore(Type.INT, positionIndex));
		// stack: s x0 x1 .. xk
		for (int i = 0; i < types.length; i++) {
			final Type t = types[types.length - 1 - i];
			if (i > 0 && !Serialization.isPrimitive(t)) {

				Serialization.getRequiredBufferSizeToStoreType(t, cg, fc, il);

				// if (Serialization.isString(t)) {
				// il.append(InstructionFactory.createDup(t.getSize()));
				// Serialization.getStringLength(t, cg, fc, il);
				// Serialization.getNrOfBytesNeededToStoreString(t, il, cg, fc);
				// } else if (Serialization.isArrayOfPrimitiveType(t)) {
				// il.append(InstructionFactory.createDup(t.getSize()));
				// Serialization.getArrayLength(t, cg, fc, il);
				// Serialization.getNrOfBytesNeededToStorePrimitiveTypeArray(t,
				// il, cg, fc);
				// }

				il.append(InstructionFactory.createLoad(Type.OBJECT, bufIndex));
				il.append(new ARRAYLENGTH());
				il.append(new IADD());
				il.append(fc.createNewArray(Type.BYTE, (short) 1));
				il.append(new DUP());

				// create a new, bigger, buffer and arraycopy
				// load src
				il.append(InstructionFactory.createLoad(Type.OBJECT, bufIndex));
				il.append(new SWAP());
				// load srcPos
				il.append(new PUSH(cpg, 0));
				il.append(new SWAP());
				// load dstPos
				il.append(new PUSH(cpg, 0));
				// load count
				il.append(InstructionFactory.createLoad(Type.OBJECT, bufIndex));
				il.append(new ARRAYLENGTH());
				// invoke arraycopy (src,srcPos,dst,dstPos,count)
				il.append(fc.createInvoke("java.lang.System", "arraycopy", Type.VOID, new Type[] { Type.OBJECT,
						Type.INT, Type.OBJECT, Type.INT, Type.INT }, Constants.INVOKESTATIC));
				il.append(InstructionFactory.createStore(Type.OBJECT, bufIndex));
			}
			Serialization.createWriteToBufFromStack(t, bufIndex, positionIndex, il, fc, cg);
		}
		// stack: s
		// call notification(methodId, buf)
		il.append(new PUSH(cpg, nc.getNotificationMethod().getId()));
		il.append(InstructionFactory.createLoad(bufType, bufIndex));
		il.append(fc.createInvoke(ClassRepository.SynchronizerClassName, "notification", Type.VOID, new Type[] {
				Type.SHORT, bufType }, Constants.INVOKEVIRTUAL));
		hipGCC.verbose(ttab + ttab + "inserted notification invocation to " + nc.getNotificationMethod().getId() + ":"
				+ nc.getNotificationMethod().getName());
		if (hipGCC.debugCode()) {
			BCELUtils.appendFlag(1000, il, cpg);
		}

		/* finalize */
		mg.getInstructionList().insert(nc.getHandle(), il);
		il.dispose();
		BCELUtils.deleteInstruction(il, nc.getHandle(), start);
		hipGCC.verbose(ttab + ttab + "deleted the original call");
	}

	private InstructionHandle[] rewriteBlockingCalls(MethodGen mg, ClassGen cg, InstructionFactory fc,
			ArrayList<MethodVariable> variables, ArrayList<ReduceMethod> reduceMethods,
			ArrayList<BlockingCall> blockingCalls, VariablesInfo varInfo) {
		hipGCC.verbose(ttab + "rewriting blocking calls");
		final int n = blockingCalls.size();
		final InstructionHandle[] restores = new InstructionHandle[n];
		final int maxLocals = mg.getMaxLocals();
		final int bufIndex = maxLocals + 1;
		final int positionIndex = bufIndex + bufType.getSize();
		final int dIndex = positionIndex + Type.INT.getSize();
		final int paramIndex = dIndex + Type.DOUBLE.getSize();
		for (int pc = 0; pc < n; pc++) {
			final BlockingCall bc = blockingCalls.get(pc);
			final int nextPc = pc + 1;
			hipGCC.verbose(ttab + "rewriting a blocking call pc=" + pc + " to " + bc.getCalledMethodName()
					+ " with active variables: " + BCELUtils.printList(bc.getActiveVariables().iterator()));
			restores[pc] = createCheckpoint(bc, mg, cg, fc, pc, nextPc, variables, varInfo, bufIndex, positionIndex,
					dIndex, paramIndex);
		}
		return restores;
	}

	private InstructionHandle createCheckpoint(BlockingCall bc, MethodGen mg, ClassGen cg, InstructionFactory fc,
			int pc, int nextPc, ArrayList<MethodVariable> variables, VariablesInfo varInfo, int bufIndex,
			int positionIndex, int dIndex, int varIndex) {

		InstructionList il = new InstructionList();
		String className = cg.getClassName();
		ConstantPoolGen cpg = cg.getConstantPool();
		ReduceMethod rm = bc.getReduceMethod();
		Type rmType = (rm == null ? null : rm.getMethod().getReturnType());
		ArrayList<StackElement> stack = bc.getStack();
		InstructionHandle start = null;

		// assume synchronizer an eventual reduce parameters are on stack

		if (hipGCC.debugCode()) {
			start = BCELUtils.appendFlag(1100 + pc, il, cpg);
		}

		/* save next state */
		// stack: s [x]
		if (hipGCC.debugCode()) {
			BCELUtils.appendFlag(1105, il, cpg);
		}
		start = il.append(InstructionFactory.createThis());
		il.append(new PUSH(cpg, nextPc));
		il.append(fc.createFieldAccess(ClassRepository.SynchronizerClassName, pcVarName, Type.INT, Constants.PUTFIELD));
		hipGCC.verbose(ttab + ttab + "saved next state to pc = " + nextPc);
		// stack: s [x]

		/* execute the call */
		if (bc.isBarrier()) {
			// stack: s
			if (hipGCC.debugCode())
				BCELUtils.appendFlag(1110, il, cpg);
			il.append(bc.getHandle().getInstruction().copy());
			hipGCC.verbose(ttab + ttab + "inserted barrier invocation");
			// stack: empty
		} else if (bc.isSync()) {
			// stack: s
			if (hipGCC.debugCode())
				BCELUtils.appendFlag(1120, il, cpg);
			il.append(bc.getHandle().getInstruction().copy());
			hipGCC.verbose(ttab + ttab + "inserted sync invocation");
			// stack: empty
		} else /* isReduce or isNotification of isBarRed */{
			// stack: s x
			if (hipGCC.debugCode()) {
				BCELUtils.appendFlag(1130, il, cpg);
			}
			final Type varType = stack.remove(stack.size() - 1).getType();
			Serialization.getRequiredBufferSizeToStoreType(varType, cg, fc, il);
			if (hipGCC.debugCode()) {
				BCELUtils.appendFlag(1170, il, cpg);
			}
			// stack: s x size
			// create buf and serialize params to it
			il.append(fc.createNewArray(Type.BYTE, (short) 1));
			il.append(InstructionFactory.createStore(Type.OBJECT, bufIndex));
			il.append(new PUSH(cpg, 0));
			il.append(InstructionFactory.createStore(Type.INT, positionIndex));
			// stack: s x
			if (hipGCC.debugCode()) {
				BCELUtils.appendFlag(1180, il, cpg);
				BCELUtils.appendFlag(10000 + Serialization.createTypeId(varType), il, cpg);
			}
			Serialization.createWriteToBufFromStack(varType, bufIndex, positionIndex, il, fc, cg);
			if (hipGCC.debugCode()) {
				BCELUtils.appendFlag(1190, il, cpg);
			}
			// stack: s
			// call reduce(methodId, buf)
			il.append(new PUSH(cpg, rm.getId()));
			il.append(InstructionFactory.createLoad(bufType, bufIndex));
			il.append(fc.createInvoke(ClassRepository.SynchronizerClassName, rm.getSynchronizerReduceMethodName(),
					Type.VOID, new Type[] { Type.SHORT, bufType }, Constants.INVOKEVIRTUAL));
			hipGCC.verbose(ttab + ttab + "inserted reduce invocation " + "with parameter of type " + varType);
			if (hipGCC.debugCode())
				BCELUtils.appendFlag(1200, il, cpg);
		}

		/* save local variables to persistent variables */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(1210, il, cpg);
		hipGCC.verbose(ttab + ttab + "saving " + bc.getActiveVariables().size() + " local variables");
		for (MethodVariable var : bc.getActiveVariables()) {
			il.append(InstructionFactory.createThis());
			il.append(InstructionFactory.createLoad(var.getType(), var.getIndex()));
			String name = uniqPrefix + methodVarName + var.getPersistentName();
			il.append(fc.createFieldAccess(className, name, var.getType(), Constants.PUTFIELD));
			hipGCC.verbose(ttab + ttab + tab + "saved variable " + var.getName() + " to " + name);
		}

		/* save stack to persistent variables */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(1220, il, cpg);
		hipGCC.verbose(ttab + ttab + "saving " + stack.size() + " stack elements");
		for (int i = stack.size() - 1; i >= 0; i--) {
			Type t = stack.get(i).getType();
			il.append(InstructionFactory.createThis());
			il.append(new SWAP());
			String name = uniqPrefix + stackVarName + bc.getBlockingCallId() + "_" + i;
			il.append(fc.createFieldAccess(className, name, t, Constants.PUTFIELD));
			hipGCC.verbose(ttab + ttab + tab + "saved stack element to local variable " + name + " of type " + t);
		}

		/* insert return */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(1230, il, cpg);
		il.append(InstructionFactory.createReturn(Type.VOID));
		hipGCC.verbose(ttab + ttab + "inserted return");

		/* store where to jump to to restore */
		final InstructionHandle restore = il.append(new NOP());

		/* restore local variables */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(1240, il, cpg);
		for (MethodVariable var : bc.getActiveVariables()) {
			il.append(InstructionFactory.createThis());
			String name = uniqPrefix + methodVarName + var.getPersistentName();
			il.append(fc.createFieldAccess(className, name, var.getType(), Constants.GETFIELD));
			il.append(InstructionFactory.createStore(var.getType(), var.getIndex()));
			hipGCC.verbose(ttab + ttab + "restored local variable " + var.getName() + " from " + name);
		}

		/* restore stack variables */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(1250, il, cpg);
		for (int i = 0; i < stack.size(); i++) {
			Type t = stack.get(i).getType();
			il.append(InstructionFactory.createThis());
			String name = uniqPrefix + stackVarName + bc.getBlockingCallId() + "_" + i;
			il.append(fc.createFieldAccess(className, name, t, Constants.GETFIELD));
			hipGCC.verbose(ttab + ttab + "restored stack variable from " + name + " of type " + t);
		}

		/* push reduce result */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(1260, il, cpg);
		if (rm != null) {
			il.append(InstructionFactory.createThis());
			il.append(fc.createInvoke(ClassRepository.SynchronizerClassName, "result", bufType, Type.NO_ARGS,
					Constants.INVOKEVIRTUAL));
			il.append(InstructionFactory.createStore(bufType, bufIndex));
			il.append(new PUSH(cpg, 0));
			il.append(InstructionFactory.createStore(Type.INT, positionIndex));
			if (hipGCC.debugCode())
				BCELUtils.appendFlag(1261, il, cpg);
			Serialization.createReadFromBuf(rmType, bufIndex, positionIndex, fc, cg, il);
			if (hipGCC.debugCode())
				BCELUtils.appendFlag(1262, il, cpg);
			hipGCC.verbose(ttab + ttab + "push on stack value returned by the reduce call");
		}
		if (hipGCC.debugCode()) {
			BCELUtils.appendFlag(1270, il, cpg);
			BCELUtils.appendFlag(3000 + pc, il, cpg);
		}

		/* finalize */
		mg.getInstructionList().insert(bc.getHandle(), il);
		il.dispose();
		BCELUtils.deleteInstruction(il, bc.getHandle(), start);
		hipGCC.verbose(ttab + ttab + "deleted the original call");

		return restore;
	}

	private void rewriteReturns(final MethodGen mg, final ClassGen cg, final InstructionFactory fc,
			final ArrayList<InstructionHandle> returns, final int blockingCallsCount) {

		hipGCC.verbose(ttab + "rewriting " + returns.size() + " returns from blockingCalls");

		final InstructionList il = mg.getInstructionList();
		final ConstantPoolGen cpg = cg.getConstantPool();

		for (InstructionHandle ret : returns) {
			InstructionHandle newRet = il.insert(ret, InstructionFactory.createThis());
			il.insert(ret, new PUSH(cpg, blockingCallsCount + 1));
			il.insert(ret, fc.createFieldAccess(ClassRepository.SynchronizerClassName, pcVarName, Type.INT,
					Constants.PUTFIELD));
			InstructionTargeter[] targeters = ret.getTargeters();
			if (targeters != null) {
				for (InstructionTargeter targeter : targeters) {
					targeter.updateTarget(ret, newRet);
				}
			}
		}
	}

	private ArrayList<MethodVariable> locateActiveVariables(final MethodGen m, final InstructionHandle ih,
			ArrayList<MethodVariable> variables) {
		final ArrayList<MethodVariable> activeVariables = new ArrayList<MethodVariable>();
		for (MethodVariable v : variables) {
			if (v.hasInScope(ih)) {
				activeVariables.add(v);
			}
		}
		return activeVariables;
	}

	private ArrayList<MethodVariable> locateMethodVariables(final MethodGen m, final ClassGen cg) {
		final ArrayList<MethodVariable> variables = new ArrayList<MethodVariable>();
		final ConstantPoolGen cpg = cg.getConstantPool();
		final LocalVariableTable lvt = m.getLocalVariableTable(cpg);
		if (lvt != null) {
			final LocalVariable[] lvs = m.getLocalVariableTable(cpg).getLocalVariableTable();
			for (int i = 0; i < lvs.length; i++) {
				final LocalVariable v = lvs[i];
				if (v != null) {
					if (!v.getName().equals("this")) {
						variables.add(new MethodVariable(v));
					}
				}
			}
			return variables;
		}

		hipGCC.warning("! for method *" + m.getName() + "* of signature " + m.getSignature() + " in class "
				+ cg.getClassName() + " no local variable table is available: "
				+ "cannot extract local variables information, rewriting MIGHT " + "fail if local variables exist");

		return variables;
	}

	private ArrayList<InstructionHandle> locateReturns(final MethodGen mg, final ClassGen cg) {
		final ArrayList<InstructionHandle> returns = new ArrayList<InstructionHandle>();
		final InstructionList il = mg.getInstructionList();
		Iterator<?> it = il.iterator();
		while (it.hasNext()) {
			final InstructionHandle ih = (InstructionHandle) it.next();
			final Instruction i = ih.getInstruction();
			if (i instanceof ReturnInstruction) {
				returns.add(ih);
			}
		}
		return returns;
	}

	private class CallLocator extends Simulator {

		private final ClassGen cg;
		private final MethodGen mg;

		private final ArrayList<NotificationMethod> notificationMethods;
		private final ArrayList<NotificationCall> nonBlockingCalls;
		private final ArrayList<MethodVariable> variables;
		private final ArrayList<ReduceMethod> reduceMethods;
		private final ArrayList<BlockingCall> blockingCalls;

		public CallLocator(MethodGen mg, JavaClass cl, ClassGen cg, ArrayList<MethodVariable> variables,
				ArrayList<ReduceMethod> reduceMethods, ArrayList<NotificationMethod> notificationMethods) {
			super(mg, cg);
			this.mg = mg;
			this.cg = cg;
			this.variables = variables;
			this.reduceMethods = reduceMethods;
			this.blockingCalls = new ArrayList<BlockingCall>();
			this.notificationMethods = notificationMethods;
			this.nonBlockingCalls = new ArrayList<NotificationCall>();
		}

		public void processBefore(InstructionHandle ih) {
			super.processAfter(ih);
			Instruction i = ih.getInstruction();
			if (i instanceof InvokeInstruction) {
				InvokeInstruction invoke = (InvokeInstruction) i;
				String methodName = getMethodName(invoke);
				ReferenceType classTypeRef = getReferenceType(invoke);
				Type[] args = getArgumentTypes(invoke);
				Type returnType = getReturnType(invoke);
				if (!(classTypeRef instanceof ObjectType))
					return;
				ObjectType classType = (ObjectType) classTypeRef;
				String className = classType.getClassName();

				// eliminate "common" calls
				if (className.equals(StringBuilder.class.getName()) || className.equals(StringBuffer.class.getName())) {
					if (methodName.equals(Constants.CONSTRUCTOR_NAME) || methodName.equals("append")
							|| methodName.equals("toString"))
						return;
				} else if (className.equals(PrintStream.class.getName())) {
					if (methodName.equals("print") || methodName.equals("println"))
						return;
				} else if (className.equals(Runtime.class.getName())) {
					if (methodName.equals("getCommunication"))
						return;
				} else if ((methodName.equals("toString") && (args == null || args.length == 0) && returnType
						.equals(Type.STRING))) {
					return;
				}

				NotificationMethod nm = null;
				try {
					nm = NotificationCall.checkNonBlockingCall(this, invoke, notificationMethods);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					System.exit(1);
				}

				if (nm != null) {
					nonBlockingCalls.add(new NotificationCall(cg, ih, invoke, copyStack(), nm));
				}
			}
		}

		@Override
		public void processAfter(InstructionHandle ih) {
			super.processAfter(ih);
			Instruction i = ih.getInstruction();
			if (i instanceof InvokeInstruction) {
				InvokeInstruction invoke = (InvokeInstruction) i;
				String methodName = getMethodName(invoke);
				ReferenceType classTypeRef = getReferenceType(invoke);
				Type[] args = getArgumentTypes(invoke);
				Type returnType = getReturnType(invoke);
				if (!(classTypeRef instanceof ObjectType))
					return;
				ObjectType classType = (ObjectType) classTypeRef;
				String className = classType.getClassName();

				// eliminate "common" calls
				if (className.equals(StringBuilder.class.getName()) || className.equals(StringBuffer.class.getName())) {
					if (methodName.equals(Constants.CONSTRUCTOR_NAME) || methodName.equals("append")
							|| methodName.equals("toString"))
						return;
				} else if (className.equals(PrintStream.class.getName())) {
					if (methodName.equals("print") || methodName.equals("println"))
						return;
				} else if (className.equals(Runtime.class.getName())) {
					if (methodName.equals("getCommunication"))
						return;
				} else if ((methodName.equals("toString") && (args == null || args.length == 0) && returnType
						.equals(Type.STRING))) {
					return;
				}

				IntValuePair<ReduceMethod> bc = BlockingCall.checkBlockingCall(this, invoke, reduceMethods);

				if (bc != null) {
					ArrayList<MethodVariable> activeVars = null;
					if (variables != null) {
						activeVars = locateActiveVariables(mg, ih, variables);
					}
					blockingCalls.add(new BlockingCall(cg, ih, invoke, copyStack(), activeVars, bc.getFirst(), bc
							.getSecond()));
				}
			}
		}

		public ArrayList<BlockingCall> getBlockingCalls() {
			return blockingCalls;
		}

		public ArrayList<NotificationCall> getNonBlockingCalls() {
			return nonBlockingCalls;
		}
	}

	private void insertPCtableswitch(MethodGen run, ClassGen cg, InstructionFactory fc, InstructionHandle[] handles) {

		hipGCC.verbose(tab + tab + "inserting pc tableswitch");

		final ConstantPoolGen cpg = cg.getConstantPool();
		final InstructionList il = run.getInstructionList();

		il.insert(fc.createFieldAccess(ClassRepository.SynchronizerClassName, pcmaxVarName, Type.INT,
				Constants.PUTFIELD));
		il.insert(new PUSH(cpg, handles.length + 1));
		InstructionHandle first = il.insert(InstructionFactory.createThis());

		/* create handler for unrecognized pc */
		final InstructionList throwIl = BCELUtils.createThrowRuntimeException(fc, cpg,
				"Unrecognized pc in " + cg.getClassName());
		final InstructionHandle unknownPc = (InstructionHandle) throwIl.iterator().next();
		il.insert(throwIl);

		/* create tableswitch */
		final int n = handles.length + 1;
		final int[] match = new int[n];
		for (int i = 0; i < match.length; i++)
			match[i] = i;
		final InstructionHandle[] targets = new InstructionHandle[n];
		targets[0] = first;
		for (int i = 0; i < handles.length; i++)
			targets[i + 1] = handles[i];
		InstructionHandle ih = (InstructionHandle) il.iterator().next();
		il.insert(ih, InstructionFactory.createThis());
		il.insert(ih,
				fc.createFieldAccess(ClassRepository.SynchronizerClassName, pcVarName, Type.INT, Constants.GETFIELD));
		il.insert(ih, new TABLESWITCH(match, targets, unknownPc));

		/* clean up */
		throwIl.dispose();
	}

	private Method findRun(Method[] methods) {
		for (Method method : methods) {
			if (method.getName().equals("run") && method.getReturnType().equals(Type.VOID)
					&& (method.getArgumentTypes() == null || method.getArgumentTypes().length == 0)) {
				return method;
			}
		}
		return null;
	}

	private ArrayList<ReduceMethod> locateReduceMethods(JavaClass cl) {
		final String className = cl.getClassName();
		final ArrayList<ReduceMethod> reduceMethods = new ArrayList<ReduceMethod>();
		final ArrayList<JavaClass> superClasses = new ArrayList<JavaClass>();
		try {
			for (JavaClass clazz = cl; clazz != null; clazz = clazz.getSuperClass()) {
				superClasses.add(clazz);
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Could not locate notification methods: " + e.getMessage(), e);
		}
		for (JavaClass superClass : superClasses) {
			final Method[] methods = superClass.getMethods();
			for (Method method : methods) {
				final String methodName = method.getName();
				if (!methodName.equals(Constants.CONSTRUCTOR_NAME)
						&& !methodName.equals(Constants.STATIC_INITIALIZER_NAME)) {
					final AnnotationEntry[] annotations = method.getAnnotationEntries();
					if (annotations != null && annotations.length > 0) {
						String reduceAnnotationClassName = null;
						for (AnnotationEntry annotation : annotations) {
							final String annotationType = annotation.getAnnotationType();
							final String annotationClassName = annotationType.substring(1, annotationType.length() - 1)
									.replace("/", ".");
							if (annotationClassName.equals(Reduce.class.getName())
									|| annotationClassName.equals(BarrierAndReduce.class.getName())) {
								if (reduceAnnotationClassName != null) {
									hipGCC.error("method " + methodName + " with signature " + method.getSignature()
											+ " in class " + className + ": multiple reduce annotations, "
											+ "must choose one");
									return null;
								}
								reduceAnnotationClassName = annotationClassName;
							}
						}
						if (reduceAnnotationClassName != null) {
							final String errMsg = ReduceMethod.checkReduceMethod(method);
							if (errMsg != null) {
								hipGCC.error(errMsg);
							} else {
								int methodId = reduceMethods.size();
								reduceMethods.add(new ReduceMethod(method, methodId, reduceAnnotationClassName));
							}
						}
					}
				}
			}
		}
		return reduceMethods;
	}

	private ArrayList<NotificationMethod> locateNotificationMethods(JavaClass cl) {
		final String className = cl.getClassName();
		final ArrayList<NotificationMethod> notifMethods = new ArrayList<NotificationMethod>();
		final ArrayList<JavaClass> superClasses = new ArrayList<JavaClass>();
		try {
			for (JavaClass clazz = cl; clazz != null; clazz = clazz.getSuperClass()) {
				superClasses.add(clazz);
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Could not locate notification methods: " + e.getMessage(), e);
		}
		for (JavaClass superClass : superClasses) {
			final Method[] methods = superClass.getMethods();
			for (Method method : methods) {
				final String methodName = method.getName();
				if (!methodName.equals(Constants.CONSTRUCTOR_NAME)
						&& !methodName.equals(Constants.STATIC_INITIALIZER_NAME)) {
					final AnnotationEntry[] annotations = method.getAnnotationEntries();
					if (annotations != null && annotations.length > 0) {
						String notificationAnnotationClassName = null;
						for (AnnotationEntry annotation : annotations) {
							final String annotationType = annotation.getAnnotationType();
							final String annotationClassName = annotationType.substring(1, annotationType.length() - 1)
									.replace("/", ".");
							if (annotationClassName.equals(Notification.class.getName())) {
								if (notificationAnnotationClassName != null) {
									hipGCC.error("method " + methodName + " with signature " + method.getSignature()
											+ " in class " + className + ": multiple notification annotations, "
											+ "must choose one");
									return null;
								}
								notificationAnnotationClassName = annotationClassName;
							}
						}
						if (notificationAnnotationClassName != null) {
							String errMsg = NotificationMethod.checkNotificationMethod(method);
							if (errMsg != null) {
								hipGCC.error(errMsg);
							} else {
								final int methodId = notifMethods.size();
								notifMethods.add(new NotificationMethod(method, methodId));
							}
						}
					}
				}
			}
		}
		return notifMethods;
	}

	private final class VariablesInfo {
		final int origVariablesCount;
		int createdLocalVariablesCount = 0;
		int createdStackVariablesCount = 0;

		public VariablesInfo(ClassGen cg) {
			origVariablesCount = 1 + cg.getFields().length;
		}

		public int getCount() {
			return origVariablesCount + createdLocalVariablesCount + createdStackVariablesCount;
		}

		public int getNewLocalVariable(Type t) {
			int num = getCount();
			createdLocalVariablesCount += t.getSize();
			return num;
		}

		public int getNewStackVariable(Type t) {
			int num = getCount();
			createdStackVariablesCount += t.getSize();
			return num;
		}

	}

	private final class MethodInfo {

		boolean modified = false;
		final MethodGen mg;
		final ArrayList<MethodVariable> vars;
		final ArrayList<NotificationCall> nonBlockingCalls;
		final ArrayList<BlockingCall> blockingCalls;

		public MethodInfo(MethodGen mg, ArrayList<MethodVariable> vars, ArrayList<NotificationCall> nonBlockingCalls,
				ArrayList<BlockingCall> blockingCalls) {
			this.mg = mg;
			this.vars = vars;
			this.nonBlockingCalls = nonBlockingCalls;
			this.blockingCalls = blockingCalls;
		}

	}
}
