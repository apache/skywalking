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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.Getter;

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
 * <h3>{@link #fullInstall()} — {@link Mode#FULL_INSTALL} (predicate: {@link #isFullInstall()})</h3>
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
 * {@link Mode#LOCAL_CACHE_ONLY} — backend schema and data are preserved; only
 * OAP-internal state (compiled bundles, dispatch, prototypes) is torn down so
 * cheap re-activation works on the next {@code /addOrUpdate}.
 * <p>Backend behaviour: full DDL — create missing tables / measures, drop retired ones,
 * auto-update BanyanDB {@code Measure} / {@code IndexRule} / {@code IndexRuleBinding} on
 * shape mismatch, and create / update index rules + bindings. Reshaping is treated as
 * intended because the caller came in through an on-demand operator request.
 *
 * <h3>{@link #createIfAbsent()} — {@link Mode#CREATE_IF_ABSENT} (predicate: {@link #isCreateIfAbsent()})</h3>
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
 * <h3>{@link #localCacheVerify()} — {@link Mode#LOCAL_CACHE_VERIFY} (predicate: {@link #isLocalCacheVerify()})</h3>
 * <p>Callers:
 * <ul>
 *   <li>Boot-time reconciler pass on a non-init OAP — the operator declared
 *       {@code init=false}, so this OAP must not perform DDL but must refuse to start if
 *       the backend isn't already in the shape the persisted runtime-rule catalog
 *       declares.</li>
 * </ul>
 * <p>Backend behaviour: read-only inspection. The installer issues the same metadata
 * read RPCs as {@link Mode#CREATE_IF_ABSENT} but never invokes create / update / drop. On
 * resource missing OR shape mismatch the installer throws — the exception propagates up
 * through the module bootstrap and causes the OAP process to exit, which under k8s
 * results in a pod backloop until either the init OAP has caught up or the operator has
 * fixed the rule files. This matches general OAP boot semantics for static models in
 * non-init mode: the OAP will not silently start with a backend that disagrees with
 * what's declared. Local {@code MetadataRegistry} is populated only when the live shape
 * matches the declared shape.
 *
 * <h3>{@link #localCacheOnly()} — {@link Mode#LOCAL_CACHE_ONLY} (predicate: {@link #isLocalCacheOnly()})</h3>
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
 * opt doesn't (and shouldn't) gate. Differs from {@link Mode#LOCAL_CACHE_VERIFY} in two
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
        FULL_INSTALL(Flags.builder()
            .inspectBackend(true)
            .createMissing(true)
            .updateOnMismatch(true)
            .dropOnRemoval(true)
            .escalateToCaller(true)
            .build()),
        /**
         * Static boot path on an init-mode OAP. Installer creates absent resources, but
         * if a resource already exists with a shape that diverges from the declared
         * model it records {@link Outcome#SKIPPED_SHAPE_MISMATCH} and does <strong>not</strong>
         * call update / reshape. Operator must reconcile via the runtime-rule REST
         * endpoint — boot is not allowed to silently mutate backend shape.
         */
        CREATE_IF_ABSENT(Flags.builder()
            .inspectBackend(true)
            .createMissing(true)
            .build()),
        /**
         * Boot path on a non-init OAP. Installer issues the same read-only inspection
         * RPCs as {@link #CREATE_IF_ABSENT} but never creates / updates / drops. On
         * resource missing or shape mismatch the installer <strong>throws</strong>; the
         * exception propagates up through module bootstrap and exits the process.
         * Under k8s this causes a pod backloop until the init OAP has caught up or the
         * operator has aligned rule files with the backend. Local {@code MetadataRegistry}
         * is populated only when the live shape matches the declared shape.
         */
        LOCAL_CACHE_VERIFY(Flags.builder()
            .inspectBackend(true)
            .failOnAbsence(true)
            .failOnShapeMismatch(true)
            .build()),
        /**
         * Peer-node reconciler tick path. Zero server RPCs — local caches populate from
         * the declared model and the main is trusted to own backend DDL. Missing or
         * mismatched resources are not an error: the next tick will retry, and the main
         * will eventually converge. Distinct from {@link #LOCAL_CACHE_VERIFY} in that
         * verification is skipped entirely, not run-and-fail.
         */
        LOCAL_CACHE_ONLY(Flags.builder().build());

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
         * {@link Mode#LOCAL_CACHE_ONLY} where the contract is "zero server RPCs". When
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
         * the declared shape. Only {@link Mode#FULL_INSTALL} (the operator-driven path)
         * permits this — boot must never silently reshape backend storage.
         *
         * <p>Note: BanyanDB's index-rule / index-rule-binding update path is gated by
         * {@link #failOnShapeMismatch} instead of this flag, preserving the long-standing
         * behaviour that init-mode OAPs reconcile index rules even under
         * {@link Mode#CREATE_IF_ABSENT}.</p>
         */
        private final boolean updateOnMismatch;
        /**
         * Call backend drop primitives ({@code client.dropMeasure} / {@code dropStream}
         * / etc.) from {@link ModelRegistry.CreatingListener#whenRemoving}. Only
         * {@link Mode#FULL_INSTALL} (operator-driven runtime-rule deletion) permits
         * this; peers under {@link Mode#LOCAL_CACHE_ONLY} short-circuit with
         * {@link Outcome#SKIPPED_NOT_ALLOWED}.
         */
        private final boolean dropOnRemoval;
        /**
         * Throw a {@link org.apache.skywalking.oap.server.core.storage.StorageException}
         * when a resource is absent on the backend after inspection. Used by
         * {@link Mode#LOCAL_CACHE_VERIFY} to fail boot rather than silently start
         * against an unprepared backend.
         */
        private final boolean failOnAbsence;
        /**
         * Throw a {@link org.apache.skywalking.oap.server.core.storage.StorageException}
         * when a present resource's live shape diverges from the declared shape. Used
         * by {@link Mode#LOCAL_CACHE_VERIFY} so boot does not silently start against a
         * backend whose schema disagrees with the rule file.
         */
        private final boolean failOnShapeMismatch;
        /**
         * Re-throw cascaded backend errors to the caller (REST handler, operator
         * tooling) instead of swallowing them. Set on {@link Mode#FULL_INSTALL}; other
         * modes log and continue so a peer-side bookkeeping glitch doesn't take down
         * the node.
         */
        private final boolean escalateToCaller;
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

    public static StorageManipulationOpt fullInstall() {
        return new StorageManipulationOpt(Mode.FULL_INSTALL);
    }

    public static StorageManipulationOpt createIfAbsent() {
        return new StorageManipulationOpt(Mode.CREATE_IF_ABSENT);
    }

    public static StorageManipulationOpt localCacheVerify() {
        return new StorageManipulationOpt(Mode.LOCAL_CACHE_VERIFY);
    }

    public static StorageManipulationOpt localCacheOnly() {
        return new StorageManipulationOpt(Mode.LOCAL_CACHE_ONLY);
    }

    /**
     * True for {@link Mode#FULL_INSTALL}. The on-demand operator workflow — drops,
     * updates, and reshapes are permitted because the caller explicitly asked for them.
     */
    public boolean isFullInstall() {
        return mode == Mode.FULL_INSTALL;
    }

    /**
     * True for {@link Mode#CREATE_IF_ABSENT}. The static boot workflow — create absent
     * resources, skip + record {@link Outcome#SKIPPED_SHAPE_MISMATCH} on a resource that
     * already exists with a different shape. Never update or drop.
     */
    public boolean isCreateIfAbsent() {
        return mode == Mode.CREATE_IF_ABSENT;
    }

    /**
     * True for {@link Mode#LOCAL_CACHE_VERIFY}. Boot-time strict verification on a
     * non-init OAP — installer issues read-only inspection RPCs and throws on missing or
     * shape-mismatched resources. No DDL.
     */
    public boolean isLocalCacheVerify() {
        return mode == Mode.LOCAL_CACHE_VERIFY;
    }

    /**
     * True for {@link Mode#LOCAL_CACHE_ONLY}. The {@code BanyanDBIndexInstaller.isExists}
     * short-circuit reads this to skip every server RPC and populate
     * {@code MetadataRegistry} only.
     */
    public boolean isLocalCacheOnly() {
        return mode == Mode.LOCAL_CACHE_ONLY;
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
         *  because the caller is in {@link Mode#LOCAL_CACHE_ONLY}. Caller may re-push with
         *  {@link #fullInstall()} to reconcile. {@link ResourceOutcome#getDiff()} carries
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