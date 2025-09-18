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

package org.apache.skywalking.oap.server.library.pprof.parser;

import org.apache.skywalking.oap.server.library.pprof.type.FrameTree;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PprofMergeBuilder {
    private final Node root = new Node("root");

    public PprofMergeBuilder merge(List<FrameTree> trees) {
        if (trees == null || trees.isEmpty()) {
            return this;
        }
        for (FrameTree tree : trees) {
            merge0(root, tree);
        }
        return this;
    }

    public PprofMergeBuilder merge(FrameTree tree) {
        merge0(root, tree);
        return this;
    }

    private void merge0(Node node, FrameTree tree) {
        if (tree == null) {
            return;
        }
        if (tree.getChildren() != null) {
            for (FrameTree childTree : tree.getChildren()) {
                Node child = getOrAddChild(node, childTree.getSignature());
                merge0(child, childTree);
            }
        }
        node.total += tree.getTotal();
        node.self += tree.getSelf();
    }

    private Node getOrAddChild(Node parent, String signature) {
        return parent.children.computeIfAbsent(signature, Node::new);
    }

    public FrameTree build() {
        return toFrameTree(root);
    }

    private FrameTree toFrameTree(Node node) {
        FrameTree tree = new FrameTree(node.signature, node.total, node.self);
        for (Node child : node.children.values()) {
            tree.getChildren().add(toFrameTree(child));
        }
        return tree;
    }

    private static class Node {
        final String signature;
        long total;
        long self;
        final Map<String, Node> children = new HashMap<>();

        Node(String signature) {
            this.signature = signature;
        }
    }
}
