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

import hipg.graph.ExplicitNodeReference;
import hipg.runtime.Runtime;
import hipg.utils.BCELUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import myutils.IOUtils;
import myutils.StringUtils;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ARRAYLENGTH;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.DUP_X1;
import org.apache.bcel.generic.IADD;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.NOP;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.SWAP;
import org.apache.bcel.generic.Type;

public class NodeRewriter {

	private final static String tab = "  ";
	private final static String tt = tab + tab;
	private final static ArrayType bufType = new ArrayType(Type.BYTE, 1);
	private final static ArrayType stateType = new ArrayType(Type.BYTE, 1);
	private final static boolean REWRITE_IMMEDIATE_CALLS = true;

	/** The compiler. */
	private final HipGCC hipGCC;

	/** List of node interfaces. */
	private final Map<String, JavaClass> NodeInterfaces;

	/** List of methods implemented by node interfaces. */
	private final Map<JavaClass, ArrayList<Method>> NodeMethods;

	/** Creates a new local node rewriter. */
	public NodeRewriter(HipGCC hipGCC, Map<String, JavaClass> NodeInterfaces,
			Map<JavaClass, ArrayList<Method>> NodeMethods) {
		this.hipGCC = hipGCC;
		this.NodeInterfaces = NodeInterfaces;
		this.NodeMethods = NodeMethods;
	}

	/**
	 * Checks if all the methods of a given @{hipg.Node} class have correct signature.
	 * 
	 * @return Error message or null if ok
	 * @throws ClassNotFoundException
	 */
	public static String checkNodeInterfaceMethods(final JavaClass cl) throws ClassNotFoundException {
		String className = cl.getClassName();
		Method[] methods = cl.getMethods();
		for (Method method : methods) {

			// check return type
			if (method.getReturnType() != Type.VOID) {
				return "Method " + method.getName() + " in class " + className + " has non-void return type";
			}

			// check the number of arguments
			Type[] argumentTypes = method.getArgumentTypes();
			int arguments = (argumentTypes == null ? 0 : argumentTypes.length);
			if (arguments == 0) {
				return "Method " + method.getName() + " in class " + className + " has no parameters";
			}

			// check if first argument is an object
			if (!(argumentTypes[0] instanceof ObjectType)) {
				return "Method " + method.getName() + " in class " + className + " has first parameter of non object";
			}

			// check if the first argument is a synchronizer
			ObjectType argTypeObj = (ObjectType) argumentTypes[0];
			JavaClass argTypeClass = ClassRepository.lookupClass(argTypeObj);
			if (!SynchronizerRewriter.isSynchronizerClass(argTypeClass)) {
				return "Method " + method.getName() + " in class " + className + " has first parameter "
						+ "of non synchronizer type";
			}

			// check if other parameters are serializable
			if (arguments > 1) {
				for (int j = 1; j < arguments; j++) {
					Type t = argumentTypes[j];
					if (!Serialization.isSerializable(t)) {
						return "Method " + method.getName() + " in class " + className + " has " + j + "-th parameter "
								+ "of non-serializable " + "type " + t;
					}
				}
			}
		}

		return null;
	}

	public static ArrayList<Method> discoverMethods(JavaClass cl) {
		final ArrayList<Method> methods = new ArrayList<Method>();
		final Method[] methodsArr = cl.getMethods();
		if (methodsArr != null) {
			for (int i = 0; i < methodsArr.length; i++) {
				Method m = methodsArr[i];
				if (!m.getName().equals(Constants.CONSTRUCTOR_NAME)
						&& !m.getName().equals(Constants.STATIC_INITIALIZER_NAME)) {
					methods.add(m);
				}
			}
		}
		return methods;
	}

	public void process(final JavaClass cl, final ClassGen cg) throws ClassNotFoundException {

		final String className = cl.getClassName();
		hipGCC.verbose("rewriting node references in class " + className);
		final InstructionFactory fc = new InstructionFactory(cg);

		/* locate remote calls */
		final ConstantPoolGen cpg = cg.getConstantPool();
		final Map<Method, InvocationsInfo> methodsInfo = new HashMap<Method, InvocationsInfo>();
		for (Method m : cg.getMethods()) {
			final String name = m.getName();
			if (!name.equals(Constants.CONSTRUCTOR_NAME) && !name.equals(Constants.STATIC_INITIALIZER_NAME)
					&& !m.isAbstract()) {
				hipGCC.verbose(tab + "checking invocations in method " + name);
				final MethodGen mg = new MethodGen(m, className, cpg);
				final CallsLocator simulator = new CallsLocator(mg, cl, cg);
				simulator.execute();
				if (simulator.remoteCalls.size() + simulator.notificationCalls.size() > 0) {
					methodsInfo.put(m, new InvocationsInfo(mg, simulator.remoteCalls, simulator.notificationCalls));
					hipGCC.verbose(tab + "in method " + name + " located");
					hipGCC.verbose(tab + tab + "remote calls: " + BCELUtils.printList(simulator.remoteCalls.iterator()));
					hipGCC.verbose(tab + tab + "notification calls: "
							+ BCELUtils.printList(simulator.notificationCalls.iterator()));
				}
			}
		}

		//
		// now start modifying the class
		//

		/* rewrite method calls */
		for (Method method : methodsInfo.keySet()) {
			final InvocationsInfo invocationsInfo = methodsInfo.get(method);
			for (RemoteCall remoteCall : invocationsInfo.remoteCalls) {
				if (remoteCall.isCreatedByGlobalNode()) {
					rewriteGlobalNodeCall(invocationsInfo.mg, cg, fc, remoteCall);
				} else {
					rewriteRemoteNeighborCall(invocationsInfo.mg, cg, fc, remoteCall);
				}
			}
			for (NotificationCall notificationCall : invocationsInfo.notificationCalls) {
				rewriteNotificationCall(invocationsInfo.mg, cg, fc, notificationCall);
			}
		}

		/* finalize */
		for (Method method : methodsInfo.keySet()) {
			final InvocationsInfo info = methodsInfo.get(method);
			final MethodGen mg = info.mg;
			mg.setMaxLocals();
			mg.setMaxStack();
			final Method newm = mg.getMethod();
			cg.replaceMethod(method, newm);
			mg.getInstructionList().dispose();
		}
	}

	private final class CallsLocator extends Simulator {

		final ClassGen cg;
		final ArrayList<RemoteCall> remoteCalls = new ArrayList<RemoteCall>();
		final ArrayList<NotificationCall> notificationCalls = new ArrayList<NotificationCall>();

		public CallsLocator(MethodGen mg, JavaClass clazz, ClassGen cg) {
			super(mg, cg);
			this.cg = cg;
		}

