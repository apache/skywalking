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

    @Before
    public void setUp() throws Exception {
        rabbitMQConsumerInterceptor = new RabbitMQConsumerInterceptor();
    }

    @Test
    public void TestRabbitMQConsumerInterceptor() throws Throwable {
        Envelope envelope = new Envelope(1111,false,"","rabbitmq-test");
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("sw6","1-MS4xLjE1NDM5NzU1OTEwMTQwMDAx-MS4xLjE1NDM5NzU1OTA5OTcwMDAw-0-1-1-IzEyNy4wLjAuMTo1Mjcy-I1JhYmJpdE1RL1RvcGljL1F1ZXVlL3JhYmJpdG1xLXRlc3QvUHJvZHVjZXI=-I1JhYmJpdE1RL1RvcGljL1F1ZXVlL3JhYmJpdG1xLXRlc3QvUHJvZHVjZXI=");
        AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder();
        Object[] arguments = new Object[]  {0,envelope,propsBuilder.headers(headers).build()};

        rabbitMQConsumerInterceptor.beforeMethod(enhancedInstance,null,arguments,null,null);
        rabbitMQConsumerInterceptor.afterMethod(enhancedInstance,null,arguments,null,null);
        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

    @Test
    public void testRabbitMQConsumerInterceptorWithNilHeaders() throws Throwable {
        Envelope envelope = new Envelope(1111,false,"","rabbitmq-test");
        AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder();
        Object[] arguments = new Object[]  {0,envelope,propsBuilder.headers(null).build()};

        rabbitMQConsumerInterceptor.beforeMethod(enhancedInstance,null,arguments,null,null);
        rabbitMQConsumerInterceptor.afterMethod(enhancedInstance,null,arguments,null,null);
        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }

    @Test
    public void testRabbitMQConsumerInterceptorWithEmptyHeaders() throws Throwable {
        Envelope envelope = new Envelope(1111,false,"","rabbitmq-test");
        Map<String, Object> headers = new HashMap<String, Object>();
        AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder();
        Object[] arguments = new Object[]  {0,envelope,propsBuilder.headers(headers).build()};

        rabbitMQConsumerInterceptor.beforeMethod(enhancedInstance,null,arguments,null,null);
        rabbitMQConsumerInterceptor.afterMethod(enhancedInstance,null,arguments,null,null);
        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegments.size(), is(1));
    }
}
