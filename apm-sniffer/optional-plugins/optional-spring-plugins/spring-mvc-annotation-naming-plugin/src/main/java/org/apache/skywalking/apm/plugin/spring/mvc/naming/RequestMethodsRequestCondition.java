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

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class RequestMethodsRequestCondition {
    private List<String> methods;
    private final String result;

    public RequestMethodsRequestCondition(Set<String> methods) {
        this.methods = new LinkedList<>();
        this.methods.addAll(methods);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < this.methods.size(); i++) {
            sb.append(this.methods.get(i).toUpperCase());
            if (this.methods.size() > (i + 1)) {
                sb.append(",");
            }
        }
        sb.append("}");
        result = sb.toString();
    }

    public String match(String requestMethod) {
        if (requestMethod == null) {
            return null;
        }
        requestMethod = requestMethod.toUpperCase();
        if (methods.contains(requestMethod)) {
            return result;
        }
        if ("HEAD".equals(requestMethod) && methods.contains("GET")) {
            return result;
        }
        return null;
    }
}
