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

import io.seata.core.protocol.transaction.AbstractBranchEndRequest;
import io.seata.core.protocol.transaction.BranchCommitRequest;
import io.seata.core.protocol.transaction.BranchRollbackRequest;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.seata.enhanced.EnhancedRequest;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.seata.Constants.RESOURCE_ID;
import static org.apache.skywalking.apm.plugin.seata.Constants.XID;

/**
 * @author kezhenxu94
 */
public class AbstractRMHandlerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        final ContextCarrier contextCarrier = new ContextCarrier();

        final EnhancedRequest enhancedRequest = (EnhancedRequest) allArguments[0];
        final String xid = ((AbstractBranchEndRequest) allArguments[0]).getXid();
        final String resourceId = ((AbstractBranchEndRequest) allArguments[0]).getResourceId();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(enhancedRequest.get(next.getHeadKey()));
        }

        final AbstractSpan span = ContextManager.createEntrySpan(
            operationName(method, allArguments),
            contextCarrier
        );

        span.tag(XID, xid);
        span.tag(RESOURCE_ID, resourceId);
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

    private String operationName(final Method method,
                                 final Object[] allArguments) {
        final Object argument0 = allArguments[0];
        if (argument0 instanceof BranchCommitRequest) {
            return ComponentsDefine.SEATA.getName() + "/RM/BranchCommit";
        } else if (argument0 instanceof BranchRollbackRequest) {
            return ComponentsDefine.SEATA.getName() + "/RM/BranchRollback";
        }
        return ComponentsDefine.SEATA.getName() + "/RM/" + method.getName();
    }
}
