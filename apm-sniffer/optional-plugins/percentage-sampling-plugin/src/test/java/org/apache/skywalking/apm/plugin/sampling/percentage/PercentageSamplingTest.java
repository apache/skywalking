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

package org.apache.skywalking.apm.plugin.sampling.percentage;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class PercentageSamplingTest {

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Test
    public void testServiceOverrideFromPlugin() {
        SamplingService service = ServiceManager.INSTANCE.findService(SamplingService.class);
        Assert.assertEquals(PercentageSamplingService.class, service.getClass());
    }

    @Test
    public void testDefaultSamplingRate() {
        // kick start PercentageSamplingService
        PercentageSamplingPluginConfig.Plugin.Sampling.SAMPLE_RATE = 0;

        SamplingService service = ServiceManager.INSTANCE.findService(SamplingService.class);
        service.boot();

        PercentageSamplingService.DFT_SAMPLING_RATE = 0;
        int sampleCnt = 0;
        for (int i = 0; i < 10; i++) {
            if (service.trySampling("whatever")) {
                sampleCnt++;
            }
        }
        Assert.assertEquals(0, sampleCnt);
        PercentageSamplingService.DFT_SAMPLING_RATE = 10000;
        for (int i = 0; i < 10; i++) {
            if (service.trySampling("whatever")) {
                sampleCnt++;
            }
        }
        Assert.assertEquals(10, sampleCnt);
    }
}
