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

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.adapter.DefaultServerWebExchange;

import java.lang.reflect.Method;
import java.util.List;

public class DispatcherHandlerHandleMethodInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String WIP_OPERATION_NAME = "WEBFLUX.handle";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        EnhancedInstance instance = DispatcherHandlerHandleMethodInterceptor.getInstance(allArguments[0]);
        if (instance != null) {
            ContextCarrier contextCarrier = new ContextCarrier();
            CarrierItem next = contextCarrier.items();
            ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
            HttpHeaders headers = exchange.getRequest().getHeaders();
            while (next.hasNext()) {
                next = next.next();
                List<String> header = headers.get(next.getHeadKey());
                if (header != null && header.size() > 0) {
                    next.setHeadValue(header.get(0));
                }
            }
            AbstractSpan span = ContextManager.createEntrySpan(WIP_OPERATION_NAME, contextCarrier);
            span.setComponent(ComponentsDefine.SPRING_WEBFLUX);
            SpanLayer.asHttp(span);
            Tags.URL.set(span, exchange.getRequest().getURI().toString());
            instance.setSkyWalkingDynamicField(span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }

    public static EnhancedInstance getInstance(Object o) {
        EnhancedInstance instance = null;
        if (o instanceof ServerWebExchangeDecorator) {
            instance = getEnhancedInstance((ServerWebExchangeDecorator) o);
        } else if (o instanceof DefaultServerWebExchange) {
            instance = (EnhancedInstance) o;
        }
        return instance;
    }


    private static EnhancedInstance getEnhancedInstance(ServerWebExchangeDecorator serverWebExchangeDecorator) {
        Object o = serverWebExchangeDecorator.getDelegate();
        if (o instanceof ServerWebExchangeDecorator) {
            return getEnhancedInstance((ServerWebExchangeDecorator) o);
        } else if (o instanceof DefaultServerWebExchange) {
            return (EnhancedInstance) o;
        } else if (o == null) {
            throw new NullPointerException("The expected class DefaultServerWebExchange is null");
        } else {
            throw new RuntimeException("Unknown parameter types:" + o.getClass());
        }
    }

}
