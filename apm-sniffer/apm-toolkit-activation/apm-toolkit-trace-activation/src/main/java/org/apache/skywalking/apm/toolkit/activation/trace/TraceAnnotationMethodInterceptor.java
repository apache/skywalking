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


package org.apache.skywalking.apm.toolkit.activation.trace;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.toolkit.trace.Trace;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link TraceAnnotationMethodInterceptor} create a local span and set the operation name which fetch from
 * <code>org.apache.skywalking.apm.toolkit.trace.annotation.Trace.operationName</code>. if the fetch value is blank string, and
 * the operation name will be the method name.
 *
 * @author zhangxin
 */
public class TraceAnnotationMethodInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        Trace trace = method.getAnnotation(Trace.class);
        String operationName = trace.operationName();
        if (operationName.length() == 0) {
            operationName = generateOperationName(method);
        }

        ContextManager.createLocalSpan(operationName);
    }

    private String generateOperationName(Method method) {
        StringBuilder operationName = new StringBuilder(method.getDeclaringClass().getName() + "." + method.getName() + "(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            operationName.append(parameterTypes[i].getName());
            if (i < (parameterTypes.length - 1)) {
                operationName.append(",");
            }
        }
        operationName.append(")");
        return operationName.toString();
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
