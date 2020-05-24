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
import org.apache.skywalking.apm.agent.core.meter.Gauge;
import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.toolkit.meter.Tag;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Supplier;

@RunWith(TracingSegmentRunner.class)
public class GaugeInterceptorTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private GaugeInterceptor gaugeInterceptor = new GaugeInterceptor();

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private Object data;
        @Override
        public Object getSkyWalkingDynamicField() {
            return data;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.data = value;
        }
    };

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testConstruct() {
        Supplier supplier = () -> 1L;
        gaugeInterceptor.onConstruct(enhancedInstance, new Object[] {
            "test", Arrays.asList(new Tag("k1", "v1")), supplier
        });

        final Object field = enhancedInstance.getSkyWalkingDynamicField();
        Assert.assertNotNull(field);
        Assert.assertTrue(field instanceof Gauge);
        final Gauge gauge = (Gauge) field;

        Assert.assertNotNull(gauge.getId());
        Assert.assertEquals(gauge.getId().getName(), "test");
        Assert.assertEquals(gauge.getId().getType(), MeterType.GAUGE);
        Assert.assertEquals(gauge.getId().getTags(), Arrays.asList(new MeterTag("k1", "v1")));

        final Supplier<Long> getter = (Supplier<Long>) Whitebox.getInternalState(gauge, "getter");
        Assert.assertEquals(getter.get().longValue(), 1L);
    }

    @Test
    public void testInvoke() throws Throwable {
        Supplier supplier = () -> 1L;
        enhancedInstance.setSkyWalkingDynamicField(new Gauge(new MeterId("test", MeterType.GAUGE, Collections.emptyList()), supplier));

        gaugeInterceptor.beforeMethod(enhancedInstance, null, new Object[] {}, null, null);
        final Object val = gaugeInterceptor.afterMethod(enhancedInstance, null, new Object[] {}, null, null);
        Assert.assertEquals(val, 1L);
    }
}
