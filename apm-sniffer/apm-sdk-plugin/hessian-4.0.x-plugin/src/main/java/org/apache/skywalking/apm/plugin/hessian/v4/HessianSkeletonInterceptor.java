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

package org.apache.skywalking.apm.plugin.hessian.v4;

import com.caucho.hessian.io.AbstractHessianInput;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * intercept invoke of HessianSkeleton
 *
 * @author Alan Lau
 */
public class HessianSkeletonInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

        Object service = allArguments[0];

        if (service == null) {
            return;
        }

        AbstractHessianInput in = (AbstractHessianInput)allArguments[0];

        final ContextCarrier contextCarrier = new ContextCarrier();

        Object realService = objInst.getSkyWalkingDynamicField();
        String operationName = ((Class<?>)realService).getName();

        AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);

        span.start(System.currentTimeMillis());
        span.setComponent(ComponentsDefine.HESSIAN);
        Tags.URL.set(span, operationName);
        SpanLayer.asRPCFramework(span);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext() && in.readHeader() != null) {
            next = next.next();
            next.setHeadValue((String)in.readObject());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) {
        Object service = allArguments[0];

        if (service == null) {
            return ret;
        }

        ContextManager.stopSpan();

        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        dealException(t);
    }

    /**
     * Log the throwable, which occurs in Hessian RPC service.
     *
     * @param throwable
     */
    public void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.errorOccurred();
        span.log(throwable);
    }

}
