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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.util.StringUtil;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttConsumerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String OPERATE_NAME_PREFIX = "Mqtt/";

    private static final String OPERATE_NAME = "/Consumer";

    @Override
    public void beforeMethod(EnhancedInstance enhancedInstance, Method method, Object[] objects, Class<?>[] classes,
                             MethodInterceptResult methodInterceptResult) throws Throwable {
        String topic = (String) objects[0];
        AbstractSpan activeSpan = ContextManager.createEntrySpan(OPERATE_NAME_PREFIX + topic + OPERATE_NAME, null);
        activeSpan.setLayer(SpanLayer.MQ);
        activeSpan.setComponent(ComponentsDefine.MQTT_CONSUMER);
        Tags.MQ_TOPIC.set(activeSpan, topic);

        MqttMessage message = (MqttMessage) objects[1];
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        List<String> payloads = Arrays.stream(payload.split(MqttProducerInterceptor.MESSAGE_SUFFIX))
                                      .collect(Collectors.toList());
        if (CollectionUtil.isEmpty(payloads) || payloads.size() == 1) {
            return;
        }
        message.setPayload(payloads.get(0).getBytes(StandardCharsets.UTF_8));
        Map<String, String> swHeaders = new HashMap<>();
        parseSWHeaders(payloads.get(1), swHeaders);
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            String propertyValue = swHeaders.get(next.getHeadKey());
            if (StringUtil.isNotEmpty(propertyValue)) {
                next.setHeadValue(propertyValue);
            }
        }
        ContextManager.extract(contextCarrier);
    }

    @Override
    public Object afterMethod(EnhancedInstance enhancedInstance, Method method, Object[] objects, Class<?>[] classes,
                              Object o) throws Throwable {
        ContextManager.stopSpan();
        return o;
    }

    @Override
    public void handleMethodException(EnhancedInstance enhancedInstance, Method method, Object[] objects,
                                      Class<?>[] classes, Throwable throwable) {
        ContextManager.activeSpan().errorOccurred().log(throwable);
    }

    private void parseSWHeaders(String payload, Map<String, String> swHeaders) {
        List<String> keyValue = Arrays.stream(payload.split(";"))
                                      .filter(StringUtil::isNotEmpty)
                                      .collect(Collectors.toList());
        keyValue.forEach(kv -> {
            List<String> kvs = Arrays.stream(kv.split("#"))
                                     .filter(StringUtil::isNotEmpty)
                                     .collect(Collectors.toList());
            if (!CollectionUtil.isEmpty(kvs) && kvs.size() == 2) {
                swHeaders.put(kvs.get(0), kvs.get(1));
            }
        });
    }

}
