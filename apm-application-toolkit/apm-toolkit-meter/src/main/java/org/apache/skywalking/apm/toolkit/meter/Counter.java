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

/**
 * A counter is a cumulative metric that represents a single monotonically increasing counter whose value can only increase.
 *
 * The source code of this class doesn't include the implementation, all logic are injected from its activation.
 */
public class Counter extends BaseMeter {

    protected Counter(MeterId meterId, Mode mode) {
        super(meterId);
    }

    /**
     * Increase count
     */
    public void increment(double count) {
    }

    /**
     * Get current value
     */
    public double get() {
        return 0;
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

    public static class Builder extends BaseBuilder<Builder, Counter> {
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
        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        @Override
        protected MeterId.MeterType getType() {
            return MeterId.MeterType.COUNTER;
        }

        @Override
        protected Counter create() {
            return new Counter(meterId, mode);
        }
    }
}
