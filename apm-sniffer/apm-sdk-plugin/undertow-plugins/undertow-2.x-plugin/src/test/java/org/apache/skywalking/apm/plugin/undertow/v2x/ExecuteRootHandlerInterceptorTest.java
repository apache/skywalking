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

package org.apache.skywalking.apm.plugin.undertow.v2x;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import java.net.InetSocketAddress;
import java.util.List;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.SW3CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
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
import org.junit.After;
import org.junit.Assert;
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

/**
 * @author chenpengfei
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ExecuteRootHandlerInterceptorTest {

    private ExecuteRootHandlerInterceptor executeRootHandlerInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private HttpHandler httpHandler;
    private HttpServerExchange exchange;
    @Mock
    ServerConnection serverConnection;
    private HeaderMap requestHeaders = new HeaderMap();
    private HeaderMap responseHeaders = new HeaderMap();

    private Object[] arguments;
    private Class[] argumentType;

    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Before
    public void setUp() throws Exception {
        Config.Agent.ACTIVE_V1_HEADER = true;
        executeRootHandlerInterceptor = new ExecuteRootHandlerInterceptor();
        exchange = new HttpServerExchange(serverConnection, requestHeaders, responseHeaders, 0);
        exchange.setRequestURI("/test/testRequestURL");
        exchange.setRequestPath("/test/testRequestURL");
        exchange.setDestinationAddress(new InetSocketAddress("localhost", 8080));
        exchange.setRequestScheme("http");
        exchange.setRequestMethod(HttpString.tryFromString("POST"));
        arguments = new Object[] {httpHandler, exchange};
        argumentType = new Class[] {httpHandler.getClass(), exchange.getClass()};
    }

    @After
    public void clear() {
        Config.Agent.ACTIVE_V1_HEADER = false;
    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        executeRootHandlerInterceptor.beforeMethod(EnhancedInstance.class, null, arguments, argumentType, methodInterceptResult);
        executeRootHandlerInterceptor.afterMethod(EnhancedInstance.class, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
    }

    @Test
    public void testWithSerializedContextData() throws Throwable {
        requestHeaders.put(HttpString.tryFromString(SW3CarrierItem.HEADER_NAME), "1.234.111|3|1|1|#192.168.1.8:18002|#/portal/|#/testEntrySpan|#AQA*#AQA*Et0We0tQNQA*");

        executeRootHandlerInterceptor.beforeMethod(EnhancedInstance.class, null, arguments, argumentType, methodInterceptResult);
        executeRootHandlerInterceptor.afterMethod(EnhancedInstance.class, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        assertTraceSegmentRef(traceSegment.getRefs().get(0));
    }

    @Test
    public void testStatusCodeNotEquals200() throws Throwable {
        exchange.setStatusCode(500);
        executeRootHandlerInterceptor.beforeMethod(EnhancedInstance.class, null, arguments, argumentType, methodInterceptResult);
        executeRootHandlerInterceptor.afterMethod(EnhancedInstance.class, null, arguments, argumentType, null);

        Assert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertThat(spans.size(), is(1));

        List<TagValuePair> tags = SpanHelper.getTags(spans.get(0));
        assertThat(tags.size(), is(3));
        assertThat(tags.get(2).getValue(), is("500"));

        assertHttpSpan(spans.get(0));
        assertThat(SpanHelper.getErrorOccurred(spans.get(0)), is(true));
    }

    @Test
    public void testWithUndertowException() throws Throwable {
        executeRootHandlerInterceptor.beforeMethod(EnhancedInstance.class, null, arguments, argumentType, methodInterceptResult);
        executeRootHandlerInterceptor.handleMethodException(EnhancedInstance.class, null, arguments, argumentType, new RuntimeException());
        executeRootHandlerInterceptor.afterMethod(EnhancedInstance.class, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        assertThat(SegmentRefHelper.getEntryServiceInstanceId(ref), is(1));
        assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("1.234.111"));
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test/testRequestURL"));
        assertComponent(span, ComponentsDefine.UNDERTOW);
        SpanAssert.assertTag(span, 0, "http://localhost:8080/test/testRequestURL");
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
    }
}