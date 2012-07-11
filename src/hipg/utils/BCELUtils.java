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

import hipg.compile.BlockingCall;
import hipg.compile.MethodVariable;
import hipg.compile.NotificationCall;
import hipg.compile.NotificationMethod;
import hipg.compile.ReduceMethod;
import hipg.compile.RemoteCall;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariableTypeTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.CHECKCAST;
import org.apache.bcel.generic.CPInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.FieldOrMethod;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.TargetLostException;
import org.apache.bcel.generic.Type;

public class BCELUtils {

	public static JavaClass firstSuperClassWithAMethod(JavaClass cl, String methodName, Type returnType, Type[] args) {
		do {
			Method[] methods = cl.getMethods();
			for (Method method : methods) {
				if (!method.isAbstract() && method.getName().equals(methodName)
						&& method.getReturnType().equals(returnType)) {
					Type[] types = method.getArgumentTypes();
					int typeCount = (types == null ? 0 : types.length);
					int expectedTypeCount = (args == null ? 0 : args.length);
					if (typeCount == expectedTypeCount) {
						if (typeCount == 0) {
							return cl;
						}
						boolean typeOK = true;
						for (int i = 0; i < typeCount && typeOK; i++) {
							if (!types[i].equals(args[i])) {
								typeOK = false;
							}
						}
						if (typeOK) {
							return cl;
						}
					}

				}
			}
			try {
				cl = cl.getSuperClass();
			} catch (ClassNotFoundException e) {
				return null;
			}
		} while (cl != null);
		return null;
	}

	public static ArrayList<Method> getConstructors(ClassGen cg) {
		ArrayList<Method> constructors = new ArrayList<Method>();
		Method[] methods = cg.getMethods();
		for (Method method : methods) {
			if (method.getName().equals(Constants.CONSTRUCTOR_NAME)
					&& !method.getName().equals(Constants.STATIC_INITIALIZER_NAME) && !method.isAbstract()) {
				constructors.add(method);
			}
		}
		return constructors;
	}

	public static InstructionList createThrowRuntimeException(InstructionFactory fc, ConstantPoolGen cpg, String msg) {
		InstructionList il = new InstructionList();
		il.append(fc.createNew("java.lang.RuntimeException"));
		il.append(new DUP());
		il.append(new PUSH(cpg, msg));
		il.append(fc.createInvoke("java.lang.RuntimeException", "<init>", Type.VOID, new Type[] { Type.STRING },
				Constants.INVOKESPECIAL));
		il.append(InstructionConstants.ATHROW);
		return il;
	}

	public static InstructionList createThrowRuntimeException(InstructionFactory fc, ConstantPoolGen cpg, String msg,
			int varIndex) {
		InstructionList il = new InstructionList();
		il.append(fc.createNew("java.lang.RuntimeException"));
		il.append(new DUP());
		il.append(fc.createNew("java.lang.StringBuilder"));
		il.append(InstructionConstants.DUP);
		il.append(fc
				.createInvoke("java.lang.StringBuilder", "<init>", Type.VOID, Type.NO_ARGS, Constants.INVOKESPECIAL));
		il.append(new PUSH(cpg, msg));
		il.append(fc.createInvoke("java.lang.StringBuilder", "append", new ObjectType("java.lang.StringBuilder"),
				new Type[] { Type.STRING }, Constants.INVOKEVIRTUAL));
		il.append(InstructionFactory.createLoad(Type.INT, varIndex));
		il.append(fc.createInvoke("java.lang.StringBuilder", "append", new ObjectType("java.lang.StringBuilder"),
				new Type[] { Type.INT }, Constants.INVOKEVIRTUAL));
		il.append(fc.createInvoke("java.lang.StringBuilder", "toString", Type.STRING, Type.NO_ARGS,
				Constants.INVOKEVIRTUAL));
		il.append(fc.createInvoke("java.lang.RuntimeException", "<init>", Type.VOID, new Type[] { Type.STRING },
				Constants.INVOKESPECIAL));
		il.append(InstructionConstants.ATHROW);
		return il;
	}

