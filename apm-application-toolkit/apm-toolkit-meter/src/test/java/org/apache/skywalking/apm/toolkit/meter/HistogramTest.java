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

import org.apache.skywalking.apm.toolkit.meter.impl.HistogramImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class HistogramTest {

    @Test
    public void testBuild() {
        // normal
        Histogram histogram = MeterFactory.histogram("test_histogram1").steps(Arrays.asList(1d, 5d, 10d)).minValue(-10)
            .tag("k1", "v1").build();
        verifyHistogram(histogram, -10d, 0, 1d, 0, 5d, 0, 10d, 0);

        // except value bigger than first bucket
        try {
            histogram = HistogramImpl.create("test_histogram2").steps(Arrays.asList(1d, 5d, 10d)).minValue(2).build();
            throw new IllegalStateException("valid failed");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }

        // except min value is equals first step
        histogram = HistogramImpl.create("test_histogram3").steps(Arrays.asList(1d, 5d, 10d)).minValue(1d)
            .tag("k1", "v1").build();
        verifyHistogram(histogram, 1d, 0, 5d, 0, 10d, 0);

        // empty step
        try {
            HistogramImpl.create("test").build();
            throw new IllegalStateException();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }

        // Build by meterId
        histogram = MeterFactory.histogram(new MeterId("test_histogram4", MeterId.MeterType.HISTOGRAM, Collections.emptyList()))
            .steps(Arrays.asList(1d, 5d, 10d)).minValue(0d).build();
        Assert.assertNotNull(histogram);
    }

    @Test
    public void testAccept() {
        MeterFactory.histogram("test_histogram_accept").steps(Arrays.asList(1d, 3d, 5d)).minValue(0).build();

        // same histogram
        HistogramImpl.create("test_histogram_accept").steps(Arrays.asList(1d, 3d, 5d)).minValue(0).build();

        // not same steps size
        try {
            HistogramImpl.create("test_histogram_accept").steps(Arrays.asList(1d, 3d, 5d, 7d)).minValue(-1).build();
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            throw e;
        }

        // not same steps value
        try {
            HistogramImpl.create("test_histogram_accept").steps(Arrays.asList(1d, 3d, 6d)).minValue(-1).build();
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            throw e;
        }
    }

    @Test
    public void testAddValue() {
        Histogram histogram = MeterFactory.histogram("test_histogram5").steps(Arrays.asList(1d, 5d, 10d)).minValue(-10)
            .tag("k1", "v1").build();

        // single value
        histogram.addValue(2);
        verifyHistogram(histogram, -10d, 0L, 1d, 1L, 5d, 0L, 10d, 0L);

        // multiple values
        histogram.addValue(2);
        histogram.addValue(2);
        histogram.addValue(9);
        verifyHistogram(histogram, -10d, 0L, 1d, 3L, 5d, 1L, 10d, 0L);

        // un-support value
        histogram.addValue(-11);
        verifyHistogram(histogram, -10d, 0L, 1d, 3L, 5d, 1L, 10d, 0L);

        // max value
        histogram.addValue(Integer.MAX_VALUE);
        histogram.addValue(9);
        histogram.addValue(10);
        verifyHistogram(histogram, -10d, 0L, 1d, 3L, 5d, 2L, 10d, 2L);
    }

    /**
     * Verify histogram bucket counts
     */
    public static void verifyHistogram(Histogram histogram, double... buckets) {
        Assert.assertNotNull(histogram);
        final Histogram.Bucket[] histogramBuckets = histogram.getBuckets();
        Assert.assertEquals(histogramBuckets.length, buckets.length / 2);
        for (int i = 0; i < histogramBuckets.length; i++) {
            Assert.assertNotNull(buckets[i]);
            Assert.assertEquals(buckets[i * 2], histogramBuckets[i].getBucket(), 0.0);
            Assert.assertEquals(buckets[i * 2 + 1], histogramBuckets[i].getCount(), 0.0);
        }
    }
}
