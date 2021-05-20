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

package org.apache.skywalking.apm.plugin.mybatis;

import java.lang.reflect.Method;
import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;

public class MyBatisShellMethodInterceptor implements InstanceMethodsAroundInterceptorV2 {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInvocationContext context) throws Throwable {
        if (ContextManager.getRuntimeContext().get(Constants.MYBATIS_SHELL_METHOD_NAME) == null) {
            context.setContext(Constants.COLLECTED_FLAG);
            String operationName = MethodUtil.generateOperationName(method);
            ContextManager.getRuntimeContext().put(Constants.MYBATIS_SHELL_METHOD_NAME, operationName);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret, MethodInvocationContext context) throws Throwable {
        if (Objects.nonNull(context.getContext())) {
            ContextManager.getRuntimeContext().remove(Constants.MYBATIS_SHELL_METHOD_NAME);
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t, MethodInvocationContext context) {
        if (Objects.nonNull(context.getContext())) {
            ContextManager.getRuntimeContext().remove(Constants.MYBATIS_SHELL_METHOD_NAME);
        }
    }
}
