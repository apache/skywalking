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
package org.apache.skywalking.apm.plugin.springrabbit;

import com.rabbitmq.client.Channel;
import java.lang.reflect.Method;
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
import org.springframework.amqp.core.Message;

/**
 * @author jjlu521016@gmail.com
 */
public class RabbitMQSendInterceptor implements InstanceMethodsAroundInterceptor {
    public static final String OPERATE_NAME_PREFIX = "RabbitMQ/";
    public static final String PRODUCER_OPERATE_NAME_SUFFIX = "/Producer";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        ContextCarrier contextCarrier = new ContextCarrier();
        Channel channel = (Channel)allArguments[0];
        Message message = (Message)allArguments[3];

        String exChangeName = (String)allArguments[1];
        String queueName = (String)allArguments[2];
        String url = channel.getConnection().getAddress().getHostAddress() + ":" + channel.getConnection().getPort();
        AbstractSpan activeSpan = ContextManager.createExitSpan(OPERATE_NAME_PREFIX + "Topic/" + exChangeName + "/Queue/" + queueName + PRODUCER_OPERATE_NAME_SUFFIX, contextCarrier, url);
        Tags.MQ_BROKER.set(activeSpan, url);
        Tags.MQ_QUEUE.set(activeSpan, queueName);
        Tags.MQ_TOPIC.set(activeSpan, exChangeName);
        SpanLayer.asMQ(activeSpan);
        activeSpan.setComponent(ComponentsDefine.RABBITMQ_PRODUCER);
        CarrierItem next = contextCarrier.items();

        while (next.hasNext()) {
            next = next.next();
            // put header info to message
            message.getMessageProperties().setHeader(next.getHeadKey(), next.getHeadValue());
        }
        allArguments[3] = message;
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
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
