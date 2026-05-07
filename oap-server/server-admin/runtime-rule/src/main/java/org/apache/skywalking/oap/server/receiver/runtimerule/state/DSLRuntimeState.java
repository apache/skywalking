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

import java.util.Objects;

/**
 * Immutable per-node snapshot of what a runtime rule is currently doing on this OAP instance.
 *
 * <p>Stored in the dslManager's {@code ConcurrentHashMap<(catalog, name), DSLRuntimeState>} so one
 * {@code get} returns a fully-populated state; readers never see a half-transitioned entry.
 * This is intentionally the opposite of {@code AlarmStatusWatcher.getAlarmRuleContext} which
 * field-reads without a snapshot and can surface mixed-generation results — the runtime-rule
 * {@code /list} surface avoids that trap from day one by replacing the record wholesale on
 * every state transition.
 *
 * <p>Records are not used here because the project must compile on JDK 11. A plain final-field
 * class with explicit getters and {@code with*} copy-constructor helpers gives the same
 * immutability contract on the supported baseline.
 */
public final class DSLRuntimeState {

    /** Local lifecycle state on this node. Distinct from the DB {@code status} column. */
    public enum LocalState {
        /** Handlers registered, samples flowing. */
        RUNNING,
        /** Transiently removed from the dispatch map during a structural apply or
         *  missed-broadcast recovery. Samples for this bundle's metrics are dropped
         *  for the duration. Always paired with a {@link SuspendOrigin} other than
         *  {@link SuspendOrigin#NONE} to describe WHY the bundle is suspended. */
        SUSPENDED,
        /** No compile has succeeded yet for this (catalog, name) on this node. */
        NOT_LOADED
    }

    /**
     * Reason this node is SUSPENDED. Distinct origins must be tracked because Resume-from-peer
     * must not undo a local self-suspend that's mid-apply.
     *
     * <ul>
     *   <li>{@link #NONE} — localState is not SUSPENDED (informational default).</li>
     *   <li>{@link #SELF} — this node entered SUSPENDED itself, right before its own
     *       STRUCTURAL apply. Only {@code SuspendResumeCoordinator#localResume}
     *       (called by the REST handler on its own failure / commit tail) clears this.</li>
     *   <li>{@link #PEER} — a peer main node broadcast Suspend to this node. Only the peer's
     *       subsequent Resume broadcast or the 60 s self-heal rule clears this.</li>
     *   <li>{@link #BOTH} — reserved lattice slot. Under correct single-main routing this
     *       value is unreachable: {@code applySuspend} rejects a cross-origin incoming
     *       {@code Suspend} with {@link org.apache.skywalking.oap.server.receiver.runtimerule.reconcile.SuspendResult#REJECTED_ORIGIN_CONFLICT}
     *       before reaching the lattice merge, so the state cannot transition into BOTH.
     *       The enum value stays to keep the {@link #add} / {@link #remove} lattice total
     *       (and to surface unambiguously if a bug ever did let both origins coexist).</li>
     * </ul>
     *
     * <p>The bundle is SUSPENDED iff this origin is anything other than {@link #NONE}.
     */
    public enum SuspendOrigin {
        NONE, SELF, PEER, BOTH;

        /**
         * Merge a new origin into the existing one. {@code add(X, Y)} returns {@code X ∪ Y}
         * in the lattice {NONE &lt; SELF/PEER &lt; BOTH}. Idempotent: adding an origin that's
         * already included returns the input unchanged.
         */
        public SuspendOrigin add(final SuspendOrigin other) {
            if (other == null || other == NONE || this == other) {
                return this;
            }
            if (this == NONE) {
                return other;
            }
            // One side is SELF, the other is PEER (they differ and neither is NONE).
            return BOTH;
        }

        /**
         * Remove an origin from the existing one. {@code remove(BOTH, SELF) == PEER}, etc.
         * Returns {@link #NONE} when the last origin is removed; caller uses that to flip
         * {@link LocalState#SUSPENDED} back to {@link LocalState#RUNNING}.
         */
        public SuspendOrigin remove(final SuspendOrigin other) {
            if (other == null || other == NONE || this == NONE) {
                return this;
            }
            if (this == BOTH) {
                return other == SELF ? PEER : SELF;
            }
            return this == other ? NONE : this;
        }
    }

