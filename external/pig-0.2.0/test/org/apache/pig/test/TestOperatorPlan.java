/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pig.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.pig.impl.plan.OperatorKey;
import org.apache.pig.impl.plan.*;
import org.apache.pig.impl.plan.optimizer.*;

import org.junit.Test;

/**
 * Test the generic operator classes (Operator, OperatorPlan,
 * PlanVisitor).  Also includes tests for optimizer framework, since that
 * can use the same generic test operators.
 */

public class TestOperatorPlan extends junit.framework.TestCase {

    private int mNextKey = 0;

    abstract class TOperator extends Operator implements Comparable {
        protected String mName;

        TOperator(String name) {
            super(new OperatorKey("", mNextKey++));
            mName = name;
        }

        public int compareTo(Object o) {
            if (!(o instanceof TOperator)) {
                return -1;
            }

            TOperator other = (TOperator)o;

            return mName.compareTo(other.mName);
        }
    }

    class SingleOperator extends TOperator {
        SingleOperator(String name) {
            super(name);
        }

        public boolean supportsMultipleInputs() {
            return false;
        }

        public boolean supportsMultipleOutputs() {
            return false;
        }

        @Override
        public void visit(PlanVisitor v) throws VisitorException {
            ((TVisitor)v).visitSingleOperator(this);
        }

        public String name() {
            return mName;
        }

    }

    class MultiOperator extends TOperator {
        MultiOperator(String name) {
            super(name);
        }

        public boolean supportsMultipleInputs() {
            return true;
        }

        public boolean supportsMultipleOutputs() {
            return true;
        }

        public void visit(PlanVisitor v) throws VisitorException {
            ((TVisitor)v).visitMultiOperator(this);
        }

        public String name() {
            return mName;
        }

    }

    class TPlan extends OperatorPlan<TOperator> {

        public String display() {
            StringBuilder buf = new StringBuilder();

            buf.append("Nodes: ");
            // Guarantee a sorting
            TreeSet<TOperator> ts = new TreeSet(mOps.keySet());
            for (TOperator op : ts) {
                buf.append(op.name());
                buf.append(' ');
            }

            buf.append("FromEdges: ");
            ts = new TreeSet(mFromEdges.keySet());
            Iterator<TOperator> i = ts.iterator();
            while (i.hasNext()) {
                TOperator from = i.next();
                TreeSet<TOperator> ts2 = new TreeSet(mFromEdges.get(from));
                Iterator<TOperator> j = ts2.iterator();
                while (j.hasNext()) {
                    buf.append(from.name());
                    buf.append("->");
                    buf.append(j.next().name());
                    buf.append(' ');
                }
            }

            buf.append("ToEdges: ");
            ts = new TreeSet(mToEdges.keySet());
            i = ts.iterator();
            while (i.hasNext()) {
                TOperator from = i.next();
                TreeSet<TOperator> ts2 = new TreeSet(mToEdges.get(from));
                Iterator<TOperator> j = ts2.iterator();
                while (j.hasNext()) {
                    buf.append(from.name());
                    buf.append("->");
                    buf.append(j.next().name());
                    buf.append(' ');
                }
            }
            return buf.toString();
        }
    }

    abstract class TVisitor extends PlanVisitor<TOperator, TPlan> {
        protected StringBuilder mJournal;

        TVisitor(TPlan plan, PlanWalker<TOperator, TPlan> walker) {
            super(plan, walker);
            mJournal = new StringBuilder();
        }

        public void visitSingleOperator(SingleOperator so) throws VisitorException {
            mJournal.append(so.name());
            mJournal.append(' ');
        }

        public void visitMultiOperator(MultiOperator mo) throws VisitorException {
            mJournal.append(mo.name());
            mJournal.append(' ');
        }

        public String getJournal() {
            return mJournal.toString();
        }
    }

    class TDepthVisitor extends TVisitor {

        TDepthVisitor(TPlan plan) {
            super(plan, new DepthFirstWalker(plan));
        }
    }

    class TDependVisitor extends TVisitor {

