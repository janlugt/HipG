/**
 * Copyright (c) 2010, 2011 Vrije Universiteit Amsterdam.
 * Written by Elzbieta Krepska, e.krepska@vu.nl.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.TargetLostException;
import org.apache.bcel.generic.Type;

public class SerializableRewriter implements Constants {

	public void process(JavaClass cl, ClassGen cg) throws ClassNotFoundException {

		final InstructionFactory fc = new InstructionFactory(cg);
		final ConstantPoolGen cpg = cg.getConstantPool();
		final String className = cl.getClassName();
		final InstructionList init = createStaticRegistration(cl, cg, fc);
		final Method clinit = getStaticInitializationMethod(cl);

		final MethodGen mg;

		if (clinit == null) {
			mg = new MethodGen(ACC_STATIC, Type.VOID, Type.NO_ARGS, new String[] {}, "<clinit>", className, init, cpg);
		} else {
			mg = new MethodGen(clinit, className, cpg);
			final InstructionList il = mg.getInstructionList();
			final InstructionHandle handles[] = il.getInstructionHandles();
			try {
				il.delete(handles[handles.length - 1]);
			} catch (TargetLostException e) {
				throw new RuntimeException("Cannot delete last instruction", e);
			}
			il.append(init);
		}

		mg.setMaxStack();
		mg.setMaxLocals();

		if (clinit == null) {
			cg.addMethod(mg.getMethod());
		} else {
			cg.replaceMethod(clinit, mg.getMethod());
		}
		mg.getInstructionList().dispose();
		init.dispose();
	}

	private Method getStaticInitializationMethod(JavaClass cl) {
		final Method[] methods = cl.getMethods();
		for (Method method : methods) {
			if (method.isStatic() && method.getName().equals("<clinit>") && method.getArgumentTypes().length == 0) {
				return method;
			}
		}
		return null;
	}

	private InstructionList createStaticRegistration(JavaClass cl, ClassGen cg, InstructionFactory fc) {

		final InstructionList il = new InstructionList();
		final ConstantPoolGen cpg = cg.getConstantPool();
		final String className = cl.getClassName();

		il.append(new PUSH(cpg, className));

		il.append(fc.createInvoke("java.lang.Class", "forName", new ObjectType("java.lang.Class"),
				new Type[] { Type.STRING }, Constants.INVOKESTATIC));
		il.append(fc.createInvoke(ClassRepository.IOUtilsClassName, "registerSerializable", Type.VOID,
				new Type[] { new ObjectType("java.lang.Class") }, Constants.INVOKESTATIC));
		il.append(InstructionFactory.createReturn(Type.VOID));

		return il;
	}

	public static boolean isSerializable(JavaClass cl) {
		return Serialization.implementsSerializable(cl);
	}

}
