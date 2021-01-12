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

package org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor;

import org.apache.skywalking.apm.plugin.spring.mvc.commons.ParsePathUtil;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

/**
 * The <code>RestMappingMethodInterceptor</code> only use the first mapping value. it will interceptor with
 * <code>@GetMapping</code>, <code>@PostMapping</code>, <code>@PutMapping</code>
 * <code>@DeleteMapping</code>, <code>@PatchMapping</code>
 */
public class RestMappingMethodInterceptor extends AbstractMethodInterceptor {
    @Override
    public String getRequestURL(Method method) {
        return ParsePathUtil.recursiveParseMethodAnnotation(method, m -> {
            String requestURL = null;
            GetMapping getMapping = AnnotationUtils.getAnnotation(m, GetMapping.class);
            PostMapping postMapping = AnnotationUtils.getAnnotation(m, PostMapping.class);
            PutMapping putMapping = AnnotationUtils.getAnnotation(m, PutMapping.class);
            DeleteMapping deleteMapping = AnnotationUtils.getAnnotation(m, DeleteMapping.class);
            PatchMapping patchMapping = AnnotationUtils.getAnnotation(m, PatchMapping.class);
            if (getMapping != null) {
                if (getMapping.value().length > 0) {
                    requestURL = getMapping.value()[0];
                } else if (getMapping.path().length > 0) {
                    requestURL = getMapping.path()[0];
                }
            } else if (postMapping != null) {
                if (postMapping.value().length > 0) {
                    requestURL = postMapping.value()[0];
                } else if (postMapping.path().length > 0) {
                    requestURL = postMapping.path()[0];
                }
            } else if (putMapping != null) {
                if (putMapping.value().length > 0) {
                    requestURL = putMapping.value()[0];
                } else if (putMapping.path().length > 0) {
                    requestURL = putMapping.path()[0];
                }
            } else if (deleteMapping != null) {
                if (deleteMapping.value().length > 0) {
                    requestURL = deleteMapping.value()[0];
                } else if (deleteMapping.path().length > 0) {
                    requestURL = deleteMapping.path()[0];
                }
            } else if (patchMapping != null) {
                if (patchMapping.value().length > 0) {
                    requestURL = patchMapping.value()[0];
                } else if (patchMapping.path().length > 0) {
                    requestURL = patchMapping.path()[0];
                }
            }
            return requestURL;
        });
    }

    @Override
    public String getAcceptedMethodTypes(Method method) {
        return ParsePathUtil.recursiveParseMethodAnnotation(method, m -> {
            if (AnnotationUtils.getAnnotation(m, GetMapping.class) != null) {
                return "{GET}";
            } else if (AnnotationUtils.getAnnotation(m, PostMapping.class) != null) {
                return "{POST}";
            } else if (AnnotationUtils.getAnnotation(m, PutMapping.class) != null) {
                return "{PUT}";
            } else if (AnnotationUtils.getAnnotation(m, DeleteMapping.class) != null) {
                return "{DELETE}";
            } else if (AnnotationUtils.getAnnotation(m, PatchMapping.class) != null) {
                return "{PATCH}";
            } else {
                return null;
            }
        });
    }
}
