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
 */

package org.apache.skywalking.apm.plugin.httpasyncclient.v4;

import org.apache.http.protocol.HttpContext;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.plugin.httpasyncclient.v4.context.Transmitter;

/**
 * request ready(completed) so we can start our local thread span;
 *
 * @author lican
 */
public class SessionRequestCompleteInterceptor implements InstanceMethodsAroundInterceptor {

    public static ThreadLocal<HttpContext> CONTEXT_LOCAL = new ThreadLocal<HttpContext>();
    public static ThreadLocal<Boolean> CONTEXT_LOCAL_EXIT = new ThreadLocal<Boolean>();
    public static ThreadLocal<AbstractSpan> CONTEXT_LOCAL_SPAN = new ThreadLocal<AbstractSpan>();

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        Transmitter transmitter = (Transmitter) objInst.getSkyWalkingDynamicField();
        if (transmitter == null) {
            CONTEXT_LOCAL_EXIT.set(false);
            CONTEXT_LOCAL_SPAN.set(null);
            return;
        }

        Boolean isOutExit = transmitter.getOutExit();
        if (!isOutExit) {
            ContextSnapshot snapshot = transmitter.getSnapshot();
            ContextManager.createLocalSpan("httpasyncclient/local");
            if (snapshot != null) {
                ContextManager.continued(snapshot);
            }
        }

        CONTEXT_LOCAL.set(transmitter.getHttpContext());
        CONTEXT_LOCAL_EXIT.set(isOutExit);
        if (isOutExit) {
            CONTEXT_LOCAL_SPAN.set(transmitter.getExitSpan());
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

    }
}
