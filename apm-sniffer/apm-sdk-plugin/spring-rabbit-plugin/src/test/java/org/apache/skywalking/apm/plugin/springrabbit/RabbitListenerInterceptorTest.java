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
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.hamcrest.CoreMatchers.is;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author jjlu521016@gmail.com
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RabbitListenerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private RabbitListenerInterceptor rabbitListenerInterceptor;

    private Object[] arguments;

    @Before
    public void setUp() throws Exception {
        rabbitListenerInterceptor = new RabbitListenerInterceptor();
        arguments = buildArgs();
    }

    @Test
    public void TestRabbitMQConsumerInterceptor() throws Throwable {
        rabbitListenerInterceptor.beforeMethod(null, null, arguments, null, null);
        rabbitListenerInterceptor.afterMethod(null, null, arguments, null, null);
        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
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
        messageProperties.setHeader("sw6", "1-NTguOTEuMTU0NjE4MTM2NDQ4NTAwMDE=-NTcuNTYuMTU0NjE4MTM2NDYzNTAwMDA=-1-57-58-IzEyNy4wLjAuMTo1Njcy-Iy9oYWhh-Iy9oYWhh");
        return new Object[] {channel, new Message(body, messageProperties)};
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