package org.skywalking.apm.plugin.nutz.http.sync;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nutz.http.Request;
import org.nutz.http.Request.METHOD;
import org.nutz.http.Response;
import org.nutz.http.Sender;
import org.nutz.http.sender.FilePostSender;
import org.nutz.http.sender.GetSender;
import org.nutz.http.sender.PostSender;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.helper.SegmentHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;

@RunWith(org.powermock.modules.junit4.PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SenderInterceptorTest {

    @SegmentStoragePoint
    public SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    Response resp;

    SenderConstructorInterceptor constructorInterceptPoint;

    SenderSendInterceptor senderSendInterceptor;

    Method sendMethod;
    Object[] allArguments;
    Class<?>[] argumentsTypes;

    @Before
    public void setUp() throws Exception {
        ServiceManager.INSTANCE.boot();
        constructorInterceptPoint = new SenderConstructorInterceptor();
        senderSendInterceptor = new SenderSendInterceptor();
    }

    public void setupSender(Class<? extends Sender> klass) throws NoSuchMethodException, SecurityException {
        sendMethod = klass.getMethod("send");
        allArguments = new Object[0];
        argumentsTypes = new Class<?>[0];
    }

    @Test
    public void test_constructor() {
        Request request = Request.create("https://nutz.cn/yvr/list", METHOD.GET);
        constructorInterceptPoint.onConstruct(enhancedInstance, new Object[]{request});
        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField(request);
    }

    @Test
    public void test_getsender_send() throws NoSuchMethodException, SecurityException, Throwable {
        setupSender(GetSender.class);
        _sender_sender_test();
    }

    @Test
    public void test_postsender_send() throws NoSuchMethodException, SecurityException, Throwable {
        setupSender(PostSender.class);
        _sender_sender_test();
    }

    @Test
    public void test_filepostsender_send() throws NoSuchMethodException, SecurityException, Throwable {
        setupSender(FilePostSender.class);
        _sender_sender_test();
    }

    protected void _sender_sender_test() throws Throwable {
        Request request = Request.create("https://nutz.cn/yvr/list", METHOD.GET);
        constructorInterceptPoint.onConstruct(enhancedInstance, new Object[]{request});
        verify(enhancedInstance, times(1)).setSkyWalkingDynamicField(request);

        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(request);
        when(resp.getStatus()).thenReturn(200);

        senderSendInterceptor.beforeMethod(enhancedInstance, sendMethod, allArguments, argumentsTypes, null);
        senderSendInterceptor.afterMethod(enhancedInstance, sendMethod, allArguments, argumentsTypes, resp);

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).getOperationName(), is("/yvr/list"));
    }
}
