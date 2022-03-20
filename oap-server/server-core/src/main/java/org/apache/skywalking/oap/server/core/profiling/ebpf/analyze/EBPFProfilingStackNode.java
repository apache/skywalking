/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.profiling.ebpf.analyze;

import com.google.common.base.Objects;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingStackElement;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTree;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;

/**
 * EBPF profiling data analyze intermediate state data
 */
public class EBPFProfilingStackNode {

    private EBPFProfilingStack.Symbol codeSignature;
    private List<EBPFProfilingStackNode> children;
    private long dumpCount;

    /**
     * create new empty, un-init node
     */
    public static EBPFProfilingStackNode newNode() {
        EBPFProfilingStackNode emptyNode = new EBPFProfilingStackNode();
        emptyNode.children = new ArrayList<>();
        return emptyNode;
    }

    /**
     * accumulate {@link EBPFProfilingStack} to this tree, it will invoke on the tree root node
     */
    public void accumulateFrom(EBPFProfilingStack stack) {
        List<EBPFProfilingStack.Symbol> stackList = stack.getSymbols();
        if (codeSignature == null) {
            codeSignature = stackList.get(0);
        }
        // add detected stack
        this.detectedBy(stack);

        // handle stack children
        EBPFProfilingStackNode parent = this;
        for (int depth = 1; depth < stackList.size(); depth++) {
            EBPFProfilingStack.Symbol elementCodeSignature = stackList.get(depth);

            // find same code signature children
            EBPFProfilingStackNode childElement = null;
            for (EBPFProfilingStackNode child : parent.children) {
                if (Objects.equal(child.codeSignature, elementCodeSignature)) {
                    childElement = child;
                    break;
                }
            }

            if (childElement != null) {
                // add detected stack
                childElement.detectedBy(stack);
                parent = childElement;
            } else {
                // add children
                EBPFProfilingStackNode childNode = newNode();
                childNode.codeSignature = elementCodeSignature;
                childNode.detectedBy(stack);

                parent.children.add(childNode);
                parent = childNode;
            }
        }
    }

    /**
     * combine from other {@link EBPFProfilingStackNode}
     */
    public EBPFProfilingStackNode combine(EBPFProfilingStackNode node) {
        // combine this node
        this.combineDetectedStacks(node);

        // merge tree using LDR to traversal tree node
        // using stack to avoid recursion
        // merge key.children <- value.children
        LinkedList<Tuple2<EBPFProfilingStackNode, EBPFProfilingStackNode>> stack = new LinkedList<>();
        stack.add(Tuple.of(this, node));
        while (!stack.isEmpty()) {
            Tuple2<EBPFProfilingStackNode, EBPFProfilingStackNode> needCombineNode = stack.pop();

            // merge value children to key
            // add to stack if need to keep traversal
            combineChildrenNodes(needCombineNode._1, needCombineNode._2, stack::add);
        }

        return this;
    }

    /**
     * merge all children nodes to appoint node
     */
    private void combineChildrenNodes(EBPFProfilingStackNode targetNode, EBPFProfilingStackNode beingMergedNode,
                                      Consumer<Tuple2<EBPFProfilingStackNode, EBPFProfilingStackNode>> continueChildrenMerging) {
        if (beingMergedNode.children.isEmpty()) {
            return;
        }

        for (EBPFProfilingStackNode childrenNode : targetNode.children) {
            // find node from being merged node children
            for (ListIterator<EBPFProfilingStackNode> it = beingMergedNode.children.listIterator(); it.hasNext(); ) {
                EBPFProfilingStackNode node = it.next();
                if (node != null && node.matches(childrenNode)) {
                    childrenNode.combineDetectedStacks(node);
                    continueChildrenMerging.accept(Tuple.of(childrenNode, node));

                    it.set(null);
                    break;
                }
            }
        }

        for (EBPFProfilingStackNode node : beingMergedNode.children) {
            if (node != null) {
                targetNode.children.add(node);
            }
        }
    }

    /**
     * build GraphQL result, calculate duration and count data using parallels
     */
    public EBPFProfilingTree buildAnalyzeResult() {
        // all nodes add to single-level list (such as flat), work for parallel calculating
        LinkedList<Tuple2<EBPFProfilingStackElement, EBPFProfilingStackNode>> nodeMapping = new LinkedList<>();
        int idGenerator = 1;

        EBPFProfilingStackElement root = buildElement(idGenerator++);
        nodeMapping.add(new Tuple2<>(root, this));

        // same with combine logic
        LinkedList<Tuple2<EBPFProfilingStackElement, EBPFProfilingStackNode>> stack = new LinkedList<>();
        stack.add(Tuple.of(root, this));
        while (!stack.isEmpty()) {
            Tuple2<EBPFProfilingStackElement, EBPFProfilingStackNode> mergingPair = stack.pop();
            EBPFProfilingStackElement respElement = mergingPair._1;

            // generate children node and add to stack and all node mapping
            for (EBPFProfilingStackNode children : mergingPair._2.children) {
                EBPFProfilingStackElement element = children.buildElement(idGenerator++);
                element.setParentId(respElement.getId());

                Tuple2<EBPFProfilingStackElement, EBPFProfilingStackNode> pair = Tuple.of(element, children);
                stack.add(pair);
                nodeMapping.add(pair);
            }
        }

        EBPFProfilingTree tree = new EBPFProfilingTree();
        nodeMapping.forEach(n -> tree.getElements().add(n._1));

        return tree;
    }

    private void detectedBy(EBPFProfilingStack stack) {
        this.dumpCount += stack.getDumpCount();
    }

    private void combineDetectedStacks(EBPFProfilingStackNode node) {
        this.dumpCount += node.dumpCount;
    }

    private EBPFProfilingStackElement buildElement(int id) {
        EBPFProfilingStackElement element = new EBPFProfilingStackElement();
        element.setId(id);
        element.setSymbol(this.codeSignature.getName());
        element.setStackType(this.codeSignature.getStackType());
        element.setDumpCount(this.dumpCount);
        return element;
    }

    private boolean matches(EBPFProfilingStackNode node) {
        return Objects.equal(this.codeSignature, node.codeSignature);
    }

}