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

/**
 * Interceptor for pulsar consumer message listener enhanced instance
 * <p>
 * Here is the intercept process steps:
 *
 * <pre>
 *  1. Get @{@link ContextCarrier} from message
 *  2. Create a local span when call <code>received</code> method
 *  3. Extract trace information from context carrier
 *  4. Stop the local span when <code>received</code> method finished.
 * </pre>
 */
public class PulsarConsumerListenerInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String CONSUMER_OPERATE_NAME = "Pulsar/Consumer/MessageListener/";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        if (allArguments[1] != null) {
            Message msg = (Message) allArguments[1];
            ContextCarrier carrier = new ContextCarrier();
            CarrierItem next = carrier.items();
            while (next.hasNext()) {
                next = next.next();
                next.setHeadValue(msg.getProperty(next.getHeadKey()));
            }
            AbstractSpan activeSpan = ContextManager.createLocalSpan(CONSUMER_OPERATE_NAME);
            ContextManager.extract(carrier);
            activeSpan.setComponent(ComponentsDefine.PULSAR_CONSUMER);
            SpanLayer.asMQ(activeSpan);
            Tags.MQ_TOPIC.set(activeSpan, msg.getTopicName());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        if (allArguments[1] != null) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {
        if (allArguments[1] != null) {
            ContextManager.activeSpan().log(t);
        }
    }
}
