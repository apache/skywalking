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

import org.apache.pulsar.client.api.MessageListener;
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
 * Interceptor for getting pulsar consumer message listener enhanced instance
 * <p>
 * Here is the intercept process steps:
 *
 * <pre>
 *  1. Return null if {@link org.apache.pulsar.client.impl.conf.ConsumerConfigurationData} has no message listener
 *  2. Return a new lambda expression wrap original message listener
 *  3. New lambda will create local span that continued message reception span
 *  4. Stop the local span when original message listener <code>received</code> method finished.
 * </pre>
 */
public class PulsarConsumerListenerInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String OPERATE_NAME_PREFIX = "Pulsar/";
    public static final String CONSUMER_OPERATE_NAME = "/Consumer/MessageListener";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        return ret == null ? null : (MessageListener) (consumer, message) -> {
            final MessageEnhanceRequiredInfo requiredInfo = (MessageEnhanceRequiredInfo) ((EnhancedInstance) message)
                    .getSkyWalkingDynamicField();
            if (requiredInfo == null || requiredInfo.getContextSnapshot() == null) {
                ((MessageListener) ret).received(consumer, message);
            } else {
                AbstractSpan activeSpan = ContextManager
                        .createLocalSpan(OPERATE_NAME_PREFIX + requiredInfo.getTopic() + CONSUMER_OPERATE_NAME);
                activeSpan.setComponent(ComponentsDefine.PULSAR_CONSUMER);
                SpanLayer.asMQ(activeSpan);
                Tags.MQ_TOPIC.set(activeSpan, requiredInfo.getTopic());
                ContextManager.continued(requiredInfo.getContextSnapshot());

                try {
                    ((MessageListener) ret).received(consumer, message);
                } catch (Exception e) {
                    ContextManager.activeSpan().log(e);
                } finally {
                    ContextManager.stopSpan();
                }
            }
        };
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {
    }
}
