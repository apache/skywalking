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

package org.apache.skywalking.apm.plugin.neo4j.v4x;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * This interceptor is used to propagate the context snapshot in {@link SessionRequiredInfo} after {@link
 * org.neo4j.driver.internal.async.NetworkSession#beginTransactionAsync}
 * method is invoked.
 */
public class SessionBeginTransactionInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        SessionRequiredInfo requiredInfo = (SessionRequiredInfo) objInst.getSkyWalkingDynamicField();
        if (requiredInfo == null) {
            return ret;
        }

        CompletionStage<EnhancedInstance> transactionStage = (CompletionStage<EnhancedInstance>) ret;
        return transactionStage.thenApply(transaction -> {
            if (transaction == null) {
                return null;
            }

            transaction.setSkyWalkingDynamicField(requiredInfo);
            return transaction;
        });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {

    }
}
