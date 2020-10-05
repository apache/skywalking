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

package org.apache.skywalking.apm.toolkit.activation.meter;

import java.util.Arrays;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.meter.BaseMeter;
import org.apache.skywalking.apm.agent.core.meter.MeterService;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.toolkit.meter.Counter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class CounterConstructInterceptorTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private CounterConstructInterceptor counterConstructInterceptor = new CounterConstructInterceptor();
    private EnhancedInstance enhancedInstance = new CounterEnhance();

    @Test
    public void testConstruct() {
        counterConstructInterceptor.onConstruct(enhancedInstance, new Object[] {
            new MeterId("test", MeterId.MeterType.COUNTER, Arrays.asList(new MeterId.Tag("k1", "v1"))),
            Counter.Mode.RATE
        });

        final MeterService service = ServiceManager.INSTANCE.findService(MeterService.class);
        final Map<MeterId, BaseMeter> meterMap = (Map<MeterId, BaseMeter>) Whitebox.getInternalState(
            service, "meterMap");
        Assert.assertEquals(1, meterMap.size());

        final BaseMeter meterData = meterMap.values().iterator().next();
        Assert.assertTrue(meterData instanceof org.apache.skywalking.apm.agent.core.meter.Counter);
        final org.apache.skywalking.apm.agent.core.meter.Counter counter = (org.apache.skywalking.apm.agent.core.meter.Counter) meterData;

        Assert.assertNotNull(counter.getId());
        Assert.assertEquals("test", counter.getId().getName());
        Assert.assertEquals(MeterType.COUNTER, counter.getId().getType());
        Assert.assertEquals(Arrays.asList(new MeterTag("k1", "v1")), counter.getId().getTags());
    }

    private static class CounterEnhance implements EnhancedInstance {
        private Object data;

        @Override
        public Object getSkyWalkingDynamicField() {
            return data;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.data = value;
        }
    }

}
