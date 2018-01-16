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
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.hamcrest.CoreMatchers;
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
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @auther lytscu
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest(HttpHost.class)
public class TestException {
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

    private Object[] allArguments, setResponseInterceptorArguments;
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
        setResponseInterceptorArguments = new Object[] {httpResponse};
        argumentsType = new Class[] {request.getClass()};
    }

    @Test
    public void testHttpClientWithException() throws Throwable {
        AbstractSpan localSpan = ContextManager.createLocalSpan("httpasyncclient/HttpAsyncRequestExecutor:");
        stateInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentsType, null);
        stateInterceptor.handleMethodException(enhancedInstance, null, allArguments, argumentsType, new RuntimeException("testException"));
        processResponseInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentsType, null);
        processResponseInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentsType, httpResponse);
        Assert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertThat(spans.size(), is(3));
        AbstractTracingSpan span = spans.get(0);
        assertThat(SpanHelper.getErrorOccurred(span), is(true));
        assertHttpSpanErrorLog(SpanHelper.getLogs(span));
        verify(request, times(1)).setHeader(anyString(), anyString());

    }

    private void assertHttpSpanErrorLog(List<LogDataEntity> logs) {
        assertThat(logs.size(), is(1));
        LogDataEntity logData = logs.get(0);
        Assert.assertThat(logData.getLogs().size(), is(4));
        Assert.assertThat(logData.getLogs().get(0).getValue(), CoreMatchers.<Object>is("error"));
        Assert.assertThat(logData.getLogs().get(1).getValue(), CoreMatchers.<Object>is(RuntimeException.class.getName()));
        Assert.assertThat(logData.getLogs().get(2).getValue(), is("testException"));
        assertNotNull(logData.getLogs().get(3).getValue());
    }
}
