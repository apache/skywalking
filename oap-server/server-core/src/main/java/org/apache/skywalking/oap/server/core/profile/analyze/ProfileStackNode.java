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
import java.util.stream.Collectors;

/**
 * Work for profiling stacks, intermediate state of the {@link ProfileStackElement} and {@link ProfileStack}
 */
public class ProfileStackNode {

    // stack code signature
    private String codeSignature;
    // owner list
    private List<ProfileStack> owners;
    // children nodes
    private List<ProfileStackNode> children;
    // include the execution time of children
    private int duration;

    /**
     * create new empty, un-init node
     * @return
     */
    public static ProfileStackNode createEmptyNode() {
        ProfileStackNode emptyNode = new ProfileStackNode();
        emptyNode.owners = new LinkedList<>();
        emptyNode.children = new LinkedList<>();
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
        // increase self
        this.increase(stack);

        // handle stack children
        ProfileStackNode parent = this;
        for (int childDepth = 1; childDepth < stackList.size(); childDepth++) {
            String elementCodeSign = stackList.get(childDepth);

            // find same code signature children
            ProfileStackNode childElement = null;
            for (ProfileStackNode child : parent.children) {
                if (Objects.equal(child.codeSignature, elementCodeSign)) {
                    childElement = child;
                    break;
                }
            }

            if (childElement != null) {
                // increase child node
                childElement.increase(stack);
                parent = childElement;
            } else {
                // add children
                ProfileStackNode childNode = createEmptyNode();
                childNode.codeSignature = elementCodeSign;
                childNode.increase(stack);

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
        this.combineThisNode(node);

        // merge tree using pre order
        LinkedList<Pair<ProfileStackNode, List<ProfileStackNode>>> stack = new LinkedList<>();
        stack.add(new Pair<>(this, node.children));
        while (!stack.isEmpty()) {
            Pair<ProfileStackNode, List<ProfileStackNode>> needCombineNode = stack.pop();
            ProfileStackNode combineTo = needCombineNode.key;

            // merge node
            for (ProfileStackNode needCombineChildren : needCombineNode.value) {
                boolean sameReference = false;
                // find node
                ProfileStackNode combinedNode = combineTo.children.stream().filter(n -> needCombineChildren.matches(n)).findFirst().orElse(null);

                if (combinedNode == null) {
                    combineTo.children.add(needCombineChildren);
                    combinedNode = needCombineChildren;
                } else if (needCombineChildren != combinedNode) {
                    combinedNode.combineThisNode(needCombineChildren);
                } else {
                    // if same reference, dont need to combine anymore
                    sameReference = true;
                }

                if (!sameReference) {
                    stack.add(new Pair<>(combinedNode, needCombineChildren.children));
                }
            }
        }

        return this;
    }

    /**
     * build GraphQL result, calculate duration and count data using parallels
     * @return
     */
    public ProfileStackElement buildAnalyzeResult() {
        // all nodes add to one list, work for parallel calculating
        LinkedList<Pair<ProfileStackNode, ProfileStackElement>> allNodes = new LinkedList<>();
        ProfileStackElement root = buildElement();
        allNodes.add(new Pair<>(this, root));

        // using pre order, build element tree
        LinkedList<Pair<ProfileStackElement, List<ProfileStackNode>>> stack = new LinkedList<>();
        stack.add(new Pair<>(root, this.children));
        while (!stack.isEmpty()) {
            Pair<ProfileStackElement, List<ProfileStackNode>> needCombineNode = stack.pop();
            ProfileStackElement combineTo = needCombineNode.key;

            combineTo.setChilds(needCombineNode.value.stream().map(c -> {
                ProfileStackElement element = c.buildElement();
                stack.add(new Pair<>(element, c.children));

                allNodes.add(new Pair<>(c, element));

                return element;
            }).collect(Collectors.toList()));
        }

        // calculate durations
        allNodes.parallelStream().forEach(t -> t.key.calculateDuration(t.value));
        allNodes.parallelStream().forEach(t -> t.key.calculateDurationExcludeChild(t.value));

        return root;
    }

    private void increase(ProfileStack stack) {
        this.owners.add(stack);
    }

    private void combineThisNode(ProfileStackNode node) {
        this.owners.addAll(node.owners);
    }

    private ProfileStackElement buildElement() {
        ProfileStackElement element = new ProfileStackElement();
        element.setCodeSignature(this.codeSignature);
        element.setChilds(new LinkedList<>());
        element.setCount(this.owners.size());
        return element;
    }

    /**
     * calculate duration to {@link ProfileStackElement#getDuration()}
     */
    private void calculateDuration(ProfileStackElement element) {
        if (this.owners.size() <= 1) {
            element.setDuration(0);
            return;
        }

        // sort owners
        Collections.sort(this.owners);

        // calculate time windows duration
        ProfileStack currentTimeWindowStartStack = owners.get(0);
        ProfileStack currentTimeWindowEndTack = owners.get(0);
        long duration = 0;
        for (ListIterator<ProfileStack> it = owners.listIterator(1); it.hasNext(); ) {
            ProfileStack currentStack = it.next();

            // is continuity
            if (currentTimeWindowEndTack.getSequence() + 1 != currentStack.getSequence()) {
                duration += currentTimeWindowEndTack.getDumpTime() - currentTimeWindowStartStack.getDumpTime();
                currentTimeWindowStartStack = currentTimeWindowEndTack;
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
