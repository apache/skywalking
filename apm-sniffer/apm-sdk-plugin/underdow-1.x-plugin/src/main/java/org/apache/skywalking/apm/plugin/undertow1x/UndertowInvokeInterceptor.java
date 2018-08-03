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
package org.apache.skywalking.apm.plugin.undertow1x;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import javax.servlet.DispatcherType;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright @ 2018/7/20
 *
 * @author cloudgc
 */
public class UndertowInvokeInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    private static final String FORWARD_REQUEST_FLAG = "SW_FORWARD_REQUEST_FLAG";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        HttpServerExchange exchange = (HttpServerExchange) allArguments[0];

        DispatcherType dispatcherType = (DispatcherType) allArguments[3];

        if (dispatcherType == DispatcherType.FORWARD) {
            if (ContextManager.isActive()) {
                AbstractSpan abstractTracingSpan = ContextManager.activeSpan();
                Map<String, String> eventMap = new HashMap<String, String>();
                eventMap.put("forward-url", exchange.getRequestURI());
                abstractTracingSpan.log(System.currentTimeMillis(), eventMap);
                ContextManager.getRuntimeContext().put(FORWARD_REQUEST_FLAG, true);
            }

            return;
        }

        ContextCarrier contextCarrier = new ContextCarrier();

        HeaderMap headers = exchange.getRequestHeaders();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(headers.getFirst(next.getHeadKey()));
        }
        AbstractSpan span = ContextManager.createEntrySpan(exchange.getRequestURI(), contextCarrier);
        Tags.URL.set(span, exchange.getRequestURL());
        Tags.HTTP.METHOD.set(span, exchange.getRequestMethod().toString());
        span.setComponent(ComponentsDefine.UNDERTOW);
        SpanLayer.asHttp(span);

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        HttpServerExchange exchange = (HttpServerExchange) allArguments[0];

        if (ContextManager.isActive()) {
            AbstractSpan span = ContextManager.activeSpan();
            if (exchange.getStatusCode() >= 400) {
                span.errorOccurred();
                Tags.STATUS_CODE.set(span, Integer.toString(exchange.getStatusCode()));
            }
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(t);
        span.errorOccurred();
    }

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }
}
