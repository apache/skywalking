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
import org.apache.skywalking.apm.agent.core.meter.adapter.HistogramAdapter;
import org.apache.skywalking.apm.agent.core.meter.builder.Histogram;
import org.apache.skywalking.apm.agent.core.meter.transform.HistogramTransformer;
import org.apache.skywalking.apm.agent.core.meter.transform.MeterTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Agent core level histogram adapter
 */
public class InternalHistogramAdapter extends InternalBaseAdapter implements HistogramAdapter, Histogram {
    protected final Bucket[] buckets;
    protected final double[] steps;

    protected InternalHistogramAdapter(MeterId meterId, double[] steps) {
        super(meterId);
        this.steps = steps;
        this.buckets = initBuckets(steps);
    }

    @Override
    public double[] getAllBuckets() {
        final int bucketLength = buckets.length;
        final double[] buckets = new double[bucketLength];
        for (int i = 0; i < bucketLength; i++) {
            buckets[i] = this.buckets[i].getBucket();
        }
        return buckets;
    }

    @Override
    public long[] getBucketValues() {
        final int bucketLength = buckets.length;
        final long[] bucketValues = new long[bucketLength];
        for (int i = 0; i < bucketLength; i++) {
            bucketValues[i] = this.buckets[i].getCount();
        }
        return bucketValues;
    }

    @Override
    public void addValue(double value) {
        Bucket bucket = findBucket(value);
        if (bucket == null) {
            return;
        }

        bucket.increment(1L);
    }

    @Override
    public Bucket[] getBuckets() {
        return this.buckets;
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

    private Bucket[] initBuckets(double[] buckets) {
        final Bucket[] list = new Bucket[buckets.length];
        for (int i = 0; i < buckets.length; i++) {
            list[i] = new Bucket(buckets[i]);
        }
        return list;
    }

    public static class Builder extends AbstractBuilder<Histogram.Builder, Histogram, HistogramAdapter> implements Histogram.Builder {
        private double minValue = 0;
        private List<Double> steps;

        public Builder(String name) {
            super(name);
        }

        @Override
        public Histogram.Builder steps(List<Double> steps) {
            this.steps = new ArrayList<>(steps);
            return this;
        }

        @Override
        public Histogram.Builder minValue(double minValue) {
            this.minValue = minValue;
            return this;
        }

        @Override
        protected MeterType getType() {
            return MeterType.HISTOGRAM;
        }

        @Override
        protected HistogramAdapter create(MeterId meterId) {
            if (steps == null || steps.isEmpty()) {
                throw new IllegalArgumentException("Missing steps setting");
            }

            // sort and distinct the steps
            steps = steps.stream().distinct().sorted().collect(Collectors.toList());

            // verify steps with except min value
            if (steps.get(0) < minValue) {
                throw new IllegalArgumentException("First step must bigger than min value");
            } else if (steps.get(0) != minValue) {
                // add the min value to the steps
                steps.add(0, minValue);
            }

            return new InternalHistogramAdapter(meterId, steps.stream().mapToDouble(t -> t).toArray());
        }

        @Override
        protected MeterTransformer<HistogramAdapter> wrapperTransformer(HistogramAdapter adapter) {
            return new HistogramTransformer(adapter);
        }
    }

    /**
     * Histogram bucket
     */
    private static class Bucket implements Histogram.Bucket {
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
