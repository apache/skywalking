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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.receiver.runtimerule.metrics.LockMetrics;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmKernelService;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.receiver.runtimerule.cluster.MainRouter;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import org.apache.skywalking.oap.server.receiver.runtimerule.apply.LalFileApplier;
import org.apache.skywalking.oap.server.receiver.runtimerule.apply.MalFileApplier;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.Classification;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.RuleEngineRegistry;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.lal.LalRuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.engine.mal.MalRuleEngine;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;

/**
 * Local per-node state owner + periodic convergence driver for runtime MAL / LAL rule
 * bundles. The full architecture (workflows, REST sequence, tick / self-heal diagrams,
 * storage-policy split, lock acquisition policy, failure model) lives in the design doc:
 * {@code docs/en/concepts-and-designs/runtime-rule-hot-update.md}. This Javadoc covers
 * only what's needed to read the code in this class.
 *
 * <h2>State owned</h2>
 * <p>All keyed by {@code "catalog:name"}:
 * <ul>
 *   <li>{@link #rules} — unified per-key {@link AppliedRuleScript}: catalog + name + last
 *       successfully-applied raw YAML + authoritative {@link DSLRuntimeState} (returned by
 *       {@code /list}, carries {@link DSLRuntimeState.SuspendOrigin SuspendOrigin}). One map
 *       under one per-file lock; all per-file operations read or replace one entry instead
 *       of coordinating across parallel maps.</li>
 *   <li>Engine-applied artefacts — live on {@link AppliedRuleScript#getApplied} as an
 *       {@link org.apache.skywalking.oap.server.receiver.runtimerule.state.EngineApplied}.
 *       Engines write on commit, read on compile/unregister; cross-DSL code (Suspend/Resume
 *       coordinator, ownership guard) drives the polymorphic interface without switching
 *       on MAL vs LAL.</li>
 *   <li>{@link StructuralCommitCoordinator} pending-commits stash — structural MAL commits
 *       verified but waiting on row-persist; drained by
 *       {@link StructuralCommitCoordinator#finalizeCommit} /
 *       {@link StructuralCommitCoordinator#discardCommit}.</li>
 * </ul>
 *
 * <h2>Locking</h2>
 * <p>The per-file {@link ReentrantLock} on each {@link AppliedRuleScript} is the outermost
 * ordering primitive. Every public entry point that mutates a {@code (catalog, name)}
 * acquires it; the REST handler wraps the whole workflow in the same lock. Cross-file
 * edits run concurrently. MeterSystem and the LAL factory handle their own internal
 * locking, so this lock only needs to protect this class's state plus the structural-
 * commit stash. Acquisition policy: REST → {@code tryLock(REST_LOCK_TIMEOUT_MS)}, 409
 * on timeout. Tick → {@code tryLock()} no-wait, skip-and-retry-next-tick on contention.
 * Internal coordinator methods → blocking {@code lock()} (operations are short).
 *
 * <h2>Source of truth</h2>
 * <p>The persisted runtime-rule entry (BanyanDB Property / ES Document / JDBC Row) is
 * authoritative cluster-wide. This class's in-memory maps are the local projection
 * converged toward that source on every {@link #tick}. The reconcile is best-effort —
 * {@code tick()} never throws out; per-file failures are logged and retried on the next
 * iteration.
 *
 * <h2>Single-main routing</h2>
 * <p>Each {@code (catalog, name)} routes to a single deterministic main (see
 * {@code MainRouter}). The main is the only node that self-suspends, applies, persists,
 * finalizes / discards. Peers receive {@code Suspend} / {@code Resume} RPCs. Routing
 * conflicts ({@link SuspendResult#REJECTED_ORIGIN_CONFLICT}) surface as 409 to the
 * operator. The REST handler forwards non-main requests to the resolved main; 421 fires
 * only when a forwarded request arrives at a node that itself doesn't believe it's main
 * (split cluster view). The tick picks its storage opt via {@link #tickStorageOpt(boolean)};
 * the per-endpoint REST opts are routed by the handler ({@code /addOrUpdate} →
 * {@code fullInstall}, {@code /inactivate} → {@code localCacheOnly}, {@code /delete} →
 * dedicated {@link DSLRuntimeDelete} path).
 */
@Slf4j
public final class DSLManager {

