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

package org.apache.skywalking.oap.server.core.storage.model;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.storage.StorageException;

/**
 * Per-call storage policy, plus the outcome of every resource the installer examined. It travels through
 * {@link ModelRegistry} → {@link ModelRegistry.CreatingListener} → {@link ModelInstaller}: the caller picks a
 * {@link Mode} through a named factory, the installer appends one {@link ResourceOutcome} per resource (table,
 * index, template, measure, stream, trace, property) it checks, and {@link ModelInstaller} logs them.
 *
 * <h2>Where each mode runs in an OAP cluster</h2>
 * Two stages change storage. <b>Boot</b> registers models while an OAP starts. It runs in production with no
 * operator watching, so it applies additive changes only. <b>Runtime</b> is the runtime-rule REST API (MAL and LAL
 * hot update). An operator drives it, so a breaking change goes through once the operator allows it
 * ({@code allowStorageChange=true}). Runtime only ever changes MAL measures: LAL rules write into record types
 * defined in code.
 * <table border="1">
 * <caption>OAP (role, doing) → mode → outcomes it records</caption>
 * <tr><th>Stage</th><th>OAP (role, doing)</th><th>Mode</th><th>Outcomes</th></tr>
 * <tr><td rowspan="2">Boot</td>
 *     <td>OAP (init or default mode, registering models at startup, persisted runtime overrides of bundled rule
 *         files included; the init OAP also registers runtime-only rules)</td>
 *     <td>{@link Mode#BOOT_INSTALL}</td>
 *     <td>{@code CREATED}, {@code UPDATED}, {@code MATCHED}, {@code BOOT_REFUSED_BREAKING_CHANGE}</td></tr>
 * <tr><td>OAP (no-init, registering models at startup)</td>
 *     <td>{@link Mode#BOOT_INSTALL}</td>
 *     <td>no DDL: it waits until the init OAP has created the resources; then {@code MATCHED} on JDBC and
 *         Elasticsearch</td></tr>
 * <tr><td rowspan="4">Runtime</td>
 *     <td>OAP (main, applying {@code /addOrUpdate} or {@code /delete?mode=revertToBundled}, or a reconcile tick,
 *         the one at startup included)</td>
 *     <td>{@link Mode#RUNTIME_APPLY}</td>
 *     <td>{@code CREATED}, {@code UPDATED}, {@code DROPPED}, {@code MATCHED}; {@code RUNTIME_BACKEND_UNTOUCHED} when
 *         JDBC or Elasticsearch removes a metric (they keep the table or index)</td></tr>
 * <tr><td>OAP (main, {@code /inactivate}, the local steps of a revert to a bundled rule, reinstalling a rule
 *         locally)</td>
 *     <td>{@link Mode#RUNTIME_LOCAL_ONLY}</td>
 *     <td>{@code RUNTIME_BACKEND_UNTOUCHED}</td></tr>
 * <tr><td>OAP (not-main, starting up)</td>
 *     <td>{@link Mode#RUNTIME_PEER_VERIFY}</td>
 *     <td>{@code MATCHED}, {@code RUNTIME_PEER_SCHEMA_ABSENT}, {@code RUNTIME_PEER_SCHEMA_DIFFERENT};
 *         {@code RUNTIME_BACKEND_UNTOUCHED} for the rule's other models, rolled back when one fails</td></tr>
 * <tr><td>OAP (not-main, reconcile tick)</td>
 *     <td>{@link Mode#RUNTIME_LOCAL_ONLY}</td>
 *     <td>{@code RUNTIME_BACKEND_UNTOUCHED}</td></tr>
 * </table>
 * A not-main OAP never applies a runtime change itself: it forwards REST calls to the main OAP. An OAP that sees no
 * peer yet (an empty cluster list at startup) counts itself main.
 *
 * <h2>Why one mutable object</h2>
 * An installer examines several resources per model (a BanyanDB measure with its index rules, binding and TopN; an
 * Elasticsearch template and index), so the policy and the outcomes travel together through one call chain.
 */
public final class StorageManipulationOpt {

