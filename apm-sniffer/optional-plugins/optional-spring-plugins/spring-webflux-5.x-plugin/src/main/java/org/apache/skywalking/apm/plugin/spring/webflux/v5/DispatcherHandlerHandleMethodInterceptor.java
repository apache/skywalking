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

import java.lang.reflect.Method;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.tag.Tags.HTTP;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.publisher.Mono;

public class DispatcherHandlerHandleMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        EnhancedInstance instance = getInstance(allArguments[0]);

        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];

        ContextCarrier carrier = new ContextCarrier();
        CarrierItem next = carrier.items();
        HttpHeaders headers = exchange.getRequest().getHeaders();
        while (next.hasNext()) {
            next = next.next();
            List<String> header = headers.get(next.getHeadKey());
            if (header != null && header.size() > 0) {
                next.setHeadValue(header.get(0));
            }
        }

        AbstractSpan span = ContextManager.createEntrySpan(exchange.getRequest().getURI().getPath(), carrier);
        
        if (instance != null && instance.getSkyWalkingDynamicField() != null) {
            ContextManager.continued((ContextSnapshot) instance.getSkyWalkingDynamicField());
        }
        span.setComponent(ComponentsDefine.SPRING_WEBFLUX);
        SpanLayer.asHttp(span);
        Tags.URL.set(span, exchange.getRequest().getURI().toString());
        HTTP.METHOD.set(span, exchange.getRequest().getMethodValue());
        instance.setSkyWalkingDynamicField(ContextManager.capture());
        span.prepareForAsync();
        ContextManager.stopSpan(span);

        exchange.getAttributes().put("SKYWALING_SPAN", span);
    }
    
    private void maybeSetPattern(AbstractSpan span, ServerWebExchange exchange) {
        if (span != null) {
            PathPattern pathPattern = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            //check if the best matching pattern matches url in request
            //to avoid operationName overwritten by fallback url
            if (pathPattern != null && pathPattern.matches(exchange.getRequest().getPath().pathWithinApplication())) {
                span.setOperationName(pathPattern.getPatternString());
            }
        }

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];

        AbstractSpan span = (AbstractSpan) exchange.getAttributes().get("SKYWALING_SPAN");
        
                return ((Mono) ret).doFinally(s -> {

                    if (span != null) {
                        maybeSetPattern(span, exchange);
                        try {
            
                            HttpStatus httpStatus = exchange.getResponse().getStatusCode();
                            // fix webflux-2.0.0-2.1.0 version have bug. httpStatus is null. not support
                            if (httpStatus != null) {
                                Tags.STATUS_CODE.set(span, Integer.toString(httpStatus.value()));
                                if (httpStatus.isError()) {
                                    span.errorOccurred();
                                }
                            }
                        } finally {
                            span.asyncFinish();
                        }
                    }
        });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }

    public static EnhancedInstance getInstance(Object o) {
        EnhancedInstance instance = null;
        if (o instanceof DefaultServerWebExchange) {
            instance = (EnhancedInstance) o;
        } else if (o instanceof ServerWebExchangeDecorator) {
            ServerWebExchange delegate = ((ServerWebExchangeDecorator) o).getDelegate();
            return getInstance(delegate);
        }
        return instance;
    }

}
