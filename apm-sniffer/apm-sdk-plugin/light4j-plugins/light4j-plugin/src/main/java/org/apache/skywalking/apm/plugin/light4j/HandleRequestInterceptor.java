/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package org.apache.skywalking.apm.plugin.light4j;

import io.undertow.server.HttpServerExchange;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

/**
 * {@link HandleRequestInterceptor} creates a local span when the {@link com.networknt.handler.LightHttpHandler#handleRequest(HttpServerExchange)}
 * method executes. {@link com.networknt.handler.LightHttpHandler} is implemented by various middleware and business handlers and
 * the Light4J framework runs these in succession. Thus when a request is sent, a chain of
 * {@link org.apache.skywalking.apm.agent.core.context.trace.LocalSpan}s is expected to be produced.
 *
 * @author tsuilouis
 */
public class HandleRequestInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) {
        String operationName = objInst.getClass().getName() + "." + method.getName();

        ContextSnapshot contextSnapshot = (ContextSnapshot) objInst.getSkyWalkingDynamicField();
        ContextManager.createLocalSpan(operationName)
                .setComponent(ComponentsDefine.LIGHT_4J);
        if (contextSnapshot != null) {
            ContextManager.continued(contextSnapshot);
        } else {
            objInst.setSkyWalkingDynamicField(ContextManager.capture());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes,
                                      Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
