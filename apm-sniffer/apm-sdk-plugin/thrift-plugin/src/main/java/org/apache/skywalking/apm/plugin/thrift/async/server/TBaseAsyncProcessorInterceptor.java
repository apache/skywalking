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

package org.apache.skywalking.apm.plugin.thrift.async.server;

import java.lang.reflect.Method;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.thrift.AbstractProcessorInterceptor;
import org.apache.thrift.AsyncProcessFunction;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseAsyncProcessor;

/**
 * @see TBaseAsyncProcessor is a Singleton
 */
public class TBaseAsyncProcessorInterceptor extends AbstractProcessorInterceptor {
    private Map<String, AsyncProcessFunction> processMapView;

    @Override
    public void onConstruct(final EnhancedInstance enhancedInstance, final Object[] objects) {
        enhancedInstance.setSkyWalkingDynamicField(objects[0].getClass().getName() + ".");
        processMapView = ((TBaseAsyncProcessor) enhancedInstance).getProcessMapView();
    }

    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // To get the 'remote peer' from AsyncFrameBufferInterceptor
        super.beforeMethod(objInst, method, allArguments, argumentsTypes, result);
        EnhancedInstance instance = (EnhancedInstance) allArguments[0];
        ContextManager.getRuntimeContext().put("peer", instance.getSkyWalkingDynamicField());
    }

    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        super.afterMethod(objInst, method, allArguments, argumentsTypes, ret);
        ContextManager.getRuntimeContext().remove("peer");
        return ret;
    }

    @Override
    protected TBase<?, ?> getFunction(final String method) {
        return processMapView.get(method).getEmptyArgsInstance();
    }
}
