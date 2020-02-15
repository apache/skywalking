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

package org.apache.skywalking.apm.plugin.rocketMQ.v4;

import java.util.List;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.rocketMQ.v4.define.SendCallBackEnhanceInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class OnSuccessInterceptorTest {

    private OnSuccessInterceptor successInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private ContextSnapshot contextSnapshot;
    @Mock
    private SendResult sendResult;

    private SendCallBackEnhanceInfo enhanceInfo;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() {
        successInterceptor = new OnSuccessInterceptor();

        enhanceInfo = new SendCallBackEnhanceInfo("test", contextSnapshot);
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(enhanceInfo);
        when(sendResult.getSendStatus()).thenReturn(SendStatus.SEND_OK);
    }

    @Test
    public void testOnSuccess() throws Throwable {
        successInterceptor.beforeMethod(enhancedInstance, null, new Object[] {sendResult}, null, null);
        successInterceptor.afterMethod(enhancedInstance, null, new Object[] {sendResult}, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan successSpan = spans.get(0);

        SpanAssert.assertComponent(successSpan, ComponentsDefine.ROCKET_MQ_PRODUCER);

    }

    @Test
    public void testOnSuccessWithErrorStatus() throws Throwable {
        when(sendResult.getSendStatus()).thenReturn(SendStatus.FLUSH_SLAVE_TIMEOUT);
        successInterceptor.beforeMethod(enhancedInstance, null, new Object[] {sendResult}, null, null);
        successInterceptor.afterMethod(enhancedInstance, null, new Object[] {sendResult}, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan successSpan = spans.get(0);

        SpanAssert.assertComponent(successSpan, ComponentsDefine.ROCKET_MQ_PRODUCER);
        SpanAssert.assertOccurException(successSpan, true);

    }

}
