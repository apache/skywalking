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

package org.apache.skywalking.apm.agent.core.meter.builder.adapter;

import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.agent.core.meter.adapter.CounterAdapter;
import org.apache.skywalking.apm.agent.core.meter.builder.Counter;
import org.apache.skywalking.apm.agent.core.meter.transform.CounterTransformer;
import org.apache.skywalking.apm.agent.core.meter.transform.MeterTransformer;

import java.util.Objects;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Agent core level counter adapter
 */
public class InternalCounterAdapter extends InternalBaseAdapter implements CounterAdapter, Counter {

    protected final DoubleAdder count;
    protected final Counter.Mode mode;

    private InternalCounterAdapter(MeterId meterId, Counter.Mode mode) {
        super(meterId);
        this.count = new DoubleAdder();
        this.mode = mode;
    }

    @Override
    public double getCount() {
        return count.doubleValue();
    }

    @Override
    public boolean usingRate() {
        return Objects.equals(mode, Mode.RATE);
    }

    @Override
    public void increment(double count) {
        this.count.add(count);
    }

    public static class Builder extends AbstractBuilder<Counter.Builder, Counter, CounterAdapter> implements Counter.Builder {
        private Counter.Mode mode = Counter.Mode.INCREMENT;

        public Builder(String name) {
            super(name);
        }

        /**
         * Setting counter mode
         */
        public Builder mode(Counter.Mode mode) {
            this.mode = mode;
            return this;
        }

        @Override
        protected CounterAdapter create(MeterId meterId) {
            return new InternalCounterAdapter(meterId, mode);
        }

        @Override
        protected MeterTransformer<CounterAdapter> wrapperTransformer(CounterAdapter adapter) {
            return new CounterTransformer(adapter);
        }

        @Override
        protected MeterType getType() {
            return MeterType.COUNTER;
        }

    }
}
