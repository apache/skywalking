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

package org.apache.skywalking.apm.plugin.reactor.netty.http.client;

import io.netty.handler.codec.http.*;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.*;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.*;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author jian.tan
 */

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class NettyHttpInterceptorTest {
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Mock
    private MockHttpClientOperations enhancedInstance;

    @Mock
    private HttpResponseStatus httpResponseStatus;

    private HttpClientOperationsInterceptor httpClientOperationsInterceptor;

    private OnOutboundCompleteInterceptor outboundCompleteInterceptor;

    private OnInboundNextInterceptor onInboundNextInterceptor;

    @Before
    public void setUp() throws Exception {
        enhancedInstance = new MockHttpClientOperations();

        httpClientOperationsInterceptor = new HttpClientOperationsInterceptor();
        outboundCompleteInterceptor = new OnOutboundCompleteInterceptor();
        onInboundNextInterceptor = new OnInboundNextInterceptor();
        when(httpResponseStatus.code()).thenReturn(200);

    }

    @Test
    public void testInterceptor() throws Throwable {

        httpClientOperationsInterceptor.onConstruct(enhancedInstance, null);
        outboundCompleteInterceptor.afterMethod(null, null, null, null, null);
        onInboundNextInterceptor.afterMethod(null, null, null, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(0));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);

        Assert.assertEquals(1, SegmentHelper.getSpans(traceSegment).size());
        AbstractTracingSpan finishedSpan = SegmentHelper.getSpans(traceSegment).get(0);

        List<TagValuePair> tags = SpanHelper.getTags(finishedSpan);
        assertThat(tags.size(), is(2));
        assertThat(tags.get(0).getValue(), is("GET"));
        assertThat(tags.get(1).getValue(), is(enhancedInstance.nettyRequest.uri()));

        Assert.assertEquals(false, SpanHelper.getErrorOccurred(finishedSpan));
    }

    private class MockHttpRequest extends DefaultHttpRequest implements EnhancedInstance {

        public MockHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri) {
            super(httpVersion, method, uri);
        }

        @Override public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override public void setSkyWalkingDynamicField(Object value) {

        }
    }

    private class MockHttpClientOperations implements EnhancedInstance {
        private MockHttpRequest nettyRequest;

        public MockHttpClientOperations() {
            this.nettyRequest = new MockHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/localhost");
        }

        @Override public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override public void setSkyWalkingDynamicField(Object value) {

        }

    }
}
