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


package org.apache.skywalking.apm.commons.datacarrier.common;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

public class AtomicRangeInteger extends Number implements java.io.Serializable {

    private static final long serialVersionUID = 4099792402691141643L;

    private static final Unsafe UNSAFE;
    private static final int SHIFT;
    private static final int BASE;
    private static final int VALUES_LENGTH = 31;
    private static final int VALUES_OFFSET = 15;

    private final int[] values;
    private final int startValue;
    private final int endValue;

    static {
        try
        {
            final PrivilegedExceptionAction<Unsafe> action = new PrivilegedExceptionAction<Unsafe>() {
                @Override
                public Unsafe run() throws Exception {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    return (Unsafe) theUnsafe.get(null);
                }
            };

            UNSAFE = AccessController.doPrivileged(action);

            int scale = UNSAFE.arrayIndexScale(int[].class);
            if ((scale & (scale - 1)) != 0) {
                throw new Error("data type scale not a power of two");
            }

            SHIFT = 31 - Integer.numberOfLeadingZeros(scale);
            BASE = UNSAFE.arrayBaseOffset(int[].class);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load unsafe", e);
        }
    }

    public AtomicRangeInteger(int startValue, int maxValue) {

        this.values = new int[VALUES_LENGTH];
        this.values[VALUES_OFFSET] = startValue;

        this.startValue = startValue;
        this.endValue = maxValue - 1;
    }

    public final int getAndIncrement() {
        int next;
        do {
            next = this.incrementAndGet(VALUES_OFFSET);
            if (next > endValue && this.compareAndSet(VALUES_OFFSET, next, startValue)) {
                return endValue;
            }
        } while (next > endValue);

        return next - 1;
    }

    private final boolean compareAndSet(int i, int expect, int update) {
        return UNSAFE.compareAndSwapInt(values, getByteOffset(i), expect, update);
    }

    private final int incrementAndGet(int i) {
        return UNSAFE.getAndAddInt(values, getByteOffset(i), 1) + 1;
    }

    private long getByteOffset(int i) {
        return ((long) i << SHIFT) + BASE;
    }

    public final int get() {
        return this.values[VALUES_OFFSET];
    }

    @Override
    public int intValue() {
        return this.values[VALUES_OFFSET];
    }

    @Override
    public long longValue() {
        return this.values[VALUES_OFFSET];
    }

    @Override
    public float floatValue() {
        return this.values[VALUES_OFFSET];
    }

    @Override
    public double doubleValue() {
        return this.values[VALUES_OFFSET];
    }
}
