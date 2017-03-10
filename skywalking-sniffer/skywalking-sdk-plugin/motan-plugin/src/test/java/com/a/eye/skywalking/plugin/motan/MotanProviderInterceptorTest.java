package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.api.context.TracerContext;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.ConstructorInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.context.SegmentAssert;
import com.a.eye.skywalking.trace.LogData;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
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
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MotanProviderInterceptorTest {


    private MockTracerContextListener contextListener;

    private MotanProviderInterceptor invokeInterceptor;
    @Mock
    private EnhancedClassInstanceContext instanceContext;
    @Mock
    private InstanceMethodInvokeContext interceptorContext;
    @Mock
    private ConstructorInvokeContext constructorInvokeContext;
    @Mock
    private Response response;
    @Mock
    private Request request;

    private URL url;

    @Before
    public void setUp() {
        invokeInterceptor = new MotanProviderInterceptor();
        contextListener = new MockTracerContextListener();
        url = URL.valueOf("motan://127.0.0.1:34000/com.a.eye.skywalking.test.TestService");

        TracerContext.ListenerManager.add(contextListener);

        when(instanceContext.get("REQUEST_URL")).thenReturn(url);
        when(interceptorContext.allArguments()).thenReturn(new Object[]{request});
        when(request.getMethodName()).thenReturn("test");
        when(request.getInterfaceName()).thenReturn("com.a.eye.skywalking.test.TestService");
        when(request.getParamtersDesc()).thenReturn("java.lang.String, java.lang.Object");
        when(constructorInvokeContext.allArguments()).thenReturn(new Object[]{url});
    }

    @Test
    public void testInvokerWithoutRefSegment() {
        invokeInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        invokeInterceptor.afterMethod(instanceContext, interceptorContext, response);

        contextListener.assertSize(1);
        contextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertMotanProviderSpan(span);
                assertTrue(traceSegment.getPrimaryRef() == null);
            }
        });
    }

    @Test
    public void testInvokerWithRefSegment() {
        HashMap attachments = new HashMap();
        attachments.put("SWTraceContext", "302017.1487666919810.624424584.17332.1.1|1|REMOTE_APP|127.0.0.1");
        when(request.getAttachments()).thenReturn(attachments);

        invokeInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        invokeInterceptor.afterMethod(instanceContext, interceptorContext, response);

        contextListener.assertSize(1);
        contextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertMotanProviderSpan(span);
                assertRefSegment(traceSegment.getPrimaryRef());
            }
        });
    }


    @Test
    public void testResponseWithException() {
        when(response.getException()).thenReturn(new RuntimeException());

        invokeInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        invokeInterceptor.afterMethod(instanceContext, interceptorContext, response);

        assertTraceSegmentWhenOccurException();
    }

    @Test
    public void testOccurException() {

        invokeInterceptor.beforeMethod(instanceContext, interceptorContext, null);
        invokeInterceptor.handleMethodException(new RuntimeException(), instanceContext, interceptorContext);
        invokeInterceptor.afterMethod(instanceContext, interceptorContext, response);

        assertTraceSegmentWhenOccurException();
    }

    private void assertTraceSegmentWhenOccurException() {
        contextListener.assertSize(1);
        contextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertMotanProviderSpan(span);
                assertThat(span.getLogs().size(), is(1));
                LogData logData = span.getLogs().get(0);
                assertLogData(logData);
            }
        });
    }


    private void assertLogData(LogData logData) {
        assertThat(logData.getFields().size(), is(4));
        MatcherAssert.assertThat(logData.getFields().get("event"), CoreMatchers.<Object>is("error"));
        MatcherAssert.assertThat(logData.getFields().get("error.kind"), CoreMatchers.<Object>is(RuntimeException.class.getName()));
        assertNull(logData.getFields().get("message"));
    }

    private void assertRefSegment(TraceSegmentRef primaryRef) {
        assertThat(primaryRef.getTraceSegmentId(), is("302017.1487666919810.624424584.17332.1.1"));
        assertThat(primaryRef.getSpanId(), is(1));
        assertThat(primaryRef.getPeerHost(), is("127.0.0.1"));
    }


    private void assertMotanProviderSpan(Span span) {
        assertThat(span.getOperationName(), is("com.a.eye.skywalking.test.TestService.test(java.lang.String, java.lang.Object)"));
        assertThat(Tags.COMPONENT.get(span), is("Motan"));
        assertThat(Tags.SPAN_KIND.get(span), is(Tags.SPAN_KIND_SERVER));
        assertTrue(Tags.SPAN_LAYER.isRPCFramework(span));
    }


    @After
    public void tearDown() {
        TracerContext.ListenerManager.remove(contextListener);
    }
}