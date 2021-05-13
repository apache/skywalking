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

package org.apache.skywalking.apm.plugin.vertx3;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RoutingContextImplBase;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RouteStateInterceptor implements InstanceMethodsAroundInterceptor,
        InstanceConstructorInterceptor {

    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile("methods:\\[([a-zA-Z,]+)\\]");

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        if (allArguments.length > 8) {
            objInst.setSkyWalkingDynamicField(allArguments[8]);
        } else if (VertxContext.VERTX_VERSION >= 35 && VertxContext.VERTX_VERSION <= 38.2) {
            try {
                Field field = objInst.getClass().getDeclaredField("contextHandlers");
                field.setAccessible(true);
                objInst.setSkyWalkingDynamicField(field.get(objInst));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        RoutingContextImplBase routingContext = (RoutingContextImplBase) allArguments[0];
        List<Handler<RoutingContext>> contextHandlers = (List<Handler<RoutingContext>>) objInst.getSkyWalkingDynamicField();
        AtomicInteger currentContextIndex = (AtomicInteger) ((EnhancedInstance) routingContext).getSkyWalkingDynamicField();
        int handlerContextIndex = currentContextIndex.get();
        if (VertxContext.VERTX_VERSION >= 35 && contextHandlers.size() > 1) {
            currentContextIndex.getAndIncrement(); //3.5+ has possibility for multiple handlers
        }
        String contextName = contextHandlers.get(handlerContextIndex).getClass().getCanonicalName();
        int lambdaOffset = contextName.indexOf("$$Lambda$");
        if (lambdaOffset > 0) contextName = contextName.substring(0, lambdaOffset + 9);
        AbstractSpan span = ContextManager.createLocalSpan(String.format("%s.handle(RoutingContext)", contextName));

        Object connection = ((EnhancedInstance) routingContext.request()).getSkyWalkingDynamicField();
        VertxContext vertxContext = (VertxContext) ((EnhancedInstance) connection).getSkyWalkingDynamicField();

        String routeMethods = null;
        if (VertxContext.VERTX_VERSION >= 37.1) {
            if (routingContext.currentRoute().methods() != null) {
                routeMethods = "{" +
                        routingContext.currentRoute().methods()
                                .stream().map(Enum::toString)
                                .collect(Collectors.joining(","))
                        + "}";
            }
        } else {
            //no methods() method; have to strip from toString()
            Matcher matcher = HTTP_METHOD_PATTERN.matcher(routingContext.currentRoute().toString());
            if (matcher.find()) {
                routeMethods = "{" + matcher.group(1) + "}";
            }
        }
        if (routeMethods != null && routingContext.currentRoute().getPath() != null) {
            vertxContext.getSpan().setOperationName(routeMethods + routingContext.currentRoute().getPath());
        }

        ContextManager.continued(vertxContext.getContextSnapshot());
        span.setComponent(ComponentsDefine.VERTX);
        SpanLayer.asHttp(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
