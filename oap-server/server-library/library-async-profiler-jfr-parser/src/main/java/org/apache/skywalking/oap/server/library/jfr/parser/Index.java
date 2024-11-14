/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser;

import java.lang.reflect.Array;
import java.util.HashMap;

public class Index<T> extends HashMap<T, Integer> {
    private final Class<T> cls;

    public Index(Class<T> cls, T empty) {
        this.cls = cls;
        super.put(empty, 0);
    }

    public int index(T key) {
        Integer index = super.get(key);
        if (index != null) {
            return index;
        } else {
            int newIndex = super.size();
            super.put(key, newIndex);
            return newIndex;
        }
    }

    @SuppressWarnings("unchecked")
    public T[] keys() {
        T[] result = (T[]) Array.newInstance(cls, size());
        for (Entry<T, Integer> entry : entrySet()) {
            result[entry.getValue()] = entry.getKey();
        }
        return result;
    }
}
