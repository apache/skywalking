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

package org.apache.skywalking.apm.plugin.spring.scheduled;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

public class ScheduledMethodInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    private static final String DEFAULT_OPERATION_NAME = "SpringScheduled";
    private static final String DEFAULT_LOGIC_ENDPOINT_CONTENT = "{\"logic-span\":true}";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        String targetMethodName = (String) objInst.getSkyWalkingDynamicField();
        String operationName = targetMethodName != null ? targetMethodName : DEFAULT_OPERATION_NAME;

        AbstractSpan span = ContextManager.createLocalSpan(operationName);
        Tags.LOGIC_ENDPOINT.set(span, DEFAULT_LOGIC_ENDPOINT_CONTENT);
        span.setComponent(ComponentsDefine.SPRING_SCHEDULED);
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

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) throws Throwable {
        Object targetMethod = allArguments[1];
        String targetMethodName = getTargetMethodName(targetMethod);

        objInst.setSkyWalkingDynamicField(targetMethodName);
    }

    private String getTargetMethodName(Object targetMethod) {
        if (targetMethod instanceof String) {
            return (String) targetMethod;
        }

        if (targetMethod instanceof Method) {
            Method method = (Method) targetMethod;

            String methodName = method.getName();
            String targetClassName = method.getDeclaringClass().getName();
            return targetClassName + "." + methodName;
        }

        return null;
    }
}
