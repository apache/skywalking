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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.AppliedRuleScript;
import org.apache.skywalking.oap.server.receiver.runtimerule.state.DSLRuntimeState;

/**
 * REST two-phase commit stash for STRUCTURAL apply. Bridges the gap between
 * {@link DSLRuntimeApply#compileAndVerify} (engine compile + verify succeeded — apply still
 * reversible) and the REST handler's row-persist (where the apply becomes durable).
 *
 * <p>Three entry points:
 * <ul>
 *   <li>{@link #stash} — apply pipeline parks a pending commit when the REST caller wants
 *       the destructive tail deferred until persist succeeds.</li>
 *   <li>{@link #finalizeCommit} — REST handler invokes after row-persist succeeds; routes
 *       the stashed outcome through {@link DSLRuntimeApply#commit} (the engine swaps
 *       appliedX, drops removedMetrics, retires the displaced loader, fires alarm reset),
 *       then runs the scheduler-side snapshot transition.</li>
 *   <li>{@link #discardCommit} — REST handler invokes after row-persist fails; routes the
 *       stashed outcome through {@link DSLRuntimeApply#rollback} (the engine drops just-
 *       registered metrics), then resumes dispatch + flips snapshot back to RUNNING.</li>
 * </ul>
 *
 * <p>The {@link #commitInline} variant runs the same commit tail without the stash —
 * used by the tick path where there is no row-persist gate to wait on.
 *
 * <p><b>What this class owns vs delegates.</b> The 2-PC stash + scheduler-side state
 * transitions (snapshot.put, suspendCoord.resumeDispatchForBundle) live here. The engine
 * pipeline (commit / rollback) lives behind {@link DSLRuntimeApply}; this coordinator
 * doesn't know how MAL's commit body works, only when to invoke it.
 */
@Slf4j
public class StructuralCommitCoordinator {

    private final Map<String, PendingApplyCommit> pendingCommits = new ConcurrentHashMap<>();

    private final Map<String, AppliedRuleScript> rules;
    private final DSLRuntimeApply dslRuntimeApply;
    private final SuspendResumeCoordinator suspendCoord;

    public StructuralCommitCoordinator(final Map<String, AppliedRuleScript> rules,
                                       final DSLRuntimeApply dslRuntimeApply,
                                       final SuspendResumeCoordinator suspendCoord) {
        this.rules = rules;
        this.dslRuntimeApply = dslRuntimeApply;
        this.suspendCoord = suspendCoord;
    }

    /**
     * Park a pending commit until the REST handler's row-persist resolves. Caller must
     * already hold the per-file lock (the apply pipeline does).
     */
    public void stash(final PendingApplyCommit p) {
        pendingCommits.put(DSLScriptKey.key(p.catalog(), p.name()), p);
    }

    /**
     * Drain the pending commit after the REST handler's row-persist succeeded. Acquires
     * the per-file lock so the commit tail is consistent with concurrent applies on the
     * same file. Returns {@code true} when a commit was actually drained, {@code false}
     * when no pending commit existed (typical for {@code force=true} re-applies on byte-
     * identical content — the engine classified as NO_CHANGE so nothing was stashed). The
     * REST handler uses the return to decide whether peers still need a Resume broadcast.
     */
    public boolean finalizeCommit(final String catalog, final String name) {
        final ReentrantLock perFile = AppliedRuleScript.lockFor(rules, catalog, name);
        perFile.lock();
        try {
            final PendingApplyCommit p = pendingCommits.remove(DSLScriptKey.key(catalog, name));
            if (p == null) {
                return false;
            }
            commitInline(p);
            return true;
        } finally {
            perFile.unlock();
        }
    }

    /**
     * Drain the pending commit after the REST handler's row-persist failed. The engine's
     * rollback drops just-registered metrics; snapshot stays at the pre-apply value so
     * the local node re-aligns with cluster state on the next tick.
     */
    public void discardCommit(final String catalog, final String name) {
        final ReentrantLock perFile = AppliedRuleScript.lockFor(rules, catalog, name);
        perFile.lock();
        try {
            final PendingApplyCommit p = pendingCommits.remove(DSLScriptKey.key(catalog, name));
            if (p == null) {
                return;
            }
            // Engine drops the just-registered added + shape-break metrics. Old applied
            // state is still intact (commit never ran), so unchanged metrics keep serving.
            dslRuntimeApply.rollback(p.outcome);
            // If this node came in SUSPENDED (peer broadcast or self-suspend), flip back
            // to RUNNING + resume dispatch so samples for unchanged metrics flow again.
            if (p.wasSuspended) {
                final String pKey = DSLScriptKey.key(catalog, name);
                suspendCoord.resumeDispatchForBundle(pKey);
                final AppliedRuleScript curScript = rules.get(pKey);
                final DSLRuntimeState cur = curScript == null ? null : curScript.getState();
                if (cur != null && cur.getLocalState() == DSLRuntimeState.LocalState.SUSPENDED) {
                    rules.put(pKey, curScript.withState(
                        cur.withLocalState(DSLRuntimeState.LocalState.RUNNING, System.currentTimeMillis())));
                }
            }
        } finally {
            perFile.unlock();
        }
    }

    /**
     * Drain a {@link PendingApplyCommit} by routing through the engine's commit (which
     * drops removedMetrics + swaps appliedMal/appliedContent + pushes the converter +
     * retires the old loader + fires alarm reset), then runs the scheduler-side snapshot
     * transition + suspend resume.
     *
     * <p>Called from the tick path directly (inline commit) and from {@link #finalizeCommit}
     * (REST path, after row-persist succeeds). Both paths hold the per-file lock already.
     */
    public void commitInline(final PendingApplyCommit p) {
        // Engine does the full commit body. This call is the only place outside DSLRuntimeApply
        // that drives engine.commit; the apply-inline path goes through dslRuntimeApply.applyInline.
        dslRuntimeApply.commit(p.outcome);

        final String pKey = DSLScriptKey.key(p.catalog(), p.name());
        // Resume dispatch for unchanged metrics that were parked during Suspend.
        if (p.wasSuspended) {
            suspendCoord.resumeDispatchForBundle(pKey);
        }
        // Snapshot transition: advance contentHash to the newly-committed bundle, flip to
        // RUNNING when the bundle came in SUSPENDED.
        final DSLRuntimeState base = p.prevSnapshot == null
            ? DSLRuntimeState.running(p.catalog(), p.name(), p.newContentHash(), p.commitNowMs)
            : p.prevSnapshot.withContentHash(p.newContentHash(), p.commitNowMs);
        final DSLRuntimeState newState = p.wasSuspended
            ? base.withLocalState(DSLRuntimeState.LocalState.RUNNING, p.commitNowMs)
            : base;
        rules.compute(pKey, (k, prev) -> prev == null
            ? new AppliedRuleScript(p.catalog(), p.name(), null, newState)
            : prev.withState(newState));
    }
}
