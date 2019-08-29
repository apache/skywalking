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

package org.apache.skywalking.apm.plugin.kafka.v1;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

/**
 * @author zhang xin
 */
public class KafkaProducerInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String OPERATE_NAME_PREFIX = "Kafka/";
    public static final String PRODUCER_OPERATE_NAME_SUFFIX = "/Producer";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

        ContextCarrier contextCarrier = new ContextCarrier();

        ProducerRecord record = (ProducerRecord)allArguments[0];
        String topicName = (String)((EnhancedInstance)record).getSkyWalkingDynamicField();
        String key = record.key() == null ? null : (String) record.key();
        AbstractSpan activeSpan = ContextManager.createExitSpan(OPERATE_NAME_PREFIX + topicName + PRODUCER_OPERATE_NAME_SUFFIX, contextCarrier, (String)objInst.getSkyWalkingDynamicField());

        Tags.MQ_BROKER.set(activeSpan, (String) objInst.getSkyWalkingDynamicField());
        Tags.MQ_TOPIC.set(activeSpan, topicName);
        Tags.MQ_QUEUE.set(activeSpan, key);
        SpanLayer.asMQ(activeSpan);
        activeSpan.setComponent(ComponentsDefine.KAFKA_PRODUCER);

        // kafkaTemplate can lead to ClassCastException
        if (allArguments[1] instanceof EnhancedInstance) {
            EnhancedInstance callbackInstance = (EnhancedInstance) allArguments[1];
            callbackInstance.setSkyWalkingDynamicField(ContextManager.capture());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
