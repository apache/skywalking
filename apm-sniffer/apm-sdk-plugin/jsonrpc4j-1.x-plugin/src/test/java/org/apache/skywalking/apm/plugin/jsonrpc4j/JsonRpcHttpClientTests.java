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
import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.context.ContextManagerExtendService;
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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class JsonRpcHttpClientTests {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private MockJsonRpcHttpClient enhancedInstance;
    private ObjectMapper objectMapper = new ObjectMapper();

    private JsonRpcHttpClientInterceptor httpClientInterceptor;
    private JsonRpcHttpClientPrepareConnectionInterceptor jsonRpcHttpClientPrepareConnectionInterceptor;
    private URL url;
    private HttpURLConnection httpURLConnection;

    @Before
    public void setUp() throws Exception {
        url = new URL("HTTP://localhost:8080/test");
        enhancedInstance = new MockJsonRpcHttpClient(objectMapper, url, new HashMap<>(), false, false);
        httpClientInterceptor = new JsonRpcHttpClientInterceptor();
        jsonRpcHttpClientPrepareConnectionInterceptor = new JsonRpcHttpClientPrepareConnectionInterceptor();
        httpURLConnection = (HttpURLConnection) url.openConnection();
    }

    @Test
    public void testMethodAround() throws Throwable {
        Object[] objects = new Object[]{"OperationKey", url};
        httpClientInterceptor.onConstruct(enhancedInstance, objects);
        httpClientInterceptor.beforeMethod(enhancedInstance, null, objects, null, null);
        jsonRpcHttpClientPrepareConnectionInterceptor.afterMethod(enhancedInstance, null, null, null, httpURLConnection);
        httpClientInterceptor.afterMethod(enhancedInstance, null, objects, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        Assert.assertEquals(1, SegmentHelper.getSpans(traceSegment).size());
        AbstractTracingSpan finishedSpan = SegmentHelper.getSpans(traceSegment).get(0);

        List<TagValuePair> tags = SpanHelper.getTags(finishedSpan);
        assertThat(tags.size(), is(2));
        assertThat(tags.get(0).getValue(), is("POST"));
        assertThat(tags.get(1).getValue(), is(url.toString()));
        Assert.assertEquals(false, SpanHelper.getErrorOccurred(finishedSpan));
    }

    private class MockJsonRpcHttpClient extends JsonRpcHttpClient implements EnhancedInstance {

        private Object object;

        public MockJsonRpcHttpClient(ObjectMapper mapper, URL serviceUrl, Map<String, String> headers, boolean gzipRequests, boolean acceptGzipResponses) {
            super(mapper, serviceUrl, headers, gzipRequests, acceptGzipResponses);
        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            object = value;
        }
    }

    @OverrideImplementor(ContextManagerExtendService.class)
    public static class ContextManagerExtendOverrideService extends ContextManagerExtendService {
    }
}
