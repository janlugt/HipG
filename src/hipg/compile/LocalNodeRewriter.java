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

import hipg.utils.BCELUtils;

import java.util.ArrayList;
import java.util.Map;

import myutils.IOUtils;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.IADD;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.NOP;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.TABLESWITCH;
import org.apache.bcel.generic.Type;

public final class LocalNodeRewriter {

	private final static String tab = "  ";
	private final static ArrayType bufType = new ArrayType(Type.BYTE, 1);

	/** The compiler. */
	private final HipGCC hipGCC;

	private final Map<String, JavaClass> NodeInterfaces;
	private final Map<JavaClass, ArrayList<Method>> NodeMethods;

	/** Creates a new local node rewriter. */
	public LocalNodeRewriter(HipGCC hipGCC, Map<String, JavaClass> NodeInterfaces,
			Map<JavaClass, ArrayList<Method>> NodeMethods) {
		this.hipGCC = hipGCC;
		this.NodeInterfaces = NodeInterfaces;
		this.NodeMethods = NodeMethods;
	}

	/**
	 * Checks if a given class extends the @{hipg.Node} interface.
	 * 
	 * @param True
	 *            if the class is a Node interface.
	 * @throws ClassNotFoundException
	 */
	public static boolean isNodeInterface(JavaClass cl) throws ClassNotFoundException {
		return cl.isInterface() && cl.implementationOf(ClassRepository.getNodeInterface());
	}

	/**
	 * Checks if a class should be rewritten by this rewriter.
	 * 
	 * @throws ClassNotFoundException
	 */
	public static boolean isLocalNodeClass(JavaClass cl) throws ClassNotFoundException {
		return !cl.isInterface() && cl.instanceOf(ClassRepository.getLocalNodeClass());
	}

	/**
	 * Rewrites a local node class.
	 * 
	 * @param cl
	 *            The local node class
	 * @param NodeInterfaces
	 *            Node interfaces to consider
	 * @return Rewritten class
	 * @throws ClassNotFoundException
	 */
	public void process(JavaClass cl, ClassGen cg) throws ClassNotFoundException {
		final String className = cg.getClassName();
		hipGCC.verbose("rewriting local node class " + className);
		final InstructionFactory fc = new InstructionFactory(cg);

		/*
		 * determine methods from the implemented node interfaces
		 */
		final ArrayList<Method> MyNodeMethods = NodeRewriter.determineMethodsOrder(cl, NodeInterfaces, NodeMethods);
		hipGCC.verbose(tab + "possible remote methods: " + BCELUtils.printList(MyNodeMethods.iterator()));

		/*
		 * create hipg_parameters method
		 */
		final MethodGen hipgParameters = (MyNodeMethods.size() == 0 ? null
				: createHipgParameters(cg, fc, MyNodeMethods));

		/*
		 * create hipg_execute method (with parameters as array)
		 */
		final MethodGen hipgExecuteArr = (MyNodeMethods.size() == 0 ? null
				: createHipgExecuteArr(cg, fc, MyNodeMethods));

		/*
		 * create hipg_execute method (with parameters as a queue)
		 */
		final MethodGen hipgExecuteQue = (MyNodeMethods.size() == 0 ? null
				: createHipgExecuteQue(cg, fc, MyNodeMethods));

		/*
		 * finalize
		 */
		if (hipgParameters != null) {
			hipgParameters.setMaxLocals();
			hipgParameters.setMaxStack();
			cg.addMethod(hipgParameters.getMethod());
		}
		if (hipgExecuteArr != null) {
			hipgExecuteArr.setMaxLocals();
			hipgExecuteArr.setMaxStack();
			cg.addMethod(hipgExecuteArr.getMethod());
		}
		if (hipgExecuteQue != null) {
			hipgExecuteQue.setMaxLocals();
			hipgExecuteQue.setMaxStack();
			cg.addMethod(hipgExecuteQue.getMethod());
		}
	}

