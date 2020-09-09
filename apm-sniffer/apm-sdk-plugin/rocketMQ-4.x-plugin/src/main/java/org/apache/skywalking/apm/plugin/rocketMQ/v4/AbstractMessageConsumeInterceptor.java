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

package org.apache.skywalking.apm.plugin.rocketMQ.v4;

import java.lang.reflect.Method;
import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link AbstractMessageConsumeInterceptor} create entry span when the <code>consumeMessage</code> in the {@link
 * org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently} and {@link
 * org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly} class.
 */
public abstract class AbstractMessageConsumeInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String CONSUMER_OPERATION_NAME_PREFIX = "RocketMQ/";

    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        List<MessageExt> msgs = (List<MessageExt>) allArguments[0];

        ContextCarrier contextCarrier = getContextCarrierFromMessage(msgs.get(0));
        AbstractSpan span = ContextManager.createEntrySpan(CONSUMER_OPERATION_NAME_PREFIX + msgs.get(0)
                                                                                                .getTopic() + "/Consumer", contextCarrier);

        span.setComponent(ComponentsDefine.ROCKET_MQ_CONSUMER);
        SpanLayer.asMQ(span);
        for (int i = 1; i < msgs.size(); i++) {
            ContextManager.extract(getContextCarrierFromMessage(msgs.get(i)));
        }

    }

    @Override
    public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    private ContextCarrier getContextCarrierFromMessage(MessageExt message) {
        ContextCarrier contextCarrier = new ContextCarrier();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(message.getUserProperty(next.getHeadKey()));
        }

        return contextCarrier;
    }
}