	public static Method locateMethodCode(Method interfaceMethod, JavaClass clazz) throws ClassNotFoundException {
		Method[] methods = clazz.getMethods();
		for (Method method : methods) {
			if (method.getSignature().compareTo(interfaceMethod.getSignature()) == 0)
				return method;
		}
		if (clazz.equals(Object.class))
			return null;
		else
			return locateMethodCode(interfaceMethod, clazz.getSuperClass());
	}

	public static ArrayList<Method> getAllNodeMethods(JavaClass clazz, JavaClass InterfaceSuperClass,
			ArrayList<JavaClass> allInterfaces) {
		ArrayList<Method> list = new ArrayList<Method>();
		for (JavaClass inter : allInterfaces) {
			Method[] methods = inter.getMethods();
			for (Method m : methods)
				list.add(m);
		}
		return list;
	}

	public static InstructionHandle next(InstructionList il, InstructionHandle ih) {
		InstructionHandle[] handles = il.getInstructionHandles();
		for (int i = 0; i < handles.length; i++) {
			if (ih == handles[i]) {
				if (i + 1 == handles.length)
					return null;
				return handles[i + 1];
			}
		}
		return null;
	}

	public static InstructionList println(String out, String msg, ConstantPoolGen cpg, ClassGen cg,
			InstructionFactory fc) {
		return println(out, msg, cpg, cg, fc, (Instruction[]) null, (Type[]) null);
	}

	public static InstructionList println(String out, String msg, ConstantPoolGen cpg, ClassGen cg,
			InstructionFactory fc, Instruction pushParam) {
		return println(out, msg, cpg, cg, fc, new Instruction[] { pushParam }, new Type[] { Type.INT });
	}

	public static InstructionList println(String out, String msg, ConstantPoolGen cpg, ClassGen cg,
			InstructionFactory fc, Instruction pushParam, Type paramType) {
		return println(out, msg, cpg, cg, fc, new Instruction[] { pushParam }, new Type[] { paramType });
	}

	public static InstructionList println(String out, String msg, ConstantPoolGen cpg, ClassGen cg,
			InstructionFactory fc, Instruction[] pushParams, Type[] paramTypes) {
		if (!msg.endsWith(" "))
			msg = msg + " ";
		InstructionList il = new InstructionList();
		il.append(fc.createFieldAccess("java.lang.System", out, new ObjectType("java.io.PrintStream"),
				Constants.GETSTATIC));
		il.append(fc.createNew("java.lang.StringBuilder"));
		il.append(InstructionConstants.DUP);
		il.append(fc
				.createInvoke("java.lang.StringBuilder", "<init>", Type.VOID, Type.NO_ARGS, Constants.INVOKESPECIAL));

		il.append(new PUSH(cpg, "In "));
		il.append(fc.createInvoke("java.lang.StringBuilder", "append", new ObjectType("java.lang.StringBuilder"),
				new Type[] { Type.STRING }, Constants.INVOKEVIRTUAL));

		il.append(InstructionFactory.createThis());
		il.append(fc.createInvoke(cg.getClassName(), "toString", Type.STRING, Type.NO_ARGS, Constants.INVOKEVIRTUAL));
		il.append(fc.createInvoke("java.lang.StringBuilder", "append", new ObjectType("java.lang.StringBuilder"),
				new Type[] { Type.STRING }, Constants.INVOKEVIRTUAL));

		il.append(new PUSH(cpg, " "));
		il.append(fc.createInvoke("java.lang.StringBuilder", "append", new ObjectType("java.lang.StringBuilder"),
				new Type[] { Type.STRING }, Constants.INVOKEVIRTUAL));

		il.append(new PUSH(cpg, msg));
		il.append(fc.createInvoke("java.lang.StringBuilder", "append", new ObjectType("java.lang.StringBuilder"),
				new Type[] { Type.STRING }, Constants.INVOKEVIRTUAL));

		if (pushParams != null && pushParams.length > 0) {
			for (int i = 0; i < pushParams.length; i++) {
				il.append(pushParams[i]);
				il.append(fc.createInvoke("java.lang.StringBuilder", "append",
						new ObjectType("java.lang.StringBuilder"), new Type[] { paramTypes[i] },
						Constants.INVOKEVIRTUAL));
				il.append(new PUSH(cpg, " "));
				il.append(fc.createInvoke("java.lang.StringBuilder", "append",
						new ObjectType("java.lang.StringBuilder"), new Type[] { Type.STRING }, Constants.INVOKEVIRTUAL));
			}
		}
		il.append(fc.createInvoke("java.lang.StringBuilder", "toString", Type.STRING, Type.NO_ARGS,
				Constants.INVOKEVIRTUAL));
		il.append(fc.createInvoke("java.io.PrintStream", "println", Type.VOID, new Type[] { Type.STRING },
				Constants.INVOKEVIRTUAL));
		return il;
	}

