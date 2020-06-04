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

package org.apache.skywalking.apm.toolkit.meter;

import java.util.concurrent.atomic.DoubleAdder;

public class Counter extends BaseMeter {

    protected final DoubleAdder count;

    /**
     * Create a counter builder by name
     */
    public static Builder create(String name) {
        return new Builder(name);
    }

    protected Counter(MeterId meterId) {
        super(meterId);
        this.count = new DoubleAdder();
    }

    /**
     * Increment count
     */
    public void increment(double count) {
        this.count.add(count);
    }

    /**
     * Get count value
     * @return
     */
    public double get() {
        return this.count.doubleValue();
    }

    /**
     * Builder the counter
     */
    public static class Builder extends BaseMeter.Builder<Counter> {

        public Builder(String name) {
            super(name);
        }

        public Builder(MeterId meterId) {
            super(meterId);
        }

        @Override
        public void accept(Counter counter) {
        }

        @Override
        public Counter create(MeterId meterId) {
            return new Counter(meterId);
        }

        @Override
        public MeterId.MeterType getType() {
            return MeterId.MeterType.COUNTER;
        }
    }
}
