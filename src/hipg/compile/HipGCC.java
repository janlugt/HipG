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

import gnu.regexp.RE;
import gnu.regexp.REException;
import hipg.utils.BCELUtils;
import ibis.compile.Ibisc;
import ibis.compile.IbiscComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.verifier.VerificationResult;
import org.apache.bcel.verifier.Verifier;
import org.apache.bcel.verifier.VerifierFactory;

public class HipGCC extends IbiscComponent {

	private static final String programName = HipGCC.class.getSimpleName();
	private static final String titleStart = "========>> ";
	private static final String titleEnd = " <<========";

	private int hipGCCverifyLevel = 3;
	private boolean hipGCCverifyStopOnError = true;
	private boolean hipGCCinfo = false;
	private boolean hipGCCverbose = false;
	private boolean hipGCCdebugCode = false;
	private boolean hipGCCdebugExe = false;

	private RE classRegex = null;

	/** Discovered implementations of the Node interface. */
	private final Map<String, JavaClass> NodeInterfaces = new HashMap<String, JavaClass>();
	/** Discovered node methods. */
	private final Map<JavaClass, ArrayList<Method>> NodeMethods = new HashMap<JavaClass, ArrayList<Method>>();
	/** Discovered synchronizer classes. */
	private final ArrayList<JavaClass> SynchronizerClasses = new ArrayList<JavaClass>();
	/** Discovered local node classes. */
	private final ArrayList<JavaClass> LocalNodeClasses = new ArrayList<JavaClass>();
	/** Discovered serializable classes. */
	private final ArrayList<JavaClass> SerializableClasses = new ArrayList<JavaClass>();
	/** Rewritten synchronizer classes. */
	private final Map<String, RewrittenSynchronizerClass> RewrittenSynchronizerClasses = new HashMap<String, RewrittenSynchronizerClass>();

	public HipGCC() {
	}

	public boolean isVerboseEnabled() {
		return hipGCCverbose || verbose;
	}

	public void info(String msg) {
		if (hipGCCinfo)
			System.err.println(programName + ": " + msg);
		else if (isVerboseEnabled())
			System.err.println(programName + ": " + titleStart + msg + titleEnd);

	}

	/** Prints an info message. */
	public void verbose(String msg) {
		if (isVerboseEnabled())
			System.err.println(programName + ": " + msg);
	}

	/** Prints a warning. */
	public void warning(String msg) {
		System.err.println(programName + ": WARNING: " + msg);
	}

	/** Prints an error message and exits. */
	public void error(String msg, Throwable t) {
		String fullMsg = programName + ": ERROR: " + msg + (t == null ? "" : (": " + t.getMessage()));
		System.err.println(fullMsg);
		if (t != null)
			t.printStackTrace();
		System.exit(1);
	}

	/** Prints an error message and exits. */
	public void error(String msg) {
		String fullMsg = programName + ": error: " + msg;
		System.err.println(fullMsg);
		System.exit(1);
	}

	boolean debugCode() {
		return hipGCCdebugCode;
	}

	boolean debugExe() {
		return hipGCCdebugExe;
	}

