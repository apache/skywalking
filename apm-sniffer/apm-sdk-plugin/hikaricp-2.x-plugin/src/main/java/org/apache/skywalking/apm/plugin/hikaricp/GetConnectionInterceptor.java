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

package org.apache.skywalking.apm.plugin.hikaricp;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.hikaricp.define.EnhanceObjectHolder;

public class GetConnectionInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String GET_CONNECTION_TIME_FLAG = "_GET_TIME_START";
    public static final String GET_CONNECTION_FAILURE_FLAG = "_GET_CONNECTION_FAILURE_FLAG";

    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        ContextManager.getRuntimeContext().put(GET_CONNECTION_TIME_FLAG, System.nanoTime());
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] argumentsTypes,
                              final Object ret) throws Throwable {
        EnhanceObjectHolder objectHolder = (EnhanceObjectHolder) objInst.getSkyWalkingDynamicField();
        if (objectHolder != null) {
            try {
                long startTime = (long) ContextManager.getRuntimeContext().get(GET_CONNECTION_TIME_FLAG);
                objectHolder.recordGetConnectionTime((System.nanoTime() - startTime) / 1000);

                objectHolder.recordGetConnectionStatue(
                    ContextManager.getRuntimeContext().get(GET_CONNECTION_FAILURE_FLAG) != null);
            } finally {
                ContextManager.getRuntimeContext().remove(GET_CONNECTION_TIME_FLAG);
                ContextManager.getRuntimeContext().remove(GET_CONNECTION_FAILURE_FLAG);
            }
        }

        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] argumentsTypes,
                                      final Throwable t) {
        ContextManager.getRuntimeContext().put(GET_CONNECTION_FAILURE_FLAG, true);
    }
}
