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
import org.apache.skywalking.apm.agent.core.meter.adapter.GaugeAdapter;
import org.apache.skywalking.apm.agent.core.meter.builder.Gauge;
import org.apache.skywalking.apm.agent.core.meter.transform.GaugeTransformer;
import org.apache.skywalking.apm.agent.core.meter.transform.MeterTransformer;

import java.util.function.Supplier;

/**
 * Agent core level gauge adapter
 */
public class InternalGaugeAdapter extends InternalBaseAdapter implements GaugeAdapter, Gauge {
    protected Supplier<Double> getter;

    private InternalGaugeAdapter(MeterId meterId, Supplier<Double> getter) {
        super(meterId);
        this.getter = getter;
    }

    @Override
    public double get() {
        final Double val = getter.get();
        return val == null ? 0.0 : val;
    }

    public static class Builder extends AbstractBuilder<Gauge.Builder, Gauge, GaugeAdapter> implements Gauge.Builder {
        private final Supplier<Double> getter;

        public Builder(String name, Supplier<Double> getter) {
            super(name);
            this.getter = getter;
        }

        @Override
        protected MeterType getType() {
            return MeterType.GAUGE;
        }

        @Override
        protected InternalGaugeAdapter create(MeterId meterId) {
            if (getter == null) {
                throw new IllegalArgumentException("getter cannot be null");
            }
            return new InternalGaugeAdapter(meterId, getter);
        }

        @Override
        protected MeterTransformer<GaugeAdapter> wrapperTransformer(GaugeAdapter adapter) {
            return new GaugeTransformer(adapter);
        }

    }
}
