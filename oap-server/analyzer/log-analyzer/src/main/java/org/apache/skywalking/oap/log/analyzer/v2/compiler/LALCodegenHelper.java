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
 */

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static utility constants and methods extracted from {@link LALClassGenerator}
 * for reuse by {@link LALBlockCodegen}.
 */
final class LALCodegenHelper {

    static final Map<String, String> LOG_GETTERS = new HashMap<>();
    static final Map<String, String> TRACE_CONTEXT_GETTERS = new HashMap<>();
    static final Set<String> LONG_FIELDS = new HashSet<>();
    static final Set<String> INT_FIELDS = new HashSet<>();

    static {
        LOG_GETTERS.put("service", "getService");
        LOG_GETTERS.put("serviceInstance", "getServiceInstance");
        LOG_GETTERS.put("endpoint", "getEndpoint");
        LOG_GETTERS.put("timestamp", "getTimestamp");
        LOG_GETTERS.put("body", "getBody");
        LOG_GETTERS.put("traceContext", "getTraceContext");
        LOG_GETTERS.put("tags", "getTags");
        LOG_GETTERS.put("layer", "getLayer");

        TRACE_CONTEXT_GETTERS.put("traceId", "getTraceId");
        TRACE_CONTEXT_GETTERS.put("traceSegmentId", "getTraceSegmentId");
        TRACE_CONTEXT_GETTERS.put("spanId", "getSpanId");

        LONG_FIELDS.add("timestamp");
        INT_FIELDS.add("spanId");
    }

    private LALCodegenHelper() {
        // utility class
    }

    static String escapeJava(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static String boxTypeName(final Class<?> primitiveType) {
        if (primitiveType == int.class) {
            return "Integer";
        } else if (primitiveType == long.class) {
            return "Long";
        } else if (primitiveType == boolean.class) {
            return "Boolean";
        } else if (primitiveType == double.class) {
            return "Double";
        } else if (primitiveType == float.class) {
            return "Float";
        }
        return null;
    }

    static String sanitizeName(final String name) {
        if (name == null || name.isEmpty()) {
            return "Generated";
        }
        final StringBuilder sb = new StringBuilder(name.length() + 1);
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            sb.append('_');
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        return sb.toString();
    }

    static String generateMapValCall(final List<String> keys) {
        if (keys.isEmpty()) {
            return "h.ctx().parsed()";
        }
        final StringBuilder call = new StringBuilder("h.mapVal(");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                call.append(", ");
            }
            call.append("\"").append(escapeJava(keys.get(i))).append("\"");
        }
        call.append(")");
        return call.toString();
    }
}
