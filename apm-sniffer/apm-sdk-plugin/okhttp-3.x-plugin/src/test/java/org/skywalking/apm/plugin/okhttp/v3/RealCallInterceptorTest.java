package org.skywalking.apm.plugin.okhttp.v3;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.TracerContext;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ConstructorInvokeContext;
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
public class RealCallInterceptorTest {

    private RealCallInterceptor realCallInterceptor;
    private MockTracerContextListener mockTracerContextListener;

    private EnhancedClassInstanceContext classInstanceContext;

    @Mock
    private ConstructorInvokeContext constructorInvokeContext;

    @Mock
    private InstanceMethodInvokeContext instanceMethodInvokeContext;

    @Mock
    private OkHttpClient client;

    private Request request;

    @Before
    public void setUp() throws Exception {
        mockTracerContextListener = new MockTracerContextListener();

        classInstanceContext = new EnhancedClassInstanceContext();

        request = new Request.Builder().url("http://skywalking.org").build();
        Object[] allArguments = {client, request, false};
        when(constructorInvokeContext.allArguments()).thenReturn(allArguments);

        ServiceManager.INSTANCE.boot();
        realCallInterceptor = new RealCallInterceptor();

        TracerContext.ListenerManager.add(mockTracerContextListener);
    }

    @Test
    public void testOnConstruct() {
        realCallInterceptor.onConstruct(classInstanceContext, constructorInvokeContext);
        Assert.assertEquals(request, classInstanceContext.get("SWRequestContextKey"));
    }

    @Test
    public void testMethodsAround() throws Throwable {
        realCallInterceptor.onConstruct(classInstanceContext, constructorInvokeContext);
        realCallInterceptor.beforeMethod(classInstanceContext, instanceMethodInvokeContext, null);

        Response response = mock(Response.class);
        when(response.code()).thenReturn(200);
        realCallInterceptor.afterMethod(classInstanceContext, instanceMethodInvokeContext, response);

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
        realCallInterceptor.onConstruct(classInstanceContext, constructorInvokeContext);
        realCallInterceptor.beforeMethod(classInstanceContext, instanceMethodInvokeContext, null);

        Response response = mock(Response.class);
        when(response.code()).thenReturn(404);
        realCallInterceptor.afterMethod(classInstanceContext, instanceMethodInvokeContext, response);

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
        Assert.assertEquals(80, Tags.PEER_PORT.get(span).intValue());
        Assert.assertEquals("OKHttp", Tags.COMPONENT.get(span));
        Assert.assertEquals("client", Tags.SPAN_KIND.get(span));
        Assert.assertEquals("/", Tags.URL.get(span));
    }

    @Test
    public void testException() throws Throwable {
        realCallInterceptor.onConstruct(classInstanceContext, constructorInvokeContext);
        realCallInterceptor.beforeMethod(classInstanceContext, instanceMethodInvokeContext, null);

        realCallInterceptor.handleMethodException(new NullPointerException("testException"), classInstanceContext, null);

        Response response = mock(Response.class);
        when(response.code()).thenReturn(200);
        realCallInterceptor.afterMethod(classInstanceContext, instanceMethodInvokeContext, response);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override public void call(TraceSegment finishedSegment) {
                Assert.assertEquals(1, finishedSegment.getSpans().size());
                assertSpan(finishedSegment.getSpans().get(0));
                Assert.assertEquals(true, Tags.ERROR.get(finishedSegment.getSpans().get(0)));

                Assert.assertEquals(1, finishedSegment.getSpans().get(0).getLogs().size());
                Assert.assertEquals(true, finishedSegment.getSpans().get(0).getLogs().get(0).getFields().containsKey("stack"));
            }
        });
    }
}
