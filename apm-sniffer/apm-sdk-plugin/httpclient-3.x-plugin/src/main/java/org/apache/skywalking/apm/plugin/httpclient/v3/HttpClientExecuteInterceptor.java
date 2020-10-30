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

package org.apache.skywalking.apm.plugin.httpclient.v3;

import java.lang.reflect.Method;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
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
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                             final Class<?>[] argumentsTypes, final MethodInterceptResult result) throws Throwable {

        final HttpMethod httpMethod = (HttpMethod) allArguments[1];
        if (httpMethod == null) {
            return;
        }
        final String remotePeer = httpMethod.getURI().getHost() + ":" + httpMethod.getURI().getPort();

        final URI uri = httpMethod.getURI();
        final String requestURI = getRequestURI(uri);

        final ContextCarrier contextCarrier = new ContextCarrier();
        final AbstractSpan span = ContextManager.createExitSpan(requestURI, contextCarrier, remotePeer);

        span.setComponent(ComponentsDefine.HTTPCLIENT);
        Tags.URL.set(span, uri.toString());
        Tags.HTTP.METHOD.set(span, httpMethod.getName());
        SpanLayer.asHttp(span);

        for (CarrierItem next = contextCarrier.items(); next.hasNext(); ) {
            next = next.next();
            httpMethod.setRequestHeader(next.getHeadKey(), next.getHeadValue());
        }

        if (HttpClientPluginConfig.Plugin.HttpClient.COLLECT_HTTP_PARAMS) {
           collectHttpParam(httpMethod, span);
        }
    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                              final Class<?>[] argumentsTypes, final Object ret) {
        if (ret != null) {
            final int statusCode = (Integer) ret;
            final AbstractSpan span = ContextManager.activeSpan();
            if (statusCode >= 400) {
                span.errorOccurred();
                Tags.STATUS_CODE.set(span, Integer.toString(statusCode));
            }
            final HttpMethod httpMethod = (HttpMethod) allArguments[1];
            if (httpMethod == null) {
                return ret;
            }
            // Active HTTP parameter collection automatically in the profiling context.
            if (!HttpClientPluginConfig.Plugin.HttpClient.COLLECT_HTTP_PARAMS && span.isProfiling()) {
                collectHttpParam(httpMethod, span);
            }
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
                                      final Class<?>[] argumentsTypes, final Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    private String getRequestURI(URI uri) throws URIException {
        String requestPath = uri.getPath();
        return requestPath != null && requestPath.length() > 0 ? requestPath : "/";
    }

    private void collectHttpParam(HttpMethod httpMethod, AbstractSpan span) {
        String tagValue = httpMethod.getQueryString();
        if (StringUtil.isNotEmpty(tagValue)) {
            tagValue = HttpClientPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD > 0 ?
                    StringUtil.cut(tagValue, HttpClientPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD) :
                    tagValue;
            Tags.HTTP.PARAMS.set(span, tagValue);
        }
    }
}
