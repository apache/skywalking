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

package org.apache.skywalking.apm.plugin.httpasyncclient.v4;

import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.KeyValuePair;
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
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest(HttpHost.class)
public class StateInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private StateInterceptor stateInterceptor;

    private SetResponseInterceptor setResponseInterceptor;

    private ProcessResponseInterceptor processResponseInterceptor;
    @Mock
    private HttpHost httpHost;
    @Mock
    private HttpRequestWrapper request;
    @Mock
    private HttpRequest httpRequest;
    @Mock
    private HttpResponse httpResponse;
    @Mock
    private StatusLine statusLine;

    private Object[] allArguments;
    private Class[] argumentsType;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        ServiceManager.INSTANCE.boot();
        stateInterceptor = new StateInterceptor();
        setResponseInterceptor = new SetResponseInterceptor();
        processResponseInterceptor = new ProcessResponseInterceptor();

        PowerMockito.mock(HttpHost.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpHost.getHostName()).thenReturn("127.0.0.1");
        when(httpHost.getSchemeName()).thenReturn("http");
        when(request.getOriginal()).thenReturn(httpRequest);
        when(httpRequest.getRequestLine()).thenReturn(new RequestLine() {
            @Override
            public String getMethod() {
                return "GET";
            }

            @Override
            public ProtocolVersion getProtocolVersion() {
                return new ProtocolVersion("http", 1, 1);
            }

            @Override
            public String getUri() {
                return "http://127.0.0.1:8080/test-web/httpasync";
            }
        });
        when(httpHost.getPort()).thenReturn(8080);

        allArguments = new Object[] {request};
        argumentsType = new Class[] {request.getClass()};
    }

    @Test
    public void testHttpClient() throws Throwable {
        AbstractSpan span = ContextManager.createLocalSpan("httpasyncclient/HttpAsyncRequestExecutor:");
        stateInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentsType, null);
        stateInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentsType, httpResponse);
        processResponseInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentsType, null);
        processResponseInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentsType, httpResponse);
        Assert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
        verify(request, times(1)).setHeader(anyString(), anyString());
    }

    @Test
    public void testStatusCodeNotEquals200() throws Throwable {
        when(statusLine.getStatusCode()).thenReturn(500);
        AbstractSpan span = ContextManager.createLocalSpan("httpasyncclient/HttpAsyncRequestExecutor:");
        stateInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentsType, null);
        stateInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentsType, httpResponse);
        allArguments = new Object[] {httpResponse};
        setResponseInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentsType, null);
        setResponseInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentsType, httpResponse);
        processResponseInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentsType, null);
        processResponseInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentsType, httpResponse);

        Assert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertThat(spans.size(), is(3));

        List<KeyValuePair> tags = SpanHelper.getTags(spans.get(0));
        assertThat(tags.size(), is(3));
        assertThat(tags.get(2).getValue(), is("500"));

        assertHttpSpan(spans.get(0));
        assertThat(SpanHelper.getErrorOccurred(spans.get(0)), is(true));
        verify(request, times(1)).setHeader(anyString(), anyString());
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test-web/httpasync"));
        assertThat(SpanHelper.getComponentId(span), is(26));
        List<KeyValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(0).getValue(), is("http://127.0.0.1:8080/test-web/httpasync"));
        assertThat(tags.get(1).getValue(), is("GET"));
        assertThat(span.isExit(), is(true));
    }

}
