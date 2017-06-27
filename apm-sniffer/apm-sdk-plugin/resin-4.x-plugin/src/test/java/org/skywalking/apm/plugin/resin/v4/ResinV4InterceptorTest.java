package org.skywalking.apm.plugin.resin.v4;

import com.caucho.server.http.CauchoRequest;
import javax.servlet.http.HttpServletResponse;
import org.hamcrest.CoreMatchers;
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
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.sniffer.mock.context.MockTracingContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.sniffer.mock.trace.SpanLogReader;
import org.skywalking.apm.sniffer.mock.trace.tags.IntTagReader;
import org.skywalking.apm.sniffer.mock.trace.tags.StringTagReader;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.context.tag.Tags;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Created by Baiyang on 2017/5/6.
 */
@RunWith(MockitoJUnitRunner.class)
public class ResinV4InterceptorTest {
    private ResinV4Interceptor interceptor;
    private MockTracingContextListener contextListener;

    @Mock
    private CauchoRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private EnhancedClassInstanceContext classInstanceContext;
    @Mock
    private InstanceMethodInvokeContext methodInvokeContext;
    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Before
    public void setUp() throws Exception {

        ServiceManager.INSTANCE.boot();

        interceptor = new ResinV4Interceptor();
        contextListener = new MockTracingContextListener();

        TracerContext.ListenerManager.add(contextListener);

        when(request.getPageURI()).thenReturn("/test/testRequestURL");
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getRequestURI()).thenReturn("/test/testRequestURL");
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/test/testRequestURL"));
        when(response.getStatus()).thenReturn(200);
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
        when(request.getHeader(Config.Plugin.Propagation.HEADER_NAME)).thenReturn("302017.1487666919810.624424584.17332.1.1|1|REMOTE_APP|127.0.0.1|Trace.globalId.123");

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
                assertThat(SpanLogReader.getLogs(span).size(), is(1));
                assertSpanLog(SpanLogReader.getLogs(span).get(0));
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
        assertThat(StringTagReader.get(span, Tags.COMPONENT), is("Resin"));
        assertThat(StringTagReader.get(span, Tags.URL), is("http://localhost:8080/test/testRequestURL"));
        assertThat(IntTagReader.get(span, Tags.STATUS_CODE), is(200));
        assertThat(StringTagReader.get(span, Tags.SPAN_KIND), is(Tags.SPAN_KIND_SERVER));
        assertThat(StringTagReader.get(span, Tags.SPAN_LAYER.SPAN_LAYER_TAG), is("http"));
    }

    @After
    public void tearDown() throws Exception {
        TracerContext.ListenerManager.remove(new MockTracingContextListener());
    }
}
