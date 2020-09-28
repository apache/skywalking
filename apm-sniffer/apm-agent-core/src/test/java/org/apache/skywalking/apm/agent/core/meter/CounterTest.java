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

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.powermock.reflect.Whitebox;

public class CounterTest {
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
    public void testTransform() {
        final Counter counter = MeterFactory.counter("test").tag("k1", "v1").build();

        counter.increment(2d);

        validateMeterData("test", Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build()), 2d, counter.transform());
    }

    @Test
    public void testTransformWithRate() {
        final Counter counter = MeterFactory.counter("test").tag("k1", "v1").mode(CounterMode.RATE).build();

        counter.increment(1d);
        counter.increment(2d);

        validateMeterData("test", Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build()), 3d, counter.transform());
        validateMeterData("test", Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build()), 0d, counter.transform());

        counter.increment(-4d);
        validateMeterData("test", Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build()), -4d, counter.transform());
    }

    @Test
    public void testGetCountWithoutRate() {
        final Counter counter = MeterFactory.counter("test").tag("k1", "v1").mode(CounterMode.INCREMENT).build();

        counter.increment(1d);
        counter.increment(2d);

        validateMeterData("test", Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build()), 3d, counter.transform());
        validateMeterData("test", Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build()), 3d, counter.transform());

        counter.increment(-4d);
        validateMeterData("test", Arrays.asList(Label.newBuilder().setName("k1").setValue("v1").build()), -1d, counter.transform());
    }

    /**
     * Check the single value message
     */
    private void validateMeterData(String name, List<Label> labels, double value, MeterData.Builder validate) {
        Assert.assertNotNull(validate);
        Assert.assertEquals(validate.getMetricCase().getNumber(), MeterData.SINGLEVALUE_FIELD_NUMBER);
        MeterSingleValue singleValue = validate.getSingleValue();
        Assert.assertNotNull(singleValue);
        Assert.assertEquals(value, singleValue.getValue(), 0.0);
        Assert.assertEquals(name, singleValue.getName());
        Assert.assertEquals(labels, singleValue.getLabelsList());
    }

}
