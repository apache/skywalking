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

package org.apache.skywalking.apm.toolkit.meter.impl;

import org.apache.skywalking.apm.toolkit.meter.Counter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Using {@link DoubleAdder} to add count
 */
public class CounterImpl extends AbstractMeter implements Counter {

    protected final DoubleAdder count;
    protected final Mode mode;

    private final AtomicReference<Double> previous = new AtomicReference();

    protected CounterImpl(MeterId meterId, Mode mode) {
        super(meterId);
        this.count = new DoubleAdder();
        this.mode = mode;
    }

    /**
     * Increment count
     */
    public void increment(double count) {
        this.count.add(count);
    }

    /**
     * Get count value
     */
    public double get() {
        return this.count.doubleValue();
    }

    /**
     * Using at the Skywalking agent get, make is support {@link Mode#RATE}
     */
    public double agentGet() {
        final double currentValue = get();
        double count;
        if (Objects.equals(Mode.RATE, this.mode)) {
            final Double previousValue = previous.getAndSet(currentValue);

            // calculate the add count
            if (previousValue == null) {
                count = currentValue;
            } else {
                count = currentValue - previousValue;
            }
        } else {
            count = currentValue;
        }

        return count;
    }

    public static class Builder extends AbstractBuilder<Counter.Builder, Counter, CounterImpl> implements Counter.Builder {
        private Counter.Mode mode = Counter.Mode.INCREMENT;

        public Builder(String name) {
            super(name);
        }

        public Builder(MeterId meterId) {
            super(meterId);
        }

        /**
         * Setting counter mode
         */
        public Builder mode(Counter.Mode mode) {
            this.mode = mode;
            return this;
        }

        @Override
        protected void accept(CounterImpl meter) throws IllegalArgumentException {
            // Rate mode must be same
            if (!Objects.equals(meter.mode, this.mode)) {
                throw new IllegalArgumentException("Mode is not same");
            }
        }

        @Override
        protected Counter create(MeterId meterId) {
            return new CounterImpl(meterId, mode);
        }

        @Override
        protected MeterId.MeterType getType() {
            return MeterId.MeterType.COUNTER;
        }
    }
}
