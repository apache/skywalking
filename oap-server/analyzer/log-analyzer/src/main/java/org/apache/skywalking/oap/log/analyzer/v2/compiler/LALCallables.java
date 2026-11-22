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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.ProtocolMessageEnum;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry;
import org.apache.skywalking.oap.server.core.dsl.DslCallables;
import org.apache.skywalking.oap.server.core.dsl.debug.ToJson;
import org.apache.skywalking.oap.server.core.source.LogMetadata;

/**
 * LAL's list of the Java a rule may call, declared in the shared {@link DslCallables} and built the first time a rule
 * compiles; checked where a method, a getter, a subscript, a static call or a {@code def} cast resolves (and by
 * {@code LalRuntimeHelper} for a property read at run time). To support another method, add it to its row in
 * {@link #build()}. The rule that depends on the rule's context (a {@code def}'s casts) stays here, outside the
 * table.
 */
public final class LALCallables {

    /** Where a message points a rule's author for what a rule may call. */
    static final String DOCUMENTATION = "the LAL documentation";

    /** Getters every protobuf message and builder has that read its descriptor, not its fields. */
    private static final Set<String> PROTOBUF_REFLECTION = Set.of(
        "getDefaultInstanceForType", "getDescriptorForType", "getParserForType", "getUnknownFields",
        "getSerializedSize", "getAllFields", "getField", "getRepeatedField", "getRepeatedFieldCount",
        "getOneofFieldDescriptor", "hasField", "hasOneof", "getInitializationErrorString", "getDescriptor");

    /** The classes a {@code def} may be cast to, besides a protobuf message and an enum the output type takes. */
    private static final Set<Class<?>> DEF_CASTS = Set.of(
        List.class, Set.class, Map.class, BigInteger.class, BigDecimal.class);

    private static final String[] JSON_ELEMENT = {
        "getAsString", "getAsLong", "getAsInt", "getAsDouble", "getAsBoolean", "getAsJsonObject", "getAsJsonArray",
        "isJsonNull", "isJsonObject", "isJsonArray"
    };

    private LALCallables() {
        // utility class
    }

    private static final class Holder {
        private static final DslCallables CALLABLES = build();
    }

    private static DslCallables build() {
        return DslCallables.builder("a LAL rule", DOCUMENTATION)
            .methods(Object.class, "toString")
            .methods(String.class, DslCallables.TEXT_METHODS)
            .methods(Map.class, "get", "containsKey", "getOrDefault", "isEmpty", "size", "keySet", "values")
            .methods(List.class, "get")
            .methods(Collection.class, "size", "isEmpty", "contains")
            .methods(JsonElement.class, JSON_ELEMENT)
            .methods(JsonObject.class, "get", "has", "size")
            .methods(JsonArray.class, "get", "size")
            .getters(MessageOrBuilder.class, LALCallables::readsAProtobufField)
            .getters(LogMetadata.class)
            .getters(LogMetadata.TraceContext.class)
            .getters(ToJson.class)
            .methods(ExecutionContext.Parsed.class, "getMap")
            .methods(Enum.class, "name")
            .methods(ProtocolMessageEnum.class, "getNumber")
            .statics(ProcessRegistry.class,
                "generateVirtualLocalProcess", "generateVirtualRemoteProcess", "generateVirtualProcess")
            .build();
    }

    /**
     * LAL's list, built on first use.
     */
    public static DslCallables get() {
        return Holder.CALLABLES;
    }

    /**
     * A protobuf getter that reads a field: not one reading the message's descriptor, nor a builder's
     * {@code getXBuilder()}, which sets the field it returns ({@code getXOrBuilder()} only reads it).
     */
    private static boolean readsAProtobufField(final String name) {
        if (PROTOBUF_REFLECTION.contains(name)) {
            return false;
        }
        final boolean builder = name.endsWith("Builder") || name.endsWith("BuilderList");
        return !builder || name.endsWith("OrBuilder") || name.endsWith("OrBuilderList");
    }

    /**
     * A {@code def}'s class cast, when a rule may make it: to a list, set, map, {@code BigInteger}, {@code BigDecimal},
     * a protobuf message (a typed input's, read through its getters), or an enum the output type takes.
     */
    static Class<?> checkDefCast(final Class<?> castClass, final Class<?> outputType) {
        if (DEF_CASTS.contains(castClass) || MessageOrBuilder.class.isAssignableFrom(castClass)
                || castClass.isEnum() && takesEnum(outputType, castClass)) {
            return castClass;
        }
        throw new IllegalArgumentException(
            "A def cannot be cast as " + castClass.getName() + ": LAL casts a def as String, Long, Integer, Double, "
                + "Float, Boolean, long, int, double, float, boolean, java.util.List, java.util.Set, java.util.Map, "
                + "java.math.BigInteger, java.math.BigDecimal, a protobuf message, or an enum one of the output type's "
                + "setters takes");
    }

    private static boolean takesEnum(final Class<?> outputType, final Class<?> enumType) {
        if (outputType == null) {
            return false;
        }
        for (final Method m : outputType.getMethods()) {
            if (m.getName().startsWith("set") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == enumType) {
                return true;
            }
        }
        return false;
    }
}
