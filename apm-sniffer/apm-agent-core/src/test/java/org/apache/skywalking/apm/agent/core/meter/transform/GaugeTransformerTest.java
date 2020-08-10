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
import org.apache.skywalking.apm.agent.core.meter.adapter.GaugeAdapter;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class GaugeTransformerTest {

    @Test
    public void testTransform() {
        final MeterId meterId = new MeterId("test", MeterType.COUNTER, Arrays.asList(new MeterTag("k1", "v1")));
        final List<Label> labels = Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build());

        // Normal
        GaugeTransformer transformer1 = new GaugeTransformer(new TestGaugeAdapter(meterId, () -> 2d));
        validateMeterData("test", labels, 2d, transformer1.transform());

        // Exception
        GaugeTransformer transformer2 = new GaugeTransformer(new TestGaugeAdapter(meterId, () -> Double.valueOf(2 / 0)));
        Assert.assertNull(transformer2.transform());

        // Null
        GaugeTransformer transformer3 = new GaugeTransformer(new TestGaugeAdapter(meterId, () -> null));
        Assert.assertNull(transformer3.transform());
    }

    /**
     * Check the single value message
     */
    private void validateMeterData(String name, List<Label> labels, double value, MeterData.Builder validate) {
        Assert.assertNotNull(validate);
        Assert.assertEquals(validate.getMetricCase().getNumber(), MeterData.SINGLEVALUE_FIELD_NUMBER);
        MeterSingleValue singleValue = validate.getSingleValue();
        Assert.assertNotNull(singleValue);
        Assert.assertEquals(singleValue.getValue(), value, 0.0);
        Assert.assertEquals(singleValue.getName(), name);
        Assert.assertEquals(singleValue.getLabelsList(), labels);
    }

    /**
     * Custom {@link GaugeAdapter} using {@link Supplier} as data getter
     */
    private static class TestGaugeAdapter implements GaugeAdapter {
        private final MeterId meterId;
        private Supplier<Double> supplier;

        public TestGaugeAdapter(MeterId meterId, Supplier<Double> supplier) {
            this.meterId = meterId;
            this.supplier = supplier;
        }

        @Override
        public Double getCount() {
            return supplier.get();
        }

        @Override
        public MeterId getId() {
            return meterId;
        }
    }
}
