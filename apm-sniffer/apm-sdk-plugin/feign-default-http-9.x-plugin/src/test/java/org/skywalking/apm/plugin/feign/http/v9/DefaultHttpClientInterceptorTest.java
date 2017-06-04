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
import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.TraceSegment;
import org.skywalking.apm.trace.tag.Tags;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Response.class})
public class DefaultHttpClientInterceptorTest {

    private DefaultHttpClientInterceptor defaultHttpClientInterceptor;
    private MockTracerContextListener mockTracerContextListener;

    private EnhancedClassInstanceContext classInstanceContext;

    @Mock
    private InstanceMethodInvokeContext instanceMethodInvokeContext;

    private Request request;

    @Before
    public void setUp() throws Exception {
        mockTracerContextListener = new MockTracerContextListener();

        classInstanceContext = new EnhancedClassInstanceContext();

        Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
        request = Request.create("GET", "http://skywalking.org", headers, "Test".getBytes(), Charset.forName("UTF-8"));

        Request.Options options = new Request.Options();

        Object[] allArguments = {request, options};
        when(instanceMethodInvokeContext.allArguments()).thenReturn(allArguments);

        ServiceManager.INSTANCE.boot();
        defaultHttpClientInterceptor = new DefaultHttpClientInterceptor();

        TracingContext.ListenerManager.add(mockTracerContextListener);
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
                Assert.assertEquals(false, Tags.ERROR.get(finishedSegment.getSpans().get(0)));
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
                Assert.assertEquals(true, Tags.ERROR.get(finishedSegment.getSpans().get(0)));
            }
        });
    }

    private void assertSpan(Span span) {
        Assert.assertEquals("http", Tags.SPAN_LAYER.get(span));
        Assert.assertEquals("GET", Tags.HTTP.METHOD.get(span));
        Assert.assertEquals("skywalking.org", Tags.PEER_HOST.get(span));
        Assert.assertEquals(-1, Tags.PEER_PORT.get(span).intValue());
        Assert.assertEquals("FeignDefaultHttp", Tags.COMPONENT.get(span));
        Assert.assertEquals("client", Tags.SPAN_KIND.get(span));
        Assert.assertEquals("", Tags.URL.get(span));
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
                Assert.assertEquals(true, Tags.ERROR.get(finishedSegment.getSpans().get(0)));

                Assert.assertEquals(1, finishedSegment.getSpans().get(0).getLogs().size());
                Assert.assertEquals(true, finishedSegment.getSpans().get(0).getLogs().get(0).getFields().containsKey("stack"));
                Assert.assertEquals("testException", finishedSegment.getSpans().get(0).getLogs().get(0).getFields().get("message"));
            }
        });
    }
}
