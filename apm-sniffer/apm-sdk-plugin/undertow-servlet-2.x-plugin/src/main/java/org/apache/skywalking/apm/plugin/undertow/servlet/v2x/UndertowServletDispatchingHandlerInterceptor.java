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

package org.apache.skywalking.apm.plugin.undertow.servlet.v2x;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;
import org.apache.skywalking.apm.plugin.servlet.ServletRequestInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class UndertowServletDispatchingHandlerInterceptor extends ServletRequestInterceptor {
    @Override
    protected boolean isCollectHTTPParams() {
        return false;
    }

    @Override
    protected int httpParamsLengthThreshold() {
        return 1500;
    }

    @Override
    protected HttpServletRequest obtainServletRequest(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes) {
        HttpServerExchange exchange = (HttpServerExchange) allArguments[0];
        ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        return (HttpServletRequest) servletRequestContext.getServletRequest();
    }

    @Override
    protected HttpServletResponse obtainServletResponse(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) {
        HttpServerExchange exchange = (HttpServerExchange) allArguments[0];
        ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        return (HttpServletResponse) servletRequestContext.getServletResponse();
    }

    @Override
    protected OfficialComponent getComponentsDefine() {
        return ComponentsDefine.UNDERTOW;
    }
}
