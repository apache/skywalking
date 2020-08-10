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

import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.agent.core.meter.adapter.HistogramAdapter;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;
import java.util.List;

public class HistogramTransformerTest {

    @Test
    public void testBuckets() {
        final MeterId meterId = new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));

        // Check buckets
        final TestHistogramAdapter adapter = new TestHistogramAdapter(meterId, new double[] {2d, 5d});
        final HistogramTransformer transformer = new HistogramTransformer(adapter);
        final HistogramTransformer.Bucket[] buckets = (HistogramTransformer.Bucket[]) Whitebox.getInternalState(transformer, "buckets");
        Assert.assertEquals(2, buckets.length);
        Assert.assertEquals(buckets[0], new HistogramTransformer.Bucket(2));
        Assert.assertEquals(buckets[1], new HistogramTransformer.Bucket(5));
    }

    @Test
    public void testTransform() {
        final MeterId meterId = new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));
        final List<Label> labels = Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build());

        // Check histogram message
        final TestHistogramAdapter adapter = new TestHistogramAdapter(meterId, new double[] {2d, 5d});
        final HistogramTransformer transformer = new HistogramTransformer(adapter);
        adapter.setValues(new long[] {5L, 10L});
        verifyHistogram("test", labels, Arrays.asList(2d, 5d), Arrays.asList(5L, 10L), transformer.transform());

        adapter.setValues(new long[] {6L, 12L});
        verifyHistogram("test", labels, Arrays.asList(2d, 5d), Arrays.asList(6L, 12L), transformer.transform());
    }

    /**
     * Check histogram message
     */
    public static void verifyHistogram(String name, List<Label> labels, List<Double> buckets,
                                       List<Long> bucketValues, MeterData.Builder validate) {
        Assert.assertNotNull(validate);
        Assert.assertEquals(validate.getMetricCase().getNumber(), MeterData.HISTOGRAM_FIELD_NUMBER);
        MeterHistogram histogram = validate.getHistogram();
        Assert.assertNotNull(histogram);
        Assert.assertEquals(histogram.getName(), name);
        Assert.assertEquals(histogram.getLabelsList(), labels);
        Assert.assertNotNull(histogram.getValuesList());
        Assert.assertEquals(histogram.getValuesCount(), bucketValues.size());

        for (int i = 0; i < bucketValues.size(); i++) {
            Assert.assertNotNull(histogram.getValues(i));
            Assert.assertEquals(histogram.getValues(i).getBucket(), buckets.get(i).doubleValue(), 0.0);
            Assert.assertEquals(histogram.getValues(i).getCount(), bucketValues.get(i).longValue());
        }
    }

    /**
     * Custom {@link HistogramAdapter} with appoint buckets and values
     */
    private static class TestHistogramAdapter implements HistogramAdapter {
        private final MeterId meterId;
        private final double[] buckets;
        private long[] values;

        public TestHistogramAdapter(MeterId meterId, double[] buckets) {
            this.meterId = meterId;
            this.buckets = buckets;
        }

        @Override
        public double[] getAllBuckets() {
            return buckets;
        }

        @Override
        public long[] getBucketValues() {
            return values;
        }

        @Override
        public MeterId getId() {
            return meterId;
        }

        public void setValues(long[] values) {
            this.values = values;
        }
    }

}
