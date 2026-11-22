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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.dsl.DslCallables;

/**
 * The Java methods a rule can call, by the class of the value it calls them on, as the TAL documentation lists them,
 * in the structure every DSL declares its list in; a value's methods are its class's and its supertypes'.
 * A static method reached through a value and a variable-length one are not called, and {@code put} and {@code add}
 * change only the rule's own map or list, see {@code TALDefCodegen}.
 */
final class TALCallables {
    private TALCallables() {
    }

    /**
     * Built the first time a rule compiles.
     */
    private static final class Holder {
        private static final DslCallables CALLABLES = DslCallables.builder("a TAL rule", "the TAL documentation")
            .methods(String.class, DslCallables.TEXT_METHODS)
            .methods(Number.class, "intValue", "longValue", "doubleValue", "floatValue")
            .methods(BigDecimal.class, "toPlainString")
            .methods(JsonElement.class, "getAsString", "getAsInt", "getAsLong", "getAsShort", "getAsByte",
                "getAsDouble", "getAsFloat", "getAsBoolean", "getAsCharacter", "getAsNumber", "getAsBigDecimal",
                "getAsBigInteger", "getAsJsonObject", "getAsJsonArray", "getAsJsonPrimitive", "getAsJsonNull",
                "isJsonNull", "isJsonObject", "isJsonArray", "isJsonPrimitive")
            .methods(JsonArray.class, "get", "size", "isEmpty")
            .methods(JsonObject.class, "get", "has", "size", "keySet", "getAsJsonObject", "getAsJsonArray",
                "getAsJsonPrimitive")
            .methods(Map.class, "get", "getOrDefault", "containsKey", "isEmpty", "size", "keySet", "put")
            .methods(Collection.class, "size", "isEmpty", "contains", "add")
            .methods(List.class, "get")
            .methods(Layer.class, "name", "isNormal")
            .methods(Enum.class, "name")
            .methods(Object.class, "toString", "equals")
            .gettersOfUnlistedTypes()
            .refusingVarargs()
            .build();
    }

    static DslCallables get() {
        return Holder.CALLABLES;
    }
}