        TDependVisitor(TPlan plan) {
            super(plan, new DependencyOrderWalker(plan));
        }
    }

    static class TOptimizer extends PlanOptimizer<TOperator, TPlan> {

        public TOptimizer(TPlan plan) {
            super(plan);
        }

        public void addRule(Rule rule) {
            mRules.add(rule);
        }
    }


    @Test
    public void testAddRemove() throws Exception {
        // Test that we can add and remove nodes from the plan.  Also test
        // that we can fetch the nodes by operator key, by operator, by
        // roots, by leaves, that they have no predecessors and no
        // successors.

        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[3];
        for (int i = 0; i < 3; i++) {
            ops[i] = new SingleOperator(Integer.toString(i));
            plan.add(ops[i]);
        }

        // All should be roots, as none are connected
        List<TOperator> roots = plan.getRoots();
        for (int i = 0; i < 3; i++) {
            assertTrue("Roots should contain operator " + i,
                roots.contains(ops[i]));
        }

        // All should be leaves, as none are connected
        List<TOperator> leaves = plan.getLeaves();
        for (int i = 0; i < 3; i++) {
            assertTrue("Leaves should contain operator " + i,
                leaves.contains(ops[i]));
        }

        // Each operator should have no successors or predecessors.
        assertNull(plan.getSuccessors(ops[1]));
        assertNull(plan.getPredecessors(ops[1]));

        // Make sure we find them all when we iterate through them.
        Set<TOperator> s = new HashSet<TOperator>();
        Iterator<TOperator> j = plan.iterator();
        while (j.hasNext()) {
            s.add(j.next());
        }

        for (int i = 0; i < 3; i++) {
            assertTrue("Iterator should contain operator " + i,
                s.contains(ops[i]));
        }

        // Test that we can find an operator by its key.
        TOperator op = plan.getOperator(new OperatorKey("", 1));
        assertEquals("Expected to get back ops[1]", ops[1], op);

        // Test that we can get an operator key by its operator
        OperatorKey opkey = new OperatorKey("", 1);
        assertTrue("Expected to get back key for ops[1]",
            opkey.equals(plan.getOperatorKey(ops[1])));

        // Test that we can remove operators
        plan.remove(ops[2]);

        assertEquals("Should only have two roots now.", 2,
            plan.getRoots().size());
        assertEquals("Should only have two leaves now.", 2,
            plan.getLeaves().size());

        j = plan.iterator();
        int k;
        for (k = 0; j.hasNext(); k++) j.next();
        assertEquals("Iterator should only return two now", 2, k);

        // Remove all operators
        plan.remove(ops[0]);
        plan.remove(ops[1]);

        assertEquals("Should only have no roots now.", 0,
            plan.getRoots().size());
        assertEquals("Should only have no leaves now.", 0,
            plan.getLeaves().size());

        j = plan.iterator();
        assertFalse("Iterator should return nothing now", j.hasNext());
    }

    @Test
    public void testInsertBetween() throws Exception {
        // Test that insertBetween works.

        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[3];
        for (int i = 0; i < 3; i++) {
            ops[i] = new SingleOperator(Integer.toString(i));
            plan.add(ops[i]);
        }

        // Connect 0 to 2
        plan.connect(ops[0], ops[2]);

        Collection p = plan.getPredecessors(ops[0]);
        assertNull(p);
        p = plan.getSuccessors(ops[0]);
        assertEquals(1, p.size());
        Iterator i = p.iterator();
        assertEquals(ops[2], i.next());

        p = plan.getPredecessors(ops[1]);
        assertNull(p);
        p = plan.getSuccessors(ops[1]);
        assertNull(p);

        p = plan.getPredecessors(ops[2]);
        assertEquals(1, p.size());
        i = p.iterator();
        assertEquals(ops[0], i.next());
        p = plan.getSuccessors(ops[2]);
        assertNull(p);

        // Insert 1 in between 0 and 2
        plan.insertBetween(ops[0], ops[1], ops[2]);

        p = plan.getPredecessors(ops[0]);
        assertNull(p);
        p = plan.getSuccessors(ops[0]);
        assertEquals(1, p.size());
        i = p.iterator();
        assertEquals(ops[1], i.next());

        p = plan.getPredecessors(ops[1]);
        assertEquals(1, p.size());
        i = p.iterator();
        assertEquals(ops[0], i.next());
        p = plan.getSuccessors(ops[1]);
        assertEquals(1, p.size());
        i = p.iterator();
        assertEquals(ops[2], i.next());

        p = plan.getPredecessors(ops[2]);
        assertEquals(1, p.size());
        i = p.iterator();
        assertEquals(ops[1], i.next());
        p = plan.getSuccessors(ops[2]);
        assertNull(p);
    }

