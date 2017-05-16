package org.skywalking.apm.plugin.resin.v3;

import com.caucho.server.connection.CauchoRequest;
import com.caucho.server.http.HttpResponse;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.TracerContext;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.trace.LogData;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.TraceSegment;
import org.skywalking.apm.trace.TraceSegmentRef;
import org.skywalking.apm.trace.tag.Tags;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * ResinInterceptorTest
 *
 * @author baiyang
 */
@RunWith(MockitoJUnitRunner.class)
public class ResinV3InterceptorTest {
    private ResinV3Interceptor interceptor;
    private MockTracerContextListener contextListener;

    @Mock
    private CauchoRequest request;
    @Mock
    private HttpResponse response;
    @Mock
    private EnhancedClassInstanceContext classInstanceContext;
    @Mock
    private InstanceMethodInvokeContext methodInvokeContext;
    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Before
    public void setUp() throws Exception {

        ServiceManager.INSTANCE.boot();

        interceptor = new ResinV3Interceptor();
        contextListener = new MockTracerContextListener();

        TracerContext.ListenerManager.add(contextListener);

        when(request.getPageURI()).thenReturn("/test/testRequestURL");
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/testRequestURL"));
        when(response.getStatusCode()).thenReturn(200);
        when(methodInvokeContext.allArguments()).thenReturn(new Object[] {request, response});
    }

    @Test
    public void testWithoutSerializedContextData() {
        interceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        interceptor.afterMethod(classInstanceContext, methodInvokeContext, null);

        contextListener.assertSize(1);
        contextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertHttpSpan(span);
            }
        });
    }

    @Test
    public void testWithSerializedContextData() {
        when(request.getHeader(ResinV3Interceptor.HEADER_NAME_OF_CONTEXT_DATA)).thenReturn("302017.1487666919810.624424584.17332.1.1|1|REMOTE_APP|127.0.0.1|Trace.globalId.123|1");

        interceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        interceptor.afterMethod(classInstanceContext, methodInvokeContext, null);

        contextListener.assertSize(1);
        contextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertHttpSpan(span);
                assertTraceSegmentRef(traceSegment.getRefs().get(0));
            }
        });
    }

    @Test
    public void testWithOccurException() {
        interceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        interceptor.handleMethodException(new RuntimeException(), classInstanceContext, methodInvokeContext);
        interceptor.afterMethod(classInstanceContext, methodInvokeContext, null);

        contextListener.assertSize(1);
        contextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertHttpSpan(span);
                assertThat(span.getLogs().size(), is(1));
                assertSpanLog(span.getLogs().get(0));
            }
        });
    }

    private void assertSpanLog(LogData logData) {
        assertThat(logData.getFields().size(), is(4));
        assertThat(logData.getFields().get("event"), CoreMatchers.<Object>is("error"));
        assertThat(logData.getFields().get("error.kind"), CoreMatchers.<Object>is(RuntimeException.class.getName()));
        assertNull(logData.getFields().get("message"));
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        assertThat(ref.getSpanId(), is(1));
        assertThat(ref.getTraceSegmentId(), is("302017.1487666919810.624424584.17332.1.1"));
    }

    private void assertHttpSpan(Span span) {
        assertThat(span.getOperationName(), is("/test/testRequestURL"));
        assertThat(Tags.COMPONENT.get(span), is("Resin"));
        assertThat(Tags.URL.get(span), is("http://localhost:8080/test/testRequestURL"));
        assertThat(Tags.STATUS_CODE.get(span), is(200));
        assertThat(Tags.SPAN_KIND.get(span), is(Tags.SPAN_KIND_SERVER));
        assertTrue(Tags.SPAN_LAYER.isHttp(span));
    }

    @After
    public void tearDown() throws Exception {
        TracerContext.ListenerManager.remove(new MockTracerContextListener());
    }
}
