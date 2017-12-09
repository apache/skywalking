/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.grpc.v1;

import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;

/**
 * {@link ClientCallOnNextInterceptor} create a exist span when the grpc start call. it will stop span when the method
 * type is non-unary.
 *
 * @author zhangxin
 */
public class ClientCallsMethodInterceptor
    implements StaticMethodsAroundInterceptor {

    @Override public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        MethodInterceptResult result) {
        EnhancedInstance clientCall = (EnhancedInstance)allArguments[0];
        GRPCDynamicFields cachedObjects = (GRPCDynamicFields)clientCall.getSkyWalkingDynamicField();

        final AbstractSpan span = ContextManager.createExitSpan(cachedObjects.getRequestMethodName(), cachedObjects.getAuthority());
        span.setComponent(ComponentsDefine.GRPC);
        SpanLayer.asRPCFramework(span);
    }

    @Override public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Object ret) {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
