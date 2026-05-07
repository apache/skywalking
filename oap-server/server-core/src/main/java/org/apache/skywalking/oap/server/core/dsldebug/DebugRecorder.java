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

/**
 * Marker interface for one debug session's binding into a rule's
 * {@link GateHolder}. The hot-path probe code only sees this marker — the
 * per-DSL append methods live on extension interfaces in each analyzer
 * module ({@code MALDebugRecorder} in {@code analyzer/meter-analyzer},
 * {@code LALDebugRecorder} in {@code analyzer/log-analyzer},
 * {@code OALDebugRecorder} back in {@code server-core} once phase 3 lands).
 *
 * <p>The probe class for a given DSL downcasts to its own extension
 * interface inside the per-recorder fan-out loop. JIT specialises the cast
 * after warm-up, and keeping the marker DSL-agnostic lets
 * {@link GateHolder#recorders} stay one typed array regardless of which
 * DSL the rule belongs to.
 *
 * <p>Implementations live in {@code server-admin/dsl-debugging} (one
 * concrete class per DSL — {@code MALDebugRecorderImpl} etc.) — that module
 * already depends on every analyzer module so it can construct any flavor.
 */
public interface DebugRecorder {

    /** The session this recorder appends to. Stable for the recorder's lifetime. */
    String sessionId();

    /** The rule this recorder is bound to. Stable for the recorder's lifetime. */
    RuleKey ruleKey();

    /**
     * Narrow gate consulted by the session registry before binding the
     * recorder to a holder. Implementations typically compare against
     * the recorder's own {@link #ruleKey()} but the matching is left to
     * the impl so future per-rule-set sessions (covering more than one
     * specific rule) can be added without touching the install path.
     */
    boolean matches(RuleKey candidate);

    /**
     * {@code true} once the session has hit its record cap; further
     * probe appends MUST short-circuit on the probe side so they don't
     * spend CPU serialising a payload the recorder will discard.
     */
    boolean isCaptured();
}
