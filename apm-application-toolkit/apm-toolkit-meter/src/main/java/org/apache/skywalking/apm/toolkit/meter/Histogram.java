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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Histogram represents the distribution of data. It includes the buckets representing continuous ranges of values, with
 * the num of collected values in every specific range. The ranges could start from any value(default 0) to positive
 * infinitive. They can be set through the constructor and immutable after that.
 *
 * The source code of this class doesn't include the implementation, all logic are injected from its activation.
 */
public class Histogram extends BaseMeter {

    protected Histogram(MeterId meterId, List<Double> steps) {
        super(meterId);
    }

    /**
     * Add value into the histogram, automatic analyze what bucket count need to be increment [step1, step2)
     */
    public void addValue(double value) {
    }

    public static class Builder extends BaseBuilder<Builder, Histogram> {
        private double minValue = 0;
        private List<Double> steps;

        public Builder(String name) {
            super(name);
        }

        public Builder(MeterId meterId) {
            super(meterId);
        }

        /**
         * Set bucket steps, the minimal values of every buckets besides the {@link #minValue}.
         */
        public Builder steps(List<Double> steps) {
            this.steps = new ArrayList<>(steps);
            return this;
        }

        /**
         * Set min value, default is zero
         */
        public Builder minValue(double minValue) {
            this.minValue = minValue;
            return this;
        }

        @Override
        protected MeterId.MeterType getType() {
            return MeterId.MeterType.HISTOGRAM;
        }

        @Override
        protected Histogram create() {
            if (steps == null || steps.isEmpty()) {
                throw new IllegalArgumentException("Missing steps setting");
            }

            // sort and distinct the steps
            steps = steps.stream().distinct().sorted().collect(Collectors.toList());

            // verify steps with except min value
            if (steps.get(0) < minValue) {
                throw new IllegalArgumentException("Step[0] must be  bigger than min value");
            } else if (steps.get(0) != minValue) {
                // add the min value to the steps
                steps.add(0, minValue);
            }

            return new Histogram(meterId, steps);
        }
    }

}
