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

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.mqtt.v3.MqttConsumerInterceptor;
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
public class MqttConsumerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {

        @Override
        public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
        }
    };

    private MqttConsumerInterceptor mqttConsumerInterceptor;

    private Object[] arguments;

    @Before
    public void setUp() throws Exception {
        mqttConsumerInterceptor = new MqttConsumerInterceptor();
        MqttMessage message = new MqttMessage();
        message.setPayload(
            ("" + "#SW_HEADERS#sw8#1-NDJjMDIxMzdhNWYzNGY2MmE5Y2ExOTQyYzI1ZjdiZWIuMS4xNjAzNzk0NzkxOTg4MDI3Mw==-NDJjMDIxMzdhNWYzNGY2MmE5Y2ExOTQyYzI1ZjdiZWIuMS4xNjAzNzk0NzkxOTg4MDI3Mg==-0-c2FpYy1tcXR0-ZTgyN2Y0MzBkMjc5NGE3MThiYWU3MDhhMjIxZGRhMTlAMTkyLjE2OC4xMTEuMQ==-TXF0dC9za3l3YWxraW5nL2FnZW50L1Byb2R1Y2VyLzE=-dGNwOi8vc21hcml0YW4tcHJvLnNhaWNzdGFjay5jb206MTg4Mw==;sw8-correlation#;sw8-x#0;")
                .getBytes(StandardCharsets.UTF_8));
        arguments = new Object[] {
            "sw-mqtt",
            message
        };
    }

    @Test
    public void testMqttProducerInterceptor() throws Throwable {
        mqttConsumerInterceptor.beforeMethod(enhancedInstance, null, arguments, null, null);
        mqttConsumerInterceptor.afterMethod(enhancedInstance, null, arguments, null, null);
        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        assertThat(traceSegmentList.size(), is(1));
    }

}
