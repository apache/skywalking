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

package org.apache.skywalking.apm.plugin.spring.mvc.commons;

import org.springframework.web.context.request.NativeWebRequest;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class EnhanceRequireObjectCache {
    private PathMappingCache pathMappingCache;
    private ThreadLocal<NativeWebRequest> nativeWebRequest = new ThreadLocal<NativeWebRequest>();
    private ThreadLocal<HttpServletResponse> httpResponse = new ThreadLocal<HttpServletResponse>();

    public void setPathMappingCache(PathMappingCache pathMappingCache) {
        this.pathMappingCache = pathMappingCache;
    }

    public HttpServletResponse getHttpServletResponse() {
        return httpResponse.get() == null ? (HttpServletResponse) nativeWebRequest.get().getNativeResponse() : httpResponse.get();
    }

    public void setNativeWebRequest(NativeWebRequest nativeWebRequest) {
        this.nativeWebRequest.set(nativeWebRequest);
    }

    public String findPathMapping(Method method) {
        return pathMappingCache.findPathMapping(method);
    }

    public void addPathMapping(Method method, String url) {
        pathMappingCache.addPathMapping(method, url);
    }

    public PathMappingCache getPathMappingCache() {
        return pathMappingCache;
    }

    public void setHttpResponse(HttpServletResponse httpResponse) {
        this.httpResponse.set(httpResponse);
    }

    public void clearRequestAndResponse() {
        setNativeWebRequest(null);
        setHttpResponse(null);
    }

}