    @Test
    public void testInsertBetweenNegative() throws Exception {
        // Test that insertBetween throws errors when it should.

        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[4];
        for (int i = 0; i < 4; i++) {
            ops[i] = new MultiOperator(Integer.toString(i));
            plan.add(ops[i]);
        }

        plan.connect(ops[0], ops[1]);

        boolean caughtIt = false;
        try {
            plan.insertBetween(ops[0], ops[3], ops[2]);
        } catch (PlanException pe) {
            caughtIt = true;
        }
        assertTrue(caughtIt);
    }

    @Test
    public void testLinearGraph() throws Exception {
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[5];
        for (int i = 0; i < 5; i++) {
            ops[i] = new SingleOperator(Integer.toString(i));
            plan.add(ops[i]);
            if (i > 0) plan.connect(ops[i - 1], ops[i]);
        }

        // Test that connecting a node not yet in the plan is detected.
        TOperator bogus = new SingleOperator("X");
        boolean sawError = false;
        try {
            plan.connect(ops[2], bogus);
        } catch (PlanException ioe) {
            assertEquals("Attempt to connect operator X which is not in "
                + "the plan.", ioe.getMessage());
            sawError = true;
        }
        assertTrue("Should have caught an error when we tried to connect a "
            + "node that was not in the plan", sawError);

        // Get roots should just return ops[0]
        List<TOperator> roots = plan.getRoots();
        assertEquals(1, roots.size());
        assertEquals(roots.get(0), ops[0]);

        // Get leaves should just return ops[4]
        List<TOperator> leaves = plan.getLeaves();
        assertEquals(1, leaves.size());
        assertEquals(leaves.get(0), ops[4]);

        // Test that connecting another input to SingleOperator gives
        // error.
        plan.add(bogus);
        sawError = false;
        try {
            plan.connect(bogus, ops[1]);
        } catch (PlanException ioe) {
            assertEquals("Attempt to give operator of type " +
                "org.apache.pig.test.TestOperatorPlan$SingleOperator " +
                "multiple inputs.  This operator does "
                + "not support multiple inputs.", ioe.getMessage());
            sawError = true;
        }
        assertTrue("Should have caught an error when we tried to connect a "
            + "second input to a Single", sawError);

        // Test that connecting another output to SingleOperator gives
        // error.
        sawError = false;
        try {
            plan.connect(ops[0], bogus);
        } catch (PlanException ioe) {
            assertEquals("Attempt to give operator of type " +
                "org.apache.pig.test.TestOperatorPlan$SingleOperator " +
                "multiple outputs.  This operator does "
                + "not support multiple outputs.", ioe.getMessage());
            sawError = true;
        }
        assertTrue("Should have caught an error when we tried to connect a "
            + "second output to a " +
            "org.apache.pig.test.TestOperatorPlan$SingleOperator", sawError);
        plan.remove(bogus);

        // Successor for ops[1] should be ops[2]
        Collection s = plan.getSuccessors(ops[1]);
        assertEquals(1, s.size());
        Iterator i = s.iterator();
        assertEquals(ops[2], i.next());

        // Predecessor for ops[1] should be ops[0]
        Collection p = plan.getPredecessors(ops[1]);
        assertEquals(1, p.size());
        i = p.iterator();
        assertEquals(ops[0], i.next());

        assertEquals("Nodes: 0 1 2 3 4 FromEdges: 0->1 1->2 2->3 3->4 ToEdges: 1->0 2->1 3->2 4->3 ", plan.display());

        // Visit it depth first
        TVisitor visitor = new TDepthVisitor(plan);
        visitor.visit();
        assertEquals("0 1 2 3 4 ", visitor.getJournal());

        // Visit it dependency order
        visitor = new TDependVisitor(plan);
        visitor.visit();
        assertEquals("0 1 2 3 4 ", visitor.getJournal());

        // Test disconnect
        plan.disconnect(ops[2], ops[3]);
        assertEquals("Nodes: 0 1 2 3 4 FromEdges: 0->1 1->2 3->4 ToEdges: 1->0 2->1 4->3 ", plan.display());

        // Test remove
        plan.remove(ops[1]);
        assertEquals("Nodes: 0 2 3 4 FromEdges: 3->4 ToEdges: 4->3 ", plan.display());
    }

