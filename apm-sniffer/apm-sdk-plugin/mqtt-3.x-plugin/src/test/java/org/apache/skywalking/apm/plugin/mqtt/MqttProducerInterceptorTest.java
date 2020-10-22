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

package org.apache.skywalking.apm.plugin.mqtt;

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
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.mqtt.v3.MqttEnhanceRequiredInfo;
import org.apache.skywalking.apm.plugin.mqtt.v3.MqttProducerInterceptor;
import org.apache.skywalking.apm.util.StringUtil;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MqttProducerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {

        private MqttEnhanceRequiredInfo mqttEnhanceRequiredInfo;

        @Override
        public Object getSkyWalkingDynamicField() {
            return this.mqttEnhanceRequiredInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.mqttEnhanceRequiredInfo = (MqttEnhanceRequiredInfo) value;
        }
    };

    private MqttProducerInterceptor mqttProducerInterceptor;

    private Object[] arguments;

    @Before
    public void setUp() throws Exception {
        mqttProducerInterceptor = new MqttProducerInterceptor();
        MqttEnhanceRequiredInfo mqttEnhanceRequiredInfo = new MqttEnhanceRequiredInfo();

        mqttEnhanceRequiredInfo.setBrokerServers(StringUtil.join(';', new String[] {"tcp://127.0.0.1:1883"}));
        enhancedInstance.setSkyWalkingDynamicField(mqttEnhanceRequiredInfo);
        MqttMessage message = new MqttMessage();
        message.setQos(0);
        arguments = new Object[] {
            "sw-mqtt",
            message
        };
    }

    @Test
    public void testMqttProducerInterceptor() throws Throwable {
        mqttProducerInterceptor.beforeMethod(enhancedInstance, null, arguments, null, null);
        mqttProducerInterceptor.afterMethod(enhancedInstance, null, arguments, null, null);
        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        assertThat(traceSegmentList.size(), is(1));

        TraceSegment segment = traceSegmentList.get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertThat(spans.size(), is(1));
        assertMqttSpan(spans.get(0));
    }

    private void assertMqttSpan(AbstractTracingSpan span) {
        SpanAssert.assertTag(span, 0, "tcp://127.0.0.1:1883");
        SpanAssert.assertTag(span, 1, "sw-mqtt");
        SpanAssert.assertComponent(span, ComponentsDefine.MQTT_PRODUCER);
        SpanAssert.assertLayer(span, SpanLayer.MQ);
        assertThat(span.getOperationName(), is("Mqtt/sw-mqtt/Producer/0"));
    }

}
