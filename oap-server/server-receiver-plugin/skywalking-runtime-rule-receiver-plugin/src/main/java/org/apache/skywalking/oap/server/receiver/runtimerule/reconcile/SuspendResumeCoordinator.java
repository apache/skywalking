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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.EngineApplied;
import org.apache.skywalking.oap.server.receiver.runtimerule.util.ContentHash;

/**
 * Suspend/Resume state machine on top of {@link DSLRuntimeState#getSuspendOrigin()}. Owns
 * the SELF / PEER / BOTH origin transitions, the dispatch-side park/unpark fan-out across
 * engines (driven through each rule's {@link EngineApplied} so this class never switches
 * on MAL vs LAL), and the self-heal sweep that recovers from a peer main crashing between
 * Suspend and Resume.
 *
 * <p>Lifecycle is driven by three callers:
 * <ul>
 *   <li><b>REST {@code /addOrUpdate} / {@code /inactivate} / {@code /delete}</b> — the
 *       local main calls {@link #localSuspend} before its DDL workflow and
 *       {@link #localResume} on its own rollback / discard path.
 *   <li><b>Cluster {@code Suspend} / {@code Resume} RPCs</b> — incoming peer broadcasts
 *       call {@link #peerSuspend} / {@link #peerResume}.
 *   <li><b>DSLManager tick</b> — calls {@link #sweepSuspendedForSelfHeal} once per tick
 *       to recover any PEER-origin entries whose main died between Suspend and Resume.
 * </ul>
 *
 * <p>Lock contract: every state transition acquires the per-file {@link ReentrantLock} that
 * lives on each {@link AppliedRuleScript} (lazy-created via
 * {@link AppliedRuleScript#lockFor}) — the same mutex the apply pipeline uses, so suspend
 * bookkeeping written here is consistent with apply-pipeline writes without a separate lock.
 */
@Slf4j
public class SuspendResumeCoordinator {

    /**
     * Bound on how long an inbound Suspend will wait for the per-file lock before
     * giving up. Short — longer than normal tick contention (which uses its own
     * tryLock and defers within milliseconds), shorter than a typical apply
     * workflow's hold on the lock. If we can't acquire within this window, the safe
     * interpretation is "another apply owns this file locally" and the correct
     * response is split-brain rejection.
     */
    private static final long SUSPEND_LOCK_TIMEOUT_MS = 500L;

    private final Map<String, AppliedRuleScript> rules;
    private final ModuleManager moduleManager;
    private final long selfHealThresholdMs;
    private final Supplier<Map<String, RuntimeRuleManagementDAO.RuntimeRuleFile>> dbRulesReader;

    public SuspendResumeCoordinator(final Map<String, AppliedRuleScript> rules,
                                    final ModuleManager moduleManager,
                                    final long selfHealThresholdMs,
                                    final Supplier<Map<String, RuntimeRuleManagementDAO.RuntimeRuleFile>> dbRulesReader) {
        this.rules = rules;
        this.moduleManager = moduleManager;
        this.selfHealThresholdMs = selfHealThresholdMs;
        this.dbRulesReader = dbRulesReader;
    }

    /**
     * Local suspend: the local REST apply workflow is about to fire DDL on the main
     * node, so dispatch must park and prior handlers must stop accepting samples.
     * Records {@link DSLRuntimeState.SuspendOrigin#SELF} on the snapshot entry. Idempotent
     * on SELF replay.
     *
     * <p>REJECTS with {@link SuspendResult#REJECTED_ORIGIN_CONFLICT} if PEER is
     * already set — that means another OAP thinks it's the main for this file at the
     * same time (routing failure or split-brain). The REST handler propagates the
     * rejection to the operator with HTTP 409; correct routing never triggers this
     * branch.
     */
    public SuspendResult localSuspend(final String catalog, final String name) {
        return applySuspend(catalog, name, DSLRuntimeState.SuspendOrigin.SELF);
    }

    /**
     * Peer-suspend: an inbound {@code Suspend} RPC from a peer main node. Records
     * {@link DSLRuntimeState.SuspendOrigin#PEER}. Idempotent on PEER replay.
     *
     * <p>REJECTS with {@link SuspendResult#REJECTED_ORIGIN_CONFLICT} if SELF is
     * already set — this node is itself mid-apply for the same file, so another node
     * claiming to be main is a routing conflict.
     */
    public SuspendResult peerSuspend(final String catalog, final String name) {
        return applySuspend(catalog, name, DSLRuntimeState.SuspendOrigin.PEER);
    }

    /**
     * Clear SELF origin. Called by the REST handler on its own rollback / exception /
     * discard path. If PEER is also set (BOTH), origin transitions BOTH → PEER and
     * the bundle stays SUSPENDED waiting for the peer's Resume or self-heal. If SELF
     * was the only origin, the bundle flips back to RUNNING and dispatch resumes.
     */
    public int localResume(final String catalog, final String name) {
        return applyResume(catalog, name, DSLRuntimeState.SuspendOrigin.SELF);
    }

