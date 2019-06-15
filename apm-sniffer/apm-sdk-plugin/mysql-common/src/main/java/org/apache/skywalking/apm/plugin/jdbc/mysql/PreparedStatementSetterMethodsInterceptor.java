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

package org.apache.skywalking.apm.plugin.jdbc.mysql;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.jdbc.define.StatementEnhanceInfos;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.DISPLAYABLE_TYPES;
import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.PS_SETTERS;
import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.SQL_PARAMETER_PLACEHOLDER;

/**
 * @author kezhenxu94
 */
public class PreparedStatementSetterMethodsInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                                   Class<?>[] argumentsTypes,
                                   MethodInterceptResult result) throws Throwable {
        final StatementEnhanceInfos statementEnhanceInfos = (StatementEnhanceInfos) objInst.getSkyWalkingDynamicField();
        final int index = (Integer) allArguments[0];
        final Object parameter = getDisplayedParameter(method, argumentsTypes, allArguments);
        statementEnhanceInfos.setParameter(index, parameter);
    }

    @Override
    public final Object afterMethod(EnhancedInstance objInst,
                                    Method method,
                                    Object[] allArguments,
                                    Class<?>[] argumentsTypes,
                                    Object ret) throws Throwable {
        return ret;
    }

    @Override
    public final void handleMethodException(EnhancedInstance objInst,
                                            Method method,
                                            Object[] allArguments,
                                            Class<?>[] argumentsTypes,
                                            Throwable t) {
    }

    private Object getDisplayedParameter(final Method method, final Class<?>[] argumentTypes, final Object[] allArguments) {
        final String methodName = method.getName();
        if ("setNull".equals(methodName)) {
            return "null";
        }
        if ("setObject".equals(methodName)) {
            final Object parameter = allArguments[0];
            final Class<?> parameterType = argumentTypes[1];

            if (DISPLAYABLE_TYPES.contains(parameterType)) {
                return parameter;
            }
        } else if (PS_SETTERS.contains(methodName)) {
            return allArguments[1];
        }
        return SQL_PARAMETER_PLACEHOLDER;
    }
}
