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

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * The core module installation controller — subscribed to {@link ModelRegistry} events so
 * every registered {@link Model} triggers either an install (on {@code whenCreating}) or a
 * drop (on {@code whenRemoving}) on the active backend.
 *
 * <p>Exposed as a {@link Service} so cross-module callers (today: the runtime-rule reconciler)
 * can retrieve the active backend's installer via {@code StorageModule.provider().getService(
 * ModelInstaller.class)} and invoke {@link #isExists(Model, StorageManipulationOpt)} for
 * post-apply DDL verification. The storage providers register their concrete subclass as the
 * {@code ModelInstaller} service implementation; the abstract type is the SPI lookup key.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class ModelInstaller implements ModelRegistry.CreatingListener, Service {
    protected final Client client;
    protected final ModuleManager moduleManager;

    @Override
    public void whenCreating(Model model, StorageManipulationOpt opt) throws StorageException {
        final int from = opt.getOutcomes().size();
        try {
            install(model, opt);
        } finally {
            logOutcomes(model, opt, from);
        }
    }

    private void install(final Model model, final StorageManipulationOpt opt) throws StorageException {
        final StorageManipulationOpt.Flags flags = opt.getFlags();

        // Zero server RPCs — peer-side ticks. The earlier order called isExists first
        // (which on ES/JDBC fires a backend read) and only then checked the policy,
        // which made the contract a half-truth. Gate ahead of isExists so a peer apply
        // is genuinely zero-RPC.
        if (!flags.isInspectBackend()) {
            // Local-cache-only (peer reconciler) tick: zero server RPCs, but the local schema
            // cache MUST still be (re)derived from the declared model — the inspectBackend flag
            // contract requires exactly this. Without it, a peer holds a live dispatch worker
            // whose cache entry is either missing (first apply) or STALE (a reshape re-fires
            // whenCreating with a new shape after StorageModels.remove+add). The read-side
            // self-heal only fills a MISSING entry, never refreshes a stale one, so the peer
            // would keep translating writes with the old shape. RPC-free; no-op for backends
            // without a local schema cache (ES, JDBC).
            populateLocalCacheOnly(model, opt);
            opt.recordOutcome("model", model.getName(),
                StorageManipulationOpt.Outcome.RUNTIME_BACKEND_UNTOUCHED, "backend left untouched");
            return;
        }

        // OAP (not-main, starting up): read-only check that the main OAP has already applied the
        // persisted runtime rules' schema. An absent or different resource fails this rule's apply on
        // this OAP; a later reconcile tick loads it locally. Distinct from the no-init wait loop below,
        // which waits instead.
        if (flags.isFailOnAbsence() || flags.isFailOnShapeMismatch()) {
            final int from = opt.getOutcomes().size();
            final InstallInfo info = isExists(model, opt);
            if (flags.isFailOnAbsence() && !info.isAllExist()) {
                // The resource is there but lacks part of the declared shape (a column, a field, the template
                // order), or it is not there at all.
                opt.recordOutcome(info.getResourceType(), info.getResourceName(),
                    info.isTableExist()
                        ? StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_DIFFERENT
                        : StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_ABSENT,
                    info.buildInstallInfoMsg());
            }
            final StorageManipulationOpt.ResourceOutcome failed = opt.firstPeerVerifyFailureSince(from);
            if (failed != null) {
                throw new StorageException(
                    "not-main OAP startup check: the main OAP has not applied the runtime-rule schema of model ["
                        + model.getName() + "] yet, so this OAP does not apply it now. " + failed);
            }
            return;
        }

        // Poll loop for the STATIC boot-time path on a non-init OAP: the init OAP owns
        // schema creation, so this node waits until the resource appears rather than
        // creating it. Gated on deferDDLToInitNode (set only on BOOT_INSTALL),
        // NOT on RunningMode alone — a runtime-rule DSL apply (runtimeApply) is the
        // operator/main-driven authority and must fall through to createTable below
        // regardless of no-init, because no init OAP knows about a metric created at
        // runtime. Without this, a no-init OAP would block here forever waiting for a
        // resource that only this very apply would ever create.
        if (deferDDLToInitNode(opt)) {
            while (true) {
                boolean allExist;
                try {
                    InstallInfo info = isExists(model, opt);
                    allExist = info.isAllExist();
                    if (!allExist) {
                        log.info(
                            "install info: {}.table for model: [{}] not all required resources exist. OAP is running in 'no-init' mode, waiting create or update... retry 3s later.",
                            info.buildInstallInfoMsg(), model.getName()
                        );
                    }
                } catch (final StorageException e) {
                    if (!isRetryableNoInitProbeFailure(e)) {
                        throw e;
                    }
                    // A transient backend error during the probe (e.g. a BanyanDB cluster data node
                    // still Init-ing, "client connection is closing") is NOT a reason to abort boot:
                    // the init OAP will create the resource and the next probe succeeds. Treat it like
                    // "not present yet" and retry in-loop, rather than letting it escape and crash-loop
                    // the pod — which would only re-enter this same loop after a full restart.
                    allExist = false;
                    log.warn("install info: existence probe for model: [{}] threw a transient backend "
                        + "error. OAP is running in 'no-init' mode, retry 3s later.", model.getName(), e);
                }
                if (allExist) {
                    break;
                }
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StorageException(
                        "interrupted while waiting for no-init backend resources for model " + model.getName(), e);
                }
            }
            return;
        }

        InstallInfo info = isExists(model, opt);
        if (info.isAllExist()) {
            return;
        }
        if (!flags.isCreateMissing()) {
            throw new IllegalStateException(
                opt.getMode() + " inspects the backend but neither creates nor verifies: model [" + model.getName() + "]");
        }
        log.debug(
            "install info: {}. table for model: [{}] not all required resources exist, creating or updating...",
            info.buildInstallInfoMsg(), model.getName()
        );
        createTable(model, opt);
    }

    @Override
    public void whenRemoving(Model model, StorageManipulationOpt opt) throws StorageException {
        final int from = opt.getOutcomes().size();
        try {
            if (!opt.getFlags().isDropOnRemoval()) {
                // OAP (main, /inactivate or another local-only step) keeps storage; OAP (not-main) leaves
                // the drop to the main OAP; a boot never drops (it only rolls back a partial registration).
                // Either way this node evicts its own local schema-cache entry, so a removed model leaves
                // no stale translation in an otherwise insert-only cache. RPC-free; no-op for backends
                // without a local cache.
                evictLocalCache(model);
                if (opt.getMode().getStage() == StorageManipulationOpt.Stage.RUNTIME) {
                    opt.recordOutcome("model", model.getName(),
                        StorageManipulationOpt.Outcome.RUNTIME_BACKEND_UNTOUCHED, "backend kept as it is");
                }
                return;
            }
            dropTable(model, opt);
            // Evict only after a successful drop — a thrown dropTable leaves the model in the
            // registry for retry (see StorageModels.remove), so its cache entry must stay too.
            evictLocalCache(model);
        } finally {
            logOutcomes(model, opt, from);
        }
    }

    /**
     * Logs the outcomes recorded since index {@code from}, one line per resource: DEBUG when nothing changed, INFO
     * when the backend changed, ERROR when an incompatible difference was left unapplied. This is the one place that
     * reports what storage installation found and did, for every backend and mode; the storage plugins log the DDL
     * they execute at DEBUG.
     */
    private static void logOutcomes(final Model model, final StorageManipulationOpt opt, final int from) {
        final List<StorageManipulationOpt.ResourceOutcome> outcomes = opt.getOutcomes();
        for (int i = from; i < outcomes.size(); i++) {
            final StorageManipulationOpt.ResourceOutcome o = outcomes.get(i);
            switch (o.getStatus()) {
                case CREATED:
                case UPDATED:
                case DROPPED:
                    log.info("storage schema [{}] model [{}]: {}", opt.getMode(), model.getName(), o);
                    break;
                case BOOT_REFUSED_BREAKING_CHANGE:
                    // The installer's detail says how to reach the declared shape: the remedy depends on the resource.
                    log.error("storage schema [{}] model [{}]: {} — left as it is: boot applies additive changes only.",
                        opt.getMode(), model.getName(), o);
                    break;
                case RUNTIME_PEER_SCHEMA_ABSENT:
                case RUNTIME_PEER_SCHEMA_DIFFERENT:
                    log.error("storage schema [{}] model [{}]: {} — the main OAP has not applied it yet.",
                        opt.getMode(), model.getName(), o);
                    break;
                default:
                    log.debug("storage schema [{}] model [{}]: {}", opt.getMode(), model.getName(), o);
                    break;
            }
        }
    }

    /**
     * True when this manipulation must defer all backend DDL to the dedicated init OAP and
     * wait for it, rather than create / update / reshape the resource on this node. This is
     * the single source of truth for the "no-init OAP doesn't own schema" rule across the
     * base installer and every backend subclass — call it instead of re-checking
     * {@link RunningMode#isNoInitMode()} inline, so the rule stays one decision.
     *
     * <p>True only for the static boot-time {@link StorageManipulationOpt#bootInstall()}
     * opt on a {@code no-init} OAP. The runtime-rule (DSL) opts leave
     * {@link StorageManipulationOpt.Flags#isDeferDDLToInitNode() deferDDLToInitNode} unset, so
     * an operator-driven apply is governed by the opt's own create / update / drop flags and
     * by cluster main-ness — never by the init / no-init / default running mode.
     */
    protected static boolean deferDDLToInitNode(final StorageManipulationOpt opt) {
        return RunningMode.isNoInitMode() && opt.getFlags().isDeferDDLToInitNode();
    }

    /**
     * Whether a {@link StorageException} from the no-init defer-loop existence probe is
     * known to be transient and should be retried in-loop. The base implementation is
     * conservative so permanent model/config errors do not become an infinite boot wait;
     * storage backends opt in only for transport-level probe failures they can classify.
     */
    protected boolean isRetryableNoInitProbeFailure(final StorageException e) {
        return false;
    }

    public void start() {
    }

    /**
     * Installer implementation could use this API to request a column name replacement. This method delegates for
     * {@link ModelManipulator}.
     */
    protected final void overrideColumnName(String columnName, String newName) {
        ModelManipulator modelOverride = moduleManager.find(CoreModule.NAME)
                                                      .provider()
                                                      .getService(ModelManipulator.class);
        modelOverride.overrideColumnName(columnName, newName);
    }

    /**
     * Check whether the storage entity exists, reporting per-resource outcomes on
     * {@code opt}. Backends with in-isExists side effects (BanyanDB's auto-update of
     * {@code Measure}/{@code IndexRule}/{@code IndexRuleBinding}) honour
     * {@link StorageManipulationOpt#isRuntimeLocalOnly()} to suppress server writes when the
     * caller is a peer node.
     */
    public abstract InstallInfo isExists(Model model, StorageManipulationOpt opt) throws StorageException;

    /**
     * Create the storage entity. All creations should be after the
     * {@link #isExists(Model, StorageManipulationOpt)} check.
     *
     * <p>Default implementation delegates to {@link #createTable(Model)} for source
     * compatibility with backends that don't yet need the opt; subclasses that want
     * to capture per-call state (e.g. BanyanDB's etcd {@code mod_revision} via
     * {@link StorageManipulationOpt#recordModRevision(long)} for a post-install
     * fence) override this overload.
     */
    public void createTable(Model model, StorageManipulationOpt opt) throws StorageException {
        createTable(model);
    }

    /**
     * Legacy create — superseded by {@link #createTable(Model, StorageManipulationOpt)}.
     * Subclasses that don't need opt access keep overriding this method; the default
     * orchestrator path goes through the opt-aware overload.
     */
    public abstract void createTable(Model model) throws StorageException;

    /**
     * Drop the storage entity for a runtime-removed model. Default is a no-op — only backends whose physical
     * schema is per-logical-model (BanyanDB Measure/Stream) should override to perform the actual drop.
     * JDBC and Elasticsearch are append-only by design and keep the underlying tables/indices intact even when
     * a model is removed from the in-memory registry; their implementations should leave this as a no-op.
     *
     * <p>Invoked by {@link ModelRegistry.CreatingListener#whenRemoving(Model, StorageManipulationOpt)} which is fired from
     * {@link ModelRegistry#remove(Class, StorageManipulationOpt)} during runtime-rule hot-remove (MAL/LAL).
     * Not invoked on startup.
     */
    public void dropTable(Model model) throws StorageException {
    }

    /**
     * Opt-aware drop variant. Backends that need post-drop bookkeeping on the opt
     * (e.g. BanyanDB capturing the tombstone {@code mod_revision} for a
     * {@code SchemaBarrierService.AwaitSchemaDeleted} fence) override this overload;
     * default delegates to the no-arg {@link #dropTable(Model)}.
     */
    public void dropTable(Model model, StorageManipulationOpt opt) throws StorageException {
        dropTable(model);
        opt.recordOutcome("model", model.getName(), StorageManipulationOpt.Outcome.RUNTIME_BACKEND_UNTOUCHED,
            "kept: this storage never drops its tables or indices");
    }

    /**
     * Refresh THIS node's local schema cache for {@code model} from the declared model, with
     * no server RPC. Called on the local-cache-only path
     * ({@link StorageManipulationOpt.Flags#isInspectBackend() inspectBackend == false}, i.e.
     * {@link StorageManipulationOpt#runtimeLocalOnly()}), where the cluster main owns
     * backend DDL and this node only needs an up-to-date entry to translate its own
     * reads/writes. Backends with a local schema cache (BanyanDB) override to (re)derive and
     * <strong>overwrite</strong> the entry — overwrite, not fill-if-absent, so a reshape that
     * re-fires {@link #whenCreating} replaces a now-stale entry instead of leaving the old
     * shape in place. Default no-op: backends without a local cache (ES, JDBC) have nothing to
     * refresh.
     */
    protected void populateLocalCacheOnly(Model model, StorageManipulationOpt opt) throws StorageException {
    }

    /**
     * Drop THIS node's local schema-cache entry for a removed {@code model}, with no server
     * RPC. Called from {@link #whenRemoving} on every node so a removed model never leaves a
     * stale translation in an otherwise insert-only cache. Default no-op: backends without a
     * local schema cache (ES, JDBC) have nothing to evict; BanyanDB overrides.
     */
    protected void evictLocalCache(Model model) {
    }

    @Getter
    @Setter
    public abstract static class InstallInfo {
        private final String modelName;
        private final boolean timeSeries;
        private final boolean superDataset;
        private final String modelType;
        private boolean allExist;
        /**
         * The model's primary resource exists (a table, an index or template, a BanyanDB measure / stream / trace /
         * property), whether or not it has the whole declared shape.
         */
        private boolean tableExist;
        /**
         * The primary resource as outcomes name it: its kind and physical name. The model, until the backend sets
         * them.
         */
        private String resourceType;
        private String resourceName;

        protected InstallInfo(Model model) {
            this.modelName = model.getName();
            this.resourceType = "model";
            this.resourceName = model.getName();
            this.timeSeries = model.isTimeSeries();
            this.superDataset = model.isSuperDataset();
            if (model.isMetric()) {
                this.modelType = "metric";
            } else if (model.isRecord()) {
                this.modelType = "record";
            } else {
                this.modelType = "unknown";
            }
        }

        public abstract String buildInstallInfoMsg();
    }
}
