package org.skywalking.apm.plugin.motan;

import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import java.util.HashMap;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.context.SW3CarrierItem;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.helper.SegmentHelper;
import org.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.skywalking.apm.agent.test.helper.SpanHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertException;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertLayer;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertLogSize;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MotanProviderInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private MotanProviderInterceptor invokeInterceptor;
    @Mock
    private Response response;
    @Mock
    private Request request;

    private URL url;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Object[] arguments;
    private Class[] argumentType;

    @Before
    public void setUp() {
        invokeInterceptor = new MotanProviderInterceptor();
        url = URL.valueOf("motan://127.0.0.1:34000/org.skywalking.apm.test.TestService");

        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(url);
        arguments = new Object[] {request};
        argumentType = new Class[] {request.getClass()};
        when(request.getMethodName()).thenReturn("test");
        when(request.getInterfaceName()).thenReturn("org.skywalking.apm.test.TestService");
        when(request.getParamtersDesc()).thenReturn("java.lang.String, java.lang.Object");
    }

    @Test
    public void testInvokerWithoutRefSegment() throws Throwable {
        invokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, null);
        invokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, response);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMotanProviderSpan(spans.get(0));
        assertTrue(traceSegment.getRefs() == null);

    }

    @Test
    public void testInvokerWithRefSegment() throws Throwable {
        HashMap attachments = new HashMap();
        attachments.put(SW3CarrierItem.HEADER_NAME, "1.123.456|3|1|1|#192.168.1.8:18002|#/portal/|#/testEntrySpan|#AQA*#AQA*Et0We0tQNQA*");
        when(request.getAttachments()).thenReturn(attachments);

        invokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, null);
        invokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, response);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMotanProviderSpan(spans.get(0));
        assertRefSegment(traceSegment.getRefs().get(0));
    }

    @Test
    public void testResponseWithException() throws Throwable {
        when(response.getException()).thenReturn(new RuntimeException());

        invokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, null);
        invokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, response);

        assertTraceSegmentWhenOccurException();
    }

    @Test
    public void testOccurException() throws Throwable {

        invokeInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, null);
        invokeInterceptor.handleMethodException(enhancedInstance, null, arguments, argumentType, new RuntimeException());
        invokeInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, response);

        assertTraceSegmentWhenOccurException();
    }

    private void assertTraceSegmentWhenOccurException() {
        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMotanProviderSpan(spans.get(0));
        assertLogSize(spans.get(0), 1);
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertRefSegment(TraceSegmentRef primaryRef) {
        assertThat(SegmentRefHelper.getTraceSegmentId(primaryRef).toString(), is("1.123.456"));
        assertThat(SegmentRefHelper.getSpanId(primaryRef), is(3));
        assertThat(SegmentRefHelper.getEntryApplicationInstanceId(primaryRef), is(1));
        assertThat(SegmentRefHelper.getPeerHost(primaryRef), is("192.168.1.8:18002"));
    }

    private void assertMotanProviderSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("org.skywalking.apm.test.TestService.test(java.lang.String, java.lang.Object)"));
        assertComponent(span, ComponentsDefine.MOTAN);
        assertThat(span.isEntry(), is(true));
        assertLayer(span, SpanLayer.RPC_FRAMEWORK);
    }

}
