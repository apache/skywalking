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

import org.apache.skywalking.apm.util.StringUtil;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PathMappingCache} cache all request urls of {@link org.springframework.stereotype.Controller} .
 */
public class PathMappingCache {

    private static final String PATH_SEPARATOR = "/";

    private String classPath = "";

    private ConcurrentHashMap<Method, String> methodPathMapping = new ConcurrentHashMap<Method, String>();

    public PathMappingCache(String classPath) {
        if (!StringUtil.isEmpty(classPath) && !classPath.startsWith(PATH_SEPARATOR)) {
            classPath = PATH_SEPARATOR + classPath;
        }
        this.classPath = classPath;
    }

    public String findPathMapping(Method method) {
        return methodPathMapping.get(method);
    }

    public void addPathMapping(Method method, String methodPath) {
        if (!StringUtil.isEmpty(methodPath) && !methodPath.startsWith(PATH_SEPARATOR) && !classPath.endsWith(PATH_SEPARATOR)) {
            methodPath = PATH_SEPARATOR + methodPath;
        }
        methodPathMapping.put(method, (classPath + methodPath).replace("//", "/"));
    }
}
