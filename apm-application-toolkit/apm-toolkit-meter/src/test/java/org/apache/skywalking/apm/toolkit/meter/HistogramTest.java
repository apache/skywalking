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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;

public class HistogramTest {

    @Test
    public void testBuild() {
        // normal
        Histogram histogram = Histogram.create("test_histogram1").steps(Arrays.asList(1d, 5d, 10d)).exceptMinValue(-10)
            .tag("k1", "v1").build();
        Assert.assertArrayEquals(
            (Histogram.Bucket[]) Whitebox.getInternalState(histogram, "buckets"),
            buildBuckets(-10d, 1d, 5d, 10d));

        // except value bigger than first bucket
        try {
            histogram = Histogram.create("test_histogram2").steps(Arrays.asList(1d, 5d, 10d)).exceptMinValue(2).build();
            throw new IllegalStateException("valid failed");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }

        // except min value is equals first step
        histogram = Histogram.create("test_histogram3").steps(Arrays.asList(1d, 5d, 10d)).exceptMinValue(1d)
            .tag("k1", "v1").build();
        Assert.assertArrayEquals(
            (Histogram.Bucket[]) Whitebox.getInternalState(histogram, "buckets"),
            buildBuckets(1d, 5d, 10d));

        // empty step
        try {
            Histogram.create("test").build();
            throw new IllegalStateException();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testAddCountToStep() {
        Histogram histogram = Histogram.create("test_histogram4").steps(Arrays.asList(1d, 5d, 10d)).exceptMinValue(-10)
            .tag("k1", "v1").build();

        // single add
        histogram.addCountToStep(1, 2);
        verifyHistogram(histogram, new Long[] {0L, 2L, 0L, 0L});

        // multiple add
        histogram.addCountToStep(1, 2);
        histogram.addCountToStep(1, 2);
        histogram.addCountToStep(5, 2);
        verifyHistogram(histogram, new Long[] {0L, 6L, 2L, 0L});

        // not exists
        histogram.addCountToStep(3, 2);
        verifyHistogram(histogram, new Long[] {0L, 6L, 2L, 0L});
    }

    @Test
    public void testAddValue() {
        Histogram histogram = Histogram.create("test_histogram5").steps(Arrays.asList(1d, 5d, 10d)).exceptMinValue(-10)
            .tag("k1", "v1").build();

        // single value
        histogram.addValue(2);
        verifyHistogram(histogram, new Long[] {0L, 1L, 0L, 0L});

        // multiple values
        histogram.addValue(2);
        histogram.addValue(2);
        histogram.addValue(9);
        verifyHistogram(histogram, new Long[] {0L, 3L, 1L, 0L});

        // un-support value
        histogram.addValue(-11);
        verifyHistogram(histogram, new Long[] {0L, 3L, 1L, 0L});

        // max value
        histogram.addValue(Integer.MAX_VALUE);
        histogram.addValue(9);
        histogram.addValue(10);
        verifyHistogram(histogram, new Long[] {0L, 3L, 2L, 2L});
    }

    private Histogram.Bucket[] buildBuckets(double... steps) {
        final Histogram.Bucket[] buckets = new Histogram.Bucket[steps.length];
        for (int i = 0; i < steps.length; i++) {
            buckets[i] = new Histogram.Bucket(steps[i]);
        }
        return buckets;
    }

    /**
     * Verify histogram bucket counts
     */
    public static void verifyHistogram(Histogram histogram, Long[] bucketValues) {
        Assert.assertNotNull(histogram);
        final Histogram.Bucket[] buckets = (Histogram.Bucket[]) Whitebox.getInternalState(histogram, "buckets");
        for (int i = 0; i < bucketValues.length; i++) {
            Assert.assertNotNull(buckets[i]);
            Assert.assertEquals(buckets[i].count.longValue(), bucketValues[i].longValue());
        }
    }
}
