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

import java.io.PrintStream;
import java.util.ArrayList;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantDouble;
import org.apache.bcel.classfile.ConstantFloat;
import org.apache.bcel.classfile.ConstantInteger;
import org.apache.bcel.classfile.ConstantLong;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantString;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.verifier.exc.AssertionViolatedException;
import org.apache.bcel.verifier.structurals.UninitializedObjectType;

/**
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public class Simulator implements Visitor {

	/** Method to simulate. */
	protected final String methodName;
	/** Class from this method. */
	protected final String className;
	/** Instruction list. */
	protected final InstructionHandle[] list;
	/** Constant pool. */
	protected final ConstantPool cp;
	protected final ConstantPoolGen cpg;
	/** Current stack. */
	protected ArrayList<StackElement> currentStack;
	/** Current list of local variables. */
	protected StackElement[] currentLocals;
	/** Current instruction handle. */
	protected InstructionHandle currentIH = null;
	/** Branches tested so far. */
	private boolean[] scheduled;
	/** If verbose. */
	private final boolean verbose;

	public Simulator(final Method method, JavaClass clazz) {
		this(method.getName(), clazz.getClassName(), clazz.getConstantPool(), null, new InstructionList(method
				.getCode().getCode()).getInstructionHandles(), method.getCode().getMaxLocals(), method.isStatic(),
				method.getArgumentTypes(), false);
	}

	public Simulator(final MethodGen methodGen, ClassGen clazz) {
		this(methodGen.getName(), clazz.getClassName(), null, clazz.getConstantPool(), methodGen.getInstructionList()
				.getInstructionHandles(), methodGen.getMaxLocals(), methodGen.isStatic(), methodGen.getArgumentTypes(),
				false);
	}

	public Simulator(final String methodName, String className, ConstantPool cp, InstructionHandle handles[],
			final int maxLocals, final boolean isStatic, final Type[] argumentTypes) {
		this(methodName, className, cp, null, handles, maxLocals, isStatic, argumentTypes, false);
	}

	public Simulator(final String methodName, final String className, final ConstantPool cp, final ConstantPoolGen cpg,
			InstructionHandle handles[], int maxLocals, final boolean isStatic, final Type[] argumentTypes,
			boolean verbose) {
		this.methodName = methodName;
		this.className = className;
		this.list = handles;
		this.cp = cp;
		this.cpg = cpg;
		this.currentStack = null;
		this.currentLocals = null;
		initStack();
		initLocals(maxLocals, isStatic, argumentTypes);
		this.scheduled = new boolean[list.length];
		this.verbose = verbose;
		if (verbose)
			System.err.println("Simulator: start " + methodName + " in class " + className);
	}

	private void initStack() {
		currentStack = new ArrayList<StackElement>();
	}

	private void initLocals(int maxLocals, boolean isStatic, Type[] argumentTypes) {
		currentLocals = new StackElement[maxLocals];
		int k = 0;
		if (!isStatic) {
			ObjectType objType = new ObjectType(className);
			Type t = objType;
			if (methodName.equals(Constants.CONSTRUCTOR_NAME))
				t = new UninitializedObjectType(objType);
			setLocal(0, new StackElement(t, null).setInitial());
			k++;
		}
		if (argumentTypes != null) {
			for (int i = 0; i < argumentTypes.length; i++) {
				Type t = argumentTypes[i];
				if (t.equals(Type.BOOLEAN) || t.equals(Type.SHORT) || t.equals(Type.BYTE) || t.equals(Type.CHAR))
					t = Type.INT;
				setLocal(k, new StackElement(t, null).setInitial());
				k += argumentTypes[i].getSize();
			}
		}
	}

	protected ArrayList<StackElement> copyStack() {
		return copyStack(0);
	}

	protected ArrayList<StackElement> copyStack(int popArguments) {
		ArrayList<StackElement> stackCpy = new ArrayList<StackElement>();
		for (int i = 0; i < currentStack.size() - popArguments; i++)
			stackCpy.add(new StackElement(currentStack.get(i).getType(), currentStack.get(i).getCreator()));
		return stackCpy;
	}

	private StackElement[] copyLocals() {
		StackElement[] localsCpy = new StackElement[currentLocals.length];
		for (int i = 0; i < currentLocals.length; i++) {
			if (currentLocals[i] == null)
				localsCpy[i] = null;
			else
				localsCpy[i] = new StackElement(currentLocals[i].getType(), currentLocals[i].getCreator());
		}
		return localsCpy;
	}

	public void execute() {
		execute(0);
	}

	private void execute(int start) {
		while (start < list.length) {
			if (scheduled[start])
				return;
			scheduled[start] = true;
			currentIH = list[start];

			processBefore(currentIH);
			currentIH.getInstruction().accept(this);

			if (verbose)
				print(System.err);

			processAfter(currentIH);

			if (currentIH.getInstruction() instanceof BranchInstruction) {
				BranchInstruction branchInstr = (BranchInstruction) currentIH.getInstruction();
				InstructionHandle target = branchInstr.getTarget();
				int branch = -1;
				for (int s = 0; s < list.length; s++) {
					if (list[s].equals(target)) {
						branch = s;
						break;
					}
				}
				if (branch < 0 || branch >= list.length) {
					if (target.getInstruction() instanceof ReturnInstruction)
						return;
					throw new RuntimeException("jump to brach " + branch + " while only " + list.length
							+ " instructions");
				}
				if (branchInstr instanceof GotoInstruction) {
					start = branch;
				} else {
					boolean scheduledNextStart = scheduled[start + 1];
					boolean scheduledBranch = scheduled[branch];

					if (scheduledNextStart && scheduledBranch)
						return;
					else if (!scheduledNextStart && scheduledBranch) {
						start++;
					} else if (scheduledNextStart && !scheduledBranch) {
						start = branch;
					} else {
						ArrayList<StackElement> stackCopy = copyStack();
						StackElement[] localsCopy = copyLocals();
						execute(branch);
						currentLocals = localsCopy;
						currentStack = stackCopy;
						start++;
					}
				}
			} else if (currentIH.getInstruction() instanceof ReturnInstruction) {
				return;
			} else {
				start++;
			}
		}
	}

	private void print(PrintStream out) {
		out.print("         ");
		out.print(currentIH);
		if (currentIH.getInstruction() instanceof InvokeInstruction) {
			InvokeInstruction invoke = (InvokeInstruction) currentIH.getInstruction();
			out.print(" {" + getMethodName(invoke) + "}");
		} else if (currentIH.getInstruction() instanceof FieldInstruction) {
			FieldInstruction field = (FieldInstruction) currentIH.getInstruction();
			out.print(" {" + getFieldName(field) + "}");
		} else if (currentIH.getInstruction() instanceof CHECKCAST) {
			CHECKCAST check = (CHECKCAST) currentIH.getInstruction();
			out.print(" {" + getLoadClassType(check) + "}");
		}
		out.print(", stack: [");
		for (int i = 0; i < currentStack.size(); i++) {
			StackElement st = currentStack.get(i);
			out.print(st + " ");
		}
		out.print("], locals: ");
		for (int i = 0; i < currentLocals.length; i++) {
			StackElement st = currentLocals[i];
			if (st != null) {
				out.print(i + "->" + st + " ");
			}
		}
		out.println();
	}

	public void processBefore(InstructionHandle ih) {
	}

	public void processAfter(InstructionHandle ih) {
	}

	private StackElement getLocal(int i) {
		StackElement st = currentLocals[i];
		if (st == null)
			throw new RuntimeException("getting a local element at index " + i + " but the result is null");
		return st;
	}

	private void setLocal(int i, StackElement el) {
		// System.err.println("setting local at index " + i + " to " + el);
		Type type = el.getType();
		if (type == Type.BYTE || type == Type.SHORT || type == Type.BOOLEAN || type == Type.CHAR) {
			throw new AssertionViolatedException("LocalVariables do not know about '" + type
					+ "'. Use Type.INT instead.");
		}
		currentLocals[i] = el;
	}

	protected StackElement peek(int i) {
		return currentStack.get(currentStack.size() - 1 - i);
	}

	private StackElement pop() {
		return currentStack.remove(currentStack.size() - 1);
	}

	private void push(Type t) {
		currentStack.add(new StackElement(t, currentIH));
	}

	private void push(Type t, InstructionHandle creator) {
		currentStack.add(new StackElement(t, creator == null ? currentIH : creator));
	}

	private void push(StackElement t) {
		currentStack.add(t.setCreator(currentIH));
	}

	private void pushDup(StackElement t) {
		StackElement top = (currentStack.size() > 0 ? peek(0) : null);
		currentStack.add(t);
		if (top != null) {
			t.setCreator(top.getCreator());
		}
	}

	private void pushOrig(StackElement t) {
		currentStack.add(t);
	}

	private void initialize(StackElement UninitializedObject) {
		UninitializedObjectType u = (UninitializedObjectType) UninitializedObject.getType();
		for (int i = 0; i < currentStack.size(); i++) {
			if (currentStack.get(i).getType() == UninitializedObject.getType()) {
				currentStack.get(i).setType(u.getInitialized());
			}
		}
		for (int i = 0; i < currentLocals.length; i++) {
			if (currentLocals[i] != null && currentLocals[i].getType() == UninitializedObject.getType()) {
				currentLocals[i].setType(u.getInitialized());
			}
		}
	}

	public void visitAALOAD(AALOAD o) {
		pop();
		StackElement arrel = pop();
		Type t = arrel.getType();
		if (t == Type.NULL) {
			push(Type.NULL);
		} else {
			ArrayType at = (ArrayType) t;
			push(at.getElementType(), arrel.getCreator());
		}
	}

	public void visitAASTORE(AASTORE o) {
		pop();
		pop();
		pop();
	}

	public void visitACONST_NULL(ACONST_NULL o) {
		push(Type.NULL);
	}

	public void visitALOAD(ALOAD o) {
		push(getLocal(o.getIndex()));
	}

	public void visitANEWARRAY(ANEWARRAY o) {
		pop();
		push(new ArrayType(getTypeCP(o), 1));
	}

	public void visitARETURN(ARETURN o) {
		pop();
	}

	public void visitARRAYLENGTH(ARRAYLENGTH o) {
		pop();
		push(Type.INT);
	}

	public void visitASTORE(ASTORE o) {
		setLocal(o.getIndex(), pop().setCreator(currentIH));
	}

	public void visitATHROW(ATHROW o) {
		StackElement t = pop();
		currentStack.clear();
		if (t.equals(Type.NULL))
			push(Type.getType("Ljava/lang/NullPointerException;"));
		else
			push(t);
	}

	public void visitBALOAD(BALOAD o) {
		pop();
		StackElement arrel = pop();
		push(Type.INT, arrel.getCreator());
	}

	public void visitBASTORE(BASTORE o) {
		pop();
		pop();
		pop();
	}

	public void visitBIPUSH(BIPUSH o) {
		push(Type.INT);
	}

	public void visitCALOAD(CALOAD o) {
		pop();
		StackElement arrel = pop();
		push(Type.INT, arrel.getCreator());
	}

	public void visitCASTORE(CASTORE o) {
		pop();
		pop();
		pop();
	}

	public void visitCHECKCAST(CHECKCAST o) {
		StackElement e = pop();
		pushOrig(e.setType(getTypeCP(o)));
	}

	public void visitD2F(D2F o) {
		pop();
		push(Type.FLOAT);
	}

	public void visitD2I(D2I o) {
		pop();
		push(Type.INT);
	}

	public void visitD2L(D2L o) {
		pop();
		push(Type.LONG);
	}

	public void visitDADD(DADD o) {
		pop();
		pop();
		push(Type.DOUBLE);
	}

	public void visitDALOAD(DALOAD o) {
		pop();
		StackElement arrel = pop();
		push(Type.DOUBLE, arrel.getCreator());
	}

	public void visitDASTORE(DASTORE o) {
		pop();
		pop();
		pop();
	}

	public void visitDCMPG(DCMPG o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitDCMPL(DCMPL o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitDCONST(DCONST o) {
		push(Type.DOUBLE);
	}

	public void visitDDIV(DDIV o) {
		pop();
		pop();
		push(Type.DOUBLE);
	}

	public void visitDLOAD(DLOAD o) {
		push(getLocal(o.getIndex()));
	}

	public void visitDMUL(DMUL o) {
		pop();
		pop();
		push(Type.DOUBLE);
	}

	public void visitDNEG(DNEG o) {
		pop();
		push(Type.DOUBLE);
	}

	public void visitDREM(DREM o) {
		pop();
		pop();
		push(Type.DOUBLE);
	}

	public void visitDRETURN(DRETURN o) {
		pop();
	}

	public void visitDSTORE(DSTORE o) {
		setLocal(o.getIndex(), pop().setCreator(currentIH));
		setLocal(o.getIndex() + 1, new StackElement(Type.UNKNOWN, currentIH));
	}

	public void visitDSUB(DSUB o) {
		pop();
		pop();
		push(Type.DOUBLE);
	}

	public void visitDUP(DUP o) {
		StackElement t = pop();
		pushDup(t);
		pushDup(t);
	}

	public void visitDUP_X1(DUP_X1 o) {
		StackElement w1 = pop();
		StackElement w2 = pop();
		pushDup(w1);
		pushDup(w2);
		pushDup(w1);
	}

	public void visitDUP_X2(DUP_X2 o) {
		StackElement w1 = pop();
		StackElement w2 = pop();
		if (w2.getType().getSize() == 2) {
			pushDup(w1);
			pushDup(w2);
			pushDup(w1);
		} else {
			StackElement w3 = pop();
			pushDup(w1);
			pushDup(w3);
			pushDup(w2);
			pushDup(w1);
		}
	}

	public void visitDUP2(DUP2 o) {
		StackElement t = pop();
		if (t.getType().getSize() == 2) {
			pushDup(t);
			pushDup(t);
		} else {
			StackElement u = pop();
			pushDup(u);
			pushDup(t);
			pushDup(u);
			pushDup(t);
		}
	}

	public void visitDUP2_X1(DUP2_X1 o) {
		StackElement t = pop();
		if (t.getType().getSize() == 2) {
			StackElement u = pop();
			pushDup(t);
			pushDup(u);
			pushDup(t);
		} else {
			StackElement u = pop();
			StackElement v = pop();
			pushDup(u);
			pushDup(t);
			pushDup(v);
			pushDup(u);
			pushDup(t);
		}
	}

	public void visitDUP2_X2(DUP2_X2 o) {
		StackElement t = pop();
		if (t.getType().getSize() == 2) {
			StackElement u = pop();
			if (u.getType().getSize() == 2) {
				pushDup(t);
				pushDup(u);
				pushDup(t);
			} else {
				StackElement v = pop();
				pushDup(t);
				pushDup(v);
				pushDup(u);
				pushDup(t);
			}
		} else {
			StackElement u = pop();
			StackElement v = pop();
			if (v.getType().getSize() == 2) {
				pushDup(u);
				pushDup(t);
				pushDup(v);
				pushDup(u);
				pushDup(t);
			} else {
				StackElement w = pop();
				pushDup(u);
				pushDup(t);
				pushDup(w);
				pushDup(v);
				pushDup(u);
				pushDup(t);
			}
		}
	}

	public void visitF2D(F2D o) {
		pop();
		push(Type.DOUBLE);
	}

	public void visitF2I(F2I o) {
		pop();
		push(Type.INT);
	}

	public void visitF2L(F2L o) {
		pop();
		push(Type.LONG);
	}

	public void visitFADD(FADD o) {
		pop();
		pop();
		push(Type.FLOAT);
	}

	public void visitFALOAD(FALOAD o) {
		pop();
		StackElement arrel = pop();
		push(Type.FLOAT, arrel.getCreator());
	}

	public void visitFASTORE(FASTORE o) {
		pop();
		pop();
		pop();
	}

	public void visitFCMPG(FCMPG o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitFCMPL(FCMPL o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitFCONST(FCONST o) {
		push(Type.FLOAT);
	}

	public void visitFDIV(FDIV o) {
		pop();
		pop();
		push(Type.FLOAT);
	}

	public void visitFLOAD(FLOAD o) {
		push(getLocal(o.getIndex()));
	}

	public void visitFMUL(FMUL o) {
		pop();
		pop();
		push(Type.FLOAT);
	}

	public void visitFNEG(FNEG o) {
		pop();
		push(Type.FLOAT);
	}

	public void visitFREM(FREM o) {
		pop();
		pop();
		push(Type.FLOAT);
	}

	public void visitFRETURN(FRETURN o) {
		pop();
	}

	public void visitFSTORE(FSTORE o) {
		setLocal(o.getIndex(), pop().setCreator(currentIH));
	}

	public void visitFSUB(FSUB o) {
		pop();
		pop();
		push(Type.FLOAT);
	}

	public void visitGETFIELD(GETFIELD o) {
		pop();
		Type t = getFieldType(o);
		if (t.equals(Type.BOOLEAN) || t.equals(Type.CHAR) || t.equals(Type.BYTE) || t.equals(Type.SHORT))
			t = Type.INT;
		push(t);
	}

	public void visitGETSTATIC(GETSTATIC o) {
		Type t = getFieldType(o);
		if (t.equals(Type.BOOLEAN) || t.equals(Type.CHAR) || t.equals(Type.BYTE) || t.equals(Type.SHORT))
			t = Type.INT;
		push(t);
	}

	public void visitGOTO(GOTO o) {
	}

	public void visitGOTO_W(GOTO_W o) {
	}

	public void visitI2B(I2B o) {
		pop();
		push(Type.INT);
	}

	public void visitI2C(I2C o) {
		pop();
		push(Type.INT);
	}

	public void visitI2D(I2D o) {
		pop();
		push(Type.DOUBLE);
	}

	public void visitI2F(I2F o) {
		pop();
		push(Type.FLOAT);
	}

	public void visitI2L(I2L o) {
		pop();
		push(Type.LONG);
	}

	public void visitI2S(I2S o) {
		pop();
		push(Type.INT);
	}

	public void visitIADD(IADD o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitIALOAD(IALOAD o) {
		pop();
		StackElement arrel = pop();
		push(Type.INT, arrel.getCreator());
	}

	public void visitIAND(IAND o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitIASTORE(IASTORE o) {
		pop();
		pop();
		pop();
	}

	public void visitICONST(ICONST o) {
		push(Type.INT);
	}

	public void visitIDIV(IDIV o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitIF_ACMPEQ(IF_ACMPEQ o) {
		pop();
		pop();
	}

	public void visitIF_ACMPNE(IF_ACMPNE o) {
		pop();
		pop();
	}

	public void visitIF_ICMPEQ(IF_ICMPEQ o) {
		pop();
		pop();
	}

	public void visitIF_ICMPGE(IF_ICMPGE o) {
		pop();
		pop();
	}

	public void visitIF_ICMPGT(IF_ICMPGT o) {
		pop();
		pop();
	}

	public void visitIF_ICMPLE(IF_ICMPLE o) {
		pop();
		pop();
	}

	public void visitIF_ICMPLT(IF_ICMPLT o) {
		pop();
		pop();
	}

	public void visitIF_ICMPNE(IF_ICMPNE o) {
		pop();
		pop();
	}

	public void visitIFEQ(IFEQ o) {
		pop();
	}

	public void visitIFGE(IFGE o) {
		pop();
	}

	public void visitIFGT(IFGT o) {
		pop();
	}

	public void visitIFLE(IFLE o) {
		pop();
	}

	public void visitIFLT(IFLT o) {
		pop();
	}

	public void visitIFNE(IFNE o) {
		pop();
	}

	public void visitIFNONNULL(IFNONNULL o) {
		pop();
	}

	public void visitIFNULL(IFNULL o) {
		pop();
	}

	public void visitIINC(IINC o) {
	}

	public void visitILOAD(ILOAD o) {
		push(getLocal(o.getIndex()));
	}

	public void visitIMUL(IMUL o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitINEG(INEG o) {
		pop();
		push(Type.INT);
	}

	public void visitINSTANCEOF(INSTANCEOF o) {
		pop();
		push(Type.INT);
	}

	public void visitINVOKEINTERFACE(INVOKEINTERFACE o) {
		pop();
		for (int i = 0; i < getArgumentTypes(o).length; i++) {
			pop();
		}
		if (getReturnType(o) != Type.VOID) {
			Type t = getReturnType(o);
			if (t.equals(Type.BOOLEAN) || t.equals(Type.CHAR) || t.equals(Type.BYTE) || t.equals(Type.SHORT))
				t = Type.INT;
			push(t);
		}
	}

	public void visitINVOKESPECIAL(INVOKESPECIAL o) {
		if (getMethodName(o).equals(Constants.CONSTRUCTOR_NAME)) {
			StackElement UninitializedObject = peek(getArgumentTypes(o).length);
			initialize(UninitializedObject);
		}
		pop();
		for (int i = 0; i < getArgumentTypes(o).length; i++) {
			pop();
		}
		if (getReturnType(o) != Type.VOID) {
			Type t = getReturnType(o);
			if (t.equals(Type.BOOLEAN) || t.equals(Type.CHAR) || t.equals(Type.BYTE) || t.equals(Type.SHORT))
				t = Type.INT;
			push(t);
		}
	}

	public void visitINVOKESTATIC(INVOKESTATIC o) {
		for (int i = 0; i < getArgumentTypes(o).length; i++) {
			pop();
		}
		if (getReturnType(o) != Type.VOID) {
			Type t = getReturnType(o);
			if (t.equals(Type.BOOLEAN) || t.equals(Type.CHAR) || t.equals(Type.BYTE) || t.equals(Type.SHORT))
				t = Type.INT;
			push(t);
		}
	}

	public void visitINVOKEVIRTUAL(INVOKEVIRTUAL o) {
		pop();
		for (int i = 0; i < getArgumentTypes(o).length; i++) {
			pop();
		}
		if (getReturnType(o) != Type.VOID) {
			Type t = getReturnType(o);
			if (t.equals(Type.BOOLEAN) || t.equals(Type.CHAR) || t.equals(Type.BYTE) || t.equals(Type.SHORT))
				t = Type.INT;
			push(t);
		}
	}

	public void visitIOR(IOR o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitIREM(IREM o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitIRETURN(IRETURN o) {
		pop();
	}

	public void visitISHL(ISHL o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitISHR(ISHR o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitISTORE(ISTORE o) {
		setLocal(o.getIndex(), pop().setCreator(currentIH));
	}

	public void visitISUB(ISUB o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitIUSHR(IUSHR o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitIXOR(IXOR o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitJSR(JSR o) {
		push(new ReturnaddressType(o.physicalSuccessor()));
	}

	public void visitJSR_W(JSR_W o) {
		push(new ReturnaddressType(o.physicalSuccessor()));
	}

	public void visitL2D(L2D o) {
		pop();
		push(Type.DOUBLE);
	}

	public void visitL2F(L2F o) {
		pop();
		push(Type.FLOAT);
	}

	public void visitL2I(L2I o) {
		pop();
		push(Type.INT);
	}

	public void visitLADD(LADD o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLALOAD(LALOAD o) {
		pop();
		StackElement arrel = pop();
		push(Type.LONG, arrel.getCreator());
	}

	public void visitLAND(LAND o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLASTORE(LASTORE o) {
		pop();
		pop();
		pop();
	}

	public void visitLCMP(LCMP o) {
		pop();
		pop();
		push(Type.INT);
	}

	public void visitLCONST(LCONST o) {
		push(Type.LONG);
	}

	public void visitLDC(LDC o) {
		Constant c = getConstant(o.getIndex());
		if (c instanceof ConstantInteger) {
			push(Type.INT);
		}
		if (c instanceof ConstantFloat) {
			push(Type.FLOAT);
		}
		if (c instanceof ConstantString) {
			push(Type.STRING);
		}
	}

	public void visitLDC_W(LDC_W o) {
		Constant c = getConstant(o.getIndex());
		if (c instanceof ConstantInteger) {
			push(Type.INT);
		}
		if (c instanceof ConstantFloat) {
			push(Type.FLOAT);
		}
		if (c instanceof ConstantString) {
			push(Type.STRING);
		}
	}

	public void visitLDC2_W(LDC2_W o) {
		Constant c = getConstant(o.getIndex());
		if (c instanceof ConstantLong) {
			push(Type.LONG);
		}
		if (c instanceof ConstantDouble) {
			push(Type.DOUBLE);
		}
	}

	public void visitLDIV(LDIV o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLLOAD(LLOAD o) {
		push(getLocal(o.getIndex()));
	}

	public void visitLMUL(LMUL o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLNEG(LNEG o) {
		pop();
		push(Type.LONG);
	}

	public void visitLOOKUPSWITCH(LOOKUPSWITCH o) {
		pop();
	}

	public void visitLOR(LOR o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLREM(LREM o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLRETURN(LRETURN o) {
		pop();
	}

	public void visitLSHL(LSHL o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLSHR(LSHR o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLSTORE(LSTORE o) {
		setLocal(o.getIndex(), pop().setCreator(currentIH));
		setLocal(o.getIndex() + 1, new StackElement(Type.UNKNOWN, currentIH));
	}

	public void visitLSUB(LSUB o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLUSHR(LUSHR o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitLXOR(LXOR o) {
		pop();
		pop();
		push(Type.LONG);
	}

	public void visitMONITORENTER(MONITORENTER o) {
		pop();
	}

	public void visitMONITOREXIT(MONITOREXIT o) {
		pop();
	}

	public void visitMULTIANEWARRAY(MULTIANEWARRAY o) {
		for (int i = 0; i < o.getDimensions(); i++) {
			pop();
		}
		push(getTypeCP(o));
	}

	public void visitNEW(NEW o) {
		push(new UninitializedObjectType((ObjectType) (getTypeCP(o))));
	}

	public void visitNEWARRAY(NEWARRAY o) {
		pop();
		push(o.getType());
	}

	public void visitNOP(NOP o) {
	}

	public void visitPOP(POP o) {
		pop();
	}

	public void visitPOP2(POP2 o) {
		Type t = pop().getType();
		if (t.getSize() == 1) {
			pop();
		}
	}

	public void visitPUTFIELD(PUTFIELD o) {
		pop();
		pop();
	}

	public void visitPUTSTATIC(PUTSTATIC o) {
		pop();
	}

	public void visitRET(RET o) {
	}

	public void visitRETURN(RETURN o) {
	}

	public void visitSALOAD(SALOAD o) {
		pop();
		StackElement arrel = pop();
		push(Type.INT, arrel.getCreator());
	}

	public void visitSASTORE(SASTORE o) {
		pop();
		pop();
		pop();
	}

	public void visitSIPUSH(SIPUSH o) {
		push(Type.INT);
	}

	public void visitSWAP(SWAP o) {
		StackElement t = pop();
		StackElement u = pop();
		pushOrig(t);
		pushOrig(u);
	}

	public void visitTABLESWITCH(TABLESWITCH o) {
		pop();
	}

	@Override
	public void visitAllocationInstruction(AllocationInstruction obj) {
	}

	@Override
	public void visitArithmeticInstruction(ArithmeticInstruction obj) {
	}

	@Override
	public void visitArrayInstruction(ArrayInstruction obj) {
	}

	@Override
	public void visitBREAKPOINT(BREAKPOINT obj) {
	}

	@Override
	public void visitBranchInstruction(BranchInstruction obj) {
	}

	@Override
	public void visitCPInstruction(CPInstruction obj) {
	}

	@Override
	public void visitConstantPushInstruction(ConstantPushInstruction obj) {
	}

	@Override
	public void visitConversionInstruction(ConversionInstruction obj) {
	}

	@Override
	public void visitExceptionThrower(ExceptionThrower obj) {
	}

	@Override
	public void visitFieldInstruction(FieldInstruction obj) {
	}

	@Override
	public void visitFieldOrMethod(FieldOrMethod obj) {
	}

	@Override
	public void visitGotoInstruction(GotoInstruction obj) {
	}

	@Override
	public void visitIMPDEP1(IMPDEP1 obj) {
	}

	@Override
	public void visitIMPDEP2(IMPDEP2 obj) {
	}

	@Override
	public void visitIfInstruction(IfInstruction obj) {
	}

	@Override
	public void visitInvokeInstruction(InvokeInstruction invoke) {
	}

	@Override
	public void visitJsrInstruction(JsrInstruction obj) {
	}

	@Override
	public void visitLoadClass(LoadClass obj) {
	}

	@Override
	public void visitLoadInstruction(LoadInstruction obj) {
	}

	@Override
	public void visitLocalVariableInstruction(LocalVariableInstruction obj) {
	}

	@Override
	public void visitPopInstruction(PopInstruction obj) {
	}

	@Override
	public void visitPushInstruction(PushInstruction obj) {
	}

	@Override
	public void visitReturnInstruction(ReturnInstruction obj) {
	}

	@Override
	public void visitSelect(Select obj) {
	}

	@Override
	public void visitStackConsumer(StackConsumer obj) {
	}

	@Override
	public void visitStackInstruction(StackInstruction obj) {
	}

	@Override
	public void visitStackProducer(StackProducer obj) {
	}

	@Override
	public void visitStoreInstruction(StoreInstruction obj) {
	}

	@Override
	public void visitTypedInstruction(TypedInstruction obj) {
	}

	@Override
	public void visitUnconditionalBranch(UnconditionalBranch obj) {
	}

	@Override
	public void visitVariableLengthInstruction(VariableLengthInstruction obj) {
	}

	public static final class StackElement {
		private Type type;
		private InstructionHandle creator;
		private boolean init;

		public StackElement(Type t, InstructionHandle creator) {
			this.type = t;
			this.creator = creator;
		}

		public StackElement setInitial() {
			init = true;
			return this;
		}

		public boolean isInitial() {
			return init;
		}

		public InstructionHandle getCreator() {
			return creator;
		}

		public Type getType() {
			return type;
		}

		public StackElement setCreator(InstructionHandle c) {
			this.creator = c;
			return this;
		}

		public StackElement setType(Type t) {
			this.type = t;
			return this;
		}

		@Override
		public String toString() {
			String typeStr = null;
			if (type instanceof ObjectType) {
				ObjectType objType = (ObjectType) type;
				String className = objType.getClassName();
				typeStr = className.substring(className.lastIndexOf('.') + 1);
			} else if (type instanceof UninitializedObjectType) {
				UninitializedObjectType u = (UninitializedObjectType) type;
				Type subType = u.getInitialized();
				String subTypeStr = null;
				if (subType instanceof ObjectType) {
					ObjectType objSubType = (ObjectType) subType;
					String subClassName = objSubType.getClassName();
					subTypeStr = subClassName.substring(subClassName.lastIndexOf('.') + 1);
				} else {
					subTypeStr = subType.toString();
				}
				typeStr = "Uninitialized " + subTypeStr;
			} else {
				typeStr = type.toString();
			}
			return typeStr + "@" + (creator == null ? "null" : creator.getPosition());
		}
	}

	public final String getSignature(FieldOrMethod o) {
		if (cpg != null)
			return o.getSignature(cpg);
		else
			return BCELUtils.getSignature(o, cp);
	}

	public final String getName(FieldOrMethod o) {
		if (cpg != null)
			return o.getName(cpg);
		else
			return BCELUtils.getName(o, cp);
	}

	public final String getMethodName(InvokeInstruction o) {
		if (cpg != null)
			return o.getMethodName(cpg);
		else
			return BCELUtils.getName(o, cp);
	}

	public final Type getReturnType(InvokeInstruction o) {
		if (cpg != null)
			return o.getReturnType(cpg);
		else
			return BCELUtils.getReturnType(o, cp);
	}

	public final Type[] getArgumentTypes(InvokeInstruction o) {
		if (cpg != null)
			return o.getArgumentTypes(cpg);
		else
			return BCELUtils.getArgumentTypes(o, cp);
	}

	public ReferenceType getReferenceType(InvokeInstruction o) {
		if (cpg != null)
			return o.getReferenceType(cpg);
		else
			return BCELUtils.getReferenceType(o, cp);
	}

	public final Type getFieldType(FieldInstruction o) {
		if (cpg != null)
			return o.getFieldType(cpg);
		else
			return BCELUtils.getFieldType(o, cp);
	}

	public final String getFieldName(FieldInstruction o) {
		if (cpg != null)
			return o.getFieldName(cpg);
		else
			return BCELUtils.getFieldName(o, cp);
	}

	public final Type getTypeCP(CPInstruction o) {
		if (cpg != null)
			return o.getType(cpg);
		else
			return BCELUtils.getTypeCP(o, cp);
	}

	public final ObjectType getLoadClassType(CHECKCAST o) {
		if (cpg != null)
			return o.getLoadClassType(cpg);
		else
			return BCELUtils.getLoadClassType(o, cp);
	}

	public final Constant getConstant(int index) {
		if (cpg != null)
			return cpg.getConstant(index);
		else
			return cp.getConstant(index);
	}
}
