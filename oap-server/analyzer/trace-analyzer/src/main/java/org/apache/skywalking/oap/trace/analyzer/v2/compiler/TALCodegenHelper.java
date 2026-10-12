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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Small helpers the generated Java source needs, shared by every DSL's codegen.
 */
public final class TALCodegenHelper {
    /**
     * The value-producing built-ins every DSL shares, mapping the name a rule writes to the runtime-helper method
     * that implements it and the type it returns. A DSL's own vocabulary goes through
     * {@link TALRootResolver#generateFunctionCall} instead.
     */
    public static final Map<String, Object[]> BUILTIN_FUNCTIONS = new HashMap<>();

    static {
        BUILTIN_FUNCTIONS.put("toJson", new Object[] {"h.toJsonObject", JsonObject.class});
        BUILTIN_FUNCTIONS.put("toJsonArray", new Object[] {"h.toJsonArray", JsonArray.class});
    }

    private TALCodegenHelper() {
    }

    /**
     * @param primitiveType a primitive class
     * @return the wrapper type's simple name, or null when the argument is not one of the boxable primitives
     */
    public static String boxTypeName(final Class<?> primitiveType) {
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
}
