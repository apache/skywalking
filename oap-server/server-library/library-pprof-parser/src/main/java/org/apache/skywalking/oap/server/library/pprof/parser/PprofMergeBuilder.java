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
import org.apache.skywalking.oap.server.library.pprof.type.Frame;
import org.apache.skywalking.oap.server.library.pprof.type.Index;
import java.util.List;

public class PprofMergeBuilder {
    private final Index<String> cpool = new Index<>(String.class, "");
    private final Frame root = new Frame("root");

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

    private void merge0(Frame frame, FrameTree tree) {
        if (tree == null) {
            return;
        }
        if (tree.getChildren() != null) {
            for (FrameTree childTree : tree.getChildren()) {
                Frame child = addChild(frame, childTree.getSignature());
                merge0(child, childTree);
            }
        }
        frame.setTotal(frame.getTotal() + tree.getTotal());
        frame.setSelf(frame.getSelf() + tree.getSelf());
    }

    private Frame addChild(Frame parent, String signature) {
        int titleIndex = cpool.index(signature);
        return parent.getChild(titleIndex, signature);
    }

    public FrameTree build() {
        return toFrameTree(root);
    }

    private FrameTree toFrameTree(Frame node) {
        FrameTree tree = new FrameTree(node.getSignature(), node.getTotal(), node.getSelf());
        for (Frame child : node.values()) {
            tree.getChildren().add(toFrameTree(child));
        }
        return tree;
    }

}
