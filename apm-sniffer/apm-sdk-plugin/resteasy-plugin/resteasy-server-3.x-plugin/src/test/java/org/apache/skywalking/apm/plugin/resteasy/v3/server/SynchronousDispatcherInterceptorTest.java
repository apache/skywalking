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

package org.apache.skywalking.apm.plugin.resteasy.v3.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
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
import org.jboss.resteasy.core.ResourceInvoker;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyAsynchronousContext;
import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SynchronousDispatcherInterceptorTest {

    private SynchronousDispatcherInterceptor synchronousDispatcherInterceptor;
    private SynchronousDispatcherExceptionInterceptor exceptionInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    HttpRequest request;

    @Mock
    HttpResponse response;

    @Mock
    ResourceInvoker resourceInvoker;

    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Mock
    private ResteasyAsynchronousContext resteasyAsynchronousContext;

    @Mock
    EnhancedInstance enhancedInstance;

    private Object[] arguments;
    private Class[] argumentType;

    private Object[] exceptionArguments;
    private Class[] exceptionArgumentType;

    @Before
    public void setup() throws URISyntaxException {
        synchronousDispatcherInterceptor = new SynchronousDispatcherInterceptor();
        exceptionInterceptor = new SynchronousDispatcherExceptionInterceptor();
        when(request.getUri()).thenReturn(new ResteasyUriInfo(new URI("http://localhost:8080/test/testRequestURL")));
        when(request.getHttpHeaders()).thenReturn(new ResteasyHttpHeaders(new MultivaluedMapImpl<String, String>()));
        when(response.getStatus()).thenReturn(200);
        when(request.getAsyncContext()).thenReturn(resteasyAsynchronousContext);
        when(request.getAsyncContext().isSuspended()).thenReturn(false);
        arguments = new Object[] {
            request,
            response,
            resourceInvoker
        };
        argumentType = new Class[] {
            request.getClass(),
            response.getClass(),
            resourceInvoker.getClass()
        };

        exceptionArguments = new Object[] {
            request,
            response,
            new RuntimeException()
        };
        exceptionArgumentType = new Class[] {
            request.getClass(),
            response.getClass(),
            new RuntimeException().getClass()
        };
    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        synchronousDispatcherInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        synchronousDispatcherInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        AssertTools.assertHttpSpan(spans.get(0));
    }

    @Test
    public void testWithSW6SerializedContextData() throws Throwable {
        MultivaluedMapImpl<String, String> multivaluedMap = new MultivaluedMapImpl<String, String>();
        multivaluedMap.putSingle(SW8CarrierItem.HEADER_NAME, "1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=");
        when(request.getHttpHeaders()).thenReturn(new ResteasyHttpHeaders(multivaluedMap));

        synchronousDispatcherInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        synchronousDispatcherInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        AssertTools.assertHttpSpan(spans.get(0));
        AssertTools.assertTraceSegmentRef(traceSegment.getRefs().get(0));
    }

    @Test
    public void testWithOccurException() throws Throwable {
        synchronousDispatcherInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        synchronousDispatcherInterceptor.handleMethodException(enhancedInstance, null, arguments, argumentType, new RuntimeException());
        synchronousDispatcherInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        AssertTools.assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    @Test
    public void testWithMainThreadOccurException() throws Throwable {
        synchronousDispatcherInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        exceptionInterceptor.beforeMethod(enhancedInstance, null, exceptionArguments, exceptionArgumentType, null);
        synchronousDispatcherInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        AssertTools.assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }
}
