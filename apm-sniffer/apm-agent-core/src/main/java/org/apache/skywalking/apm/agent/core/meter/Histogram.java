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

import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Histogram extends Meter<Histogram> {

    private final Bucket[] buckets;

    public Histogram(MeterId id, List<Integer> buckets) {
        super(id);
        this.buckets = initBuckets(buckets);
    }

    /**
     * Add count into the step
     */
    public void addCountToStep(int step, long count) {
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
    public void addValue(int value) {
        Bucket bucket = findBucket(value);
        if (bucket == null) {
            return;
        }

        bucket.increment(1L);
    }

    /**
     * Using binary search the bucket
     */
    private Bucket findBucket(int value) {
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

    @Override
    public boolean accept(Histogram newMeter) {
        // buckets must be the same
        return Arrays.equals(newMeter.buckets, buckets);
    }

    @Override
    public MeterData.Builder transform() {
        final MeterData.Builder builder = MeterData.newBuilder();

        return builder.setHistogram(MeterHistogram.newBuilder()
            .setName(id.getName())
            .addAllLabels(id.transformTags())
            .addAllValues(Stream.of(buckets).map(Bucket::transform).collect(Collectors.toList()))
            .build());
    }

    private Bucket[] initBuckets(List<Integer> buckets) {
        final Bucket[] list = new Bucket[buckets.size()];
        for (int i = 0; i < buckets.size(); i++) {
            list[i] = new Bucket(buckets.get(i));
        }
        return list;
    }

    public static class Bucket {
        private int bucket;
        private AtomicLong count = new AtomicLong();
        private AtomicReference<Long> previous = new AtomicReference<>();

        public Bucket(int bucket) {
            this.bucket = bucket;
        }

        public void increment(long count) {
            this.count.addAndGet(count);
        }

        public MeterBucketValue transform() {
            final long currentCount = count.get();
            final Long previous = this.previous.getAndSet(currentCount);

            return MeterBucketValue.newBuilder()
                .setBucket(bucket)
                .setCount(previous == null ? currentCount : currentCount - previous)
                .build();
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