    /** Coarse hint about whether the bundle's {@link
     *  org.apache.skywalking.oap.server.core.classloader.RuleClassLoader} has been retired
     *  and, if so, whether the JVM has confirmed collection. */
    public enum LoaderGc {
        /** The loader is alive, serving active classes. */
        LIVE,
        /** The loader is retired but the manager's graveyard phantom reference has not
         *  fired yet. Brief window is expected; persistent presence is the leak signal. */
        PENDING,
        /** Confirmed collected by the JVM (phantom fired). */
        COLLECTED
    }

    private final String catalog;
    private final String name;
    private final String contentHash;
    private final LocalState localState;
    /**
     * Why the bundle is SUSPENDED. {@link SuspendOrigin#NONE} when {@link #localState} is not
     * SUSPENDED; one of SELF / PEER / BOTH otherwise. The REST handler's own apply contributes
     * SELF; an inbound Suspend RPC from a peer contributes PEER. Split tracking is load-bearing
     * for the Resume RPC — Resume must only undo PEER; it must never race the main-node's own
     * in-flight apply by clearing a SELF origin.
     */
    private final SuspendOrigin suspendOrigin;
    private final LoaderGc loaderGc;
    private final String lastApplyError;
    private final long lastAppliedAtMs;
    private final long enteredCurrentStateAtMs;
    /**
     * Monotonic clock stamp paired with {@link #enteredCurrentStateAtMs}. Wall-clock is kept
     * for {@code /list} operator readability; this field is the source used for threshold
     * arithmetic (self-heal timeout, stale-loader WARN) so an NTP jump or a backwards wall-
     * clock tick can never make a SUSPENDED bundle appear younger or older than it actually
     * is. Both stamps are advanced together on every state transition.
     */
    private final long enteredCurrentStateAtNanos;

    public DSLRuntimeState(final String catalog, final String name, final String contentHash,
                       final LocalState localState, final LoaderGc loaderGc,
                       final String lastApplyError, final long lastAppliedAtMs,
                       final long enteredCurrentStateAtMs) {
        this(catalog, name, contentHash, localState, SuspendOrigin.NONE, loaderGc,
            lastApplyError, lastAppliedAtMs, enteredCurrentStateAtMs, System.nanoTime());
    }

    public DSLRuntimeState(final String catalog, final String name, final String contentHash,
                       final LocalState localState, final LoaderGc loaderGc,
                       final String lastApplyError, final long lastAppliedAtMs,
                       final long enteredCurrentStateAtMs,
                       final long enteredCurrentStateAtNanos) {
        this(catalog, name, contentHash, localState, SuspendOrigin.NONE, loaderGc,
            lastApplyError, lastAppliedAtMs, enteredCurrentStateAtMs, enteredCurrentStateAtNanos);
    }

    public DSLRuntimeState(final String catalog, final String name, final String contentHash,
                       final LocalState localState, final SuspendOrigin suspendOrigin,
                       final LoaderGc loaderGc,
                       final String lastApplyError, final long lastAppliedAtMs,
                       final long enteredCurrentStateAtMs,
                       final long enteredCurrentStateAtNanos) {
        this.catalog = catalog;
        this.name = name;
        this.contentHash = contentHash;
        this.localState = localState;
        this.suspendOrigin = suspendOrigin == null ? SuspendOrigin.NONE : suspendOrigin;
        this.loaderGc = loaderGc;
        this.lastApplyError = lastApplyError;
        this.lastAppliedAtMs = lastAppliedAtMs;
        this.enteredCurrentStateAtMs = enteredCurrentStateAtMs;
        this.enteredCurrentStateAtNanos = enteredCurrentStateAtNanos;
    }

    public static DSLRuntimeState running(final String catalog, final String name,
                                      final String contentHash, final long nowMs) {
        return new DSLRuntimeState(catalog, name, contentHash, LocalState.RUNNING,
            SuspendOrigin.NONE, LoaderGc.LIVE, null, nowMs, nowMs, System.nanoTime());
    }

    /**
     * First-time apply failed before any registration completed — nothing is live locally,
     * so {@link LocalState#NOT_LOADED} is the correct state, and {@code contentHash} is
     * deliberately left {@code null} so the dslManager tick's short-circuit (which compares
     * {@code prev.getContentHash()} against the DB's current hash) does NOT skip the file.
     * The next tick will re-classify and retry the apply.
     *
     * <p>Callers chain {@link #withApplyError} to record the diagnostic.
     */
    public static DSLRuntimeState failedFirstApply(final String catalog, final String name, final long nowMs) {
        return new DSLRuntimeState(catalog, name, /* contentHash */ null, LocalState.NOT_LOADED,
            SuspendOrigin.NONE, LoaderGc.LIVE, null, nowMs, nowMs, System.nanoTime());
    }

