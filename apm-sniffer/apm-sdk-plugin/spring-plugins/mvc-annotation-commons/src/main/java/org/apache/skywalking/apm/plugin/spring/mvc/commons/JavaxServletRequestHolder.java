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

import java.util.Enumeration;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

public class JavaxServletRequestHolder implements RequestHolder {

    private final HttpServletRequest request;

    public JavaxServletRequestHolder(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String getHeader(final String headerName) {
        return request.getHeader(headerName);
    }

    @Override
    public Enumeration<String> getHeaders(final String headerName) {
        return request.getHeaders(headerName);
    }

    @Override
    public String requestURL() {
        return request.getRequestURL().toString();
    }

    @Override
    public String requestMethod() {
        return request.getMethod();
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return request.getParameterMap();
    }
}
