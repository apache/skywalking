/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.convert;

import java.util.Arrays;

public class CallStack {
    String[] names = new String[16];
    byte[] types = new byte[16];
    int size;

    public void push(String name, byte type) {
        if (size >= names.length) {
            names = Arrays.copyOf(names, size * 2);
            types = Arrays.copyOf(types, size * 2);
        }
        names[size] = name;
        types[size] = type;
        size++;
    }

    public void pop() {
        size--;
    }

    public void clear() {
        size = 0;
    }
}