    /**
     * The stage a storage change happens in. See the class Javadoc for which OAP runs which stage.
     */
    public enum Stage {
        /** Registering models while an OAP starts. Additive changes only. */
        BOOT,
        /** The runtime-rule REST API and its reconciler. Breaking changes need the operator's approval. */
        RUNTIME
    }

    /**
     * What an installer may do to the backend. Each mode belongs to one {@link Stage}; installers read its
     * {@link Flags} rather than branching on the mode.
     */
    public enum Mode {
        /**
         * Boot.
         * <ul>
         *   <li>OAP (init or default mode, registering models at startup) → creates absent resources, extends a
         *       resource that lacks declared columns, tags or fields, and refuses a breaking difference.</li>
         *   <li>OAP (no-init, registering models at startup) → no DDL; waits until the init OAP has created the
         *       resources.</li>
         * </ul>
         */
        BOOT_INSTALL(Stage.BOOT, Flags.builder()
            .inspectBackend(true)
            .createMissing(true)
            .applyAdditiveOnMismatch(true)
            .deferDDLToInitNode(true)
            .build()),
        /**
         * Runtime.
         * <ul>
         *   <li>OAP (main, applying {@code /addOrUpdate} or {@code /delete?mode=revertToBundled}, or a reconcile
         *       tick) → full DDL: creates, updates and drops. A breaking rule edit reaches storage only once the
         *       operator allowed it, as a drop followed by a create.</li>
         *   <li>OAP (not-main) → never; it forwards REST calls to the main OAP.</li>
         * </ul>
         */
        RUNTIME_APPLY(Stage.RUNTIME, Flags.builder()
            .inspectBackend(true)
            .createMissing(true)
            .updateOnMismatch(true)
            .dropOnRemoval(true)
            .escalateToCaller(true)
            .build()),
        /**
         * Runtime.
         * <ul>
         *   <li>OAP (main) → never.</li>
         *   <li>OAP (not-main, starting up) → read-only check that the main OAP has already put the persisted
         *       runtime rules' schema in place. An absent or different resource fails that rule's apply on this
         *       OAP for this pass (logged); a later reconcile tick loads the rule locally
         *       ({@link #RUNTIME_LOCAL_ONLY}), trusting the main OAP.</li>
         * </ul>
         */
        RUNTIME_PEER_VERIFY(Stage.RUNTIME, Flags.builder()
            .inspectBackend(true)
            .failOnAbsence(true)
            .failOnShapeMismatch(true)
            .build()),
        /**
         * Runtime. The backend is never called.
         * <ul>
         *   <li>OAP (main, {@code /inactivate}, the local steps of a revert to a bundled rule, reinstalling a rule
         *       locally) → storage is kept as it is; only this OAP's own state changes.</li>
         *   <li>OAP (not-main, reconcile tick) → the main OAP owns the DDL; this OAP refreshes its local schema
         *       cache.</li>
         * </ul>
         */
        RUNTIME_LOCAL_ONLY(Stage.RUNTIME, Flags.builder().build());

        @Getter
        private final Stage stage;
        @Getter
        private final Flags flags;

        Mode(final Stage stage, final Flags flags) {
            this.stage = stage;
            this.flags = flags;
        }
    }

