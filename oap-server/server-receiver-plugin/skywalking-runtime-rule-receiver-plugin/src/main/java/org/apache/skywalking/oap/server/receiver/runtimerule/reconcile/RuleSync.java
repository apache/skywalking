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

package org.apache.skywalking.oap.server.receiver.runtimerule.reconcile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.metrics.LockMetrics;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;

/**
 * Periodic DB → local-state sync. Reads the current DAO state, diffs against the in-memory
 * snapshot, and drives apply / unregister / rehydrate for each (catalog, name) pair through
 * the orchestrators ({@link DSLRuntimeApply}, {@link DSLRuntimeUnregister}). DSL-agnostic —
 * does NOT hold engine-specific applied state ({@code appliedMal} / {@code appliedLal});
 * those live behind the engine boundary and the orchestrators consult them on the timer's
 * behalf.
 *
 * <p>The timer is the resilience boundary — per-rule failures are caught and logged so a
 * single bad bundle can't stall the rest of the convergence pass. The timer is also
 * idempotent: a skipped or partially-applied file gets re-attempted on the next interval.
 *
 * <p>Three sub-phases run in order:
 * <ol>
 *   <li><b>Apply loop</b> — iterate every DB row, classify and route through {@link
 *       DSLManager#applyOneRuleFile} (which delegates to {@link DSLRuntimeApply} or {@link
 *       DSLRuntimeUnregister} via the per-DSL drivers). Honours the per-tick
 *       {@link StorageManipulationOpt} and the marker-debt promotion (peer that was
 *       withoutSchemaChange is now main → re-fire under withSchemaChange).</li>
 *   <li><b>Gone-keys cleanup</b> — anything in the snapshot that's not in the DB and not
 *       static-shadowed gets {@link DSLRuntimeUnregister}'d. Snapshot removal is deferred
 *       past unregister so a transient teardown failure doesn't lose the retry.</li>
 *   <li><b>Static rehydrate</b> — {@link StaticRuleLoader#loadIfMissing} brings any
 *       {@code /delete}d static rule back online from disk content.</li>
 * </ol>
 *
 * <p><b>Why this class only reads {@code snapshot}.</b> The {@code snapshot} map carries the
 * scheduler-side metadata of every apply attempt: last contentHash, localState
 * (RUNNING/SUSPENDED/NOT_LOADED), suspendOrigin, applyError, timestamps. This is enough to
 * decide:
 * <ul>
 *   <li><b>Pre-compile short-circuit</b> — if {@code prev.contentHash == newHash} and the
 *       active/inactive status matches, skip the file entirely.</li>
 *   <li><b>Gone-keys</b> — bundles in {@code snapshot} but absent from the DB.</li>
 * </ul>
 * "Is this currently registered?" is an engine question; the orchestrators ask the engine
 * (via {@code engine.activeClaimsExcluding} / engine-internal applied maps) when they need it.
 */
@Slf4j
public final class RuleSync {

    private final ModuleManager moduleManager;
    private final LockMetrics lockMetrics;
    private final Map<String, AppliedRuleScript> rules;
    private final StaticRuleLoader staticRuleLoader;
    private final ApplyOneRuleFile applyOne;
    private final Unregister unregister;
    private final TickStorageOptPicker storageOptPicker;

    public RuleSync(final ModuleManager moduleManager,
                        final LockMetrics lockMetrics,
                        final Map<String, AppliedRuleScript> rules,
                        final StaticRuleLoader staticRuleLoader,
                        final ApplyOneRuleFile applyOne,
                        final Unregister unregister,
                        final TickStorageOptPicker storageOptPicker) {
        this.moduleManager = moduleManager;
        this.lockMetrics = lockMetrics;
        this.rules = rules;
        this.staticRuleLoader = staticRuleLoader;
        this.applyOne = applyOne;
        this.unregister = unregister;
        this.storageOptPicker = storageOptPicker;
    }

