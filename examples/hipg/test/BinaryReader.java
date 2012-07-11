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
import java.util.StringTokenizer;
import java.util.Vector;

import myutils.IOUtils;

/**
 * Reads a binary file according to a specified format.
 * 
 * 
 * @author ela -- ekr@cs.vu.nl
 */
public class BinaryReader {

	private static void usage() {
		System.err.println(BinaryReader.class.getSimpleName() + " <path> <format...>");
		System.err.println("Hip header format: ");
		System.exit(1);
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			usage();
		}

		/* open file */
		String path = args[0];
		InputStream in = new BufferedInputStream(new FileInputStream(new File(path)));

		/* create format string */
		StringBuilder format = new StringBuilder();
		for (int i = 1; i < args.length; i++)
			format.append(args[i] + " ");

		/* recognize all inputs */
		StringBuilder sb = new StringBuilder();
		Vector<Object> values = new java.util.Vector<Object>();
		recognize(in, sb, format.toString(), values);

		/* print results */
		if (sb.length() > 0) {
			System.out.print(sb);
			if (sb.charAt(sb.length() - 1) != '\n')
				System.out.println();
		}
	}

	private static void recognizeType(InputStream in, StringBuilder out, String format, Vector<Object> values)
			throws IOException {
		Object o;
		if (format.equals("int")) {
			o = IOUtils.readInt(in);
		} else if (format.equals("short")) {
			o = IOUtils.readShort(in);
		} else if (format.equals("long")) {
			o = IOUtils.readLong(in);
		} else if (format.equals("int")) {
			o = IOUtils.readInt(in);
		} else if (format.equals("string")) {
			o = IOUtils.readString(in);
		} else if (format.equals("int[]")) {
			o = IOUtils.readIntArray(in);
		} else if (format.equals("long[]")) {
			o = IOUtils.readLongArray(in);
		} else if (format.equals("string")) {
			o = IOUtils.readString(in);
		} else {
			o = null;
			System.err.println("Not recognized format: " + format);
			System.exit(1);
		}
		if (o != null) {
			out.append(o.toString());
			// System.out.println("adding " + o + " at " + values.size());
			values.add(o);
			out.append("\n");
		}
	}

	private static void recognize(InputStream in, StringBuilder out, String format, Vector<Object> values)
			throws IOException {
		StringTokenizer tok = new StringTokenizer(format);
		while (tok.hasMoreElements()) {
			String token = tok.nextToken();
			StringTokenizer tok2 = new StringTokenizer(token, ",");
			while (tok2.hasMoreElements()) {
				String token2 = tok2.nextToken();
				String insideList = null;
				if (token2.startsWith("list")) {
					int length = -1;
					if (token2.startsWith("list:")) {
						String lenStr = token2.substring("list:".length(), token2.indexOf('('));
						try {
							int len = Integer.parseInt(lenStr);
							Object val = values.get(len);
							if (val == null) {
								System.err.println("Value " + len + " is not set");
								System.exit(1);
							}
							Integer i = (Integer) val;
							length = i.intValue();
						} catch (Throwable e) {
							System.err.println("Unrecognized numer: " + lenStr + " in " + token2 + ": "
									+ e.getMessage());
							System.exit(1);
						}
						insideList = token2.substring(("list:".length()) + (lenStr.length()) + ("(".length()),
								token2.length() - 1);

						System.out.println("entire = " + token2);
						System.out.println("inside = " + insideList);
					} else {
						length = IOUtils.readInt(in);
						insideList = token2.substring("list(".length(), token2.length() - 1);
					}
					if (length < 0) {
						out.append("null\n");
					} else if (length == 0) {
						out.append("[]\n");
					} else {
						for (int i = 0; i < length; i++) {
							recognize(in, out, insideList, values);
						}
					}
				} else {
					recognizeType(in, out, token2, values);
				}
			}
		}
	}
}
