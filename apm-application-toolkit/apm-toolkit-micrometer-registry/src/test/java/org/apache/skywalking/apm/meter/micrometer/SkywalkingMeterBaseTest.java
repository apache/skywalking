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

package org.apache.skywalking.apm.meter.micrometer;

import org.apache.skywalking.apm.toolkit.meter.Counter;
import org.apache.skywalking.apm.toolkit.meter.Gauge;
import org.apache.skywalking.apm.toolkit.meter.Histogram;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.apache.skywalking.apm.toolkit.meter.Percentile;
import org.junit.Assert;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SkywalkingMeterBaseTest {

    public void assertCounter(Counter counter, String name, List<MeterId.Tag> tags, double count) {
        Assert.assertEquals(name, counter.getMeterId().getName());
        Assert.assertEquals(tags, counter.getMeterId().getTags());
        Assert.assertEquals(MeterId.MeterType.COUNTER, counter.getMeterId().getType());

        Assert.assertEquals(count, counter.get(), 0.0);
    }

    public void assertGauge(Gauge gauge, String name, List<MeterId.Tag> tags, double value) {
        assertGauge(gauge, name, tags, value, false);
    }

    public void assertGauge(Gauge gauge, String name, List<MeterId.Tag> tags, double value, boolean greaterThanValueMode) {
        Assert.assertEquals(name, gauge.getMeterId().getName());
        Assert.assertEquals(tags, gauge.getMeterId().getTags());
        Assert.assertEquals(MeterId.MeterType.GAUGE, gauge.getMeterId().getType());

        if (greaterThanValueMode) {
            Assert.assertTrue(gauge.get() > value);
        } else {
            Assert.assertEquals(value, gauge.get(), 0.0);
        }
    }

    public void assertHistogramNull(Optional<Histogram> histogramOptional) {
        Assert.assertNull(histogramOptional.orElse(null));
    }

    public void assertHistogram(Optional<Histogram> histogramOptional, String name, List<MeterId.Tag> tags, double... bucketsAndCount) {
        final Histogram histogram = histogramOptional.orElse(null);
        Assert.assertNotNull(histogram);
        Assert.assertEquals(name, histogram.getMeterId().getName());
        Assert.assertEquals(tags, histogram.getMeterId().getTags());
        Assert.assertEquals(MeterId.MeterType.HISTOGRAM, histogram.getMeterId().getType());

        final Histogram.Bucket[] buckets = histogram.getBuckets();
        Assert.assertEquals(bucketsAndCount.length / 2, buckets.length);
        for (int i = 0; i < buckets.length; i++) {
            Assert.assertEquals(bucketsAndCount[i * 2], buckets[i].getBucket(), 0.0);
            Assert.assertEquals((long) bucketsAndCount[i * 2 + 1], buckets[i].getCount());
        }
    }

    public void assertPercentileNull(Optional<Percentile> percentileOptional) {
        Assert.assertNull(percentileOptional.orElse(null));
    }

    public void assertPercentile(Optional<Percentile> percentileOptional, String name, List<MeterId.Tag> tags, double... records) {
        final Percentile percentile = percentileOptional.orElse(null);
        Assert.assertNotNull(percentile);
        Assert.assertEquals(name, percentile.getMeterId().getName());
        Assert.assertEquals(tags, percentile.getMeterId().getTags());
        Assert.assertEquals(MeterId.MeterType.PERCENTILE, percentile.getMeterId().getType());

        final ConcurrentHashMap<Double, AtomicLong> percentileRecords = percentile.getRecordWithCount();
        Assert.assertEquals(records.length / 2, percentileRecords.size());
        for (int i = 0; i < records.length; i += 2) {
            Assert.assertTrue(percentileRecords.containsKey(records[i]));
            Assert.assertEquals((long) records[i + 1], percentileRecords.get(records[i]).get());
        }
    }
}
