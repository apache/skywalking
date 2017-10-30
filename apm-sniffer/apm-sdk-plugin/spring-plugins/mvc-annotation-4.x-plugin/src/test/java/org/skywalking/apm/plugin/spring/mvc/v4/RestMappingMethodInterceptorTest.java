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

package org.skywalking.apm.plugin.spring.mvc.v4;

import java.lang.reflect.Method;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.agent.test.helper.SegmentHelper;
import org.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.skywalking.apm.agent.test.helper.SpanHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertException;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertLayer;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertTag;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RestMappingMethodInterceptorTest {
    private RestMappingMethodInterceptor interceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;
    @Mock
    private MethodInterceptResult methodInterceptResult;

    private Object[] arguments;
    private Class[] argumentType;

    private EnhancedInstance enhancedInstance;

    private ControllerConstructorInterceptor controllerConstructorInterceptor;

    @Before
    public void setUp() throws Exception {
        interceptor = new RestMappingMethodInterceptor();
        enhancedInstance = new RestMappingMethodInterceptorTest.MockEnhancedInstance1();
        controllerConstructorInterceptor = new ControllerConstructorInterceptor();

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(response.getStatus()).thenReturn(200);

        arguments = new Object[] {request, response};
        argumentType = new Class[] {request.getClass(), response.getClass()};

    }

    @Test
    public void testGetMapping() throws Throwable {
        controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
        RestMappingClass1 mappingClass1 = new RestMappingClass1();
        Method m = mappingClass1.getClass().getMethod("getRequestURL");
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/getRequestURL"));
        ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);

        interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0), "/getRequestURL");
    }

    @Test
    public void testPostMapping() throws Throwable {
        controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
        RestMappingClass1 mappingClass1 = new RestMappingClass1();
        Method m = mappingClass1.getClass().getMethod("postRequestURL");
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/postRequestURL"));
        ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);

        interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0), "/postRequestURL");
    }

    @Test
    public void testPutMapping() throws Throwable {
        controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
        RestMappingClass1 mappingClass1 = new RestMappingClass1();
        Method m = mappingClass1.getClass().getMethod("putRequestURL");
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/putRequestURL"));
        ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);

        interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0), "/putRequestURL");
    }

    @Test
    public void testDeleteMapping() throws Throwable {
        controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
        RestMappingClass1 mappingClass1 = new RestMappingClass1();
        Method m = mappingClass1.getClass().getMethod("deleteRequestURL");
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/deleteRequestURL"));
        ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);

        interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0), "/deleteRequestURL");
    }

    @Test
    public void testPatchMapping() throws Throwable {
        controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
        RestMappingClass1 mappingClass1 = new RestMappingClass1();
        Method m = mappingClass1.getClass().getMethod("patchRequestURL");
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/patchRequestURL"));
        ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);

        interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0), "/patchRequestURL");
    }

    @Test
    public void testDummy() throws Throwable {
        controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
        RestMappingClass1 mappingClass1 = new RestMappingClass1();
        Method m = mappingClass1.getClass().getMethod("dummy");
        when(request.getRequestURI()).thenReturn("/test");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test"));
        ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);

        interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0), "");
    }

    @Test
    public void testWithOccurException() throws Throwable {
        controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
        RestMappingClass1 mappingClass1 = new RestMappingClass1();
        Method m = mappingClass1.getClass().getMethod("getRequestURL");
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/getRequestURL"));
        ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(servletRequestAttributes);

        interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
        interceptor.handleMethodException(enhancedInstance, m, arguments, argumentType, new RuntimeException());
        interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0), "/getRequestURL");
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        assertThat(SegmentRefHelper.getEntryApplicationInstanceId(ref), is(1));
        assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("1.444.555"));
    }

    private void assertHttpSpan(AbstractTracingSpan span, String suffix) {
        assertThat(span.getOperationName(), is("/test" + suffix));
        assertComponent(span, ComponentsDefine.SPRING_MVC_ANNOTATION);
        assertTag(span, 0, "http://localhost:8080/test" + suffix);
        assertThat(span.isEntry(), is(true));
        assertLayer(span, SpanLayer.HTTP);
    }

    @RequestMapping(value = "/test")
    private class MockEnhancedInstance1 implements EnhancedInstance {
        private Object value;

        @Override
        public Object getSkyWalkingDynamicField() {
            return value;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.value = value;
        }
    }

    private class RestMappingClass1 {
        @GetMapping("/getRequestURL")
        public void getRequestURL() {

        }

        @PostMapping("/postRequestURL")
        public void postRequestURL() {

        }

        @PutMapping("/putRequestURL")
        public void putRequestURL() {

        }

        @DeleteMapping("/deleteRequestURL")
        public void deleteRequestURL() {

        }

        @PatchMapping("/patchRequestURL")
        public void patchRequestURL() {

        }

        public void dummy() {

        }
    }
}
