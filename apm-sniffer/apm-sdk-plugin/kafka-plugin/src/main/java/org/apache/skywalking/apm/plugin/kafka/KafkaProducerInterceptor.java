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

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public class KafkaProducerInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String OPERATE_NAME_PREFIX = "Kafka/";
    public static final String PRODUCER_OPERATE_NAME_SUFFIX = "/Producer";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {

        ContextCarrier contextCarrier = new ContextCarrier();

        ProducerRecord record = (ProducerRecord) allArguments[0];
        String topicName = record.topic();
        AbstractSpan activeSpan = ContextManager.createExitSpan(OPERATE_NAME_PREFIX + topicName + PRODUCER_OPERATE_NAME_SUFFIX, contextCarrier, (String) objInst
                .getSkyWalkingDynamicField());

        Tags.MQ_BROKER.set(activeSpan, (String) objInst.getSkyWalkingDynamicField());
        Tags.MQ_TOPIC.set(activeSpan, topicName);
        contextCarrier.extensionInjector().injectSendingTimestamp();
        SpanLayer.asMQ(activeSpan);
        activeSpan.setComponent(ComponentsDefine.KAFKA_PRODUCER);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            record.headers().add(next.getHeadKey(), next.getHeadValue().getBytes(StandardCharsets.UTF_8));
        }

        //when use lambda expression, not to generate inner class,
        //    and not to trigger kafka CallBack class define, so allArguments[1] can't to cast EnhancedInstance
        Object shouldCallbackInstance = allArguments[1];
        if (null != shouldCallbackInstance) {
            if (shouldCallbackInstance instanceof EnhancedInstance) {
                EnhancedInstance callbackInstance = (EnhancedInstance) shouldCallbackInstance;
                ContextSnapshot snapshot = ContextManager.capture();
                if (null != snapshot) {
                    CallbackCache cache = new CallbackCache();
                    cache.setSnapshot(snapshot);
                    callbackInstance.setSkyWalkingDynamicField(cache);
                }
            } else if (shouldCallbackInstance instanceof Callback) {
                Callback callback = (Callback) shouldCallbackInstance;
                ContextSnapshot snapshot = ContextManager.capture();
                if (null != snapshot) {
                    CallbackCache cache = new CallbackCache();
                    cache.setSnapshot(snapshot);
                    cache.setCallback(callback);
                    allArguments[1] = new CallbackAdapterInterceptor(cache);
                }
            }
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {

    }
}
