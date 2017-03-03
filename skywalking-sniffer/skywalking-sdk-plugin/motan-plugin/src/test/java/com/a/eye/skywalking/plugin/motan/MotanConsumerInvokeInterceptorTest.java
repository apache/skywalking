package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.api.context.TracerContext;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.context.SegmentAssert;
import com.a.eye.skywalking.trace.LogData;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MotanConsumerInvokeInterceptorTest {

    private MockTracerContextListener contextListener;

    private MotanConsumerInvokeInterceptor invokeInterceptor;
    @Mock
    private EnhancedClassInstanceContext instanceContext;
    @Mock
    private InstanceMethodInvokeContext interceptorContext;
    @Mock
    private Response response;
    @Mock
    private Request request;

    private URL url;

    @Before
    public void setUp() {
        contextListener = new MockTracerContextListener();
        invokeInterceptor = new MotanConsumerInvokeInterceptor();
        url = URL.valueOf("motan://127.0.0.1:34000/com.a.eye.skywalking.test.TestService");

        TracerContext.ListenerManager.add(contextListener);

        when(instanceContext.get("REQUEST_URL")).thenReturn(url);
        when(interceptorContext.allArguments()).thenReturn(new Object[]{request});
        when(request.getMethodName()).thenReturn("test");
        when(request.getInterfaceName()).thenReturn("com.a.eye.skywalking.test.TestService");
        when(request.getParamtersDesc()).thenReturn("java.lang.String, java.lang.Object");
    }

    @Test
    public void testInvokeInterceptor() {
        invokeInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        invokeInterceptor.afterMethod(instanceContext, interceptorContext, response);

        contextListener.assertSize(1);
        contextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertMotanConsumerSpan(span);
                verify(request, times(1)).setAttachment(anyString(), anyString());
            }
        });
    }

    @Test
    public void testResponseWithException() {
        when(response.getException()).thenReturn(new RuntimeException());

        invokeInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        invokeInterceptor.afterMethod(instanceContext, interceptorContext, response);

        contextListener.assertSize(1);
        assertTraceSegmentWhenOccurException();
    }

    private void assertTraceSegmentWhenOccurException() {
        contextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertMotanConsumerSpan(span);
                verify(request, times(1)).setAttachment(anyString(), anyString());
                assertThat(span.getLogs().size(), is(1));
                LogData logData = span.getLogs().get(0);
                assertLogData(logData);
            }
        });
    }

    @Test
    public void testInvokeInterceptorWithException() {

        invokeInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        invokeInterceptor.handleMethodException(new RuntimeException(), instanceContext, interceptorContext);
        invokeInterceptor.afterMethod(instanceContext, interceptorContext, response);

        contextListener.assertSize(1);
        assertTraceSegmentWhenOccurException();
    }

    private void assertLogData(LogData logData) {
        assertThat(logData.getFields().size(), is(4));
        MatcherAssert.assertThat(logData.getFields().get("event"), CoreMatchers.<Object>is("error"));
        MatcherAssert.assertThat(logData.getFields().get("error.kind"), CoreMatchers.<Object>is(RuntimeException.class.getName()));
        assertNull(logData.getFields().get("message"));
    }

    private void assertMotanConsumerSpan(Span span) {
        assertThat(span.getOperationName(), is("com.a.eye.skywalking.test.TestService.test(java.lang.String, java.lang.Object)"));
        assertThat(Tags.COMPONENT.get(span), is("Motan"));
        assertThat(Tags.SPAN_KIND.get(span), is(Tags.SPAN_KIND_CLIENT));
        assertThat(Tags.PEER_HOST.get(span), is("127.0.0.1"));
        assertThat(Tags.PEER_PORT.get(span), is(34000));
        assertTrue(Tags.SPAN_LAYER.isRPCFramework(span));
        assertThat(Tags.URL.get(span), is("motan://127.0.0.1:34000/default_rpc/com.a.eye.skywalking.test.TestService/1.0/service"));
    }

    @After
    public void tearDown() {
        TracerContext.ListenerManager.remove(contextListener);
    }
}