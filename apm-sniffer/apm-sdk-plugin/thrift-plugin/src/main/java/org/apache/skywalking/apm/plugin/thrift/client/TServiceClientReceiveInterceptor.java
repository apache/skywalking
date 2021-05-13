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

package org.apache.skywalking.apm.plugin.thrift.client;

import java.lang.reflect.Method;
import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * TAsyncClient creates a TServiceClient for receiving the response. So splitting this method,
 * TAsyncClient#receiveBase(...), into here for more efficiency.
 */
public class TServiceClientReceiveInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] objects,
                             final Class<?>[] classes,
                             final MethodInterceptResult ret) throws Throwable {
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst,
                              final Method method,
                              final Object[] objects,
                              final Class<?>[] classes,
                              final Object ret) throws Throwable {
        // Have to stop only when the span is created by TServiceClientInterceptor#beforeMethod(...).
        if (Objects.nonNull(objInst.getSkyWalkingDynamicField())) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance enhancedInstance,
                                      final Method method,
                                      final Object[] objects,
                                      final Class<?>[] classes,
                                      final Throwable throwable) {
        ContextManager.activeSpan().log(throwable);
    }
}
