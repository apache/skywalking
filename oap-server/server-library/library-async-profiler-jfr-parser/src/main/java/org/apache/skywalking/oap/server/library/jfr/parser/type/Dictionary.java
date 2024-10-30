/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.type;

/**
 * Fast and compact long->Object map.
 */
public class Dictionary<T> {
    private static final int INITIAL_CAPACITY = 16;

    private long[] keys;
    private Object[] values;
    private int size;

    public Dictionary() {
        this.keys = new long[INITIAL_CAPACITY];
        this.values = new Object[INITIAL_CAPACITY];
    }

    public void clear() {
        keys = new long[INITIAL_CAPACITY];
        values = new Object[INITIAL_CAPACITY];
        size = 0;
    }

    public void put(long key, T value) {
        if (key == 0) {
            throw new IllegalArgumentException("Zero key not allowed");
        }

        int mask = keys.length - 1;
        int i = hashCode(key) & mask;
        while (keys[i] != 0) {
            if (keys[i] == key) {
                values[i] = value;
                return;
            }
            i = (i + 1) & mask;
        }
        keys[i] = key;
        values[i] = value;

        if (++size * 2 > keys.length) {
            resize(keys.length * 2);
        }
    }

    @SuppressWarnings("unchecked")
    public T get(long key) {
        int mask = keys.length - 1;
        int i = hashCode(key) & mask;
        while (keys[i] != key && keys[i] != 0) {
            i = (i + 1) & mask;
        }
        return (T) values[i];
    }

    @SuppressWarnings("unchecked")
    public void forEach(Visitor<T> visitor) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != 0) {
                visitor.visit(keys[i], (T) values[i]);
            }
        }
    }

    public int preallocate(int count) {
        if (count * 2 > keys.length) {
            resize(Integer.highestOneBit(count * 4 - 1));
        }
        return count;
    }

    private void resize(int newCapacity) {
        long[] newKeys = new long[newCapacity];
        Object[] newValues = new Object[newCapacity];
        int mask = newKeys.length - 1;

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != 0) {
                for (int j = hashCode(keys[i]) & mask; ; j = (j + 1) & mask) {
                    if (newKeys[j] == 0) {
                        newKeys[j] = keys[i];
                        newValues[j] = values[i];
                        break;
                    }
                }
            }
        }

        keys = newKeys;
        values = newValues;
    }

    private static int hashCode(long key) {
        key *= 0xc6a4a7935bd1e995L;
        return (int) (key ^ (key >>> 32));
    }

    public interface Visitor<T> {
        void visit(long key, T value);
    }
}
