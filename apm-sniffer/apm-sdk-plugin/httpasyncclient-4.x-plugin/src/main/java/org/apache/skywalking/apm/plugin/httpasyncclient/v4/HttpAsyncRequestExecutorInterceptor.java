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
 */

package org.apache.skywalking.apm.plugin.httpasyncclient.v4;

import org.apache.http.HttpHost;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
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
import org.apache.skywalking.apm.plugin.httpclient.HttpClientPluginConfig;
import org.apache.skywalking.apm.util.StringUtil;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;

import static org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestCompleteInterceptor.CONTEXT_LOCAL;

/**
 * the actual point request begin fetch the request from thread local .
 */
public class HttpAsyncRequestExecutorInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        HttpContext context = CONTEXT_LOCAL.get();
        CONTEXT_LOCAL.remove();
        if (context == null) {
            return;
        }
        final ContextCarrier contextCarrier = new ContextCarrier();
        HttpRequestWrapper requestWrapper = (HttpRequestWrapper) context.getAttribute(HttpClientContext.HTTP_REQUEST);
        HttpHost httpHost = (HttpHost) context.getAttribute(HttpClientContext.HTTP_TARGET_HOST);

        RequestLine requestLine = requestWrapper.getRequestLine();
        String uri = requestLine.getUri();
        String operationName = uri.startsWith("http") ? new URL(uri).getPath() : uri;
        int port = httpHost.getPort();
        AbstractSpan span = ContextManager.createExitSpan(operationName, contextCarrier, httpHost.getHostName() + ":" + (port == -1 ? 80 : port));
        span.setComponent(ComponentsDefine.HTTP_ASYNC_CLIENT);
        Tags.URL.set(span, requestWrapper.getOriginal().getRequestLine().getUri());
        Tags.HTTP.METHOD.set(span, requestLine.getMethod());
        SpanLayer.asHttp(span);
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            requestWrapper.setHeader(next.getHeadKey(), next.getHeadValue());
        }
        if (HttpClientPluginConfig.Plugin.HttpClient.COLLECT_HTTP_PARAMS) {
            collectHttpParam(requestWrapper.getURI(), span);
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

    private void collectHttpParam(URI uri, AbstractSpan span) {
        if (uri == null) {
            return;
        }
        String tagValue = uri.getQuery();
        if (StringUtil.isNotEmpty(tagValue)) {
            tagValue = HttpClientPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD > 0 ?
                    StringUtil.cut(tagValue, HttpClientPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD) :
                    tagValue;
            Tags.HTTP.PARAMS.set(span, tagValue);
        }
    }
}
