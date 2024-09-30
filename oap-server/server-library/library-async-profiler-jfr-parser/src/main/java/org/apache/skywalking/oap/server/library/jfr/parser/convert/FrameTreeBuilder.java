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

package org.apache.skywalking.oap.server.library.jfr.parser.convert;

import java.util.Comparator;
import java.util.regex.Pattern;

import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_C1_COMPILED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_INLINED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_INTERPRETED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_JIT_COMPILED;
import static org.apache.skywalking.oap.server.library.jfr.parser.convert.Frame.TYPE_NATIVE;

public class FrameTreeBuilder implements Comparator<Frame> {
    private static final Frame[] EMPTY_FRAME_ARRAY = {};
    private static final String[] FRAME_SUFFIX = {"_[0]", "_[j]", "_[i]", "", "", "_[k]", "_[1]"};
    private static final byte HAS_SUFFIX = (byte) 0x80;
    private static final int FLUSH_THRESHOLD = 15000;

    private final Arguments args;
    private final Index<String> cpool = new Index<>(String.class, "");
    private final Frame root = new Frame(0, TYPE_NATIVE);
    private final StringBuilder outbuf = new StringBuilder(FLUSH_THRESHOLD + 1000);
    private int[] order;
    private int depth;
    private int lastLevel;
    private long lastX;
    private long lastTotal;
    private long mintotal;

    public FrameTreeBuilder(Arguments args) {
        this.args = args;
    }

    public void addSample(CallStack stack, long ticks) {
        if (excludeStack(stack)) {
            return;
        }

        Frame frame = root;
        if (args.reverse) {
            for (int i = stack.size; --i >= args.skip; ) {
                frame = addChild(frame, stack.names[i], stack.types[i], ticks);
            }
        } else {
            for (int i = args.skip; i < stack.size; i++) {
                frame = addChild(frame, stack.names[i], stack.types[i], ticks);
            }
        }
        frame.total += ticks;
        frame.self += ticks;

        depth = Math.max(depth, stack.size);
    }

    private boolean excludeStack(CallStack stack) {
        Pattern include = args.include;
        Pattern exclude = args.exclude;
        if (include == null && exclude == null) {
            return false;
        }

        for (int i = 0; i < stack.size; i++) {
            if (exclude != null && exclude.matcher(stack.names[i]).matches()) {
                return true;
            }
            if (include != null && include.matcher(stack.names[i]).matches()) {
                if (exclude == null) return false;
                include = null;
            }
        }

        return include != null;
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

    @Override
    public int compare(Frame f1, Frame f2) {
        return order[f1.getTitleIndex()] - order[f2.getTitleIndex()];
    }

}
