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

package hipg.app.scc;

import hipg.Reduce;
import hipg.app.Bigraph.BiLocalNode;
import hipg.app.Bigraph.BiNode;
import hipg.app.utils.SccStructure;
import hipg.graph.ExplicitGraph;
import hipg.runtime.Runtime;
import hipg.runtime.Synchronizer;

public class Quotient {

	public static interface Quotientable {
		long getComponentId();
	}

	public static interface QuotientNode extends BiNode {
		public void sccDep(QuotientComputer q, long componentId);
	}

	public static abstract class LocalQuotientNode<TNode extends QuotientNode> extends BiLocalNode<TNode> implements
			QuotientNode, Quotientable {
		public LocalQuotientNode(ExplicitGraph<TNode> graph, int reference) {
			super(graph, reference);
		}

		public void sccDep0(QuotientComputer q) {
			for (int j = 0; hasNeighbor(j); j++) {
				neighbor(j).sccDep(q, getComponentId());
			}
		}

		public void sccDep(QuotientComputer q, long componentId) {
			if (getComponentId() != componentId) {
				q.LocalSCCs.addTransition(componentId, getComponentId());
			}
		}
	}

	public static class QuotientComputer extends Synchronizer {
		private final ExplicitGraph<QuotientNode> g;
		private final SccStructure LocalSCCs;
		public SccStructure GlobalSCCStructure;

		@SuppressWarnings("unchecked")
		public <TNode extends QuotientNode> QuotientComputer(ExplicitGraph<TNode> g, SccStructure LocalSCCStructure) {
			this.g = (ExplicitGraph<QuotientNode>) g;
			this.LocalSCCs = (LocalSCCStructure == null ? new SccStructure() : LocalSCCStructure);
		}

		@Reduce
		public SccStructure GlobalQuotient(SccStructure s) {
			return LocalSCCs.combine(s);
		}

		private void print(String msg) {
			if (Runtime.getRank() == 0) {
				System.err.println(msg);
			}
		}

		public void run() {
			print("Quotient: start");
			for (int i = 0; i < g.nodes(); i++) {
				((LocalQuotientNode<?>) g.node(i)).sccDep0(this);
			}
			barrier();
			print("Quotient: after barrier");
			GlobalSCCStructure = GlobalQuotient(null);
			print("Quotient: combined");
		}
	}
}
