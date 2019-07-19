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
package org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * Abstract InstanceMethodsAroundInterceptor
 * 
 * @author qxo
 *
 */
public abstract class AbstractInstanceMethodsAroundInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    @Deprecated
    public final Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        throw new IllegalAccessError("This method should be invoked!");
    }

    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret, MethodAroundContext context) throws Throwable {
        final AbstractSpan span = context.getCurrentSpan();
        if (span != null) {
            ContextManager.stopSpan(span);
        }
        return ret;
    }


    @Override
    @Deprecated
   public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes,Throwable t) {
        throw new IllegalAccessError("This method should be invoked!");
    }
    
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes,Throwable t, MethodAroundContext context) {
        final AbstractSpan span = context.getCurrentSpan();
        if (span != null) {
            span.errorOccurred().log(t);
        }
    }
}