	public static InstructionHandle appendFlag(int flag, InstructionList il, ConstantPoolGen cpg) {
		InstructionHandle ih = il.append(new PUSH(cpg, flag));
		for (int k = 0; k < 2; k++)
			il.append(new PUSH(cpg, flag));
		for (int k = 0; k < 3; k++)
			il.append(new POP());
		return ih;
	}

	public static InstructionHandle appendFlag(String flag, InstructionList il, ConstantPoolGen cpg) {
		InstructionHandle ih = il.append(new PUSH(cpg, flag));
		for (int k = 0; k < 2; k++)
			il.append(new PUSH(cpg, flag));
		for (int k = 0; k < 3; k++)
			il.append(new POP());
		return ih;
	}

	public static InstructionHandle appendErrorInCode(InstructionList il, ConstantPoolGen cpg) {
		InstructionHandle ih = il.append(new PUSH(cpg, 100));
		il.append(InstructionFactory.createStore(Type.OBJECT, 10));
		return ih;
	}

	public static void deleteInstruction(InstructionList il, InstructionHandle ih, InstructionHandle newIh) {
		InstructionTargeter[] targeters = ih.getTargeters();
		if (targeters != null && targeters.length > 0) {
			for (InstructionTargeter targeter : targeters) {
				targeter.updateTarget(ih, newIh);
			}
		}
		try {
			il.delete(ih);
		} catch (TargetLostException e) {
			throw new RuntimeException(e);
		}
	}

	public static String printList(final Iterator<?> iter) {
		if (iter == null)
			return "null";
		if (!iter.hasNext())
			return "[]";
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		boolean first = true;
		while (iter.hasNext()) {
			Object jc = iter.next();
			if (first)
				first = false;
			else
				sb.append(", ");
			if (jc == null)
				sb.append("null");
			final String name;
			if (jc instanceof Method) {
				name = ((Method) jc).getName();
			} else if (jc instanceof java.lang.reflect.Method) {
				name = ((java.lang.reflect.Method) jc).getName();
			} else if (jc instanceof ReduceMethod) {
				name = ((ReduceMethod) jc).getName();
			} else if (jc instanceof NotificationMethod) {
				name = ((NotificationMethod) jc).getName();
			} else if (jc instanceof BlockingCall) {
				name = ((BlockingCall) jc).getCalledMethodName();
			} else if (jc instanceof RemoteCall) {
				name = ((RemoteCall) jc).getCalledMethodName();
			} else if (jc instanceof NotificationCall) {
				name = ((NotificationCall) jc).getCalledMethodName();
			} else if (jc instanceof JavaClass) {
				name = ((JavaClass) jc).getClassName();
			} else if (jc instanceof ClassGen) {
				name = ((ClassGen) jc).getClassName();
			} else if (jc instanceof String) {
				name = ((String) jc);
			} else if (jc instanceof MethodVariable) {
				name = ((MethodVariable) jc).getName() + ":" + (((MethodVariable) jc).getType());
			} else {
				name = jc.toString();
			}
			sb.append(name);
		}
		sb.append("]");
		return sb.toString();
	}

