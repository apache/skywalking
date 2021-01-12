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

package org.apache.skywalking.apm.plugin.okhttp.v3;

import java.lang.reflect.Method;
import okhttp3.Request;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link EnqueueInterceptor} create a local span and the prefix of the span operation name is start with `Async` when
 * the `enqueue` method called and also put the `ContextSnapshot` and `RealCall` instance into the
 * `SkyWalkingDynamicField`.
 */
public class EnqueueInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        EnhancedInstance callbackInstance = (EnhancedInstance) allArguments[0];
        Request request = (Request) objInst.getSkyWalkingDynamicField();
        ContextManager.createLocalSpan("Async" + request.url().uri().getPath());

        /**
         * Here is the process about how to trace the async function.
         *
         * 1. Storage `Request` object into `RealCall` instance when the constructor of `RealCall` called.
         * 2. Put the `RealCall` instance to `CallBack` instance
         * 3. Get the `RealCall` instance from `CallBack` and then Put the `RealCall` into `AsyncCall` instance
         *    since the constructor of `RealCall` called.
         * 5. Create the exit span by using the `RealCall` instance when `AsyncCall` method called.
         */

        callbackInstance.setSkyWalkingDynamicField(new EnhanceRequiredInfo(objInst, ContextManager.capture()));
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

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }
}