    @Test
    public void testDAG() throws Exception {
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[6];
        for (int i = 0; i < 6; i++) {
            ops[i] = new MultiOperator(Integer.toString(i));
            plan.add(ops[i]);
        }
        plan.connect(ops[0], ops[2]);
        plan.connect(ops[1], ops[2]);
        plan.connect(ops[2], ops[3]);
        plan.connect(ops[3], ops[4]);
        plan.connect(ops[3], ops[5]);

        // Get roots should return ops[0] and ops[1]
        List<TOperator> roots = plan.getRoots();
        assertEquals(2, roots.size());
        assertTrue(roots.contains(ops[0]));
        assertTrue(roots.contains(ops[1]));

        // Get leaves should return ops[4] and ops[5]
        List<TOperator> leaves = plan.getLeaves();
        assertEquals(2, leaves.size());
        assertTrue(leaves.contains(ops[4]));
        assertTrue(leaves.contains(ops[5]));

        // Successor for ops[3] should be ops[4] and ops[5]
        List<TOperator> s = new ArrayList<TOperator>(plan.getSuccessors(ops[3]));
        assertEquals(2, s.size());
        assertTrue(s.contains(ops[4]));
        assertTrue(s.contains(ops[5]));
        
        // Predecessor for ops[2] should be ops[0] and ops[1]
        s = new ArrayList<TOperator>(plan.getPredecessors(ops[2]));
        assertEquals(2, s.size());
        assertTrue(s.contains(ops[0]));
        assertTrue(s.contains(ops[1]));

        assertEquals("Nodes: 0 1 2 3 4 5 FromEdges: 0->2 1->2 2->3 3->4 3->5 ToEdges: 2->0 2->1 3->2 4->3 5->3 ", plan.display());

        // Visit it depth first
        TVisitor visitor = new TDepthVisitor(plan);
        visitor.visit();
        // There are a number of valid patterns, make sure we found one of
        // them.
        String result = visitor.getJournal();
        assertTrue(result.equals("1 2 3 4 5 0 ") ||
            result.equals("1 2 3 5 4 0 ") || result.equals("0 2 3 4 5 1 ")
            || result.equals("0 2 3 5 4 1 "));

        // Visit it dependency order
        visitor = new TDependVisitor(plan);
        visitor.visit();
        result = visitor.getJournal();
        assertTrue(result.equals("0 1 2 3 4 5 ") ||
            result.equals("0 1 2 3 5 4 "));

        // Test disconnect
        plan.disconnect(ops[2], ops[3]);
        assertEquals("Nodes: 0 1 2 3 4 5 FromEdges: 0->2 1->2 3->4 3->5 ToEdges: 2->0 2->1 4->3 5->3 ", plan.display());

        // Test remove
        plan.remove(ops[2]);
        assertEquals("Nodes: 0 1 3 4 5 FromEdges: 3->4 3->5 ToEdges: 4->3 5->3 ", plan.display());
    }

    class AlwaysTransform extends Transformer<TOperator, TPlan> {
        public boolean mTransformed = false;

        AlwaysTransform(TPlan plan) {
            super(plan, new DepthFirstWalker<TOperator, TPlan>(plan));
        }

        public boolean check(List<TOperator> nodes) {
            return true;
        }

