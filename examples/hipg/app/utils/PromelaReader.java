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

package hipg.app.utils;

import hipg.format.GraphCreationException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject.Kind;

import spinja.concurrent.model.ConcurrentModel;
import spinja.exceptions.ValidationException;
import spinja.model.Model;
import spinja.model.Transition;
import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.optimizer.GraphOptimizer;
import spinja.promela.compiler.optimizer.RemoveUselessActions;
import spinja.promela.compiler.optimizer.RemoveUselessGotos;
import spinja.promela.compiler.optimizer.RenumberAll;
import spinja.promela.compiler.optimizer.StateMerging;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.Promela;
import spinja.promela.model.NeverClaimModel;
import spinja.promela.model.PromelaModel;
import spinja.promela.model.PromelaTransition;
import spinja.util.ByteArrayStorage;

public class PromelaReader {

	public static ConcurrentModel<PromelaTransition> readPromela(final String specPath) throws GraphCreationException {
		return readPromela(specPath, makeModelName(specPath), false, false, false, false);
	}

	public static ConcurrentModel<PromelaTransition> readPromela(final String specPath,
			final boolean keepGeneratedModel, final boolean reuseGeneratedModel, final boolean ignoreNeverClaim,
			final boolean ignoreAsserts) throws GraphCreationException {
		return readPromela(specPath, makeModelName(specPath), keepGeneratedModel, reuseGeneratedModel,
				ignoreNeverClaim, ignoreAsserts);
	}

	public static ConcurrentModel<PromelaTransition> readPromela(final String specPath, final String modelName,
			final boolean keepGeneratedModel, final boolean reuseGeneratedModel, final boolean ignoreNeverClaim,
			final boolean ignoreAsserts) throws GraphCreationException {

		checkExists(specPath);

		final String code;

		if (reuseGeneratedModel) {
			code = readModel(storedModelPath(modelName));
		} else {
			String preprocessedSpec = preprocessSpec(specPath, modelName, ignoreAsserts);
			Specification specification = loadSpec(preprocessedSpec, modelName);
			code = generateModel(specification, modelName, keepGeneratedModel);
			deleteFile(preprocessedSpec);
		}

		JavaFileManager fileManager = compileModel(code, modelName);
		PromelaModel promelaModel = createModel(fileManager, modelName);
		final ConcurrentModel<PromelaTransition> model;

		if (ignoreNeverClaim) {
			model = promelaModel;
		} else {
			try {
				model = NeverClaimModel.createNever(promelaModel);
			} catch (ValidationException e) {
				throw new GraphCreationException("Could not create never claim model from " + specPath + ": "
						+ e.getMessage(), e);
			}
		}

		return model;
	}

	public static String preprocessSpec(final String path, final String modelName, final boolean ignoreAsserts)
			throws GraphCreationException {
		final Random rand = new Random(System.nanoTime());
		final int uniqId = Math.abs(rand.nextInt());
		final String output = "/tmp/_preprocessed_" + modelName + "_" + uniqId + ".E";
		final String assertMacro = (ignoreAsserts ? ("-Dassert(cond)=") : "");
		executeCommand("cpp -x c -P -w -nostdinc " + assertMacro + " " + path + " -o " + output);
		return output;
	}

	public static Specification loadSpec(final String path, final String modelName) throws GraphCreationException {

		File file = new File(path);

		Promela prom;
		try {
			prom = new Promela(new FileInputStream(path));
		} catch (FileNotFoundException e) {
			throw new GraphCreationException("File " + file.getAbsolutePath() + " not found: " + e.getMessage(), e);
		}

		Specification specification;
		try {
			specification = prom.spec(modelName);
		} catch (ParseException e) {
			throw new GraphCreationException("Parsing of " + path + " failed: " + e.getMessage(), e);
		}

		final GraphOptimizer[] optimizers = new GraphOptimizer[] { new StateMerging(), new RemoveUselessActions(),
				new RemoveUselessGotos(), new RenumberAll() };

		for (final Proctype proc : specification) {
			for (final GraphOptimizer opt : optimizers) {
				opt.optimize(proc.getAutomaton());
			}
		}

		final Proctype never = specification.getNever();
		if (never != null) {
			for (final GraphOptimizer opt : optimizers) {
				opt.optimize(never.getAutomaton());
			}
		}

		return specification;
	}

	private static String storedModelDir() {
		return "spinja";
	}

	private static String storedModelPath(String modelName) {
		return storedModelDir() + File.separator + modelName + "Model.java";
	}

