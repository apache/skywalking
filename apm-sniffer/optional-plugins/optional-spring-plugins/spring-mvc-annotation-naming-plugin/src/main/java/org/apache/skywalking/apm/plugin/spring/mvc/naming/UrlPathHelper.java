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

import org.springframework.util.StringUtils;

public class UrlPathHelper {

    public static String getLookupPath(String requestUri, String contextPath, String servletPath, String pathInfo) {
        String pathWithinApp = requestUri;
        if (contextPath != null) {
            pathWithinApp = UrlPathHelper.getRemainingPath(requestUri, contextPath, true);
        }
        if (servletPath != null) {
            String rest = getPathWithinServletMapping(servletPath, pathWithinApp, pathInfo);
            return StringUtils.hasLength(rest) ? rest : pathWithinApp;
        }
        return pathWithinApp;
    }

    public static String getRemainingPath(String requestUri, String mapping, boolean ignoreCase) {
        int index1 = 0;
        int index2 = 0;
        for (; (index1 < requestUri.length()) && (index2 < mapping.length()); index1++, index2++) {
            char c1 = requestUri.charAt(index1);
            char c2 = mapping.charAt(index2);
            if (c1 == ';') {
                index1 = requestUri.indexOf('/', index1);
                if (index1 == -1) {
                    return null;
                }
                c1 = requestUri.charAt(index1);
            }
            if (c1 == c2 || (ignoreCase && (Character.toLowerCase(c1) == Character.toLowerCase(c2)))) {
                continue;
            }
            return null;
        }
        if (index2 != mapping.length()) {
            return null;
        } else if (index1 == requestUri.length()) {
            return "";
        } else if (requestUri.charAt(index1) == ';') {
            index1 = requestUri.indexOf('/', index1);
        }
        return index1 != -1 ? requestUri.substring(index1) : "";
    }

    private static String getSanitizedPath(String path) {
        int index = path.indexOf("//");
        if (index < 0) {
            return path;
        } else {
            StringBuilder sanitized;
            for (sanitized = new StringBuilder(path); index != -1; index = sanitized.indexOf("//", index)) {
                sanitized.deleteCharAt(index);
            }

            return sanitized.toString();
        }
    }

    public static String getPathWithinServletMapping(String servletPath, String pathWithinApp, String pathInfo) {
        String sanitizedPathWithinApp = getSanitizedPath(pathWithinApp);
        String path;
        if (servletPath.contains(sanitizedPathWithinApp)) {
            path = getRemainingPath(sanitizedPathWithinApp, servletPath, false);
        } else {
            path = getRemainingPath(pathWithinApp, servletPath, false);
        }

        if (path != null) {
            return path;
        } else {
            if (pathInfo != null) {
                return pathInfo;
            } else {
                path = getRemainingPath(pathWithinApp, servletPath, false);
                if (path != null) {
                    return pathWithinApp;
                }
                return servletPath;
            }
        }
    }
}
