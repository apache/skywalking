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

import com.google.common.collect.Lists;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HistogramTest {

    private String name;
    private MeterId meterId;
    private List<Label> labels;
    private List<Integer> buckets;
    private List<Integer> buckets1;

    @Before
    public void setup() {
        name = "test_meter";
        final List<MeterTag> tags = Lists.newArrayList(new MeterTag("key", "value"));
        meterId = new MeterId(name, MeterType.HISTOGRAM, tags);

        labels = meterId.transformTags();

        buckets = Arrays.asList(0, 2, 4, 5, 8, 9);
        buckets1 = Arrays.asList(0, 2, 4, 5, 8, 9, 10);
    }

    @Test
    public void testAddCountToStep() {
        final Histogram histogram = new Histogram(meterId, buckets);

        // single add
        histogram.addCountToStep(0, 2);
        verifyHistogram(name, labels, buckets, new Long[] {2L, 0L, 0L, 0L, 0L, 0L}, histogram.transform());

        // multiple add
        histogram.addCountToStep(0, 2);
        histogram.addCountToStep(0, 2);
        histogram.addCountToStep(2, 2);
        verifyHistogram(name, labels, buckets, new Long[] {4L, 2L, 0L, 0L, 0L, 0L}, histogram.transform());

        // empty
        verifyHistogram(name, labels, buckets, new Long[] {0L, 0L, 0L, 0L, 0L, 0L}, histogram.transform());

        // not exists
        histogram.addCountToStep(1, 2);
        histogram.addCountToStep(-1, 2);
        verifyHistogram(name, labels, buckets, new Long[] {0L, 0L, 0L, 0L, 0L, 0L}, histogram.transform());
    }

    @Test
    public void testAddValue() {
        final Histogram histogram = new Histogram(meterId, buckets);

        // single value
        histogram.addValue(2);
        verifyHistogram(name, labels, buckets, new Long[] {0L, 1L, 0L, 0L, 0L, 0L}, histogram.transform());

        // multiple values
        histogram.addValue(2);
        histogram.addValue(2);
        histogram.addValue(9);
        verifyHistogram(name, labels, buckets, new Long[] {0L, 2L, 0L, 0L, 0L, 1L}, histogram.transform());

        // un-support value
        histogram.addValue(-1);
        verifyHistogram(name, labels, buckets, new Long[] {0L, 0L, 0L, 0L, 0L, 0L}, histogram.transform());

        // max value
        histogram.addValue(Integer.MAX_VALUE);
        histogram.addValue(9);
        histogram.addValue(10);
        verifyHistogram(name, labels, buckets, new Long[] {0L, 0L, 0L, 0L, 0L, 3L}, histogram.transform());
    }

    @Test
    public void testAccept() {
        Assert.assertTrue(new Histogram(meterId, buckets).accept(new Histogram(meterId, buckets)));
        Assert.assertFalse(new Histogram(meterId, buckets).accept(new Histogram(meterId, buckets1)));
    }

    public static void verifyHistogram(String name, List<Label> labels, List<Integer> buckets,
                                       Long[] bucketValues, MeterData.Builder validate) {

        Assert.assertNotNull(validate);
        Assert.assertEquals(validate.getMetricCase().getNumber(), MeterData.HISTOGRAM_FIELD_NUMBER);
        MeterHistogram histogram = validate.getHistogram();
        Assert.assertNotNull(histogram);
        Assert.assertEquals(histogram.getName(), name);
        Assert.assertEquals(histogram.getLabelsList(), labels);
        Assert.assertNotNull(histogram.getValuesList());
        Assert.assertEquals(histogram.getValuesCount(), bucketValues.length);

        for (int i = 0; i < bucketValues.length; i++) {
            Assert.assertNotNull(histogram.getValues(i));
            Assert.assertEquals(histogram.getValues(i).getBucket(), buckets.get(i).intValue());
            Assert.assertEquals(histogram.getValues(i).getCount(), bucketValues[i].longValue());
        }
    }

}
