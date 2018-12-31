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
 * {@link RabbitListenerInterceptor} create a local span and set the operation name which fetch from
 * <code>org.apache.skywalking.apm.toolkit.trace.annotation.Trace.operationName</code>. if the fetch value is blank string, and
 * the operation name will be the method name.
 *
 * @author jjlu521016@gmail.com
 */
public class RabbitListenerInterceptor implements InstanceMethodsAroundInterceptor {
    public static final String OPERATE_NAME_PREFIX = "RabbitMQ/";
    public static final String CONSUMER_OPERATE_NAME_SUFFIX = "/Consumer";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        ContextCarrier contextCarrier = new ContextCarrier();
        Channel channel = (Channel) allArguments[0];
        Message message = (Message) allArguments[1];
        String topic = message.getMessageProperties().getReceivedExchange();
        String queue = message.getMessageProperties().getReceivedRoutingKey();
        String url = channel.getConnection().getAddress().getHostAddress() + channel.getConnection().getPort();

        AbstractSpan activeSpan = ContextManager.createEntrySpan(OPERATE_NAME_PREFIX + "Topic/" + topic + "/Queue/" + queue + CONSUMER_OPERATE_NAME_SUFFIX, null).start(System.currentTimeMillis());
        Tags.MQ_BROKER.set(activeSpan, url);
        Tags.MQ_TOPIC.set(activeSpan, topic);
        Tags.MQ_QUEUE.set(activeSpan, queue);
        activeSpan.setComponent(ComponentsDefine.RABBITMQ_CONSUMER);
        SpanLayer.asMQ(activeSpan);
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(message.getMessageProperties().getHeaders().get(next.getHeadKey()).toString());
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
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
