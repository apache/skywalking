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
 * Per-call policy + outcome for a storage model manipulation — threaded through the
 * {@link ModelRegistry} → {@link ModelRegistry.CreatingListener} → {@link ModelInstaller} call
 * chain. The {@link Mode} is set by the caller up-front; outcome entries are appended by the
 * installer as it examines each underlying storage resource (table, index, measure, index
 * rule, binding, template, etc.).
 *
 * <h2>Canonical profiles — always use a named factory</h2>
 * Four modes, each matching one distinct caller scenario. Use the factories; the
 * constructor is private. If a future scenario genuinely needs a fifth mode, add it to
 * {@link Mode} here so every caller keeps picking from a known set.
 *
 * <h3>{@link #withSchemaChange()} — {@link Mode#WITH_SCHEMA_CHANGE} (predicate: {@link #isWithSchemaChange()})</h3>
 * <p>Callers:
 * <ul>
 *   <li>Main-node REST apply ({@code /addOrUpdate}, {@code /delete}) — operator-driven,
 *       structural changes explicitly intended (recovery pushes use the same
 *       {@code /addOrUpdate} route with {@code allowStorageChange=true} +
 *       {@code force=true})</li>
 *   <li>Main-node reconciler tick for files that haven't yet converged via REST
 *       (rare — REST usually wins the race)</li>
 * </ul>
 * <p>Note: {@code /inactivate} is a soft-pause that goes through
 * {@link Mode#WITHOUT_SCHEMA_CHANGE} — backend schema and data are preserved; only
 * OAP-internal state (compiled bundles, dispatch, prototypes) is torn down so
 * cheap re-activation works on the next {@code /addOrUpdate}.
 * <p>Backend behaviour: full DDL — create missing tables / measures, drop retired ones,
 * auto-update BanyanDB {@code Measure} / {@code IndexRule} / {@code IndexRuleBinding} on
 * shape mismatch, and create / update index rules + bindings. Reshaping is treated as
 * intended because the caller came in through an on-demand operator request.
 *
 * <h3>{@link #schemaCreateIfAbsent()} — {@link Mode#SCHEMA_CREATE_IF_ABSENT} (predicate: {@link #isSchemaCreateIfAbsent()})</h3>
 * <p>Callers:
 * <ul>
 *   <li>Startup-time model registration (every OAP, via stream processors — static MAL /
 *       LAL files on disk)</li>
 * </ul>
 * <p>Backend behaviour: create resources that are absent; when a resource is present with
 * a shape that differs from what the model declares, record
 * {@link Outcome#SKIPPED_SHAPE_MISMATCH SKIPPED_SHAPE_MISMATCH} and <strong>do not</strong>
 * call update/reshape. Silent acceptance on reboot used to happen on BanyanDB
 * ({@code ALREADY_EXISTS} swallow) and JDBC (column-type changes undetected); explicit
 * skip surfaces the mismatch to the operator, who must reshape via the on-demand
 * runtime-rule REST endpoint (the only workflow that may change backend schema).
 *
 * <h3>{@link #verifySchemaOnly()} — {@link Mode#VERIFY_SCHEMA_ONLY} (predicate: {@link #isVerifySchemaOnly()})</h3>
 * <p>Callers:
 * <ul>
 *   <li>Boot-time runtime-rule reconciler pass on a cluster <em>peer</em> (a node that is
 *       not the hash-selected main for the file) — the main owns DDL, so this node must not
 *       perform it but must refuse to start if the backend isn't already in the shape the
 *       persisted runtime-rule catalog declares. Chosen by main-ness, not running mode, so a
 *       peer behaves the same in no-init and default mode.</li>
 * </ul>
 * <p>Backend behaviour: read-only inspection. The installer issues the same metadata
 * read RPCs as {@link Mode#SCHEMA_CREATE_IF_ABSENT} but never invokes create / update / drop. On
 * resource missing OR shape mismatch the installer throws — the exception propagates up
 * through the module bootstrap and causes the OAP process to exit, which under k8s
 * results in a pod backloop until either the init OAP has caught up or the operator has
 * fixed the rule files. This matches general OAP boot semantics for static models in
 * non-init mode: the OAP will not silently start with a backend that disagrees with
 * what's declared. Local {@code MetadataRegistry} is populated only when the live shape
 * matches the declared shape.
 *
 * <h3>{@link #withoutSchemaChange()} — {@link Mode#WITHOUT_SCHEMA_CHANGE} (predicate: {@link #isWithoutSchemaChange()})</h3>
 * <p>Callers:
 * <ul>
 *   <li>Peer-node reconciler tick (peer is not the hash-selected main for this file —
 *       main owns server-side DDL)</li>
 *   <li>Main-node REST {@code /inactivate} — soft-pause: backend schema + data are
 *       preserved, only OAP-internal state (compiled bundles, dispatch, prototypes) is
 *       torn down so re-activation on the next {@code /addOrUpdate} is cheap</li>
 * </ul>
 * <p>Backend behaviour: zero server RPCs. {@code BanyanDBIndexInstaller.isExists}
 * short-circuits: {@code parseMetadata} + populate {@code MetadataRegistry} + return
 * {@code allExist=true}. {@code whenCreating} / {@code whenRemoving} record
 * {@link Outcome#SKIPPED_NOT_ALLOWED SKIPPED_NOT_ALLOWED} outcomes instead of firing
 * {@code createTable} / {@code dropTable}. Peer's local MeterSystem still compiles
 * Metrics classes and populates {@code meterPrototypes} — that's pure in-JVM work the
 * opt doesn't (and shouldn't) gate. Differs from {@link Mode#VERIFY_SCHEMA_ONLY} in two
 * ways: no server RPCs (cache populates from local model), and missing / mismatched
 * resources are <strong>not</strong> a fatal error (the next tick will retry, or the
 * main will catch up).
 *
 * <h2>Why it's a single mutable object instead of separate policy/result</h2>
 * Installers can nest many resource operations per Model (BanyanDB Measure + N index rules
 * + binding + optional TopN; ES template + current index). The call chain passes one
 * object; the installer appends outcomes as each resource is examined. The caller reads
 * {@link #getOutcomes()} after the call returns to log or report.
 */
public final class StorageManipulationOpt {

    /**
     * Storage-manipulation mode. The installer branches once on this value to decide whether
     * server-side DDL (create / drop / update) is allowed. See the class Javadoc for the
     * scenario each mode covers.
     */
    public enum Mode {
        /**
         * Main-node on-demand path. Installer performs full DDL: create absent resources,
         * detect shape mismatch and apply the additive subset each backend supports
         * online ({@code client.update} for BanyanDB, add-column for JDBC, mapping append
         * for ES). Reshape is treated as intended because the caller explicitly asked
         * for it via the operator REST endpoint.
         */
        WITH_SCHEMA_CHANGE(Flags.builder()
            .inspectBackend(true)
            .createMissing(true)
            .updateOnMismatch(true)
            .dropOnRemoval(true)
            .escalateToCaller(true)
            .build()),
        /**
         * Static boot-time model registration, run by every OAP. On an init / standalone
         * OAP the installer creates absent resources, but if a resource already exists with
         * a shape that diverges from the declared model it records
         * {@link Outcome#SKIPPED_SHAPE_MISMATCH} and does <strong>not</strong> call
         * update / reshape. Operator must reconcile via the runtime-rule REST endpoint —
         * boot is not allowed to silently mutate backend shape.
         *
         * <p>This is the only mode that sets {@code deferDDLToInitNode}: on a {@code no-init}
         * OAP the installer defers to the init OAP (waits in the
         * {@link ModelInstaller#whenCreating} poll loop) rather than creating the resource
         * itself. The runtime-rule (DSL) modes never defer.
         */
        SCHEMA_CREATE_IF_ABSENT(Flags.builder()
            .inspectBackend(true)
            .createMissing(true)
            .deferDDLToInitNode(true)
            .build()),
        /**
         * Boot path on a non-init OAP. Installer issues the same read-only inspection
         * RPCs as {@link #SCHEMA_CREATE_IF_ABSENT} but never creates / updates / drops. On
         * resource missing or shape mismatch the installer <strong>throws</strong>; the
         * exception propagates up through module bootstrap and exits the process.
         * Under k8s this causes a pod backloop until the init OAP has caught up or the
         * operator has aligned rule files with the backend. Local {@code MetadataRegistry}
         * is populated only when the live shape matches the declared shape.
         */
        VERIFY_SCHEMA_ONLY(Flags.builder()
            .inspectBackend(true)
            .failOnAbsence(true)
            .failOnShapeMismatch(true)
            .build()),
        /**
         * Peer-node reconciler tick path. Zero server RPCs — local caches populate from
         * the declared model and the main is trusted to own backend DDL. Missing or
         * mismatched resources are not an error: the next tick will retry, and the main
         * will eventually converge. Distinct from {@link #VERIFY_SCHEMA_ONLY} in that
         * verification is skipped entirely, not run-and-fail.
         */
        WITHOUT_SCHEMA_CHANGE(Flags.builder().build());

        @Getter
        private final Flags flags;

        Mode(final Flags flags) {
            this.flags = flags;
        }
    }

    /**
     * Per-mode behavioural flags. Each control point in the install / remove pipeline
     * checks one flag instead of branching on the {@link Mode} value, so adding a new
     * mode is a matter of choosing flag values rather than auditing every {@code if
     * (opt.isXxx())} site. Flags are immutable and shared across all opts of the same
     * mode.
     *
     * <p>Each flag describes a distinct privilege the installer is granted by the
     * caller. They are independently composable on paper, but the canonical
     * combinations live on {@link Mode} — call sites should never construct a
     * {@code Flags} directly.</p>
     */
    @Builder
    @Getter
    public static final class Flags {
        /**
         * Issue read RPCs to the backend (existence + shape compare). False on
         * {@link Mode#WITHOUT_SCHEMA_CHANGE} where the contract is "zero server RPCs". When
         * false the installer must populate local caches from the declared model and
         * return early without inspecting the backend.
         */
        private final boolean inspectBackend;
        /**
         * Call backend create primitives ({@code client.define}, JDBC {@code CREATE
         * TABLE}, ES {@code createIndex}, BanyanDB {@code defineIndexRule} /
         * {@code defineIndexRuleBinding}) when a resource is absent.
         */
        private final boolean createMissing;
        /**
         * Call backend update primitives ({@code client.update}, JDBC {@code ALTER
         * TABLE}, ES mapping append) when a present resource's live shape diverges from
         * the declared shape. Only {@link Mode#WITH_SCHEMA_CHANGE} (the operator-driven path)
         * permits this — boot must never silently reshape backend storage.
         *
         * <p>Note: BanyanDB's index-rule / index-rule-binding update path is gated by
         * {@link #failOnShapeMismatch} instead of this flag, preserving the long-standing
         * behaviour that init-mode OAPs reconcile index rules even under
         * {@link Mode#SCHEMA_CREATE_IF_ABSENT}.</p>
         */
        private final boolean updateOnMismatch;
        /**
         * Call backend drop primitives ({@code client.dropMeasure} / {@code dropStream}
         * / etc.) from {@link ModelRegistry.CreatingListener#whenRemoving}. Only
         * {@link Mode#WITH_SCHEMA_CHANGE} (operator-driven runtime-rule deletion) permits
         * this; peers under {@link Mode#WITHOUT_SCHEMA_CHANGE} short-circuit with
         * {@link Outcome#SKIPPED_NOT_ALLOWED}.
         */
        private final boolean dropOnRemoval;
        /**
         * Throw a {@link org.apache.skywalking.oap.server.core.storage.StorageException}
         * when a resource is absent on the backend after inspection. Used by
         * {@link Mode#VERIFY_SCHEMA_ONLY} to fail boot rather than silently start
         * against an unprepared backend.
         */
        private final boolean failOnAbsence;
        /**
         * Throw a {@link org.apache.skywalking.oap.server.core.storage.StorageException}
         * when a present resource's live shape diverges from the declared shape. Used
         * by {@link Mode#VERIFY_SCHEMA_ONLY} so boot does not silently start against a
         * backend whose schema disagrees with the rule file.
         */
        private final boolean failOnShapeMismatch;
        /**
         * Re-throw cascaded backend errors to the caller (REST handler, operator
         * tooling) instead of swallowing them. Set on {@link Mode#WITH_SCHEMA_CHANGE}; other
         * modes log and continue so a peer-side bookkeeping glitch doesn't take down
         * the node.
         */
        private final boolean escalateToCaller;
        /**
         * On a {@code no-init} OAP, defer all backend DDL to the dedicated init OAP and wait
         * (poll loop in {@link ModelInstaller#whenCreating}) rather than create / update the
         * resource here. Set ONLY on {@link Mode#SCHEMA_CREATE_IF_ABSENT} — the static
         * boot-time model registration that every OAP runs. The init / no-init / default
         * running-mode axis governs <strong>static</strong> schema only.
         *
         * <p>The runtime-rule (DSL) opts — {@link Mode#WITH_SCHEMA_CHANGE},
         * {@link Mode#VERIFY_SCHEMA_ONLY}, {@link Mode#WITHOUT_SCHEMA_CHANGE} — leave this
         * {@code false}, so an operator-driven runtime apply is driven by the other flags and
         * by cluster main-ness, never by {@code RunningMode}. Without this distinction a
         * no-init OAP (every production cluster node) would route a runtime {@code withSchemaChange}
         * create into the init-node poll loop and block forever, because no init OAP knows
         * about a metric that was created at runtime.
         */
        private final boolean deferDDLToInitNode;
    }

    @Getter
    private final Mode mode;

    /** Per-resource outcomes appended as the installer examines each underlying resource.
     *  Read-only externally; copy-on-write so concurrent readers (e.g., metrics scrapers)
     *  never see a torn list. */
    private final List<ResourceOutcome> outcomes = new CopyOnWriteArrayList<>();

    /**
     * Behavioural flags for this opt. Convenience accessor — equivalent to
     * {@code getMode().getFlags()}. Call sites read individual flags (e.g.
     * {@code opt.getFlags().isCreateMissing()}) instead of pattern-matching on the
     * {@link Mode}.
     */
    public Flags getFlags() {
        return mode.getFlags();
    }

    public static StorageManipulationOpt withSchemaChange() {
        return new StorageManipulationOpt(Mode.WITH_SCHEMA_CHANGE);
    }

    public static StorageManipulationOpt schemaCreateIfAbsent() {
        return new StorageManipulationOpt(Mode.SCHEMA_CREATE_IF_ABSENT);
    }

    public static StorageManipulationOpt verifySchemaOnly() {
        return new StorageManipulationOpt(Mode.VERIFY_SCHEMA_ONLY);
    }

    public static StorageManipulationOpt withoutSchemaChange() {
        return new StorageManipulationOpt(Mode.WITHOUT_SCHEMA_CHANGE);
    }

    /**
     * {@link Mode#WITH_SCHEMA_CHANGE} but with the post-install schema fence DEFERRED. The
     * installer records each resource's {@code mod_revision} without fencing, then registers a
     * single flush via {@link #setDeferredFence(DeferredFence)}; the caller runs that flush ONCE
     * with {@link #runDeferredFence()} after the whole apply (e.g. a multi-rule file) so the
     * bundle waits on one barrier instead of one fence per metric/downsampling. All flags are
     * identical to {@link #withSchemaChange()} — only the create/update fence is batched; drops
     * still fence inline.
     */
    public static StorageManipulationOpt withSchemaChangeDeferredFence() {
        final StorageManipulationOpt opt = new StorageManipulationOpt(Mode.WITH_SCHEMA_CHANGE);
        opt.deferFence = true;
        return opt;
    }

    /**
     * {@link #withSchemaChangeDeferredFence()} with an explicit batched-fence timeout. Used by the
     * runtime-rule operator apply, which fences on a generous cluster-propagation budget (default
     * 3 min, configurable) instead of the installer's short inline default — the apply is async +
     * progress-queryable, so a long single wait is affordable. The inline/static/delete fence paths
     * (which never set a timeout here) keep the installer's short constant.
     *
     * @param timeout the batched-fence wait; passed to the backend via {@link #getFenceTimeoutMs()}.
     * @return a deferred-fence opt carrying {@code timeout}.
     */
    public static StorageManipulationOpt withSchemaChangeDeferredFence(final Duration timeout) {
        final StorageManipulationOpt opt = withSchemaChangeDeferredFence();
        opt.fenceTimeoutMs = timeout == null ? 0L : timeout.toMillis();
        return opt;
    }

    /**
     * True for {@link Mode#WITH_SCHEMA_CHANGE}. The on-demand operator workflow — drops,
     * updates, and reshapes are permitted because the caller explicitly asked for them.
     */
    public boolean isWithSchemaChange() {
        return mode == Mode.WITH_SCHEMA_CHANGE;
    }

    /**
     * True for {@link Mode#SCHEMA_CREATE_IF_ABSENT}. The static boot workflow — create absent
     * resources, skip + record {@link Outcome#SKIPPED_SHAPE_MISMATCH} on a resource that
     * already exists with a different shape. Never update or drop.
     */
    public boolean isSchemaCreateIfAbsent() {
        return mode == Mode.SCHEMA_CREATE_IF_ABSENT;
    }

    /**
     * True for {@link Mode#VERIFY_SCHEMA_ONLY}. Boot-time strict verification on a
     * non-init OAP — installer issues read-only inspection RPCs and throws on missing or
     * shape-mismatched resources. No DDL.
     */
    public boolean isVerifySchemaOnly() {
        return mode == Mode.VERIFY_SCHEMA_ONLY;
    }

    /**
     * True for {@link Mode#WITHOUT_SCHEMA_CHANGE}. The {@code BanyanDBIndexInstaller.isExists}
     * short-circuit reads this to skip every server RPC and populate
     * {@code MetadataRegistry} only.
     */
    public boolean isWithoutSchemaChange() {
        return mode == Mode.WITHOUT_SCHEMA_CHANGE;
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
     * {@link #withSchemaChangeDeferredFence()} opt instead of fencing per resource; the apply
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
     * True only for {@link #withSchemaChangeDeferredFence()}. The installer reads this to skip
     * the per-resource create/update fence and register a single {@link DeferredFence} instead.
     */
    @Getter
    private boolean deferFence = false;

    private volatile DeferredFence deferredFence;

    /**
     * Batched-fence wait in millis, or {@code 0} (the default) meaning "use the backend installer's
     * own short constant". Set only by {@link #withSchemaChangeDeferredFence(Duration)} on the
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
     * True when every recorded outcome is benign — resource is matched or was created /
     * updated / dropped per policy. False when any outcome is {@link Outcome#MISSING},
     * {@link Outcome#EXISTING_MISMATCH}, or {@link Outcome#SKIPPED_NOT_ALLOWED}.
     */
    public boolean isAllOk() {
        for (final ResourceOutcome o : outcomes) {
            switch (o.getStatus()) {
                case MISSING:
                case EXISTING_MISMATCH:
                case SKIPPED_NOT_ALLOWED:
                case SKIPPED_SHAPE_MISMATCH:
                    return false;
                default:
                    break;
            }
        }
        return true;
    }

    /**
     * True when at least one recorded outcome is {@link Outcome#SKIPPED_SHAPE_MISMATCH}.
     * Callers (notably {@code MeterSystem.create} / {@code StorageModels.add}) read this
     * after firing the {@code whenCreating} chain to decide whether to proceed with local
     * registration or roll it back — a shape-mismatched metric must not be registered
     * because its backend-declared schema disagrees with what's declared in the rule file.
     */
    public boolean hasShapeMismatch() {
        for (final ResourceOutcome o : outcomes) {
            if (o.getStatus() == Outcome.SKIPPED_SHAPE_MISMATCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * First {@link Outcome#SKIPPED_SHAPE_MISMATCH} outcome recorded, or {@code null} if
     * none. Used to surface the diff on {@code /runtime/rule/list} and in error responses.
     */
    public ResourceOutcome firstShapeMismatch() {
        for (final ResourceOutcome o : outcomes) {
            if (o.getStatus() == Outcome.SKIPPED_SHAPE_MISMATCH) {
                return o;
            }
        }
        return null;
    }

    public enum Outcome {
        /** The resource was not present on storage and creation was either not attempted
         *  (policy) or deferred to a later step in the chain. */
        MISSING,
        /** Resource present and matches the intended shape. No action taken. */
        EXISTING_MATCHED,
        /** Resource present but live shape differs from intended; update was NOT applied
         *  because the caller is in {@link Mode#WITHOUT_SCHEMA_CHANGE}. Caller may re-push with
         *  {@link #withSchemaChange()} to reconcile. {@link ResourceOutcome#getDiff()} carries
         *  a short description of the difference. */
        EXISTING_MISMATCH,
        /** Installer ran {@code createTable} (or equivalent) and the resource now exists. */
        CREATED,
        /** Installer ran {@code client.update} (BanyanDB) or mapping-append (ES) to
         *  reconcile live shape with intended. {@link ResourceOutcome#getDiff()} carries
         *  a short description of what was updated. */
        UPDATED,
        /** Installer ran {@code dropTable} and the resource is no longer present. */
        DROPPED,
        /** Installer intended to act (create, drop, update) but was blocked by policy.
         *  {@link ResourceOutcome#getDiff()} carries the reason. */
        SKIPPED_NOT_ALLOWED,
        /** Boot-time shape mismatch — backend already holds a resource with the same name
         *  but a different shape than the declared model. The installer did NOT drop, did
         *  NOT update, and did NOT register; the operator must reconcile explicitly via
         *  the on-demand runtime-rule {@code /addOrUpdate} endpoint (only workflow that
         *  may change backend schema). {@link ResourceOutcome#getDiff()} carries the
         *  declared-vs-backend diff for operator inspection. */
        SKIPPED_SHAPE_MISMATCH
    }

    @Getter
    public static final class ResourceOutcome {
        /** Short label for the underlying resource kind — e.g. "measure", "stream",
         *  "property", "indexRule", "indexRuleBinding", "topN", "template", "index",
         *  "table", "additionalTable". Operator-facing; kept lower-case. */
        private final String resourceType;
        /** Fully-qualified resource name as the backend sees it (group + name for
         *  BanyanDB; index name for ES; table name for JDBC). */
        private final String resourceName;
        private final Outcome status;
        /** Non-null on {@link Outcome#EXISTING_MISMATCH}, {@link Outcome#UPDATED},
         *  {@link Outcome#SKIPPED_NOT_ALLOWED}. Null otherwise. */
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