	public static <T> ArrayList<T> arrayToList(T[] arr) {
		if (arr == null)
			return null;
		ArrayList<T> list = new ArrayList<T>();
		for (int i = 0; i < arr.length; i++)
			list.add(arr[i]);
		return list;
	}

	public void append(InstructionList main, InstructionList sublist) {
		main.append(sublist);
		sublist.dispose();
	}

	public static final LocalVariableTypeTable getLocalVariableTableType(Method m) {
		final Code code = m.getCode();
		if (code == null) {
			return null;
		}
		LocalVariableTypeTable t = getLocalVariableTableType(code.getAttributes());
		if (t == null) {
			t = getLocalVariableTableType(m.getAttributes());
		}
		return t;
	}

	public static final LocalVariableTypeTable getLocalVariableTableType(final Attribute[] attributes) {
		if (attributes != null) {
			for (Attribute attribute : attributes) {
				if (attribute != null) {
					if (attribute instanceof LocalVariableTypeTable) {
						return (LocalVariableTypeTable) attribute;
					}
				}
			}
		}
		return null;
	}

	//
	// FieldOrMethod
	//

	public static final String getSignature(FieldOrMethod o, ConstantPool cp) {
		ConstantCP cmr = (ConstantCP) cp.getConstant(o.getIndex());
		ConstantNameAndType cnat = (ConstantNameAndType) cp.getConstant(cmr.getNameAndTypeIndex());
		return ((ConstantUtf8) cp.getConstant(cnat.getSignatureIndex())).getBytes();
	}

	public static final String getName(FieldOrMethod o, ConstantPool cp) {
		ConstantCP cmr = (ConstantCP) cp.getConstant(o.getIndex());
		ConstantNameAndType cnat = (ConstantNameAndType) cp.getConstant(cmr.getNameAndTypeIndex());
		return ((ConstantUtf8) cp.getConstant(cnat.getNameIndex())).getBytes();
	}

	//
	// InvokeInstruction
	//

	public static final String getMethodName(InvokeInstruction o, ConstantPool cp) {
		return getName(o, cp);
	}

	public static final Type getReturnType(InvokeInstruction o, ConstantPool cp) {
		return Type.getReturnType(getSignature(o, cp));
	}

	public static final Type[] getArgumentTypes(InvokeInstruction o, ConstantPool cp) {
		// return o.getArgumentTypes(cp);
		return Type.getArgumentTypes(getSignature(o, cp));
	}

	public static ReferenceType getReferenceType(InvokeInstruction o, ConstantPool cp) {
		ConstantCP cmr = (ConstantCP) cp.getConstant(o.getIndex());
		String className = cp.getConstantString(cmr.getClassIndex(), org.apache.bcel.Constants.CONSTANT_Class);
		if (className.startsWith("[")) {
			return (ArrayType) Type.getType(className);
		} else {
			className = className.replace('/', '.');
			return new ObjectType(className);
		}
	}

	//
	// FieldInstruction
	//

	public static final Type getFieldType(FieldInstruction o, ConstantPool cp) {
		// return o.getFieldType(cp);
		return Type.getType(getSignature(o, cp));
	}

	public static final String getFieldName(FieldInstruction o, ConstantPool cp) {
		// return o.getFieldName(cp);
		return getName(o, cp);
	}

	//
	// CPInstruction
	//

	public static final Type getTypeCP(CPInstruction o, ConstantPool cp) {
		// return o.getType(cp);
		String name = cp.getConstantString(o.getIndex(), org.apache.bcel.Constants.CONSTANT_Class);
		if (!name.startsWith("[")) {
			name = "L" + name + ";";
		}
		return Type.getType(name);
	}

	public static final ObjectType getLoadClassType(CHECKCAST o, ConstantPool cp) {
		// return o.getLoadClassType(cp);
		Type t = getTypeCP(o, cp);
		if (t instanceof ArrayType) {
			t = ((ArrayType) t).getBasicType();
		}
		return (t instanceof ObjectType) ? (ObjectType) t : null;
	}

}
