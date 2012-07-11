/**
 * Copyright (c) 2009-2011 Vrije Universiteit Amsterdam
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
 */

package hipg.test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import myutils.IOUtils;
import myutils.MathUtils;

public class Header {

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.err.println("Usage: " + Header.class.getSimpleName() + " <path>");
			System.exit(1);
		}

		String path = args[0];
		System.out.println("Reading graph: " + path);
		String headerPath = path + File.separator + "info";
		File headerFile = new File(headerPath);
		InputStream header = null;

		header = new BufferedInputStream(new FileInputStream(headerFile));

		// version
		long version = IOUtils.readInt(header);
		System.out.println("Graph version: " + version);

		// user info
		String info = IOUtils.readString(header);
		System.out.println("Info: " + info);

		// #segments
		int segmentCount = (int) IOUtils.readInt(header);

		// segment root
		long rootSegment = IOUtils.readInt(header);
		long rootOffset = IOUtils.readInt(header);
		System.out.println("Root: in segment " + rootSegment + " with offset " + rootOffset);

		// #labels
		long labels = IOUtils.readInt(header);
		System.out.println("Labels: " + labels);

		// tau
		long tau = IOUtils.readInt(header);
		System.out.println("Tau: " + tau);

		// dummy
		long dummy = IOUtils.readInt(header);
		System.out.println("Dummy: " + dummy);

		// #states in segments
		long segmentSizes[] = new long[segmentCount];
		long globalStateCount = 0;
		for (int i = 0; i < segmentCount; i++) {
			long size = IOUtils.readInt(header);
			segmentSizes[i] = size;
			globalStateCount += size;
		}

		System.out.println("States = " + globalStateCount + " ("
				+ MathUtils.round((double) globalStateCount / 1000000.0) + " millions)");

		for (int i = 0; i < segmentCount; i++) {
			long size = segmentSizes[i];
			System.out.println("Segment " + i + " has " + size + " states ("
					+ MathUtils.proc(segmentSizes[i], globalStateCount) + "%)");
		}

		// #transitions
		long globalTransitionCount = 0;
		long[][] transitionSizes = new long[segmentCount][segmentCount];
		for (int i = 0; i < segmentCount; i++) {
			for (int j = 0; j < segmentCount; j++) {
				long t = IOUtils.readInt(header);
				transitionSizes[i][j] = t;
				globalTransitionCount += t;
			}
		}

		System.out.println("Transitions = " + globalTransitionCount + " ("
				+ MathUtils.round((double) globalTransitionCount / 1000000.0) + " millions)");

		for (int i = 0; i < segmentCount; i++) {
			System.out.println("Transitions from " + i + " to: ");
			for (int j = 0; j < segmentCount; j++) {
				long size = transitionSizes[i][j];
				System.out.println("  -> " + j + ": " + size + " (" + MathUtils.proc(size, globalTransitionCount)
						+ "%)");
			}
		}

	}

}
