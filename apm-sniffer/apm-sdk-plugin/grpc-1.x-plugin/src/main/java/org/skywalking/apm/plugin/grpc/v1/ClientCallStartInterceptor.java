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

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.lang.reflect.Method;
import org.skywalking.apm.agent.core.context.CarrierItem;
import org.skywalking.apm.agent.core.context.ContextCarrier;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;

/**
 * {@link ClientCallOnNextInterceptor} create a exist span when the grpc start call. it will stop span when the method
 * type is non-unary.
 *
 * @author zhangxin
 */
public class ClientCallStartInterceptor
    implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        GRPCDynamicFields cachedObjects = (GRPCDynamicFields)objInst.getSkyWalkingDynamicField();
        final Metadata headers = (Metadata)allArguments[1];
        final AbstractSpan span = ContextManager.createExitSpan(cachedObjects.getRequestMethodName(), cachedObjects.getAuthority());
        span.setComponent(ComponentsDefine.GRPC);
        SpanLayer.asRPCFramework(span);
        final ContextCarrier contextCarrier = new ContextCarrier();
        ContextManager.inject(contextCarrier);

        CarrierItem contextItem = contextCarrier.items();
        while (contextItem.hasNext()) {
            contextItem = contextItem.next();
            Metadata.Key<String> headerKey = Metadata.Key.of(contextItem.getHeadKey(), Metadata.ASCII_STRING_MARSHALLER);
            headers.put(headerKey, contextItem.getHeadValue());
        }

        GRPCDynamicFields listenerCachedObject = new GRPCDynamicFields();
        listenerCachedObject.setSnapshot(ContextManager.capture());
        listenerCachedObject.setDescriptor(cachedObjects.getDescriptor());
        ((EnhancedInstance)allArguments[0]).setSkyWalkingDynamicField(listenerCachedObject);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {

        if (((GRPCDynamicFields)objInst.getSkyWalkingDynamicField()).getMethodType() != MethodDescriptor.MethodType.UNARY) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
