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
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.function.Supplier;

public class GaugeTest {

    private String name;
    private MeterId meterId;
    private List<Label> labels;

    @Before
    public void setup() {
        name = "test_meter";
        final List<MeterTag> tags = Lists.newArrayList(new MeterTag("key", "value"));
        meterId = new MeterId(name, MeterType.GAUGE, tags);

        labels = meterId.transformTags();
    }

    @Test
    public void testNullGetter() {
        final Gauge gauge = new Gauge(meterId, null);
        Assert.assertNull(gauge.transform());
    }

    @Test
    public void testNormal() {
        final Gauge gauge = new Gauge(meterId, () -> 1L);

        // test with multiple times
        validateMeterData(name, labels, 1L, gauge.transform());
        validateMeterData(name, labels, 1L, gauge.transform());
    }

    @Test
    public void testException() {
        final Gauge gauge = new Gauge(meterId, () -> Long.valueOf(1 / 0));
        Assert.assertNull(gauge.transform());
    }

    @Test
    public void testGet() {
        final Gauge gauge = new Gauge(meterId, () -> 1L);
        Assert.assertEquals(gauge.get().longValue(), 1L);
    }

    @Test
    public void testGetterNull() {
        final Gauge gauge = new Gauge(meterId, () -> null);
        Assert.assertNull(gauge.transform());
    }

    @Test
    public void testAccept() {
        Supplier<Long> supplier1 = () -> null;
        Supplier<Long> supplier2 = () -> null;
        Assert.assertTrue(new Gauge(meterId, supplier1).accept(new Gauge(meterId, supplier1)));
        Assert.assertFalse(new Gauge(meterId, supplier1).accept(new Gauge(meterId, supplier2)));
    }

    private void validateMeterData(String name, List<Label> labels, long value, MeterData.Builder validate) {
        Assert.assertNotNull(validate);
        Assert.assertEquals(validate.getMetricCase().getNumber(), MeterData.SINGLEVALUE_FIELD_NUMBER);
        MeterSingleValue singleValue = validate.getSingleValue();
        Assert.assertNotNull(singleValue);
        Assert.assertEquals(singleValue.getValue(), value);
        Assert.assertEquals(singleValue.getName(), name);
        Assert.assertEquals(singleValue.getLabelsList(), labels);
    }
}
