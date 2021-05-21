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

package org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.RuntimeContext;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.RequestUtil;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.SpringMVCPluginConfig;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.exception.IllegalMethodStackDepthException;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.exception.ServletResponseNotFoundException;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.CONTROLLER_METHOD_STACK_DEPTH;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.FORWARD_REQUEST_FLAG;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT;

/**
 * the abstract method interceptor
 */
public abstract class AbstractMethodInterceptor implements InstanceMethodsAroundInterceptor {

    private static boolean IS_SERVLET_GET_STATUS_METHOD_EXIST;
    private static final String SERVLET_RESPONSE_CLASS = "javax.servlet.http.HttpServletResponse";
    private static final String GET_STATUS_METHOD = "getStatus";

    private static boolean IN_SERVLET_CONTAINER;

    static {
        IS_SERVLET_GET_STATUS_METHOD_EXIST = MethodUtil.isMethodExist(
                AbstractMethodInterceptor.class.getClassLoader(), SERVLET_RESPONSE_CLASS, GET_STATUS_METHOD);
        try {
            Class.forName(SERVLET_RESPONSE_CLASS, true, AbstractMethodInterceptor.class.getClassLoader());
            IN_SERVLET_CONTAINER = true;
        } catch (Exception ignore) {
            IN_SERVLET_CONTAINER = false;
        }
    }

    public abstract String getRequestURL(Method method);

    public abstract String getAcceptedMethodTypes(Method method);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {

        Boolean forwardRequestFlag = (Boolean) ContextManager.getRuntimeContext().get(FORWARD_REQUEST_FLAG);
        /**
         * Spring MVC plugin do nothing if current request is forward request.
         * Ref: https://github.com/apache/skywalking/pull/1325
         */
        if (forwardRequestFlag != null && forwardRequestFlag) {
            return;
        }

        String operationName;
        if (SpringMVCPluginConfig.Plugin.SpringMVC.USE_QUALIFIED_NAME_AS_ENDPOINT_NAME) {
            operationName = MethodUtil.generateOperationName(method);
        } else {
            EnhanceRequireObjectCache pathMappingCache = (EnhanceRequireObjectCache) objInst.getSkyWalkingDynamicField();
            String requestURL = pathMappingCache.findPathMapping(method);
            if (requestURL == null) {
                requestURL = getRequestURL(method);
                pathMappingCache.addPathMapping(method, requestURL);
                requestURL = pathMappingCache.findPathMapping(method);
            }
            operationName = getAcceptedMethodTypes(method) + requestURL;
        }

        Object request = ContextManager.getRuntimeContext().get(REQUEST_KEY_IN_RUNTIME_CONTEXT);

        if (request != null) {
            StackDepth stackDepth = (StackDepth) ContextManager.getRuntimeContext().get(CONTROLLER_METHOD_STACK_DEPTH);

            if (stackDepth == null) {
                final ContextCarrier contextCarrier = new ContextCarrier();

                if (IN_SERVLET_CONTAINER && HttpServletRequest.class.isAssignableFrom(request.getClass())) {
                    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
                    CarrierItem next = contextCarrier.items();
                    while (next.hasNext()) {
                        next = next.next();
                        next.setHeadValue(httpServletRequest.getHeader(next.getHeadKey()));
                    }

                    AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);
                    Tags.URL.set(span, httpServletRequest.getRequestURL().toString());
                    Tags.HTTP.METHOD.set(span, httpServletRequest.getMethod());
                    span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
                    SpanLayer.asHttp(span);

                    if (SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS) {
                        RequestUtil.collectHttpParam(httpServletRequest, span);
                    }

                    if (!CollectionUtil.isEmpty(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS)) {
                        RequestUtil.collectHttpHeaders(httpServletRequest, span);
                    }
                } else if (ServerHttpRequest.class.isAssignableFrom(request.getClass())) {
                    final ServerHttpRequest serverHttpRequest = (ServerHttpRequest) request;
                    CarrierItem next = contextCarrier.items();
                    while (next.hasNext()) {
                        next = next.next();
                        next.setHeadValue(serverHttpRequest.getHeaders().getFirst(next.getHeadKey()));
                    }

                    AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);
                    Tags.URL.set(span, serverHttpRequest.getURI().toString());
                    Tags.HTTP.METHOD.set(span, serverHttpRequest.getMethodValue());
                    span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
                    SpanLayer.asHttp(span);

                    if (SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS) {
                        RequestUtil.collectHttpParam(serverHttpRequest, span);
                    }

                    if (!CollectionUtil.isEmpty(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS)) {
                        RequestUtil.collectHttpHeaders(serverHttpRequest, span);
                    }
                } else {
                    throw new IllegalStateException("this line should not be reached");
                }

                stackDepth = new StackDepth();
                ContextManager.getRuntimeContext().put(CONTROLLER_METHOD_STACK_DEPTH, stackDepth);
            } else {
                AbstractSpan span = ContextManager.createLocalSpan(buildOperationName(objInst, method));
                span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
            }

