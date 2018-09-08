package org.apache.skywalking.apm.plugin.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
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

import static org.hamcrest.CoreMatchers.is;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RabbitMQConsumerInterceptorTest {

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

    private  RabbitMQConsumerInterceptor rabbitMQConsumerInterceptor;

    private Object[] arguments;

    @Before
    public void setUp() throws Exception {
        rabbitMQConsumerInterceptor = new RabbitMQConsumerInterceptor();
        Envelope envelope = new Envelope(1111,false,"","rabbitmq-test");
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("sw3","");
        AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder();
        arguments = new Object[]  {0,0,envelope,propsBuilder.headers(headers).build()};
    }

    @Test
    public void TestActiveMQConsumerAndProducerConstructorInterceptor() throws Throwable {
        rabbitMQConsumerInterceptor.beforeMethod(enhancedInstance,null,arguments,null,null);
        rabbitMQConsumerInterceptor.afterMethod(enhancedInstance,null,arguments,null,null);
        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }
}
