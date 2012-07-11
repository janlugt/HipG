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

package hipg.test;

import hipg.Config;
import hipg.Notification;
import hipg.format.GraphCreationException;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

public class TestNotification {

	public static class MySynchronizer extends Synchronizer {

		private boolean stopped = false;
		private int notification = -1;

		public MySynchronizer() {
		}

		@Notification
		public void Stop() {
			stopped = true;
		}

		@Notification
		public void Notify1(int s) {
			notification = s;
		}

		@Notification
		public void Notify2(long s) {
		}

		@Notification
		public void Notify3(char s) {
		}

		@Notification
		public void Notify4(byte s) {
		}

		@Notification
		public void Notify5(boolean s) {
		}

		@Notification
		public void Notify6(double s) {
		}

		@Notification
		public void Notify7(float s) {
		}

		@Notification
		public void Notify8(String s) {
		}

		@Notification
		public void Notify9(int s, float t, double u) {
		}

		@Notification
		public void Notify10(float s, String v) {
		}

		@Notification
		public void Notify10(int[] u) {
		}

		@Notification
		public void Notify11(float s, String v, int[] z) {
		}

		@Notification
		public void Notify12(float s, String v, int[] z, double[] uu, int k) {
		}

		@Notification
		public void NotifyOne(int a) {
		}

		@Notification
		public void NotifyOne(long a) {
		}

		private void sleep() {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			sleep();
			if (Runtime.getRank() == 0) {
				Notify1(17);
				Notify2(18L);
				Notify3('a');
				Notify4((byte) 19);
				Notify5(true);
				Notify6(20.0d);
				Notify7(21.5f);
				Notify8("ala");
				Notify9(58, 55.2f, 395.3d);
				Notify10(new int[] { 3, 5, 1 });
				Notify11(24.2f, "dlskj", null);
				Notify12(234.2f, "test2", new int[0], new double[] { 12.4 }, 9);
				NotifyOne((int) 0L);
				NotifyOne(70000000000L);
			}
			sleep();
			Stop();
			sleep();
		}
	}

	public static void main(String[] args) throws GraphCreationException {
		Runtime.getRuntime().awaitPool(Config.POOLSIZE);
		MySynchronizer s = new MySynchronizer();
		Runtime.getRuntime().spawnAll(s);
		Runtime.getRuntime().barrier();
		if (!s.stopped)
			System.err.println(Runtime.getRank() + " not stopped(!!!!)");
		else if (s.notification != 17)
			System.err.println(Runtime.getRank() + " wrong notification " + s.notification + " != 17 (!!!!)");
		else
			System.err.println(Runtime.getRank() + " OK");
	}
}
