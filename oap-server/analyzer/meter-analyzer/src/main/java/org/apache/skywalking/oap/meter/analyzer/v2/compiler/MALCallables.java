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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.registry.ProcessRegistry;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.dsl.DslCallables;

/**
 * What a MAL closure may call, as the MAL documentation lists it; built the first time a closure compiles. To support
 * another method, add it to its class here.
 */
final class MALCallables {

    private MALCallables() {
    }

    static DslCallables get() {
        return Holder.LIST;
    }

    /**
     * Refuses a call on a value the list does not allow. A receiver of unknown type is taken as text, as the code
     * generator casts it; an array's size() is Groovy's length.
     */
    static void require(final Class<?> receiver, final String name, final int arity) {
        if (receiver != null && receiver.isArray() && "size".equals(name) && arity == 0) {
            return;
        }
        final boolean unknown = receiver == null || receiver == Object.class;
        get().require(unknown && DslCallables.TEXT_METHODS.contains(name) ? String.class : receiver, name, arity);
    }

    /**
     * @return the class of the list's static calls a closure names, by its simple (Integer) or full name; else null
     */
    static Class<?> listedClass(final String name) {
        for (final Class<?> type : get().declaredStatics().keySet()) {
            if (type.getSimpleName().equals(name) || type.getName().equals(name)) {
                return type;
            }
        }
        return null;
    }

    private static final class Holder {
        static final DslCallables LIST = DslCallables.builder("a MAL closure", "the MAL documentation")
            .methods(Object.class, "toString", "equals")
            .methods(String.class, DslCallables.TEXT_METHODS)
            .methods(List.class, "get", "size", "isEmpty", "contains", "indexOf")
            // put and remove: on a map the closure built, or on the labels outside a filter (MALClosureCodegen)
            .methods(Map.class, "get", "getOrDefault", "containsKey", "size", "isEmpty", "keySet", "values", "put",
                "remove")
            .methods(Enum.class, "name")
            .methods(Layer.class, "name")
            .getters(MeterEntity.class)
            .setters(MeterEntity.class)
            .statics(Integer.class, "parseInt", "valueOf")
            .statics(Long.class, "parseLong", "valueOf")
            .statics(Double.class, "parseDouble", "valueOf")
            .statics(String.class, "valueOf")
            .statics(Arrays.class, "asList")
            .statics(ProcessRegistry.class, "generateVirtualLocalProcess", "generateVirtualRemoteProcess",
                "generateVirtualProcess")
            .staticFields(ProcessRegistry.class, "LOCAL_VIRTUAL_PROCESS", "REMOTE_VIRTUAL_PROCESS")
            .build();
    }
}
