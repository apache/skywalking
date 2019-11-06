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

package org.apache.skywalking.apm.plugin.spring.webflux.v5;

/**
 * @author zhaoyuguang
 */

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import java.lang.reflect.Method;

public class AbstractServerResponseMethodInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * The error reason
     * see more details org.springframework.boot.web.reactive.error.DefaultErrorAttributes#storeErrorInformation
     */
    private final static String ERROR_ATTRIBUTE = "org.springframework.boot.web.reactive.error.DefaultErrorAttributes.ERROR";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        EnhancedInstance instance = DispatcherHandlerHandleMethodInterceptor.getInstance(allArguments[0]);
        if (instance != null) {
            AbstractSpan span = (AbstractSpan) instance.getSkyWalkingDynamicField();
            if (span == null) {
                return;
            }
            ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
            HttpStatus status = exchange.getResponse().getStatusCode();
            if (status != null && status.value() >= 400) {
                span.errorOccurred();
                if (exchange.getAttribute(ERROR_ATTRIBUTE) != null) {
                    span.log((Throwable) exchange.getAttribute(ERROR_ATTRIBUTE));
                }
                Tags.STATUS_CODE.set(span, Integer.toString(status.value()));
            }
            if (ContextManager.isActive()) {
                ContextManager.stopSpan(span);
            } else {
                span.asyncFinish();
            }
            ((EnhancedInstance) allArguments[0]).setSkyWalkingDynamicField(null);
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
