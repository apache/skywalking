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

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.meter.MeterService;
import org.apache.skywalking.apm.agent.core.meter.transform.CounterTransformer;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.agent.core.meter.transform.MeterTransformer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.toolkit.meter.impl.CounterImpl;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;
import java.util.Map;

public class CounterInterceptorTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private CounterInterceptor counterInterceptor = new CounterInterceptor();
    private EnhancedInstance enhancedInstance = new CounterEnhance(
        new MeterId("test", MeterId.MeterType.COUNTER, Arrays.asList(new MeterId.Tag("k1", "v1"))));

    @Test
    public void testConstruct() {
        counterInterceptor.onConstruct(enhancedInstance, null);

        final MeterService service = ServiceManager.INSTANCE.findService(MeterService.class);
        final Map<MeterId, MeterTransformer> meterMap = (Map<MeterId, MeterTransformer>) Whitebox.getInternalState(service, "meterMap");
        Assert.assertEquals(1, meterMap.size());

        final MeterTransformer transformer = meterMap.values().iterator().next();
        Assert.assertTrue(transformer instanceof CounterTransformer);
        final CounterTransformer counterTransformer = (CounterTransformer) transformer;

        Assert.assertNotNull(counterTransformer.getId());
        Assert.assertEquals("test", counterTransformer.getId().getName());
        Assert.assertEquals(MeterType.COUNTER, counterTransformer.getId().getType());
        Assert.assertEquals(Arrays.asList(new MeterTag("k1", "v1")), counterTransformer.getId().getTags());
    }

    private static class CounterEnhance extends CounterImpl implements EnhancedInstance {
        protected CounterEnhance(MeterId meterId) {
            super(meterId, Mode.INCREMENT);
        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
        }
    }

}
