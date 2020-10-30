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
 */

package org.apache.skywalking.apm.plugin.httpasyncclient.v4;

import java.net.URI;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.httpasyncclient.v4.wrapper.FutureCallbackWrapper;
import org.apache.skywalking.apm.plugin.httpasyncclient.v4.wrapper.HttpAsyncResponseConsumerWrapper;
import org.apache.skywalking.apm.plugin.httpclient.HttpClientPluginConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestCompleteInterceptor.CONTEXT_LOCAL;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest(HttpHost.class)
public class HttpAsyncClientInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private EnhancedInstance enhancedInstance;

    private HttpAsyncClientInterceptor httpAsyncClientInterceptor;

    private HttpAsyncRequestExecutorInterceptor requestExecutorInterceptor;

    private SessionRequestConstructorInterceptor sessionRequestConstructorInterceptor;

    private SessionRequestCompleteInterceptor completeInterceptor;

    @Mock
    private HttpAsyncRequestProducer producer;

    @Mock
    private HttpAsyncResponseConsumer consumer;

    @Mock
    private HttpContext httpContext;

    @Mock
    private FutureCallback callback;

    @Mock
    private HttpRequestWrapper requestWrapper;

    @Mock
    private HttpHost httpHost;

    @Mock
    private HttpResponse response;

    @Before
    public void setUp() throws Exception {
        ServiceManager.INSTANCE.boot();
        httpAsyncClientInterceptor = new HttpAsyncClientInterceptor();
        requestExecutorInterceptor = new HttpAsyncRequestExecutorInterceptor();
        sessionRequestConstructorInterceptor = new SessionRequestConstructorInterceptor();
        completeInterceptor = new SessionRequestCompleteInterceptor();

        httpContext = new BasicHttpContext();
        httpContext.setAttribute(HttpClientContext.HTTP_REQUEST, requestWrapper);
        httpContext.setAttribute(HttpClientContext.HTTP_TARGET_HOST, httpHost);
        CONTEXT_LOCAL.set(httpContext);
        HttpClientPluginConfig.Plugin.HttpClient.COLLECT_HTTP_PARAMS = true;
        when(httpHost.getHostName()).thenReturn("127.0.0.1");
        when(httpHost.getSchemeName()).thenReturn("http");

        final RequestLine requestLine = new RequestLine() {
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
                return "http://127.0.0.1:8080/test-web/test";
            }
        };

        when(response.getStatusLine()).thenReturn(new StatusLine() {
            @Override
            public ProtocolVersion getProtocolVersion() {
                return new ProtocolVersion("http", 1, 1);
            }

            @Override
            public int getStatusCode() {
                return 200;
            }

            @Override
            public String getReasonPhrase() {
                return null;
            }
        });

        when(requestWrapper.getRequestLine()).thenReturn(requestLine);
        when(requestWrapper.getOriginal()).thenReturn(new HttpGet("http://localhost:8081/original/test"));
        when(requestWrapper.getURI()).thenReturn(new URI("http://localhost:8081/original/test?a=1&b=test"));
        when(httpHost.getPort()).thenReturn(8080);

        enhancedInstance = new EnhancedInstance() {

            private Object object;

            @Override
            public Object getSkyWalkingDynamicField() {
                return object;
            }

            @Override
            public void setSkyWalkingDynamicField(Object value) {
                this.object = value;
            }
        };
    }

    @Test
    public void testSuccess() throws Throwable {

        //mock active span;
        ContextManager.createEntrySpan("mock-test", new ContextCarrier());

        Thread thread = baseTest();

        ContextManager.stopSpan();

        thread.join();
        Assert.assertThat(segmentStorage.getTraceSegments().size(), is(2));

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(findNeedSegemnt());
        assertHttpSpan(spans.get(0));
        verify(requestWrapper, times(3)).setHeader(anyString(), anyString());

    }

    @Test
    public void testNoContext() throws Throwable {

        Thread thread = baseTest();
        thread.join();

        Assert.assertThat(segmentStorage.getTraceSegments().size(), is(0));

        verify(requestWrapper, times(0)).setHeader(anyString(), anyString());

    }

    private Thread baseTest() throws Throwable {
        Object[] allArguments = new Object[] {
            producer,
            consumer,
            httpContext,
            callback
        };
        Class[] types = new Class[] {
            HttpAsyncRequestProducer.class,
            HttpAsyncResponseConsumer.class,
            HttpContext.class,
            FutureCallback.class
        };
        httpAsyncClientInterceptor.beforeMethod(enhancedInstance, null, allArguments, types, null);
        Assert.assertEquals(CONTEXT_LOCAL.get(), httpContext);
        Assert.assertTrue(allArguments[1] instanceof HttpAsyncResponseConsumerWrapper);
        Assert.assertTrue(allArguments[3] instanceof FutureCallbackWrapper);

        sessionRequestConstructorInterceptor.onConstruct(enhancedInstance, null);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //start local
                    completeInterceptor.beforeMethod(enhancedInstance, null, null, null, null);
                    //start request
                    requestExecutorInterceptor.beforeMethod(enhancedInstance, null, null, null, null);

                    HttpAsyncResponseConsumerWrapper consumerWrapper = new HttpAsyncResponseConsumerWrapper(consumer);

                    consumerWrapper.responseReceived(response);

                    new FutureCallbackWrapper(callback).completed(null);

                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        });
        thread.start();
        return thread;
    }

    private TraceSegment findNeedSegemnt() {
        for (TraceSegment traceSegment : segmentStorage.getTraceSegments()) {
            if (SegmentHelper.getSpans(traceSegment).size() > 1) {
                return traceSegment;
            }
        }
        return null;
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test-web/test"));
        assertThat(SpanHelper.getComponentId(span), is(26));
        List<TagValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(0).getValue(), is("http://localhost:8081/original/test"));
        assertThat(tags.get(1).getValue(), is("GET"));
        assertThat(tags.get(2).getValue(), is("a=1&b=test"));
        assertThat(span.isExit(), is(true));
    }

    @Test
    public void afterMethod() throws Throwable {
        baseCompleteTest(completeInterceptor);
        baseCompleteTest(httpAsyncClientInterceptor);
        baseCompleteTest(requestExecutorInterceptor);
    }

    private void baseCompleteTest(InstanceMethodsAroundInterceptor instanceMethodsAroundInterceptor) throws Throwable {
        Object ret = new Object();
        Object result = instanceMethodsAroundInterceptor.afterMethod(enhancedInstance, null, null, null, ret);
        Assert.assertEquals(ret, result);
    }
}