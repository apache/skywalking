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

import one.jfr.CallStack;

import static org.apache.skywalking.oap.server.library.jfr.parser.Frame.TYPE_C1_COMPILED;
import static org.apache.skywalking.oap.server.library.jfr.parser.Frame.TYPE_INLINED;
import static org.apache.skywalking.oap.server.library.jfr.parser.Frame.TYPE_INTERPRETED;
import static org.apache.skywalking.oap.server.library.jfr.parser.Frame.TYPE_JIT_COMPILED;
import static org.apache.skywalking.oap.server.library.jfr.parser.Frame.TYPE_NATIVE;

public class FrameTreeBuilder {
    private final Index<String> cpool = new Index<>(String.class, "all");
    private final Frame root = new Frame(0, TYPE_NATIVE);
    private int depth;

    public void addSample(CallStack stack, long ticks) {
        Frame frame = root;
        int size = stack.getSize();
        for (int i = 0; i < size; i++) {
            frame = addChild(frame, stack.getNames()[i], stack.getTypes()[i], ticks);
        }

        frame.total += ticks;
        frame.self += ticks;

        depth = Math.max(depth, size);
    }

    private Frame addChild(Frame frame, String title, byte type, long ticks) {
        frame.total += ticks;

        int titleIndex = cpool.index(title);

        Frame child;
        switch (type) {
            case TYPE_INTERPRETED:
                (child = frame.getChild(titleIndex, TYPE_JIT_COMPILED)).interpreted += ticks;
                break;
            case TYPE_INLINED:
                (child = frame.getChild(titleIndex, TYPE_JIT_COMPILED)).inlined += ticks;
                break;
            case TYPE_C1_COMPILED:
                (child = frame.getChild(titleIndex, TYPE_JIT_COMPILED)).c1 += ticks;
                break;
            default:
                child = frame.getChild(titleIndex, type);
        }
        return child;
    }

    public FrameTree build() {
        String[] keys = cpool.keys();
        return FrameTree.buildTree(root, keys);
    }

}
