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

package org.apache.skywalking.apm.toolkit.activation.meter.adapter;

import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.adapter.HistogramAdapter;
import org.apache.skywalking.apm.toolkit.activation.meter.util.MeterIdConverter;
import org.apache.skywalking.apm.toolkit.meter.Histogram;

public class ToolkitHistogramAdapter implements HistogramAdapter {

    private final Histogram histogram;
    private final MeterId id;

    public ToolkitHistogramAdapter(Histogram histogram) {
        this.histogram = histogram;
        this.id = MeterIdConverter.convert(histogram.getMeterId());
    }

    @Override
    public double[] getAllBuckets() {
        final int bucketLength = histogram.getBuckets().length;
        final double[] buckets = new double[bucketLength];
        for (int i = 0; i < bucketLength; i++) {
            buckets[i] = histogram.getBuckets()[i].getBucket();
        }
        return buckets;
    }

    @Override
    public long[] getBucketValues() {
        final int bucketLength = histogram.getBuckets().length;
        final long[] bucketValues = new long[bucketLength];
        for (int i = 0; i < bucketLength; i++) {
            bucketValues[i] = histogram.getBuckets()[i].getCount();
        }
        return bucketValues;
    }

    @Override
    public MeterId getId() {
        return id;
    }

}
