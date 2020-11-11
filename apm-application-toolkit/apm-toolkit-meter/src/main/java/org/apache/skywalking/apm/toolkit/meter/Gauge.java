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

import java.util.function.Supplier;

/**
 * A gauge is a metric that represents a single numerical value that can arbitrarily go up and down.
 *
 * The source code of this class doesn't include the implementation, all logic are injected from its activation.
 */
public class Gauge extends BaseMeter {

    protected Gauge(MeterId meterId, Supplier<Double> getter) {
        super(meterId);
    }

    /**
     * Get current value
     */
    public double get() {
        return 0;
    }

    public static class Builder extends BaseBuilder<Builder, Gauge> {
        protected Supplier<Double> getter;

        public Builder(String name, Supplier<Double> getter) {
            super(name);
            this.getter = getter;
        }

        public Builder(MeterId meterId, Supplier<Double> getter) {
            super(meterId);
            this.getter = getter;
        }

        @Override
        protected MeterId.MeterType getType() {
            return MeterId.MeterType.GAUGE;
        }

        @Override
        protected Gauge create() {
            return new Gauge(meterId, getter);
        }
    }

}
