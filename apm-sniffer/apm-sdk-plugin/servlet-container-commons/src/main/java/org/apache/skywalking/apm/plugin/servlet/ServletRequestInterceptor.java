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

package org.apache.skywalking.apm.plugin.servlet;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;
import org.apache.skywalking.apm.util.StringUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public abstract class ServletRequestInterceptor implements InstanceMethodsAroundInterceptor {

    private static boolean IS_SERVLET_GET_STATUS_METHOD_EXIST;
    private static final String SERVLET_RESPONSE_CLASS = "javax.servlet.http.HttpServletResponse";
    private static final String GET_STATUS_METHOD = "getStatus";

    static {
        IS_SERVLET_GET_STATUS_METHOD_EXIST = MethodUtil.isMethodExist(
                ServletRequestInterceptor.class.getClassLoader(), SERVLET_RESPONSE_CLASS, GET_STATUS_METHOD);
    }

    /**
     * * The {@link TraceSegment#ref(TraceSegmentRef refSegment)} of current trace segment will reference to the trace segment id of the previous
     * level if the serialized context is not null.
     *
     * @param result change this result, if you want to truncate the method.
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        HttpServletRequest request = obtainServletRequest(objInst, method, allArguments, argumentsTypes);
        ContextCarrier contextCarrier = new ContextCarrier();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(request.getHeader(next.getHeadKey()));
        }

        AbstractSpan span = ContextManager.createEntrySpan(request.getRequestURI(), contextCarrier);
        Tags.URL.set(span, request.getRequestURL().toString());
        Tags.HTTP.METHOD.set(span, request.getMethod());
        Tags.HTTP.SERVLET_CONTEXT_PATH.set(span, request.getContextPath());
        Tags.HTTP.SERVLET_PATH.set(span, getServletPath(request));
        Tags.HTTP.SERVLET_PATH_INFO.set(span, request.getPathInfo());
        span.setComponent(getComponentsDefine());
        SpanLayer.asHttp(span);

        if (isCollectHTTPParams()) {
            collectHttpParam(request, span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        HttpServletRequest request = obtainServletRequest(objInst, method, allArguments, argumentsTypes);
        HttpServletResponse response = obtainServletResponse(objInst, method, allArguments, argumentsTypes, ret);

        AbstractSpan span = ContextManager.activeSpan();
        if (IS_SERVLET_GET_STATUS_METHOD_EXIST && response.getStatus() >= 400) {
            span.errorOccurred();
            Tags.STATUS_CODE.set(span, Integer.toString(response.getStatus()));
        }
        // Active HTTP parameter collection automatically in the profiling context.
        if (!isCollectHTTPParams() && span.isProfiling()) {
            collectHttpParam(request, span);
        }
        ContextManager.getRuntimeContext().remove(Constants.FORWARD_REQUEST_FLAG);
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(t);
    }

    private void collectHttpParam(HttpServletRequest request, AbstractSpan span) {
        final Map<String, String[]> parameterMap = new HashMap<>();
        for (final Enumeration<String> names = request.getParameterNames(); names.hasMoreElements(); ) {
            final String name = names.nextElement();
            parameterMap.put(name, request.getParameterValues(name));
        }

        if (!parameterMap.isEmpty()) {
            String tagValue = CollectionUtil.toString(parameterMap);
            tagValue = httpParamsLengthThreshold() > 0 ?
                    StringUtil.cut(tagValue, httpParamsLengthThreshold()) :
                    tagValue;
            Tags.HTTP.PARAMS.set(span, tagValue);
        }
    }

    public String getServletPath(HttpServletRequest request) {
        String servletPath = (String) request.getAttribute("javax.servlet.include.servlet_path");
        if (servletPath == null) {
            servletPath = request.getServletPath();
        }
        if (servletPath == null) {
            return "";
        }
        if (servletPath.length() > 1 && servletPath.endsWith("/")) {
            servletPath = servletPath.substring(0, servletPath.length() - 1);
        }

        return servletPath;
    }

    protected abstract boolean isCollectHTTPParams();

    protected abstract int httpParamsLengthThreshold();

    protected abstract HttpServletRequest obtainServletRequest(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes);

    protected abstract HttpServletResponse obtainServletResponse(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret);

    protected abstract OfficialComponent getComponentsDefine();
}