    /**
     * What a {@link Mode} allows, one flag per control point in the install / remove pipeline, so a new mode is a
     * choice of flag values rather than an audit of every call site. Only {@link Mode} builds them.
     */
    @Builder
    @Getter
    public static final class Flags {
        /**
         * Read the backend (existence and shape). Off on {@link Mode#RUNTIME_LOCAL_ONLY}, whose contract is "no
         * backend call": the installer only fills local caches from the declared model.
         */
        private final boolean inspectBackend;
        /**
         * Create an absent resource ({@code client.define}, JDBC {@code CREATE TABLE}, Elasticsearch
         * {@code createIndex}, BanyanDB {@code defineIndexRule} / {@code defineIndexRuleBinding}).
         */
        private final boolean createMissing;
        /**
         * Apply any difference between a present BanyanDB measure, stream, trace or property and its declared
         * shape ({@code client.update}). {@link Mode#RUNTIME_APPLY} only.
         *
         * <p>BanyanDB's index-rule / index-rule-binding / TopN update is gated by {@link #failOnShapeMismatch}
         * instead, so an init or default-mode OAP keeps reconciling those at boot as it always has. JDBC and
         * Elasticsearch read neither update flag: whenever they create, they add the columns or mapping fields a
         * present table or index lacks, and never remove or retype one.</p>
         */
        private final boolean updateOnMismatch;
        /**
         * Apply a difference to a present BanyanDB measure, stream, trace or property only when it is additive: new
         * tags or fields, nothing removed or retyped (a property is schemaless, so any change).
         * {@link Mode#BOOT_INSTALL} only; a breaking difference is recorded as
         * {@link Outcome#BOOT_REFUSED_BREAKING_CHANGE}.
         */
        private final boolean applyAdditiveOnMismatch;
        /**
         * Drop the backend resource from {@link ModelRegistry.CreatingListener#whenRemoving}.
         * {@link Mode#RUNTIME_APPLY} only; {@link Mode#RUNTIME_LOCAL_ONLY} records
         * {@link Outcome#RUNTIME_BACKEND_UNTOUCHED} instead.
         */
        private final boolean dropOnRemoval;
        /**
         * Throw a {@link StorageException} when a resource is absent. {@link Mode#RUNTIME_PEER_VERIFY}: a not-main
         * OAP doesn't apply the rule until the main OAP has created the resource; the rule's apply fails on this OAP
         * and a later reconcile tick loads it locally.
         */
        private final boolean failOnAbsence;
        /**
         * Throw a {@link StorageException} when a present resource's shape differs.
         * {@link Mode#RUNTIME_PEER_VERIFY}: a not-main OAP doesn't apply the rule until the main OAP has applied the
         * shape; the rule's apply fails on this OAP and a later reconcile tick loads it locally.
         */
        private final boolean failOnShapeMismatch;
        /**
         * Re-throw backend errors to the caller (the REST handler) instead of logging and continuing.
         * {@link Mode#RUNTIME_APPLY} only.
         */
        private final boolean escalateToCaller;
        /**
         * On a no-init OAP, leave all DDL to the init OAP and wait for the resources (the poll loop in
         * {@link ModelInstaller#whenCreating}). {@link Mode#BOOT_INSTALL} only: the init / no-init / default
         * running mode governs boot. Runtime modes never defer, because no init OAP knows about a metric created
         * at runtime; a no-init OAP routed into the wait loop by a runtime apply would wait forever.
         */
        private final boolean deferDDLToInitNode;
    }

    @Getter
    private final Mode mode;

    /** Per-resource outcomes, in the order the installer examined the resources. Copy-on-write, so a reader
     *  never sees a torn list. */
    private final List<ResourceOutcome> outcomes = new CopyOnWriteArrayList<>();

    /**
     * Behavioural flags for this opt, equivalent to {@code getMode().getFlags()}. Call sites read individual flags
     * (e.g. {@code opt.getFlags().isCreateMissing()}) instead of pattern-matching on the {@link Mode}.
     */
    public Flags getFlags() {
        return mode.getFlags();
    }

    /** {@link Mode#BOOT_INSTALL}: model registration while an OAP starts. */
    public static StorageManipulationOpt bootInstall() {
        return new StorageManipulationOpt(Mode.BOOT_INSTALL);
    }

    /** {@link Mode#RUNTIME_APPLY}: the main OAP applying a runtime-rule change. */
    public static StorageManipulationOpt runtimeApply() {
        return new StorageManipulationOpt(Mode.RUNTIME_APPLY);
    }

    /** {@link Mode#RUNTIME_PEER_VERIFY}: a not-main OAP checking the main OAP's work at startup. */
    public static StorageManipulationOpt runtimePeerVerify() {
        return new StorageManipulationOpt(Mode.RUNTIME_PEER_VERIFY);
    }