	private static String generateModel(Specification specification, String modelName, boolean keep)
			throws GraphCreationException {

		final String code;

		try {
			code = specification.generateModel();
		} catch (ParseException e) {
			throw new GraphCreationException("Could not parse model " + specification + ": " + e.getMessage(), e);
		}

		if (keep) {

			mkdirs(storedModelDir());

			String fileName = storedModelPath(modelName);

			System.err.println("Writing model to " + fileName);

			FileWriter writer = null;
			try {
				writer = new FileWriter(fileName);
				writer.write(code);
			} catch (Throwable e) {
				System.err.println("Warning: could not write out code to file " + fileName);
			} finally {
				if (writer != null) {
					try {
						writer.close();
					} catch (IOException e2) {
					}
				}
			}

		}

		return code;
	}

	private static String readModel(final String path) throws GraphCreationException {

		final char newline = '\n';

		StringBuilder sb = new StringBuilder();
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(new File(path)));
		} catch (FileNotFoundException e) {
			throw new GraphCreationException("File " + path + " not found (" + new File(path).getAbsolutePath() + ")");
		}

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line);
				sb.append(newline);
			}
		} catch (IOException e) {
			throw new GraphCreationException("Could not read " + path);
		}

		System.err.println("Model read from " + path);

		return sb.toString();
	}

	private static JavaFileManager compileModel(String code, final String modelName) throws GraphCreationException {

		JavaFileObject src;
		try {
			src = new StringJavaFileObject(modelName + "Model", code);
		} catch (URISyntaxException e) {
			throw new GraphCreationException("Cannot create compiled file object: " + e.getMessage(), e);
		}

		JavaFileManager fileManager = new MemoryJavaFileManager();

		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		final CompilationTask task = compiler.getTask(null, fileManager, null, null, null, Arrays.asList(src));
		Boolean result = task.call();

		if (result == null || !result) {
			throw new GraphCreationException("Compilation failed");
		}

		return fileManager;
	}

	private static final PromelaModel createModel(final JavaFileManager fileManager, final String modelName)
			throws GraphCreationException {

		final Object instance;
		try {
			instance = fileManager.getClassLoader(null).loadClass("spinja." + modelName + "Model").newInstance();
		} catch (Throwable e) {
			throw new GraphCreationException("Could not create model " + modelName + ": " + e.getMessage(), e);
		}

		return (PromelaModel) instance;
	}

	private static final class StringJavaFileObject extends SimpleJavaFileObject {

		private final String code;

		public StringJavaFileObject(String name, String code) throws URISyntaxException {
			super(new URI(name + Kind.SOURCE.extension), Kind.SOURCE);
			this.code = code;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
			return code;
		}

	}

	private static final class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

		class MemoryJavaFileObject extends SimpleJavaFileObject {

			ByteArrayOutputStream stream = new ByteArrayOutputStream();

			MemoryJavaFileObject(String name) {
				super(URI.create("mem:///" + name + Kind.CLASS.extension), Kind.CLASS);
			}

			byte[] getBytes() {
				return stream.toByteArray();
			}

			@Override
			public OutputStream openOutputStream() throws IOException {
				return stream;
			}

		}

		private HashMap<String, MemoryJavaFileObject> classes = new HashMap<String, MemoryJavaFileObject>();

		public MemoryJavaFileManager() {
			super(getFileManager());
		}

		private static final StandardJavaFileManager getFileManager() {
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			if (compiler == null) {
				throw new RuntimeException("Cannot find system java compiler (java.home="
						+ System.getProperty("java.home") + ")");
			}
			return compiler.getStandardFileManager(null, null, null);
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String name, Kind kind, FileObject sibling)
				throws IOException {
			if (StandardLocation.CLASS_OUTPUT == location && JavaFileObject.Kind.CLASS == kind) {
				MemoryJavaFileObject file = new MemoryJavaFileObject(name);
				classes.put(name, file);
				return file;
			} else {
				return super.getJavaFileForOutput(location, name, kind, sibling);
			}
		}

		public byte[] getClassBytes(String className) {
			if (classes.containsKey(className)) {
				return classes.get(className).getBytes();
			}
			return null;
		}

		public ClassLoader getClassLoader(Location location) {
			return new SecureClassLoader() {
				@Override
				protected Class<?> findClass(String name) throws ClassNotFoundException {
					byte[] b = getClassBytes(name);
					if (b == null) {
						System.out.println(name + " not found.");
						throw new ClassNotFoundException();
					}
					return super.defineClass(name, b, 0, b.length);
				}
			};
		}

	}

	private static final void mkdirs(String path) throws GraphCreationException {
		File dir = new File(path);
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new GraphCreationException("Could not create dir " + path);
			}
		} else if (!dir.isDirectory()) {
			throw new GraphCreationException("Not a directory: " + path);
		}
	}

	private static final String makeModelName(final String path) {
		StringBuilder sb = new StringBuilder();
		int lastSeparatorIdx = path.lastIndexOf(File.separator);
		int lastDotIdx = path.lastIndexOf('.');
		if (lastSeparatorIdx < 0)
			lastSeparatorIdx = -1;
		if (lastDotIdx < 0)
			lastDotIdx = path.length();
		String str = path.substring(lastSeparatorIdx + 1, lastDotIdx);
		int letters = 0;
		for (int i = 0; i < str.length(); i++) {
			char ch = str.charAt(i);
			if (Character.isLetter(ch) || Character.isDigit(ch)) {
				sb.append(ch);
				letters++;
			} else {
				sb.append("_");
			}
		}
		String name = sb.toString();
		if (letters == 0) {
			name = "Pan";
		}
		return "_" + name + "_";
	}

	private static <TTransition extends Transition> int countTransitions(final Model<TTransition> model) {
		TTransition last = null;
		int n = 0;
		while (true) {
			last = model.nextTransition(last);
			if (last == null)
				break;
			n++;
		}
		return n;
	}

	private static <TTransition extends Transition> void printState(final PrintStream out, final int depth,
			final Model<TTransition> model, final ByteArrayStorage storage) {
		for (int i = 0; i < depth; i++)
			out.print(" ");
		// byte[] state = new byte[model.getSize()];
		// storage.setBuffer(state);
		// model.encode(storage);
		// out.print(Utils.toHex(state));
		out.print(" (" + countTransitions(model) + ")");
		out.println();
	}

	private static final <TTransition extends Transition> void printModel(final TTransition root,
			final PrintStream out, final Model<TTransition> model, final int depth, final int maxDepth,
			final ByteArrayStorage storage) throws ValidationException {
		printState(out, depth, model, storage);
		TTransition trans = null;
		while (true) {
			trans = model.nextTransition(trans);
			if (trans == null)
				break;
			if (maxDepth == 0 || depth < maxDepth) {
				trans.take();
				printModel(trans, out, model, depth + 1, maxDepth, storage);
				trans.undo();
			}
		}
	}

	private static final <TTransition extends Transition> void printModel(final Model<TTransition> model,
			final int maxDepth) {
		try {
			ByteArrayStorage storage = new ByteArrayStorage();
			PrintStream out = System.out;
			printModel(null, out, model, 0, maxDepth, storage);
		} catch (ValidationException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws GraphCreationException {

		boolean keep = false;
		boolean reuse = false;
		boolean print = false;
		boolean ignoreNeverClaim = false;
		boolean ignoreAsserts = false;
		String path = null;

		for (String arg : args) {
			if ("-keep".equals(arg)) {
				keep = true;
			} else if ("-reuse".equals(arg)) {
				reuse = true;
			} else if ("-print".equals(arg)) {
				print = true;
			} else if ("-N".equals(arg) || "-ignoreNeverClaim".equals(arg)) {
				ignoreNeverClaim = true;
			} else if ("-A".equals(arg) || "-ignoreAsserts".equals(args)) {
				ignoreAsserts = true;
			} else if (path == null) {
				path = arg;
			} else {
				System.err.println("Unrecognized argument: " + arg);
				System.exit(1);
			}
		}

		if (path == null) {
			System.err.println(PromelaReader.class.getSimpleName() + " <options> <promela file>");
			System.err.println("possible options: -keep, -reuse, -print, -N, -A");
			System.exit(1);
		}

		String name = makeModelName(path);

		System.err.println("Reading specification " + path + " into model " + name);

		ConcurrentModel<PromelaTransition> model = PromelaReader.readPromela(path, name, keep, reuse, ignoreNeverClaim,
				ignoreAsserts);

		if (print) {
			printModel(model, 3);
		}
	}

	private static void executeCommand(String command) throws GraphCreationException {

		Process proc;

		try {
			proc = Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			throw new GraphCreationException("Could not create a process" + " to execute command '" + command + "': "
					+ e.getMessage(), e);
		}

		try {
			final byte[] buf = new byte[1024];
			final InputStream err = proc.getInputStream();
			final StringBuilder sb = new StringBuilder();
			boolean error = false;
			while (err != null && err.available() > 0) {
				int n = err.read(buf);
				if (n > 0) {
					error = true;
					sb.append(new String(buf, 0, n));
					sb.append("\n");
				}
			}
			if (error) {
				throw new GraphCreationException("Command '" + command + "' returned errors: \n" + sb.toString());
			}
		} catch (IOException ioe) {
			throw new GraphCreationException("Cannot read error output of command '" + command + "': "
					+ ioe.getMessage(), ioe);
		}
		int exitVal;

		try {
			exitVal = proc.waitFor();
		} catch (Throwable e) {
			throw new GraphCreationException("Could not wait for the process" + " executing the command '" + command
					+ "':" + e.getMessage(), e);
		}

		if (exitVal != 0) {
			throw new GraphCreationException("Command '" + command + "' failed: returned exit value " + exitVal);
		}
	}

	private static void deleteFile(final String path) {
		File file = new File(path);
		file.delete();
	}

	private static void checkExists(final String path) throws GraphCreationException {
		if (!new File(path).exists()) {
			throw new GraphCreationException("File " + path + " does not exit");
		}
	}
}
