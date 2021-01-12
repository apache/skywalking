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
import java.util.HashMap;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
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

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MotanProviderInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private MotanProviderInterceptor invokeInterceptor;
    @Mock
    private Response response;
    @Mock
    private Request request;

    private URL url;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Object[] arguments;
    private Class[] argumentType;

    @Before
    public void setUp() {
        invokeInterceptor = new MotanProviderInterceptor();
        url = URL.valueOf("motan://127.0.0.1:34000/org.apache.skywalking.apm.test.TestService");

        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(url);
        arguments = new Object[] {request};
        argumentType = new Class[] {request.getClass()};
        when(request.getMethodName()).thenReturn("test");
        when(request.getInterfaceName()).thenReturn("org.apache.skywalking.apm.test.TestService");
        when(request.getParamtersDesc()).thenReturn("java.lang.String, java.lang.Object");
    }

    @Test
    public void testInvokerWithoutRefSegment() throws Throwable {
        invokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, null);
        invokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, response);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMotanProviderSpan(spans.get(0));
        assertTrue(traceSegment.getRefs() == null);

    }

    @Test
    public void testInvokerWithRefSegment() throws Throwable {
        HashMap attachments = new HashMap();
        attachments.put(
            SW8CarrierItem.HEADER_NAME,
            "1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA="
        );
        when(request.getAttachments()).thenReturn(attachments);

        invokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, null);
        invokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, response);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMotanProviderSpan(spans.get(0));
        assertRefSegment(traceSegment.getRefs().get(0));
    }

    @Test
    public void testResponseWithException() throws Throwable {
        when(response.getException()).thenReturn(new RuntimeException());

        invokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, null);
        invokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, response);

        assertTraceSegmentWhenOccurException();
    }

    @Test
    public void testOccurException() throws Throwable {

        invokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, null);
        invokeInterceptor.handleMethodException(
            enhancedInstance, null, arguments, argumentType, new RuntimeException());
        invokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, response);

        assertTraceSegmentWhenOccurException();
    }

    private void assertTraceSegmentWhenOccurException() {
        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMotanProviderSpan(spans.get(0));
        SpanAssert.assertLogSize(spans.get(0), 1);
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertRefSegment(TraceSegmentRef primaryRef) {
        assertThat(SegmentRefHelper.getTraceSegmentId(primaryRef).toString(), is("3.4.5"));
        assertThat(SegmentRefHelper.getSpanId(primaryRef), is(3));
        MatcherAssert.assertThat(SegmentRefHelper.getParentServiceInstance(primaryRef), is("instance"));
        assertThat(SegmentRefHelper.getPeerHost(primaryRef), is("127.0.0.1:8080"));
    }

    private void assertMotanProviderSpan(AbstractTracingSpan span) {
        assertThat(
            span.getOperationName(),
            is("org.apache.skywalking.apm.test.TestService.test(java.lang.String, java.lang.Object)")
        );
        assertComponent(span, ComponentsDefine.MOTAN);
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.RPC_FRAMEWORK);
    }

}