    /** {@link Mode#RUNTIME_LOCAL_ONLY}: a runtime change to this OAP's own state, with no backend call. */
    public static StorageManipulationOpt runtimeLocalOnly() {
        return new StorageManipulationOpt(Mode.RUNTIME_LOCAL_ONLY);
    }

    /**
     * {@link Mode#RUNTIME_APPLY} with the post-install schema fence DEFERRED. The
     * installer records each resource's {@code mod_revision} without fencing, then registers a
     * single flush via {@link #setDeferredFence(DeferredFence)}; the caller runs that flush ONCE
     * with {@link #runDeferredFence()} after the whole apply (e.g. a multi-rule file) so the
     * bundle waits on one barrier instead of one fence per metric/downsampling. All flags are
     * identical to {@link #runtimeApply()} — only the create/update fence is batched; drops
     * still fence inline.
     */
    public static StorageManipulationOpt runtimeApplyDeferredFence() {
        final StorageManipulationOpt opt = new StorageManipulationOpt(Mode.RUNTIME_APPLY);
        opt.deferFence = true;
        return opt;
    }

    /**
     * {@link #runtimeApplyDeferredFence()} with an explicit batched-fence timeout. Used by the
     * runtime-rule operator apply, which fences on a generous cluster-propagation budget (default
     * 3 min, configurable) instead of the installer's short inline default — the apply is async +
     * progress-queryable, so a long single wait is affordable. The inline/static/delete fence paths
     * (which never set a timeout here) keep the installer's short constant.
     *
     * @param timeout the batched-fence wait; passed to the backend via {@link #getFenceTimeoutMs()}.
     * @return a deferred-fence opt carrying {@code timeout}.
     */
    public static StorageManipulationOpt runtimeApplyDeferredFence(final Duration timeout) {
        final StorageManipulationOpt opt = runtimeApplyDeferredFence();
        opt.fenceTimeoutMs = timeout == null ? 0L : timeout.toMillis();
        return opt;
    }

    /** True for {@link Mode#RUNTIME_APPLY}. */
    public boolean isRuntimeApply() {
        return mode == Mode.RUNTIME_APPLY;
    }

    /** True for {@link Mode#BOOT_INSTALL}. */
    public boolean isBootInstall() {
        return mode == Mode.BOOT_INSTALL;
    }

    /** True for {@link Mode#RUNTIME_PEER_VERIFY}. */
    public boolean isRuntimePeerVerify() {
        return mode == Mode.RUNTIME_PEER_VERIFY;
    }

    /** True for {@link Mode#RUNTIME_LOCAL_ONLY}. */
    public boolean isRuntimeLocalOnly() {
        return mode == Mode.RUNTIME_LOCAL_ONLY;
    }

    private StorageManipulationOpt(final Mode mode) {
        this.mode = mode;
    }

    /**
     * Highest etcd {@code mod_revision} returned by any registry write performed
     * during this opt's lifetime. Backends that expose a global revision (BanyanDB
     * via the schema-barrier service) accumulate per-write revisions here so the
     * post-install fence can wait on a single value. Backends without a revision
     * concept leave it at {@link #DEFAULT_MOD_REVISION} (0) and the fence is a
     * no-op.
     */
    private final AtomicLong maxModRevision = new AtomicLong(0L);

    /** Sentinel returned by {@link #getMaxModRevision()} when no DDL was performed. */
    public static final long DEFAULT_MOD_REVISION = 0L;

    /**
     * Record an etcd mod_revision returned by a registry write. The opt keeps the
     * maximum so the caller can fence on a single revision after the install pass.
     */
    public void recordModRevision(final long rev) {
        if (rev <= 0L) {
            return;
        }
        maxModRevision.accumulateAndGet(rev, Math::max);
    }

    /**
     * Highest mod_revision recorded so far, or {@link #DEFAULT_MOD_REVISION} if no
     * write produced one. Callers that need to fence subsequent data writes /
     * queries against the new schema pass this to
     * {@code SchemaWatcher.awaitRevisionApplied}.
     */
    public long getMaxModRevision() {
        return maxModRevision.get();
    }

