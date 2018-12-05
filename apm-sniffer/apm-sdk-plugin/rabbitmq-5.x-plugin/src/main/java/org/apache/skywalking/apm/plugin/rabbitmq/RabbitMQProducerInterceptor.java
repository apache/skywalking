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

package org.apache.skywalking.apm.plugin.rabbitmq;

import com.rabbitmq.client.AMQP;
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
import java.util.HashMap;
import java.util.Map;

public class RabbitMQProducerInterceptor implements InstanceMethodsAroundInterceptor {
    public static final String OPERATE_NAME_PREFIX = "RabbitMQ/";
    public static final String PRODUCER_OPERATE_NAME_SUFFIX = "/Producer";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        ContextCarrier contextCarrier = new ContextCarrier();
        AMQP.BasicProperties  properties = (AMQP.BasicProperties) allArguments[4];
        String  exChangeName =  (String) allArguments[0];
        String  queueName =  (String) allArguments[1];
        String url = (String) objInst.getSkyWalkingDynamicField();
        AbstractSpan activeSpan = ContextManager.createExitSpan(OPERATE_NAME_PREFIX + "Topic/" + exChangeName + "Queue/" + queueName + PRODUCER_OPERATE_NAME_SUFFIX, contextCarrier, url);
        Tags.MQ_BROKER.set(activeSpan,url);
        Tags.MQ_QUEUE.set(activeSpan,queueName);
        Tags.MQ_TOPIC.set(activeSpan,exChangeName);
        SpanLayer.asMQ(activeSpan);
        activeSpan.setComponent(ComponentsDefine.RABBITMQ_PRODUCER);
        CarrierItem next = contextCarrier.items();

        Map<String, Object> headers = new HashMap<String, Object>();
        while (next.hasNext()) {
            next = next.next();
            headers.put(next.getHeadKey(),next.getHeadValue().getBytes());
        }

        if (properties == null) {
            AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder();
            properties = propsBuilder.headers(headers).build();
            allArguments[4] = properties;
        }
        allArguments[4] = properties.builder().headers(headers).build();

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
