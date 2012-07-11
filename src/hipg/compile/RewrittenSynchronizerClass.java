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

import java.util.ArrayList;

import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.InstructionHandle;

public class RewrittenSynchronizerClass {
	private ClassGen generatedClass;
	private ArrayList<ReduceMethod> reduceMethods;
	private ArrayList<NotificationMethod> notificationMethods;
	private ArrayList<MethodVariable> variables;
	private ArrayList<BlockingCall> blockingCalls;
	private ArrayList<NotificationCall> nonBlockingCalls;
	private ArrayList<InstructionHandle> returns;

	public RewrittenSynchronizerClass(ArrayList<ReduceMethod> reduceMethods,
			ArrayList<NotificationMethod> notificationMethods, ArrayList<MethodVariable> variables,
			ArrayList<BlockingCall> blockingCalls, ArrayList<NotificationCall> nonBlockingCalls,
			ArrayList<InstructionHandle> returns, ClassGen generatedClass) {
		this.reduceMethods = reduceMethods;
		this.notificationMethods = notificationMethods;
		this.variables = variables;
		this.blockingCalls = blockingCalls;
		this.nonBlockingCalls = nonBlockingCalls;
		this.returns = returns;
		this.generatedClass = generatedClass;
	}

	public ArrayList<ReduceMethod> getReduceMethods() {
		return reduceMethods;
	}

	public ArrayList<NotificationMethod> getNotificationMethods() {
		return notificationMethods;
	}

	public ArrayList<MethodVariable> getVariables() {
		return variables;
	}

	public ArrayList<BlockingCall> getBlockingCalls() {
		return blockingCalls;
	}

	public ArrayList<NotificationCall> getNonBlockingCalls() {
		return nonBlockingCalls;
	}

	public ArrayList<InstructionHandle> getReturns() {
		return returns;
	}

	public ClassGen getGeneratedClass() {
		return generatedClass;
	}

}
