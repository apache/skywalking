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

package org.apache.skywalking.apm.plugin.undertow.v2x;

import io.undertow.server.HttpServerExchange;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.plugin.undertow.v2x.util.TraceContextUtils;

import java.lang.reflect.Method;

/**
 * @author chenpengfei
 */
public class ExecuteRootHandlerInterceptor implements StaticMethodsAroundInterceptor {

    @Override
    public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, MethodInterceptResult result) {
        if (TraceContextUtils.isNotInRoutingHandlerTracing()) {
            HttpServerExchange exchange = (HttpServerExchange) allArguments[1];
            TraceContextUtils.buildUndertowEntrySpan(exchange, exchange.getRequestPath());
        }
    }

    @Override
    public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, Object ret) {
        if (TraceContextUtils.isNotInRoutingHandlerTracing()) {
            HttpServerExchange exchange = (HttpServerExchange) allArguments[1];

            AbstractSpan span = ContextManager.activeSpan();
            if (exchange.getStatusCode() >= 400) {
                span.errorOccurred();
                Tags.STATUS_CODE.set(span, Integer.toString(exchange.getStatusCode()));
            }
            ContextManager.stopSpan();
            ContextManager.getRuntimeContext().remove(Constants.FORWARD_REQUEST_FLAG);
        }
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }

}