	/**
	 * Creates and adds a method (int)hipg_parameters(short methodId) to the
	 * class. This method returns a number of parameters used by a method that
	 * can be executed remotely.
	 * 
	 * @param cg
	 *            Class to add a method to
	 * @param nodeMethods
	 *            Methods that can be executed remotely
	 */
	private MethodGen createHipgParameters(ClassGen cg, InstructionFactory fc, ArrayList<Method> NodeMethods) {

		hipGCC.verbose(tab + "creating hipg_parameters for " + NodeMethods.size() + " methods");

		final ConstantPoolGen cpg = cg.getConstantPool();
		final InstructionList il = new InstructionList();

		/*
		 * create method
		 */
		final MethodGen mg = new MethodGen(Constants.ACC_PUBLIC, Type.INT,
				new Type[] { Type.SHORT, bufType, Type.INT }, new String[] { "methodId", "buf", "offset" },
				"hipg_parameters", cg.getClassName(), il, cpg);

		/*
		 * create cases (returns number of parameters for a method)
		 */
		final int[] matches = new int[NodeMethods.size()];
		final InstructionHandle[] targets = new InstructionHandle[NodeMethods.size()];
		final int methodIdIndex = 1;
		final int bufIndex = 2;
		final int offsetIndex = 3;

		for (int i = 0; i < NodeMethods.size(); i++) {
			final Method m = NodeMethods.get(i);
			final Type[] types = m.getArgumentTypes();
			matches[i] = i;
			final InstructionHandle first;

			/*
			 * compute parameter's size
			 */

			if (types == null || types.length <= 1) {
				first = il.append(new PUSH(cpg, 0));
			} else {
				first = il.append(new PUSH(cpg, 0));
				for (int j = types.length - 1; j >= 1; j--) {
					final Type t = types[j];
					if (Serialization.isPrimitive(t)) {
						il.append(new PUSH(cpg, Serialization.staticTypeSizeInBytes(t)));
						il.append(new IADD());
					} else if (Serialization.isString(t) || Serialization.isArrayOfPrimitiveType(t)
							|| Serialization.implementsSerializable(t)) {
						
						// stack = empty; 
						// Serialization.readLength(buf, offset)
						il.append(InstructionFactory.createLoad(bufType, bufIndex));
						il.append(InstructionFactory.createLoad(Type.INT, offsetIndex));
						il.append(fc.createInvoke(ClassRepository.IOUtilsClassName, "readLength", Type.INT,
								new Type[] { bufType, Type.INT }, Constants.INVOKESTATIC));
						
						// stack = len
						// offset += LENGTH_LENGTH
						il.append(InstructionFactory.createLoad(Type.INT, offsetIndex));
						il.append(new PUSH(cpg, IOUtils.LENGTH_BYTES));
						il.append(new IADD());
						il.append(InstructionFactory.createStore(Type.INT, offsetIndex));
						
						// stack: len
						// if length == NULL_ARRAY_LENGTH
						il.append(new DUP());
						il.append(new PUSH(cpg, -1));
						final BranchInstruction ifnonull = InstructionFactory.createBranchInstruction(
								Constants.IF_ICMPNE, null);
						il.append(ifnonull);
						
						// stack: len
						// { length == NULL_ARRAY_LENGTH } pop, done
						il.append(new POP());
						final BranchInstruction gotodone = InstructionFactory.createBranchInstruction(Constants.GOTO,
								null);
						il.append(gotodone);
						
						// stack: len
						// { length != NULL_ARRAY_LENGTH } offset += getLength()
						final InstructionHandle getLen = il.append(new NOP());
						if (Serialization.isString(t)) {
							Serialization.getNrOfBytesNeededToStoreString(t, il, cg, fc);
						} else if (Serialization.isArrayOfPrimitiveType(t)) {
							Serialization.getNrOfBytesNeededToStorePrimitiveTypeArray(t, il, cg, fc);
						} else {
							Serialization.getNrOfBytesNeededToStoreSerializable(t, il, cg, fc);
						}
						
						// stack: bytes
						il.append(new DUP());
						il.append(InstructionFactory.createLoad(Type.INT, offsetIndex));
						il.append(new IADD());
						il.append(InstructionFactory.createStore(Type.INT, offsetIndex));
						
						// stack: bytes
						il.append(new IADD());
						// stack: empty
						
						// done:
						InstructionHandle done = il.append(new NOP());
						ifnonull.setTarget(getLen);
						gotodone.setTarget(done);
					} else {
						throw new RuntimeException(t + " not serializable");
					}
				}
			}
			il.append(InstructionFactory.createReturn(Type.INT));
			targets[i] = first;
		}

		/*
		 * create tableswitch
		 */
		if (NodeMethods.size() > 1) {
			final InstructionList throwIl = BCELUtils.createThrowRuntimeException(fc, cpg, "Unrecognized method ",
					methodIdIndex);
			final InstructionHandle defaultTarget = il.append(throwIl);
			final TABLESWITCH tableswitch = new TABLESWITCH(matches, targets, defaultTarget);
			il.insert(tableswitch);
			il.insert(InstructionFactory.createLoad(Type.SHORT, methodIdIndex));

			throwIl.dispose();
		}

		return mg;
	}

