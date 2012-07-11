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

import myutils.IOUtils;
import myutils.StringUtils;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DUP;
import org.apache.bcel.generic.DUP2_X1;
import org.apache.bcel.generic.IADD;
import org.apache.bcel.generic.ISHL;
import org.apache.bcel.generic.ISHR;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.NOP;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.POP;
import org.apache.bcel.generic.POP2;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.SWAP;
import org.apache.bcel.generic.Type;

public class Serialization {

	public final static Type ByteArrayType = new ArrayType(Type.BYTE, 1);

	public static boolean isSerializable(Type t) {
		return isPrimitive(t) || isString(t) || isArrayOfPrimitiveType(t) || implementsSerializable(t);
	}

	public static boolean isPrimitive(Type t) {
		return t.equals(Type.BOOLEAN) || t.equals(Type.SHORT) || t.equals(Type.BYTE) || t.equals(Type.INT)
				|| t.equals(Type.CHAR) || t.equals(Type.LONG) || t.equals(Type.FLOAT) || t.equals(Type.DOUBLE);
	}

	public static boolean isString(Type t) {
		return t.equals(Type.STRING)
				|| (t instanceof ObjectType && ((ObjectType) t).getClassName().equals(String.class.getName()));
	}

	public static boolean isArray(Type t) {
		return (t instanceof ArrayType);
	}

	public static boolean isArrayOfPrimitiveType(Type t) {
		if (t instanceof ArrayType) {
			ArrayType at = (ArrayType) t;
			return isPrimitive(at.getBasicType());
		}
		return false;
	}

	public static boolean isNullObject(Type t) {
		if (t instanceof ReferenceType) {
			ReferenceType rt = (ReferenceType) t;
			if (rt.getSignature().equals("<null object>"))
				return true;
		}
		return false;
	}

	public static void getRequiredBufferSizeToStoreType(final Type varType, final ClassGen cg,
			final InstructionFactory fc, final InstructionList il) {
		if (Serialization.isPrimitive(varType)) {
			il.append(new PUSH(cg.getConstantPool(), Serialization.staticTypeSizeInBytes(varType)));
		} else if (Serialization.isString(varType)) {
			il.append(InstructionFactory.createDup(varType.getSize()));
			Serialization.getStringLength(varType, cg, fc, il);
			Serialization.getNrOfBytesNeededToStoreString(varType, il, cg, fc);
		} else if (Serialization.isArrayOfPrimitiveType(varType)) {
			il.append(InstructionFactory.createDup(varType.getSize()));
			Serialization.getArrayLength(varType, cg, fc, il);
			Serialization.getNrOfBytesNeededToStorePrimitiveTypeArray(varType, il, cg, fc);
		} else if (Serialization.implementsSerializable(varType)) {
			il.append(InstructionFactory.createDup(varType.getSize()));
			Serialization.getSerializableLength(varType, cg, fc, il);
		} else {
			throw new RuntimeException(varType + " not serializable");
		}
	}

	public final static int staticTypeSizeInBytes(final Type t) {
		if (t.equals(Type.BOOLEAN) || t.equals(Type.BYTE))
			return 1;
		else if (t.equals(Type.SHORT) || t.equals(Type.CHAR))
			return 2;
		else if (t.equals(Type.INT) || t.equals(Type.FLOAT))
			return 4;
		else if (t.equals(Type.LONG) || t.equals(Type.DOUBLE))
			return 8;
		else
			throw new RuntimeException("Cannot determine static size of type " + t);
	}

	public final static int staticTypeSizeInBytesLog(final Type t) {
		return (int) Math.round(Math.log(staticTypeSizeInBytes(t)) / Math.log(2));
	}

	public final static boolean implementsSerializable(final Type t) {
		if (t instanceof ObjectType) {
			ObjectType ot = (ObjectType) t;
			JavaClass jc = ClassRepository.lookupClass(ot);
			return implementsSerializable(jc);
		}
		return false;
	}

	public final static boolean implementsSerializable(final JavaClass jc) {
		try {
			return jc.implementationOf(ClassRepository.getSerializableInterface());
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Cannot lookup Serializable class: " + e.getMessage(), e);
		}
	}

