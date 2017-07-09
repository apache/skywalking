package org.skywalking.apm.plugin.jedis.v2;

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

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class JedisMethodInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    private JedisMethodInterceptor interceptor;

    private Object[] allArgument;

    private Class[] argumentType;

    @Before
    public void setUp() throws Exception {
        allArgument = new Object[] {"OperationKey", "OperationValue"};
        argumentType = new Class[] {String.class, String.class};

        interceptor = new JedisMethodInterceptor();
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn("127.0.0.1:6379");
    }

    @Test
    public void testIntercept() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, "set", allArgument, argumentType, null);
        interceptor.afterMethod(enhancedInstance, "set", allArgument, argumentType, null);

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertRedisSpan(spans.get(0));
    }

    @Test
    public void testInterceptWithMultiHost() throws Throwable {
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn("127.0.0.1:6379;127.0.0.1:16379;");

        interceptor.beforeMethod(enhancedInstance, "set", allArgument, argumentType, null);
        interceptor.afterMethod(enhancedInstance, "set", allArgument, argumentType, null);

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertRedisSpan(spans.get(0));
    }

    @Test
    public void testInterceptWithException() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, "set", allArgument, argumentType, null);
        interceptor.handleMethodException(enhancedInstance, "set", allArgument, argumentType, new RuntimeException());
        interceptor.afterMethod(enhancedInstance, "set", allArgument, argumentType, null);

        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertRedisSpan(spans.get(0));

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

    private void assertRedisSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("Jedis/set"));
        assertThat(span.isExit(), is(true));
        assertThat(SpanHelper.getComponentId(span), is(7));
        List<KeyValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(0).getValue(), is("Redis"));
        assertThat(tags.get(1).getValue(), is("set OperationKey"));
        assertThat(SpanHelper.getLayer(span), is(SpanLayer.DB));
    }

}