    /**
     * Run the full tick body once. {@code atBoot=true} on the synchronous first tick from
     * {@code RuntimeRuleModuleProvider.notifyAfterCompleted}; the storage-opt picker uses this
     * to choose {@code verifySchemaOnly} on no-init OAPs (fail boot if backend is not in shape).
     */
    public void runOnce(final boolean atBoot) {
        final RuntimeRuleManagementDAO dao;
        try {
            dao = moduleManager.find(StorageModule.NAME).provider()
                .getService(RuntimeRuleManagementDAO.class);
        } catch (final Throwable t) {
            log.warn("RuntimeRuleManagementDAO not available from the active storage module; "
                + "skipping tick", t);
            return;
        }
        final List<RuntimeRuleManagementDAO.RuntimeRuleFile> ruleFiles;
        try {
            ruleFiles = dao.getAll();
        } catch (final IOException e) {
            log.warn("failed to read runtime_rule files; next tick will retry", e);
            return;
        }
        ruleFiles.sort(Comparator
            .comparing(RuntimeRuleManagementDAO.RuntimeRuleFile::getCatalog)
            .thenComparing(RuntimeRuleManagementDAO.RuntimeRuleFile::getName));

        // Capture the storage policy ONCE for the whole tick. Re-querying mid-tick is no
        // more authoritative than the first read.
        final StorageManipulationOpt tickOpt = storageOptPicker.pick(atBoot);
        final Set<String> seenKeys = new HashSet<>();
        final long nowMs = System.currentTimeMillis();

        for (final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile : ruleFiles) {
            applyOneFromDb(ruleFile, nowMs, tickOpt, seenKeys);
        }

        cleanupGoneKeys(seenKeys, tickOpt);

        staticRuleLoader.loadIfMissing(seenKeys, nowMs, tickOpt);
    }

    /** Per-row apply. Short-circuits when {@code dbActive == localActive} and the content
     *  hash matches; otherwise drives the per-file apply path. */
    private void applyOneFromDb(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                                final long nowMs, final StorageManipulationOpt tickOpt,
                                final Set<String> seenKeys) {
        final String key = DSLScriptKey.key(ruleFile.getCatalog(), ruleFile.getName());
        seenKeys.add(key);
        final String newHash = ContentHash.sha256Hex(ruleFile.getContent());
        final AppliedRuleScript prevScript = rules.get(key);
        final DSLRuntimeState prev = prevScript == null ? null : prevScript.getState();
        final boolean dbActive = !"INACTIVE".equals(ruleFile.getStatus());
        final boolean localEffectivelyActive = prev != null
            && prev.getLocalState() != DSLRuntimeState.LocalState.NOT_LOADED;
        if (prev != null
                && dbActive == localEffectivelyActive
                && Objects.equals(prev.getContentHash(), newHash)) {
            return;
        }
        final ReentrantLock perFile = AppliedRuleScript.lockFor(rules,
            ruleFile.getCatalog(), ruleFile.getName());
        if (!lockMetrics.tryAcquireForSyncTimer(perFile, ruleFile.getCatalog(), ruleFile.getName())) {
            return;
        }
        try (HistogramMetrics.Timer ignored = lockMetrics.startSyncTimerHoldTimer()) {
            try {
                applyOne.applyOneRuleFile(ruleFile, newHash, prev, nowMs, key, false, tickOpt);
            } catch (final Throwable t) {
                // Per-rule isolation: one failing apply must not abort the tick.
                log.warn("runtime-rule dslManager: apply path threw for {}/{}; tick continues "
                    + "with other rules, next tick will retry",
                    ruleFile.getCatalog(), ruleFile.getName(), t);
            }
        } finally {
            perFile.unlock();
        }
    }

