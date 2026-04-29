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
        final StorageManipulationOpt.Flags flags = opt.getFlags();

        // Zero server RPCs — peer-side ticks. The earlier order called isExists first
        // (which on ES/JDBC fires a backend read) and only then checked the policy,
        // which made the contract a half-truth. Gate ahead of isExists so a peer apply
        // is genuinely zero-RPC.
        if (!flags.isInspectBackend()) {
            opt.recordOutcome("table", model.getName(),
                StorageManipulationOpt.Outcome.SKIPPED_NOT_ALLOWED,
                "local-cache-only mode; main-node is expected to have installed this resource");
            log.debug(
                "install: model [{}] not installed; local-cache-only mode — skipping (no isExists probe)",
                model.getName()
            );
            return;
        }

        // Strict verify path — run the read-only existence/shape inspection and surface
        // missing or mismatched resources as fatal so module bootstrap exits (k8s pod
        // backloop). Operator must align with the init OAP first. Distinct from the
        // legacy non-init poll loop further down: that loop waits forever; this path
        // fails fast.
        if (flags.isFailOnAbsence() || flags.isFailOnShapeMismatch()) {
            InstallInfo info = isExists(model, opt);
            if (flags.isFailOnShapeMismatch() && opt.hasShapeMismatch()) {
                final StorageManipulationOpt.ResourceOutcome o = opt.firstShapeMismatch();
                throw new StorageException(
                    "local-cache-verify boot: backend resource '" + (o == null ? model.getName() : o.getResourceName())
                        + "' shape diverges from declared model — refusing to start. "
                        + "Reconcile via the init OAP's /runtime/rule/addOrUpdate first. diff: "
                        + (o == null ? "n/a" : o.getDiff()));
            }
            if (flags.isFailOnAbsence() && !info.isAllExist()) {
                throw new StorageException(
                    "local-cache-verify boot: backend resources for model '" + model.getName()
                        + "' are not all present — refusing to start. Wait for the init OAP to "
                        + "create them or push the runtime rule. " + info.buildInstallInfoMsg());
            }
            return;
        }

        // Legacy poll loop for non-init OAPs that did not opt into the strict verify
        // mode. Static models (boot-time) still take this path; runtime-rule reconciler
        // explicitly chooses verify so this loop is bypassed.
        if (RunningMode.isNoInitMode()) {
            while (true) {
                InstallInfo info = isExists(model, opt);
                if (!info.isAllExist()) {
                    try {
                        log.info(
                            "install info: {}.table for model: [{}] not all required resources exist. OAP is running in 'no-init' mode, waiting create or update... retry 3s later.",
                            info.buildInstallInfoMsg(), model.getName()
                        );
                        Thread.sleep(3000L);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                } else {
                    break;
                }
            }
            return;
        }

        InstallInfo info = isExists(model, opt);
        if (info.isAllExist()) {
            return;
        }
        if (!flags.isCreateMissing()) {
            // Inspect-but-don't-create: caller wants existence reported as outcome but
            // explicitly forbids DDL. Today no canonical mode hits this branch, but the
            // flag combination is valid (e.g. dry-run reporting) and falling through to
            // createTable would silently violate the contract.
            opt.recordOutcome("table", model.getName(),
                StorageManipulationOpt.Outcome.MISSING,
                "missing on backend; createMissing flag is off — skipping DDL");
            return;
        }
        log.info(
            "install info: {}. table for model: [{}] not all required resources exist, creating or updating...",
            info.buildInstallInfoMsg(), model.getName()
        );
        createTable(model, opt);
        opt.recordOutcome("table", model.getName(),
            StorageManipulationOpt.Outcome.CREATED, null);
    }

    @Override
    public void whenRemoving(Model model, StorageManipulationOpt opt) throws StorageException {
        if (!opt.getFlags().isDropOnRemoval()) {
            opt.recordOutcome("table", model.getName(),
                StorageManipulationOpt.Outcome.SKIPPED_NOT_ALLOWED,
                "dropOnRemoval flag is off; server drop is main-node responsibility (or boot path that never drops)");
            return;
        }
        dropTable(model, opt);
        opt.recordOutcome("table", model.getName(),
            StorageManipulationOpt.Outcome.DROPPED, null);
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
     * {@link StorageManipulationOpt#isWithoutSchemaChange()} to suppress server writes when the
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
    }

    @Getter
    @Setter
    public abstract static class InstallInfo {
        private final String modelName;
        private final boolean timeSeries;
        private final boolean superDataset;
        private final String modelType;
        private boolean allExist;

        protected InstallInfo(Model model) {
            this.modelName = model.getName();
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
