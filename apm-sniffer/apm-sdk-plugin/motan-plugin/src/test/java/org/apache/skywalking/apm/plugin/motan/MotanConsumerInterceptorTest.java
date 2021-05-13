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

package org.apache.skywalking.apm.plugin.motan;

import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MotanConsumerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private MotanConsumerInterceptor invokeInterceptor;
    @Mock
    private Response response;
    @Mock
    private Request request;

    private URL url;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() {
        invokeInterceptor = new MotanConsumerInterceptor();
        url = URL.valueOf("motan://127.0.0.1:34000/org.apache.skywalking.apm.test.TestService");

        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(url);
        when(request.getMethodName()).thenReturn("test");
        when(request.getInterfaceName()).thenReturn("org.apache.skywalking.apm.test.TestService");
        when(request.getParamtersDesc()).thenReturn("java.lang.String, java.lang.Object");
    }

    @Test
    public void testInvokeInterceptor() throws Throwable {
        invokeInterceptor.beforeMethod(enhancedInstance, null, new Object[] {request}, new Class[] {request.getClass()}, null);
        invokeInterceptor.afterMethod(enhancedInstance, null, new Object[] {request}, new Class[] {request.getClass()}, response);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMotanConsumerSpan(spans.get(0));
        verify(request, times(3)).setAttachment(anyString(), anyString());
    }

    @Test
    public void testResponseWithException() throws Throwable {
        when(response.getException()).thenReturn(new RuntimeException());

        invokeInterceptor.beforeMethod(enhancedInstance, null, new Object[] {request}, new Class[] {request.getClass()}, null);
        invokeInterceptor.afterMethod(enhancedInstance, null, new Object[] {request}, new Class[] {request.getClass()}, response);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertTraceSegmentWhenOccurException(spans.get(0));
    }

    private void assertTraceSegmentWhenOccurException(AbstractTracingSpan tracingSpan) {
        assertMotanConsumerSpan(tracingSpan);
        verify(request, times(3)).setAttachment(anyString(), anyString());
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(tracingSpan);
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    @Test
    public void testInvokeInterceptorWithException() throws Throwable {

        invokeInterceptor.beforeMethod(enhancedInstance, null, new Object[] {request}, new Class[] {request.getClass()}, null);
        invokeInterceptor.handleMethodException(enhancedInstance, null, new Object[] {request}, new Class[] {request.getClass()}, new RuntimeException());
        invokeInterceptor.afterMethod(enhancedInstance, null, new Object[] {request}, new Class[] {request.getClass()}, response);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertTraceSegmentWhenOccurException(spans.get(0));
    }

    private void assertMotanConsumerSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("org.apache.skywalking.apm.test.TestService.test(java.lang.String, java.lang.Object)"));
        assertComponent(span, ComponentsDefine.MOTAN);
        SpanAssert.assertLayer(span, SpanLayer.RPC_FRAMEWORK);
        SpanAssert.assertTag(span, 0, "motan://127.0.0.1:34000/default_rpc/org.apache.skywalking.apm.test.TestService/1.0/service");
    }

}
