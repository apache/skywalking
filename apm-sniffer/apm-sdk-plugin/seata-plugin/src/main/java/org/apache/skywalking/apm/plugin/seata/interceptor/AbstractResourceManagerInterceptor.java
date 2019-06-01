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

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

/**
 * @author kezhenxu94
 */
public class AbstractResourceManagerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
//        final EnhancedRequest enhancedRequest = ContextManager.getRuntimeContext().get("EnhancedRequest", EnhancedRequest.class);
//        ContextManager.getRuntimeContext().remove("EnhancedRequest");
//        final ContextSnapshot contextSnapshot = ContextManager.getRuntimeContext().get("ContextSnapshot", ContextSnapshot.class);
//        ContextManager.getRuntimeContext().remove("ContextSnapshot");

        final ContextCarrier contextCarrier = new ContextCarrier();
//        CarrierItem next = contextCarrier.items();
//        while (next.hasNext()) {
//            next = next.next();
//            next.setHeadValue(enhancedRequest.get(next.getHeadKey()));
//        }

        final AbstractSpan span = ContextManager.createEntrySpan(
            operationName(method),
            contextCarrier
        );

//        ContextManager.continued(contextSnapshot);

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
        return ComponentsDefine.SEATA.getName() + "/RM/" + method.getName();
    }
}
