package org.skywalking.apm.plugin.dubbo;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import java.lang.reflect.Field;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.TracerContext;
import org.skywalking.apm.agent.core.plugin.interceptor.EnhancedClassInstanceContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.plugin.dubbox.BugFixActive;
import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.sniffer.mock.trace.SpanLogReader;
import org.skywalking.apm.sniffer.mock.trace.tags.StringTagReader;
import org.skywalking.apm.trace.LogData;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.TraceSegment;
import org.skywalking.apm.trace.TraceSegmentRef;
import org.skywalking.apm.trace.tag.Tags;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({RpcContext.class, BugFixActive.class})
public class DubboInterceptorTest {

    private MockTracerContextListener mockTracerContextListener;
    private DubboInterceptor dubboInterceptor;
    private RequestParamForTestBelow283 testParam;
    @Mock
    private RpcContext rpcContext;
    @Mock
    private Invoker invoker;
    @Mock
    private Invocation invocation;
    @Mock
    private EnhancedClassInstanceContext classInstanceContext;
    @Mock
    private InstanceMethodInvokeContext methodInvokeContext;
    @Mock
    private MethodInterceptResult methodInterceptResult;
    @Mock
    private Result result;

    @Before
    public void setUp() throws Exception {
        ServiceManager.INSTANCE.boot();

        dubboInterceptor = new DubboInterceptor();
        testParam = new RequestParamForTestBelow283();
        mockTracerContextListener = new MockTracerContextListener();
        TracerContext.ListenerManager.add(mockTracerContextListener);

        mockStatic(RpcContext.class);
        mockStatic(BugFixActive.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("dubbo://127.0.0.1:20880/org.skywalking.apm.test.TestDubboService"));
        when(invocation.getMethodName()).thenReturn("test");
        when(invocation.getParameterTypes()).thenReturn(new Class[] {String.class});
        when(invocation.getArguments()).thenReturn(new Object[] {testParam});
        Mockito.when(RpcContext.getContext()).thenReturn(rpcContext);
        when(rpcContext.isConsumerSide()).thenReturn(true);
        when(methodInvokeContext.allArguments()).thenReturn(new Object[] {invoker, invocation});
        Config.Agent.APPLICATION_CODE = "DubboTestCases-APP";
    }

    @Test
    public void testConsumerBelow283() {
        when(BugFixActive.isActive()).thenReturn(true);

        dubboInterceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        dubboInterceptor.afterMethod(classInstanceContext, methodInvokeContext, result);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertConsumerSpan(traceSegment.getSpans().get(0));

                assertNotNull(testParam.getTraceContext());
                ContextCarrier contextCarrier = new ContextCarrier();
                contextCarrier.deserialize(testParam.getTraceContext());
                Assert.assertTrue(contextCarrier.isValid());
            }
        });
    }

    @Test
    public void testConsumerWithAttachment() {
        dubboInterceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        dubboInterceptor.afterMethod(classInstanceContext, methodInvokeContext, result);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertConsumerSpan(traceSegment.getSpans().get(0));
            }
        });
    }

    @Test
    public void testConsumerWithException() {
        dubboInterceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        dubboInterceptor.handleMethodException(new RuntimeException(), classInstanceContext, methodInvokeContext);
        dubboInterceptor.afterMethod(classInstanceContext, methodInvokeContext, result);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertConsumerTraceSegmentInErrorCase(traceSegment);
            }
        });
    }

    @Test
    public void testConsumerWithResultHasException() {
        when(result.getException()).thenReturn(new RuntimeException());

        dubboInterceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        dubboInterceptor.afterMethod(classInstanceContext, methodInvokeContext, result);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertConsumerTraceSegmentInErrorCase(traceSegment);
            }
        });
    }

    @Test
    public void testProviderWithAttachment() {
        when(rpcContext.isConsumerSide()).thenReturn(false);
        when(rpcContext.getAttachment(DubboInterceptor.ATTACHMENT_NAME_OF_CONTEXT_DATA)).thenReturn("302017.1487666919810.624424584.17332.1.1|1|REMOTE_APP|127.0.0.1|Trace.globalId.123|1");

        dubboInterceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        dubboInterceptor.afterMethod(classInstanceContext, methodInvokeContext, result);
        assertProvider();
    }

    @Test
    public void testProviderBelow283() {
        when(rpcContext.isConsumerSide()).thenReturn(false);
        when(BugFixActive.isActive()).thenReturn(true);

        testParam.setTraceContext("302017.1487666919810.624424584.17332.1.1|1|REMOTE_APP|127.0.0.1|Trace.globalId.123|1");

        dubboInterceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        dubboInterceptor.afterMethod(classInstanceContext, methodInvokeContext, result);

        assertProvider();
    }

    private void assertConsumerTraceSegmentInErrorCase(
        TraceSegment traceSegment) {
        assertThat(traceSegment.getSpans().size(), is(1));
        assertConsumerSpan(traceSegment.getSpans().get(0));
        Span span = traceSegment.getSpans().get(0);
        assertThat(SpanLogReader.getLogs(span).size(), is(1));
        assertErrorLog(SpanLogReader.getLogs(span).get(0));
    }

    private void assertErrorLog(LogData logData) {
        assertThat(logData.getFields().size(), is(4));
        assertThat(logData.getFields().get("event"), CoreMatchers.<Object>is("error"));
        assertThat(logData.getFields().get("error.kind"), CoreMatchers.<Object>is(RuntimeException.class.getName()));
        assertNull(logData.getFields().get("message"));
    }

    private void assertProvider() {
        final TraceSegmentRef expect = new TraceSegmentRef();
        expect.setSpanId(1);
        expect.setTraceSegmentId("302017.1487666919810.624424584.17332.1.1");

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                assertProviderSpan(traceSegment.getSpans().get(0));
                assertTraceSegmentRef(traceSegment.getRefs().get(0), expect);
            }
        });
    }

    private void assertTraceSegmentRef(TraceSegmentRef actual, TraceSegmentRef expect) {
        assertThat(actual.getSpanId(), is(expect.getSpanId()));
        assertThat(actual.getTraceSegmentId(), is(expect.getTraceSegmentId()));
    }

    private void assertProviderSpan(Span span) {
        assertCommonsAttribute(span);
        assertThat(StringTagReader.get(span, Tags.SPAN_KIND), is(Tags.SPAN_KIND_SERVER));
    }

    private void assertConsumerSpan(Span span) {
        assertCommonsAttribute(span);
        assertThat(StringTagReader.get(span, Tags.SPAN_KIND), is(Tags.SPAN_KIND_CLIENT));
    }

    private void assertCommonsAttribute(Span span) {
        assertThat(StringTagReader.get(span, Tags.SPAN_LAYER.SPAN_LAYER_TAG), is("rpc"));
        assertThat(StringTagReader.get(span, Tags.COMPONENT), is(DubboInterceptor.DUBBO_COMPONENT));
        assertThat(StringTagReader.get(span, Tags.URL), is("dubbo://127.0.0.1:20880/org.skywalking.apm.test.TestDubboService.test(String)"));
        assertThat(span.getOperationName(), is("org.skywalking.apm.test.TestDubboService.test(String)"));
    }

    @After
    public void tearDown() throws Exception {
        TracerContext.ListenerManager.remove(mockTracerContextListener);
    }
}
