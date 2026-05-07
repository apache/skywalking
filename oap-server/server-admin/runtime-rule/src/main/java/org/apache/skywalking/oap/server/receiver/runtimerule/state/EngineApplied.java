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

package org.apache.skywalking.oap.server.receiver.runtimerule.state;

import java.util.Set;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * Engine-opaque per-rule applied artefact slot on {@link AppliedRuleScript}. Every engine's
 * concrete {@code Applied} type ({@code MalFileApplier.Applied},
 * {@code LalFileApplier.Applied}) implements this interface, so cross-DSL code — the
 * Suspend/Resume coordinator, cross-file ownership guard, classloader graveyard hand-off —
 * can drive dispatch and claim queries polymorphically without switching on MAL vs LAL.
 *
 * <p>Engines cast back to their richer concrete type whenever they need the full applied
 * shape (e.g. MAL's compile path needs the {@code Rule} + {@code MetricConvert} that the
 * generic interface deliberately doesn't expose).
 *
 * <p>Two design constraints worth flagging:
 * <ul>
 *   <li><b>No held module references.</b> {@link #suspendDispatch}/{@link #resumeDispatch}
 *       receive a {@link ModuleManager} on each call so the {@code Applied} stays a plain
 *       data carrier and survives module reloads / test harness rewires that swap
 *       MeterSystem or LAL Factory under it.</li>
 *   <li><b>Empty &ne; unsupported.</b> Engines without alarm semantics (LAL) return
 *       {@link java.util.Collections#emptySet()} from {@link #alarmResetTargets()}; the
 *       coordinator interprets empty as "nothing to reset" rather than "this engine doesn't
 *       support alarms" — both readings drive the same no-op.</li>
 * </ul>
 */
public interface EngineApplied {

    /**
     * Park dispatch / mark this bundle as suspended for sample handling. For MAL: route
     * {@link org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem#suspendDispatch}
     * across the registered metric names. For LAL: drive
     * {@code LogFilterListener.Factory.suspend} on the registered rule keys.
     *
     * @param moduleManager looked up on each call to avoid holding a stale reference
     * @return number of dispatch primitives successfully paused (metric names for MAL,
     *         rule keys for LAL); {@code 0} if the engine's runtime services aren't
     *         resolvable (early boot, embedded test topology) — caller treats as a no-op.
     */
    int suspendDispatch(ModuleManager moduleManager);

    /** Inverse of {@link #suspendDispatch}: resume dispatch for this bundle, return the
     *  count of primitives un-parked. */
    int resumeDispatch(ModuleManager moduleManager);

    /**
     * Cluster-wide unique keys this bundle claims on the active side — metric names (MAL)
     * or {@code (layer, ruleName)} keys (LAL). The cross-file ownership guard reads this
     * to detect collisions: another active file claiming the same key is a config error
     * the operator must resolve via {@code /inactivate} or {@code /delete} on one of them.
     *
     * @return immutable, possibly empty set of claimed keys
     */
    Set<String> claimedKeys();

    /**
     * Per-file classloader that owns generated DSL classes for this bundle, or {@code null}
     * for bundles applied without a dedicated loader (boot-seeded static rules; legacy
     * 2-arg LAL apply entry point). Cross-DSL teardown reads this to retire the loader
     * through {@code ClassLoaderGc} so GC of the generated classes is observable.
     *
     * <p>Returned as {@link Object} so the {@code state} package doesn't need to import the
     * concrete {@code RuleClassLoader} type from {@code classloader}; teardown code casts
     * before passing to {@code ClassLoaderGc.retire}.
     */
    Object classLoader();

    /**
     * Metric names whose alarm window the engine wants reset on tear-down. MAL returns the
     * metric set the bundle owned; LAL returns an empty set (alarm windows key off metric
     * names, not log rules).
     *
     * @return immutable, possibly empty set of metric names
     */
    Set<String> alarmResetTargets();
}
