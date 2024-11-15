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

package org.apache.skywalking.oap.server.library.jfr.parser;

import org.apache.skywalking.oap.server.library.jfr.type.Frame;
import org.apache.skywalking.oap.server.library.jfr.type.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.type.Index;

import java.util.List;

import static org.apache.skywalking.oap.server.library.jfr.type.Frame.TYPE_INTERPRETED;
import static org.apache.skywalking.oap.server.library.jfr.type.Frame.TYPE_NATIVE;

public class JFRMergeBuilder {
    private final Index<String> cpool = new Index<>(String.class, "");
    private final Frame root = new Frame(0, TYPE_NATIVE);

    public JFRMergeBuilder merge(List<FrameTree> trees) {
        if (trees == null || trees.isEmpty()) {
            return this;
        }
        for (FrameTree tree : trees) {
            merge0(root, tree);
        }
        return this;
    }

    public JFRMergeBuilder merge(FrameTree tree) {
        merge0(root, tree);
        return this;
    }

    public void merge0(Frame frame, FrameTree tree) {
        if (tree == null) {
            return;
        }
        if (tree.getChildren() != null) {
            for (FrameTree children : tree.getChildren()) {
                Frame child = addChild(frame, children.getFrame());
                merge0(child, children);
            }
        }
        frame.setTotal(frame.getTotal() + tree.getTotal());
        frame.setSelf(frame.getSelf() + tree.getSelf());
    }

    private Frame addChild(Frame frame, String title) {
        int titleIndex = cpool.index(title);
        return frame.getChild(titleIndex, TYPE_INTERPRETED);
    }

    public FrameTree build() {
        String[] keys = cpool.keys();
        return FrameTree.buildTree(root, keys);
    }
}