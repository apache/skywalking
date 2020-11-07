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

package org.apache.skywalking.apm.plugin.asynchttpclient.v2;

import java.util.List;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
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
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest(Response.class)
public class ExecuteInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Mock
    private MethodInterceptResult result;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Request request;

    private Object[] allArguments;
    private Class[] argumentTypes;

    private ExecuteInterceptor executeInterceptor;

    @Before
    public void setUp() throws Exception {
        request = new RequestBuilder().setUrl("http://skywalking.org/").build();
        allArguments = new Object[] {
            request,
            null
        };
        argumentTypes = new Class[] {
            org.asynchttpclient.Request.class,
            org.asynchttpclient.AsyncHandler.class
        };
        executeInterceptor = new ExecuteInterceptor();
    }

    @Test
    public void testSuccess() throws Throwable {
        ContextManager.createEntrySpan("mock-test", new ContextCarrier());

        executeInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, result);
        executeInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, null);

        AsyncHandlerWrapper asyncHandlerWrapper = (AsyncHandlerWrapper) allArguments[1];
        asyncHandlerWrapper.onCompleted();
        ContextManager.stopSpan();

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertSpan(spans.get(0));
        SpanAssert.assertOccurException(spans.get(0), false);

    }

    @Test
    public void testException() throws Throwable {
        ContextManager.createEntrySpan("mock-test", new ContextCarrier());

        executeInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, result);
        executeInterceptor.handleMethodException(
            enhancedInstance, null, allArguments, argumentTypes, new NullPointerException("testException"));
        executeInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, result);

        ContextManager.stopSpan();
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertSpan(spans.get(0));
        SpanAssert.assertOccurException(spans.get(0), true);
        SpanAssert.assertLogSize(spans.get(0), 1);
        SpanAssert.assertException(SpanHelper.getLogs(spans.get(0))
                                             .get(0), NullPointerException.class, "testException");
    }

    @Test
    public void afterMethod() throws Throwable {
        Object ret = new Object();
        executeInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, result);
        Object result = executeInterceptor.afterMethod(enhancedInstance, null, null, null, ret);
        Assert.assertEquals(ret, result);
    }

    private void assertSpan(AbstractTracingSpan span) {
        assertComponent(span, new OfficialComponent(102, "AsyncHttpClient"));
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
        SpanAssert.assertTag(span, 0, "GET");
        SpanAssert.assertTag(span, 1, "http://skywalking.org/");
        assertThat(span.isExit(), is(true));
        assertThat(span.getOperationName(), is("AsyncHttpClient/"));
    }
}
