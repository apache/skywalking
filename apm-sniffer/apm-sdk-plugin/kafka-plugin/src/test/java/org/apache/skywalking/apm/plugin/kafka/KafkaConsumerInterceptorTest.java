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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.KAFKA_CONSUMER;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class KafkaConsumerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private ConsumerEnhanceRequiredInfo consumerEnhanceRequiredInfo;

    private KafkaConsumerInterceptor consumerInterceptor;

    private EnhancedInstance consumerInstance = new EnhancedInstance() {
        @Override
        public Object getSkyWalkingDynamicField() {
            return consumerEnhanceRequiredInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            consumerEnhanceRequiredInfo = (ConsumerEnhanceRequiredInfo) value;
        }
    };

    private Map<TopicPartition, List<ConsumerRecord>> messages;

    @Before
    public void setUp() {
        consumerInterceptor = new KafkaConsumerInterceptor();
        consumerEnhanceRequiredInfo = new ConsumerEnhanceRequiredInfo();

        List<String> topics = new ArrayList<String>();
        topics.add("test");
        topics.add("test-1");
        consumerEnhanceRequiredInfo.setTopics(topics);
        List<String> brokers = new ArrayList<String>();
        brokers.add("localhost:9092");
        brokers.add("localhost:19092");
        consumerEnhanceRequiredInfo.setBrokerServers(brokers);
        consumerEnhanceRequiredInfo.setGroupId("test");

        messages = new HashMap<TopicPartition, List<ConsumerRecord>>();
        TopicPartition topicPartition = new TopicPartition("test", 1);
        List<ConsumerRecord> records = new ArrayList<ConsumerRecord>();
        ConsumerRecord consumerRecord = new ConsumerRecord("test", 1, 0, "1", "1");
        consumerRecord.headers()
                      .add(
                          SW8CarrierItem.HEADER_NAME,
                          "1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA="
                              .getBytes()
                      );
        records.add(consumerRecord);
        messages.put(topicPartition, records);
    }

    @Test
    public void testConsumerWithoutMessage() throws Throwable {
        consumerInterceptor.beforeMethod(consumerInstance, null, new Object[0], new Class[0], null);
        consumerInterceptor.afterMethod(
            consumerInstance, null, new Object[0], new Class[0], new HashMap<TopicPartition, List<ConsumerRecord>>());

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertThat(traceSegments.size(), is(0));
    }

    @Test
    public void testConsumerWithMessage() throws Throwable {
        consumerInterceptor.beforeMethod(consumerInstance, null, new Object[0], new Class[0], null);
        consumerInterceptor.afterMethod(consumerInstance, null, new Object[0], new Class[0], messages);

        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertThat(traceSegments.size(), is(1));

        TraceSegment traceSegment = traceSegments.get(0);
        assertNotNull(traceSegment.getRef());
        assertTraceSegmentRef(traceSegment.getRef());

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertConsumerSpan(spans.get(0));
    }

    private void assertConsumerSpan(AbstractTracingSpan span) {
        SpanAssert.assertLayer(span, SpanLayer.MQ);
        SpanAssert.assertComponent(span, KAFKA_CONSUMER);
        SpanAssert.assertTagSize(span, 2);
        SpanAssert.assertTag(span, 0, "localhost:9092;localhost:19092");
        SpanAssert.assertTag(span, 1, "test;test-1");
    }

    private void assertTraceSegmentRef(TraceSegmentRef ref) {
        MatcherAssert.assertThat(SegmentRefHelper.getParentServiceInstance(ref), is("instance"));
        MatcherAssert.assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        MatcherAssert.assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("3.4.5"));
    }
}