            stackDepth.increment();
        }
    }

    private String buildOperationName(Object invoker, Method method) {
        StringBuilder operationName = new StringBuilder(invoker.getClass().getName()).append(".")
                .append(method.getName())
                .append("(");
        for (Class<?> type : method.getParameterTypes()) {
            operationName.append(type.getName()).append(",");
        }

        if (method.getParameterTypes().length > 0) {
            operationName = operationName.deleteCharAt(operationName.length() - 1);
        }

        return operationName.append(")").toString();
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        final RuntimeContext runtimeContext = ContextManager.getRuntimeContext();
        Boolean forwardRequestFlag = (Boolean) runtimeContext.get(FORWARD_REQUEST_FLAG);
        /**
         * Spring MVC plugin do nothing if current request is forward request.
         * Ref: https://github.com/apache/skywalking/pull/1325
         */
        if (forwardRequestFlag != null && forwardRequestFlag) {
            return ret;
        }

        Object request = runtimeContext.get(REQUEST_KEY_IN_RUNTIME_CONTEXT);

        if (request != null) {
            StackDepth stackDepth = (StackDepth) runtimeContext.get(CONTROLLER_METHOD_STACK_DEPTH);
            if (stackDepth == null) {
                throw new IllegalMethodStackDepthException();
            } else {
                stackDepth.decrement();
            }

            AbstractSpan span = ContextManager.activeSpan();

            if (stackDepth.depth() == 0) {
                Object response = runtimeContext.get(RESPONSE_KEY_IN_RUNTIME_CONTEXT);
                if (response == null) {
                    throw new ServletResponseNotFoundException();
                }

                Integer statusCode = null;

                if (IS_SERVLET_GET_STATUS_METHOD_EXIST && HttpServletResponse.class.isAssignableFrom(response.getClass())) {
                    statusCode = ((HttpServletResponse) response).getStatus();
                } else if (ServerHttpResponse.class.isAssignableFrom(response.getClass())) {
                    if (IS_SERVLET_GET_STATUS_METHOD_EXIST) {
                        statusCode = ((ServerHttpResponse) response).getRawStatusCode();
                    }
                    Object context = runtimeContext.get(REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT);
                    if (context != null) {
                        ((AbstractSpan[]) context)[0] = span.prepareForAsync();
                    }
                }

                if (statusCode != null && statusCode >= 400) {
                    span.errorOccurred();
                    Tags.STATUS_CODE.set(span, Integer.toString(statusCode));
                }

                runtimeContext.remove(REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT);
                runtimeContext.remove(REQUEST_KEY_IN_RUNTIME_CONTEXT);
                runtimeContext.remove(RESPONSE_KEY_IN_RUNTIME_CONTEXT);
                runtimeContext.remove(CONTROLLER_METHOD_STACK_DEPTH);
            }

            // Active HTTP parameter collection automatically in the profiling context.
            if (!SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS && span.isProfiling()) {
                if (HttpServletRequest.class.isAssignableFrom(request.getClass())) {
                    RequestUtil.collectHttpParam((HttpServletRequest) request, span);
                } else if (ServerHttpRequest.class.isAssignableFrom(request.getClass())) {
                    RequestUtil.collectHttpParam((ServerHttpRequest) request, span);
                }
            }

            ContextManager.stopSpan();
        }

        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