		@Override
		public void processBefore(InstructionHandle handleRemote) {
			try {
				doProcessBefore(handleRemote);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		private void doProcessBefore(InstructionHandle handle) throws ClassNotFoundException {

			/* check if the instruction in an invoke instruction */
			if (!(handle.getInstruction() instanceof InvokeInstruction))
				return;

			/* make sure the instruction is not a constructor */
			if (methodName.equals(Constants.CONSTRUCTOR_NAME) || methodName.equals(Constants.CONSTRUCTOR_NAME)) {
				return;
			}

			if (!checkIfRemoteCall(handle))
				checkIfNotificationCall(handle);

		}

		private boolean checkIfRemoteCall(InstructionHandle handle) throws ClassNotFoundException {

			InvokeInstruction invoke = (InvokeInstruction) handle.getInstruction();
			String calledClassName = ((ObjectType) getReferenceType(invoke)).getClassName();
			String calledMethodName = getMethodName(invoke);
			String calledMethodSignature = getSignature(invoke);
			Type[] calledArgumentTypes = getArgumentTypes(invoke);
			int calledArguments = (calledArgumentTypes == null ? 0 : calledArgumentTypes.length);

			/* check if the call is made on a node */
			JavaClass calledClass = ClassRepository.lookupClass(calledClassName);
			if (!calledClass.instanceOf(ClassRepository.getNodeInterface())) {
				return false;
			}

			/* check if the call is to the remote method from a node interface */
			boolean recognized = false;
			for (JavaClass NodeInterface : NodeInterfaces.values()) {
				if (calledClass.implementationOf(NodeInterface)) {
					Method[] methods = NodeInterface.getMethods();
					for (int i = 0; i < methods.length && !recognized; i++) {
						Method method = methods[i];
						String name = method.getName();
						if (name.equals(calledMethodName) && method.getSignature().equals(calledMethodSignature)) {
							recognized = true;
							break;
						}
					}
					if (recognized) {
						break;
					}
				}
			}
			if (!recognized) {
				hipGCC.verbose(tt + "call to " + calledMethodName + " on " + calledClassName + ": not defined "
						+ "by a node interface; not rewriting");
				return false;
			}

			/* check if the creator was found */
			InstructionHandle creatorHandle = peek(calledArguments).getCreator();
			if (creatorHandle == null) {
				hipGCC.warning(tt + "call to " + calledMethodName + " on " + calledClassName + ": "
						+ calledClass.getClassName() + " created from variable; not rewriting");
				return false;
			}
			if (!(creatorHandle.getInstruction() instanceof InvokeInstruction)) {
				hipGCC.verbose(tt + "call to " + calledMethodName + " on " + calledClassName + ": "
						+ calledClass.getClassName() + " not created by a neighbor invoke instruction; not rewriting");
				return false;
			}
			InvokeInstruction invokeCreator = (InvokeInstruction) creatorHandle.getInstruction();

			boolean rewriteRemoteNode = checkIfRemoteNodeMethod(invokeCreator);
			boolean rewriteGraphNode = (rewriteRemoteNode ? false : checkIfGlobalNodeMethod(invokeCreator));

			if (rewriteRemoteNode || rewriteGraphNode) {
				RemoteCall rc = new RemoteCall(cg.getConstantPool(), invoke, handle, invokeCreator, creatorHandle,
						rewriteGraphNode);
				remoteCalls.add(rc);
			}

			return true;
		}

		private boolean checkIfRemoteNodeMethod(InvokeInstruction invokeCreator) throws ClassNotFoundException {
			final Type classType = Type.getReturnType(getReferenceType(invokeCreator).getSignature());
			final String methodName = getMethodName(invokeCreator);

			if (methodName.equals("localNeighbor") || methodName.equals("localInNeighbor")) {
				hipGCC.verbose(tt + "call to " + methodName + " on " + classType
						+ " is not a local neighbor call; not rewriting");
				return false;
			}

			if (!methodName.equals("remoteNeighbor") && !methodName.equals("remoteInNeighbor")
					&& !methodName.equals("neighbor") && !methodName.equals("inNeighbor")) {
				hipGCC.verbose(tt + "call to " + methodName + " on " + classType
						+ " is not a remote neighbor call; not rewriting");
				return false;
			}

			hipGCC.verbose(tt + "call to " + methodName + " on " + classType + ": will rewrite");

			return true;
		}

		private boolean checkIfGlobalNodeMethod(InvokeInstruction invokeCreator) throws ClassNotFoundException {
			final ObjectType classType = (ObjectType) Type
					.getReturnType(getReferenceType(invokeCreator).getSignature());
			final String methodName = getMethodName(invokeCreator);
			final String methodSignature = getSignature(invokeCreator);
			final JavaClass clazz = Repository.lookupClass(classType.getClassName());

			if (!methodName.equals("globalNode") || !methodSignature.equals("(J)Lhipg/Node;")
					|| !clazz.instanceOf(ClassRepository.getExplicitGraphClass())) {
				hipGCC.verbose(tt + "call to " + methodName + " on " + classType + ": " + classType
						+ " not created a call to node on a graph; not rewriting");
				return false;
			}
			return true;
		}

		private boolean checkIfNotificationCall(InstructionHandle handle) {

			final InvokeInstruction invoke = (InvokeInstruction) handle.getInstruction();
			final String calledClassName = ((ObjectType) getReferenceType(invoke)).getClassName();
			final String calledMethodName = getMethodName(invoke);
			final String calledMethodSignature = getSignature(invoke);

			/* check if the call is made on a rewritten synchronizer */
			RewrittenSynchronizerClass rsc = hipGCC.getRewrittenSynchronizerClass(calledClassName);
			if (rsc == null) {
				return false;
			}

			/* check that the method is a notification method */
			NotificationMethod notificationMethod = null;
			for (NotificationMethod nm : rsc.getNotificationMethods()) {
				Method method = nm.getMethod();
				if (method.getName().equals(calledMethodName) && method.getSignature().equals(calledMethodSignature)) {
					if (notificationMethod != null) {
						throw new RuntimeException("Two methods that fit call to " + calledMethodName + " on "
								+ calledClassName + " with signature " + calledMethodSignature + "?");
					}
					notificationMethod = nm;
				}
			}
			if (notificationMethod == null) {
				return false;
			}

			notificationCalls.add(new NotificationCall(cg, handle, invoke, copyStack(), notificationMethod));

			return true;
		}

	}

	public static ArrayList<Method> determineMethodsOrder(JavaClass cl, Map<String, JavaClass> NodeInterfaces,
			Map<JavaClass, ArrayList<Method>> NodeMethods) throws ClassNotFoundException {
		final ArrayList<Method> orderedMethods = new ArrayList<Method>();
		final JavaClass[] allInterfaces = cl.getAllInterfaces();
		for (JavaClass iface : allInterfaces) {
			if (NodeInterfaces.get(iface.getClassName()) != null) {
				orderedMethods.addAll(NodeMethods.get(iface));
			}
		}
		// Collections.reverse(orderedMethods);
		return orderedMethods;
	}

	private void rewriteRemoteNeighborCall(MethodGen mg, ClassGen cg, InstructionFactory fc, RemoteCall rc)
			throws ClassNotFoundException {

		final ConstantPoolGen cpg = cg.getConstantPool();
		final InstructionList il = mg.getInstructionList();

		String remoteMethodName = rc.getInvokeRemoteCall().getMethodName(cpg);

		if (rc.getInvokeCreator() == null) {
			hipGCC.verbose(tab + "do not know how to rewrite " + remoteMethodName + "() " + "in method " + mg.getName());
			return;
		}

		String invokeName = rc.getInvokeCreator().getMethodName(cpg);
		boolean in = invokeName.contains("InNeighbor") || invokeName.contains("inNeighbor");
		String neighborStr = (in ? "InNeighbor" : "Neighbor");

		hipGCC.verbose(tab + "rewriting a call to " + remoteMethodName + "() in method " + mg.getName()
				+ "() created by: " + invokeName);

		int freeVarIndex = mg.getMaxLocals() + 1;
		final int nodeIndex = freeVarIndex++;
		final int neighborIndex = freeVarIndex++;

		/* rewrite 'neighbor' call */

		InstructionList neighborsCode = createNeighborCode(mg, cg, fc, rc.getInvokeCreator(), neighborStr, nodeIndex,
				neighborIndex);
		InstructionHandle neighborsFirst = il.insert(rc.getInvokeCreatorHandle(), neighborsCode);
		neighborsCode.dispose();

		/* delete neighbors call */
		BCELUtils.deleteInstruction(il, rc.getInvokeCreatorHandle(), neighborsFirst);

		/* rewrite the call to a remote method */

		InstructionList callRemote = null;

		callRemote = createCallRemoteMethodCodeOnNeighbor(mg, cg, fc, rc, neighborStr, freeVarIndex, nodeIndex,
				neighborIndex);

		InstructionHandle remoteFirst = il.insert(rc.getInvokeRemoteCallHandle(), callRemote);
		callRemote.dispose();

		/* delete the original call to a remote method */
		BCELUtils.deleteInstruction(il, rc.getInvokeRemoteCallHandle(), remoteFirst);

	}

	private void rewriteGlobalNodeCall(MethodGen mg, ClassGen cg, InstructionFactory fc, RemoteCall rc)
			throws ClassNotFoundException {

		final ConstantPoolGen cpg = cg.getConstantPool();
		final InstructionList il = mg.getInstructionList();

		String remoteMethodName = rc.getInvokeRemoteCall().getMethodName(cpg);

		if (rc.getInvokeCreator() == null) {
			hipGCC.verbose(tab + "do not know how to rewrite " + remoteMethodName + "() " + "in method " + mg.getName());
			return;
		}

		String invokeName = rc.getInvokeCreator().getMethodName(cpg);
		boolean in = invokeName.contains("InNeighbor") || invokeName.contains("inNeighbor");
		String neighborStr = (in ? "InNeighbor" : "Neighbor");

		hipGCC.verbose(tab + "rewriting a call to " + remoteMethodName + "() " + "in method " + mg.getName()
				+ " created by: " + invokeName);

		int freeVarIndex = mg.getMaxLocals() + 1;
		final int graphIndex = freeVarIndex + 1;
		final int dstIndex = graphIndex + 1;
		freeVarIndex = dstIndex + Type.LONG.getSize();

		/* rewrite 'neighbor' call */

		InstructionList globalNodeCode = createGlobalNodeCode(mg, cg, fc, rc, neighborStr, graphIndex, dstIndex);
		InstructionHandle neighborsFirst = il.insert(rc.getInvokeCreatorHandle(), globalNodeCode);
		globalNodeCode.dispose();

		/* delete neighbors call */
		BCELUtils.deleteInstruction(il, rc.getInvokeCreatorHandle(), neighborsFirst);

		/* rewrite the call to a remote method */
		InstructionList callRemote = createCallGlobalNodeCodeOnNode(mg, cg, fc, rc, neighborStr, freeVarIndex,
				graphIndex, dstIndex);
		InstructionHandle remoteFirst = il.insert(rc.getInvokeRemoteCallHandle(), callRemote);
		callRemote.dispose();

		/* delete the original call to a remote method */
		BCELUtils.deleteInstruction(il, rc.getInvokeRemoteCallHandle(), remoteFirst);

	}

	private InstructionList createNeighborCode(MethodGen mg, ClassGen cg, InstructionFactory fc,
			InvokeInstruction invoke, String neighborStr, int nodeIndex, int neighborIndex) {

		ConstantPoolGen cpg = cg.getConstantPool();
		InstructionList il = new InstructionList();
		ObjectType nodeType = (ObjectType) invoke.getReferenceType(cpg);
		JavaClass nodeJavaClass = ClassRepository.lookupClass(nodeType);
		JavaClass nodeImplementation = BCELUtils.firstSuperClassWithAMethod(nodeJavaClass,
				"is" + neighborStr + "Local", Type.BOOLEAN, new Type[] { Type.INT });
		if (nodeJavaClass == null)
			throw new RuntimeException("Could not find node class " + nodeType);
		if (nodeImplementation == null)
			throw new RuntimeException("Could not find node implementation class of node "
					+ nodeJavaClass.getClassName());

		hipGCC.verbose(tt + "rewriting a neighbor call in method " + mg.getName());

		// assume: the top element of the stack is an integer
		// value 'i' denoting the neighbor to use;
		// directly below is a 'node'

		if (hipGCC.debugCode())
			BCELUtils.appendFlag(10, il, cpg);
		/* store node and neighbor number in a local variable */
		// stack: node i
		il.append(new DUP());
		il.append(InstructionFactory.createStore(Type.INT, neighborIndex));
		il.append(new SWAP());
		il.append(new DUP());
		il.append(InstructionFactory.createStore(Type.OBJECT, nodeIndex));
		il.append(new DUP_X1());
		il.append(new SWAP());
		il.append(new DUP_X1());
		// stack: node i node i

		/* check if node is local */

		// stack: node i node i
		il.append(fc.createInvoke(nodeImplementation.getClassName(), "is" + neighborStr + "Local", Type.BOOLEAN,
				new Type[] { Type.INT }, Constants.INVOKEVIRTUAL));
		// stack: node i isLoc
		il.append(new PUSH(cpg, 0));
		// stack: node i isLoc 0
		BranchInstruction ifremote = InstructionFactory.createBranchInstruction(Constants.IF_ICMPEQ, null);
		il.append(ifremote);
		// stack: node i

		/* handle local node */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(20, il, cpg);
		// stack: node i
		il.append(fc.createInvoke(nodeImplementation.getClassName(), "local" + neighborStr, new ObjectType(
				nodeImplementation.getClassName()), new Type[] { Type.INT }, invoke.getOpcode()));
		// stack: locNeigh
		BranchInstruction gotoDone = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(gotoDone);

		/* handle remote node */
		InstructionHandle startRemote = il.append(new NOP());
		// stack: node i
		il.append(new POP());
		il.append(new POP());
		il.append(InstructionConstants.ACONST_NULL);
		// stack: null

		if (gotoDone != null) {
			InstructionHandle last = il.append(new NOP());
			gotoDone.setTarget(last);
			ifremote.setTarget(startRemote);
		}

		return il;
	}

	private InstructionList createGlobalNodeCode(MethodGen mg, ClassGen cg, InstructionFactory fc, RemoteCall rc,
			String neighborStr, int graphIndex, int dstIndex) {

		ConstantPoolGen cpg = cg.getConstantPool();
		InstructionList il = new InstructionList();

		InvokeInstruction creatorInvoke = rc.getInvokeCreator();
		ObjectType graphType = (ObjectType) creatorInvoke.getReferenceType(cpg);
		JavaClass graphJavaClass = ClassRepository.lookupClass(graphType);
		JavaClass nodeClass = ClassRepository.getExplicitLocalNodeClass();

		hipGCC.verbose(tt + "rewriting a node call to " + creatorInvoke.getMethodName(cpg) + " in method "
				+ mg.getName());

		if (hipGCC.debugCode())
			BCELUtils.appendFlag(30, il, cpg);

		/* store node and neighbor number in a local variable */
		// dst is the reference of type long
		// stack: graph dst
		final Type refType = Type.LONG;
		il.append(InstructionFactory.createStore(refType, dstIndex));
		il.append(InstructionFactory.createStore(Type.OBJECT, graphIndex));
		// stack:

		/* check if node is local */
		// stack:
		il.append(InstructionFactory.createLoad(refType, dstIndex));
		il.append(fc.createInvoke(ExplicitNodeReference.class.getName(), "isLocal", Type.BOOLEAN,
				new Type[] { Type.LONG }, Constants.INVOKESTATIC));
		// stack: isLoc
		il.append(new PUSH(cpg, 0));
		BranchInstruction ifremote = InstructionFactory.createBranchInstruction(Constants.IF_ICMPEQ, null);
		il.append(ifremote);
		// stack:

		/* handle local node */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(40, il, cpg);
		// stack:
		il.append(InstructionFactory.createLoad(Type.OBJECT, graphIndex));
		il.append(InstructionFactory.createLoad(refType, dstIndex));
		il.append(fc.createInvoke(ExplicitNodeReference.class.getName(), "getId", Type.INT, new Type[] { Type.LONG },
				Constants.INVOKESTATIC));
		il.append(fc.createInvoke(graphJavaClass.getClassName(), "node", new ObjectType(nodeClass.getClassName()),
				new Type[] { Type.INT }, creatorInvoke.getOpcode()));
		// stack: locNeigh
		BranchInstruction gotoDone = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(gotoDone);

		/* handle remote node */
		InstructionHandle startRemote = il.append(new NOP());
		// stack:
		il.append(InstructionConstants.ACONST_NULL);
		// stack: null

		if (gotoDone != null) {
			InstructionHandle last = il.append(new NOP());
			gotoDone.setTarget(last);
			ifremote.setTarget(startRemote);
		}

		return il;
	}

	private InstructionList createCallRemoteMethodCodeOnNeighbor(MethodGen mg, ClassGen cg, InstructionFactory fc,
			RemoteCall rc, String neighborStr, int freeVarIndex, int nodeIndex, int neighborIndex)
			throws ClassNotFoundException {

		ConstantPoolGen cpg = cg.getConstantPool();
		InstructionList il = new InstructionList();
		String remoteMethodName = rc.getInvokeRemoteCall().getMethodName(cpg);
		ObjectType nodeType = (ObjectType) rc.getInvokeCreator().getReferenceType(cpg);
		JavaClass nodeJavaClass = ClassRepository.lookupClass(nodeType);
		JavaClass nodeImplementation = BCELUtils.firstSuperClassWithAMethod(nodeJavaClass,
				"is" + neighborStr + "Local", Type.BOOLEAN, new Type[] { Type.INT });

		hipGCC.verbose(tt + "rewriting call to " + remoteMethodName + " in method " + mg.getName());

		// assume stack: neighbor x1 x2 .. xn
		// where x=(x1..xn) is a list of parameter of the remote call

		/* check if neighbor is local */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(50, il, cpg);
		// stack: neigh x
		il.append(InstructionFactory.createLoad(Type.OBJECT, nodeIndex));
		il.append(InstructionFactory.createLoad(Type.INT, neighborIndex));
		il.append(fc.createInvoke(nodeImplementation.getClassName(), "is" + neighborStr + "Local", Type.BOOLEAN,
				new Type[] { Type.INT }, Constants.INVOKEVIRTUAL));
		il.append(new PUSH(cpg, 0));
		BranchInstruction ifremote = InstructionFactory.createBranchInstruction(Constants.IF_ICMPEQ, null);
		il.append(ifremote);
		// stack: neigh x

		/* local call */

		BranchInstruction iftoodeep = null;

		if (REWRITE_IMMEDIATE_CALLS) {
			// stack: neigh x
			// if (Runtime.getRuntime().ImmediateDepth())
			il.append(fc.createInvoke(ClassRepository.RuntimeClassName, "incImmediateDepth", Type.BOOLEAN,
					Type.NO_ARGS, Constants.INVOKESTATIC));
			il.append(new PUSH(cpg, 0));
			iftoodeep = InstructionFactory.createBranchInstruction(Constants.IF_ICMPEQ, null);
			il.append(iftoodeep);
		}

		// invoke local call
		// stack: neigh x
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(60, il, cpg);
		il.append(rc.getInvokeRemoteCall().copy());
		if (REWRITE_IMMEDIATE_CALLS) {
			il.append(fc.createInvoke(ClassRepository.RuntimeClassName, "decImmediateDepth", Type.VOID, Type.NO_ARGS,
					Constants.INVOKESTATIC));
		}
		BranchInstruction gotoDone = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(gotoDone);
		// stack: empty

		// store local call on the stack
		// stack: neigh x
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(70, il, cpg);
		final InstructionList storeLocalCall = createStoreLocalCall(mg, cg, fc, rc, neighborStr, freeVarIndex);
		InstructionHandle toodeep = il.append(storeLocalCall);
		storeLocalCall.dispose();
		if (REWRITE_IMMEDIATE_CALLS) {
			iftoodeep.setTarget(toodeep);
		}
		BranchInstruction gotoDone2 = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(gotoDone2);

		/* remote call */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(80, il, cpg);
		// stack: neigh x
		final InstructionList sendUserMessage = createSendUserMessage(mg, cg, fc, rc, neighborStr, freeVarIndex,
				nodeIndex, neighborIndex, -1, -1);
		InstructionHandle remoteStart = il.append(sendUserMessage);
		sendUserMessage.dispose();
		// stack: empty ?
		if (hipGCC.debugCode()) {
			BCELUtils.appendFlag(90, il, cpg);
		}

		// /* fake send user message for info */
		// InstructionHandle sendStart = il.append(new NOP());
		// Type[] argTypes = rc.getInvokeRemote().getArgumentTypes(cpg);
		// for (int i = argTypes.length - 1; i >= 0; i--)
		// il.append(InstructionFactory.createPop(argTypes[i].getSize()));
		// il.append(new POP());

		ifremote.setTarget(remoteStart);
		InstructionHandle done = il.append(new NOP());
		gotoDone.setTarget(done);
		gotoDone2.setTarget(done);

		return il;
	}

	private InstructionList createCallGlobalNodeCodeOnNode(MethodGen mg, ClassGen cg, InstructionFactory fc,
			RemoteCall rc, String neighborStr, int freeVarIndex, int graphIndex, int dstIndex)
			throws ClassNotFoundException {

		ConstantPoolGen cpg = cg.getConstantPool();
		InstructionList il = new InstructionList();
		String remoteMethodName = rc.getInvokeRemoteCall().getMethodName(cpg);

		hipGCC.verbose(tt + "rewriting call to " + remoteMethodName + " in method " + mg.getName());

		// assume stack: node x1 x2 .. xn
		// where x=(x1..xn) is a list of parameter of the remote call

		/* check if node is local */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(100, il, cpg);
		// stack: node x
		final Type refType = Type.LONG;
		il.append(InstructionFactory.createLoad(refType, dstIndex));
		il.append(fc.createInvoke(ExplicitNodeReference.class.getName(), "isLocal", Type.BOOLEAN,
				new Type[] { Type.LONG }, Constants.INVOKESTATIC));
		il.append(new PUSH(cpg, 0));
		BranchInstruction ifremote = InstructionFactory.createBranchInstruction(Constants.IF_ICMPEQ, null);
		il.append(ifremote);
		// stack: node x

		/* local call */
		BranchInstruction iftoodeep = null;

		if (REWRITE_IMMEDIATE_CALLS) {
			// stack: node x
			// if (Runtime.getRuntime().ImmediateDepth())
			il.append(fc.createInvoke(ClassRepository.RuntimeClassName, "incImmediateDepth", Type.BOOLEAN,
					Type.NO_ARGS, Constants.INVOKESTATIC));
			il.append(new PUSH(cpg, 0));
			iftoodeep = InstructionFactory.createBranchInstruction(Constants.IF_ICMPEQ, null);
			il.append(iftoodeep);
		}

		// invoke local call
		// stack: node x
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(110, il, cpg);
		il.append(rc.getInvokeRemoteCall().copy());
		if (REWRITE_IMMEDIATE_CALLS) {
			il.append(fc.createInvoke(ClassRepository.RuntimeClassName, "decImmediateDepth", Type.VOID, Type.NO_ARGS,
					Constants.INVOKESTATIC));
		}
		BranchInstruction gotoDone = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(gotoDone);
		// stack: empty

		// store local call on the stack
		// stack: neigh x
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(120, il, cpg);
		final InstructionList storeLocalCall = createStoreLocalCall(mg, cg, fc, rc, neighborStr, freeVarIndex);
		InstructionHandle toodeep = il.append(storeLocalCall);
		storeLocalCall.dispose();
		if (REWRITE_IMMEDIATE_CALLS) {
			iftoodeep.setTarget(toodeep);
		}
		BranchInstruction gotoDone2 = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(gotoDone2);

		/* remote call */
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(130, il, cpg);
		// stack: neigh x
		final InstructionList sendUserMessage = createSendUserMessage(mg, cg, fc, rc, neighborStr, freeVarIndex, -1,
				-1, graphIndex, dstIndex);
		InstructionHandle remoteStart = il.append(sendUserMessage);
		sendUserMessage.dispose();
		// stack: empty ?
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(140, il, cpg);

		// /* fake send user message for info */
		// InstructionHandle sendStart = il.append(new NOP());
		// Type[] argTypes = rc.getInvokeRemote().getArgumentTypes(cpg);
		// for (int i = argTypes.length - 1; i >= 0; i--)
		// il.append(InstructionFactory.createPop(argTypes[i].getSize()));
		// il.append(new POP());

		ifremote.setTarget(remoteStart);
		InstructionHandle done = il.append(new NOP());
		gotoDone.setTarget(done);
		gotoDone2.setTarget(done);

		return il;
	}

	private int determineNodeImplementationType(JavaClass localObjClass) throws ClassNotFoundException {
		if (localObjClass == null) {
			return -1;
		}
		if (localObjClass.instanceOf(ClassRepository.getGraphInterface())) {
			if (localObjClass.instanceOf(ClassRepository.getExplicitGraphClass())) {
				return Runtime.GRAPH_EXPLICIT;
			} else if (localObjClass.instanceOf(ClassRepository.getOnTheFlyGraphClass())) {
				return Runtime.GRAPH_ONTHEFLY;
			}
		} else {
			JavaClass nodeImplementation = BCELUtils.firstSuperClassWithAMethod(localObjClass, "isNeighborLocal",
					Type.BOOLEAN, new Type[] { Type.INT });
			if (nodeImplementation == null) {
				return -2;
			} else if (nodeImplementation.instanceOf(ClassRepository.getExplicitLocalNodeClass())) {
				return Runtime.GRAPH_EXPLICIT;
			} else if (nodeImplementation.instanceOf(ClassRepository.getOnTheFlyLocalNodeClass())) {
				return Runtime.GRAPH_ONTHEFLY;
			}
		}
		return -1;
	}

	private InstructionList createSendUserMessage(MethodGen mg, ClassGen cg, InstructionFactory fc, RemoteCall rc,
			final String neighborStr, final int freeVarIndex, final int nodeIndex, final int neighborIndex,
			final int graphIndex, final int dstIndex) throws ClassNotFoundException {

		InstructionList il = new InstructionList();
		ConstantPoolGen cpg = cg.getConstantPool();

		InvokeInstruction remoteInvoke = rc.getInvokeRemoteCall();
		String remoteMethodName = remoteInvoke.getMethodName(cpg);
		String remoteMethodSignature = remoteInvoke.getSignature(cpg);
		Type[] remoteArgumentTypes = remoteInvoke.getArgumentTypes(cpg);
		int remoteArguments = (remoteArgumentTypes == null ? 0 : remoteArgumentTypes.length);
		int[] remoteArgumentIndices = new int[remoteArguments];
		ObjectType remoteType = (ObjectType) remoteInvoke.getReferenceType(cpg);

		InvokeInstruction creatorInvoke = rc.getInvokeCreator();
		ObjectType creatorType = (ObjectType) creatorInvoke.getReferenceType(cpg);
		JavaClass creatorClass = ClassRepository.lookupClass(creatorType);
		final int nodeImplementationType = determineNodeImplementationType(creatorClass);
		ObjectType nodeType = creatorClass.instanceOf(ClassRepository.getGraphInterface()) ? remoteType : creatorType;
		JavaClass nodeClass = ClassRepository.lookupClass(nodeType);

		if (nodeImplementationType < 0) {
			throw new RuntimeException("Unknown node implementation type for node class: " + nodeClass.getClassName());
		}

		/* determine node methods order */
		ArrayList<Method> methods = determineMethodsOrder(nodeClass, NodeInterfaces, NodeMethods);
		hipGCC.verbose(tab + tab + "determined node methods for the called node class " + creatorType + ": "
				+ BCELUtils.printList(methods.iterator()));

		/* determine method id */
		int methodId = 0;
		while (methodId < methods.size()) {
			Method m = methods.get(methodId);
			if (m.getName().equals(remoteMethodName) && m.getSignature().equals(remoteMethodSignature)) {
				break;
			}
			methodId++;
		}
		if (methodId >= methods.size()) {
			hipGCC.error("Method " + remoteMethodName + "() was not found among remote methods. This is a bug!(1)");
		}

		/* assume: stack contains the object and parameters of the remote method */

		/* compute indices */
		int newVarIndex = freeVarIndex;
		final int paramCountIndex = newVarIndex;
		newVarIndex += Type.INT.getSize();
		final int bufIndex = newVarIndex;
		newVarIndex += bufType.getSize();
		final int positionIndex = newVarIndex;
		newVarIndex += Type.INT.getSize();

		/* compute number of parameters and store parameters */
		// stack: neigh parameters
		if (hipGCC.debugCode()) {
			BCELUtils.appendFlag(150, il, cpg);
		}
		int staticParamCount = 0;
		for (int j = 1; j < remoteArguments; j++) {
			final Type t = remoteArgumentTypes[j];
			if (Serialization.isPrimitive(t)) {
				staticParamCount += Serialization.staticTypeSizeInBytes(t);
			}
		}
		il.append(new PUSH(cpg, staticParamCount));
		il.append(InstructionFactory.createStore(Type.INT, paramCountIndex));
		for (int j = remoteArguments - 1; j >= 0; j--) {
			final Type varType = remoteArgumentTypes[j];
			if (hipGCC.debugCode()) {
				BCELUtils.appendFlag(j > 0 ? (20000 + Serialization.createTypeId(varType)) : 20000, il, cpg);
			}
			final int storeIdx = newVarIndex;
			if (Serialization.isPrimitive(varType) || j == 0) {
				il.append(InstructionFactory.createStore(varType, storeIdx));
			} else {
				il.append(InstructionFactory.createDup(varType.getSize()));
				il.append(InstructionFactory.createStore(varType, storeIdx));
				Serialization.getRequiredBufferSizeToStoreType(varType, cg, fc, il);
				// il.append(InstructionFactory.createPop(varType.getSize()));//??should
				// this be here
				il.append(InstructionFactory.createLoad(Type.INT, paramCountIndex));
				il.append(new IADD());
				il.append(InstructionFactory.createStore(Type.INT, paramCountIndex));
			}
			remoteArgumentIndices[j] = storeIdx;
			newVarIndex += varType.getSize();
		}
		final int synchIndex = remoteArgumentIndices[0];
		// stack = neighborNode(=null)
		il.append(new POP());
		// stack: empty

		/* get state (for the on-the-fly graphs) */
		if (nodeImplementationType == Runtime.GRAPH_ONTHEFLY) {
			if (hipGCC.debugCode()) {
				BCELUtils.appendFlag(160, il, cpg);
			}
			if (neighborIndex >= 0) {
				// byte[] state = node.getNeighbor(neighborIndex)
				il.append(InstructionFactory.createLoad(Type.OBJECT, nodeIndex));
				il.append(InstructionFactory.createLoad(Type.INT, neighborIndex));
				il.append(fc.createInvoke(nodeClass.getClassName(), "getNeighbor", stateType, new Type[] { Type.INT },
						Constants.INVOKEVIRTUAL));
				il.append(new DUP());
				Serialization.getRequiredBufferSizeToStoreType(stateType, cg, fc, il);
				il.append(InstructionFactory.createLoad(Type.INT, paramCountIndex));
				il.append(new IADD());
				il.append(InstructionFactory.createStore(Type.INT, paramCountIndex));
				il.append(new POP());
			} else {
				throw new RuntimeException("Not implemented");
			}
		}
		// stack: empty OR state

		/* Get write buffer. */
		// stack: empty OR state
		// FastMessage msg = Runtime.getCommunication().getUserMessage(
		// nodeIndex.neighborsOwner(neighborIndex), synchronizerIndex,
		// paramCountIndex));
		if (hipGCC.debugCode()) {
			BCELUtils.appendFlag(170, il, cpg);
		}
		il.append(fc.createInvoke(ClassRepository.RuntimeClassName, "getCommunication",
				ClassRepository.CommunicationType, Type.NO_ARGS, Constants.INVOKESTATIC));
		if (neighborIndex >= 0) {
			// node.getOwner(neighborIndex);
			il.append(InstructionFactory.createLoad(Type.OBJECT, nodeIndex));
			il.append(InstructionFactory.createLoad(Type.INT, neighborIndex));
			il.append(fc.createInvoke(creatorType.getClassName(), StringUtils.LowercaseFirstLetter(neighborStr)
					+ "Owner", Type.INT, new Type[] { Type.INT }, Constants.INVOKEVIRTUAL));
		} else {
			// ExplicitNodeReference.getOwner(dstIndex);
			il.append(InstructionFactory.createLoad(Type.LONG, dstIndex));
			il.append(fc.createInvoke(ExplicitNodeReference.class.getName(), "getOwner", Type.INT,
					new Type[] { Type.LONG }, Constants.INVOKESTATIC));
		}
		il.append(InstructionFactory.createLoad(Type.OBJECT, synchIndex));
		il.append(InstructionFactory.createLoad(Type.INT, paramCountIndex));
		il.append(fc.createInvoke(ClassRepository.CommunicationClassName, "getUserMessage",
				ClassRepository.FastMessageType, new Type[] { Type.INT, ClassRepository.SynchronizerType, Type.INT },
				Constants.INVOKEVIRTUAL));
		// stack: (empty OR state) msg
		il.append(new DUP());
		il.append(fc.createFieldAccess(ClassRepository.FastMessageClassName, "buf", bufType, Constants.GETFIELD));
		il.append(InstructionFactory.createStore(bufType, bufIndex));
		il.append(new DUP());
		il.append(fc.createFieldAccess(ClassRepository.FastMessageClassName, "position", Type.INT, Constants.GETFIELD));
		il.append(InstructionFactory.createStore(Type.INT, positionIndex));
		// stack: (empty OR state) msg
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(180, il, cpg);

		/* load parameters to write:
		 * 
		 * 1) synchronizer.getOwner() (int) 2) synchronizer.getId() (int) 3) node.graphId (short) 4) methodId (short)
		 * 5) target: either referenceId (int) or a full state (byte[]) */
		if (nodeImplementationType == Runtime.GRAPH_ONTHEFLY) {
			il.append(new SWAP());
		}
		// stack: msg (empty OR state)
		il.append(InstructionFactory.createLoad(bufType, bufIndex));
		il.append(InstructionFactory.createLoad(Type.INT, positionIndex));
		il.append(InstructionFactory.createLoad(Type.OBJECT, synchIndex));
		il.append(new DUP());
		il.append(fc.createInvoke(ClassRepository.SynchronizerClassName, "getOwner", Type.INT, Type.NO_ARGS,
				Constants.INVOKEVIRTUAL));
		il.append(new SWAP());
		il.append(fc.createInvoke(ClassRepository.SynchronizerClassName, "getId", Type.INT, Type.NO_ARGS,
				Constants.INVOKEVIRTUAL));
		if (neighborIndex >= 0) {
			if (hipGCC.debugCode())
				BCELUtils.appendFlag(181, il, cpg);
			il.append(InstructionFactory.createLoad(Type.OBJECT, nodeIndex));
			il.append(fc.createInvoke(creatorType.getClassName(), "graphId", Type.SHORT, Type.NO_ARGS,
					Constants.INVOKEVIRTUAL));
		} else {
			if (hipGCC.debugCode())
				BCELUtils.appendFlag(182, il, cpg);
			il.append(InstructionFactory.createLoad(Type.OBJECT, graphIndex));
			il.append(fc.createInvoke(creatorType.getClassName(), "getId", Type.SHORT, Type.NO_ARGS,
					Constants.INVOKEVIRTUAL));
		}
		il.append(new PUSH(cpg, methodId));
		// stack: msg (empty OR state) synchOwner synchId graphId methodId
		if (nodeImplementationType == Runtime.GRAPH_EXPLICIT) {
			// buf[position++] = node.(in)neighborId(neighbor)
			if (neighborIndex >= 0) {
				if (hipGCC.debugCode())
					BCELUtils.appendFlag(205, il, cpg);
				// node.neighborId(neighborIndex)
				il.append(InstructionFactory.createLoad(Type.OBJECT, nodeIndex));
				il.append(InstructionFactory.createLoad(Type.INT, neighborIndex));
				il.append(fc.createInvoke(creatorType.getClassName(), StringUtils.LowercaseFirstLetter(neighborStr)
						+ "Id", Type.INT, new Type[] { Type.INT }, Constants.INVOKEVIRTUAL));
			} else {
				if (hipGCC.debugCode())
					BCELUtils.appendFlag(206, il, cpg);
				// ExplicitNodeReference.getId(dst)
				il.append(InstructionFactory.createLoad(Type.LONG, dstIndex));
				il.append(fc.createInvoke(ExplicitNodeReference.class.getName(), "getId", Type.INT,
						new Type[] { Type.LONG }, Constants.INVOKESTATIC));
			}
			il.append(fc.createInvoke(ClassRepository.SerializationClassName, "writeExplicitUserMessage", Type.VOID,
					new Type[] { bufType, Type.INT, Type.INT, Type.INT, Type.SHORT, Type.SHORT, Type.INT },
					Constants.INVOKESTATIC));
			il.append(new PUSH(cpg, 3 * IOUtils.INT_BYTES + 2 * IOUtils.SHORT_BYTES));
		} else if (nodeImplementationType == Runtime.GRAPH_ONTHEFLY) {
			if (hipGCC.debugCode())
				BCELUtils.appendFlag(207, il, cpg);
			il.append(fc.createInvoke(ClassRepository.SerializationClassName, "writeOnTheFlyUserMessage", Type.VOID,
					new Type[] { stateType, bufType, Type.INT, Type.INT, Type.INT, Type.SHORT, Type.SHORT },
					Constants.INVOKESTATIC));
			il.append(new PUSH(cpg, 2 * IOUtils.INT_BYTES + 2 * IOUtils.SHORT_BYTES + IOUtils.LENGTH_BYTES));

		}
		il.append(InstructionFactory.createLoad(Type.INT, positionIndex));
		il.append(new IADD());
		il.append(InstructionFactory.createStore(Type.INT, positionIndex));
		// stack: msg

		/* serialize parameters */
		if (hipGCC.debugCode()) {
			BCELUtils.appendFlag(209, il, cpg);
		}
		// stack: msg
		for (int j = 1; j < remoteArguments; j++) {
			final Type varType = remoteArgumentTypes[j];
			final int varIndex = remoteArgumentIndices[j];
			try {
				if (hipGCC.debugExe()) {
					InstructionList dbg = BCELUtils.println("err", "writing argument of type to position", cpg, cg, fc,
							InstructionFactory.createLoad(Type.INT, positionIndex), Type.INT);
					il.append(dbg);
					dbg.dispose();
				}
				// stack: msg
				if (hipGCC.debugCode()) {
					BCELUtils.appendFlag(-20000 - j, il, cpg);
				}
				Serialization.createWriteToBufFromIndex(varType, varIndex, bufIndex, positionIndex, il, fc, cg);
				// stack: msg
			} catch (Throwable e) {
				hipGCC.error("Problem with " + j + "-th parameter type " + varType + " of method " + remoteMethodName
						+ " in " + creatorType + ": " + e.getMessage());
			}
		}
		// stack: msg

		/* commit write */
		if (hipGCC.debugCode()) {
			BCELUtils.appendFlag(210, il, cpg);
		}
		// stack: msg
		il.append(InstructionFactory.createLoad(Type.INT, positionIndex));
		il.append(fc.createInvoke(ClassRepository.FastMessageClassName, "commitWrite", Type.VOID,
				new Type[] { Type.INT }, Constants.INVOKEVIRTUAL));
		// stack: empty

		if (hipGCC.debugCode())
			BCELUtils.appendFlag(220, il, cpg);

		return il;
	}

	private InstructionList createStoreLocalCall(MethodGen mg, ClassGen cg, InstructionFactory fc, RemoteCall rc,
			String neighborStr, int freeVarIndex) throws ClassNotFoundException {

		InstructionList il = new InstructionList();
		ConstantPoolGen cpg = cg.getConstantPool();

		InvokeInstruction invokeRemote = rc.getInvokeRemoteCall();
		String calledMethodName = invokeRemote.getMethodName(cpg);
		String calledMethodSignature = invokeRemote.getSignature(cpg);
		Type[] calledArgumentTypes = invokeRemote.getArgumentTypes(cpg);
		int calledArguments = (calledArgumentTypes == null ? 0 : calledArgumentTypes.length);
		int[] calledArgumentIndices = new int[calledArguments];
		ObjectType nodeType = (ObjectType) rc.getInvokeRemoteCall().getReferenceType(cpg);

		/* determine node methods order */
		ArrayList<Method> methods = determineMethodsOrder(ClassRepository.lookupClass(nodeType), NodeInterfaces,
				NodeMethods);
		hipGCC.verbose(tab + tab + "determined node methods for the called node class " + nodeType + ": "
				+ BCELUtils.printList(methods.iterator()));

		/* determine method id */
		int methodId = 0;
		while (methodId < methods.size()) {
			Method m = methods.get(methodId);
			if (m.getName().equals(calledMethodName) && m.getSignature().equals(calledMethodSignature)) {
				break;
			}
			methodId++;
		}
		if (methodId >= methods.size()) {
			hipGCC.error("Method " + calledMethodName + "() was not found among remote methods. This is a bug!(2)");
		}
		/* compute indices */
		int newVarIndex = freeVarIndex + Type.INT.getSize();
		newVarIndex += Type.INT.getSize();
		newVarIndex += bufType.getSize();
		newVarIndex += Type.INT.getSize();
		newVarIndex += Type.DOUBLE.getSize();

		if (hipGCC.debugCode())
			BCELUtils.appendFlag(230, il, cpg);

		/* store parameters from stack into local variables and pop the (null) reference to a remote node. effect: pops
		 * all parameters */
		// stack: neigh x
		// where x = (x0,x1...x{n-1}) n=calledArguments
		// are parameters of the the call that is being rewritten
		for (int i = 0; i < calledArguments; i++) {
			int j = calledArguments - 1 - i;
			Type t = calledArgumentTypes[j];
			int storeIdx = newVarIndex;
			il.append(InstructionFactory.createStore(t, storeIdx));
			calledArgumentIndices[j] = storeIdx;
			newVarIndex += t.getSize();
		}
		final int synchronizerIndex = calledArgumentIndices[0];

		if (hipGCC.debugCode())
			BCELUtils.appendFlag(240, il, cpg);
		// stack: neigh
		il.append(fc.createCast(nodeType, ClassRepository.LocalNodeType));
		// stack: localNode
		il.append(InstructionFactory.createLoad(Type.OBJECT, synchronizerIndex));
		// stack: localNode synchronizer
		il.append(new SWAP());
		// stack: synchronizer localNode
		il.append(new PUSH(cpg, methodId));
		// stack: synchronizer localNode methodId
		// invoke addMethodInvocation(localNode,methodOd)
		il.append(fc.createInvoke(ClassRepository.SynchronizerClassName, "addMethodInvocation",
				ClassRepository.BigQueueType, new Type[] { ClassRepository.LocalNodeType, Type.SHORT },
				Constants.INVOKEVIRTUAL));
		// stack: synchStack
		for (int i = 1; i < calledArguments; i++) {
			if (hipGCC.debugCode()) {
				BCELUtils.appendFlag(240 + i, il, cpg);
			}
			// stack: synchStack
			Serialization.createWriteToQueueOnStack(calledArgumentTypes[i], calledArgumentIndices[i], il, fc);
			// stack: synchStack
		}
		// stack: synchStack
		il.append(new POP());
		// stack: empty
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(270, il, cpg);

		return il;
	}

	private void rewriteNotificationCall(MethodGen mg, ClassGen cg, InstructionFactory fc, NotificationCall nc)
			throws ClassNotFoundException {

		final InstructionList il = new InstructionList();
		final ConstantPoolGen cpg = cg.getConstantPool();
		final NotificationMethod nm = nc.getNotificationMethod();
		final ArrayList<StackElement> stack = nc.getStack();
		final InstructionHandle start = null;

		hipGCC.verbose(tab + "rewriting a call to " + nm.getMethod().getName() + "() " + "in method " + mg.getName());

		int freeVarIndex = mg.getMaxLocals();
		final int bufIndex = freeVarIndex++;
		final int positionIndex = freeVarIndex++;

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
				if (hipGCC.debugCode()) {
					BCELUtils.appendFlag(280, il, cpg);
				}
				Serialization.getRequiredBufferSizeToStoreType(t, cg, fc, il);
			}
		}

