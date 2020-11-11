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

package org.apache.skywalking.apm.plugin.feign.http.v9;

import feign.Request;
import feign.Response;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.skywalking.apm.util.StringUtil;

import static feign.Util.valuesOrEmpty;

/**
 * {@link DefaultHttpClientInterceptor} intercept the default implementation of http calls by the Feign.
 */
public class DefaultHttpClientInterceptor implements InstanceMethodsAroundInterceptor {

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    /**
     * Get the {@link feign.Request} from {@link EnhancedInstance}, then create {@link AbstractSpan} and set host, port,
     * kind, component, url from {@link feign.Request}. Through the reflection of the way, set the http header of
     * context data into {@link feign.Request#headers}.
     *
     * @param method intercept method
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable NoSuchFieldException or IllegalArgumentException
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Request request = (Request) allArguments[0];
        URL url = new URL(request.url());
        ContextCarrier contextCarrier = new ContextCarrier();
        int port = url.getPort() == -1 ? 80 : url.getPort();
        String remotePeer = url.getHost() + ":" + port;
        String operationName = url.getPath();
        FeignResolvedURL feignResolvedURL = PathVarInterceptor.URL_CONTEXT.get();
        if (feignResolvedURL != null) {
            try {
                operationName = operationName.replace(feignResolvedURL.getUrl(), feignResolvedURL.getOriginUrl());
            } finally {
                PathVarInterceptor.URL_CONTEXT.remove();
            }
        }
        if (operationName.length() == 0) {
            operationName = "/";
        }
        AbstractSpan span = ContextManager.createExitSpan(operationName, contextCarrier, remotePeer);
        span.setComponent(ComponentsDefine.FEIGN);
        Tags.HTTP.METHOD.set(span, request.method());
        Tags.URL.set(span, request.url());
        SpanLayer.asHttp(span);

        if (FeignPluginConfig.Plugin.Feign.COLLECT_REQUEST_BODY) {
            boolean needCollectHttpBody = false;
            Iterator<String> stringIterator = valuesOrEmpty(request.headers(), CONTENT_TYPE_HEADER).iterator();
            String contentTypeHeaderValue = stringIterator.hasNext() ? stringIterator.next() : "";
            for (String contentType : FeignPluginConfig.Plugin.Feign.SUPPORTED_CONTENT_TYPES_PREFIX.split(",")) {
                if (contentTypeHeaderValue.startsWith(contentType)) {
                    needCollectHttpBody = true;
                    break;
                }
            }
            if (needCollectHttpBody) {
                collectHttpBody(request, span);
            }
        }

        Field headersField = Request.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            List<String> contextCollection = new ArrayList<String>(1);
            contextCollection.add(next.getHeadValue());
            headers.put(next.getHeadKey(), contextCollection);
        }
        headers.putAll(request.headers());

        headersField.set(request, Collections.unmodifiableMap(headers));
    }

    private void collectHttpBody(final Request request, final AbstractSpan span) {
        if (request.body() == null || request.charset() == null) {
            return;
        }
        String tagValue = new String(request.body(), request.charset());
        tagValue = FeignPluginConfig.Plugin.Feign.FILTER_LENGTH_LIMIT > 0 ?
            StringUtil.cut(tagValue, FeignPluginConfig.Plugin.Feign.FILTER_LENGTH_LIMIT) : tagValue;

        Tags.HTTP.BODY.set(span, tagValue);
    }

    /**
     * Get the status code from {@link Response}, when status code greater than 400, it means there was some errors in
     * the server. Finish the {@link AbstractSpan}.
     *
     * @param method intercept method
     * @param ret    the method's original return value.
     * @return origin ret
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) {
        Response response = (Response) ret;
        if (response != null) {
            int statusCode = response.status();

            AbstractSpan span = ContextManager.activeSpan();
            if (statusCode >= 400) {
                span.errorOccurred();
                Tags.STATUS_CODE.set(span, Integer.toString(statusCode));
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
}