	public static void getStringLength(final Type t, final ClassGen cg, final InstructionFactory fc,
			final InstructionList il) {

		if (!isString(t)) {
			throw new RuntimeException(t + " is not a string");
		}

		ConstantPoolGen cpg = cg.getConstantPool();

		// assume on stack: string

		// (var.length()+1)/2 + 1
		il.append(new DUP());
		BranchInstruction ifnonull = InstructionFactory.createBranchInstruction(Constants.IFNONNULL, null);
		il.append(ifnonull);
		il.append(new POP());
		il.append(new PUSH(cpg, 0));
		BranchInstruction done = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(done);
		InstructionHandle getLen = il.append(fc.createInvoke(String.class.getName(), "length", Type.INT, Type.NO_ARGS,
				Constants.INVOKEVIRTUAL));
		InstructionHandle last = il.append(new NOP());

		ifnonull.setTarget(getLen);
		done.setTarget(last);

		// resulting stack: length
	}

	public static void getNrOfBytesNeededToStoreString(final Type t, final InstructionList il, final ClassGen cg,
			final InstructionFactory fc) {
		if (!isString(t)) {
			throw new RuntimeException(t + " is not a string");
		}
		ConstantPoolGen cpg = cg.getConstantPool();
		// stack: len
		il.append(new PUSH(cpg, 1));
		il.append(new ISHL());
		il.append(new PUSH(cpg, IOUtils.LENGTH_BYTES));
		il.append(new IADD());
		// stack: 2*len+4
	}

	private static void getArrayLength(final Type t, final ClassGen cg, final InstructionFactory fc,
			final InstructionList il) {

		if (!isArray(t)) {
			throw new RuntimeException(t + " is not an array");
		}

		ConstantPoolGen cpg = cg.getConstantPool();

		// assume stack = S array

		il.append(new DUP());
		BranchInstruction ifnonull = InstructionFactory.createBranchInstruction(Constants.IFNONNULL, null);
		il.append(ifnonull);
		il.append(new POP());
		il.append(new PUSH(cpg, 0));
		BranchInstruction done = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(done);
		InstructionHandle getLen = il.append(InstructionConstants.ARRAYLENGTH);
		InstructionHandle last = il.append(new NOP());

		ifnonull.setTarget(getLen);
		done.setTarget(last);

		// resulting stack = S len
	}

	public static void getNrOfBytesNeededToStorePrimitiveTypeArray(final Type t, final InstructionList il,
			final ClassGen cg, final InstructionFactory fc) {
		if (!isArrayOfPrimitiveType(t)) {
			throw new RuntimeException(t + " is not an array of primitive type");
		}
		final ConstantPoolGen cpg = cg.getConstantPool();
		// assume on stack: length
		final Type BasicType = ((ArrayType) t).getBasicType();
		if (BasicType.equals(Type.BOOLEAN)) {
			// (len+7)/8 + IOUtils.LENGTH_BYTES
			il.append(new PUSH(cpg, 7));
			il.append(new IADD());
			il.append(new PUSH(cpg, 3));
			il.append(new ISHR());
			il.append(new PUSH(cpg, IOUtils.LENGTH_BYTES));
			il.append(new IADD());
		} else {
			// 2+len*varSize
			int ts = staticTypeSizeInBytesLog(BasicType);
			if (ts > 0) {
				il.append(new PUSH(cpg, ts));
				il.append(new ISHL());
			}
			il.append(new PUSH(cpg, IOUtils.LENGTH_BYTES));
			il.append(new IADD());
		}
		// resulting stack: nrOfBytes
	}

	public static void getNrOfBytesNeededToStoreSerializable(final Type t, final InstructionList il, final ClassGen cg,
			final InstructionFactory fc) {
		if (!implementsSerializable(t)) {
			throw new RuntimeException(t + " is not serializable");
		}
		// assume on stack: length
	}

