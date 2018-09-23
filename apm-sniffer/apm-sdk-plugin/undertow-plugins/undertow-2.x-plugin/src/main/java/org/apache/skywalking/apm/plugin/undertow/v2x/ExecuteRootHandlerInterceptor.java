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

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;

/**
 * @author chenpengfei
 */
public class ExecuteRootHandlerInterceptor implements StaticMethodsAroundInterceptor {

    @Override
    public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, MethodInterceptResult result) {
        HttpServerExchange exchange = (HttpServerExchange) allArguments[1];

        ContextCarrier contextCarrier = new ContextCarrier();
        HeaderMap headers = exchange.getRequestHeaders();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(headers.getFirst(next.getHeadKey()));
        }
        AbstractSpan span = ContextManager.createEntrySpan(exchange.getRequestPath(), contextCarrier);
        Tags.URL.set(span, exchange.getRequestURL());
        Tags.HTTP.METHOD.set(span, exchange.getRequestMethod().toString());
        span.setComponent(ComponentsDefine.UNDERTOW);
        SpanLayer.asHttp(span);
    }

    @Override
    public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, Object ret) {
        HttpServerExchange exchange = (HttpServerExchange) allArguments[1];

        AbstractSpan span = ContextManager.activeSpan();
        if (exchange.getStatusCode() >= 400) {
            span.errorOccurred();
            Tags.STATUS_CODE.set(span, Integer.toString(exchange.getStatusCode()));
        }
        ContextManager.stopSpan();
        ContextManager.getRuntimeContext().remove(Constants.FORWARD_REQUEST_FLAG);
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }
}