        public void transform(List<TOperator> nodes) {
            mTransformed = true;
        }
    }

    // Test that we don't match when nodes don't match pattern.  Will give
    // a pattern of S->S->M and a plan of S->M->S.
    @Test
    public void testOptimizerDifferentNodes() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[3];
        ops[0] = new SingleOperator("1");
        plan.add(ops[0]);
        ops[1] = new MultiOperator("2");
        plan.add(ops[1]);
        ops[2] = new SingleOperator("3");
        plan.add(ops[2]);
        plan.connect(ops[0], ops[1]);
        plan.connect(ops[1], ops[2]);

        // Create our rule
        ArrayList<String> nodes = new ArrayList<String>(3);
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$MultiOperator");
        HashMap<Integer, Integer> edges = new HashMap<Integer, Integer>(2);
        edges.put(0, 1);
        edges.put(1, 2);
        ArrayList<Boolean> required = new ArrayList<Boolean>(3);
        required.add(true);
        required.add(true);
        required.add(true);
        AlwaysTransform transformer = new AlwaysTransform(plan);
        Rule<TOperator, TPlan> r =
            new Rule<TOperator, TPlan>(nodes, edges, required, transformer);

        TOptimizer optimizer = new TOptimizer(plan);
        optimizer.addRule(r);

