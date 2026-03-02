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

package org.apache.skywalking.oap.log.analyzer.v2.compiler.rt;

import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.Binding;

/**
 * Static helper methods called by v2-generated {@code LalExpression} and consumer classes.
 * Centralizes type coercion and field access logic that was previously duplicated
 * into every generated class via {@code addHelperMethods()}.
 */
public final class LalRuntimeHelper {

    private LalRuntimeHelper() {
    }

    public static Object getAt(final Object obj, final String key) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Binding.Parsed) {
            return ((Binding.Parsed) obj).getAt(key);
        }
        if (obj instanceof Map) {
            return ((Map) obj).get(key);
        }
        return Binding.Parsed.getField(obj, key);
    }

    public static long toLong(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        if (obj instanceof String) {
            return Long.parseLong((String) obj);
        }
        return 0L;
    }

    public static int toInt(final Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        return 0;
    }

    public static String toStr(final Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    public static boolean toBool(final Object obj) {
        if (obj instanceof Boolean) {
            return ((Boolean) obj).booleanValue();
        }
        if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }
        return obj != null;
    }

    public static boolean isTruthy(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return ((Boolean) obj).booleanValue();
        }
        if (obj instanceof String) {
            return !((String) obj).isEmpty();
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue() != 0;
        }
        return true;
    }

    public static String tagValue(final Binding b, final String key) {
        final List dl = b.log().getTags().getDataList();
        for (int i = 0; i < dl.size(); i++) {
            final KeyStringValuePair kv = (KeyStringValuePair) dl.get(i);
            if (key.equals(kv.getKey())) {
                return kv.getValue();
            }
        }
        return "";
    }

    public static Object safeCall(final Object obj, final String method) {
        if (obj == null) {
            return null;
        }
        if ("toString".equals(method)) {
            return obj.toString();
        }
        if ("trim".equals(method)) {
            return obj.toString().trim();
        }
        if ("isEmpty".equals(method)) {
            return Boolean.valueOf(obj.toString().isEmpty());
        }
        return obj.toString();
    }
}
