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

package org.apache.skywalking.apm.plugin.activemq;

import java.lang.reflect.Method;
import org.apache.activemq.command.MessageDispatch;
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

public class ActiveMQConsumerInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String OPERATE_NAME_PREFIX = "ActiveMQ/";
    public static final String CONSUMER_OPERATE_NAME_SUFFIX = "/Consumer";
    public static final byte QUEUE_TYPE = 1;
    public static final byte TOPIC_TYPE = 2;
    public static final byte TEMP_TOPIC_TYPE = 6;
    public static final byte TEMP_QUEUE_TYPE = 5;

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        ContextCarrier contextCarrier = new ContextCarrier();
        String url = (String) objInst.getSkyWalkingDynamicField();
        MessageDispatch messageDispatch = (MessageDispatch) allArguments[0];
        AbstractSpan activeSpan = null;
        if (messageDispatch.getDestination().getDestinationType() == QUEUE_TYPE || messageDispatch.getDestination()
                                                                                                  .getDestinationType() == TEMP_QUEUE_TYPE) {
            activeSpan = ContextManager.createEntrySpan(OPERATE_NAME_PREFIX + "Queue/" + messageDispatch.getDestination()
                                                                                                        .getPhysicalName() + CONSUMER_OPERATE_NAME_SUFFIX, null)
                                       .start(System.currentTimeMillis());
            Tags.MQ_BROKER.set(activeSpan, url);
            Tags.MQ_QUEUE.set(activeSpan, messageDispatch.getDestination().getPhysicalName());
        } else if (messageDispatch.getDestination()
                                  .getDestinationType() == TOPIC_TYPE || messageDispatch.getDestination()
                                                                                        .getDestinationType() == TEMP_TOPIC_TYPE) {
            activeSpan = ContextManager.createEntrySpan(OPERATE_NAME_PREFIX + "Topic/" + messageDispatch.getDestination()
                                                                                                        .getPhysicalName() + CONSUMER_OPERATE_NAME_SUFFIX, null)
                                       .start(System.currentTimeMillis());
            Tags.MQ_BROKER.set(activeSpan, url);
            Tags.MQ_TOPIC.set(activeSpan, messageDispatch.getDestination().getPhysicalName());
        }
        activeSpan.setComponent(ComponentsDefine.ACTIVEMQ_CONSUMER);
        SpanLayer.asMQ(activeSpan);
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            Object propertyValue = messageDispatch.getMessage().getProperty(next.getHeadKey());
            if (propertyValue != null) {
                next.setHeadValue(propertyValue.toString());
            }
        }
        ContextManager.extract(contextCarrier);

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
        ContextManager.activeSpan().log(t);
    }
}
