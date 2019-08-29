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

package org.apache.skywalking.apm.plugin.kafka.v2;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
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

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 1. The group.id is used as a queue when producing messages
 *
 * @author stalary
 */
public class KafkaConsumerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String OPERATE_NAME_PREFIX = "Kafka/";

    private static final String CONSUMER_OPERATE_NAME_SUFFIX = "/Consumer";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        ConsumerEnhanceRequiredInfo requiredInfo = (ConsumerEnhanceRequiredInfo) objInst.getSkyWalkingDynamicField();
        requiredInfo.setStartTime(System.currentTimeMillis());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        Map<TopicPartition, List<ConsumerRecord<?, ?>>> records = (Map<TopicPartition, List<ConsumerRecord<?, ?>>>) ret;
        if (!records.isEmpty()) {
            ConsumerEnhanceRequiredInfo requiredInfo = (ConsumerEnhanceRequiredInfo) objInst.getSkyWalkingDynamicField();
            AbstractSpan activeSpan = ContextManager.createEntrySpan(OPERATE_NAME_PREFIX + requiredInfo.getTopics() + CONSUMER_OPERATE_NAME_SUFFIX, null).start(requiredInfo.getStartTime());
            activeSpan.setComponent(ComponentsDefine.KAFKA_CONSUMER);
            SpanLayer.asMQ(activeSpan);
            Tags.MQ_BROKER.set(activeSpan, requiredInfo.getBootstrapServers());
            Tags.MQ_TOPIC.set(activeSpan, requiredInfo.getTopics());
            Tags.MQ_QUEUE.set(activeSpan, requiredInfo.getGroupId());

            for (List<ConsumerRecord<?, ?>> consumerRecords : records.values()) {
                for (ConsumerRecord<?, ?> record : consumerRecords) {
                    ContextCarrier contextCarrier = new ContextCarrier();
                    CarrierItem next = contextCarrier.items();
                    while (next.hasNext()) {
                        next = next.next();
                        Iterator<Header> headerIterator = record.headers().headers(next.getHeadKey()).iterator();
                        if (headerIterator.hasNext()) {
                            next.setHeadValue(new String(headerIterator.next().value()));
                        }
                    }
                    ContextManager.extract(contextCarrier);
                }
            }
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
