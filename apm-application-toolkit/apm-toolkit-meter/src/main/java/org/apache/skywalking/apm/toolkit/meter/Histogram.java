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

public class Histogram extends BaseMeter {

    private List<Integer> steps;

    public static Histogram.Builder create(String name) {
        return new Builder(name);
    }

    private Histogram(String name, List<Tag> tags, List<Integer> steps) {
        super(name, tags);
        this.steps = steps;
    }

    /**
     * Add count into the step
     */
    public void addCountToStep(int step, long count) {
    }

    /**
     * Add value into the histogram, automatic analyze what bucket count need to be increment
     * [step1, step2)
     */
    public void addValue(int value) {
    }

    public static class Builder {
        private final String name;
        private List<Tag> tags = new ArrayList<>();
        private int exceptMinValue = 0;
        private List<Integer> steps;

        public Builder(String name) {
            this.name = name;
        }

        /**
         * append new tag
         */
        public Histogram.Builder tag(String name, String value) {
            this.tags.add(new Tag(name, value));
            return this;
        }

        /**
         * Setting bucket steps
         */
        public Builder steps(List<Integer> steps) {
            this.steps = steps;
            return this;
        }

        /**
         * Setting except min value, default is zero
         */
        public Builder exceptMinValue(int minValue) {
            this.exceptMinValue = minValue;
            return this;
        }

        /**
         * Build a new histogram object with verify
         */
        public Histogram build() {
            if (steps == null || steps.isEmpty()) {
                throw new IllegalArgumentException("Missing steps setting");
            }

            // sort and distinct the steps
            steps = steps.stream().distinct().sorted().collect(Collectors.toList());

            // verify steps with except min value
            if (steps.get(0) < exceptMinValue) {
                throw new IllegalArgumentException("First step must bigger than min value");
            } else if (steps.get(0) != exceptMinValue) {
                // add the min value to the steps
                steps.add(0, exceptMinValue);
            }

            return new Histogram(name, tags, steps);
        }

    }

}
