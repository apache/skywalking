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
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.plugin.grpc.v1.vo.GRPCDynamicFields;

import static org.skywalking.apm.plugin.grpc.v1.define.Constants.STREAM_OPERATION_NAME_SUFFIX;

/**
 * {@link ClientCallOnNextInterceptor} create a local span when the client stream receive an message that send from
 * server stream and record the value of <code>OnNext.count</code> tag.
 *
 * @author zhangxin
 */
public class ClientCallOnNextInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        GRPCDynamicFields cachedObjects = (GRPCDynamicFields)objInst.getSkyWalkingDynamicField();
        ContextManager.createLocalSpan(cachedObjects.getRequestMethodName() + STREAM_OPERATION_NAME_SUFFIX);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();

        // record the call count of onNext method
        GRPCDynamicFields cachedObjects = (GRPCDynamicFields)objInst.getSkyWalkingDynamicField();
        cachedObjects.incrementOnNextCount();

        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
