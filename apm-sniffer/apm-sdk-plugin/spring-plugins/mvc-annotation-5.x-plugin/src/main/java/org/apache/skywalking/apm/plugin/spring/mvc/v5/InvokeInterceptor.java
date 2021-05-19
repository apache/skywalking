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

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.util.CrossThreadRefContainer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT;

public class InvokeInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        final ServerHttpResponse response = exchange.getResponse();
        CrossThreadRefContainer container = new CrossThreadRefContainer();
        ContextManager.getRuntimeContext()
                .put(REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT, container);
        ContextManager.getRuntimeContext()
                .put(RESPONSE_KEY_IN_RUNTIME_CONTEXT, response);
        ContextManager.getRuntimeContext()
                .put(REQUEST_KEY_IN_RUNTIME_CONTEXT, exchange.getRequest());
        objInst.setSkyWalkingDynamicField(container);
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] argumentsTypes,
                              final Object ret) throws Throwable {
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        return ((Mono) ret).doFinally(s -> {
            CrossThreadRefContainer container = (CrossThreadRefContainer) objInst.getSkyWalkingDynamicField();
            Object ref = container.getRef();
            if (!(ref instanceof AbstractSpan)) {
                return;
            }
            AbstractSpan span = (AbstractSpan) ref;
            HttpStatus httpStatus = exchange.getResponse().getStatusCode();
            if (httpStatus != null && httpStatus.isError()) {
                span.errorOccurred();
                Tags.STATUS_CODE.set(span, Integer.toString(httpStatus.value()));
            }
            span.asyncFinish();
        });
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] argumentsTypes,
                                      final Throwable t) {

    }
}
