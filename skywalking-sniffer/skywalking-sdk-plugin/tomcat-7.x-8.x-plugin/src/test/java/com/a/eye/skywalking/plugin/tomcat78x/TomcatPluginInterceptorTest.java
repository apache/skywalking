package com.a.eye.skywalking.plugin.tomcat78x;

import com.a.eye.skywalking.context.TracerContext;
import com.a.eye.skywalking.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.a.eye.skywalking.trace.tag.Tags;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TomcatPluginInterceptorTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private InstanceMethodInvokeContext interceptorContext;
    private EnhancedClassInstanceContext context;
    private MethodInterceptResult result;
    private TomcatPluginInterceptor tomcatPluginInterceptor;

    @BeforeClass
    public static void setUpBeforeClass() {
        TracerContext.ListenerManager.add(TestTraceContextListener.INSTANCE);
    }

    @Before
    public void setUp() {
        context = new EnhancedClassInstanceContext();
        result = new MethodInterceptResult();
        tomcatPluginInterceptor = new TomcatPluginInterceptor();
        TestTraceContextListener.INSTANCE.clearData();

        when(request.getRequestURI()).thenReturn("/test/a");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/a"));
        when(interceptorContext.allArguments()).thenReturn(new Object[]{request, response});
        when(response.getStatus()).thenReturn(200);
    }

    @Test
    public void testNormalRequest() {
        tomcatPluginInterceptor.beforeMethod(context, interceptorContext, result);
        tomcatPluginInterceptor.afterMethod(context, interceptorContext, null);
        assertThat(TestTraceContextListener.INSTANCE.getTraceSegments().size(), is(1));
        assertTraceSegment(TestTraceContextListener.INSTANCE.getTraceSegments().get(0));
        assertNull(TestTraceContextListener.INSTANCE.getTraceSegments().get(0).getPrimaryRef());
        verify(response, times(1)).getStatus();
    }

    @Test
    public void testRequestWithHeader() {
        when(request.getHeader("SkyWalking-TRACING-NAME")).thenReturn("trace_id|2");
        tomcatPluginInterceptor.beforeMethod(context, interceptorContext, result);
        tomcatPluginInterceptor.afterMethod(context, interceptorContext, null);
        assertThat(TestTraceContextListener.INSTANCE.getTraceSegments().size(), is(1));
        assertTraceSegment(TestTraceContextListener.INSTANCE.getTraceSegments().get(0));

        assertNotNull(TestTraceContextListener.INSTANCE.getTraceSegments().get(0).getPrimaryRef());
        assertPrimaryRef(TestTraceContextListener.INSTANCE.getTraceSegments().get(0).getPrimaryRef(), 2);
    }

    @Test
    public void testRequestWithOccurException() {
        tomcatPluginInterceptor.beforeMethod(context, interceptorContext, result);
        tomcatPluginInterceptor.handleMethodException(new Throwable("occur exception"), context, interceptorContext);
        tomcatPluginInterceptor.afterMethod(context, interceptorContext, null);

        assertThat(TestTraceContextListener.INSTANCE.getTraceSegments().size(), is(1));
        assertTraceSegment(TestTraceContextListener.INSTANCE.getTraceSegments().get(0));
        Span span = TestTraceContextListener.INSTANCE.getTraceSegments().get(0).getSpans().get(0);
        assertThat(span.getLogs().size(), is(1));
    }

    private void assertTraceSegment(TraceSegment expectedSegment) {
        assertThat(expectedSegment.getSpans().size(), is(1));
        assertSpan(expectedSegment.getSpans().get(0));
    }

    private void assertPrimaryRef(TraceSegmentRef actualSegmentRef, int expectedSpanId) {
        assertThat(actualSegmentRef.getSpanId(), is(expectedSpanId));
        assertNotNull(actualSegmentRef.getTraceSegmentId());
    }

    private void assertSpan(Span span) {
        assertTrue(Tags.SPAN_LAYER.isHttp(span));
        assertThat(span.getOperationName(), is("/test/a"));
        assertThat(Tags.URL.get(span), is("http://localhost:8080/test/a"));
        assertThat(span.getSpanId(), is(0));
    }

}