    /**
     * Unified per-rule map: each entry is a single {@link AppliedRuleScript} carrying the
     * raw YAML last successfully applied + the authoritative {@link DSLRuntimeState}.
     * {@code AppliedRuleScript} is immutable; updates produce a new instance via the
     * {@code with*} builders, so a {@link java.util.concurrent.ConcurrentMap#compute compute}
     * call on this map gives atomic per-key transitions without an external lock.
     *
     * <p>Replaces the historical pair of parallel maps ({@code snapshot}, {@code appliedContent})
     * — every per-file operation (classify, apply, unregister, suspend, resume, persist,
     * {@code /list}) now reads or replaces one entry on this single map instead of
     * coordinating across two. The per-file lock orders writes that must be atomic w.r.t.
     * each other (e.g. an engine's commit content-write + the orchestrator's snapshot
     * state-write); inside that lock, individual {@code rules.compute} calls are themselves
     * already atomic.
     */
    @Getter
    private final Map<String, AppliedRuleScript> rules = new ConcurrentHashMap<>();

    private final ModuleManager moduleManager;
    /** SELF / PEER / BOTH origin transitions + dispatch park/unpark + self-heal sweep.
     *  Exposed via {@code @Getter} so callers (REST handler, cluster RPC handler) can
     *  reach Suspend/Resume directly without DSLManager carrying pass-through wrappers. */
    @Getter
    private final SuspendResumeCoordinator suspendCoord;
    /** REST 2-PC coordinator: stash / finalize / discard pending commits + the
     *  destructive commit tail shared by tick + REST. Exposed via {@code @Getter}. */
    @Getter
    private final StructuralCommitCoordinator commitCoord;

    /**
     * Elapsed time a bundle can stay in {@link DSLRuntimeState.LocalState#SUSPENDED} with the DB
     * content unchanged before the dslManager unsuspends it to its retained old content. 60 s
     * — the 60 s budget exceeds dslManager tick + ES refresh + storage replica lag + RPC jitter.
     */
    @Getter
    private final long selfHealThresholdMs;

    /** Lock-observability wrapper. Owned by the DSLManager; the REST handler borrows via
     *  {@link #getLockMetrics()} so every lock acquire path reports to the same histograms. */
    @Getter
    private final LockMetrics lockMetrics;

    /** Bundle teardown primitive — see {@link DSLRuntimeUnregister}'s class Javadoc. */
    private final DSLRuntimeUnregister dslRuntimeUnregister;

    /** Apply orchestrator — symmetric to {@link DSLRuntimeUnregister}. Drives the engine
     *  phase pipeline (compile → fireSchemaChanges → verify → commit | rollback) for every
     *  classify result that warrants applying. */
    private final DSLRuntimeApply dslRuntimeApply;

    /** Destructive {@code /delete} pipeline. Re-registers prototypes locally then tears down
     *  under fullInstall so the backend cascade fires before the DAO row is deleted.
     *  Exposed via {@code @Getter}. */
    @Getter
    private final DSLRuntimeDelete dslRuntimeDelete;

    /** Boot-time seed + tick-time rehydrate of static rules. Exposed via {@code @Getter}
     *  so the module provider can drive the boot-time load directly. */
    @Getter
    private final StaticRuleLoader staticRuleLoader;

    /** One-tick body — DB diff + apply + gone-keys cleanup + static rehydrate. */
    private final RuleSync ruleSync;

    /** Catalog → engine lookup. Built once here from the per-DSL maps the scheduler owns;
     *  every apply / unregister path routes through this registry to the right engine. */
    @Getter
    private final RuleEngineRegistry engineRegistry;

    public DSLManager(final ModuleManager moduleManager,
                      final long selfHealThresholdMs) {
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
        this.engineRegistry = new RuleEngineRegistry();
        this.engineRegistry.register(new MalRuleEngine(this.rules, this.moduleManager));
        this.engineRegistry.register(new LalRuleEngine(this.rules, this.moduleManager));
        this.selfHealThresholdMs = selfHealThresholdMs;
        this.lockMetrics =
            new LockMetrics(moduleManager);
        this.suspendCoord = new SuspendResumeCoordinator(
            this.rules, this.moduleManager, this.selfHealThresholdMs,
            this::readCurrentDbRules
        );
        this.dslRuntimeApply = new DSLRuntimeApply(this.engineRegistry);
        this.commitCoord = new StructuralCommitCoordinator(
            this.rules, this.dslRuntimeApply, this.suspendCoord
        );
        this.dslRuntimeUnregister = new DSLRuntimeUnregister(
            this.rules, this.moduleManager,
            this::invokeAlarmReset, this.engineRegistry
        );
        this.dslRuntimeDelete = new DSLRuntimeDelete(
            this.engineRegistry, this.moduleManager,
            this.rules, this::invokeAlarmReset
        );
        this.staticRuleLoader = new StaticRuleLoader(
            this.engineRegistry, this.rules,
            this.lockMetrics, this::applyOneRuleFile
        );
        this.ruleSync = new RuleSync(
            this.moduleManager, this.lockMetrics, this.rules,
            this.staticRuleLoader,
            this::applyOneRuleFile, this.dslRuntimeUnregister::unregister, this::tickStorageOpt
        );
    }

