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

package org.apache.skywalking.apm.plugin.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.List;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.KAFKA_PRODUCER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class KafkaProducerInterceptorTest {
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private KafkaProducerInterceptor producerInterceptor;

    private Object[] arguments;
    private Class[] argumentType;

    private EnhancedInstance kafkaProducerInstance = new EnhancedInstance() {
        @Override public Object getSkyWalkingDynamicField() {
            return "localhost:9092";
        }

        @Override public void setSkyWalkingDynamicField(Object value) {

        }
    };

    private EnhancedInstance messageInstance = new MockProducerMessage();

    private class MockProducerMessage extends ProducerRecord implements EnhancedInstance {

        public MockProducerMessage() {
            super("test", "key1", "");
        }

        @Override public Object getSkyWalkingDynamicField() {
            return "test";
        }

        @Override public void setSkyWalkingDynamicField(Object value) {

        }
    }

    @Before
    public void setUp() {
        producerInterceptor = new KafkaProducerInterceptor();

        arguments = new Object[] {messageInstance, null};
        argumentType = new Class[] {ProducerRecord.class};
    }

    @Test
    public void testSendMessage() throws Throwable {
        producerInterceptor.beforeMethod(kafkaProducerInstance, null, arguments, argumentType, null);
        producerInterceptor.afterMethod(kafkaProducerInstance, null, arguments, argumentType, null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        assertThat(traceSegmentList.size(), is(1));

        TraceSegment segment = traceSegmentList.get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertThat(spans.size(), is(1));

        assertMessageSpan(spans.get(0));
    }

    private void assertMessageSpan(AbstractTracingSpan span) {
        SpanAssert.assertTag(span, 0, "localhost:9092");
        SpanAssert.assertTag(span, 1, "test");
        SpanAssert.assertComponent(span, KAFKA_PRODUCER);
        SpanAssert.assertLayer(span, SpanLayer.MQ);
        assertThat(span.getOperationName(), is("Kafka/test/Producer"));
    }
}