    /**
     * Clear PEER origin. Called by the inbound {@code Resume} RPC handler and by the
     * self-heal sweep when the peer main that issued Suspend never sent Resume.
     */
    public int peerResume(final String catalog, final String name) {
        return applyResume(catalog, name, DSLRuntimeState.SuspendOrigin.PEER);
    }

    private SuspendResult applySuspend(final String catalog, final String name,
                                       final DSLRuntimeState.SuspendOrigin incoming) {
        final String key = DSLScriptKey.key(catalog, name);
        // tryLock with a bounded deadline instead of blocking indefinitely. In the
        // split-brain scenario two nodes both enter the per-file workflow, each holds
        // its own per-file lock across its broadcastSuspend call, and each peer's
        // Suspend handler would block on the other's lock until the gRPC deadline
        // fires — the client then converts the timeout to an unreachable/null ack
        // and BOTH sides proceed, racing on persist. Short timeout here turns that
        // race into an immediate REJECTED_ORIGIN_CONFLICT.
        final ReentrantLock lock = AppliedRuleScript.lockFor(rules, catalog, name);
        final boolean acquired;
        try {
            acquired = lock.tryLock(SUSPEND_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("runtime-rule Suspend interrupted while waiting for per-file lock on "
                + "{}/{}; surfacing as split-brain rejection.", catalog, name);
            return SuspendResult.REJECTED_ORIGIN_CONFLICT;
        }
        if (!acquired) {
            log.warn("runtime-rule Suspend could not acquire per-file lock on {}/{} within "
                + "{} ms — another apply workflow is in flight locally; treating as "
                + "split-brain (the local workflow already owns SELF origin).",
                catalog, name, SUSPEND_LOCK_TIMEOUT_MS);
            return SuspendResult.REJECTED_ORIGIN_CONFLICT;
        }
        try {
            final AppliedRuleScript existingScript = rules.get(key);
            final DSLRuntimeState existing = existingScript == null ? null : existingScript.getState();
            if (existing == null) {
                return SuspendResult.NOT_PRESENT;
            }
            final DSLRuntimeState.SuspendOrigin current = existing.getSuspendOrigin();
            if (current == incoming) {
                return SuspendResult.ALREADY_SUSPENDED;
            }
            if (current == DSLRuntimeState.SuspendOrigin.SELF
                    || current == DSLRuntimeState.SuspendOrigin.PEER
                    || current == DSLRuntimeState.SuspendOrigin.BOTH) {
                log.warn("runtime-rule ORIGIN CONFLICT: {}/{} already suspended by {}; "
                    + "refusing {} suspend. Likely cause: cluster routing misfire or split-brain — "
                    + "two nodes think they own the main role for this file.",
                    catalog, name, current, incoming);
                return SuspendResult.REJECTED_ORIGIN_CONFLICT;
            }
            // current == NONE: bundle was RUNNING. Park dispatch and flip to SUSPENDED.
            suspendDispatchForBundle(key);
            final long nowMs = System.currentTimeMillis();
            rules.put(key, existingScript.withState(existing.withSuspendOrigin(incoming, nowMs)));
            return SuspendResult.SUSPENDED;
        } finally {
            lock.unlock();
        }
    }

    private int applyResume(final String catalog, final String name,
                            final DSLRuntimeState.SuspendOrigin clearing) {
        final String key = DSLScriptKey.key(catalog, name);
        final ReentrantLock lock = AppliedRuleScript.lockFor(rules, catalog, name);
        lock.lock();
        try {
            final AppliedRuleScript existingScript = rules.get(key);
            final DSLRuntimeState existing = existingScript == null ? null : existingScript.getState();
            if (existing == null
                    || existing.getLocalState() != DSLRuntimeState.LocalState.SUSPENDED) {
                return 0;
            }
            final DSLRuntimeState.SuspendOrigin newOrigin = existing.getSuspendOrigin().remove(clearing);
            if (newOrigin == existing.getSuspendOrigin()) {
                return 0;
            }
            final long nowMs = System.currentTimeMillis();
            if (newOrigin == DSLRuntimeState.SuspendOrigin.NONE) {
                final int resumed = resumeDispatchForBundle(key);
                rules.put(key, existingScript.withState(existing.withSuspendOrigin(newOrigin, nowMs)));
                return resumed;
            }
            rules.put(key, existingScript.withState(existing.withSuspendOrigin(newOrigin, nowMs)));
            return 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Park dispatch for whatever the bundle has applied — engine-agnostic via
     * {@link EngineApplied#suspendDispatch}. Returns the count of dispatch primitives
     * paused; {@code 0} when the bundle hasn't been committed yet (no applied artefact)
     * or the engine's runtime services aren't resolvable.
     */
    private int suspendDispatchForBundle(final String key) {
        final AppliedRuleScript script = rules.get(key);
        if (script == null) {
            return 0;
        }
        final EngineApplied applied = script.getApplied();
        if (applied == null) {
            return 0;
        }
        return applied.suspendDispatch(moduleManager);
    }

    /**
     * Inverse of {@link #suspendDispatchForBundle}. Public so the apply pipeline can drive
     * the post-apply resume after a successful structural commit / shape-match without
     * taking a second cluster RPC round-trip.
     */
    public int resumeDispatchForBundle(final String key) {
        final AppliedRuleScript script = rules.get(key);
        if (script == null) {
            return 0;
        }
        final EngineApplied applied = script.getApplied();
        if (applied == null) {
            return 0;
        }
        return applied.resumeDispatch(moduleManager);
    }

    /**
     * Recover bundles stuck in {@link DSLRuntimeState.LocalState#SUSPENDED} by a
     * peer-origin Suspend whose main crashed before sending Resume. Only acts on
     * PEER-only origins — SELF origin is the local REST apply's own bookkeeping, and
     * BOTH origin indicates a SELF apply is in flight alongside a PEER broadcast (the
     * local apply's finalize / discard path is the recovery, not self-heal).
     *
     * <p>Bundles whose DB content has advanced since the suspend are left for the
     * apply pipeline to pick up via the normal content-hash diff — those are the
     * "main node succeeded, we're catching up" path. We deliberately do not flip
     * those back to RUNNING here: the correct handlers for the new content haven't
     * been installed yet.
     *
     * <p>Most main-side failures now clear peer-side SUSPENDED within an RPC
     * round-trip via the Resume broadcast, so this sweep is a backstop for the
     * narrow case where the main crashes after Suspend but before Resume. Self-heal
     * threshold can be tuned via the constructor parameter.
     */
    public void sweepSuspendedForSelfHeal() {
        final long nowNanos = System.nanoTime();
        final long thresholdNanos = TimeUnit.MILLISECONDS.toNanos(selfHealThresholdMs);

        final Map<String, RuntimeRuleManagementDAO.RuntimeRuleFile> dbRules = dbRulesReader.get();
        if (dbRules == null) {
            log.debug("runtime-rule self-heal: storage DAO unavailable, skipping sweep");
            return;
        }

        for (final AppliedRuleScript script : rules.values()) {
            final DSLRuntimeState current = script.getState();
            if (current == null
                    || current.getLocalState() != DSLRuntimeState.LocalState.SUSPENDED) {
                continue;
            }
            if (current.getSuspendOrigin() != DSLRuntimeState.SuspendOrigin.PEER) {
                continue;
            }
            final long ageNanos = nowNanos - current.getEnteredCurrentStateAtNanos();
            if (ageNanos < thresholdNanos) {
                continue;
            }

            final String key = DSLScriptKey.key(current.getCatalog(), current.getName());
            final RuntimeRuleManagementDAO.RuntimeRuleFile currentDbRule = dbRules.get(key);

            if (currentDbRule == null) {
                log.debug("runtime-rule self-heal: bundle {}/{} DB rule gone; delta-apply will drop",
                    current.getCatalog(), current.getName());
                continue;
            }
            if (RuntimeRule.STATUS_INACTIVE.equals(currentDbRule.getStatus())) {
                log.debug("runtime-rule self-heal: bundle {}/{} DB rule INACTIVE; delta-apply "
                    + "will tear down — not resuming", current.getCatalog(), current.getName());
                continue;
            }
            final String currentDbHash = ContentHash.sha256Hex(currentDbRule.getContent());
            if (!Objects.equals(currentDbHash, current.getContentHash())) {
                log.debug("runtime-rule self-heal: bundle {}/{} DB advanced ({} → {}); "
                    + "leaving delta-apply to handle",
                    current.getCatalog(), current.getName(),
                    DSLScriptKey.shortHash(current.getContentHash()),
                    DSLScriptKey.shortHash(currentDbHash));
                continue;
            }

            final long ageMs = TimeUnit.NANOSECONDS.toMillis(ageNanos);
            log.warn("runtime-rule self-heal: bundle {}/{} has been PEER-suspended for {} ms "
                + "(threshold {} ms) and DB content unchanged at hash {} — clearing PEER origin. "
                + "Likely cause: the main node that issued Suspend crashed before sending "
                + "Resume (Resume broadcast is the primary recovery path; this is the backstop).",
                current.getCatalog(), current.getName(), ageMs, selfHealThresholdMs,
                DSLScriptKey.shortHash(current.getContentHash()));
            peerResume(current.getCatalog(), current.getName());
        }
    }

}
