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

package org.apache.skywalking.oap.server.receiver.runtimerule.engine;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;

/**
 * Shared base contract for the per-call state object every {@link RuleEngine} phase receives.
 * Holds only DSL-agnostic services + the unified per-rule map; DSL-specific state
 * (per-key MAL applied artifacts, per-key LAL applied artifacts, etc.) lives on engine-specific
 * subtypes — {@code engine.mal.MalApplyContext}, {@code engine.lal.LalApplyContext}, future
 * {@code engine.oal.OalApplyContext} — that each engine constructs via its own
 * {@link RuleEngine#newApplyContext} factory.
 *
 * <p>This is a context object, not a service. The scheduler builds an
 * {@link ApplyInputs} record once per apply / unregister call and passes it to the engine; the
 * engine narrows it into its own context subtype, plugging in any DSL-specific state map
 * references it holds. Engines never hold long-lived references to a context — every phase
 * method takes one as a parameter and uses it transactionally.
 *
 * <p>Classloader retire / install is NOT exposed on the context. Engines reach the
 * {@link org.apache.skywalking.oap.server.core.classloader.DSLClassLoaderManager#INSTANCE}
 * singleton directly when they need to mint or drop a per-file loader; threading the manager
 * through every context would add coupling without value (lifetime is process-wide, not
 * per-call).
 */
public interface ApplyContext {
    /** For looking up ModelInstaller / MeterSystem during verify + cross-file ownership reads. */
    ModuleManager getModuleManager();

    /** Install policy for THIS apply / unregister call. */
    StorageManipulationOpt getStorageOpt();

    /** Best-effort alarm-window reset. Scheduler-owned; engines invoke at commit / unregister
     *  with the affected metric name set. The orchestrator may swap in a no-op resetter for
     *  update-path teardowns where the caller drives the alarm reset itself. */
    Consumer<Set<String>> getAlarmResetter();

    /** Unified per-key rule script map: content + {@link
     *  org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState} bundled into
     *  one {@link AppliedRuleScript}. Engines read prior content via {@code rules.get(key)
     *  != null ? rules.get(key).getContent() : null} and write on commit via
     *  {@code rules.compute(key, ...)}. The orchestrator owns state transitions; engines own
     *  content writes on commit. Both go through the same map under the per-file lock. */
    Map<String, AppliedRuleScript> getRules();
}
