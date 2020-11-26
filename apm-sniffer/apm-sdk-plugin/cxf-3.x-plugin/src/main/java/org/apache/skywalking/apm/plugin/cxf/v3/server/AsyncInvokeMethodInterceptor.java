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

package org.apache.skywalking.apm.plugin.cxf.v3.server;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * Create a new span and associate the existing link.
 */
public class AsyncInvokeMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                             final Class<?>[] argumentsTypes, final MethodInterceptResult result) {

        AbstractSpan span = ContextManager.createLocalSpan("/CXF/AsyncInvoke");
        span.setComponent(ComponentsDefine.APACHE_CXF);
        SpanLayer.asRPCFramework(span);

        final Object storedField = objInst.getSkyWalkingDynamicField();
        if (storedField != null) {
            final ContextSnapshot contextSnapshot = (ContextSnapshot) storedField;
            ContextManager.continued(contextSnapshot);
        }

    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                              final Class<?>[] argumentsTypes, final Object ret) {

        final Object storedField = objInst.getSkyWalkingDynamicField();
        if (storedField != null) {
            ContextManager.stopSpan();
        }

        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                                      final Class<?>[] argumentsTypes, final Throwable t) {

        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }
}
