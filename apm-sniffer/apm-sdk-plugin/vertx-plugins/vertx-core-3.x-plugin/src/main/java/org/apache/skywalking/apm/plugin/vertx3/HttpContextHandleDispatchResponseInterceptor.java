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

import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.web.client.impl.HttpContext;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.vertx3.HttpClientRequestImplInterceptor.HttpClientRequestContext;

import java.lang.reflect.Method;

public class HttpContextHandleDispatchResponseInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    @SuppressWarnings("rawtypes")
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        HttpContext httpContext = (HttpContext) objInst;
        HttpClientRequest clientRequest = httpContext.clientRequest();
        VertxContext context = ((HttpClientRequestContext) ((EnhancedInstance) clientRequest)
                .getSkyWalkingDynamicField()).vertxContext;
        Tags.HTTP_RESPONSE_STATUS_CODE.set(context.getSpan(), httpContext.clientResponse().statusCode());
        context.getSpan().asyncFinish();

        AbstractSpan span = ContextManager.createLocalSpan("#" + context.getSpan().getOperationName());
        span.setComponent(ComponentsDefine.VERTX);
        SpanLayer.asHttp(span);
        ContextManager.continued(context.getContextSnapshot());
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
