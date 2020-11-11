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
import org.apache.skywalking.apm.plugin.rocketMQ.v4.define.SendCallBackEnhanceInfo;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link OnExceptionInterceptor} create local span when the method {@link org.apache.rocketmq.client.producer.SendCallback#onException(Throwable)}
 * execute.
 */
public class OnExceptionInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String CALLBACK_OPERATION_NAME_PREFIX = "RocketMQ/";
    private static final String DEFAULT_TOPIC = "no_topic";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        SendCallBackEnhanceInfo enhanceInfo = (SendCallBackEnhanceInfo) objInst.getSkyWalkingDynamicField();
        String topicId = DEFAULT_TOPIC;
        // The SendCallBackEnhanceInfo could be null when there is an internal exception in the client API,
        // such as MQClientException("no route info of this topic")
        if (enhanceInfo != null) {
            topicId = enhanceInfo.getTopicId();
        }
        AbstractSpan activeSpan = ContextManager.createLocalSpan(CALLBACK_OPERATION_NAME_PREFIX + topicId + "/Producer/Callback");
        activeSpan.setComponent(ComponentsDefine.ROCKET_MQ_PRODUCER);
        activeSpan.log((Throwable) allArguments[0]);
        if (enhanceInfo != null && enhanceInfo.getContextSnapshot() != null) {
            ContextManager.continued(enhanceInfo.getContextSnapshot());
        }
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
