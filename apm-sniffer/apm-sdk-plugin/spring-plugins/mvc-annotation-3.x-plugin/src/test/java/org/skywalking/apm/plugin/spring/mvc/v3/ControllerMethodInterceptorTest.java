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

package org.skywalking.apm.plugin.spring.mvc.v3;

import java.lang.reflect.Method;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.context.SW3CarrierItem;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.helper.SegmentHelper;
import org.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.skywalking.apm.agent.test.helper.SpanHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertException;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertLayer;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertTag;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest({RequestContextHolder.class, ServletRequestAttributes.class})
public class ControllerMethodInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;
    @Mock
    private NativeWebRequest nativeWebRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private ServletRequestAttributes servletRequestAttributes;
    @Mock
    private HttpServletRequest httpServletRequest;

    private Method method;

    private EnhanceRequireObjectCache enhanceRequireObjectCache;
    private ControllerMethodInterceptor controllerMethodInterceptor;

    @Before
    public void setUp() throws NoSuchMethodException {
        controllerMethodInterceptor = new ControllerMethodInterceptor();
        enhanceRequireObjectCache = new EnhanceRequireObjectCache();
        enhanceRequireObjectCache.setPathMappingCache(new PathMappingCache("/test"));
        method = ControllerMethodInterceptorTest.class.getDeclaredMethod("mockControllerService");
        enhanceRequireObjectCache.addPathMapping(method, "/test");

        enhanceRequireObjectCache.setNativeWebRequest(nativeWebRequest);
        enhanceRequireObjectCache.setPathMappingCache(new PathMappingCache("/test"));

        mockStatic(RequestContextHolder.class);
        when(servletRequestAttributes.getRequest()).thenReturn(httpServletRequest);
        when(nativeWebRequest.getNativeResponse()).thenReturn(httpServletResponse);
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(enhanceRequireObjectCache);
        when(RequestContextHolder.getRequestAttributes()).thenReturn(servletRequestAttributes);
        when(httpServletRequest.getMethod()).thenReturn("GET");
        when(httpServletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/skywalking-test/test"));
    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        controllerMethodInterceptor.beforeMethod(enhancedInstance, method, null, null, null);
        controllerMethodInterceptor.afterMethod(enhancedInstance, method, null, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertRequestSpan(spans.get(0));
    }

    @Test
    public void testWithSerializedContextData() throws Throwable {
        Mockito.when(httpServletRequest.getHeader(SW3CarrierItem.HEADER_NAME)).thenReturn("1.234.111|3|1|1|#192.168.1.8:18002|#/portal/|#/testEntrySpan|#AQA*#AQA*Et0We0tQNQA*");

        controllerMethodInterceptor.beforeMethod(enhancedInstance, method, null, null, null);
        controllerMethodInterceptor.afterMethod(enhancedInstance, method, null, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertRequestSpan(spans.get(0));

        List<TraceSegmentRef> traceSegmentRefs = traceSegment.getRefs();
        assertThat(traceSegmentRefs.size(), is(1));
        assertTraceSegmentRef(traceSegmentRefs.get(0));
    }

    @Test
    public void testOccurException() throws Throwable {
        controllerMethodInterceptor.beforeMethod(enhancedInstance, method, null, null, null);
        controllerMethodInterceptor.handleMethodException(enhancedInstance, method, null, null, new RuntimeException());
        controllerMethodInterceptor.afterMethod(enhancedInstance, method, null, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertRequestSpan(spans.get(0));

        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        assertThat(SegmentRefHelper.getEntryApplicationInstanceId(ref), is(1));
        assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("1.234.111"));
    }

    private void assertRequestSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("/test/test"));
        assertComponent(span, ComponentsDefine.SPRING_MVC_ANNOTATION);
        assertTag(span, 0, "http://localhost:8080/skywalking-test/test");
        assertThat(span.isEntry(), is(true));
        assertLayer(span, SpanLayer.HTTP);
    }

    @RequestMapping("/test")
    public void mockControllerService() {

    }
}
