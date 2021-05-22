/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.mvc.commons;

import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.util.StringUtil;
import org.springframework.http.server.reactive.ServerHttpRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestUtil {
    public static void collectHttpParam(HttpServletRequest request, AbstractSpan span) {
        final Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            String tagValue = CollectionUtil.toString(parameterMap);
            tagValue = SpringMVCPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD > 0 ?
                    StringUtil.cut(tagValue, SpringMVCPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD) : tagValue;
            Tags.HTTP.PARAMS.set(span, tagValue);
        }
    }

    public static void collectHttpParam(ServerHttpRequest request, AbstractSpan span) {
        Map<String, String[]> parameterMap = new HashMap<>(request.getQueryParams().size());
        request.getQueryParams().forEach((key, value) -> {
            parameterMap.put(key, value.toArray(new String[0]));
        });
        if (!parameterMap.isEmpty()) {
            String tagValue = CollectionUtil.toString(parameterMap);
            tagValue = SpringMVCPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD > 0 ?
                    StringUtil.cut(tagValue, SpringMVCPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD) : tagValue;
            Tags.HTTP.PARAMS.set(span, tagValue);
        }
    }

    public static void collectHttpHeaders(HttpServletRequest request, AbstractSpan span) {
        final List<String> headersList = new ArrayList<>(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS.size());
        SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS.stream()
                .filter(
                        headerName -> request.getHeaders(headerName) != null)
                .forEach(headerName -> {
                    Enumeration<String> headerValues = request.getHeaders(
                            headerName);
                    List<String> valueList = Collections.list(
                            headerValues);
                    if (!CollectionUtil.isEmpty(valueList)) {
                        String headerValue = valueList.toString();
                        headersList.add(headerName + "=" + headerValue);
                    }
                });

        collectHttpHeaders(headersList, span);
    }

    public static void collectHttpHeaders(ServerHttpRequest request, AbstractSpan span) {
        final List<String> headersList = new ArrayList<>(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS.size());
        SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS.stream()
                .filter(headerName -> getHeaders(request, headerName).hasMoreElements())
                .forEach(headerName -> {
                    Enumeration<String> headerValues = getHeaders(request, headerName);
                    List<String> valueList = Collections.list(
                            headerValues);
                    if (!CollectionUtil.isEmpty(valueList)) {
                        String headerValue = valueList.toString();
                        headersList.add(headerName + "=" + headerValue);
                    }
                });

        collectHttpHeaders(headersList, span);
    }

    private static void collectHttpHeaders(final List<String> headersList, final AbstractSpan span) {
        if (headersList != null && !headersList.isEmpty()) {
            String tagValue = String.join("\n", headersList);
            tagValue = SpringMVCPluginConfig.Plugin.Http.HTTP_HEADERS_LENGTH_THRESHOLD > 0 ?
                    StringUtil.cut(tagValue, SpringMVCPluginConfig.Plugin.Http.HTTP_HEADERS_LENGTH_THRESHOLD) : tagValue;
            Tags.HTTP.HEADERS.set(span, tagValue);
        }
    }

    public static Enumeration<String> getHeaders(final ServerHttpRequest request, final String headerName) {
        List<String> values = request.getHeaders().get(headerName);
        if (values == null) {
            return Collections.enumeration(Collections.emptyList());
        }
        return Collections.enumeration(values);
    }
}
