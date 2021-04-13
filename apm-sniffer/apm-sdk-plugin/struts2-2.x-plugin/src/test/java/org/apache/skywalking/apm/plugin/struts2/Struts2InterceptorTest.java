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

package org.apache.skywalking.apm.plugin.struts2;

import com.opensymphony.xwork2.ActionContext;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.struts2.StrutsStatics;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertException;
import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertLayer;
import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertTag;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest({ActionContext.class})
public class Struts2InterceptorTest {

    private Struts2Interceptor struts2Interceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    private ActionContext actionContext;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Object[] arguments;
    private Class[] argumentType;

    private Object[] exceptionArguments;
    private Class[] exceptionArgumentType;

    @Before
    public void setUp() throws Exception {
        struts2Interceptor = new Struts2Interceptor();
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/testRequestURL"));
        when(response.getStatus()).thenReturn(200);

        mockStatic(ActionContext.class);
        actionContext = mock(ActionContext.class);
        when(actionContext.get(org.apache.struts2.StrutsStatics.HTTP_RESPONSE)).thenReturn(response);
        when(actionContext.get(StrutsStatics.HTTP_REQUEST)).thenReturn(request);
        when(ActionContext.getContext()).thenReturn(actionContext);

        arguments = new Object[] {
            request,
            response
        };
        argumentType = new Class[] {
            request.getClass(),
            response.getClass()
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
        struts2Interceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        struts2Interceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
    }

    @Test
    public void testWithSerializedContextData() throws Throwable {
        when(request.getHeader(
            SW8CarrierItem.HEADER_NAME)).thenReturn("1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=");

        struts2Interceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        struts2Interceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        assertTraceSegmentRef(traceSegment.getRef());
    }

    @Test
    public void testWithOccurException() throws Throwable {
        struts2Interceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        struts2Interceptor.handleMethodException(enhancedInstance, null, arguments, argumentType, new RuntimeException());
        struts2Interceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        assertThat(SegmentRefHelper.getParentServiceInstance(ref), is("instance"));
        assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("3.4.5"));
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test/testRequestURL"));
        assertComponent(span, ComponentsDefine.STRUTS2);
        assertTag(span, 0, "http://localhost:8080/test/testRequestURL");
        assertThat(span.isEntry(), is(true));
        assertLayer(span, SpanLayer.HTTP);
    }
}
