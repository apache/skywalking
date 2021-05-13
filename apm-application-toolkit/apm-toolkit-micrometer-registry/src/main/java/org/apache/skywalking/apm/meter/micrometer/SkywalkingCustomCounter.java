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

package org.apache.skywalking.apm.meter.micrometer;

import io.micrometer.core.instrument.Measurement;
import org.apache.skywalking.apm.toolkit.meter.BaseBuilder;
import org.apache.skywalking.apm.toolkit.meter.Counter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

/**
 * Work for custom {@link Measurement}, support the skywalking rate mode
 */
public class SkywalkingCustomCounter extends Counter {

    private final Measurement measurement;

    protected SkywalkingCustomCounter(MeterId meterId, Measurement measurement, SkywalkingConfig config) {
        super(meterId, MeterBuilder.getCounterMode(meterId, config));
        this.measurement = measurement;
    }

    @Override
    public double get() {
        return measurement.getValue();
    }

    /**
     * Custom counter builder
     */
    public static class Builder extends BaseBuilder<Builder, Counter> {
        private final Measurement measurement;
        private final SkywalkingConfig config;

        public Builder(MeterId meterId, Measurement measurement, SkywalkingConfig config) {
            super(meterId);
            this.measurement = measurement;
            this.config = config;
        }

        @Override
        protected MeterId.MeterType getType() {
            return MeterId.MeterType.COUNTER;
        }

        @Override
        protected Counter create() {
            return new SkywalkingCustomCounter(meterId, measurement, config);
        }
    }
}
