package org.skywalking.apm.plugin.motan;

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
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.TracerContext;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ConstructorInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.sniffer.mock.context.MockTracingContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.sniffer.mock.trace.SpanLogReader;
import org.skywalking.apm.sniffer.mock.trace.tags.StringTagReader;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.context.tag.Tags;

import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MotanProviderInterceptorTest {

    private MockTracingContextListener contextListener;

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
        ServiceManager.INSTANCE.boot();

        invokeInterceptor = new MotanProviderInterceptor();
        contextListener = new MockTracingContextListener();
        url = URL.valueOf("motan://127.0.0.1:34000/org.skywalking.apm.test.TestService");

        TracerContext.ListenerManager.add(contextListener);

        when(instanceContext.get("REQUEST_URL")).thenReturn(url);
        when(interceptorContext.allArguments()).thenReturn(new Object[] {request});
        when(request.getMethodName()).thenReturn("test");
        when(request.getInterfaceName()).thenReturn("org.skywalking.apm.test.TestService");
        when(request.getParamtersDesc()).thenReturn("java.lang.String, java.lang.Object");
        when(constructorInvokeContext.allArguments()).thenReturn(new Object[] {url});
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
                assertTrue(traceSegment.getRefs() == null);
            }
        });
    }

    @Test
    public void testInvokerWithRefSegment() {
        HashMap attachments = new HashMap();
        attachments.put(Config.Plugin.Propagation.HEADER_NAME, "302017.1487666919810.624424584.17332.1.1|1|REMOTE_APP|127.0.0.1|Trace.globalId.123");
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
                assertRefSegment(traceSegment.getRefs().get(0));
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
                assertThat(SpanLogReader.getLogs(span).size(), is(1));
                LogData logData = SpanLogReader.getLogs(span).get(0);
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
        assertThat(span.getOperationName(), is("org.skywalking.apm.test.TestService.test(java.lang.String, java.lang.Object)"));
        assertThat(StringTagReader.get(span, Tags.COMPONENT), is("Motan"));
        assertThat(StringTagReader.get(span, Tags.SPAN_KIND), is(Tags.SPAN_KIND_SERVER));
        assertThat(StringTagReader.get(span, Tags.SPAN_LAYER.SPAN_LAYER_TAG), is("rpc"));
    }

    @After
    public void tearDown() {
        TracerContext.ListenerManager.remove(contextListener);
    }
}
