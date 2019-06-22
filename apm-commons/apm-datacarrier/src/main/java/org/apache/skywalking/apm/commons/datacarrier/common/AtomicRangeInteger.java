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
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by lkxiaolou on 2019/06/22.
 */
public class AtomicRangeInteger extends Number implements Serializable {
    private static final long serialVersionUID = -4099792402691141643L;

    /**
     * please dont't change the left and right Placeholders
     * to disable false-sharing for more performance
     */
    private int[] leftPlaceholders;
    private AtomicInteger value;
    private int[] rightPlaceholders;

    private int startValue;
    private int endValue;

    public AtomicRangeInteger(int startValue, int maxValue) {
        this.value = new AtomicInteger(startValue);
        this.startValue = startValue;
        this.endValue = maxValue - 1;
        leftPlaceholders = new int[15];
        rightPlaceholders = new int[15];
    }

    public final int getAndIncrement() {
        int next;
        do {
            next = this.value.incrementAndGet();
            if (next > endValue && this.value.compareAndSet(next, startValue)) {
                return endValue;
            }
        } while (next > endValue);

        return next - 1;
    }

    public final int get() {
        return this.value.get();
    }

    public int intValue() {
        return this.value.intValue();
    }

    public long longValue() {
        return this.value.longValue();
    }

    public float floatValue() {
        return this.value.floatValue();
    }

    public double doubleValue() {
        return this.value.doubleValue();
    }
}
