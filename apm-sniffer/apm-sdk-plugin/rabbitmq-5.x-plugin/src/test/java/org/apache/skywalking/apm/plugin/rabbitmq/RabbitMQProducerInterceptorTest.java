package org.apache.skywalking.apm.plugin.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.ACTIVEMQ_PRODUCER;
import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.RABBITMQ_PRODUCER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RabbitMQProducerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        @Override
        public Object getSkyWalkingDynamicField() {
            return "127.0.0.1:5272";
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
        }
    };

    private  RabbitMQProducerInterceptor rabbitMQProducerInterceptor;

    private Object[] arguments;

    @Before
    public void setUp() throws Exception {
        rabbitMQProducerInterceptor = new RabbitMQProducerInterceptor();
        arguments = new Object[]  {"","rabbitmq-test",0,0,null};
    }

    @Test
    public void TestActiveMQConsumerAndProducerConstructorInterceptor() throws Throwable {
        rabbitMQProducerInterceptor.beforeMethod(enhancedInstance,null,arguments,null,null);
        rabbitMQProducerInterceptor.afterMethod(enhancedInstance,null,arguments,null,null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        assertThat(traceSegmentList.size(), is(1));

        TraceSegment segment = traceSegmentList.get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertThat(spans.size(), is(1));
        assertRabbitMQSpan(spans.get(0));
    }

    private void assertRabbitMQSpan(AbstractTracingSpan span) {
        SpanAssert.assertTag(span, 0, "127.0.0.1:5272");
        SpanAssert.assertTag(span, 1, "rabbitmq-test");
        SpanAssert.assertComponent(span, RABBITMQ_PRODUCER);
        SpanAssert.assertLayer(span, SpanLayer.MQ);
        assertThat(span.getOperationName(), is("RabbitMQ/Topic/Queue/rabbitmq-test/Producer"));
    }
}
