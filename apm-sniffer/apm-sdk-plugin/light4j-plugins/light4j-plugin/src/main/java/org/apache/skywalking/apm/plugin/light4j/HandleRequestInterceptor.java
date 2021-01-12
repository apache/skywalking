/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package org.apache.skywalking.apm.plugin.light4j;

import com.networknt.exception.ExceptionHandler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.handler.OrchestrationHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link HandleRequestInterceptor} creates an entry span before the execution of {@link
 * com.networknt.exception.ExceptionHandler#handleRequest(HttpServerExchange)} in the I/O thread.
 * <p>
 * If the {@link Light4JPluginConfig.Plugin.Light4J#TRACE_HANDLER_CHAIN} flag is set, additionally a local span is produced
 * for each {@link com.networknt.handler.MiddlewareHandler} and business handler before their respective {@link
 * com.networknt.handler.LightHttpHandler#handleRequest(HttpServerExchange)} method executes. Since {@link
 * com.networknt.handler.LightHttpHandler} is implemented by various middleware and business handlers and the Light4J
 * framework delegates to these in succession, a chain of {@link org.apache.skywalking.apm.agent.core.context.trace.LocalSpan}s
 * will be produced.
 */
public class HandleRequestInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) {
        if (isExceptionHandler(objInst)) {
            HttpServerExchange exchange = (HttpServerExchange) allArguments[0];

            if (exchange.isInIoThread()) {

                String operationName = exchange.getRequestPath() + "@" + exchange.getRequestMethod();
                final HeaderMap headers = exchange.getRequestHeaders();
                final ContextCarrier contextCarrier = new ContextCarrier();

                CarrierItem next = contextCarrier.items();
                while (next.hasNext()) {
                    next = next.next();
                    next.setHeadValue(headers.getFirst(next.getHeadKey()));
                }

                AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);
                Tags.URL.set(span, exchange.getRequestURL());
                Tags.HTTP.METHOD.set(span, exchange.getRequestMethod().toString());
                span.setComponent(ComponentsDefine.LIGHT_4J);
                SpanLayer.asHttp(span);

                if (exchange.getStatusCode() >= 400) {
                    span.errorOccurred();
                    Tags.STATUS_CODE.set(span, String.valueOf(exchange.getStatusCode()));
                }

                ContextManager.stopSpan(span);

                objInst.setSkyWalkingDynamicField(ContextManager.capture());
            } else if (Light4JPluginConfig.Plugin.Light4J.TRACE_HANDLER_CHAIN) {
                String operationName = objInst.getClass().getName() + "." + method.getName();

                ContextSnapshot snapshot = (ContextSnapshot) objInst.getSkyWalkingDynamicField();
                ContextManager.createLocalSpan(operationName).setComponent(ComponentsDefine.LIGHT_4J);

                ContextManager.continued(snapshot);
            }
        } else if (Light4JPluginConfig.Plugin.Light4J.TRACE_HANDLER_CHAIN && (isMiddlewareHandler(
            objInst) || isBusinessHandler(objInst))) {
            String operationName = objInst.getClass().getName() + "." + method.getName();

            ContextManager.createLocalSpan(operationName).setComponent(ComponentsDefine.LIGHT_4J);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) {
        if (isExceptionHandler(objInst)) {
            HttpServerExchange exchange = (HttpServerExchange) allArguments[0];

            if (Light4JPluginConfig.Plugin.Light4J.TRACE_HANDLER_CHAIN && !exchange.isInIoThread()) {
                ContextManager.stopSpan();
            }
        } else if (Light4JPluginConfig.Plugin.Light4J.TRACE_HANDLER_CHAIN && (isMiddlewareHandler(
            objInst) || isBusinessHandler(objInst))) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    private boolean isBusinessHandler(EnhancedInstance objInst) {
        return !objInst.getClass().getInterfaces()[0].equals(MiddlewareHandler.class) && !objInst.getClass()
                                                                                                 .equals(
                                                                                                     OrchestrationHandler.class);
    }

    private boolean isMiddlewareHandler(EnhancedInstance objInst) {
        return objInst.getClass().getInterfaces()[0].equals(MiddlewareHandler.class);
    }

    private boolean isExceptionHandler(EnhancedInstance objInst) {
        return objInst.getClass().equals(ExceptionHandler.class);
    }
}
