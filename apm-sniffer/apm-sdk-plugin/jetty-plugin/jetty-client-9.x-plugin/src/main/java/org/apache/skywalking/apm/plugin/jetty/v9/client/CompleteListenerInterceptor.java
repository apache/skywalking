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


package org.apache.skywalking.apm.plugin.jetty.v9.client;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.eclipse.jetty.client.api.Result;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class CompleteListenerInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        ContextSnapshot contextSnapshot = (ContextSnapshot)objInst.getSkyWalkingDynamicField();
        if (contextSnapshot != null) {
            Result callBackResult = (Result)allArguments[0];

            AbstractSpan abstractSpan = ContextManager.createLocalSpan("CallBack/" + callBackResult.getRequest().getURI().getPath());
            ContextManager.continued(contextSnapshot);

            if (callBackResult.isFailed()) {
                abstractSpan.errorOccurred().log(callBackResult.getFailure());
                Tags.STATUS_CODE.set(abstractSpan, Integer.toString(callBackResult.getResponse().getStatus()));
            }
            abstractSpan.setComponent(ComponentsDefine.JETTY_CLIENT);
            abstractSpan.setLayer(SpanLayer.HTTP);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextSnapshot contextSnapshot = (ContextSnapshot)objInst.getSkyWalkingDynamicField();
        if (contextSnapshot != null) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