		// stack: s x0 x1 .. xk
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(290, il, cpg);
		il.append(new PUSH(cpg, initParamSize));
		if (doAdd) {
			il.append(new IADD());
		}
		// stack: s x0 x1 .. xk sz
		il.append(fc.createNewArray(Type.BYTE, (short) 1));
		il.append(InstructionFactory.createStore(Type.OBJECT, bufIndex));
		il.append(new PUSH(cpg, 0));
		il.append(InstructionFactory.createStore(Type.INT, positionIndex));
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(300, il, cpg);
		// stack: s x0 x1 .. xk
		for (int i = 0; i < types.length; i++) {
			Type t = stack.remove(stack.size() - 1).getType();
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
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(310, il, cpg);
		// stack: s
		// call notification(methodId, buf)
		il.append(new PUSH(cpg, nc.getNotificationMethod().getId()));
		il.append(InstructionFactory.createLoad(bufType, bufIndex));
		il.append(fc.createInvoke(ClassRepository.SynchronizerClassName, "notification", Type.VOID, new Type[] {
				Type.SHORT, bufType }, Constants.INVOKEVIRTUAL));
		hipGCC.verbose(tab + tab + "inserted notification invocation to method " + nc.getNotificationMethod().getId()
				+ ":" + nc.getCalledMethodName());
		if (hipGCC.debugCode())
			BCELUtils.appendFlag(320, il, cpg);

		/* finalize */
		mg.getInstructionList().insert(nc.getHandle(), il);
		il.dispose();
		BCELUtils.deleteInstruction(il, nc.getHandle(), start);
		hipGCC.verbose(tab + tab + "deleted the original call");

	}

	private static final class InvocationsInfo {

		final MethodGen mg;
		final ArrayList<RemoteCall> remoteCalls;
		final ArrayList<NotificationCall> notificationCalls;

		public InvocationsInfo(MethodGen mg, ArrayList<RemoteCall> remoteCalls,
				ArrayList<NotificationCall> notificationCalls) {
			this.mg = mg;
			this.remoteCalls = remoteCalls;
			this.notificationCalls = notificationCalls;
		}

	}
}
