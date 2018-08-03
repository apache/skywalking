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


package org.apache.skywalking.apm.plugin.undertow1x;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.apache.skywalking.apm.agent.core.context.SW3CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.*;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import javax.servlet.DispatcherType;

import java.net.InetSocketAddress;
import java.util.List;

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Copyright @ 2018/8/2
 *
 * @author cloudgc
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class UndertowInvokeInterceptorTest {


    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    ServerConnection serverConnection;


    private UndertowInvokeInterceptor undertowInvokeInterceptor;
    private UndertowExceptionInterceptor undertowExceptionInterceptor;

    private Object[] arguments;
    private Class[] argumentType;

    private Object[] exceptionArguments;
    private Class[] exceptionArgumentType;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    private HttpServerExchange exchange;

    private HeaderMap heads;


    @Before
    public void setUp() throws Exception {
        undertowInvokeInterceptor = new UndertowInvokeInterceptor();
        undertowExceptionInterceptor = new UndertowExceptionInterceptor();

        String reqUri = "/test/testRequestURL";
        String reqUrl = "http://localhost:7777//test/testRequestURL";
        heads = new HeaderMap();
        exchange = new HttpServerExchange(serverConnection, heads, null, 1024L);
        exchange.setRequestURI(reqUri);
        exchange.setRequestScheme("http");
        exchange.setRequestPath(reqUrl);
        exchange.setDestinationAddress(new InetSocketAddress("localhost", 7777));
        exchange.setRequestMethod(HttpString.tryFromString("POST"));
        arguments = new Object[]{exchange, null, null, DispatcherType.REQUEST};
        argumentType = new Class[]{exchange.getClass(), null, null, DispatcherType.class};

        exceptionArguments = new Object[]{exchange, null, null, null, new RuntimeException()};
        exceptionArgumentType = new Class[]{exchange.getClass(), null, null, null, new Exception().getClass()};

    }

    @Test
    public void tet1ExchangeContextData() throws Throwable {
        undertowInvokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        undertowInvokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
    }

    @Test
    public void testWithSerializedContextData() throws Throwable {

        heads.put(HttpString.tryFromString(SW3CarrierItem.HEADER_NAME),
                "1.234.111|3|1|1|#192.168.1.8:18002|#/portal/|#/testEntrySpan|#AQA*#AQA*Et0We0tQNQA*");
        undertowInvokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        undertowInvokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        assertTraceSegmentRef(traceSegment.getRefs().get(0));
    }


    @Test
    public void testWithOccurException() throws Throwable {
        undertowInvokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        undertowInvokeInterceptor.handleMethodException(enhancedInstance, null, arguments, argumentType, new RuntimeException());
        undertowInvokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }


    @Test
    public void testWithUndertowException() throws Throwable {
        undertowInvokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        undertowExceptionInterceptor.beforeMethod(enhancedInstance, null, exceptionArguments, exceptionArgumentType, null);
        undertowInvokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }


    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        assertThat(SegmentRefHelper.getEntryApplicationInstanceId(ref), is(1));
        assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("1.234.111"));
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test/testRequestURL"));
        assertComponent(span, ComponentsDefine.UNDERTOW);
        SpanAssert.assertTag(span, 0, "http://localhost:7777/test/testRequestURL");
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
    }

}
