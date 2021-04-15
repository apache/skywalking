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

package org.apache.skywalking.apm.plugin.jsonrpc4j;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class JsonServiceExporterInterceptor implements InstanceMethodsAroundInterceptor {

    private static boolean IS_SERVLET_GET_STATUS_METHOD_EXIST;
    private static final String SERVLET_RESPONSE_CLASS = "javax.servlet.http.HttpServletResponse";
    private static final String GET_STATUS_METHOD = "getStatus";

    static {
        IS_SERVLET_GET_STATUS_METHOD_EXIST = MethodUtil.isMethodExist(JsonServiceExporterInterceptor.class.getClassLoader(), SERVLET_RESPONSE_CLASS, GET_STATUS_METHOD);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, MethodInterceptResult result) throws Throwable {

        HttpServletRequest request = (HttpServletRequest) objects[0];
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(request.getHeader(next.getHeadKey()));
        }

        AbstractSpan span = ContextManager.createEntrySpan(request.getRequestURI(), contextCarrier);
        Tags.HTTP.METHOD.set(span, request.getMethod());
        Tags.URL.set(span, request.getRequestURL().toString());
        span.setComponent(ComponentsDefine.JSON_RPC);
        SpanLayer.asRPCFramework(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, Object ret) throws Throwable {

        HttpServletResponse response = (HttpServletResponse) objects[1];
        AbstractSpan span = ContextManager.activeSpan();
        if (IS_SERVLET_GET_STATUS_METHOD_EXIST) {
            Tags.STATUS_CODE.set(span, String.valueOf(response.getStatus()));
        }

        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, Throwable throwable) {

    }
}
