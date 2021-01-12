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
 * Interceptor for send callback enhanced instance.
 * <p>
 * Here is the intercept process steps:
 *
 * <pre>
 *  1. Get the @{@link SendCallbackEnhanceRequiredInfo} and record the service url, context snapshot
 *  2. Create the local span when the callback invoke <code>sendComplete</code> method
 *  3. Stop the local span when <code>sendComplete</code> method finished.
 * </pre>
 */
public class SendCallbackInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String OPERATION_NAME = "Pulsar/Producer/Callback";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        SendCallbackEnhanceRequiredInfo requiredInfo = (SendCallbackEnhanceRequiredInfo) objInst.getSkyWalkingDynamicField();
        if (null != requiredInfo.getContextSnapshot()) {
            AbstractSpan activeSpan = ContextManager.createLocalSpan(OPERATION_NAME);
            activeSpan.setComponent(ComponentsDefine.PULSAR_PRODUCER);
            Tags.MQ_TOPIC.set(activeSpan, requiredInfo.getTopic());
            SpanLayer.asMQ(activeSpan);
            ContextManager.continued(requiredInfo.getContextSnapshot());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        SendCallbackEnhanceRequiredInfo requiredInfo = (SendCallbackEnhanceRequiredInfo) objInst.getSkyWalkingDynamicField();
        if (null != requiredInfo.getContextSnapshot()) {
            Exception exceptions = (Exception) allArguments[0];
            if (exceptions != null) {
                ContextManager.activeSpan().log(exceptions);
            }
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        SendCallbackEnhanceRequiredInfo requiredInfo = (SendCallbackEnhanceRequiredInfo) objInst.getSkyWalkingDynamicField();
        if (null != requiredInfo.getContextSnapshot()) {
            ContextManager.activeSpan().log(t);
        }
    }
}
