/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package one.jfr;

import lombok.Getter;

import java.util.Arrays;

/**
 * This class is placed in the one.jfr package because some classes and fields of the async-profiler-converter package
 * can only be accessed under the same package name, and what we want to do is to expand it, so here we choose to create
 * a class with the same package name for extension.
 */
@Getter
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
