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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * Owns the lifecycle of every active debug session on this OAP node.
 *
 * <h2>Workflow</h2>
 * <pre>
 *   REST handler ─►  install(ruleKey, clientId, limits)
 *                      │
 *                      │ 1. ask each registered DebugHolderLookup until one returns
 *                      │    a non-null holder (DSL-agnostic dispatch via lookup.serves(...))
 *                      │ 2. ask each registered DebugRecorderFactory until one matches
 *                      │ 3. create DebugSession{ recorder, boundHolder }
 *                      │ 4. holder.addRecorder(recorder)  -- 0&rarr;1 transition flips the gate
 *                      ▼
 *                  active session  ─►  probes fan out into recorder
 *
 *   REST handler ─►  poll(sessionId)            ─►  recorder.snapshotRecords()
 *
 *   stop / retention  ─►  uninstall(sessionId)
 *                            │
 *                            │ session.boundHolder.removeRecorder(session.recorder)
 *                            │ -- bound to the *original* holder so a hot-update of
 *                            │    the rule never strands V1's recorder array
 *                            ▼
 *                         retired
 * </pre>
 *
 * <p>Multiple sessions on the same rule are first-class: each is one entry
 * in the holder's {@code recorders[]} array; the gate stays {@code true}
 * until the last recorder leaves. Multiple lookups / factories per DSL are
 * allowed too — first-match wins, so hot-update paths can register an
 * override that takes precedence over the static-rule path without
 * unregistering the static lookup.
 *
 * <p>Thread safety: registrations and the session map use concurrent
 * collections; install / uninstall / poll are atomic at the session-
 * granularity. The probe hot path doesn't touch this class — it only
 * reads {@link GateHolder#recorders}.
 */
@Slf4j
public final class DebugSessionRegistry {

    /**
     * Active-session ceiling per OAP node. The captured payload of every live
     * session is held in heap until retention timeout; without a ceiling an
     * unauthenticated admin caller could pin {@code recordCap × payloadSize}
     * bytes per session indefinitely. SWIP-13 fixes this at 200 — large enough
     * for legitimate concurrent debug sessions across an operator team, small
     * enough that the per-node heap budget is bounded.
     */
    public static final int MAX_ACTIVE_SESSIONS = 200;

    private final ConcurrentHashMap<String, DebugSession> sessions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<DebugHolderLookup> lookups = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DebugRecorderFactory> factories = new CopyOnWriteArrayList<>();

    public void registerLookup(final DebugHolderLookup lookup) {
        lookups.add(lookup);
    }

    public void registerRecorderFactory(final DebugRecorderFactory factory) {
        factories.add(factory);
    }

    /**
     * Snapshot of every active session. Defensive copy — safe to render to a
     * REST response without further synchronisation.
     */
    public Collection<DebugSession> snapshotActive() {
        return new ArrayList<>(sessions.values());
    }

    public DebugSession find(final String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    /**
     * Stop every session bound to the supplied {@code clientId}. Used by the cluster
     * {@code StopByClientId} RPC so a load-balancer split that delivered the previous
     * POST to a different peer doesn't strand a duplicate session. Returns the list of
     * stopped session IDs (empty when no match) so the caller can surface the cleanup
     * footprint in the RPC ack.
     *
     * <p>Idempotent — a clientId with no live session returns an empty list and does
     * nothing else. Iterates a defensive copy so callers can also call
     * {@link #uninstall(String)} on the same id without ConcurrentModificationException.
     */
    public List<String> stopByClientId(final String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> stopped = new ArrayList<>();
        for (final DebugSession session : new ArrayList<>(sessions.values())) {
            if (clientId.equals(session.getClientId())) {
                if (uninstall(session.getSessionId())) {
                    stopped.add(session.getSessionId());
                }
            }
        }
        return stopped;
    }

    /**
     * Create a session bound to the live holder for {@code ruleKey}.
     *
     * @return the new session, or {@code null} if no registered lookup can
     *         resolve a live holder for this rule key (treat as 404 at the
     *         REST layer).
     * @throws IllegalStateException if a holder is found but no recorder
     *         factory is registered for that DSL — a configuration bug,
     *         not a runtime-data condition.
     */
    public DebugSession install(final RuleKey ruleKey,
                                final String clientId,
                                final SessionLimits limits) {
        // Local POST always mints a fresh sessionId, so the outcome is INSTALLED or
        // NOT_LOCAL — never ALREADY_INSTALLED. Drop the typed wrapper for callers
        // that don't need to distinguish the duplicate case.
        final InstallOutcome outcome = installWithId(
            UUID.randomUUID().toString(), ruleKey, clientId, limits);
        return outcome.getStatus() == InstallOutcome.Status.NOT_LOCAL ? null : outcome.getSession();
    }

    /**
     * Install with a caller-supplied sessionId. Used by the cluster
     * {@code InstallDebugSession} RPC: every peer binds a recorder under the same
     * sessionId so the receiving node can later collect by id without a per-peer
     * id mapping. Idempotent — a duplicate install for the same sessionId returns
     * an {@link InstallOutcome.Status#ALREADY_INSTALLED} outcome carrying the
     * existing session.
     *
     * <p>Returns {@link InstallOutcome.Status#NOT_LOCAL} when no lookup resolves a
     * live holder for this rule key on this node — the caller treats that as
     * "this peer simply contributes nothing to the session", which is the
     * SWIP §6 best-effort contract.
     */
    public InstallOutcome installWithId(final String sessionId, final RuleKey ruleKey,
                                        final String clientId, final SessionLimits limits) {
        final DebugSession existing = sessions.get(sessionId);
        if (existing != null) {
            return new InstallOutcome(InstallOutcome.Status.ALREADY_INSTALLED, existing);
        }
        // Resolve the holder FIRST. The active-session ceiling only applies to nodes
        // that actually own the rule — if this node doesn't have the live artifact,
        // surfacing TOO_MANY_SESSIONS would falsely reject a session whose actual
        // holder lives on a peer with capacity, breaking the LB-safe contract. The
        // load-shedding role of the cap is preserved: an attacker spamming installs
        // at unloaded rules still gets NOT_LOCAL (the cheap path), and only nodes
        // that would actually bind a recorder enforce the cap.
        final GateHolder holder = resolveHolder(ruleKey);
        if (holder == null) {
            return InstallOutcome.NOT_LOCAL_OUTCOME;
        }
        final DebugRecorderFactory factory = resolveFactory(ruleKey);
        if (factory == null) {
            throw new IllegalStateException(
                "No DebugRecorderFactory registered for catalog " + ruleKey.getCatalog()
                    + " — phase 1d wiring incomplete.");
        }
        final AbstractDebugRecorder recorder = factory.create(sessionId, ruleKey, holder, limits);
        final long now = System.currentTimeMillis();
        final DebugSession session = new DebugSession(
            sessionId, clientId, ruleKey, recorder, holder, now,
            now + limits.getRetentionMillis()
        );
        // Atomic slot reservation + holder bind: cap-check, putIfAbsent, AND
        // holder.addRecorder all run under the same lock so a concurrent
        // {@link #uninstall} (or reaper-driven stop) cannot remove the session
        // BETWEEN registry-publication and holder-binding and leave an
        // unreachable recorder bound to the holder. Both install and uninstall
        // take the registry lock first, then the holder lock (via add/remove
        // recorder which is synchronized on the holder), preserving the same
        // lock order in both directions — no deadlock.
        final DebugSession reserved;
        synchronized (sessions) {
            if (sessions.size() >= MAX_ACTIVE_SESSIONS) {
                return InstallOutcome.TOO_MANY_SESSIONS_OUTCOME;
            }
            final DebugSession winner = sessions.putIfAbsent(sessionId, session);
            if (winner != null) {
                return new InstallOutcome(InstallOutcome.Status.ALREADY_INSTALLED, winner);
            }
            holder.addRecorder(recorder);
            reserved = session;
        }
        log.info("DSL debug: installed session {} on {} (clientId={})",
                 sessionId, ruleKey, clientId);
        return new InstallOutcome(InstallOutcome.Status.INSTALLED, reserved);
    }

    /**
     * Stop and remove a session. Idempotent — if the session is already gone
     * (retention timeout, prior stop) the call is a no-op. Always uninstalls
     * on the {@link DebugSession#getBoundHolder() bound holder} captured at
     * install time, never on a re-resolved live holder, so a hot-update that
     * happened during the session window can't leak a recorder onto V1.
     */
    public boolean uninstall(final String sessionId) {
        // Mirror install's lock order (registry → holder) so the install path's
        // cap-check / publication / addRecorder triplet stays atomic relative
        // to uninstall: a concurrent uninstall serializes behind the install
        // and never observes a session that's published but not yet bound.
        // Without this, a concurrent install + uninstall race could leave the
        // holder with a recorder bound for a session the registry has already
        // evicted — a permanent leak the retention sweeper can't reach
        // because it iterates the registry, not the holder's recorder array.
        final DebugSession session;
        synchronized (sessions) {
            session = sessions.remove(sessionId);
            if (session == null) {
                return false;
            }
            session.getBoundHolder().removeRecorder(session.getRecorder());
        }
        log.info("DSL debug: uninstalled session {} on {}",
                 sessionId, session.getRuleKey());
        return true;
    }

    /**
     * Sweep retention-expired sessions. Called periodically by the module
     * provider's scheduled executor. Idempotent and bounded — each call walks
     * the active session map once.
     */
    public int reapExpired(final long nowMillis) {
        int reaped = 0;
        final List<String> toRemove = new ArrayList<>();
        for (final DebugSession session : sessions.values()) {
            if (session.getRetentionDeadlineMillis() <= nowMillis) {
                toRemove.add(session.getSessionId());
            }
        }
        for (final String id : toRemove) {
            if (uninstall(id)) {
                reaped++;
            }
        }
        return reaped;
    }

    private GateHolder resolveHolder(final RuleKey ruleKey) {
        for (final DebugHolderLookup lookup : lookups) {
            if (!lookup.serves(ruleKey)) {
                continue;
            }
            final GateHolder holder = lookup.lookup(ruleKey);
            if (holder != null) {
                return holder;
            }
        }
        return null;
    }

    private DebugRecorderFactory resolveFactory(final RuleKey ruleKey) {
        for (final DebugRecorderFactory factory : factories) {
            if (factory.serves(ruleKey)) {
                return factory;
            }
        }
        return null;
    }
}
