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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalMetricsRuntime;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalNativeOutputs;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalValueConversions;

/**
 * The roles generated code calls to run a rule, beside the run itself, {@code TalRuntimeHelper}, which carries the
 * execution, the rule's services and the script's logger: {@link TalValueConversions}, {@link TalMetricsRuntime} and
 * {@link TalNativeOutputs}, each a class of static methods. The compiler finds a call's role by the method's name, so
 * a name belongs to one role; two roles declaring one name fail the first compile. The functions a rule calls are not
 * here: each input's provider hands the kernel its own and its helpers', see
 * {@code TALSourceTypeProvider#helperFunctions()}.
 */
final class TALRuntimeRoles {
    private static final List<Class<?>> ROLES =
        List.of(TalValueConversions.class, TalMetricsRuntime.class, TalNativeOutputs.class);
    private static final Map<String, Class<?>> OWNERS = index(ROLES);

    private TALRuntimeRoles() {
    }

    /**
     * @param roles the role classes
     * @return each public static method name to the role declaring it
     * @throws IllegalStateException when two roles declare one name
     */
    static Map<String, Class<?>> index(final List<Class<?>> roles) {
        final Map<String, Class<?>> owners = new HashMap<>();
        for (final Class<?> role : roles) {
            for (final Method method : role.getDeclaredMethods()) {
                final int modifiers = method.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || method.isSynthetic()) {
                    continue;
                }
                final Class<?> previous = owners.putIfAbsent(method.getName(), role);
                if (previous != null && previous != role) {
                    throw new IllegalStateException(
                        "TAL runtime method " + method.getName() + " is declared by both " + previous.getName()
                            + " and " + role.getName() + "; a method name belongs to one role");
                }
            }
        }
        return Map.copyOf(owners);
    }

    /**
     * @param name a method of a role generated code calls
     * @return {@code <role class>.<name>}, the static call generated code makes
     * @throws IllegalArgumentException when no role declares the method
     */
    static String method(final String name) {
        final Class<?> owner = OWNERS.get(name);
        if (owner == null) {
            throw new IllegalArgumentException("no TAL runtime role declares " + name);
        }
        return owner.getName() + "." + name;
    }
}
