/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

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

import java.util.HashMap;

public class Frame extends HashMap<Integer, Frame> {
    public static final byte TYPE_INTERPRETED = 0;
    public static final byte TYPE_JIT_COMPILED = 1;
    public static final byte TYPE_INLINED = 2;
    public static final byte TYPE_NATIVE = 3;
    public static final byte TYPE_CPP = 4;
    public static final byte TYPE_KERNEL = 5;
    public static final byte TYPE_C1_COMPILED = 6;

    private static final int TYPE_SHIFT = 28;

    final int key;
    long total;
    long self;
    long inlined;
    long c1;
    long interpreted;

    private Frame(int key) {
        this.key = key;
    }

    Frame(int titleIndex, byte type) {
        this(titleIndex | type << TYPE_SHIFT);
    }

    Frame getChild(int titleIndex, byte type) {
        return super.computeIfAbsent(titleIndex | type << TYPE_SHIFT, Frame::new);
    }

    int getTitleIndex() {
        return key & ((1 << TYPE_SHIFT) - 1);
    }

    byte getType() {
        if (inlined * 3 >= total) {
            return TYPE_INLINED;
        } else if (c1 * 2 >= total) {
            return TYPE_C1_COMPILED;
        } else if (interpreted * 2 >= total) {
            return TYPE_INTERPRETED;
        } else {
            return (byte) (key >>> TYPE_SHIFT);
        }
    }

    int depth(long cutoff) {
        int depth = 0;
        if (size() > 0) {
            for (Frame child : values()) {
                if (child.total >= cutoff) {
                    depth = Math.max(depth, child.depth(cutoff));
                }
            }
        }
        return depth + 1;
    }
}