    /**
     * Runs on the single-threaded dslManager executor scheduled by {@code RuntimeRuleModuleProvider}.
     * Never throws — the scheduler swallows the exception anyway, so errors are logged and the
     * next tick proceeds from whatever state the last one left.
     */
    public void tick() {
        tick(false);
    }

    /**
     * Variant invoked once at boot from {@code RuntimeRuleModuleProvider.notifyAfterCompleted}
     * with {@code atBoot=true}. The boot pass on a no-init OAP picks
     * {@link StorageManipulationOpt#localCacheVerify()} so missing or shape-mismatched
     * backend schema fails the bootstrap (k8s pod backloop) instead of silently
     * proceeding. The scheduled executor calls the no-arg overload so subsequent ticks
     * stay on the lenient {@code localCacheOnly} retry path.
     *
     * <p>Boot semantics are scoped to no-init mode only — init-mode OAPs continue to
     * pick {@link StorageManipulationOpt#createIfAbsent()} (boot creates), and
     * default-mode OAPs continue to pick by cluster main-ness.
     */
    public void tick(final boolean atBoot) {
        try {
            sweepSuspendedForSelfHeal();
            applyDeltasFromDatabase(atBoot);
        } catch (final Throwable t) {
            if (atBoot) {
                // Re-throw so the bootstrap aborts; pod backloops on k8s, operator sees
                // the failure instead of silently starting against an unprepared backend.
                throw new RuntimeException("runtime-rule dslManager boot pass failed", t);
            }
            log.error("runtime-rule dslManager tick failed; will retry on next interval", t);
        }
    }

    // Boot-time static-rule seeding lives on {@link StaticRuleLoader#loadAll}; callers reach
    // it via {@link #getStaticRuleLoader()}.

    /**
     * Recover bundles stuck in {@link DSLRuntimeState.LocalState#SUSPENDED} by a peer-origin
     * Suspend whose main crashed before sending Resume. Only acts on PEER-only origins —
     * SELF origin is the local REST apply's own bookkeeping, and BOTH origin indicates a
     * SELF apply is in flight alongside a PEER broadcast (the local apply's finalize /
     * discard path is the recovery, not self-heal).
     *
     * <p>Bundles whose DB content HAS advanced since the suspend are left for
     * {@link #applyDeltasFromDatabase(boolean)} to pick up via the normal content-hash diff — those
     * are the "main node succeeded, we're catching up" path. We deliberately do not flip
     * those back to RUNNING here: the correct handlers for the new content haven't been
     * installed yet, so a premature flip would resume dispatch against a bundle whose schema
     * may already have moved.
     *
     * <p>Most main-side failures now clear peer-side SUSPENDED within an RPC round-trip via
     * the Resume broadcast, so this sweep is a backstop for the narrow case where the main
     * crashes after Suspend but before Resume. Self-heal threshold can be tuned via
     * {@link #selfHealThresholdMs}.
     *
     * <p>Time arithmetic uses {@link System#nanoTime()} via
     * {@link DSLRuntimeState#getEnteredCurrentStateAtNanos()} rather than wall clock. An NTP
     * jump or backwards wall-clock tick on the host would otherwise either delay a
     * legitimate self-heal indefinitely or fire one prematurely. Wall-clock stamps stay on
     * DSLRuntimeState for operator readability on {@code /list}; threshold math reads the
     * monotonic side.
     */
    void sweepSuspendedForSelfHeal() {
        suspendCoord.sweepSuspendedForSelfHeal();
    }

    /**
     * One DAO fetch per tick. Returns a map keyed by {@code "catalog:name"} of every persisted
     * runtime rule (BanyanDB property / ES document / JDBC row — same logical entity, three
     * shapes), or {@code null} when the DAO isn't resolvable (early boot, some embedded test
     * topologies). The caller treats {@code null} as "skip self-heal this tick" — a correct-
     * but-conservative default when we can't observe the persisted state.
     *
     * <p>The full {@link RuntimeRuleManagementDAO.RuntimeRuleFile} is captured (not just the
     * content hash) so self-heal can distinguish "content unchanged + ACTIVE" (the pre-
     * suspend bundle is still authoritative → resume) from "content unchanged + INACTIVE"
     * (the operator deliberately inactivated → leave SUSPENDED for the delta-apply path to
     * tear down).
     */
    private Map<String, RuntimeRuleManagementDAO.RuntimeRuleFile> readCurrentDbRules() {
        final RuntimeRuleManagementDAO dao;
        try {
            dao = moduleManager.find(StorageModule.NAME).provider()
                               .getService(RuntimeRuleManagementDAO.class);
        } catch (final Throwable t) {
            return null;
        }
        final Map<String, RuntimeRuleManagementDAO.RuntimeRuleFile> rules = new HashMap<>();
        try {
            for (final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile : dao.getAll()) {
                rules.put(DSLScriptKey.key(ruleFile.getCatalog(), ruleFile.getName()), ruleFile);
            }
        } catch (final IOException ioe) {
            log.debug("runtime-rule self-heal: DAO fetch failed this tick ({})", ioe.getMessage());
            return null;
        }
        return rules;
    }

