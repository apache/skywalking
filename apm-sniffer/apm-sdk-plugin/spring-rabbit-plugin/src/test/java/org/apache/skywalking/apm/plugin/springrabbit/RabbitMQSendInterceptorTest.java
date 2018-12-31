/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.skywalking.apm.plugin.springrabbit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.RABBITMQ_PRODUCER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author jjlu521016@gmail.com
 */

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RabbitMQSendInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private RabbitMQSendInterceptor rabbitMQSendInterceptor;

    private Object[] arguments;

    @Before
    public void setUp() throws Exception {
        rabbitMQSendInterceptor = new RabbitMQSendInterceptor();

        arguments = buildArgs();
    }

    @Test
    public void TestRabbitMQProducerInterceptor() throws Throwable {
        rabbitMQSendInterceptor.beforeMethod(null, null, arguments, null, null);
        rabbitMQSendInterceptor.afterMethod(null, null, arguments, null, null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        assertThat(traceSegmentList.size(), is(1));

        TraceSegment segment = traceSegmentList.get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertThat(spans.size(), is(1));
        assertRabbitMQSpan(spans.get(0));
    }

    private void assertRabbitMQSpan(AbstractTracingSpan span) {
        SpanAssert.assertTag(span, 0, "127.0.0.1:5672");
        SpanAssert.assertTag(span, 1, "spring-rabbit-test");
        SpanAssert.assertComponent(span, RABBITMQ_PRODUCER);
        SpanAssert.assertLayer(span, SpanLayer.MQ);
        assertThat(span.getOperationName(), is("RabbitMQ/Topic/topic-1/Queue/spring-rabbit-test/Producer"));
    }

    public Object[] buildArgs() {

        Connection connection = mock(Connection.class);
        when(connection.getId()).thenReturn("connection-1");
        when(connection.getAddress()).thenReturn(getAddress());
        when(connection.getPort()).thenReturn(5672);
        Channel channel = mock(Channel.class);
        when(channel.getConnection()).thenReturn(connection);
        when(channel.getChannelNumber()).thenReturn(1);

        byte[] body = "Test message".getBytes();
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setHeader("test", "myheader1");
        return new Object[] {channel, "topic-1", "spring-rabbit-test", new Message(body, messageProperties), true, null};
    }

    public InetAddress getAddress() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
    }
}