    /**
     * A storage-backend schema fence whose execution is deferred to the end of a batched
     * apply. The backend installer (e.g. BanyanDB) registers one on a
     * {@link #runtimeApplyDeferredFence()} opt instead of fencing per resource; the apply
     * orchestration runs it once via {@link #runDeferredFence()}. Implemented as a closure in
     * the storage plugin so core stays backend-agnostic (same pattern as the local-cache
     * populator). A timeout inside the fence is a non-fatal WARN; only a barrier transport
     * error surfaces as {@link StorageException}.
     */
    @FunctionalInterface
    public interface DeferredFence {
        void await() throws StorageException;
    }

    /**
     * True only for {@link #runtimeApplyDeferredFence()}. The installer reads this to skip
     * the per-resource create/update fence and register a single {@link DeferredFence} instead.
     */
    @Getter
    private boolean deferFence = false;

    private volatile DeferredFence deferredFence;

    /**
     * Batched-fence wait in millis, or {@code 0} (the default) meaning "use the backend installer's
     * own short constant". Set only by {@link #runtimeApplyDeferredFence(Duration)} on the
     * runtime-rule operator path; the backend reads it when running the deferred fence so the
     * inline/static/delete paths (which leave it {@code 0}) keep the short timeout.
     */
    @Getter
    private long fenceTimeoutMs = 0L;

    /**
     * True when the CALLER (the apply orchestrator) runs {@link #runDeferredFence()} itself —
     * typically on a background thread after the durable commit — rather than the backend installer
     * running it inline at the end of the apply. The runtime-rule REST apply sets this so the long
     * (3-min) fence does not block the apply / hold peers suspended; the reconciler tick leaves it
     * {@code false} so the installer keeps fencing inline with the short timeout.
     */
    @Getter
    @Setter
    private boolean fenceRunByCaller = false;

    /**
     * Outcome of a deferred fence, recorded by the backend so the orchestrator can mark
     * {@code APPLIED} vs {@code DEGRADED}-with-laggards after {@link #runDeferredFence()} returns.
     */
    public static final class FenceOutcome {
        @Getter
        private final boolean applied;
        @Getter
        private final List<String> laggardNodeIds;

        public FenceOutcome(final boolean applied, final List<String> laggardNodeIds) {
            this.applied = applied;
            this.laggardNodeIds = laggardNodeIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(laggardNodeIds);
        }
    }

    /** Recorded by the backend during {@link #runDeferredFence()}; read by the orchestrator after.
     *  Null when no deferred fence ran (no DDL) or the backend records no outcome. */
    @Getter
    @Setter
    private volatile FenceOutcome fenceOutcome;

    /**
     * Register the single fence to run after the batched apply completes. Idempotent — the
     * installer may call it once per resource; the latest (equivalent) closure wins. No-op
     * carrier for backends without a revision concept (they never call it).
     */
    public void setDeferredFence(final DeferredFence fence) {
        this.deferredFence = fence;
    }

    /**
     * Run the registered {@link DeferredFence} once, if any. Called by the apply orchestration
     * after all DDL for the batch is fired. No-op when nothing was registered (peer/no-change
     * applies, or non-BanyanDB backends).
     *
     * <p><strong>One-shot.</strong> A single reconciler tick reuses ONE opt across every rule
     * file ({@code RuleSync#runOnce}), calling this once per file. The closure + accumulated
     * {@link #maxModRevision} are <strong>always</strong> reset (even when this file performed no
     * DDL and registered no closure), so the next file neither re-runs this file's stale fence nor
     * inherits this file's revision — each file fences on its own DDL only. (Drop revisions that a
     * later commit-tail records on a shared opt are inline-fenced at drop time and benign here: the
     * next file's own create revision is monotonically higher, so it dominates the fence.) The reset
     * is in a {@code finally} so a fence transport failure still isolates the next file; the closure
     * reads {@link #getMaxModRevision()} during {@code await()}, so it is reset only after.
     *
     * <p>{@link #fenceOutcome} is cleared <em>before</em> the fence runs (so a shared tick opt
     * starts each file clean) and the backend sets it <em>during</em> the run; it is intentionally
     * NOT cleared afterward so the caller can read it (the runtime-rule orchestrator reads it to
     * decide {@code APPLIED} vs {@code DEGRADED}).
     */
    public void runDeferredFence() throws StorageException {
        final DeferredFence fence = this.deferredFence;
        this.deferredFence = null;
        this.fenceOutcome = null;
        try {
            if (fence != null) {
                fence.await();
            }
        } finally {
            maxModRevision.set(0L);
        }
    }

