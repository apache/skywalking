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

package org.apache.skywalking.apm.plugin.spring.transaction;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.transaction.context.Constants;
import org.springframework.transaction.TransactionDefinition;

public class GetTransactionMethodInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        if (allArguments[0] == null) {
            AbstractSpan span = ContextManager.createLocalSpan(
                Constants.OPERATION_NAME_SPRING_TRANSACTION_NO_TRANSACTION_DEFINITION_GIVEN);
            span.setComponent(ComponentsDefine.SPRING_TX);
            return;
        }
        TransactionDefinition definition = (TransactionDefinition) allArguments[0];
        AbstractSpan span = ContextManager.createLocalSpan(
            Constants.OPERATION_NAME_SPRING_TRANSACTION_GET_TRANSACTION_METHOD + buildOperationName(definition
                                                                                                        .getName()));
        span.tag(Constants.TAG_SPRING_TRANSACTION_ISOLATION_LEVEL, String.valueOf(definition.getIsolationLevel()));
        span.tag(
            Constants.TAG_SPRING_TRANSACTION_PROPAGATION_BEHAVIOR, String.valueOf(definition.getPropagationBehavior()));
        span.tag(Constants.TAG_SPRING_TRANSACTION_TIMEOUT, String.valueOf(definition.getTimeout()));
        span.setComponent(ComponentsDefine.SPRING_TX);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    private String buildOperationName(String transactionDefinitionName) {
        if (!SpringTXPluginConfig.Plugin.SpringTransaction.SIMPLIFY_TRANSACTION_DEFINITION_NAME) {
            return transactionDefinitionName;
        }
        String[] ss = transactionDefinitionName.split("\\.");

        int simplifiedLength = ss.length - 2;
        if (simplifiedLength < 0) {
            return transactionDefinitionName;
        }
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < ss.length - 1; i++) {
            name.append(i < simplifiedLength ? ss[i].charAt(0) : ss[i]).append(".");
        }
        return name.append(ss[ss.length - 1]).toString();
    }
}