	private static void getSerializableLength(final Type t, final ClassGen cg, final InstructionFactory fc,
			final InstructionList il) {

		if (!implementsSerializable(t)) {
			throw new RuntimeException(t + " is not a serializable object");
		}

		ConstantPoolGen cpg = cg.getConstantPool();

		// assume stack = array

		il.append(new DUP());
		BranchInstruction ifnonull = InstructionFactory.createBranchInstruction(Constants.IFNONNULL, null);
		il.append(ifnonull);
		il.append(new POP());
		il.append(new PUSH(cpg, 0));
		BranchInstruction done = InstructionFactory.createBranchInstruction(Constants.GOTO, null);
		il.append(done);
		InstructionHandle getLen = il.append(fc.createInvoke(((ObjectType) t).getClassName(), "length", Type.INT,
				Type.NO_ARGS, Constants.INVOKEVIRTUAL));
		InstructionHandle last = il.append(new PUSH(cg.getConstantPool(), IOUtils.LENGTH_BYTES));
		il.append(new IADD());

		ifnonull.setTarget(getLen);
		done.setTarget(last);

		// resulting stack = len
	}

	private static String createTypeName(Type t) {
		Type b = t;
		if (isPrimitive(t)) {
			return StringUtils.UppercaseFirstLetter(Constants.TYPE_NAMES[b.getType()]);
		} else if (isArrayOfPrimitiveType(t)) {
			Type bt = ((ArrayType) t).getBasicType();
			return StringUtils.UppercaseFirstLetter(Constants.TYPE_NAMES[bt.getType()]) + "Array";
		} else if (isString(t)) {
			return "String";
		} else if (implementsSerializable(t)) {
			return "Serializable";
		} else {
			throw new RuntimeException("Unrecognized type " + t + " (" + (t == null ? "null" : t.getClass().getName())
					+ ")");
		}
	}

	public static int createTypeId(Type t) {
		if (isPrimitive(t)) {
			if (t.equals(Type.BOOLEAN)) {
				return 1;
			} else if (t.equals(Type.SHORT)) {
				return 2;
			} else if (t.equals(Type.BYTE)) {
				return 3;
			} else if (t.equals(Type.INT)) {
				return 4;
			} else if (t.equals(Type.CHAR)) {
				return 5;
			} else if (t.equals(Type.LONG)) {
				return 6;
			} else if (t.equals(Type.FLOAT)) {
				return 7;
			} else if (t.equals(Type.DOUBLE)) {
				return 8;
			} else {
				return 10;
			}
		} else if (isArrayOfPrimitiveType(t)) {
			ArrayType at = (ArrayType) t;
			return 20 + createTypeId(at.getBasicType());
		} else if (isString(t)) {
			return 11;
		} else if (implementsSerializable(t)) {
			return 12;
		} else {
			throw new RuntimeException("Unrecognized type " + t + " (" + (t == null ? "null" : t.getClass().getName())
					+ ")");
		}
	}

	private static Type createVarType(Type t) {
		if (isPrimitive(t) || isArray(t) || isString(t)) {
			return t;
		} else if (implementsSerializable(t)) {
			return ClassRepository.SerializableType;
		} else {
			throw new RuntimeException("Unrecognized type " + t + " (" + (t == null ? "null" : t.getClass().getName())
					+ ")");
		}
	}

	/**
	 * Creates code to write a variable to a byte queue. The queue is on stack. The variable is under the given index
	 * and with given type. The queue variable is consumed.
	 */
	public static void createWriteToQueueOnStack(final Type varType, final int varIndex, final InstructionList il,
			final InstructionFactory fc) {
		il.append(new DUP());
		il.append(InstructionFactory.createLoad(varType, varIndex));
		if (varType.getSize() == 1) {
			il.append(new SWAP());
		} else {
			il.append(new DUP2_X1());
			il.append(new POP2());
		}
		il.append(fc.createInvoke(ClassRepository.IOUtilsClassName, "write" + createTypeName(varType), Type.VOID,
				new Type[] { createVarType(varType), ClassRepository.BigQueueType }, Constants.INVOKESTATIC));
	}

	public static void createWriteToBufFromIndex(final Type varType, final int varIndex, final int bufIndex,
			final int positionIndex, final InstructionList il, final InstructionFactory fc, final ClassGen cg) {
		il.append(InstructionFactory.createLoad(varType, varIndex));
		createWriteToBufFromStack(varType, bufIndex, positionIndex, il, fc, cg);
	}

