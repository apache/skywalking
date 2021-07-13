/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.mvc.v5;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.RuntimeContext;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT;

public class InvokeInterceptor implements InstanceMethodsAroundInterceptorV2 {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInvocationContext context) throws Throwable {
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        final ServerHttpResponse response = exchange.getResponse();
        /**
         * First, we put the slot in the RuntimeContext,
         * as well as the MethodInvocationContext (MIC),
         * so that we can access it in the {@link org.apache.skywalking.apm.plugin.spring.mvc.v5.InvokeInterceptor#afterMethod}
         * Then we fetch the slot from {@link org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.AbstractMethodInterceptor#afterMethod}
         * and fulfill the slot with the real AbstractSpan.
         * Afterwards, we can safely remove the RuntimeContext.
         * Finally, when the lambda executes in the {@link reactor.core.publisher.Mono#doFinally},
         * ref of span could be acquired from MIC.
         */
        AbstractSpan[] ref = new AbstractSpan[1];
        RuntimeContext runtimeContext = ContextManager.getRuntimeContext();
        runtimeContext.put(REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT, ref);
        runtimeContext.put(RESPONSE_KEY_IN_RUNTIME_CONTEXT, response);
        runtimeContext.put(REQUEST_KEY_IN_RUNTIME_CONTEXT, exchange.getRequest());
        context.setContext(ref);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret, MethodInvocationContext context) throws Throwable {
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        return ((Mono) ret).doFinally(s -> {
            Object ctx = context.getContext();
            if (ctx == null) {
                return;
            }
            AbstractSpan span = ((AbstractSpan[]) ctx)[0];
            if (span == null) {
                return;
            }
            HttpStatus httpStatus = exchange.getResponse().getStatusCode();
            if (httpStatus != null && httpStatus.isError()) {
                span.errorOccurred();
                Tags.STATUS_CODE.set(span, Integer.toString(httpStatus.value()));
            }
            span.asyncFinish();
        });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t, MethodInvocationContext context) {

    }
}
