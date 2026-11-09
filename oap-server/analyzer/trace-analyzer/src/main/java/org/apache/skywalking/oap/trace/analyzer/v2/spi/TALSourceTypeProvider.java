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

package org.apache.skywalking.oap.trace.analyzer.v2.spi;

import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * The bridge between TAL and one input a rule may declare in {@code inputType}, and the point an input is extended at.
 * TAL asks every input the same questions, and each answers in its own concepts: the OTLP span reads attributes,
 * events, a resource and a scope and builds through span metadata; the native span reads tags and logs and builds the
 * agent analysis's sources from the segment.
 *
 * <p>The compiler resolves a rule's {@code inputType} entry against the registered aliases first and against the
 * declared {@link #inputType()} class name second, then types the {@code span} root to that class and resolves its
 * getter chains by reflection. It checks every construct and function a rule uses against the input's, and names the
 * inputs a misused one belongs to.
 *
 * <p>An input's functions are its provider's own public methods whose first parameter is the run, the
 * {@code TalRuntimeHelper}: what the input's rules read its data with and build with, named in the input's own words,
 * such as {@code attr} on an OTLP span and {@code tag} on a native one. Beside them, it hands the kernel the helper
 * functions its rules call, {@link #helperFunctions()}. Its data is read through its span, the {@link #inputType()};
 * the services a function needs are reached once, in {@link #bind}, or through the run's {@code FilterSpec}.
 *
 * <p>The trace-analyzer module registers the OTLP provider and the native one, {@code sw-native}. Implementations are
 * discovered through {@link java.util.ServiceLoader} from
 * {@code META-INF/services/org.apache.skywalking.oap.trace.analyzer.v2.spi.TALSourceTypeProvider}.
 */
public interface TALSourceTypeProvider {
    /**
     * @return the short name a rule writes in {@code inputType}, such as {@code otlp} or {@code sw-native}
     */
    String alias();

    /**
     * @return the Java type of the span the rule reads, which the {@code span} root is typed to
     */
    Class<?> inputType();

    /**
     * @return the constructs this input's rules may use beyond those of the language every input has: what the input
     * builds with
     */
    Set<TALConstruct> constructs();

    /**
     * The helper function implementations this input's rules call beside the provider's own functions, such as the
     * logic and configuration functions the shipped inputs share. Each serves the inputs its
     * {@link TALFunctionProvider#supportsInputType} accepts, every input or a specific one, and must accept this one.
     * A function name has one owner per input, among the provider's own functions, these helpers and the modules'
     * providers.
     *
     * @return the helpers, bound once when the rules compile
     */
    List<TALFunctionProvider> helperFunctions();

    /**
     * Reach the services the input's functions read, once, when the function registry is bound in the trace
     * analyzer's {@code start()}, so a function call reads a field rather than looking a service up.
     *
     * @param moduleManager the module manager; null for the default registry a unit test compiles against, which
     *                      starts no module
     */
    default void bind(ModuleManager moduleManager) {
    }
}