    /** Run one tick — DB diff + apply + gone-keys cleanup + static rehydrate. Delegates to
     *  {@link RuleSync}. */
    private void applyDeltasFromDatabase(final boolean atBoot) {
        ruleSync.runOnce(atBoot);
    }

    /**
     * Synchronously apply one rule file on this node. Used by the REST handler's sync path so
     * {@code /addOrUpdate} can return a precise {@code structural_applied} / {@code
     * ddl_verify_failed} response instead of always 202. Acquires the per-file lock, runs
     * the same {@link #applyOneRuleFile} path the 30-second tick uses, and reports the resulting
     * {@link DSLRuntimeState} so the caller can distinguish success from
     * {@code applyError}-annotated degradation.
     *
     * <p>Thread-safe with the dslManager tick: the per-file lock from
     * {@link AppliedRuleScript#lockFor} serializes both paths on the same
     * {@code (catalog, name)}. Other files' ticks run concurrently because the tick acquires
     * per-file locks the same way.
     */
    public DSLRuntimeState applyNowForRuleFile(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile) {
        return applyNowForRuleFile(ruleFile, false);
    }

    /**
     * Synchronous apply overload that supports deferred commit. When
     * {@code deferCommit=true} and the apply path reaches a successful MAL STRUCTURAL/NEW
     * commit point, the destructive tail (drop removedMetrics, swap the engine-applied
     * artefacts, retire old loader, alarm reset, advance snapshot) is stashed in
     * {@link StructuralCommitCoordinator}'s pending-commits map rather than applied
     * inline. The caller must then invoke {@link StructuralCommitCoordinator#finalizeCommit}
     * or {@link StructuralCommitCoordinator#discardCommit} to drain the stash.
     *
     * <p>Used by the REST handler's STRUCTURAL path so row-persist failure can revert
     * to the pre-apply state — including restoring metrics that would otherwise have been
     * dropped by the commit — instead of leaving the node diverged from cluster state.
     * Other call sites (the dslManager tick) pass {@code deferCommit=false} and get the
     * inline commit they've always had.
     */
    public DSLRuntimeState applyNowForRuleFile(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                                            final boolean deferCommit) {
        return applyNowForRuleFile(ruleFile, deferCommit, StorageManipulationOpt.fullInstall());
    }

    /**
     * Storage-opt overload of {@link #applyNowForRuleFile(RuntimeRuleManagementDAO.RuntimeRuleFile, boolean)}.
     *
     * <p>The REST {@code /inactivate} path passes {@link StorageManipulationOpt#localCacheOnly()}
     * here so the OAP-internal teardown — MeterSystem prototypes, MetricsStreamProcessor
     * entry / persistent workers, BatchQueue handlers, retired RuleClassLoader — runs to
     * completion while the backend's measure / table / index, and the data already stored
     * under the pre-inactivate metric, are left intact. {@code /delete} (and STRUCTURAL
     * {@code /addOrUpdate} that drops shape-broken metrics) keeps {@code fullInstall()} so
     * the destructive cascade reaches the backend as before.
     *
     * <p>Other call sites should keep using the no-opt overload above so the documented
     * "REST path = fullInstall, peer tick = localCacheOnly" routing rule is unchanged.
     */
    public DSLRuntimeState applyNowForRuleFile(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                                            final boolean deferCommit,
                                            final StorageManipulationOpt storageOpt) {
        final String key = DSLScriptKey.key(ruleFile.getCatalog(), ruleFile.getName());
        final String newHash = ContentHash.sha256Hex(ruleFile.getContent());
        final AppliedRuleScript prevScript = rules.get(key);
        final DSLRuntimeState prev = prevScript == null ? null : prevScript.getState();
        final long nowMs = System.currentTimeMillis();
        final ReentrantLock perFile = AppliedRuleScript.lockFor(rules,
            ruleFile.getCatalog(), ruleFile.getName());
        perFile.lock();
        try {
            applyOneRuleFile(ruleFile, newHash, prev, nowMs, key, deferCommit, storageOpt);
            final AppliedRuleScript after = rules.get(key);
            return after == null ? null : after.getState();
        } finally {
            perFile.unlock();
        }
    }

