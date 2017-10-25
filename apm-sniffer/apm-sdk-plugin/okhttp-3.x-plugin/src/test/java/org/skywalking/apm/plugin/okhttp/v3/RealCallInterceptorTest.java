/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.okhttp.v3;

import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.helper.SegmentHelper;
import org.skywalking.apm.agent.test.helper.SpanHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertException;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertLayer;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertLogSize;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertOccurException;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertTag;

/**
 * @author peng-yongsheng
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest({Response.class})
public class RealCallInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private RealCallInterceptor realCallInterceptor;

    @Mock
    private OkHttpClient client;

    private Request request;

    private Object[] allArguments;
    private Class[] argumentTypes;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {

        private Object object;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    };

    @Before
    public void setUp() throws Exception {
        request = new Request.Builder().url("http://skywalking.org").build();
        allArguments = new Object[] {client, request, false};
        argumentTypes = new Class[] {client.getClass(), request.getClass(), Boolean.class};
        realCallInterceptor = new RealCallInterceptor();
    }

    @Test
    public void testOnConstruct() {
        realCallInterceptor.onConstruct(enhancedInstance, allArguments);
        assertThat(enhancedInstance.getSkyWalkingDynamicField(), is(allArguments[1]));
    }

    @Test
    public void testMethodsAround() throws Throwable {
        realCallInterceptor.onConstruct(enhancedInstance, allArguments);
        realCallInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, null);

        Response response = mock(Response.class);
        when(response.code()).thenReturn(200);
        realCallInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, response);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertSpan(spans.get(0));
        assertOccurException(spans.get(0), false);
    }

    @Test
    public void testMethodsAroundError() throws Throwable {
        realCallInterceptor.onConstruct(enhancedInstance, allArguments);
        realCallInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, null);

        Response response = mock(Response.class);
        when(response.code()).thenReturn(404);
        realCallInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, response);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertSpan(spans.get(0));
        assertOccurException(spans.get(0), true);
    }

    private void assertSpan(AbstractTracingSpan span) {
        assertComponent(span, ComponentsDefine.OKHTTP);
        assertLayer(span, SpanLayer.HTTP);
        assertTag(span, 0, "GET");
        assertTag(span, 1, "http://skywalking.org/");
        assertThat(span.isExit(), is(true));
        assertThat(span.getOperationName(), is("/"));
    }

    @Test
    public void testException() throws Throwable {
        realCallInterceptor.onConstruct(enhancedInstance, allArguments);
        realCallInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, null);

        realCallInterceptor.handleMethodException(enhancedInstance, null, allArguments, argumentTypes, new NullPointerException("testException"));

        Response response = mock(Response.class);
        when(response.code()).thenReturn(200);
        realCallInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, response);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertSpan(spans.get(0));
        assertOccurException(spans.get(0), true);
        assertLogSize(spans.get(0), 1);
        assertException(SpanHelper.getLogs(spans.get(0)).get(0), NullPointerException.class, "testException");
    }
}