    /**
     * Append a per-resource outcome. Called by the installer as it examines each
     * underlying storage resource.
     */
    public void recordOutcome(final String resourceType, final String resourceName,
                              final Outcome status, final String diff) {
        outcomes.add(new ResourceOutcome(resourceType, resourceName, status, diff));
    }

    /** Read-only view of outcomes recorded so far, in the order the installer visited them. */
    public List<ResourceOutcome> getOutcomes() {
        return Collections.unmodifiableList(outcomes);
    }

    /**
     * First {@link Outcome#BOOT_REFUSED_BREAKING_CHANGE} recorded at or after index {@code from}, or {@code null}.
     * A caller that keeps a model off on a refusal ({@code MetricsStreamProcessor}, {@code MeterSystem}) passes the
     * outcome count it saw before its own call: one opt can serve many models (a MAL rule file shares one across its
     * metrics), and another model's refusal must not switch this one off.
     */
    public ResourceOutcome firstBootRefusalSince(final int from) {
        return firstSince(from, Outcome.BOOT_REFUSED_BREAKING_CHANGE, Outcome.BOOT_REFUSED_BREAKING_CHANGE);
    }

    /**
     * First {@link Outcome#RUNTIME_PEER_SCHEMA_ABSENT} or {@link Outcome#RUNTIME_PEER_SCHEMA_DIFFERENT} recorded at
     * or after index {@code from}, or {@code null}. A not-main OAP fails the rule's apply on one
     * ({@link ModelInstaller#whenCreating}).
     */
    public ResourceOutcome firstPeerVerifyFailureSince(final int from) {
        return firstSince(from, Outcome.RUNTIME_PEER_SCHEMA_ABSENT, Outcome.RUNTIME_PEER_SCHEMA_DIFFERENT);
    }

    private ResourceOutcome firstSince(final int from, final Outcome one, final Outcome other) {
        for (int i = from; i < outcomes.size(); i++) {
            final ResourceOutcome o = outcomes.get(i);
            if (o.getStatus() == one || o.getStatus() == other) {
                return o;
            }
        }
        return null;
    }

    /**
     * The outcome for a present resource whose difference this opt doesn't let the installer apply: refused at
     * boot, or not yet applied by the main OAP when a not-main OAP verifies.
     *
     * @return {@link Outcome#BOOT_REFUSED_BREAKING_CHANGE} or {@link Outcome#RUNTIME_PEER_SCHEMA_DIFFERENT}
     * @throws IllegalStateException for a mode that applies every difference or never inspects the backend
     */
    public Outcome unappliedDifference() {
        switch (mode) {
            case BOOT_INSTALL:
                return Outcome.BOOT_REFUSED_BREAKING_CHANGE;
            case RUNTIME_PEER_VERIFY:
                return Outcome.RUNTIME_PEER_SCHEMA_DIFFERENT;
            default:
                throw new IllegalStateException(mode + " never leaves a difference unapplied");
        }
    }