	/** Processes all classes discovered by Ibisc. */
	@Override
	public void process(Iterator<?> classes) {
		int matched = 0;

		/* pre-process classes (discover Node interfaces) */

		while (classes.hasNext()) {
			JavaClass clazz = (JavaClass) classes.next();
			if (classRegex == null || classRegex.isMatch(clazz.getClassName())) {
				try {
					matched++;
					discover(clazz);
				} catch (Throwable t) {
					error("Could not process class " + clazz.getClassName() + ": " + t.getMessage(), t);
				}
			}
		}

		if (matched == 0) {
			warning("no class matched the regular expression: " + classRegex);
			System.exit(1);
		}

		if (NodeInterfaces.isEmpty() && SynchronizerClasses.isEmpty()) {
			warning("no node interfaces or synchronizer classes found");
			System.exit(1);
		}

		if (NodeInterfaces.isEmpty()) {
			warning("no node interfaces discovered");
		}

		if (SynchronizerClasses.isEmpty()) {
			warning("no synchronizer classes discovered");
		}

		if (verbose) {
			System.err.print("discovered node interfaces: ");
			for (JavaClass NodeInterface : NodeInterfaces.values()) {
				System.err.print(NodeInterface.getClassName() + " ");
			}
			System.err.println();
			System.err.print("discovered local node classes: ");
			for (JavaClass LocalNodeClass : LocalNodeClasses) {
				System.err.print(LocalNodeClass.getClassName() + " ");
			}
			System.err.println();
			System.err.print("discovered synchronizer classes: ");
			for (JavaClass SynchronizerClass : SynchronizerClasses) {
				System.err.print(SynchronizerClass.getClassName() + " ");
			}
			System.err.println();
		}

		/* rewrite classes */

		final NodeRewriter nodeRewriter = new NodeRewriter(this, NodeInterfaces, NodeMethods);
		final LocalNodeRewriter localNodeRewriter = new LocalNodeRewriter(this, NodeInterfaces, NodeMethods);
		final SynchronizerRewriter synchronizerRewriter = new SynchronizerRewriter(this);
		final SerializableRewriter serializableRewriter = new SerializableRewriter();
		RewrittenSynchronizerClass r;

		for (JavaClass cl : SerializableClasses) {
			info("Rewriting serializable class " + cl.getClassName());
			try {
				final ClassGen cg = new ClassGen(cl);
				serializableRewriter.process(cl, cg);
				modifiedClass(cl, cg);
			} catch (Throwable t) {
				t.printStackTrace();
				System.exit(1);
			}
		}

		for (JavaClass cl : SynchronizerClasses) {
			info("Rewriting synchronizer " + cl.getClassName());
			try {
				final ClassGen cg = new ClassGen(cl);
				nodeRewriter.process(cl, cg);
				r = synchronizerRewriter.process(cl, cg);
				RewrittenSynchronizerClasses.put(cg.getClassName(), r);
				modifiedClass(cl, cg);
			} catch (Throwable t) {
				t.printStackTrace();
				System.exit(1);
			}
		}

		for (JavaClass cl : LocalNodeClasses) {
			info("Rewriting local node " + cl.getClassName());
			try {
				final ClassGen cg = new ClassGen(cl);
				nodeRewriter.process(cl, cg);
				localNodeRewriter.process(cl, cg);
				modifiedClass(cl, cg);
			} catch (Throwable t) {
				t.printStackTrace();
				System.exit(1);
			}
		}
	}

	/**
	 * Rewrites a class.
	 * 
	 * @throws ClassNotFoundException
	 */
	private void discover(JavaClass cl) throws ClassNotFoundException {
		if (cl == null) {
			throw new NullPointerException("Cannot process a null class");
		} else if (cl.getClassName().endsWith("_rewritten") || cl.getClassName().contains("_rewritten$")) {
			verbose("skipping already rewritten class " + cl.getClassName());
		} else if (LocalNodeRewriter.isNodeInterface(cl)) {
			if (!NodeInterfaces.containsKey(cl.getClassName())) {
				String err = NodeRewriter.checkNodeInterfaceMethods(cl);
				if (err == null) {
					NodeInterfaces.put(cl.getClassName(), cl);
					final ArrayList<Method> methods = NodeRewriter.discoverMethods(cl);
					NodeMethods.put(cl, methods);
					verbose("located node interface " + cl.getClassName() + " with methods: "
							+ BCELUtils.printList(methods.iterator()));
				} else {
					error(err);
				}
			}
		} else if (SynchronizerRewriter.isSynchronizerClass(cl)) {
			if (!SynchronizerClasses.contains(cl)) {
				SynchronizerClasses.add(cl);
			}
		} else if (LocalNodeRewriter.isLocalNodeClass(cl)) {
			if (!LocalNodeClasses.contains(cl)) {
				LocalNodeClasses.add(cl);
			}
		} else if (SerializableRewriter.isSerializable(cl)) {
			if (!SerializableClasses.contains(cl)) {
				SerializableClasses.add(cl);
			}
		}
	}

	public void modifiedClass(JavaClass cl, ClassGen cg) {
		if (wrapper == null)
			throw new RuntimeException("Cannot modify class " + cl.getClassName() + " : no wrapper");
		if (cl != null && cg != null) {
			final JavaClass newClass = cg.getJavaClass();
			Repository.removeClass(cl);
			Repository.addClass(newClass);
			setModified(wrapper.getInfo(newClass));
			if (hipGCCverifyLevel >= 0) {
				if (!verifyClass(newClass, hipGCCverifyLevel)) {
					if (hipGCCverifyStopOnError) {
						error("Class " + cg.getClassName() + " failed verification to level: " + hipGCCverifyLevel);
					} else {
						info("Note: Failures of verification level 3b are often suprious");
					}
				}
			}
		}
	}

	public RewrittenSynchronizerClass getRewrittenSynchronizerClass(String className) {
		return RewrittenSynchronizerClasses.get(className);
	}

