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

package org.apache.skywalking.apm.plugin.spring.mvc.v4;

import java.lang.reflect.Method;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.PathMappingCache;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.RequestMappingMethodInterceptor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RequestMappingMethodInterceptorTest {
    private RequestMappingMethodInterceptor interceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private ServletRequestAttributes servletRequestAttributes;

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

    @Mock
    private NativeWebRequest nativeWebRequest;

    @Before
    public void setUp() throws Exception {
        interceptor = new RequestMappingMethodInterceptor();
        enhancedInstance = new MockEnhancedInstance1();
        controllerConstructorInterceptor = new ControllerConstructorInterceptor();
        servletRequestAttributes = new ServletRequestAttributes(request, response);

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/testRequestURL"));
        when(response.getStatus()).thenReturn(200);

        arguments = new Object[] {
            request,
            response
        };
        argumentType = new Class[] {
            request.getClass(),
            response.getClass()
        };

    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        SpringTestCaseHelper.createCaseHandler(request, response, new SpringTestCaseHelper.CaseHandler() {
            @Override
            public void handleCase() throws Throwable {
                controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
                RequestMappingClass1 mappingClass1 = new RequestMappingClass1();
                Method m = mappingClass1.getClass().getMethod("testRequestURL");
                RequestContextHolder.setRequestAttributes(servletRequestAttributes);

                interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
                interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);
            }
        });

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertHttpSpan(spans.get(0));
    }

    @Test
    public void testWithOccurException() throws Throwable {
        SpringTestCaseHelper.createCaseHandler(request, response, new SpringTestCaseHelper.CaseHandler() {
            @Override
            public void handleCase() throws Throwable {
                controllerConstructorInterceptor.onConstruct(enhancedInstance, null);
                RequestMappingClass1 mappingClass1 = new RequestMappingClass1();
                Method m = mappingClass1.getClass().getMethod("testRequestURL");
                RequestContextHolder.setRequestAttributes(servletRequestAttributes);

                interceptor.beforeMethod(enhancedInstance, m, arguments, argumentType, methodInterceptResult);
                interceptor.handleMethodException(enhancedInstance, m, arguments, argumentType, new RuntimeException());
                interceptor.afterMethod(enhancedInstance, m, arguments, argumentType, null);
            }
        });

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        assertThat(SegmentRefHelper.getParentServiceInstance(ref), is("instance"));
        assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("1.444.555"));
    }

    private void assertHttpSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test/testRequestURL"));
        assertComponent(span, ComponentsDefine.SPRING_MVC_ANNOTATION);
        SpanAssert.assertTag(span, 0, "http://localhost:8080/test/testRequestURL");
        assertThat(span.isEntry(), is(true));
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
    }

    @RequestMapping(value = "/test")
    private class MockEnhancedInstance1 implements EnhancedInstance {
        private EnhanceRequireObjectCache value = new EnhanceRequireObjectCache();

        @Override
        public Object getSkyWalkingDynamicField() {
            value.setPathMappingCache(new PathMappingCache("/test"));
            return value;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    }

    private class RequestMappingClass1 {
        @RequestMapping("/testRequestURL")
        public void testRequestURL() {

        }
    }
}
