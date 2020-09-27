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

package org.apache.skywalking.apm.agent.core.meter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;

/**
 * Histogram represents the distribution of data. It includes the buckets representing continuous ranges of values, with
 * the num of collected values in every specific range. The ranges could start from any value(default 0) to positive
 * infinitive. They can be set through the constructor and immutable after that.
 */
public class Histogram extends BaseMeter {
    protected final Bucket[] buckets;

    /**
     * @param meterId as the unique id of this meter instance
     * @param steps presents the minimal value of every step
     */
    public Histogram(MeterId meterId, List<Double> steps) {
        super(meterId);
        this.buckets = initBuckets(steps);
    }

    /**
     * Add value into the histogram, automatic analyze what bucket count need to be increment [step1, step2)
     */
    public void addValue(double value) {
        Bucket bucket = findBucket(value);
        if (bucket == null) {
            return;
        }

        bucket.increment(1L);
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

    private Bucket[] initBuckets(List<Double> steps) {
        return steps.stream().map(Bucket::new).toArray(Bucket[]::new);
    }

    @Override
    public MeterData.Builder transform() {
        final MeterData.Builder builder = MeterData.newBuilder();

        // get all values
        List<MeterBucketValue> values = Arrays.stream(buckets)
                                              .map(Histogram.Bucket::transform).collect(Collectors.toList());

        return builder.setHistogram(MeterHistogram.newBuilder()
                                                  .setName(getName())
                                                  .addAllLabels(transformTags())
                                                  .addAllValues(values)
                                                  .build());
    }

    public static class Builder extends AbstractBuilder<Builder, Histogram> {
        private double minValue = 0;
        private List<Double> steps;

        /**
         * Build a new meter build, meter name is required
         */
        public Builder(String name) {
            super(name);
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
        protected MeterType getType() {
            return MeterType.HISTOGRAM;
        }

        @Override
        protected Histogram create(MeterId meterId) {
            if (steps == null || steps.isEmpty()) {
                throw new IllegalArgumentException("Missing steps setting");
            }

            // sort and distinct the steps
            steps = steps.stream().distinct().sorted().collect(Collectors.toList());

            // verify steps with except min value
            if (steps.get(0) < minValue) {
                throw new IllegalArgumentException("Step[0] must be bigger than min value");
            } else if (steps.get(0) != minValue) {
                // add the min value to the steps
                steps.add(0, minValue);
            }

            return new Histogram(meterId, steps);
        }
    }

    /**
     * Histogram bucket
     */
    protected static class Bucket {
        protected double bucket;
        protected AtomicLong count = new AtomicLong();

        public Bucket(double bucket) {
            this.bucket = bucket;
        }

        public void increment(long count) {
            this.count.addAndGet(count);
        }

        public MeterBucketValue transform() {
            return MeterBucketValue.newBuilder()
                                   .setBucket(bucket)
                                   .setCount(count.get())
                                   .build();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Bucket bucket1 = (Bucket) o;
            return bucket == bucket1.bucket;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bucket);
        }
    }
}
