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

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

public class NotificationMethod {

	private final Method method;
	private final int id;

	public NotificationMethod(Method method, int id) {
		this.method = method;
		this.id = id;
	}

	public static final String checkNotificationMethod(Method method) {
		if (!method.getReturnType().equals(Type.VOID)) {
			return "Notification method " + method.getName() + " has non-void return type";
		}
		return null;
	}

	public final Method getMethod() {
		return method;
	}

	public final int getId() {
		return id;
	}

	public final String getName() {
		return method.getName();
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean detail) {
		return method.getName() + (detail ? " (id=" + id + ")" : "");
	}
}
