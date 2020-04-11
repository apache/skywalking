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

package org.apache.skywalking.apm.plugin.jetty.v9.server;

import java.util.List;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.HttpTransport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class HandleInterceptorTest {

    private HandleInterceptor jettyInvokeInterceptor;
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private Request request;

    @Mock
    private Response response;
    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Mock
    private MockService service;

    private Object[] arguments;
    private Class[] argumentType;

    @Before
    public void setUp() throws Exception {
        jettyInvokeInterceptor = new HandleInterceptor();
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/testRequestURL"));
        when(response.getStatus()).thenReturn(200);
        when(service.getResponse()).thenReturn(response);
        when(service.getRequest()).thenReturn(request);
        arguments = new Object[] {service};
        argumentType = new Class[] {service.getClass()};

    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        jettyInvokeInterceptor.beforeMethod(service, null, arguments, argumentType, methodInterceptResult);
        jettyInvokeInterceptor.afterMethod(service, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
    }

    @Test
    public void testWithSerializedContextData() throws Throwable {
        when(request.getHeader(
            SW8CarrierItem.HEADER_NAME)).thenReturn("1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=");

        jettyInvokeInterceptor.beforeMethod(service, null, arguments, argumentType, methodInterceptResult);
        jettyInvokeInterceptor.afterMethod(service, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        assertTraceSegmentRef(traceSegment.getRefs().get(0));
    }

    @Test
    public void testWithOccurException() throws Throwable {
        jettyInvokeInterceptor.beforeMethod(service, null, arguments, argumentType, methodInterceptResult);
        jettyInvokeInterceptor.handleMethodException(service, null, arguments, argumentType, new RuntimeException());
        jettyInvokeInterceptor.afterMethod(service, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        assertThat(SegmentRefHelper.getParentServiceInstance(ref), is("instance"));
        assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("3.4.5"));
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test/testRequestURL"));
        assertComponent(span, ComponentsDefine.JETTY_SERVER);
        SpanAssert.assertTag(span, 0, "http://localhost:8080/test/testRequestURL");
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
    }

    public static class MockService extends HttpChannel implements EnhancedInstance {

        public MockService(Connector connector, HttpConfiguration configuration, EndPoint endPoint,
            HttpTransport transport, HttpInput input) {
            super(connector, configuration, endPoint, transport, input);
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
