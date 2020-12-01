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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class HistogramTest {
    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @After
    public void after() {
        final MeterService meterService = ServiceManager.INSTANCE.findService(MeterService.class);
        ((ConcurrentHashMap<MeterId, BaseMeter>) Whitebox.getInternalState(meterService, "meterMap")).clear();
    }

    @Test
    public void testBuckets() {
        final MeterId meterId = new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));

        // Check buckets
        final Histogram histogram = MeterFactory.histogram("test").steps(Arrays.asList(2d, 5d)).minValue(2d).build();
        final Histogram.Bucket[] buckets = (Histogram.Bucket[]) Whitebox.getInternalState(histogram, "buckets");
        Assert.assertEquals(2, buckets.length);
        Assert.assertEquals(buckets[0], new Histogram.Bucket(2));
        Assert.assertEquals(buckets[1], new Histogram.Bucket(5));
    }

    @Test
    public void testTransform() {
        final List<Label> labels = Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build());

        // Check histogram message
        final Histogram histogram = MeterFactory.histogram("test")
                                                .steps(Arrays.asList(2d, 5d))
                                                .minValue(1d)
                                                .tag("k1", "v1")
                                                .build();
        histogram.addValue(1);
        histogram.addValue(3);
        histogram.addValue(3);
        histogram.addValue(7);
        verifyHistogram("test", labels, Arrays.asList(1d, 2d, 5d), Arrays.asList(1L, 2L, 1L), histogram.transform());

        histogram.addValue(9);
        verifyHistogram("test", labels, Arrays.asList(1d, 2d, 5d), Arrays.asList(1L, 2L, 2L), histogram.transform());
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

}
