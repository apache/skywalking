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

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.mqtt.v3.MqttEnhanceRequiredInfo;
import org.apache.skywalking.apm.plugin.mqtt.v3.MqttProducerConnectInterceptor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by yuanguohua on 2020/10/16 10:18
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MqttProducerConnectInterceptorTest {

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

    private MqttProducerConnectInterceptor mqttProducerConnectInterceptor;

    private Object[] arguments;

    @Before
    public void setUp() throws Exception {
        mqttProducerConnectInterceptor = new MqttProducerConnectInterceptor();
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setServerURIs(new String[]{"tcp://127.0.0.1:1883"});
        arguments = new Object[] {mqttConnectOptions};
    }

    @Test
    public void testMqttProducerConnectInterceptor() throws Throwable {
        mqttProducerConnectInterceptor.beforeMethod(enhancedInstance, null, arguments, null, null);
        mqttProducerConnectInterceptor.afterMethod(enhancedInstance, null, arguments, null, null);
        assertThat(((MqttEnhanceRequiredInfo) enhancedInstance.getSkyWalkingDynamicField()).getBrokerServers(), Is.is("tcp://127.0.0.1:1883"));
    }


}
