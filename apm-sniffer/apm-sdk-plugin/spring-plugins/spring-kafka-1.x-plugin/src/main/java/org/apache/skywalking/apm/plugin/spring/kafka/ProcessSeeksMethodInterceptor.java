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

package org.apache.skywalking.apm.plugin.spring.kafka;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.kafka.define.Constants;
import org.apache.skywalking.apm.plugin.kafka.define.InterceptorMethod;

import java.lang.reflect.Method;

/**
 * The interceptor intends to be imposed on the method `processSeeks` in the ListenerConsumer class
 * defined in {@link org.springframework.kafka.listener.KafkaMessageListenerContainer}
 */
public class ProcessSeeksMethodInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String OPERATION_NAME = "/spring-kafka" + Constants.KAFKA_POLL_AND_INVOKE_OPERATION_NAME;

    /**
     * The `beforeMethod` is called before `processSeeks`, which can be used to mark the end of the last iteration.
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        InterceptorMethod.endKafkaPollAndInvokeIteration(null);
    }

    /**
     * The `afterMethod` is called after `processSeeks`, where the new PollAndInvoke iteration starts
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        InterceptorMethod.beginKafkaPollAndInvokeIteration(OPERATION_NAME);
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        InterceptorMethod.handleMethodException(t);
    }
}