    /**
     * Apply one rule file's state to this node under the per-file lock already held by the
     * caller. Dispatches on catalog: MAL catalogs ({@code otel-rules}, {@code log-mal-rules})
     * parse + register via {@link MalFileApplier}; LAL goes through {@link LalFileApplier}
     * with the same classify → compile → swap structure. INACTIVE status routes to
     * {@code dslRuntimeUnregister.unregister}. Both paths drive structural commits via the
     * {@link PendingApplyCommit} stash so a persist failure can roll back cleanly.
     */
    private void applyOneRuleFile(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                                   final String newHash, final DSLRuntimeState prev,
                                   final long nowMs, final String key,
                                   final boolean deferCommit,
                                   final StorageManipulationOpt storageOpt) {
        final boolean wasSuspended = prev != null
            && prev.getLocalState() == DSLRuntimeState.LocalState.SUSPENDED;
        final boolean isInactive = "INACTIVE".equals(ruleFile.getStatus());

        if (engineRegistry.forCatalog(ruleFile.getCatalog()) == null) {
            // Catalog has no engine registered with the runtime-rule receiver. Surface a warn
            // so the operator sees the misroute and skip. The seed loop already drops these —
            // this branch protects the tick / on-demand paths from a row that somehow reached
            // them, e.g. via an explicit DAO write that named a catalog the runtime-rule
            // receiver does not own.
            log.warn("runtime-rule dslManager: ignoring rule {}/{} — catalog '{}' has no "
                + "engine registered (recognised: {})",
                ruleFile.getCatalog(), ruleFile.getName(), ruleFile.getCatalog(),
                this.engineRegistry.engines());
            return;
        }

        // DSL-agnostic apply driver. The scheduler does classify routing, ownership guard,
        // snapshot transitions, and 2-PC stash bookkeeping; everything DSL-specific lives
        // behind the engine SPI (engine.classify, engine.claimedKeys, engine.activeClaimsExcluding,
        // engine.compile/verify/commit/rollback via DSLRuntimeApply).
        handleApply(ruleFile, key, prev, wasSuspended, isInactive, newHash, nowMs,
            deferCommit, storageOpt);
    }

