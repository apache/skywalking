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

package org.apache.skywalking.oap.server.core.profile.analyze;

import com.google.common.base.Objects;
import org.apache.skywalking.oap.server.core.query.entity.ProfileStackElement;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Work for profiling stacks, intermediate state of the {@link ProfileStackElement} and {@link ProfileStack}
 */
public class ProfileStackNode {

    private String codeSignature;
    private List<ProfileStack> detectedStacks;
    private List<ProfileStackNode> children;
    private int duration;

    /**
     * create new empty, un-init node
     * @return
     */
    public static ProfileStackNode newNode() {
        ProfileStackNode emptyNode = new ProfileStackNode();
        emptyNode.detectedStacks = new LinkedList<>();
        emptyNode.children = new ArrayList<>();
        return emptyNode;
    }

    /**
     * accumulate {@link ProfileStack} to this tree, it will invoke on the tree root node
     * @param stack
     */
    public void accumulateFrom(ProfileStack stack) {
        List<String> stackList = stack.getStack();
        if (codeSignature == null) {
            codeSignature = stackList.get(0);
        }
        // add detected stack
        this.detectedBy(stack);

        // handle stack children
        ProfileStackNode parent = this;
        for (int depth = 1; depth < stackList.size(); depth++) {
            String elementCodeSignature = stackList.get(depth);

            // find same code signature children
            ProfileStackNode childElement = null;
            for (ProfileStackNode child : parent.children) {
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
                ProfileStackNode childNode = newNode();
                childNode.codeSignature = elementCodeSignature;
                childNode.detectedBy(stack);

                parent.children.add(childNode);
                parent = childNode;
            }
        }
    }

    /**
     * combine from other {@link ProfileStackNode}
     * @param node
     * @return
     */
    public ProfileStackNode combine(ProfileStackNode node) {
        // combine this node
        this.combineDetectedStacks(node);

        // merge tree using LDR to traversal tree node
        // using stack to avoid recursion
        // merge key.children <- value.children
        LinkedList<Pair<ProfileStackNode, ProfileStackNode>> stack = new LinkedList<>();
        stack.add(new Pair<>(this, node));
        while (!stack.isEmpty()) {
            Pair<ProfileStackNode, ProfileStackNode> needCombineNode = stack.pop();

            // merge value children to key
            // add to stack if need to keep traversal
            combineChildrenNodes(needCombineNode.key, needCombineNode.value, stack::add);
        }

        return this;
    }

    /**
     * merge all children nodes to appoint node
     * @param targetNode
     * @param beingMergedNode
     * @param continueChildrenMerging
     */
    private void combineChildrenNodes(ProfileStackNode targetNode, ProfileStackNode beingMergedNode, Consumer<Pair<ProfileStackNode, ProfileStackNode>> continueChildrenMerging) {
        if (beingMergedNode.children.isEmpty()) {
            return;
        }

        for (ProfileStackNode childrenNode : targetNode.children) {
            // find node from being merged node children
            for (ListIterator<ProfileStackNode> it = beingMergedNode.children.listIterator(); it.hasNext();) {
                ProfileStackNode node = it.next();
                if (node != null && node.matches(childrenNode)) {
                    childrenNode.combineDetectedStacks(node);
                    continueChildrenMerging.accept(new Pair<>(childrenNode, node));

                    it.set(null);
                    break;
                }
            }
        }

        for (ProfileStackNode node : beingMergedNode.children) {
            if (node != null) {
                targetNode.children.add(node);
            }
        }
    }

    /**
     * build GraphQL result, calculate duration and count data using parallels
     * @return
     */
    public ProfileStackElement buildAnalyzeResult() {
        // all nodes add to single-level list (such as flat), work for parallel calculating
        LinkedList<Pair<ProfileStackElement, ProfileStackNode>> nodeMapping = new LinkedList<>();
        ProfileStackElement root = buildElement();
        nodeMapping.add(new Pair<>(root, this));

        // same with combine logic
        LinkedList<Pair<ProfileStackElement, ProfileStackNode>> stack = new LinkedList<>();
        stack.add(new Pair<>(root, this));
        while (!stack.isEmpty()) {
            Pair<ProfileStackElement, ProfileStackNode> mergingPair = stack.pop();
            ProfileStackElement respElement = mergingPair.key;

            // generate children node and add to stack and all node mapping
            respElement.setChildren(mergingPair.value.children.stream().map(c -> {
                ProfileStackElement element = c.buildElement();
                Pair<ProfileStackElement, ProfileStackNode> pair = new Pair<>(element, c);
                stack.add(pair);
                nodeMapping.add(pair);

                return element;
            }).collect(Collectors.toList()));
        }

        // calculate durations
        nodeMapping.parallelStream().forEach(t -> t.value.calculateDuration(t.key));
        nodeMapping.parallelStream().forEach(t -> t.value.calculateDurationExcludeChild(t.key));

        return root;
    }

    private void detectedBy(ProfileStack stack) {
        this.detectedStacks.add(stack);
    }

    private void combineDetectedStacks(ProfileStackNode node) {
        this.detectedStacks.addAll(node.detectedStacks);
    }

    private ProfileStackElement buildElement() {
        ProfileStackElement element = new ProfileStackElement();
        element.setCodeSignature(this.codeSignature);
        element.setChildren(new LinkedList<>());
        element.setCount(this.detectedStacks.size());
        return element;
    }

    /**
     * calculate duration to {@link ProfileStackElement#getDuration()}
     */
    private void calculateDuration(ProfileStackElement element) {
        if (this.detectedStacks.size() <= 1) {
            element.setDuration(0);
            return;
        }

        Collections.sort(this.detectedStacks);

        // calculate time windows duration
        ProfileStack currentTimeWindowStartStack = detectedStacks.get(0);
        ProfileStack currentTimeWindowEndTack = detectedStacks.get(0);
        long duration = 0;
        for (ListIterator<ProfileStack> it = detectedStacks.listIterator(1); it.hasNext(); ) {
            ProfileStack currentStack = it.next();

            // is continuity
            if (currentTimeWindowEndTack.getSequence() + 1 != currentStack.getSequence()) {
                duration += currentTimeWindowEndTack.getDumpTime() - currentTimeWindowStartStack.getDumpTime();
                currentTimeWindowStartStack = currentStack;
            }

            currentTimeWindowEndTack = currentStack;
        }

        // calculate last one time windows
        duration += currentTimeWindowEndTack.getDumpTime() - currentTimeWindowStartStack.getDumpTime();

        this.duration = Math.toIntExact(duration);
        element.setDuration(this.duration);
    }

    /**
     * calculate duration to {@link ProfileStackElement#getDurationChildExcluded()}, expends on {@link #calculateDuration(ProfileStackElement)}
     * @param element
     */
    private void calculateDurationExcludeChild(ProfileStackElement element) {
        element.setDurationChildExcluded(element.getDuration() - children.stream().mapToInt(t -> t.duration).sum());
    }

    private boolean matches(ProfileStackNode node) {
        return Objects.equal(this.codeSignature, node.codeSignature);
    }

    private static class Pair<K, V> {
        private final K key;
        private final V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

}