	public static void createWriteToBufFromStack(final Type varType, final int bufIndex, final int positionIndex,
			final InstructionList il, final InstructionFactory fc, final ClassGen cg) {
		if (!isPrimitive(varType)) {
			il.append(InstructionFactory.createDup(varType.getSize()));
		}
		// on stack: value to serialize
		il.append(InstructionFactory.createLoad(ByteArrayType, bufIndex));
		il.append(InstructionFactory.createLoad(Type.INT, positionIndex));
		il.append(fc.createInvoke(ClassRepository.IOUtilsClassName, "write" + createTypeName(varType), Type.VOID,
				new Type[] { createVarType(varType), ByteArrayType, Type.INT }, Constants.INVOKESTATIC));
		getRequiredBufferSizeToStoreType(varType, cg, fc, il);
		il.append(InstructionFactory.createLoad(Type.INT, positionIndex));
		il.append(new IADD());
		il.append(InstructionFactory.createStore(Type.INT, positionIndex));
		if (!isPrimitive(varType)) {
			il.append(InstructionFactory.createPop(varType.getSize()));
		}
	}

	public static void createReadFromBuf(final Type varType, final int bufIndex, final int positionIndex,
			final InstructionFactory fc, final ClassGen cg, final InstructionList il) {
		final ConstantPoolGen cpg = cg.getConstantPool();
		il.append(InstructionFactory.createLoad(ByteArrayType, bufIndex));
		il.append(InstructionFactory.createLoad(Type.INT, positionIndex));
		il.append(fc.createInvoke(ClassRepository.IOUtilsClassName, "read" + createTypeName(varType),
				createVarType(varType), new Type[] { ByteArrayType, Type.INT }, Constants.INVOKESTATIC));
		if (implementsSerializable(varType)) {
			il.append(fc.createCast(ClassRepository.SerializableType, varType));
		}
		// Update position.
		if (isPrimitive(varType)) {
			il.append(InstructionFactory.createLoad(Type.INT, positionIndex));
			il.append(new PUSH(cpg, staticTypeSizeInBytes(varType)));
			il.append(new IADD());
		} else {
			il.append(InstructionFactory.createDup(varType.getSize()));
			il.append(fc.createInvoke(ClassRepository.IOUtilsClassName, "bytes" + createTypeName(varType), Type.INT,
					new Type[] { createVarType(varType) }, Constants.INVOKESTATIC));
		}
		il.append(InstructionFactory.createStore(Type.INT, positionIndex));
	}

	public static void createReadFromQueue(final Type t, final int Qindex, final InstructionList il,
			final InstructionFactory fc) {
		il.append(InstructionFactory.createLoad(ClassRepository.BigQueueType, Qindex));
		il.append(fc.createInvoke(ClassRepository.IOUtilsClassName, "read" + createTypeName(t), createVarType(t),
				new Type[] { ClassRepository.BigQueueType }, Constants.INVOKESTATIC));
	}

	public static final void writeExplicitUserMessage(final byte[] buf, int offset, final int synchOwner,
			final int synchId, final short graphId, final short methodId, final int target) {
		IOUtils.write2Ints(synchOwner, synchId, buf, offset);
		offset += (IOUtils.INT_BYTES << 1);
		IOUtils.write2Shorts(graphId, methodId, buf, offset);
		offset += (IOUtils.SHORT_BYTES << 1);
		IOUtils.writeInt(target, buf, offset);
	}

	public static final void writeOnTheFlyUserMessage(final byte[] state, final byte[] buf, int offset,
			final int synchOwner, final int synchId, final short graphId, final short methodId) {
		IOUtils.write2Ints(synchOwner, synchId, buf, offset);
		offset += (IOUtils.INT_BYTES << 1);
		IOUtils.write2Shorts(graphId, methodId, buf, offset);
		offset += (IOUtils.SHORT_BYTES << 1);
		IOUtils.writeByteArray(state, buf, offset);
	}
}
