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
import org.junit.Assert;

import java.util.List;
import java.util.Optional;

public class SkywalkingMeterBaseTest {

    /**
     * Check counter data
     */
    public void assertCounter(Counter counter, String name, List<MeterId.Tag> tags, double count) {
        Assert.assertEquals(name, counter.getMeterId().getName());
        Assert.assertEquals(tags, counter.getMeterId().getTags());
        Assert.assertEquals(MeterId.MeterType.COUNTER, counter.getMeterId().getType());

        Assert.assertEquals(count, counter.get(), 0.0);
    }

    /**
     * Check gauge data, and value must be same
     */
    public void assertGauge(Gauge gauge, String name, List<MeterId.Tag> tags, double value) {
        assertGauge(gauge, name, tags, value, false);
    }

    /**
     * Check gauge data, and value could greater than provide value
     */
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

    /**
     * Check not have histogram
     */
    public void assertHistogramNull(Optional<Histogram> histogramOptional) {
        Assert.assertNull(histogramOptional.orElse(null));
    }

    /**
     * Check histogram cannot be null and data correct
     * @param bucketsAndCount bucket and value array
     */
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

}
