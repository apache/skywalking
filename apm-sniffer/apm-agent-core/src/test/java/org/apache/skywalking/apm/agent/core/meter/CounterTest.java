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
import org.junit.Test;

import java.util.List;

public class CounterTest {

    @Test
    public void testIncrement() {
        final List<MeterTag> tags = Lists.newArrayList(new MeterTag("key", "value"));
        final String name = "test_meter";
        final MeterId meterId = new MeterId(name, MeterType.COUNTER, tags);
        final Counter counter = new Counter(meterId);

        final List<Label> labels = meterId.transformTags();

        // empty data
        MeterData.Builder transform = counter.transform();
        Assert.assertNull(transform);

        // increment count
        counter.increment(1);
        transform = counter.transform();
        validateMeterData(name, labels, 1L, transform);

        // increment negative
        counter.increment(-2);
        transform = counter.transform();
        validateMeterData(name, labels, -2, transform);

        // multiple increment
        counter.increment(1);
        counter.increment(3);
        transform = counter.transform();
        validateMeterData(name, labels, 4, transform);

        // empty data again
        transform = counter.transform();
        Assert.assertNull(transform);

    }

    @Test
    public void testAccept() {
        final List<MeterTag> tags = Lists.newArrayList(new MeterTag("key", "value"));
        final String name = "test_meter";
        final MeterId meterId = new MeterId(name, MeterType.COUNTER, tags);
        final Counter counter1 = new Counter(meterId);
        final Counter counter2 = new Counter(meterId);

        Assert.assertTrue(counter1.accept(counter2));
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
