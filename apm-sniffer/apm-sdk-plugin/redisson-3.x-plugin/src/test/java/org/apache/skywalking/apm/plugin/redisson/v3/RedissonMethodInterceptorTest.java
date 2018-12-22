package org.apache.skywalking.apm.plugin.redisson.v3;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommands;

import java.lang.reflect.Method;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author zhaoyuguang
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RedissonMethodInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private MockInstance mockInstance;

    private RedissonMethodInterceptor interceptor;

    private Object[] arguments;

    private Class[] argumentTypes;

    private class MockInstance extends RedisConnection implements EnhancedInstance {

        public MockInstance() throws IllegalAccessException, InstantiationException {
            super(null);
        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Before
    public void setUp() throws Exception {
        interceptor = new RedissonMethodInterceptor();
        arguments = new Object[]{new CommandData(null, null, RedisCommands.SET, null)};
        argumentTypes = new Class[]{CommandData.class};
        when(mockInstance.getRedisClient()).thenReturn(null);
    }

    @Test
    public void testIntercept() throws Throwable {
        interceptor.beforeMethod(mockInstance, getExecuteMethod(), arguments, argumentTypes, null);
        interceptor.afterMethod(mockInstance, getExecuteMethod(), arguments, argumentTypes, null);
        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertRedissonSpan(spans.get(0));
    }

    @Test
    public void testInterceptWithException() throws Throwable {
        interceptor.beforeMethod(mockInstance, getExecuteMethod(), arguments, argumentTypes, null);
        interceptor.handleMethodException(mockInstance, getExecuteMethod(), arguments, argumentTypes, new RuntimeException());
        interceptor.afterMethod(mockInstance, getExecuteMethod(), arguments, argumentTypes, null);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertRedissonSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertRedissonSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("Redisson/SET"));
        assertThat(SpanHelper.getComponentId(span), is(ComponentsDefine.REDISSON.getId()));
        assertThat(span.isExit(), is(true));
        assertThat(SpanHelper.getLayer(span), CoreMatchers.is(SpanLayer.CACHE));
    }

    private Method getExecuteMethod() {
        try {
            return RedisConnection.class.getMethod("send");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

}