	public boolean verifyClass(JavaClass cl, int maxLevel) {
		Verifier verifier = VerifierFactory.getVerifier(cl.getClassName());
		boolean verificationFailed = false;

		if (maxLevel >= 1) {
			VerificationResult result = verifier.doPass1();
			if (result.getStatus() == VerificationResult.VERIFIED_REJECTED) {
				System.out.println("Ibisc: Verification pass 1 failed on class: " + cl.getClassName());
				System.out.println(result.getMessage());
				verificationFailed = true;
			} else if (maxLevel >= 2) {
				result = verifier.doPass2();
				if (result.getStatus() == VerificationResult.VERIFIED_REJECTED) {
					System.out.println("Ibisc: Verification pass 2 failed on class: " + cl.getClassName());
					System.out.println(result.getMessage());
					verificationFailed = true;
				} else if (maxLevel >= 3) {
					Method[] cMethods = cl.getMethods();
					for (int i = 0; i < cMethods.length; i++) {
						result = verifier.doPass3a(i);
						if (result.getStatus() == VerificationResult.VERIFIED_REJECTED) {
							System.out.println("Ibisc: Verification pass 3a failed on class: " + cl.getClassName()
									+ " for method " + cMethods[i].getName());
							System.out.println(result.getMessage());
							verificationFailed = true;
						} else if (maxLevel >= 4) {
							result = verifier.doPass3b(i);
							if (result.getStatus() == VerificationResult.VERIFIED_REJECTED) {
								System.out.println("Ibisc: Verification pass 3b failed on class " + cl.getClassName()
										+ " for method " + cMethods[i].getName());
								System.out.println(result.getMessage());
								verificationFailed = true;
							}
						}
					}
				}
			}
		}
		info("Verifying class " + cl.getClassName() + " (level <= " + maxLevel + ") result = "
				+ (verificationFailed ? "!!! FAIL !!!" : "OK"));
		return !verificationFailed;
	}

	/** Create usage info. */
	@Override
	public String getUsageString() {
		return programName + " -hipgcc <class-list regexp> [-hipgcc-verbose] "
				+ "[-hipgcc-info] [-hipgcc-debug-code] [-hipgcc-debug-exe] "
				+ "[-hipgcc-[no-]verify] [-hipgcc-verify-N] [hipgcc-verify-no-stop-on-error]";
	}

	/** Process arguments (as indicated by the usage info). */
	@Override
	public boolean processArgs(ArrayList<String> args) {
		boolean run = false;
		for (Iterator<String> iter = args.iterator(); iter.hasNext();) {
			final String arg = iter.next().toLowerCase();
			if (arg.equals("-hipg") || arg.equals("-hipgc") || arg.equals("-hipgcc")) {
				run = true;
				iter.remove();
				if (iter.hasNext()) {
					String regex = iter.next();
					if (!regex.equals("*")) {
						try {
							classRegex = new RE(regex);
						} catch (REException e) {
							error("Could not compile regular expression: " + regex);
						}
					}
					iter.remove();
				} else {
					error("No regular expression given");
				}
				verbose("Rewriting classes: " + classRegex);
			} else if (arg.equals("-hipgcc-verbose")) {
				hipGCCverbose = true;
				iter.remove();
			} else if (arg.equals("-hipgcc-debug-code")) {
				hipGCCdebugCode = true;
				iter.remove();
			} else if (arg.equals("-hipgcc-debug-exe")) {
				hipGCCdebugExe = true;
				iter.remove();
			} else if (arg.equals("-hipgcc-info")) {
				hipGCCinfo = true;
				iter.remove();
			} else if (arg.equals("-hipgcc-no-verify")) {
				hipGCCverifyLevel = 0;
				iter.remove();
			} else if (arg.equals("-hipgcc-verify")) {
				hipGCCverifyLevel = 3;
				iter.remove();
			} else if (arg.equals("-hipgcc-verify-stop-on-error")) {
				hipGCCverifyStopOnError = true;
				iter.remove();
			} else if (arg.equals("-hipgcc-verify-no-stop-on-error")) {
				hipGCCverifyStopOnError = false;
				iter.remove();
			} else if (arg.startsWith("-hipgcc-verify-")) {
				String levelString = arg.substring("-hipgcc-verify-".length());
				try {
					hipGCCverifyLevel = Integer.parseInt(levelString);
					iter.remove();
				} catch (NumberFormatException e) {
					// ignore this argument
				}
			}
		}
		return run;
	}

	/** Rewriter implementation (BCEL). */
	@Override
	public String rewriterImpl() {
		return "BCEL";
	}

	public static void main(String[] args) {
		Ibisc.main(args);
	}
}
