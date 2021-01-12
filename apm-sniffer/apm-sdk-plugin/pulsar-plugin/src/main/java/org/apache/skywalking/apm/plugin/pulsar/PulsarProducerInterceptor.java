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

package org.apache.skywalking.apm.plugin.pulsar;

import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.common.api.proto.PulsarApi;
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

/**
 * Interceptor for pulsar producer enhanced instance.
 * <p>
 * Here is the intercept process steps:
 *
 * <pre>
 *  1. Get the {@link ProducerEnhanceRequiredInfo} and record the service url, topic name
 *  2. Create the exit span when the producer invoke <code>sendAsync</code> method
 *  3. Inject the context to {@link Message#getProperties()}
 *  4. Create {@link SendCallbackEnhanceRequiredInfo} with <code>ContextManager.capture()</code> and set the
 *     callback enhanced instance skywalking dynamic field to the created required info.
 *  5. Stop the exit span when <code>sendAsync</code> method finished.
 * </pre>
 */
public class PulsarProducerInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String OPERATE_NAME_PREFIX = "Pulsar/";
    public static final String PRODUCER_OPERATE_NAME_SUFFIX = "/Producer";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        if (allArguments[0] != null) {
            ProducerEnhanceRequiredInfo requiredInfo = (ProducerEnhanceRequiredInfo) objInst.getSkyWalkingDynamicField();
            ContextCarrier contextCarrier = new ContextCarrier();
            String topicName = requiredInfo.getTopic();
            AbstractSpan activeSpan = ContextManager.createExitSpan(OPERATE_NAME_PREFIX + topicName + PRODUCER_OPERATE_NAME_SUFFIX, contextCarrier, requiredInfo
                .getServiceUrl());
            Tags.MQ_BROKER.set(activeSpan, requiredInfo.getServiceUrl());
            Tags.MQ_TOPIC.set(activeSpan, topicName);
            contextCarrier.extensionInjector().injectSendingTimestamp();
            SpanLayer.asMQ(activeSpan);
            activeSpan.setComponent(ComponentsDefine.PULSAR_PRODUCER);
            CarrierItem next = contextCarrier.items();
            MessageImpl msg = (MessageImpl) allArguments[0];
            while (next.hasNext()) {
                next = next.next();
                msg.getMessageBuilder()
                   .addProperties(PulsarApi.KeyValue.newBuilder()
                                                    .setKey(next.getHeadKey())
                                                    .setValue(next.getHeadValue()));
            }
            if (allArguments.length > 1) {
                EnhancedInstance callbackInstance = (EnhancedInstance) allArguments[1];
                if (callbackInstance != null) {
                    ContextSnapshot snapshot = ContextManager.capture();
                    if (null != snapshot) {
                        SendCallbackEnhanceRequiredInfo callbackRequiredInfo = new SendCallbackEnhanceRequiredInfo();
                        callbackRequiredInfo.setTopic(topicName);
                        callbackRequiredInfo.setContextSnapshot(snapshot);
                        callbackInstance.setSkyWalkingDynamicField(callbackRequiredInfo);
                    }
                }
            }
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        if (allArguments[0] != null) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        if (allArguments[0] != null) {
            ContextManager.activeSpan().log(t);
        }
    }
}
