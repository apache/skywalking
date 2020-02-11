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

import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
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

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.RABBITMQ_PRODUCER;
import static org.hamcrest.CoreMatchers.is;
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

    private RabbitMQProducerInterceptor rabbitMQProducerInterceptor;

    private Object[] arguments;

    @Before
    public void setUp() throws Exception {
        rabbitMQProducerInterceptor = new RabbitMQProducerInterceptor();
        arguments = new Object[] {
            "",
            "rabbitmq-test",
            0,
            0,
            null
        };
    }

    @Test
    public void TestRabbitMQProducerInterceptor() throws Throwable {
        rabbitMQProducerInterceptor.beforeMethod(enhancedInstance, null, arguments, null, null);
        rabbitMQProducerInterceptor.afterMethod(enhancedInstance, null, arguments, null, null);

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
