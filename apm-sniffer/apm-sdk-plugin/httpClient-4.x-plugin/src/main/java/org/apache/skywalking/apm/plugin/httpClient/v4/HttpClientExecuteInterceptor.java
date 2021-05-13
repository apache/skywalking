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

package org.apache.skywalking.apm.plugin.httpClient.v4;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpUriRequest;
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

public class HttpClientExecuteInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        if (allArguments[0] == null || allArguments[1] == null) {
            // illegal args, can't trace. ignore.
            return;
        }
        final HttpHost httpHost = (HttpHost) allArguments[0];
        HttpRequest httpRequest = (HttpRequest) allArguments[1];
        final ContextCarrier contextCarrier = new ContextCarrier();

        String remotePeer = httpHost.getHostName() + ":" + port(httpHost);

        String uri = httpRequest.getRequestLine().getUri();
        String requestURI = getRequestURI(uri);
        String operationName = requestURI;
        AbstractSpan span = ContextManager.createExitSpan(operationName, contextCarrier, remotePeer);

        span.setComponent(ComponentsDefine.HTTPCLIENT);
        Tags.URL.set(span, buildSpanValue(httpHost, uri));
        Tags.HTTP.METHOD.set(span, httpRequest.getRequestLine().getMethod());
        SpanLayer.asHttp(span);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            httpRequest.setHeader(next.getHeadKey(), next.getHeadValue());
        }
        if (HttpClientPluginConfig.Plugin.HttpClient.COLLECT_HTTP_PARAMS) {
            collectHttpParam(httpRequest, span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        if (allArguments[0] == null || allArguments[1] == null) {
            return ret;
        }

        if (ret != null) {
            HttpResponse response = (HttpResponse) ret;
            StatusLine responseStatusLine = response.getStatusLine();
            if (responseStatusLine != null) {
                int statusCode = responseStatusLine.getStatusCode();
                AbstractSpan span = ContextManager.activeSpan();
                if (statusCode >= 400) {
                    span.errorOccurred();
                    Tags.STATUS_CODE.set(span, Integer.toString(statusCode));
                }
                HttpRequest httpRequest = (HttpRequest) allArguments[1];
                // Active HTTP parameter collection automatically in the profiling context.
                if (!HttpClientPluginConfig.Plugin.HttpClient.COLLECT_HTTP_PARAMS && span.isProfiling()) {
                    collectHttpParam(httpRequest, span);
                }
            }
        }

        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.log(t);
    }

    private String getRequestURI(String uri) throws MalformedURLException {
        if (isUrl(uri)) {
            String requestPath = new URL(uri).getPath();
            return requestPath != null && requestPath.length() > 0 ? requestPath : "/";
        } else {
            return uri;
        }
    }

    private boolean isUrl(String uri) {
        String lowerUrl = uri.toLowerCase();
        return lowerUrl.startsWith("http") || lowerUrl.startsWith("https");
    }

    private String buildSpanValue(HttpHost httpHost, String uri) {
        if (isUrl(uri)) {
            return uri;
        } else {
            StringBuilder buff = new StringBuilder();
            buff.append(httpHost.getSchemeName().toLowerCase());
            buff.append("://");
            buff.append(httpHost.getHostName());
            buff.append(":");
            buff.append(port(httpHost));
            buff.append(uri);
            return buff.toString();
        }
    }

    private int port(HttpHost httpHost) {
        int port = httpHost.getPort();
        return port > 0 ? port : "https".equals(httpHost.getSchemeName().toLowerCase()) ? 443 : 80;
    }

    private void collectHttpParam(HttpRequest httpRequest, AbstractSpan span) {
        if (httpRequest instanceof HttpUriRequest) {
            URI uri = ((HttpUriRequest) httpRequest).getURI();
            String tagValue = uri.getQuery();
            if (StringUtil.isNotEmpty(tagValue)) {
                tagValue = HttpClientPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD > 0 ?
                        StringUtil.cut(tagValue, HttpClientPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD) :
                        tagValue;
                Tags.HTTP.PARAMS.set(span, tagValue);
            }
        }
    }
}