        optimizer.optimize();
        assertFalse(transformer.mTransformed);
    }

    // Test that we don't match when edges don't match pattern.  Will give
    // a pattern of S->S->M and a plan of S->S M.
    @Test
    public void testOptimizerDifferentEdges() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[3];
        ops[0] = new SingleOperator("1");
        plan.add(ops[0]);
        ops[1] = new SingleOperator("2");
        plan.add(ops[1]);
        ops[2] = new MultiOperator("3");
        plan.add(ops[2]);
        plan.connect(ops[0], ops[1]);

        // Create our rule
        ArrayList<String> nodes = new ArrayList<String>(3);
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$MultiOperator");
        HashMap<Integer, Integer> edges = new HashMap<Integer, Integer>(2);
        edges.put(0, 1);
        edges.put(1, 2);
        ArrayList<Boolean> required = new ArrayList<Boolean>(3);
        required.add(true);
        required.add(true);
        required.add(true);
        AlwaysTransform transformer = new AlwaysTransform(plan);
        Rule<TOperator, TPlan> r =
            new Rule<TOperator, TPlan>(nodes, edges, required, transformer);

        TOptimizer optimizer = new TOptimizer(plan);
        optimizer.addRule(r);
        optimizer.optimize();
        assertFalse(transformer.mTransformed);
    }

    // Test that we match when appropriate.  Will give
    // a pattern of S->S->M and a plan of S->S->M.
    @Test
    public void testOptimizerMatches() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[3];
        ops[0] = new SingleOperator("1");
        plan.add(ops[0]);
        ops[1] = new SingleOperator("2");
        plan.add(ops[1]);
        ops[2] = new MultiOperator("3");
        plan.add(ops[2]);
        plan.connect(ops[0], ops[1]);
        plan.connect(ops[1], ops[2]);

        // Create our rule
        ArrayList<String> nodes = new ArrayList<String>(3);
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$MultiOperator");
        HashMap<Integer, Integer> edges = new HashMap<Integer, Integer>(2);
        edges.put(0, 1);
        edges.put(1, 2);
        ArrayList<Boolean> required = new ArrayList<Boolean>(3);
        required.add(true);
        required.add(true);
        required.add(true);
        AlwaysTransform transformer = new AlwaysTransform(plan);
        Rule<TOperator, TPlan> r =
            new Rule<TOperator, TPlan>(nodes, edges, required, transformer);

        TOptimizer optimizer = new TOptimizer(plan);
        optimizer.addRule(r);

        optimizer.optimize();
        assertTrue(transformer.mTransformed);
    }

    // Test that we match when the pattern says any.  Will give
    // a pattern of any and a plan of S->S->M.
    @Test
    public void testOptimizerMatchesAny() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[3];
        ops[0] = new SingleOperator("1");
        plan.add(ops[0]);
        ops[1] = new SingleOperator("2");
        plan.add(ops[1]);
        ops[2] = new MultiOperator("3");
        plan.add(ops[2]);
        plan.connect(ops[0], ops[1]);
        plan.connect(ops[1], ops[2]);

        // Create our rule
        ArrayList<String> nodes = new ArrayList<String>(3);
        nodes.add("any");
        HashMap<Integer, Integer> edges = new HashMap<Integer, Integer>(2);
        ArrayList<Boolean> required = new ArrayList<Boolean>(1);
        required.add(true);
        AlwaysTransform transformer = new AlwaysTransform(plan);
        Rule<TOperator, TPlan> r =
            new Rule<TOperator, TPlan>(nodes, edges, required, transformer);

        TOptimizer optimizer = new TOptimizer(plan);
        optimizer.addRule(r);

        optimizer.optimize();
        assertTrue(transformer.mTransformed);
    }

    // Test that we match when the whole plan doesn't match.  Will give
    // a pattern of S->S->M and a plan of S->S->S->M.
    @Test
    public void testOptimizerMatchesPart() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[4];
        ops[0] = new SingleOperator("1");
        plan.add(ops[0]);
        ops[1] = new SingleOperator("2");
        plan.add(ops[1]);
        ops[2] = new SingleOperator("3");
        plan.add(ops[2]);
        ops[3] = new MultiOperator("4");
        plan.add(ops[3]);
        plan.connect(ops[0], ops[1]);
        plan.connect(ops[1], ops[2]);
        plan.connect(ops[2], ops[3]);

        // Create our rule
        ArrayList<String> nodes = new ArrayList<String>(3);
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$MultiOperator");
        HashMap<Integer, Integer> edges = new HashMap<Integer, Integer>(2);
        edges.put(0, 1);
        edges.put(1, 2);
        ArrayList<Boolean> required = new ArrayList<Boolean>(3);
        required.add(true);
        required.add(true);
        required.add(true);
        AlwaysTransform transformer = new AlwaysTransform(plan);
        Rule<TOperator, TPlan> r =
            new Rule<TOperator, TPlan>(nodes, edges, required, transformer);

        TOptimizer optimizer = new TOptimizer(plan);
        optimizer.addRule(r);

        optimizer.optimize();
        assertTrue(transformer.mTransformed);
    }

    // Test that we match when a node is optional and the optional node is
    // present.  Will give
    // a pattern of S->S->M (with second S optional) and a plan of S->S->M.
    @Test
    public void testOptimizerOptionalMatches() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[3];
        ops[0] = new SingleOperator("1");
        plan.add(ops[0]);
        ops[1] = new SingleOperator("2");
        plan.add(ops[1]);
        ops[2] = new MultiOperator("3");
        plan.add(ops[2]);
        plan.connect(ops[0], ops[1]);
        plan.connect(ops[1], ops[2]);

        // Create our rule
        ArrayList<String> nodes = new ArrayList<String>(3);
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$MultiOperator");
        HashMap<Integer, Integer> edges = new HashMap<Integer, Integer>(2);
        edges.put(0, 1);
        edges.put(1, 2);
        ArrayList<Boolean> required = new ArrayList<Boolean>(3);
        required.add(true);
        required.add(false);
        required.add(true);
        AlwaysTransform transformer = new AlwaysTransform(plan);
        Rule<TOperator, TPlan> r =
            new Rule<TOperator, TPlan>(nodes, edges, required, transformer);

        TOptimizer optimizer = new TOptimizer(plan);
        optimizer.addRule(r);

        optimizer.optimize();
        assertTrue(transformer.mTransformed);
    }

    // Test that we match when a node is optional and the optional node is
    // missing.  Will give
    // a pattern of S->S->M (with second S optional) and a plan of S->M.
    @Test
    public void testOptimizerOptionalMissing() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[2];
        ops[0] = new SingleOperator("1");
        plan.add(ops[0]);
        ops[1] = new MultiOperator("2");
        plan.add(ops[1]);
        plan.connect(ops[0], ops[1]);

        // Create our rule
        ArrayList<String> nodes = new ArrayList<String>(3);
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$MultiOperator");
        HashMap<Integer, Integer> edges = new HashMap<Integer, Integer>(2);
        edges.put(0, 1);
        edges.put(1, 2);
        ArrayList<Boolean> required = new ArrayList<Boolean>(3);
        required.add(true);
        required.add(false);
        required.add(true);
        AlwaysTransform transformer = new AlwaysTransform(plan);
        Rule<TOperator, TPlan> r =
            new Rule<TOperator, TPlan>(nodes, edges, required, transformer);

        TOptimizer optimizer = new TOptimizer(plan);
        optimizer.addRule(r);

        optimizer.optimize();
        assertTrue(transformer.mTransformed);
    }

    class NeverTransform extends Transformer<TOperator, TPlan> {
        public boolean mTransformed = false;

        NeverTransform(TPlan plan) {
            super(plan, new DepthFirstWalker<TOperator, TPlan>(plan));
        }

        public boolean check(List<TOperator> nodes) {
            return false;
        }

        public void transform(List<TOperator> nodes) {
            mTransformed = true;
        }
    }

    // Test that even if we match, if check returns false then the optimization
    // is not done.
    @Test
    public void testCheck() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[3];
        ops[0] = new SingleOperator("1");
        plan.add(ops[0]);
        ops[1] = new SingleOperator("2");
        plan.add(ops[1]);
        ops[2] = new MultiOperator("3");
        plan.add(ops[2]);
        plan.connect(ops[0], ops[1]);
        plan.connect(ops[1], ops[2]);

        // Create our rule
        ArrayList<String> nodes = new ArrayList<String>(3);
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$SingleOperator");
        nodes.add("org.apache.pig.test.TestOperatorPlan$MultiOperator");
        HashMap<Integer, Integer> edges = new HashMap<Integer, Integer>(2);
        edges.put(0, 1);
        edges.put(1, 2);
        ArrayList<Boolean> required = new ArrayList<Boolean>(3);
        required.add(true);
        required.add(true);
        required.add(true);
        NeverTransform transformer = new NeverTransform(plan);
        Rule<TOperator, TPlan> r =
            new Rule<TOperator, TPlan>(nodes, edges, required, transformer);

        TOptimizer optimizer = new TOptimizer(plan);
        optimizer.addRule(r);

        optimizer.optimize();
        assertFalse(transformer.mTransformed);
    }

    @Test
    public void testReplace() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[6];
        ops[0] = new MultiOperator("1");
        plan.add(ops[0]);
        ops[1] = new MultiOperator("2");
        plan.add(ops[1]);
        ops[2] = new MultiOperator("3");
        plan.add(ops[2]);
        ops[3] = new MultiOperator("4");
        plan.add(ops[3]);
        ops[4] = new MultiOperator("5");
        plan.add(ops[4]);
        plan.connect(ops[0], ops[2]);
        plan.connect(ops[1], ops[2]);
        plan.connect(ops[2], ops[3]);
        plan.connect(ops[2], ops[4]);
        ops[5] = new MultiOperator("6");
        plan.replace(ops[2], ops[5]);

        assertEquals("Nodes: 1 2 4 5 6 FromEdges: 1->6 2->6 6->4 6->5 ToEdges: 4->6 5->6 6->1 6->2 ", plan.display());
    }

    @Test
    public void testReplaceNoConnections() throws Exception {
        // Build a plan
        TPlan plan = new TPlan();
        TOperator[] ops = new TOperator[4];
        ops[0] = new MultiOperator("1");
        plan.add(ops[0]);
        ops[1] = new MultiOperator("2");
        plan.add(ops[1]);
        ops[2] = new MultiOperator("3");
        plan.add(ops[2]);
        plan.connect(ops[0], ops[2]);
        ops[3] = new MultiOperator("4");
        plan.replace(ops[1], ops[3]);

        assertEquals("Nodes: 1 3 4 FromEdges: 1->3 ToEdges: 3->1 ", plan.display());
    }




}

