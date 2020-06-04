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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Histogram extends BaseMeter {

    protected final Bucket[] buckets;
    protected final List<Double> steps;

    public static Histogram.Builder create(String name) {
        return new Builder(name);
    }

    protected Histogram(MeterId meterId, List<Double> steps) {
        super(meterId);
        this.steps = steps;
        this.buckets = initBuckets(steps);
    }

    /**
     * Add count into the step
     */
    public void addCountToStep(double step, long count) {
        // lookup the bucket, only matches with the step
        Bucket bucket = findBucket(step);
        if (bucket == null || bucket.bucket != step) {
            return;
        }

        bucket.increment(count);
    }

    /**
     * Add value into the histogram, automatic analyze what bucket count need to be increment
     * [step1, step2)
     */
    public void addValue(double value) {
        Bucket bucket = findBucket(value);
        if (bucket == null) {
            return;
        }

        bucket.increment(1L);
    }

    public Bucket[] getBuckets() {
        return buckets;
    }

    /**
     * Using binary search the bucket
     */
    private Bucket findBucket(double value) {
        int low = 0;
        int high = buckets.length - 1;

        while (low <= high) {
            int mid = (low + high) / 2;
            if (buckets[mid].bucket < value)
                low = mid + 1;
            else if (buckets[mid].bucket > value)
                high = mid - 1;
            else
                return buckets[mid];
        }

        // because using min value as bucket, need using previous bucket
        low -= 1;

        return low < buckets.length && low >= 0 ? buckets[low] : null;
    }

    private Bucket[] initBuckets(List<Double> buckets) {
        final Bucket[] list = new Bucket[buckets.size()];
        for (int i = 0; i < buckets.size(); i++) {
            list[i] = new Bucket(buckets.get(i));
        }
        return list;
    }

    public static class Builder extends BaseMeter.Builder<Histogram> {
        private double exceptMinValue = 0;
        private List<Double> steps;

        public Builder(String name) {
            super(name);
        }

        public Builder(MeterId meterId) {
            super(meterId);
        }

        /**
         * Setting bucket steps
         */
        public Builder steps(List<Double> steps) {
            this.steps = new ArrayList<>(steps);
            return this;
        }

        /**
         * Setting except min value, default is zero
         */
        public Builder exceptMinValue(double minValue) {
            this.exceptMinValue = minValue;
            return this;
        }

        @Override
        public void accept(Histogram meter) {
            if (this.steps.get(0) != exceptMinValue) {
                this.steps.add(0, exceptMinValue);
            }
            if (meter.buckets.length != this.steps.size()) {
                throw new IllegalArgumentException("Steps are not has the same size");
            }
            List<Double> meterSteps = new ArrayList<>(meter.buckets.length);
            for (Bucket bucket : meter.buckets) {
                meterSteps.add(bucket.bucket);
            }
            if (!Objects.equals(meterSteps, this.steps)) {
                throw new IllegalArgumentException("Steps are not the same");
            }
        }

        @Override
        public Histogram create(MeterId meterId) {
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

            return new Histogram(meterId, steps);
        }

        @Override
        public MeterId.MeterType getType() {
            return MeterId.MeterType.HISTOGRAM;
        }
    }

    public static class Bucket {
        protected double bucket;
        protected AtomicLong count = new AtomicLong();

        public Bucket(double bucket) {
            this.bucket = bucket;
        }

        public void increment(long count) {
            this.count.addAndGet(count);
        }

        public double getBucket() {
            return bucket;
        }

        public long getCount() {
            return count.get();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Bucket bucket1 = (Bucket) o;
            return bucket == bucket1.bucket;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bucket);
        }
    }
}
