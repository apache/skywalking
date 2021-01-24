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

package org.apache.skywalking.apm.agent.core.sampling;

import org.apache.skywalking.apm.agent.core.conf.dynamic.AgentConfigChangeWatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class SamplingRateWatcherTest {
    private SamplingService samplingService = new SamplingService();

    @Before
    public void setUp() {
        samplingService.prepare();
    }

    @Test
    public void testConfigModifyEvent() {
        SamplingRateWatcher samplingRateWatcher = Whitebox.getInternalState(
            samplingService, "samplingRateWatcher");
        samplingRateWatcher.notify(new AgentConfigChangeWatcher.ConfigChangeEvent(
            "10",
            AgentConfigChangeWatcher.EventType.MODIFY
        ));
        Assert.assertEquals(10, samplingRateWatcher.getSamplingRate());
        Assert.assertEquals("agent.sample_n_per_3_secs", samplingRateWatcher.getPropertyKey());
    }

    @Test
    public void testConfigDeleteEvent() {
        SamplingRateWatcher samplingRateWatcher = Whitebox.getInternalState(
            samplingService, "samplingRateWatcher");
        samplingRateWatcher.notify(new AgentConfigChangeWatcher.ConfigChangeEvent(
            null,
            AgentConfigChangeWatcher.EventType.DELETE
        ));
        Assert.assertEquals("agent.sample_n_per_3_secs", samplingRateWatcher.getPropertyKey());
    }
}
