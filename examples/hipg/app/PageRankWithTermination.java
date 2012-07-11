package hipg.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import myutils.ConversionUtils;
import hipg.Config;
import hipg.Node;
import hipg.Reduce;
import hipg.format.GraphCreationException;
import hipg.format.GraphIO;
import hipg.format.synthetic.AbstractSyntheticGraphMaker;
import hipg.format.synthetic.SyntheticGraph;
import hipg.graph.ExplicitGraph;
import hipg.graph.ExplicitLocalNode;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

public class PageRankWithTermination {
	private static final double D = 0.85, E = 0.001;

	public static interface MyNode extends Node {
		public void rank(Ranker ranker, double r);

		public void compute(Ranker ranker);

		public void send(Ranker ranker);
	}

	public static final class MyLocalNode extends ExplicitLocalNode<MyNode> implements MyNode {
		private double rank, ranksum = 0.0, diff, N;

		public MyLocalNode(ExplicitGraph<MyNode> graph, int reference) {
			super(graph, reference);
		}

		public void rank(Ranker ranker, double r) {
			ranksum += r;
		}

		public void compute(Ranker ranker) {
			double oldRank = rank;
			rank = (1 - D) / N + D * ranksum;
			ranksum = 0.0;
			diff = Math.abs(rank - oldRank);
		}
		
		public void send(Ranker ranker) {
			for (int i = 0; hasNeighbor(i); i++) {
				neighbor(i).rank(ranker, rank / outdegree());
			}
		}
	}

	public static class Ranker extends Synchronizer {
		private final ExplicitGraph<MyNode> g;
		private long startTime;

		public Ranker(ExplicitGraph<MyNode> g) {
			this.g = g;
		}

		@Reduce
		public double getGlobalErrorSum(double errorSum) {
			for (int i = 0; i < g.nodes(); i++) {
				final double diff = ((MyLocalNode) g.node(i)).diff;
				errorSum += diff;
			}
			return errorSum;
		}

		@Override
		public void run() {
			startTime = System.nanoTime();
			for (int i = 0; i < g.nodes(); i++) {
				((MyLocalNode) g.node(i)).rank = 1.0 / g.nodes();
				((MyLocalNode) g.node(i)).N = g.nodes();
			}
			barrier();
			print(" Initialization took: " + ConversionUtils.ns2sec(System.nanoTime() - startTime));			
			
			int step = 0;
			double errorSum = 2 * E;
			while(errorSum > E) {
				print("Step " + step);
				startTime = System.nanoTime();
				for (int i = 0; i < g.nodes(); i++) {
					((MyLocalNode) g.node(i)).send(this);
					if (i % 10000 == 9999) {
						Runtime.nice();
					}
				}
				barrier();
				print(" Send took: " + ConversionUtils.ns2sec(System.nanoTime() - startTime));
				startTime = System.nanoTime();
				for (int i = 0; i < g.nodes(); i++) {
					((MyLocalNode) g.node(i)).compute(this);
				}
				barrier();
				print(" Compute took: " + ConversionUtils.ns2sec(System.nanoTime() - startTime));
				startTime = System.nanoTime();
				errorSum = getGlobalErrorSum(0.0);
				barrier();
				print(" Global error sum reduction took: " + ConversionUtils.ns2sec(System.nanoTime() - startTime)
						+ " (" + errorSum + ")");
				step++;
			}
		}
	}

	public class BinaryReader implements SyntheticGraph {
		private long nodeCount, edgeCount;
		private String filename;

		public BinaryReader(String filename) throws GraphCreationException {
			this.filename = filename;
		}

		@SuppressWarnings("unused")
    @Override
		public <TNode extends Node, TLocalNode extends ExplicitLocalNode<TNode>> long create(
				AbstractSyntheticGraphMaker<TNode, TLocalNode> maker) throws GraphCreationException {
			long source = 0;
			FileInputStream fis = null;
			try {
				File file = new File(filename);
				fis = new FileInputStream(file);
				FileChannel ch = fis.getChannel();
				int size = (int) ch.size();
				MappedByteBuffer bb = ch.map(MapMode.READ_ONLY, 0, size);
				bb.order(ByteOrder.LITTLE_ENDIAN);
				IntBuffer ib = bb.asIntBuffer();
				int magic = ib.get();
				assert(magic == 0x03939999);
				int nodeIdentifierSize = ib.get();
				int edgeIdentifierSize = ib.get();
				nodeCount = ib.get();
				edgeCount = ib.get();

				// Nodes
				int[] indices = new int[(int) nodeCount + 1];
				for (int i = 0; i < nodeCount + 1; i++) {
					indices[i] = ib.get();
				}
				
				// Edges
				for (int i = 0; i < nodeCount; i++) {
					source = maker.addNode();
					for (int j = indices[i]; j < indices[i+1]; j++) {
						int target = ib.get();
						maker.addTransition(source, target);
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			  try {
          fis.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
			}

			return source;
		}

		@Override
		public long estimateGlobalNodes() {
			return nodeCount;
		}

		@Override
		public long estimateGlobalTransitions() {
			return edgeCount;
		}

		@Override
		public boolean canSynthetizeTranspose() {
			return true;
		}

		@Override
		public boolean transitionsPerNodeCreatedSequentially() {
			return true;
		}
	}
	
	private static void print(String msg) {
		if (Runtime.getRank() == 0) {
			System.out.println(Runtime.getRank() + ": " + msg);
			System.out.flush();
		}
	}

	public static void main(String[] args) throws GraphCreationException {
		if (args.length < 2) {
			// Print usage and exit.
			System.err.println(PageRankWithTermination.class.getName() + " <graph>");
			System.err.println("where graph can be specified as one of the following:");
			System.err.println(GraphIO.formatSpecificationMessage());
			System.exit(1);
		}

		// Read graph.
		print("Reading graph in format " + args[0] + " " + args[1]);
		long readStart = System.nanoTime();
		final ExplicitGraph<MyNode> g = hipg.format.GraphIO.read(MyLocalNode.class, MyNode.class, args[0], args[1],
				Config.POOLSIZE);
		long readTime = System.nanoTime() - readStart;
		print("Graph with " + g.getGlobalSize() + " nodes read in " + ConversionUtils.ns2sec(readTime) + "s");

		// Run PageRank.
		print("Starting Ranker");
		Ranker ranker = new Ranker(g);
		long start = System.nanoTime();
		Runtime.getRuntime().spawnAll(ranker);
		Runtime.getRuntime().barrier();
		long time = System.nanoTime() - start;

		// Print results.
		final long N = g.getGlobalSize();
		print("Computed ranks for graph with global size " + N);
		print("Ranker on " + Runtime.getPoolSize() + " processors took " + ConversionUtils.ns2sec(time) + "s");
	}
}