    /**
     * What happened to one storage resource. Each value lists the OAPs that record it, in the class Javadoc's
     * terms.
     */
    public enum Outcome {
        /**
         * Checked; the live resource has the declared shape.
         * <ul>
         *   <li>OAP (init or default mode, boot) → nothing to do.</li>
         *   <li>OAP (no-init, boot) → the init OAP has created the resource (JDBC and Elasticsearch).</li>
         *   <li>OAP (main, runtime apply) → nothing to do.</li>
         *   <li>OAP (not-main, startup check) → the main OAP has applied this resource.</li>
         * </ul>
         */
        MATCHED,
        /**
         * The resource was absent and is now created.
         * <ul>
         *   <li>OAP (init or default mode, boot) → created at startup.</li>
         *   <li>OAP (main, runtime apply) → created for a new or changed rule.</li>
         * </ul>
         */
        CREATED,
        /**
         * The resource existed with a different shape and is now updated: a BanyanDB schema update, a JDBC
         * {@code ADD COLUMN}, an Elasticsearch mapping append. {@link ResourceOutcome#getDiff()} says what changed.
         * <ul>
         *   <li>OAP (init or default mode, boot) → an additive change: new columns, tags or fields. Any change for a
         *       BanyanDB property, which is schemaless, and for a BanyanDB group, index rule, binding or TopN
         *       aggregation, which boot has always reconciled.</li>
         *   <li>OAP (main, runtime apply) → the change the rule edit carried.</li>
         * </ul>
         */
        UPDATED,
        /**
         * The resource is removed.
         * <ul>
         *   <li>OAP (main, runtime apply on BanyanDB) → dropped by a rule edit that retires a metric, or by a
         *       revert to a bundled rule that drops runtime-only metrics. A plain {@code /delete} keeps the
         *       resource.</li>
         * </ul>
         */
        DROPPED,
        /**
         * Boot found a breaking difference on a BanyanDB measure, stream or trace (a removed or retyped tag or field,
         * a different entity) and left the resource as it is; its index rules and binding are left as they are too,
         * with no outcome of their own. {@code MetricsStreamProcessor} and {@code MeterSystem} keep a metric off on it
         * when they register the metric after the storage module added its listener.
         * <ul>
         *   <li>OAP (init or default mode, boot) → refused: boot applies additive changes only.</li>
         * </ul>
         */
        BOOT_REFUSED_BREAKING_CHANGE,
        /**
         * <ul>
         *   <li>OAP (not-main, startup check) → the main OAP hasn't created this resource yet; the rule's apply
         *       fails on this OAP for this pass.</li>
         * </ul>
         */
        RUNTIME_PEER_SCHEMA_ABSENT,
        /**
         * <ul>
         *   <li>OAP (not-main, startup check) → the live shape differs from the persisted rule: the main OAP hasn't
         *       applied it yet; the rule's apply fails on this OAP for this pass.</li>
         * </ul>
         */
        RUNTIME_PEER_SCHEMA_DIFFERENT,
        /**
         * The backend was deliberately not called.
         * <ul>
         *   <li>OAP (main, {@code /inactivate}, the local steps of a revert to a bundled rule, reinstalling a rule
         *       locally) → storage is kept as it is.</li>
         *   <li>OAP (main, runtime apply on JDBC or Elasticsearch, removing a metric) → tables and indices are kept:
         *       those storages never drop them.</li>
         *   <li>OAP (not-main, reconcile tick) → the main OAP owns the DDL; only this OAP's local state is set
         *       up.</li>
         *   <li>OAP (not-main, startup check) → a rule's other models, rolled back when one fails its check.</li>
         * </ul>
         */
        RUNTIME_BACKEND_UNTOUCHED
    }

    @Getter
    public static final class ResourceOutcome {
        /** Short, lower-case label for the resource kind: "group", "measure", "stream", "trace", "property",
         *  "indexRule", "indexRuleBinding", "topN", "template", "index", "table", "additionalTable"; "model" when the
         *  outcome covers the whole model rather than one resource. */
        private final String resourceType;
        /** The resource's name as the backend sees it (BanyanDB group + name; Elasticsearch index name; JDBC
         *  table name). */
        private final String resourceName;
        private final Outcome status;
        /** What changed, or why nothing was changed; {@code null} when there is nothing to add. */
        private final String diff;

        public ResourceOutcome(final String resourceType, final String resourceName,
                                final Outcome status, final String diff) {
            this.resourceType = resourceType;
            this.resourceName = resourceName;
            this.status = status;
            this.diff = diff;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(resourceType).append('(').append(resourceName).append(")=").append(status);
            if (diff != null) {
                sb.append("[").append(diff).append("]");
            }
            return sb.toString();
        }
    }
}
