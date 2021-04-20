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

package org.apache.skywalking.apm.plugin.jsonrpc4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonRpcBasicServer;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class JsonRpcServerTests {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private MockJsonRpcBasicServer mockJsonRpcBasicServer;

    private MockJsonServiceExporterInterceptor mockJsonServiceExporterInterceptor;

    private JsonServiceExporterInterceptor jsonServiceExporterInterceptor;

    private JsonRpcBasicServerInvokeInterceptor jsonRpcBasicServerInvokeInterceptor;

    private ObjectMapper objectMapper = new ObjectMapper();

    private HttpServletRequest httpServletRequest;

    private HttpServletResponse httpServletResponse;

    @Before
    public void setUp() throws Exception {
        httpServletRequest = mock(HttpServletRequest.class);
        httpServletResponse = mock(HttpServletResponse.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/test");
        when(httpServletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080"));
        when(httpServletResponse.getStatus()).thenReturn(200);
        httpServletResponse = mock(HttpServletResponse.class);
        jsonRpcBasicServerInvokeInterceptor = new JsonRpcBasicServerInvokeInterceptor();
        jsonServiceExporterInterceptor = new JsonServiceExporterInterceptor();
        mockJsonRpcBasicServer = new MockJsonRpcBasicServer(objectMapper, null);
        mockJsonServiceExporterInterceptor = new MockJsonServiceExporterInterceptor();
    }

    @Test
    public void testJsonRpcServerMethodAround() throws Throwable {
        Object[] objects = new Object[]{httpServletRequest};
        jsonServiceExporterInterceptor.beforeMethod(mockJsonServiceExporterInterceptor, null, objects, null, null);
        objects = new Object[]{null, JsonRpcServerTests.class.getMethod("testJsonRpcServerMethodAround")};
        jsonRpcBasicServerInvokeInterceptor.beforeMethod(mockJsonRpcBasicServer, null, objects, null, null);
        objects = new Object[]{null, httpServletResponse};
        jsonServiceExporterInterceptor.afterMethod(mockJsonServiceExporterInterceptor, null, objects, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        Assert.assertEquals(1, SegmentHelper.getSpans(traceSegment).size());
        AbstractTracingSpan finishedSpan = SegmentHelper.getSpans(traceSegment).get(0);

        List<TagValuePair> tags = SpanHelper.getTags(finishedSpan);
        assertThat(tags.size(), is(4));
        Assert.assertEquals(false, SpanHelper.getErrorOccurred(finishedSpan));
    }

    private class MockJsonRpcBasicServer extends JsonRpcBasicServer implements EnhancedInstance {

        public MockJsonRpcBasicServer(ObjectMapper mapper, Object handler) {
            super(mapper, handler);
        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
        }
    }

    private class MockJsonServiceExporterInterceptor extends JsonServiceExporterInterceptor implements EnhancedInstance {

        @Override
        public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    }
}
