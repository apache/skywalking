package org.skywalking.apm.plugin.feign.http.v9;

import feign.Request;
import feign.Response;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.sniffer.mock.context.MockTracingContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.sniffer.mock.trace.SpanLogReader;
import org.skywalking.apm.sniffer.mock.trace.tags.BooleanTagReader;
import org.skywalking.apm.sniffer.mock.trace.tags.StringTagReader;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.tag.Tags;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Response.class})
public class DefaultHttpClientInterceptorTest {

    private DefaultHttpClientInterceptor defaultHttpClientInterceptor;
    private MockTracingContextListener mockTracerContextListener;

    private EnhancedClassInstanceContext classInstanceContext;

    @Mock
    private InstanceMethodInvokeContext instanceMethodInvokeContext;

    private Request request;

    @Before
    public void setUp() throws Exception {
        mockTracerContextListener = new MockTracingContextListener();

        classInstanceContext = new EnhancedClassInstanceContext();

        Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
        request = Request.create("GET", "http://skywalking.org", headers, "Test".getBytes(), Charset.forName("UTF-8"));

        Request.Options options = new Request.Options();

        Object[] allArguments = {request, options};
        when(instanceMethodInvokeContext.allArguments()).thenReturn(allArguments);

        ServiceManager.INSTANCE.boot();
        defaultHttpClientInterceptor = new DefaultHttpClientInterceptor();

        TracerContext.ListenerManager.add(mockTracerContextListener);
    }

    @Test
    public void testMethodsAround() throws Throwable {
        defaultHttpClientInterceptor.beforeMethod(classInstanceContext, instanceMethodInvokeContext, null);

        Response response = mock(Response.class);
        when(response.status()).thenReturn(200);
        defaultHttpClientInterceptor.afterMethod(classInstanceContext, instanceMethodInvokeContext, response);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override public void call(TraceSegment finishedSegment) {
                Assert.assertEquals(1, finishedSegment.getSpans().size());
                assertSpan(finishedSegment.getSpans().get(0));
                Assert.assertEquals(false, BooleanTagReader.get(finishedSegment.getSpans().get(0), Tags.ERROR));
            }
        });
    }

    @Test
    public void testMethodsAroundError() throws Throwable {
        defaultHttpClientInterceptor.beforeMethod(classInstanceContext, instanceMethodInvokeContext, null);

        Response response = mock(Response.class);
        when(response.status()).thenReturn(404);
        defaultHttpClientInterceptor.afterMethod(classInstanceContext, instanceMethodInvokeContext, response);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override public void call(TraceSegment finishedSegment) {
                Assert.assertEquals(1, finishedSegment.getSpans().size());
                assertSpan(finishedSegment.getSpans().get(0));
                Assert.assertEquals(true, BooleanTagReader.get(finishedSegment.getSpans().get(0), Tags.ERROR));
            }
        });
    }

    private void assertSpan(Span span) {
        Assert.assertEquals("http", StringTagReader.get(span, Tags.SPAN_LAYER.SPAN_LAYER_TAG));
        Assert.assertEquals("GET", StringTagReader.get(span, Tags.HTTP.METHOD));
        Assert.assertEquals("skywalking.org", span.getPeerHost());
        Assert.assertEquals(-1, span.getPort());
        Assert.assertEquals("FeignDefaultHttp", StringTagReader.get(span, Tags.COMPONENT));
        Assert.assertEquals("discovery", StringTagReader.get(span, Tags.SPAN_KIND));
        Assert.assertEquals("", StringTagReader.get(span, Tags.URL));
    }

    @Test
    public void testException() throws Throwable {
        defaultHttpClientInterceptor.beforeMethod(classInstanceContext, instanceMethodInvokeContext, null);

        defaultHttpClientInterceptor.handleMethodException(new NullPointerException("testException"), classInstanceContext, null);

        Response response = mock(Response.class);
        when(response.status()).thenReturn(200);
        defaultHttpClientInterceptor.afterMethod(classInstanceContext, instanceMethodInvokeContext, response);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override public void call(TraceSegment finishedSegment) {
                Assert.assertEquals(1, finishedSegment.getSpans().size());
                assertSpan(finishedSegment.getSpans().get(0));
                Assert.assertEquals(true, BooleanTagReader.get(finishedSegment.getSpans().get(0), Tags.ERROR));

                Assert.assertEquals(1, SpanLogReader.getLogs(finishedSegment.getSpans().get(0)).size());
                Assert.assertEquals(true, SpanLogReader.getLogs(finishedSegment.getSpans().get(0)).get(0).getFields().containsKey("stack"));
                Assert.assertEquals("testException", SpanLogReader.getLogs(finishedSegment.getSpans().get(0)).get(0).getFields().get("message"));
            }
        });
    }
}
