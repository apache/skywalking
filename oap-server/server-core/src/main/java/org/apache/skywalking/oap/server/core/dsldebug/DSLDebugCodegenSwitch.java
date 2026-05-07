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

package org.apache.skywalking.oap.server.core.dsldebug;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Process-wide boolean every DSL code generator (MAL / LAL / OAL) consults
 * before emitting probe call sites or {@link GateHolder} fields. Defaults to
 * {@code false} — debug-probe injection is OFF unless the {@code dsl-debugging}
 * module is enabled <i>and</i> its {@code injectionEnabled} flag is true.
 *
 * <h2>Why a global switch instead of a generator constructor arg</h2>
 * Code generators are constructed in many places — runtime-rule applies,
 * static loaders, OAL boot. Threading a constructor flag through every
 * site multiplies the diff. The switch is process-scoped and read once per
 * compile; the dsl-debugging module's {@code prepare()} flips it on at
 * startup before any code generator runs (provider {@code prepare()} fires
 * before any provider {@code start()}).
 *
 * <h2>Idle path when injection is OFF</h2>
 * The generator emits zero probe lines and zero holder field — the compiled
 * MAL / LAL / OAL bytecode is byte-identical to a build without SWIP-13.
 * Sessions can't be created (the REST path returns 503), there are no
 * call sites that could fire, and the generated class doesn't reference
 * {@code GateHolder} at all.
 *
 * <h2>Boot-time only</h2>
 * The flag is read at every codegen call. dsl-debugging flips it during
 * {@code prepare()}. Flipping it back at runtime would require regenerating
 * every compiled rule; the operator must restart OAP for a flag change to
 * take effect.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DSLDebugCodegenSwitch {

    private static final AtomicBoolean INJECTION_ENABLED = new AtomicBoolean(false);

    /** Called by dsl-debugging.prepare() when its config has injectionEnabled=true. */
    public static void enableInjection() {
        INJECTION_ENABLED.set(true);
    }

    /** Reset to default (off). Used by tests after install/teardown. */
    public static void resetInjection() {
        INJECTION_ENABLED.set(false);
    }

    /** {@code true} when DSL code generators should emit probe call sites. */
    public static boolean isInjectionEnabled() {
        return INJECTION_ENABLED.get();
    }
}
