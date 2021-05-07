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

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v3x;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.List;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class NettyRoutingFilterInterceptorTest {

    private final EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private ContextSnapshot snapshot;

        @Override
        public Object getSkyWalkingDynamicField() {
            return snapshot;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.snapshot = (ContextSnapshot) value;
        }
    };
    private final NettyRoutingFilterInterceptor interceptor = new NettyRoutingFilterInterceptor();
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testWithNullDynamicField() throws Throwable {
        interceptor.beforeMethod(null, null, new Object[]{enhancedInstance}, null, null);
        interceptor.afterMethod(null, null, null, null, null);
        ContextManager.stopSpan();
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertEquals(traceSegments.size(), 1);
        final List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegments.get(0));
        Assert.assertNotNull(spans);
        Assert.assertEquals(spans.size(), 1);
        SpanAssert.assertComponent(spans.get(0), ComponentsDefine.SPRING_CLOUD_GATEWAY);
    }

    @Test
    public void testWithContextSnapshot() throws Throwable {
        final AbstractSpan entrySpan = ContextManager.createEntrySpan("/get", null);
        SpanLayer.asHttp(entrySpan);
        entrySpan.setComponent(ComponentsDefine.SPRING_WEBFLUX);
        enhancedInstance.setSkyWalkingDynamicField(ContextManager.capture());
        interceptor.beforeMethod(null, null, new Object[]{enhancedInstance}, null, null);
        interceptor.afterMethod(null, null, null, null, null);
        ContextManager.stopSpan();
        ContextManager.stopSpan(entrySpan);
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertEquals(traceSegments.size(), 1);
        final List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegments.get(0));
        Assert.assertNotNull(spans);
        Assert.assertEquals(spans.size(), 2);
        SpanAssert.assertComponent(spans.get(0), ComponentsDefine.SPRING_CLOUD_GATEWAY);
        SpanAssert.assertComponent(spans.get(1), ComponentsDefine.SPRING_WEBFLUX);
        SpanAssert.assertLayer(spans.get(1), SpanLayer.HTTP);
    }
}