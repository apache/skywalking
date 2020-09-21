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

package org.apache.skywalking.apm.agent.core.meter.builder.adapter;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.meter.MeterDataBaseTest;
import org.apache.skywalking.apm.agent.core.meter.MeterFactory;
import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterService;
import org.apache.skywalking.apm.agent.core.meter.transform.MeterTransformer;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.util.concurrent.ConcurrentHashMap;

@RunWith(TracingSegmentRunner.class)
public class AbstractBuilderTest extends MeterDataBaseTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    // Meter registerd meter map, clean after test case finished
    private ConcurrentHashMap<MeterId, MeterTransformer> meterMap;

    @Before
    public void beforeTest() {
        MeterService meterService = Whitebox.getInternalState(AbstractBuilder.class, "METER_SERVICE");
        meterMap = Whitebox.getInternalState(meterService, "meterMap");
        meterMap.clear();
    }

    @After
    public void afterTest() {
        meterMap.clear();
    }

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testBuild() {
        MeterFactory.counter("test").tag("k1", "v1").build();
        Assert.assertEquals(1, meterMap.size());

        MeterFactory.counter("test").tag("k1", "v1").build();
        Assert.assertEquals(1, meterMap.size());

        MeterFactory.counter("test").tag("k1", "v2").build();
        Assert.assertEquals(2, meterMap.size());
    }
}
