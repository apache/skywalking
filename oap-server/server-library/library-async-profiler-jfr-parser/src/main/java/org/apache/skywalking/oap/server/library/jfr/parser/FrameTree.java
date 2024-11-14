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

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class FrameTree {

    private final String frame;
    private final long total;
    private final long self;
    private List<FrameTree> children;

    public FrameTree(Frame frame, String[] key2frame) {
        int titleIndex = frame.getTitleIndex();
        this.frame = key2frame[titleIndex];
        this.total = frame.total;
        this.self = frame.self;
    }

    public static FrameTree buildTree(Frame frame, String[] key2frame) {
        if (frame == null) return null;

        FrameTree frameTree = new FrameTree(frame, key2frame);
        // has children?
        if (!frame.isEmpty()) {
            frameTree.children = new ArrayList<>(frame.size());
            // build tree
            for (Frame childFrame : frame.values()) {
                FrameTree childFrameTree = buildTree(childFrame, key2frame);
                frameTree.children.add(childFrameTree);
            }
        }
        return frameTree;
    }

}
