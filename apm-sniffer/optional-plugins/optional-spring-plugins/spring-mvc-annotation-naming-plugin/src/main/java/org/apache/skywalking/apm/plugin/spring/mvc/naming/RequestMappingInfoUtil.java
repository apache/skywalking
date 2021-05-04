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

package org.apache.skywalking.apm.plugin.spring.mvc.naming;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RequestMappingInfoUtil {
    private static final ILog LOGGER = LogManager.getLogger(RequestMappingInfoUtil.class);

    public static List<RequestMappingInfo> collectRequestMappingInfo(EnhancedInstance objInst) {
        List<RequestMappingInfo> result = new ArrayList<>();
        if (objInst == null) {
            return result;
        }
        String[] basePath;
        try {
            basePath = getBasePath(objInst);
        } catch (Throwable t) {
            LOGGER.error(t, "failed for extract naming rule from controller '{}'", objInst.getClass().getName());
            return result;
        }
        for (Method method : objInst.getClass().getMethods()) {
            try {
                SimpleMappingInfo simpleMappingInfo = extractMappingInfo(method);
                if (simpleMappingInfo == null) {
                    continue;
                }
                String[] path = simpleMappingInfo.getPath();
                if (path.length == 0) {
                    path = new String[]{""};
                }
                String[] requestMethod = simpleMappingInfo.getRequestMethod();
                result.add(new RequestMappingInfo(basePath, path, requestMethod));
            } catch (Throwable e) {
                LOGGER.error(e, "failed for extract naming rule from controller '{}'", method);
            }
        }
        return result;
    }

    private static SimpleMappingInfo extractMappingInfo(Method method) {
        RequestMapping requestMapping;
        try {
            //this method since spring mvc 4.2
            requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        } catch (Throwable t) {
            // findAnnotation as fallback
            requestMapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        }

        if (requestMapping == null) {
            return null;
        }

        String[] requestMethod = null;
        String[] path;

        if (requestMapping.method().length > 0) {
            requestMethod = new String[requestMapping.method().length];
            for (int i = 0; i < requestMethod.length; i++) {
                requestMethod[i] = requestMapping.method()[i].name();
            }
        }
        path = requestMapping.value();
        if (path.length == 0) {
            try {
                path = requestMapping.path();
            } catch (Exception e) {
                //do nothing
            }
        }
        return new SimpleMappingInfo(path, requestMethod);
    }

    private static String[] getBasePath(EnhancedInstance objInst) {
        RequestMapping requestMapping = AnnotationUtils.findAnnotation(objInst.getClass(), RequestMapping.class);
        if (requestMapping == null) {
            return null;
        }
        String[] path = requestMapping.value();
        if (path.length == 0) {
            try {
                path = requestMapping.path();
            } catch (Throwable t) {
                //do nothing
            }
        }
        return path;
    }

    private static class SimpleMappingInfo {
        String[] path;
        String[] requestMethod;

        public SimpleMappingInfo(String[] path, String[] requestMethod) {
            this.path = path;
            this.requestMethod = requestMethod;
        }

        public String[] getPath() {
            return path;
        }

        public String[] getRequestMethod() {
            return requestMethod;
        }
    }
}
