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

package org.apache.skywalking.apm.plugin.mqtt.v3;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.util.StringUtil;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttPublish;

public class MqttProducerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String OPERATE_NAME_PREFIX = "Mqtt/";

    private static final String OPERATE_NAME = "/Producer/";

    protected static final String MESSAGE_SUFFIX = "#SW_HEADERS#";

    @Override
    public void beforeMethod(EnhancedInstance enhancedInstance, Method method, Object[] objects, Class<?>[] classes,
                             MethodInterceptResult methodInterceptResult) throws Throwable {
        String topic;
        int qos;
        MqttPublish mqttPublish;
        if (objects[0] instanceof MqttPublish) {
            mqttPublish = (MqttPublish) objects[0];
            topic = mqttPublish.getTopicName();
            qos = mqttPublish.getMessage().getQos();
        } else {
            return;
        }
        String operationName = OPERATE_NAME_PREFIX + topic + OPERATE_NAME + qos;
        ContextCarrier contextCarrier = new ContextCarrier();
        MqttEnhanceRequiredInfo requiredInfo = (MqttEnhanceRequiredInfo) enhancedInstance.getSkyWalkingDynamicField();
        AbstractSpan activeSpan = ContextManager.createExitSpan(
            operationName, contextCarrier, requiredInfo.getBrokerServers());
        Tags.MQ_BROKER.set(activeSpan, requiredInfo.getBrokerServers());
        Tags.MQ_TOPIC.set(activeSpan, topic);
        activeSpan.setLayer(SpanLayer.MQ);
        activeSpan.setComponent(ComponentsDefine.MQTT_PRODUCER);
        CarrierItem next = contextCarrier.items();
        StringBuilder stringBuilder = new StringBuilder();
        while (next.hasNext()) {
            next = next.next();
            stringBuilder.append(next.getHeadKey()).append("#").append(next.getHeadValue()).append(";");
        }
        if (StringUtil.isNotEmpty(stringBuilder.toString())) {
            String sourcePayload = new String(mqttPublish.getMessage().getPayload(), StandardCharsets.UTF_8);
            String newPayload = sourcePayload + MESSAGE_SUFFIX + stringBuilder.toString();
            mqttPublish.getMessage().setPayload(newPayload.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance enhancedInstance, Method method, Object[] objects, Class<?>[] classes,
                              Object o) throws Throwable {
        if (ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return o;
    }

    @Override
    public void handleMethodException(EnhancedInstance enhancedInstance, Method method, Object[] objects,
                                      Class<?>[] classes, Throwable throwable) {
        ContextManager.activeSpan().log(throwable);
    }
}
