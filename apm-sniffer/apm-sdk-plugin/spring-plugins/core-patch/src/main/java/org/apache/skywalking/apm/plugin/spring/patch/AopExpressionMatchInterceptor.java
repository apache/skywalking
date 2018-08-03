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
 */

package org.apache.skywalking.apm.plugin.spring.patch;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;

/**
 * {@link AopExpressionMatchInterceptor} check if the method is match the enhanced method
 * if yes,return false else return true;
 *
 * @author lican
 */
public class AopExpressionMatchInterceptor implements StaticMethodsAroundInterceptor {

    private List<Method> methods = new ArrayList<Method>(2);

    public AopExpressionMatchInterceptor() {
        methods.addAll(Arrays.asList(EnhancedInstance.class.getDeclaredMethods()));
    }

    @Override
    public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Object ret) {
        Method targetAopMethod = (Method)allArguments[1];
        Class<?> targetAopClass = (Class<?>)allArguments[2];
        if (targetAopClass != null && EnhancedInstance.class.isAssignableFrom(targetAopClass) && isEnhancedMethod(targetAopMethod)) {
            return false;
        }
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Throwable t) {

    }

    private boolean isEnhancedMethod(Method targetMethod) {
        for (Method method : methods) {
            if (method.getName().equals(targetMethod.getName())
                && method.getReturnType().equals(targetMethod.getReturnType())
                && equalParamTypes(method.getParameterTypes(), targetMethod.getParameterTypes())) {
                return true;
            }
        }
        return false;
    }

    private boolean equalParamTypes(Class<?>[] params1, Class<?>[] params2) {
        if (params1.length != params2.length) {
            return false;
        }
        for (int i = 0; i < params1.length; i++) {
            if (!params1[i].equals(params2[i])) {
                return false;
            }
        }
        return true;
    }
}
