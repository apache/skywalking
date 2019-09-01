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

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

/**
 * @author  zhang xin, stalary
 */
public class KafkaConsumerInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String OPERATE_NAME_PREFIX = "Kafka/";
    public static final String CONSUMER_OPERATE_NAME = "/Consumer/";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        ConsumerEnhanceRequiredInfo requiredInfo = (ConsumerEnhanceRequiredInfo)objInst.getSkyWalkingDynamicField();
        requiredInfo.setStartTime(System.currentTimeMillis());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        Map<TopicPartition, List<ConsumerRecord<?, ?>>> records = (Map<TopicPartition, List<ConsumerRecord<?, ?>>>)ret;
        //
        // The entry span will only be created when the consumer received at least one message.
        //
        if (records.size() > 0) {
            ConsumerEnhanceRequiredInfo requiredInfo = (ConsumerEnhanceRequiredInfo)objInst.getSkyWalkingDynamicField();
            AbstractSpan activeSpan = ContextManager.createEntrySpan(OPERATE_NAME_PREFIX + requiredInfo.getTopics() + CONSUMER_OPERATE_NAME + requiredInfo.getGroupId(), null).start(requiredInfo.getStartTime());

            activeSpan.setComponent(ComponentsDefine.KAFKA_CONSUMER);
            SpanLayer.asMQ(activeSpan);
            Tags.MQ_BROKER.set(activeSpan, requiredInfo.getBrokerServers());
            Tags.MQ_TOPIC.set(activeSpan, requiredInfo.getTopics());

            for (List<ConsumerRecord<?, ?>> consumerRecords : records.values()) {
                for (ConsumerRecord<?, ?> record : consumerRecords) {
                    ContextCarrier contextCarrier = new ContextCarrier();

                    CarrierItem next = contextCarrier.items();
                    while (next.hasNext()) {
                        next = next.next();
                        Iterator<Header> iterator = record.headers().headers(next.getHeadKey()).iterator();
                        if (iterator.hasNext()) {
                            next.setHeadValue(new String(iterator.next().value()));
                        }
                    }
                    ContextManager.extract(contextCarrier);
                }
            }
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
