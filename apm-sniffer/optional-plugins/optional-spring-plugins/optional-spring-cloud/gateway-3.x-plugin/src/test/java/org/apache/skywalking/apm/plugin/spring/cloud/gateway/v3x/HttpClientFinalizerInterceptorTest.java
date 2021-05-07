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

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
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
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v3x.define.EnhanceObjectCache;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.reactivestreams.Publisher;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import java.util.List;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class HttpClientFinalizerInterceptorTest {

    private final static String URI = "http://localhost:8080/get";
    private final static String ENTRY_OPERATION_NAME = "/get";
    private final HttpClientFinalizerSendInterceptor sendInterceptor = new HttpClientFinalizerSendInterceptor();
    private final HttpClientFinalizerResponseConnectionInterceptor responseConnectionInterceptor = new HttpClientFinalizerResponseConnectionInterceptor();
    private final BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>> originalSendBiFunction = (httpClientRequest, nettyOutbound) -> (Publisher<Void>) s -> {
    };
    private final BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher<Void>> originalResponseConnectionBiFunction = (httpClientResponse, connection) -> (Publisher<Void>) s -> {
    };
    private final EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private EnhanceObjectCache enhanceObjectCache;

        @Override
        public Object getSkyWalkingDynamicField() {
            return enhanceObjectCache;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.enhanceObjectCache = (EnhanceObjectCache) value;
        }
    };
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    private HttpClientResponse mockResponse;
    private HttpClientRequest mockRequest;
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    private AbstractSpan entrySpan;

    @Before
    public void setUp() throws Exception {
        final EnhanceObjectCache enhanceObjectCache = new EnhanceObjectCache();
        enhanceObjectCache.setUrl(URI);
        enhancedInstance.setSkyWalkingDynamicField(enhanceObjectCache);
        entrySpan = ContextManager.createEntrySpan(ENTRY_OPERATION_NAME, null);
        entrySpan.setLayer(SpanLayer.HTTP);
        entrySpan.setComponent(ComponentsDefine.SPRING_WEBFLUX);
        mockRequest = new MockCliengRequest();
        mockResponse = new MockClientResponse();
    }

    @Test
    public void testWithDynamicFieldNull() throws Throwable {
        enhancedInstance.setSkyWalkingDynamicField(null);
        executeSendRequest();
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertEquals(traceSegments.size(), 0);
        if (ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
    }

    @Test
    public void testWithEmptyUri() throws Throwable {
        final EnhanceObjectCache objectCache = (EnhanceObjectCache) enhancedInstance.getSkyWalkingDynamicField();
        objectCache.setUrl("");
        executeSendRequest();
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertEquals(traceSegments.size(), 1);
        final List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegments.get(0));
        assertNotNull(spans);
        assertEquals(spans.size(), 1);
        assertUpstreamSpan(spans.get(0));
        assertNotNull(objectCache.getSpan1());
        assertEquals(objectCache.getSpan1().getSpanId(), entrySpan.getSpanId());
    }

    @Test
    public void testWithUri() throws Throwable {
        executeSendRequest();
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertEquals(traceSegments.size(), 1);
        final EnhanceObjectCache objectCache = (EnhanceObjectCache) enhancedInstance
                .getSkyWalkingDynamicField();
        assertNotNull(objectCache.getSpan1());
        assertNotNull(objectCache.getSpan());
        assertEquals(objectCache.getSpan1().getSpanId(), entrySpan.getSpanId());
        assertTrue(objectCache.getSpan().isExit());
        assertEquals(objectCache.getSpan().getOperationName(), "SpringCloudGateway/sendRequest");
        final List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegments.get(0));
        assertNotNull(spans);
        assertEquals(spans.size(), 2);
        assertUpstreamSpan(spans.get(1));
        assertDownstreamSpan(spans.get(0));
    }

    private void executeSendRequest() throws Throwable {
        Object[] sendArguments = new Object[]{originalSendBiFunction};
        sendInterceptor.beforeMethod(enhancedInstance, null, sendArguments, null, null);
        sendInterceptor.afterMethod(enhancedInstance, null, new Object[0], null, enhancedInstance);
        ((BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>>) sendArguments[0])
                .apply(mockRequest, null);
        Object[] responseConnectionArguments = new Object[]{originalResponseConnectionBiFunction};
        responseConnectionInterceptor
                .beforeMethod(enhancedInstance, null, responseConnectionArguments, null, null);
        responseConnectionInterceptor.afterMethod(enhancedInstance, null, new Object[0], null, enhancedInstance);
        ((BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher<Void>>) responseConnectionArguments[0])
                .apply(mockResponse, null);
    }

    private void assertUpstreamSpan(AbstractSpan span) {
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
        SpanAssert.assertComponent(span, ComponentsDefine.SPRING_WEBFLUX);
    }

    private void assertDownstreamSpan(AbstractSpan span) {
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
        SpanAssert.assertComponent(span, ComponentsDefine.SPRING_CLOUD_GATEWAY);
        SpanAssert.assertTagSize(span, 2);
        SpanAssert.assertTag(span, 0, URI);
        SpanAssert.assertTag(span, 1, String.valueOf(HttpResponseStatus.OK.code()));
    }
}