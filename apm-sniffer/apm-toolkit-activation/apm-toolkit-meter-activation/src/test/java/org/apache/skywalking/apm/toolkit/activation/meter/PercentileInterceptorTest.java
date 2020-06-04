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
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.agent.core.meter.transform.MeterTransformer;
import org.apache.skywalking.apm.agent.core.meter.transform.PercentileTransformer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.toolkit.meter.MeterId;
import org.apache.skywalking.apm.toolkit.meter.Percentile;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;
import java.util.Map;

public class PercentileInterceptorTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private PercentileInterceptor percentileInterceptor = new PercentileInterceptor();
    private EnhancedInstance enhancedInstance = new PercentileEnhance(
        new MeterId("test", MeterId.MeterType.HISTOGRAM, Arrays.asList(new MeterId.Tag("k1", "v1"))));

    @Test
    public void testConstruct() {
        percentileInterceptor.onConstruct(enhancedInstance, null);

        final MeterService service = ServiceManager.INSTANCE.findService(MeterService.class);
        final Map<MeterId, MeterTransformer> meterMap = (Map<MeterId, MeterTransformer>) Whitebox.getInternalState(service, "meterMap");
        Assert.assertEquals(1, meterMap.size());

        final Object field = meterMap.values().iterator().next();
        Assert.assertNotNull(field);
        Assert.assertTrue(field instanceof PercentileTransformer);
        final PercentileTransformer percentileTransformer = (PercentileTransformer) field;

        Assert.assertNotNull(percentileTransformer.getId());
        Assert.assertEquals("test", percentileTransformer.getId().getName());
        Assert.assertEquals(MeterType.HISTOGRAM, percentileTransformer.getId().getType());
        Assert.assertEquals(Arrays.asList(new MeterTag("k1", "v1")), percentileTransformer.getId().getTags());
    }

    private static class PercentileEnhance extends Percentile implements EnhancedInstance {
        protected PercentileEnhance(MeterId meterId) {
            super(meterId);
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