    /** Tear down bundles whose DB row is gone. */
    private void cleanupGoneKeys(final Set<String> seenKeys, final StorageManipulationOpt tickOpt) {
        final List<String> removedKeys = new ArrayList<>();
        for (final String existing : rules.keySet()) {
            if (seenKeys.contains(existing)) {
                continue;
            }
            // Skip boot-seeded bundled-only entries — DSLRuntimeState is null when the
            // entry was created by the StaticRuleLoader and the operator hasn't touched it
            // (no /addOrUpdate, no /inactivate). For those entries the DB never carried a
            // row, so its absence is not a "removed" signal. Operator-touched entries
            // (state != null) get teared down + bundled fall-over reload below.
            final AppliedRuleScript script = rules.get(existing);
            if (script != null && script.getState() == null) {
                continue;
            }
            removedKeys.add(existing);
        }
        for (final String gone : removedKeys) {
            final String[] parts = gone.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            final ReentrantLock perFile = AppliedRuleScript.lockFor(rules, parts[0], parts[1]);
            if (!lockMetrics.tryAcquireForSyncTimer(perFile, parts[0], parts[1])) {
                continue;
            }
            try (HistogramMetrics.Timer ignored = lockMetrics.startSyncTimerHoldTimer()) {
                final AppliedRuleScript prevScript = rules.get(gone);
                final DSLRuntimeState prev = prevScript == null ? null : prevScript.getState();
                log.info("runtime-rule dslManager: rule file deleted {} (last hash={})",
                    gone, prev == null ? "?" : DSLScriptKey.shortHash(prev.getContentHash()));
                if (prevScript == null) {
                    rules.remove(gone);
                    continue;
                }
                // Map removal deferred to AFTER unregister succeeds. If unregister throws,
                // the entry stays so the next tick retries via the same removedKeys path.
                try {
                    // unregisterBundle with installBundledAfter=true: tear down the removed
                    // runtime registrations, then if the rule has a bundled twin install
                    // it fresh via a bundled: loader. Returns true when a bundled fall-over
                    // landed — in that case we KEEP the rules entry (installBundled re-seeded
                    // it as a bundled-served entry, equivalent to a boot-seeded one).
                    // Otherwise the entry is fully gone and we remove it.
                    //
                    // Storage opt: ALWAYS withoutSchemaChange for the unregister leg. The
                    // new /delete contract says default-mode (no bundled twin) leaves the
                    // backend as inert artefact, and the only schema-changing /delete path
                    // (revertToBundled) drives the schema mutation through the apply
                    // pipeline at REST time, not here. Passing the tickOpt would let a
                    // peer-promoted-to-main node drop the backend during gone-keys cleanup,
                    // contradicting the operator-facing contract.
                    final boolean bundledReloaded = unregister.unregisterBundle(
                        parts[0], parts[1], true, StorageManipulationOpt.withoutSchemaChange(), true);
                    if (!bundledReloaded) {
                        rules.remove(gone);
                    }
                } catch (final Throwable t) {
                    log.warn("runtime-rule dslManager: teardown threw for removed rule {}; "
                        + "rule entry retained — next tick will retry", gone, t);
                }
            } finally {
                perFile.unlock();
            }
        }
    }

    /** Functional handle for per-file apply — supplied by DSLManager. */
    @FunctionalInterface
    public interface ApplyOneRuleFile {
        void applyOneRuleFile(RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile, String newHash,
                              DSLRuntimeState prev, long nowMs, String key, boolean deferCommit,
                              StorageManipulationOpt storageOpt);
    }

    /** Functional handle for unregister-bundle. */
    @FunctionalInterface
    public interface Unregister {
        boolean unregisterBundle(String catalog, String name, boolean invokeAlarmOnRemove,
                                 StorageManipulationOpt storageOpt, boolean installBundledAfter);
    }

    /** Functional handle for per-tick storage-opt picking (init / no-init / main vs peer). */
    @FunctionalInterface
    public interface TickStorageOptPicker {
        StorageManipulationOpt pick(boolean atBoot);
    }
}