    /**
     * DSL-agnostic apply driver. Routes classify outcomes, runs the cross-file ownership
     * guard, drives the engine pipeline through {@link DSLRuntimeApply}, and stashes /
     * commits via {@link StructuralCommitCoordinator}. Adding a new DSL needs zero edits
     * here — register an engine with {@link RuleEngineRegistry} and the driver picks it up.
     *
     * <pre>
     *   classify
     *     ├─ INACTIVE   → unregisterBundle  + tombstone snapshot
     *     ├─ NO_CHANGE  → snapshot hash refresh
     *     └─ NEW / FILTER_ONLY / STRUCTURAL → continue:
     *
     *   ownership guard (engine.claimedKeys / engine.activeClaimsExcluding + DAO INACTIVE)
     *     └─ conflict → snapshot error stamp
     *
     *   dslRuntimeApply.compileAndVerify
     *     ├─ COMPILE_FAILED → snapshot error stamp (engine self-rolled-back)
     *     ├─ VERIFY_FAILED  → snapshot error stamp (engine self-rolled-back)
     *     └─ READY_TO_COMMIT → wrap in PendingApplyCommit:
     *         ├─ deferCommit → commitCoord.stash    (REST 2-PC, drained on persist outcome)
     *         └─ inline      → commitCoord.commitInline (tick / FILTER_ONLY)
     * </pre>
     */
    private void handleApply(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                             final String key, final DSLRuntimeState prev,
                             final boolean wasSuspended, final boolean isInactive,
                             final String newHash, final long nowMs,
                             final boolean deferCommit,
                             final StorageManipulationOpt storageOpt) {
        final RuleEngine<?> engine = engineRegistry.forCatalog(ruleFile.getCatalog());
        final AppliedRuleScript priorScript = rules.get(key);
        final String priorContent = priorScript == null ? null : priorScript.getContent();

        // 1. Classify (folds isInactive in).
        final Classification cl;
        try {
            cl = engine.classify(priorContent, ruleFile.getContent(), isInactive);
        } catch (final RuntimeException ce) {
            log.error("runtime-rule dslManager: classify FAILED for {}/{}: {}",
                ruleFile.getCatalog(), ruleFile.getName(), ce.getMessage(), ce);
            stampClassifyError(ruleFile, key, prev, nowMs, ce.getMessage());
            return;
        }
        if (log.isInfoEnabled()) {
            log.info("runtime-rule dslManager: classification for {}/{} = {}",
                ruleFile.getCatalog(), ruleFile.getName(), cl);
        }

        // 2. INACTIVE — full tear-down via DSLRuntimeUnregister + tombstone state. Static
        //    fall-over does NOT fire here: the operator's /inactivate intent is "off" and
        //    bringing the bundled twin back instantly would defeat soft-pause. To restore
        //    bundled, the operator runs /delete (drops the row, gone-keys path reloads).
        if (cl == Classification.INACTIVE) {
            dslRuntimeUnregister.unregister(
                ruleFile.getCatalog(), ruleFile.getName(), true, storageOpt);
            log.info("runtime-rule dslManager: {}/{} INACTIVE — unregistered",
                ruleFile.getCatalog(), ruleFile.getName());
            // Clear lastApplyError explicitly: withContentHash no-ops when the hash hasn't
            // moved (the usual /inactivate case where content stays, status flips), so a
            // stale error from a prior failed apply would otherwise leak via /list.
            final DSLRuntimeState newState = prev == null
                ? new DSLRuntimeState(ruleFile.getCatalog(), ruleFile.getName(), newHash,
                    DSLRuntimeState.LocalState.NOT_LOADED, DSLRuntimeState.LoaderGc.LIVE,
                    null, nowMs, nowMs)
                : prev.withContentHash(newHash, nowMs)
                      .withLocalState(DSLRuntimeState.LocalState.NOT_LOADED, nowMs)
                      .withApplyError(null, nowMs);
            rules.compute(key, (k, existing) -> existing == null
                ? new AppliedRuleScript(ruleFile.getCatalog(), ruleFile.getName(), null, newState)
                : existing.withState(newState));
            return;
        }

        // 3. NO_CHANGE — content byte-identical and still ACTIVE. The caller's hash short-
        // circuit usually catches this; if we're here a status flip, recovery state, or a
        // REST {@code force=true} re-post brought us through. When the bundle is SELF-
        // suspended on entry (REST main self-suspended before calling apply), there's no
        // commit to stash and no commit-tail to drain — the resume side won't fire by
        // itself. {@code localResume} clears SELF only, so a peer tick reaching this branch
        // on a PEER-suspended bundle correctly leaves the PEER origin alone (the main's
        // Resume broadcast or self-heal owns that side).
        if (cl == Classification.NO_CHANGE) {
            log.debug("runtime-rule dslManager: {}/{} no content change, skipping",
                ruleFile.getCatalog(), ruleFile.getName());
            if (wasSuspended) {
                suspendCoord.localResume(ruleFile.getCatalog(), ruleFile.getName());
            }
            // localResume already updated the entry on a SELF-clear; re-read so the hash
            // refresh below stamps the right base.
            final AppliedRuleScript curScript = rules.get(key);
            final DSLRuntimeState cur = curScript == null ? null : curScript.getState();
            if (cur != null) {
                final DSLRuntimeState refreshed = cur.withContentHash(newHash, nowMs);
                rules.compute(key, (k, existing) -> existing == null
                    ? new AppliedRuleScript(ruleFile.getCatalog(), ruleFile.getName(),
                        null, refreshed)
                    : existing.withState(refreshed));
            }
            return;
        }

        // 4. Cross-file ownership guard.
        final List<String> conflicts = checkOwnershipConflicts(engine, ruleFile, key);
        if (!conflicts.isEmpty()) {
            final String msg = "rule-name collision with other active files: " + conflicts;
            log.error("runtime-rule dslManager CRITICAL: apply REJECTED for {}/{}: {}",
                ruleFile.getCatalog(), ruleFile.getName(), msg);
            stampApplyError(ruleFile, key, prev, nowMs, msg, true);
            return;
        }

        // 5. Engine pipeline — compile + fireSchemaChanges + verify.
        final DSLRuntimeApply.Outcome outcome = dslRuntimeApply.compileAndVerify(
            ruleFile, cl, buildApplyInputs(storageOpt));
        if (outcome.status == DSLRuntimeApply.Outcome.Status.COMPILE_FAILED) {
            // Engine has already rolled back partial registrations.
            log.error("runtime-rule dslManager CRITICAL: apply COMPILE_FAILED for {}/{}: {}",
                ruleFile.getCatalog(), ruleFile.getName(), outcome.error);
            stampApplyError(ruleFile, key, prev, nowMs, outcome.error, true);
            return;
        }
        if (outcome.status == DSLRuntimeApply.Outcome.Status.VERIFY_FAILED) {
            // Engine.rollback already ran. Stamp verify error.
            log.error("runtime-rule dslManager CRITICAL: apply VERIFY_FAILED for {}/{}: {}",
                ruleFile.getCatalog(), ruleFile.getName(), outcome.error);
            final AppliedRuleScript currentScript = rules.get(key);
            final DSLRuntimeState current = currentScript == null ? null : currentScript.getState();
            if (current != null) {
                rules.put(key, currentScript.withState(current.withApplyError(outcome.error, nowMs)));
            }
            return;
        }

        // 6. READY_TO_COMMIT — wrap and stash (REST 2-PC) or commit inline (tick / sync).
        log.info("runtime-rule dslManager: apply OK for {}/{}",
            ruleFile.getCatalog(), ruleFile.getName());
        final PendingApplyCommit pending = new PendingApplyCommit(outcome, prev, wasSuspended, nowMs);
        if (deferCommit) {
            commitCoord.stash(pending);
            return;
        }
        commitCoord.commitInline(pending);
    }