    public DSLRuntimeState withLocalState(final LocalState newState, final long nowMs) {
        if (this.localState == newState) {
            return this;
        }
        // Non-SUSPENDED transitions clear origin — it's only meaningful while SUSPENDED.
        // Callers flipping SUSPENDED origin should use withSuspendOrigin which handles the
        // paired SUSPENDED↔RUNNING flip when the origin lattice drains.
        final SuspendOrigin newOrigin = newState == LocalState.SUSPENDED
            ? suspendOrigin : SuspendOrigin.NONE;
        return new DSLRuntimeState(catalog, name, contentHash, newState, newOrigin, loaderGc,
            lastApplyError, lastAppliedAtMs, nowMs, System.nanoTime());
    }

    /**
     * Apply an origin mutation. Transitions:
     * <ul>
     *   <li>RUNNING + non-NONE origin → SUSPENDED with that origin.</li>
     *   <li>SUSPENDED + NONE origin → RUNNING (origin lattice drained).</li>
     *   <li>SUSPENDED + non-NONE origin → SUSPENDED with that origin (e.g., peer cleared
     *       but self still set → stays SUSPENDED, origin flips from BOTH to SELF).</li>
     * </ul>
     * {@code enteredCurrentState*} timestamps advance on every origin transition — not only
     * SUSPENDED↔RUNNING flips — because self-heal measures "how long has the bundle been at
     * its current effective (state, origin) tuple". In particular, when origin transitions
     * BOTH→PEER (local REST apply finishes, peer suspend still in effect), self-heal's
     * threshold must count from that moment, not from when the bundle first became
     * SUSPENDED with SELF origin. Otherwise self-heal fires prematurely before the PEER-only
     * window has actually elapsed.
     */
    public DSLRuntimeState withSuspendOrigin(final SuspendOrigin newOrigin, final long nowMs) {
        final SuspendOrigin effective = newOrigin == null ? SuspendOrigin.NONE : newOrigin;
        final LocalState newLocal = effective == SuspendOrigin.NONE
            ? (localState == LocalState.SUSPENDED ? LocalState.RUNNING : localState)
            : LocalState.SUSPENDED;
        if (newLocal == localState && effective == suspendOrigin) {
            return this;
        }
        return new DSLRuntimeState(catalog, name, contentHash, newLocal, effective, loaderGc,
            lastApplyError, lastAppliedAtMs,
            nowMs, System.nanoTime());
    }

    public DSLRuntimeState withLoaderGc(final LoaderGc newGc) {
        if (this.loaderGc == newGc) {
            return this;
        }
        return new DSLRuntimeState(catalog, name, contentHash, localState, suspendOrigin, newGc,
            lastApplyError, lastAppliedAtMs, enteredCurrentStateAtMs, enteredCurrentStateAtNanos);
    }

    public DSLRuntimeState withApplyError(final String err, final long nowMs) {
        return new DSLRuntimeState(catalog, name, contentHash, localState, suspendOrigin, loaderGc,
            err, nowMs, enteredCurrentStateAtMs, enteredCurrentStateAtNanos);
    }

    /**
     * Advance the bundle's content hash and mark this as a successful apply. Clears any
     * {@code lastApplyError} carried over from a previous failed attempt (a successful apply
     * on the new content means whatever error the old content raised is no longer relevant)
     * and stamps {@code lastAppliedAtMs} with the current wall-clock time.
     *
     * <p>No-op when the hash is unchanged — state is already current.
     */
    public DSLRuntimeState withContentHash(final String newHash, final long nowMs) {
        if (Objects.equals(this.contentHash, newHash)) {
            return this;
        }
        return new DSLRuntimeState(catalog, name, newHash, localState, suspendOrigin, loaderGc,
            /* lastApplyError */ null, /* lastAppliedAtMs */ nowMs,
            nowMs, System.nanoTime());
    }

    public String getCatalog() {
        return catalog;
    }

    public String getName() {
        return name;
    }

    public String getContentHash() {
        return contentHash;
    }

    public LocalState getLocalState() {
        return localState;
    }

    public SuspendOrigin getSuspendOrigin() {
        return suspendOrigin;
    }

    public LoaderGc getLoaderGc() {
        return loaderGc;
    }

    public String getLastApplyError() {
        return lastApplyError;
    }

    public long getLastAppliedAtMs() {
        return lastAppliedAtMs;
    }

    public long getEnteredCurrentStateAtMs() {
        return enteredCurrentStateAtMs;
    }

    public long getEnteredCurrentStateAtNanos() {
        return enteredCurrentStateAtNanos;
    }
}
