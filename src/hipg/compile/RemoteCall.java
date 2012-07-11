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

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InvokeInstruction;

public class RemoteCall {

	private final ConstantPoolGen cpg;

	private final InvokeInstruction invokeRemoteCall;
	private final InstructionHandle invokeRemoteCallHandle;

	private final InvokeInstruction invokeCreator;
	private final InstructionHandle invokeCreatorHandle;

	private final boolean createdByGlobalNode;

	public RemoteCall(ConstantPoolGen cpg, InvokeInstruction invokeRemoteCall,
			InstructionHandle invokeRemoteCallHandle, InvokeInstruction invokeNeighbor,
			InstructionHandle invokeNeighborHandle, boolean createdByGlobalNode) {
		this.cpg = cpg;
		this.invokeRemoteCall = invokeRemoteCall;
		this.invokeRemoteCallHandle = invokeRemoteCallHandle;
		this.invokeCreator = invokeNeighbor;
		this.invokeCreatorHandle = invokeNeighborHandle;
		this.createdByGlobalNode = createdByGlobalNode;
	}

	public final boolean isCreatedByGlobalNode() {
		return createdByGlobalNode;
	}

	public final String getCalledMethodName() {
		return getInvokeRemoteCall().getMethodName(cpg);
	}

	public final InvokeInstruction getInvokeRemoteCall() {
		return invokeRemoteCall;
	}

	public final InstructionHandle getInvokeRemoteCallHandle() {
		return invokeRemoteCallHandle;
	}

	public final InvokeInstruction getInvokeCreator() {
		return invokeCreator;
	}

	public final InstructionHandle getInvokeCreatorHandle() {
		return invokeCreatorHandle;
	}

}
