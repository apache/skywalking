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

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.server.reactive.ServerHttpRequest;

public class ReactiveRequestHolder implements RequestHolder {
    private final ServerHttpRequest serverHttpRequest;

    public ReactiveRequestHolder(final ServerHttpRequest serverHttpRequest) {
        this.serverHttpRequest = serverHttpRequest;
    }

    @Override
    public String getHeader(final String headerName) {
        return this.serverHttpRequest.getHeaders().getFirst(headerName);
    }

    @Override
    public Enumeration<String> getHeaders(final String headerName) {
        List<String> values = this.serverHttpRequest.getHeaders().get(headerName);
        if (values == null) {
            return Collections.enumeration(Collections.EMPTY_LIST);
        }
        return Collections.enumeration(values);
    }

    @Override
    public String requestURL() {
        return this.serverHttpRequest.getURI().toString();
    }

    @Override
    public String requestMethod() {
        return this.serverHttpRequest.getMethodValue();
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> parameterMap = new HashMap<>(this.serverHttpRequest.getQueryParams().size());
        this.serverHttpRequest.getQueryParams().forEach((key, value) -> {
            parameterMap.put(key, value.toArray(new String[0]));
        });
        return parameterMap;
    }
}
