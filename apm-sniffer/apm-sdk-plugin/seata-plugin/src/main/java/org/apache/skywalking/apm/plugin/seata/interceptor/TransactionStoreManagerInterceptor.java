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

package org.apache.skywalking.apm.plugin.seata.interceptor;

import io.seata.server.session.SessionCondition;
import io.seata.server.store.TransactionStoreManager;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.seata.Constants.*;

/**
 * TODO: revise the writeSession operations, it produces too many spans
 *
 * @author kezhenxu94
 */
public class TransactionStoreManagerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        final String methodName = method.getName();

        final AbstractSpan span = ContextManager.createLocalSpan(
            operationName(method)
        );

        if ("writeSession".equals(methodName)) {
            final TransactionStoreManager.LogOperation logOperation = (TransactionStoreManager.LogOperation) allArguments[0];
            span.tag(LOG_OPERATION, logOperation.name());
        } else if ("readSession".equals(methodName)) {
            final Object argument0 = allArguments[0];
            if (argument0 instanceof String) {
                span.tag(XID, (String) argument0);
            } else if (argument0 instanceof SessionCondition) {
                final SessionCondition sessionCondition = (SessionCondition) argument0;
                span.tag(XID, sessionCondition.getXid());
                span.tag(TRANSACTION_ID, String.valueOf(sessionCondition.getTransactionId()));
            }
        }

        span.setComponent(ComponentsDefine.SEATA);
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] argumentsTypes,
                              final Object ret) throws Throwable {
        if (ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] argumentsTypes,
                                      final Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().errorOccurred().log(t);
        }
    }

    private String operationName(final Method method) {
        return ComponentsDefine.SEATA.getName() + "/TransactionStoreManager/" + method.getName();
    }
}
