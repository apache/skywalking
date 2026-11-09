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

package org.apache.skywalking.oap.server.core.source;

import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Functions contributed to TAL for the inputs that build with them. Each input's own functions are
 * declared on its side this way, the OTLP span's and the native span's, and a module adds its own the same way
 * without depending on the trace analyzer. The functions are the public methods the implementing class declares
 * itself; a rule calls one by name, and the compiler resolves it by name, argument count and argument types, and
 * types its result from the method's return type. A function whose first parameter is a {@link TALFunctionContext}
 * receives the run there, ahead of the rule's arguments.
 *
 * <p>Implementations are found through the {@code ServiceLoader}. Those of loaded modules are bound once, in
 * the trace analyzer's {@code start()}, after their module started. An implementation is stateless across runs and
 * safe to call from every receiver thread. A function name two providers serve for one input fails the boot; a
 * function a rule calls that only another input's provider serves fails the rule, naming the inputs it belongs to.
 */
public interface TALFunctionProvider {
    /**
     * The alias of the input that SkyWalking agents' segments arrive as.
     */
    String SW_NATIVE_INPUT_TYPE = "sw-native";

    /**
     * @return the name of the module this provider belongs to; it is bound only when that module is loaded
     */
    String module();

    /**
     * Which rules may call these functions, by the alias of the rule's input.
     *
     * @param inputType the alias of a rule's input, such as {@code otlp} or {@code sw-native}
     * @return whether rules of that input may call these functions
     */
    boolean supportsInputType(String inputType);

    /**
     * Reach the services the functions need. Called once, after the provider's module started.
     *
     * @param moduleManager the module manager; null for the default registry a unit test compiles against, which
     *                      starts no module
     */
    default void bind(ModuleManager moduleManager) {
    }
}
