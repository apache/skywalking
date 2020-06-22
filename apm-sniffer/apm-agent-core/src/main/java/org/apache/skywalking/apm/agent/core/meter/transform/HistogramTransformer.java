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

package org.apache.skywalking.apm.agent.core.meter.transform;

import org.apache.skywalking.apm.agent.core.meter.adapter.HistogramAdapter;
import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HistogramTransformer extends MeterTransformer<HistogramAdapter> {

    private final Bucket[] buckets;

    public HistogramTransformer(HistogramAdapter adapter) {
        super(adapter);
        this.buckets = initBuckets(adapter.getAllBuckets());
    }

    @Override
    public MeterData.Builder transform() {
        final MeterData.Builder builder = MeterData.newBuilder();

        // get all values
        List<MeterBucketValue> values = new ArrayList<>(this.buckets.length);
        final long[] bucketValues = adapter.getBucketValues();
        for (int i = 0; i < bucketValues.length; i++) {
            values.add(buckets[i].transform(bucketValues[i]));
        }

        return builder.setHistogram(MeterHistogram.newBuilder()
            .setName(getName())
            .addAllLabels(transformTags())
            .addAllValues(values)
            .build());
    }

    private Bucket[] initBuckets(double[] buckets) {
        final Bucket[] list = new Bucket[buckets.length];
        for (int i = 0; i < buckets.length; i++) {
            list[i] = new Bucket(buckets[i]);
        }
        return list;
    }

    public static class Bucket {
        private double bucket;

        public Bucket(double bucket) {
            this.bucket = bucket;
        }

        public MeterBucketValue transform(long currentCount) {
            return MeterBucketValue.newBuilder()
                .setBucket(bucket)
                .setCount(currentCount)
                .build();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Bucket bucket1 = (Bucket) o;
            return Double.compare(bucket1.bucket, bucket) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bucket);
        }
    }
}
