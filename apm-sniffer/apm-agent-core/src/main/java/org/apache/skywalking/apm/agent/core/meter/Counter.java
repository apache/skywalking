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

package org.apache.skywalking.apm.agent.core.meter;

import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * A counter is a cumulative metric that represents a single monotonically increasing counter whose value can only increase.
 */
public class Counter extends BaseMeter {

    protected final DoubleAdder count;
    protected final CounterMode mode;
    private final AtomicReference<Double> previous = new AtomicReference();

    public Counter(MeterId meterId, CounterMode mode) {
        super(meterId);
        this.count = new DoubleAdder();
        this.mode = mode;
    }

    public void increment(double count) {
        this.count.add(count);
    }

    public double get() {
        return count.doubleValue();
    }

    @Override
    public MeterData.Builder transform() {
        // using rate mode or increase
        final double currentValue = get();
        double count;
        if (Objects.equals(mode, CounterMode.RATE)) {
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

        final MeterData.Builder builder = MeterData.newBuilder();
        builder.setSingleValue(MeterSingleValue.newBuilder()
            .setName(getName())
            .addAllLabels(transformTags())
            .setValue(count).build());

        return builder;
    }

    /**
     * Counter mode
     */
    public enum Mode {
        /**
         * Increase single value, report the real value
         */
        INCREMENT,

        /**
         * Rate with previous value when report
         */
        RATE
    }

    public static class Builder extends AbstractBuilder<Builder, Counter> {
        private CounterMode mode = CounterMode.INCREMENT;

        public Builder(String name) {
            super(name);
        }

        /**
         * Setting counter mode
         */
        public Builder mode(CounterMode mode) {
            this.mode = mode;
            return this;
        }

        @Override
        protected Counter create(MeterId meterId) {
            return new Counter(meterId, mode);
        }

        @Override
        protected MeterType getType() {
            return MeterType.COUNTER;
        }

    }
}
