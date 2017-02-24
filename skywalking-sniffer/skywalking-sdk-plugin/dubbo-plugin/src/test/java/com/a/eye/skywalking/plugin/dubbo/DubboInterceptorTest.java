package com.a.eye.skywalking.plugin.dubbo;

import com.a.eye.skywalking.api.context.TracerContext;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.MethodInterceptResult;
import com.a.eye.skywalking.plugin.dubbox.BugFixActive;
import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.context.SegmentAssert;
import com.a.eye.skywalking.trace.LogData;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.a.eye.skywalking.trace.tag.Tags;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
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
        dubboInterceptor = new DubboInterceptor();
        testParam = new RequestParamForTestBelow283();
        mockTracerContextListener = new MockTracerContextListener();
        TracerContext.ListenerManager.add(mockTracerContextListener);

        mockStatic(RpcContext.class);
        mockStatic(BugFixActive.class);
        when(invoker.getUrl()).thenReturn(URL.valueOf("dubbo://127.0.0.1:20880/com.a.eye.skywalking.test.TestDubboService"));
        when(invocation.getMethodName()).thenReturn("test");
        when(invocation.getParameterTypes()).thenReturn(new Class[]{String.class});
        when(invocation.getArguments()).thenReturn(new Object[]{testParam});
        Mockito.when(RpcContext.getContext()).thenReturn(rpcContext);
        when(rpcContext.isConsumerSide()).thenReturn(true);
        when(methodInvokeContext.allArguments()).thenReturn(new Object[]{invoker, invocation});
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
                testParam.assertSelf("0", "127.0.0.1");
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
        when(rpcContext.getAttachment(DubboInterceptor.ATTACHMENT_NAME_OF_CONTEXT_DATA)).thenReturn("302017.1487666919810.624424584.17332.1.1|1|REMOTE_APP|127.0.0.1");

        dubboInterceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        dubboInterceptor.afterMethod(classInstanceContext, methodInvokeContext, result);
        assertProvider();
    }


    @Test
    public void testProviderBelow283() {
        when(rpcContext.isConsumerSide()).thenReturn(false);
        when(BugFixActive.isActive()).thenReturn(true);

        testParam.setContextData("302017.1487666919810.624424584.17332.1.1|1|REMOTE_APP|127.0.0.1");


        dubboInterceptor.beforeMethod(classInstanceContext, methodInvokeContext, methodInterceptResult);
        dubboInterceptor.afterMethod(classInstanceContext, methodInvokeContext, result);

        assertProvider();
    }


    private void assertConsumerTraceSegmentInErrorCase(TraceSegment traceSegment) {
        assertThat(traceSegment.getSpans().size(), is(1));
        assertConsumerSpan(traceSegment.getSpans().get(0));
        Span span = traceSegment.getSpans().get(0);
        assertThat(span.getLogs().size(), is(1));
        assertErrorLog(span.getLogs().get(0));
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
                assertTraceSegmentRef(traceSegment.getPrimaryRef(), expect);
            }
        });
    }

    private void assertTraceSegmentRef(TraceSegmentRef actual, TraceSegmentRef expect) {
        assertThat(actual.getSpanId(), is(expect.getSpanId()));
        assertThat(actual.getTraceSegmentId(), is(expect.getTraceSegmentId()));
    }


    private void assertProviderSpan(Span span) {
        assertCommonsAttribute(span);
        assertThat(Tags.SPAN_KIND.get(span), is(Tags.SPAN_KIND_SERVER));
    }

    private void assertConsumerSpan(Span span) {
        assertCommonsAttribute(span);
        assertThat(Tags.SPAN_KIND.get(span), is(Tags.SPAN_KIND_CLIENT));
    }

    private void assertCommonsAttribute(Span span) {
        assertThat(Tags.SPAN_LAYER.isRPCFramework(span), is(true));
        assertThat(Tags.COMPONENT.get(span), is(DubboInterceptor.DUBBO_COMPONENT));
        assertThat(Tags.URL.get(span), is("dubbo://127.0.0.1:20880/com.a.eye.skywalking.test.TestDubboService.test(String)"));
        assertThat(span.getOperationName(), is("com.a.eye.skywalking.test.TestDubboService.test(String)"));
    }

    @After
    public void tearDown() throws Exception {
        TracerContext.ListenerManager.remove(mockTracerContextListener);
    }

}