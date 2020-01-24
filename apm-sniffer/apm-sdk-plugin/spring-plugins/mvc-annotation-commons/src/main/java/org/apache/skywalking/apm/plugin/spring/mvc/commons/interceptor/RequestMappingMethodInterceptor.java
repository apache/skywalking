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
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

/**
 * The <code>RequestMappingMethodInterceptor</code> only use the first mapping value.
 * it will interceptor with <code>@RequestMapping</code>
 *
 * @author clevertension
 */
public class RequestMappingMethodInterceptor extends AbstractMethodInterceptor {
    @Override
    public String getRequestURL(Method method) {
        return ParsePathUtil.recursiveParseMethodAnnotaion(method, m -> {
            String requestURL = null;
            RequestMapping methodRequestMapping = AnnotationUtils.getAnnotation(m, RequestMapping.class);
            if (methodRequestMapping != null) {
                if (methodRequestMapping.value().length > 0) {
                    requestURL = methodRequestMapping.value()[0];
                } else if (methodRequestMapping.path().length > 0) {
                    requestURL = methodRequestMapping.path()[0];
                }
            }
            return requestURL;
        });
    }

    @Override
    public String getAcceptedMethodTypes(Method method) {
        return ParsePathUtil.recursiveParseMethodAnnotaion(method, m -> {
            RequestMapping methodRequestMapping = AnnotationUtils.getAnnotation(m, RequestMapping.class);
            if (methodRequestMapping == null || methodRequestMapping.method().length == 0) {
                return null;
            }
            StringBuilder methodTypes = new StringBuilder();
            methodTypes.append("{");
            for (int i = 0; i < methodRequestMapping.method().length; i++) {
                methodTypes.append(methodRequestMapping.method()[i].toString());
                if (methodRequestMapping.method().length > (i + 1)) {
                    methodTypes.append(",");
                }
            }
            methodTypes.append("}");
            return methodTypes.toString();
        });
    }
}
