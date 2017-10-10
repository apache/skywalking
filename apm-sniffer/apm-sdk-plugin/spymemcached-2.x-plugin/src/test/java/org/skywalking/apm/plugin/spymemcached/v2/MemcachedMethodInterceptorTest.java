package org.skywalking.apm.plugin.spymemcached.v2;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.util.KeyValuePair;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.helper.SegmentHelper;
import org.skywalking.apm.agent.test.helper.SpanHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import net.spy.memcached.MemcachedClient;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MemcachedMethodInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @Mock
    private EnhancedInstance enhancedInstance;
    private MemcachedMethodInterceptor interceptor;

    private Object[] allArgument;
    private Class[] argumentType;
    
    @Before
    public void setUp() throws Exception {
        allArgument = new Object[] {"OperationKey", "OperationValue"};
        argumentType = new Class[] {String.class, String.class};

        interceptor = new MemcachedMethodInterceptor();
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn("127.0.0.1:11211");
    }

    @Test
    public void testIntercept() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, getMockSetMethod(), allArgument, argumentType, null);
        interceptor.afterMethod(enhancedInstance, getMockGetMethod(), allArgument, argumentType, null);

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertMemcacheSpan(spans.get(0));
    }

    @Test
    public void testInterceptWithException() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, getMockSetMethod(), allArgument, argumentType, null);
        interceptor.handleMethodException(enhancedInstance, getMockSetMethod(), allArgument, argumentType, new RuntimeException());
        interceptor.afterMethod(enhancedInstance, getMockSetMethod(), allArgument, argumentType, null);

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertMemcacheSpan(spans.get(0));

        assertLogData(SpanHelper.getLogs(spans.get(0)));
    }
    
    private void assertLogData(List<LogDataEntity> logDataEntities) {
        assertThat(logDataEntities.size(), is(1));
        LogDataEntity logData = logDataEntities.get(0);
        Assert.assertThat(logData.getLogs().size(), is(4));
        Assert.assertThat(logData.getLogs().get(0).getValue(), CoreMatchers.<Object>is("error"));
        Assert.assertThat(logData.getLogs().get(1).getValue(), CoreMatchers.<Object>is(RuntimeException.class.getName()));
        Assert.assertNull(logData.getLogs().get(2).getValue());
        assertNotNull(logData.getLogs().get(3).getValue());
    }

    private void assertMemcacheSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("SpyMemcached/set"));
        assertThat(span.isExit(), is(true));
        assertThat(SpanHelper.getComponentId(span), is(20));
        List<KeyValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(0).getValue(), is("Memcache"));
        assertThat(tags.get(1).getValue(), is("set OperationKey"));
        assertThat(SpanHelper.getLayer(span), is(SpanLayer.DB));
    }

    private Method getMockSetMethod() {
        try {
            return MemcachedClient.class.getMethod("set", String.class, int.class, Object.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }
  
    private Method getMockGetMethod() {
        try {
            return MemcachedClient.class.getMethod("get", String.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }
}
