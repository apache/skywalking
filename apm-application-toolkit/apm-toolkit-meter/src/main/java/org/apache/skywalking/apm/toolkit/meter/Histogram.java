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

import java.util.List;

/**
 * Similar to a histogram, a summary sample observations (usual things like request durations and response sizes).
 * While it also provides a total count of observations and a sum of all observed values, it calculates configurable quartiles over a sliding time window.
 * The histogram provides detailed data in each data group.
 */
public interface Histogram extends BaseMeter {

    /**
     * Add value into the histogram, automatic analyze what bucket count need to be increment
     * [step1, step2)
     */
    void addValue(double value);

    /**
     * Get all buckets
     */
    Bucket[] getBuckets();

    interface Builder extends BaseBuilder<Builder, Histogram> {

        /**
         * Setting bucket steps
         */
        Builder steps(List<Double> steps);

        /**
         * Setting min value, default is zero
         */
        Builder minValue(double minValue);
    }

    /**
     * Histogram bucket
     */
    interface Bucket {

        /**
         * Get bucket key
         */
        double getBucket();

        /**
         * Get bucket count
         */
        long getCount();
    }
}