	/**
	 * Creates and adds a method (int)hipg_execute(short
	 * methodId,synchronizer,parameters array,offset) to the given class. This
	 * method executes a remote method and returns number of parameters
	 * consumed.
	 * 
	 * @param cg
	 *            Class to add a method to
	 * @param nodeMethods
	 *            Methods that can be executed remotely
	 */
	private MethodGen createHipgExecuteArr(ClassGen cg, InstructionFactory fc, ArrayList<Method> NodeMethods) {
		hipGCC.verbose(tab + "creating hipg_execute(array)");

		final ConstantPoolGen cpg = cg.getConstantPool();
		final InstructionList il = new InstructionList();

		/*
		 * create method
		 */
		final MethodGen mg = new MethodGen(Constants.ACC_PUBLIC, Type.INT, new Type[] { Type.SHORT,
				ClassRepository.SynchronizerInterfaceType, bufType, Type.INT }, new String[] { "methodId",
				"synchronizer", "buf", "offset" }, "hipg_execute", cg.getClassName(), il, cpg);

		/*
		 * create cases (returns number of parameters for a method)
		 */
		final int[] matches = new int[NodeMethods.size()];
		final InstructionHandle[] targets = new InstructionHandle[NodeMethods.size()];
		final int methodIdIndex = 1;
		final int synchronizerIndex = 2;
		final int bufIndex = 3;
		final int positionIndex = 4;

		for (int i = 0; i < NodeMethods.size(); i++) {
			final Method m = NodeMethods.get(i);
			final Type[] types = m.getArgumentTypes();

			hipGCC.verbose(tab + tab + "rewriting method " + i + " of name " + m.getName() + " and signature "
					+ m.getSignature());

			matches[i] = i;
			targets[i] = il.append(InstructionFactory.createThis());
			il.append(InstructionFactory.createLoad(ClassRepository.SynchronizerType, synchronizerIndex));
			il.append(fc.createCheckCast((ReferenceType) types[0]));
			for (int l = 1; l < types.length; l++) {
				Serialization.createReadFromBuf(types[l], bufIndex, positionIndex, fc, cg, il);
			}
			// invoke the method
			il.append(fc.createInvoke(cg.getClassName(), m.getName(), m.getReturnType(), m.getArgumentTypes(),
					Constants.INVOKEVIRTUAL));
			// return new position
			il.append(InstructionFactory.createLoad(Type.INT, positionIndex));
			il.append(InstructionFactory.createReturn(Type.INT));
		}

		/*
		 * create tableswitch
		 */
		if (NodeMethods.size() > 1) {
			final InstructionList throwIl = BCELUtils.createThrowRuntimeException(fc, cpg, "Unrecognized method ",
					methodIdIndex);
			final InstructionHandle defaultTarget = il.append(throwIl);
			final TABLESWITCH tableswitch = new TABLESWITCH(matches, targets, defaultTarget);
			il.insert(tableswitch);
			il.insert(InstructionFactory.createLoad(Type.SHORT, methodIdIndex));

			throwIl.dispose();
		}

		return mg;
	}

	/**
	 * Creates and adds a method (int)hipg_execute(short
	 * methodId,synchronizer,parameters queue) to the given class. This method
	 * executes a remote method and consumes parameters from the queue.
	 * 
	 * @param cg
	 *            Class to add a method to
	 * @param nodeMethods
	 *            Methods that can be executed remotely
	 */
	private MethodGen createHipgExecuteQue(ClassGen cg, InstructionFactory fc, ArrayList<Method> NodeMethods) {

		hipGCC.verbose(tab + "creating hipg_execute(queue)");

		final ConstantPoolGen cpg = cg.getConstantPool();
		final InstructionList il = new InstructionList();

		/*
		 * create method
		 */
		final MethodGen mg = new MethodGen(Constants.ACC_PUBLIC, Type.VOID, new Type[] { Type.SHORT,
				ClassRepository.SynchronizerInterfaceType, ClassRepository.BigQueueType }, new String[] { "methodId",
				"synchronizer", "buf" }, "hipg_execute", cg.getClassName(), il, cpg);

		/*
		 * create cases (returns number of parameters for a method)
		 */
		final InstructionHandle[] targets = new InstructionHandle[NodeMethods.size()];
		final int[] matches = new int[NodeMethods.size()];

		final int methodIdIndex = 1;
		final int synchronizerIndex = 2;
		final int Qindex = 3;

		for (int i = 0; i < NodeMethods.size(); i++) {
			final Method m = NodeMethods.get(i);
			final Type[] types = m.getArgumentTypes();

			hipGCC.verbose(tab + tab + "rewriting method " + i + " of name " + m.getName() + " and signature "
					+ m.getSignature());

			matches[i] = i;
			targets[i] = il.append(InstructionFactory.createThis());

			il.append(InstructionFactory.createLoad(ClassRepository.SynchronizerType, synchronizerIndex));
			il.append(fc.createCheckCast((ReferenceType) types[0]));
			for (int l = 1; l < types.length; l++) {
				Serialization.createReadFromQueue(types[l], Qindex, il, fc);
			}
			// invoke the method
			il.append(fc.createInvoke(cg.getClassName(), m.getName(), m.getReturnType(), m.getArgumentTypes(),
					Constants.INVOKEVIRTUAL));
			il.append(InstructionFactory.createReturn(Type.VOID));
		}

		/*
		 * create tableswitch
		 */
		if (NodeMethods.size() > 1) {
			final InstructionList throwIl = BCELUtils.createThrowRuntimeException(fc, cpg, "Unrecognized method ",
					methodIdIndex);
			final InstructionHandle defaultTarget = il.append(throwIl);
			il.insert(new TABLESWITCH(matches, targets, defaultTarget));
			il.insert(InstructionFactory.createLoad(Type.SHORT, methodIdIndex));
			throwIl.dispose();
		}

		return mg;
	}

}
