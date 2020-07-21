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

import org.apache.skywalking.apm.toolkit.meter.Gauge;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.function.Supplier;

/**
 * Using {@link Supplier} as the value.
 */
public class GaugeImpl extends AbstractMeter implements Gauge {

    protected Supplier<Double> getter;

    public GaugeImpl(MeterId meterId, Supplier<Double> getter) {
        super(meterId);
        this.getter = getter;
    }

    /**
     * Get count
     */
    public double get() {
        return getter.get();
    }

    public static class Builder extends AbstractBuilder<Gauge.Builder, Gauge, GaugeImpl> implements Gauge.Builder {
        private final Supplier<Double> getter;

        public Builder(String name, Supplier<Double> getter) {
            super(name);
            this.getter = getter;
        }

        public Builder(MeterId meterId, Supplier<Double> getter) {
            super(meterId);
            this.getter = getter;
        }

        @Override
        public void accept(GaugeImpl meter) {
            if (this.getter != meter.getter) {
                throw new IllegalArgumentException("Getter is not same");
            }
        }

        @Override
        public GaugeImpl create(MeterId meterId) {
            if (getter == null) {
                throw new IllegalArgumentException("getter cannot be null");
            }
            return new GaugeImpl(meterId, getter);
        }

        @Override
        public MeterId.MeterType getType() {
            return MeterId.MeterType.GAUGE;
        }
    }

}
