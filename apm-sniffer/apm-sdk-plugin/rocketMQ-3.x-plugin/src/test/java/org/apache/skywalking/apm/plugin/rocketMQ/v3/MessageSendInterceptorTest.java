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

package org.apache.skywalking.apm.plugin.rocketMQ.v3;

import java.util.List;
import com.alibaba.rocketmq.client.impl.CommunicationMode;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.common.protocol.header.SendMessageRequestHeader;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MessageSendInterceptorTest {

    private MessageSendInterceptor messageSendInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private Object[] arguments;

    private Object[] argumentsWithoutCallback;

    @Mock
    private Message message;

    @Mock
    private SendMessageRequestHeader messageRequestHeader;

    @Mock
    private EnhancedInstance callBack;

    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() {
        messageSendInterceptor = new MessageSendInterceptor();
        enhancedInstance = new EnhancedInstance() {
            @Override
            public Object getSkyWalkingDynamicField() {
                return "127.0.0.1:6543";
            }

            @Override
            public void setSkyWalkingDynamicField(Object value) {

            }
        };

        arguments = new Object[] {
            "127.0.0.1",
            "test",
            message,
            messageRequestHeader,
            null,
            CommunicationMode.ASYNC,
            callBack
        };
        argumentsWithoutCallback = new Object[] {
            "127.0.0.1",
            "test",
            message,
            messageRequestHeader,
            null,
            CommunicationMode.ASYNC,
            null
        };
        when(messageRequestHeader.getProperties()).thenReturn("");
        when(message.getTags()).thenReturn("TagA");
    }

    @Test
    public void testSendMessage() throws Throwable {
        messageSendInterceptor.beforeMethod(enhancedInstance, null, arguments, null, null);
        messageSendInterceptor.afterMethod(enhancedInstance, null, arguments, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan mqSpan = spans.get(0);

        SpanAssert.assertLayer(mqSpan, SpanLayer.MQ);
        SpanAssert.assertComponent(mqSpan, ComponentsDefine.ROCKET_MQ_PRODUCER);
        SpanAssert.assertTag(mqSpan, 0, "127.0.0.1");
        verify(messageRequestHeader).setProperties(anyString());
        verify(callBack).setSkyWalkingDynamicField(Matchers.any());
    }

    @Test
    public void testSendMessageWithoutCallBack() throws Throwable {
        messageSendInterceptor.beforeMethod(enhancedInstance, null, argumentsWithoutCallback, null, null);
        messageSendInterceptor.afterMethod(enhancedInstance, null, argumentsWithoutCallback, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));

        AbstractTracingSpan mqSpan = spans.get(0);

        SpanAssert.assertLayer(mqSpan, SpanLayer.MQ);
        SpanAssert.assertComponent(mqSpan, ComponentsDefine.ROCKET_MQ_PRODUCER);
        SpanAssert.assertTag(mqSpan, 0, "127.0.0.1");
        verify(messageRequestHeader).setProperties(anyString());
    }

}
