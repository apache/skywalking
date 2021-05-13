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
import io.undertow.server.RoutingHandler;
import io.undertow.server.ServerConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.undertow.v2x.handler.TracingHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RoutingHandlerInterceptorTest {
    private RoutingHandlerInterceptor interceptor;
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @Mock
    private HttpHandler httpHandler;
    @Mock
    ServerConnection serverConnection;

    @Mock
    private MethodInterceptResult methodInterceptResult;
    @Mock
    private EnhancedInstance enhancedInstance;
    private String template = "/projects/{projectId}/users";
    private String uri = "/projects/{projectId}/users";

    @Before
    public void setUp() throws Exception {
        interceptor = new RoutingHandlerInterceptor();

    }

    @Test
    public void testBindArguments() throws Throwable {
        Method method = RoutingHandler.class.getMethod("add", HttpString.class, String.class, HttpHandler.class);
        Object[] arguments = new Object[] {
            Methods.GET,
            template,
            httpHandler
        };
        interceptor.beforeMethod(enhancedInstance, method, arguments, method.getParameterTypes(), methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, method, arguments, method.getParameterTypes(), null);
        assertTrue(arguments[2] instanceof TracingHandler);
    }

    @Test
    public void testStatusCodeIsOk() throws Throwable {
        TracingHandler handler = new TracingHandler(template, httpHandler);
        HttpServerExchange exchange = buildExchange();
        handler.handleRequest(exchange);
        exchange.endExchange();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
    }

    @Test
    public void testStatusCodeIsNotOk() throws Throwable {
        TracingHandler handler = new TracingHandler(template, httpHandler);
        HttpServerExchange exchange = buildExchange();
        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
        handler.handleRequest(exchange);
        exchange.endExchange();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
        assertThat(SpanHelper.getErrorOccurred(spans.get(0)), is(true));
    }

    private HttpServerExchange buildExchange() {
        HeaderMap requestHeaders = new HeaderMap();
        HeaderMap responseHeaders = new HeaderMap();
        HttpServerExchange exchange = new HttpServerExchange(serverConnection, requestHeaders, responseHeaders, 0);
        exchange.setRequestURI(uri);
        exchange.setRequestPath(uri);
        exchange.setDestinationAddress(new InetSocketAddress("localhost", 8080));
        exchange.setRequestScheme("http");
        exchange.setRequestMethod(Methods.GET);
        return exchange;
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is(template));
        assertComponent(span, ComponentsDefine.UNDERTOW);
        SpanAssert.assertTag(span, 0, "http://localhost:8080" + uri);
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
    }

}
