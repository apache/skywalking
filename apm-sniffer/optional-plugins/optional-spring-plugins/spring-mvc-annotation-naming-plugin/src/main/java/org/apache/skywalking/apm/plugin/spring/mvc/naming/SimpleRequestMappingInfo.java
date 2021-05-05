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

import java.util.Set;

public class SimpleRequestMappingInfo {
    private final PatternsRequestCondition patternsRequestCondition;
    private RequestMethodsRequestCondition requestMethodsRequestCondition;

    public SimpleRequestMappingInfo(Set<String> patterns, Set<String> method) {
        this.patternsRequestCondition = new PatternsRequestCondition(patterns);
        if (method != null && method.size() > 0) {
            this.requestMethodsRequestCondition = new RequestMethodsRequestCondition(method);
        }
    }

    public String lookup(String path, String method) {
        String methods = "";
        if (requestMethodsRequestCondition != null && method != null) {
            methods = requestMethodsRequestCondition.match(method);
            if (methods == null) {
                return null;
            }
        }
        if (patternsRequestCondition == null) {
            return null;
        }

        String pattern = patternsRequestCondition.match(path);
        if (pattern == null) {
            return null;
        }
        if (requestMethodsRequestCondition != null) {
            return methods + pattern;
        }
        return pattern;
    }

    public Set<String> getDirectPaths() {
        return patternsRequestCondition.getDirectPaths();
    }

}
