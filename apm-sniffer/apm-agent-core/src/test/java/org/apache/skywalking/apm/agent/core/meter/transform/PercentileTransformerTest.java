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

import com.google.common.collect.Maps;
import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.agent.core.meter.adapter.PercentileAdapter;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PercentileTransformerTest {

    @Test
    public void testTransform() {
        final MeterId meterId = new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));
        final List<Label> labels = Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build());

        final HashMap<Double, Long> records = Maps.newHashMap();
        records.put(2d, 1L);
        records.put(5d, 3L);
        records.put(7d, 1L);
        final TestPercentileAdapter adapter = new TestPercentileAdapter(meterId, records);
        final PercentileTransformer transformer = new PercentileTransformer(adapter);
        verifyHistogram("test", labels, Arrays.asList(2d, 5d, 7d), Arrays.asList(1L, 3L, 1L), transformer.transform());
    }

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

    private static class TestPercentileAdapter implements PercentileAdapter {
        private final MeterId meterId;
        private Map<Double, Long> records;

        public TestPercentileAdapter(MeterId meterId, Map<Double, Long> records) {
            this.meterId = meterId;
            this.records = records;
        }

        @Override
        public MeterId getId() {
            return meterId;
        }

        @Override
        public Map<Double, Long> drainRecords() {
            return records;
        }
    }

}