    private org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs buildApplyInputs(
            final StorageManipulationOpt storageOpt) {
        return new org.apache.skywalking.oap.server.receiver.runtimerule.engine.ApplyInputs(
            moduleManager, storageOpt,
            this::invokeAlarmReset, rules);
    }

    /**
     * Cross-file ownership guard. DSL-agnostic: routes through {@code engine.claimedKeys} for
     * the planned set and {@code engine.activeClaimsExcluding} for ACTIVE peers' claims, plus
     * the DAO for INACTIVE-row claims. Returns the list of human-readable conflict
     * descriptions (empty when the planned key set is conflict-free).
     *
     * <p>Two ownership sources are checked:
     * <ol>
     *   <li>Active appliedX entries on this engine — covers runtime files this node has
     *       applied plus boot-seeded static rules.</li>
     *   <li>INACTIVE rows in the DAO — {@code /inactivate} clears appliedX but the row's
     *       content + status remain. Per the soft-pause contract, an inactive rule still
     *       HOLDS its claimed keys: the operator's recourse is to update or {@code /delete}
     *       that rule before reusing its keys in another file.</li>
     * </ol>
     */
    private List<String> checkOwnershipConflicts(
            final RuleEngine<?> engine,
            final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
            final String selfKey) {
        final Set<String> planned = engine.claimedKeys(
            ruleFile.getContent(), ruleFile.getCatalog() + "/" + ruleFile.getName());
        final List<String> conflicts = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> other : engine.activeClaimsExcluding(selfKey).entrySet()) {
            for (final String pk : planned) {
                if (other.getValue().contains(pk)) {
                    conflicts.add(pk + " owned by " + other.getKey());
                }
            }
        }
        try {
            final RuntimeRuleManagementDAO dao = moduleManager.find(StorageModule.NAME)
                .provider().getService(RuntimeRuleManagementDAO.class);
            if (dao != null) {
                for (final RuntimeRuleManagementDAO.RuntimeRuleFile other : dao.getAll()) {
                    if (!engine.supportedCatalogs().contains(other.getCatalog())) {
                        continue;
                    }
                    final String otherKey = DSLScriptKey.key(other.getCatalog(), other.getName());
                    if (selfKey.equals(otherKey)) {
                        continue;
                    }
                    if (!RuntimeRule.STATUS_INACTIVE.equals(other.getStatus())) {
                        continue;
                    }
                    final Set<String> claimedByInactive = engine.claimedKeys(
                        other.getContent(), other.getCatalog() + "/" + other.getName());
                    for (final String pk : planned) {
                        if (claimedByInactive.contains(pk)) {
                            conflicts.add(pk + " held by inactive " + otherKey
                                + " (update or /delete that rule first)");
                        }
                    }
                }
            }
        } catch (final Throwable t) {
            log.warn("runtime-rule: inactive-claim check failed for {}/{}; relying on "
                + "active-only result", ruleFile.getCatalog(), ruleFile.getName(), t);
        }
        return conflicts;
    }

    /** State-transition helper: stamp classify-failure error without advancing
     *  contentHash (so the next tick retries). */
    private void stampClassifyError(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                                    final String key, final DSLRuntimeState prev,
                                    final long nowMs, final String message) {
        final DSLRuntimeState newState = prev == null
            ? DSLRuntimeState.failedFirstApply(ruleFile.getCatalog(), ruleFile.getName(), nowMs)
                .withApplyError("classify failed: " + message, nowMs)
            : prev.withApplyError("classify failed: " + message, nowMs);
        rules.compute(key, (k, existing) -> existing == null
            ? new AppliedRuleScript(ruleFile.getCatalog(), ruleFile.getName(), null, newState)
            : existing.withState(newState));
    }

    /** State-transition helper for apply failures: stamp the error, optionally flip
     *  SUSPENDED → RUNNING so dispatch isn't left parked. Does NOT advance contentHash —
     *  the next tick re-classifies and retries on whatever content the operator pushes. */
    private void stampApplyError(final RuntimeRuleManagementDAO.RuntimeRuleFile ruleFile,
                                 final String key, final DSLRuntimeState prev,
                                 final long nowMs, final String message,
                                 final boolean resumeIfSuspended) {
        if (resumeIfSuspended && prev != null
                && prev.getLocalState() == DSLRuntimeState.LocalState.SUSPENDED) {
            suspendCoord.resumeDispatchForBundle(key);
        }
        final DSLRuntimeState newState = prev == null
            ? DSLRuntimeState.failedFirstApply(ruleFile.getCatalog(), ruleFile.getName(), nowMs)
                .withApplyError(message, nowMs)
            : prev.withApplyError(message, nowMs)
                  .withLocalState(DSLRuntimeState.LocalState.RUNNING, nowMs);
        rules.compute(key, (k, existing) -> existing == null
            ? new AppliedRuleScript(ruleFile.getCatalog(), ruleFile.getName(), null, newState)
            : existing.withState(newState));
    }

    /**
     * Best-effort dispatch to the alarm-kernel service. If the alarm module is not loaded in
     * this OAP deployment (some embedded / test topologies), the lookup fails and the reset
     * is silently skipped — alarm windows self-heal within one evaluation period anyway.
     */
    private void invokeAlarmReset(final Set<String> affectedMetricNames) {
        if (affectedMetricNames == null || affectedMetricNames.isEmpty()) {
            return;
        }
        try {
            final AlarmKernelService kernel = moduleManager.find(AlarmModule.NAME).provider()
                                                           .getService(AlarmKernelService.class);
            kernel.reset(affectedMetricNames);
        } catch (final Throwable t) {
            log.debug("runtime-rule dslManager: alarm-kernel reset skipped ({}); alarm windows "
                + "will self-heal within one evaluation period", t.getMessage());
        }
    }

    /**
     * Pick the {@link StorageManipulationOpt} for a tick-driven apply.
     *
     * <p>Two axes:
     *
     * <p><b>RunningMode (boot/init context).</b>
     * <ul>
     *   <li>{@code init} mode — OAP is the dedicated initialiser; install schema if
     *       absent. {@link StorageManipulationOpt#createIfAbsent()} matches what the
     *       rest of the static-rule install path does in init mode (idempotent against
     *       backends that already hold the table).
     *   <li>{@code no-init} mode — this OAP must NOT touch the backend; the init OAP
     *       owns schema. The opt depends on whether this is the synchronous boot pass
     *       or a scheduled tick:
     *     <ul>
     *       <li><b>Boot pass</b> ({@code atBoot=true}) →
     *           {@link StorageManipulationOpt#localCacheVerify()}. Strict: backend
     *           resources must already exist with the declared shape. A missing or
     *           mismatched schema fails the bootstrap (k8s pod backloop) — operator must
     *           bring up the init OAP first, or align rule files with the backend.
     *       <li><b>Scheduled tick</b> ({@code atBoot=false}) →
     *           {@link StorageManipulationOpt#localCacheOnly()}. Lenient: the timer
     *           retries forever without raising errors so transient absence (init OAP
     *           still catching up between ticks) self-heals.
     *     </ul>
     *   <li>default mode (regular running OAP) — branch on cluster main-ness, see below.
     * </ul>
     *
     * <p><b>Cluster main-ness (default mode only).</b>
     * <ul>
     *   <li>Self is main → {@link StorageManipulationOpt#fullInstall()}. The REST path
     *       has the same shape; tick rarely runs on main because REST usually
     *       converges the main's state first.
     *   <li>Peer (someone else is main) → {@link StorageManipulationOpt#localCacheOnly()}.
     *       Local MeterSystem + MetadataRegistry populate so the peer dispatches samples
     *       correctly, but no server-side DDL fires.
     * </ul>
     *
     * <p>When the cluster module isn't wired (embedded test topology), {@link
     * MainRouter#isSelfMain} returns {@code true} and the default-mode branch falls
     * through to {@code fullInstall} — single-process deployments are always main.
     *
     * @param atBoot true for the synchronous one-shot pass invoked from
     *               {@code RuntimeRuleModuleProvider.notifyAfterCompleted}; false for
     *               scheduled-executor ticks.
     */
    private StorageManipulationOpt tickStorageOpt(final boolean atBoot) {
        if (RunningMode.isInitMode()) {
            return StorageManipulationOpt.createIfAbsent();
        }
        if (RunningMode.isNoInitMode()) {
            return atBoot
                ? StorageManipulationOpt.localCacheVerify()
                : StorageManipulationOpt.localCacheOnly();
        }
        try {
            final RemoteClientManager rcm = moduleManager.find(CoreModule.NAME).provider()
                                                         .getService(RemoteClientManager.class);
            return MainRouter.isSelfMain(rcm)
                ? StorageManipulationOpt.fullInstall()
                : StorageManipulationOpt.localCacheOnly();
        } catch (final Throwable t) {
            return StorageManipulationOpt.fullInstall();
        }
    }
}
