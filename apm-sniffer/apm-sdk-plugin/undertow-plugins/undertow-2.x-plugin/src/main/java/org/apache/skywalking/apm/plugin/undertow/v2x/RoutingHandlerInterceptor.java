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

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.undertow.v2x.util.TraceContextUtils;

import java.lang.reflect.Method;

/**
 * @author AI
 * 2019-07-25
 */
public class RoutingHandlerInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        int httpHandlerIndex = -1;
        for (int index = 0; index < allArguments.length; index++) {
            Object any = allArguments[index];
            if (any instanceof HttpHandler) {
                httpHandlerIndex = index;
                break;
            }
        }
        if (httpHandlerIndex > -1) {
            HttpHandler handler = (HttpHandler) allArguments[httpHandlerIndex];
            String template = (String) allArguments[1];
            allArguments[httpHandlerIndex] = new TracingHandler(template, handler);
            TraceContextUtils.enabledRoutingHandlerTracing();
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }

    private static class TracingHandler implements HttpHandler {
        private final String template;
        private final HttpHandler next;

        TracingHandler(String template, HttpHandler handler) {
            this.next = handler;
            this.template = template;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            final AbstractSpan span = TraceContextUtils.buildUndertowEntrySpan(exchange, template);
            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                @Override
                public void exchangeEvent(HttpServerExchange httpServerExchange, NextListener nextListener) {
                    if (httpServerExchange.getStatusCode() >= 400) {
                        span.errorOccurred();
                        Tags.STATUS_CODE.set(span, Integer.toString(httpServerExchange.getStatusCode()));
                    }
                    span.asyncFinish();
                    nextListener.proceed();
                }
            });
            span.prepareForAsync();
            next.handleRequest(exchange);
            ContextManager.stopSpan(span);
        }
    }

}
