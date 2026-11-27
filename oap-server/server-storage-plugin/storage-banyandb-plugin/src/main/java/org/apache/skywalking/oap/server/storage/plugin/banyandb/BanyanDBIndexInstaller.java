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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.Status;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Group;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.IntervalRule;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Property;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Trace;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRuleBinding;
import org.apache.skywalking.banyandb.schema.v1.BanyandbSchema.SchemaKey;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TraceTagSpec;
import org.apache.skywalking.library.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.library.banyandb.v1.client.SchemaWatcher;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.ResourceExist;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.model.StorageManipulationOpt;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

/**
 * BanyanDB-side {@link ModelInstaller}. Owns the boot-time + runtime path that turns OAP's
 * declared {@link Model}s into BanyanDB groups, measures, streams, properties, traces, and
 * their index rules / bindings.
 *
 * <ul>
 *   <li><b>isExists</b> — compares the declared shape with what BanyanDB holds and, as the
 *       opt allows, brings a present measure / stream / trace / property in line: any
 *       difference on {@link StorageManipulationOpt.Mode#RUNTIME_APPLY}, an additive one on
 *       {@link StorageManipulationOpt.Mode#BOOT_INSTALL}. Records {@code MATCHED},
 *       {@code UPDATED}, or the outcome for a difference left unapplied, which keeps a
 *       metric on that resource off.</li>
 *   <li><b>createTable</b> — DDL path. Creates the resource if missing, updates index
 *       rules / bindings if drift is detected, and on time-series resources installs
 *       per-downsampling siblings.</li>
 *   <li><b>dropTable</b> — runtime-rule teardown. Deletes the measure / stream and its
 *       index rules. Resources backing in-progress writes are first paused on every node
 *       via the runtime-rule Suspend RPC; the dropTable call here is just the BanyanDB
 *       side of that cutover.</li>
 *   <li><b>Schema-cutover fence</b> — after every Create / Update / Delete that returned
 *       a non-zero etcd {@code mod_revision} OR that touched a known schema key, this
 *       installer waits (best-effort, bounded by a 2 s timeout) on
 *       {@link SchemaWatcher#awaitRevisionApplied} /
 *       {@link SchemaWatcher#awaitSchemaDeleted} for every BanyanDB data node to apply
 *       the change before returning to the caller. On laggard timeout it logs a warning
 *       naming the laggards and continues — see the runtime-rule architecture doc for
 *       why this is best-effort, not a hard guarantee.</li>
 *   <li><b>Local-only shortcut</b> — when {@code opt.flags.inspectBackend == false}
 *       ({@link StorageManipulationOpt.Mode#RUNTIME_LOCAL_ONLY}: a not-main OAP's tick, or
 *       the main OAP's {@code /inactivate}), {@code isExists} skips every server RPC and
 *       just populates the local {@link MetadataRegistry} so this OAP's DAOs can
 *       translate the model for sample read / write.</li>
 * </ul>
 */
@Slf4j
public class BanyanDBIndexInstaller extends ModelInstaller {
    // BanyanDB group setting aligned with the OAP settings
    private final Set<String/*group name*/> groupAligned = new HashSet<>();
    private final Map<String/*group name*/, Map<String/*rule name*/, IndexRule>> groupIndexRules = new HashMap<>();
    private final BanyanDBStorageConfig config;

    public BanyanDBIndexInstaller(Client client, ModuleManager moduleManager, BanyanDBStorageConfig config) {
        super(client, moduleManager);
        this.config = config;
        // Let read/persist paths self-heal a missing local schema entry (MetadataRegistry.repopulateLocally):
        // re-derive the model's Schema locally with zero server RPC via the same primitive the peer
        // boot path uses. This closes the "<metric> is not registered" flood that arises when a
        // runtimeLocalOnly peer apply or a runtime-rule bundled fall-over rebuilds the dispatch
        // worker but skips the populate. DownSamplingConfigService is resolved lazily per call — a
        // self-heal only fires post-boot, when CoreModule is long up.
        MetadataRegistry.INSTANCE.registerLocalSchemaPopulator(model -> {
            final DownSamplingConfigService downSamplingConfigService = moduleManager.find(CoreModule.NAME)
                                                                                     .provider()
                                                                                     .getService(DownSamplingConfigService.class);
            registerLocallyByKind(model, downSamplingConfigService);
        });
    }

    @Override
    protected boolean isRetryableNoInitProbeFailure(final StorageException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof BanyanDBException) {
                return isTransientBanyanDBProbeFailure((BanyanDBException) cause);
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean isTransientBanyanDBProbeFailure(final BanyanDBException e) {
        final Status.Code code = e.getStatus();
        if (Status.Code.UNAVAILABLE.equals(code)
            || Status.Code.DEADLINE_EXCEEDED.equals(code)
            || Status.Code.CANCELLED.equals(code)
            || Status.Code.RESOURCE_EXHAUSTED.equals(code)
            || Status.Code.ABORTED.equals(code)) {
            return true;
        }
        if (!Status.Code.UNKNOWN.equals(code)) {
            return false;
        }
        final String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("client connection is closing")
            || message.contains("connection is closing")
            || message.contains("transport is closing")
            || message.contains("connection refused")
            || message.contains("connection reset")
            || message.contains("broken pipe");
    }

    @Override
    public InstallInfo isExists(Model model, StorageManipulationOpt opt) throws StorageException {
        InstallInfoBanyanDB installInfo = new InstallInfoBanyanDB(model);
        installInfo.setDownSampling(model.getDownsampling());
        final DownSamplingConfigService downSamplingConfigService = moduleManager.find(CoreModule.NAME)
                                                         .provider()
                                                         .getService(DownSamplingConfigService.class);
        final MetadataRegistry.SchemaMetadata metadata = MetadataRegistry.INSTANCE.parseMetadata(
            model, config, downSamplingConfigService);
        installInfo.setTableName(metadata.name());
        installInfo.setKind(metadata.getKind());
        installInfo.setGroup(metadata.getGroup());
        installInfo.setResourceType(metadata.getKind().name().toLowerCase(Locale.ROOT));
        installInfo.setResourceName(metadata.getGroup() + "/" + metadata.name());

        // Peer-mode shortcut: when the caller has inspectBackend=false the contract is
        // "zero server RPCs". The cluster main has already installed the resource on
        // BanyanDB; we just populate the local MetadataRegistry so this peer's DAOs
        // can translate this Model for sample read/write. No checkMeasure auto-update,
        // no race with main's recent work.
        if (!opt.getFlags().isInspectBackend()) {
            registerLocallyByKind(model, downSamplingConfigService);
            installInfo.setGroupExist(true);
            installInfo.setTableExist(true);
            installInfo.setAllExist(true);
            opt.recordOutcome(metadata.getKind().name().toLowerCase(Locale.ROOT), metadata.getGroup() + "/" + metadata.name(),
                StorageManipulationOpt.Outcome.RUNTIME_BACKEND_UNTOUCHED, "local schema cache refreshed");
            return installInfo;
        }

        try {
            final BanyanDBClient c = ((BanyanDBStorageClient) this.client).client;
            // first check resource existence and create group if necessary
            final ResourceExist resourceExist = checkResourceExistence(metadata, c, opt);
            installInfo.setGroupExist(resourceExist.isHasGroup());
            installInfo.setTableExist(resourceExist.isHasResource());
            if (!resourceExist.isHasResource() && model.getBanyanDBModelExtension().getAttachmentTarget() == null) {
                installInfo.setAllExist(false);
                return installInfo;
            } else {
                // Run shape-compat checks — and the updates they drive for runtimeApply —
                // unless this is the static boot-time path deferring to the init OAP. The
                // runtime-rule DSL opts (runtimeApply / runtimePeerVerify) are never
                // deferred, so an operator-driven shape UPDATE reconciles on a no-init OAP
                // exactly as on a default / standalone one. (runtimePeerVerify still runs the
                // checks but records RUNTIME_PEER_SCHEMA_ABSENT / _DIFFERENT instead of writing.)
                final boolean runShapeChecks = !deferDDLToInitNode(opt);
                if (model.isTimeSeries()) {
                    // register models only locally(Schema cache) but not remotely
                    if (model.isRecord()) {
                        if (BanyanDB.TraceGroup.NONE != model.getBanyanDBModelExtension().getTraceGroup()) {
                            // trace
                            TraceModel traceModel = MetadataRegistry.INSTANCE.registerTraceModel(model, config);
                            if (model.getBanyanDBModelExtension().getAttachmentTarget() != null) {
                                installInfo.setAllExist(true);
                                return installInfo;
                            }
                            if (runShapeChecks) {
                                final int from = opt.getOutcomes().size();
                                if (checkTrace(traceModel.getTrace(), c, opt)) {
                                    checkIndexRules(model.getName(), traceModel.getIndexRules(), updatedSince(opt, from), c, opt);
                                    checkIndexRuleBinding(
                                        traceModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                        BanyandbCommon.Catalog.CATALOG_TRACE, c, opt
                                    );
                                }
                            }
                        } else {
                            // stream
                            StreamModel streamModel = MetadataRegistry.INSTANCE.registerStreamModel(
                                model, config);
                            if (runShapeChecks) {
                                final int from = opt.getOutcomes().size();
                                if (checkStream(streamModel.getStream(), c, opt)) {
                                    checkIndexRules(model.getName(), streamModel.getIndexRules(), updatedSince(opt, from), c, opt);
                                    checkIndexRuleBinding(
                                        streamModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                        BanyandbCommon.Catalog.CATALOG_STREAM, c, opt
                                    );
                                    // Stream not support server side TopN pre-aggregation
                                }
                            }
                        }
                    } else { // measure
                        MeasureModel measureModel = MetadataRegistry.INSTANCE.registerMeasureModel(model, config, downSamplingConfigService);
                        if (runShapeChecks) {
                            final int from = opt.getOutcomes().size();
                            if (checkMeasure(measureModel.getMeasure(), c, opt)) {
                                checkIndexRules(model.getName(), measureModel.getIndexRules(), updatedSince(opt, from), c, opt);
                                checkIndexRuleBinding(
                                    measureModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                    BanyandbCommon.Catalog.CATALOG_MEASURE, c, opt
                                );
                                checkTopNAggregation(model, c, opt);
                            }
                        }
                    }
                } else {
                    PropertyModel propertyModel = MetadataRegistry.INSTANCE.registerPropertyModel(model, config);
                    if (runShapeChecks) {
                        checkProperty(propertyModel.getProperty(), c, opt);
                    }
                }
                installInfo.setAllExist(true);
                fenceOnRevision(c, opt, "isExists:" + model.getName());
                return installInfo;
            }
        } catch (BanyanDBException ex) {
            throw new StorageException("fail to check existence", ex);
        }
    }

    /** Schema-watch budget per fence call. Standalone BanyanDB converges within
     *  microseconds; multi-node clusters within a few hundred ms. The
     *  runtime-rule REST handler's Armeria request timeout is 10 s and an
     *  apply may fire several fences (one per downsampling), so a per-fence
     *  budget of 2 s leaves comfortable headroom. A real stuck data node still
     *  surfaces — just as a bounded WARN per fence rather than an indefinite
     *  hang. */
    private static final Duration FENCE_TIMEOUT = Duration.ofSeconds(2);

    /**
     * If any registry write performed during the call recorded a non-zero
     * mod_revision, fence on it via {@code SchemaBarrierService.AwaitRevisionApplied}
     * so subsequent data writes / queries against the new shape are guaranteed to
     * land on a backend that has observed the schema. No-op when no revision was
     * recorded (peer-side ticks, or unchanged shape).
     *
     * <p>A non-applied result (one or more data nodes still lagging at the timeout)
     * is logged at WARN; the apply still completes. Operators tail the log to spot a
     * stuck node — they don't need a synchronous failure here because the next data
     * write that touches the lagging node would surface the issue.
     */
    private void fenceOnRevision(final BanyanDBClient client, final StorageManipulationOpt opt,
                                 final String context) throws BanyanDBException {
        if (opt.isDeferFence()) {
            // Batched apply: do NOT fence per resource. Register a single flush that the apply
            // orchestration runs once after all DDL is fired (StorageManipulationOpt#runDeferredFence),
            // so a multi-rule file waits on ONE barrier on the cumulative max revision instead of
            // one fence per metric/downsampling. The closure reads opt.getMaxModRevision() at flush
            // time, after every resource has recorded its revision.
            opt.setDeferredFence(() -> {
                try {
                    doDeferredFence(client, opt, "batched apply");
                } catch (final BanyanDBException e) {
                    throw new StorageException("batched schema fence failed", e);
                }
            });
            return;
        }
        doFenceOnRevision(client, opt, context);
    }

    private void doFenceOnRevision(final BanyanDBClient client, final StorageManipulationOpt opt,
                                   final String context) throws BanyanDBException {
        doFenceOnRevisionValue(client, opt.getMaxModRevision(), context);
    }

    /**
     * The deferred (batched) fence the runtime-rule apply runs once after all DDL. Unlike the inline
     * {@link #doFenceOnRevisionValue}, this (1) honors the opt's configured timeout
     * ({@link StorageManipulationOpt#getFenceTimeoutMs()}, the runtime-rule 3-min budget) instead of
     * the short inline {@link #FENCE_TIMEOUT}, and (2) records the outcome (applied + laggard node
     * ids) on the opt so the orchestrator can mark {@code APPLIED} vs {@code DEGRADED} and gate the
     * dispatch resume on this fence. A laggard timeout is still a non-fatal WARN.
     */
    private void doDeferredFence(final BanyanDBClient client, final StorageManipulationOpt opt,
                                 final String context) throws BanyanDBException {
        final long rev = opt.getMaxModRevision();
        if (rev <= 0L) {
            return;
        }
        final Duration timeout = opt.getFenceTimeoutMs() > 0L
            ? Duration.ofMillis(opt.getFenceTimeoutMs())
            : FENCE_TIMEOUT;
        final SchemaWatcher.Result result = client.getSchemaWatcher().awaitRevisionApplied(rev, timeout);
        if (!result.isApplied()) {
            log.warn("BanyanDB schema-watch fence did NOT confirm revision {} within {} ms for {}; "
                + "proceeding anyway. Laggards: {}", rev, timeout.toMillis(), context, result.getLaggards());
            final List<String> laggardIds = result.getLaggards().stream()
                .map(l -> l.getNode())
                .collect(Collectors.toList());
            opt.setFenceOutcome(new StorageManipulationOpt.FenceOutcome(false, laggardIds));
        } else {
            log.debug("BanyanDB schema-watch fence confirmed revision {} for {}", rev, context);
            opt.setFenceOutcome(new StorageManipulationOpt.FenceOutcome(true, List.of()));
        }
    }

    private void doFenceOnRevisionValue(final BanyanDBClient client, final long rev,
                                        final String context) throws BanyanDBException {
        if (rev <= 0L) {
            return;
        }
        final SchemaWatcher.Result result = client.getSchemaWatcher().awaitRevisionApplied(rev, FENCE_TIMEOUT);
        if (!result.isApplied()) {
            log.warn("BanyanDB schema-watch fence did NOT confirm revision {} within {} ms for {}; "
                + "proceeding anyway. Laggards: {}", rev, FENCE_TIMEOUT.toMillis(), context, result.getLaggards());
        } else {
            log.debug("BanyanDB schema-watch fence confirmed revision {} for {}", rev, context);
        }
    }

    @Override
    public void createTable(Model model) throws StorageException {
        // Legacy entry point preserved for binary compatibility; orchestrator calls
        // the opt-aware overload.
        createTable(model, StorageManipulationOpt.runtimeApply());
    }

    @Override
    public void createTable(Model model, StorageManipulationOpt opt) throws StorageException {
        try {
            final BanyanDBClient client = ((BanyanDBStorageClient) this.client).client;
            DownSamplingConfigService configService = moduleManager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(DownSamplingConfigService.class);
            if (model.isTimeSeries()) {
                if (model.isRecord()) {
                    if (BanyanDB.TraceGroup.NONE != model.getBanyanDBModelExtension().getTraceGroup()) {
                        TraceModel traceModel = MetadataRegistry.INSTANCE.registerTraceModel(model, config);
                        if (model.getBanyanDBModelExtension().getAttachmentTarget() != null) {
                            return;
                        }
                        // trace
                        Trace trace = traceModel.getTrace();
                        if (trace != null) {
                            log.debug("BanyanDB DDL: define trace {}", nameOf(trace.getMetadata()));
                            try {
                                opt.recordModRevision(client.define(trace));
                                opt.recordOutcome("trace", nameOf(trace.getMetadata()),
                                    StorageManipulationOpt.Outcome.CREATED, null);
                                if (CollectionUtils.isNotEmpty(traceModel.getIndexRules())) {
                                    for (IndexRule indexRule : traceModel.getIndexRules()) {
                                        opt.recordModRevision(defineIndexRule(model.getName(), indexRule, client, opt));
                                    }
                                    opt.recordModRevision(defineIndexRuleBinding(
                                        traceModel.getIndexRules(), trace.getMetadata().getGroup(), trace.getMetadata().getName(),
                                        BanyandbCommon.Catalog.CATALOG_TRACE, client, opt
                                    ));
                                }
                            } catch (BanyanDBException ex) {
                                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                                    log.debug("BanyanDB trace {} already created by another OAP node", model.getName());
                                } else {
                                    throw ex;
                                }
                            }
                        }
                    } else {
                        // stream
                        StreamModel streamModel = MetadataRegistry.INSTANCE.registerStreamModel(model, config);
                        Stream stream = streamModel.getStream();
                        if (stream != null) {
                            log.debug("BanyanDB DDL: define stream {}", nameOf(stream.getMetadata()));
                            try {
                                opt.recordModRevision(client.define(stream));
                                opt.recordOutcome("stream", nameOf(stream.getMetadata()),
                                    StorageManipulationOpt.Outcome.CREATED, null);
                                if (CollectionUtils.isNotEmpty(streamModel.getIndexRules())) {
                                    for (IndexRule indexRule : streamModel.getIndexRules()) {
                                        opt.recordModRevision(defineIndexRule(model.getName(), indexRule, client, opt));
                                    }
                                    opt.recordModRevision(defineIndexRuleBinding(
                                        streamModel.getIndexRules(), stream.getMetadata().getGroup(),
                                        stream.getMetadata().getName(),
                                        BanyandbCommon.Catalog.CATALOG_STREAM, client, opt
                                    ));
                                }
                            } catch (BanyanDBException ex) {
                                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                                    log.debug(
                                        "BanyanDB stream {}_{} already created by another OAP node",
                                        model.getName(),
                                        model.getDownsampling()
                                    );
                                } else {
                                    throw ex;
                                }
                            }
                        }
                    }
                } else { // measure
                    MeasureModel measureModel = MetadataRegistry.INSTANCE.registerMeasureModel(model, config, configService);
                    Measure measure = measureModel.getMeasure();
                    if (measure != null) {
                        log.debug("BanyanDB DDL: define measure {}", nameOf(measure.getMetadata()));
                        try {
                            opt.recordModRevision(client.define(measure));
                            opt.recordOutcome("measure", nameOf(measure.getMetadata()),
                                StorageManipulationOpt.Outcome.CREATED, null);
                            if (CollectionUtils.isNotEmpty(measureModel.getIndexRules())) {
                                for (IndexRule indexRule : measureModel.getIndexRules()) {
                                    opt.recordModRevision(defineIndexRule(model.getName(), indexRule, client, opt));
                                }
                                opt.recordModRevision(defineIndexRuleBinding(
                                    measureModel.getIndexRules(), measure.getMetadata().getGroup(), measure.getMetadata().getName(),
                                    BanyandbCommon.Catalog.CATALOG_MEASURE, client, opt
                                ));
                            }
                        } catch (BanyanDBException ex) {
                            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                                log.debug("BanyanDB measure {}_{} already created by another OAP node",
                                    model.getName(),
                                    model.getDownsampling());
                            } else {
                                throw ex;
                            }
                        }
                        final MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
                        defineTopNAggregation(schema, client, opt);
                    }
                }
            } else {
                PropertyModel propertyModel = MetadataRegistry.INSTANCE.registerPropertyModel(model, config);
                Property property = propertyModel.getProperty();
                log.debug("BanyanDB DDL: define property {}", nameOf(property.getMetadata()));
                try {
                    opt.recordModRevision(client.define(property));
                    opt.recordOutcome("property", nameOf(property.getMetadata()),
                        StorageManipulationOpt.Outcome.CREATED, null);
                } catch (BanyanDBException ex) {
                    if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                        log.debug("BanyanDB property {} already created by another OAP node", model.getName());
                    } else {
                        throw ex;
                    }
                }
            }
            // Fence on the highest mod_revision recorded during this createTable
            // pass before returning. Subsequent data writes / queries against the new
            // shape are guaranteed to land on a backend that has observed the schema.
            fenceOnRevision(client, opt, "createTable:" + model.getName());
        } catch (BanyanDBException ex) {
            throw new StorageException("fail to create schema " + model.getName(), ex);
        }
    }

    /**
     * Drop the physical schema backing a runtime-removed model. Invoked by {@link ModelInstaller#whenRemoving(Model, StorageManipulationOpt)}
     * during MAL/LAL hot-remove (never on the startup path). Because BanyanDB keeps one physical resource per
     * logical model (per Measure / Stream / Trace / Property), dropping here is both safe and necessary —
     * without it, a later re-create with a different shape would be silently rejected as ALREADY_EXISTS on the
     * server while the old shape lingers.
     *
     * <p>Errors are logged but not re-thrown for NOT_FOUND (target already gone — idempotent from the caller's
     * perspective). Any other {@link BanyanDBException} is wrapped as {@link StorageException} so the runtime-rule
     * workflow can abort and retry on the next reconciler tick.
     */
    @Override
    public void dropTable(Model model) throws StorageException {
        // Legacy entry point: delegate to opt-aware overload with a default opt so
        // existing callers don't need to construct one.
        dropTable(model, StorageManipulationOpt.runtimeApply());
    }

    @Override
    public void dropTable(Model model, StorageManipulationOpt opt) throws StorageException {
        try {
            final BanyanDBClient client = ((BanyanDBStorageClient) this.client).client;
            final DownSamplingConfigService configService = moduleManager.find(CoreModule.NAME)
                                                                         .provider()
                                                                         .getService(DownSamplingConfigService.class);
            final MetadataRegistry.SchemaMetadata metadata = MetadataRegistry.INSTANCE.parseMetadata(
                model, config, configService);
            final String group = metadata.getGroup();
            final String name = metadata.name();
            final String kind = metadata.getKind().name().toLowerCase(Locale.ROOT);
            // Tombstone revision of THIS drop's primary resource only — used to decide the
            // deletion fence. It must be the primary's own revision, NOT opt.getMaxModRevision():
            // a single opt is reused across many files in a tick, so the cumulative max can carry
            // an unrelated earlier create/binding revision and make a tombstone-less delete
            // (primary revision 0) skip the AwaitSchemaDeleted fallback. 0 for trace/property,
            // whose delete RPCs have no revision-returning variant — those always key-fence.
            long primaryDeleteRev = StorageManipulationOpt.DEFAULT_MOD_REVISION;
            switch (metadata.getKind()) {
                case MEASURE:
                    // Drop the TopN aggregations built on the measure first, then its index rule binding, then the
                    // measure. A TopN aggregation is named after its bydb-topn.yml rule, not after the measure, so
                    // they are found by their source. One that fails to drop stops the drop before the measure goes,
                    // which would leave it orphaned; the model stays registered and the drop is tried again. Index
                    // rules are shared by the group's measures and stay.
                    for (final TopNAggregation topN : client.findTopNAggregations(group)) {
                        if (!name.equals(topN.getSourceMeasure().getName())) {
                            continue;
                        }
                        final String topNName = topN.getMetadata().getName();
                        try {
                            log.debug("BanyanDB DDL: drop TopN aggregation {}/{}", group, topNName);
                            opt.recordModRevision(client.deleteTopNAggregationWithRevision(group, topNName));
                            opt.recordOutcome("topN", group + "/" + topNName, StorageManipulationOpt.Outcome.DROPPED, null);
                        } catch (BanyanDBException ex) {
                            if (!Status.Code.NOT_FOUND.equals(ex.getStatus())) {
                                throw ex;
                            }
                        }
                    }
                    dropIndexRuleBindingsBestEffort(client, group, name, opt);
                    log.debug("BanyanDB DDL: drop {} {}/{}", kind, group, name);
                    primaryDeleteRev = client.deleteMeasureWithRevision(group, name);
                    opt.recordModRevision(primaryDeleteRev);
                    break;
                case STREAM:
                    dropIndexRuleBindingsBestEffort(client, group, name, opt);
                    log.debug("BanyanDB DDL: drop {} {}/{}", kind, group, name);
                    primaryDeleteRev = client.deleteStreamWithRevision(group, name);
                    opt.recordModRevision(primaryDeleteRev);
                    break;
                case TRACE:
                    dropIndexRuleBindingsBestEffort(client, group, name, opt);
                    log.debug("BanyanDB DDL: drop {} {}/{}", kind, group, name);
                    client.deleteTrace(group, name);
                    break;
                case PROPERTY:
                    log.debug("BanyanDB DDL: drop {} {}/{}", kind, group, name);
                    client.deletePropertyDefinition(group, name);
                    break;
                default:
                    throw new StorageException(
                        "dropTable unsupported kind=" + metadata.getKind() + " for model " + model.getName());
            }
            opt.recordOutcome(kind, group + "/" + name, StorageManipulationOpt.Outcome.DROPPED, null);
            // Fence: prefer the revision-based wait when the server recorded a tombstone
            // mod_revision for THIS resource; otherwise fall back to AwaitSchemaDeleted keyed on
            // the primary resource so callers get a hard "removed everywhere" signal.
            fenceOnRevisionOrDeletion(client, metadata, primaryDeleteRev, "dropTable:" + model.getName());
        } catch (BanyanDBException ex) {
            if (Status.Code.NOT_FOUND.equals(ex.getStatus())) {
                opt.recordOutcome("model", model.getName(), StorageManipulationOpt.Outcome.DROPPED, "already absent");
                return;
            }
            throw new StorageException("fail to drop schema " + model.getName(), ex);
        }
    }

    /**
     * Prefer {@code AwaitRevisionApplied(primaryDeleteRev)} when the registry returned a
     * non-zero tombstone revision for the primary resource; otherwise fall back to
     * {@code AwaitSchemaDeleted(key)} keyed on the primary resource. The fallback
     * exists because {@code mod_revision == 0} on a delete response means the server
     * did not record a tombstone — the revision-based fence cannot observe a
     * deletion that didn't get one.
     *
     * <p>The decision keys on {@code primaryDeleteRev} — the primary resource's own delete
     * revision — NOT {@code opt.getMaxModRevision()}. A single opt is shared across every file
     * in a reconciler tick, so its cumulative max can hold an unrelated earlier create/binding
     * revision; using it here would make a tombstone-less primary delete take the revision
     * branch and silently skip {@code AwaitSchemaDeleted}. Because the primary delete is issued
     * last (after TopN + bindings), its revision is the highest of this drop and fencing on it
     * also covers the earlier lower-revision deletes of the same drop.
     */
    private void fenceOnRevisionOrDeletion(final BanyanDBClient client,
                                           final MetadataRegistry.SchemaMetadata metadata,
                                           final long primaryDeleteRev,
                                           final String context) throws BanyanDBException {
        if (primaryDeleteRev > 0L) {
            // Drops fence inline (never deferred): a deletion's visibility is per-key and must
            // not ride a batched revision flush — drops stay correct even under a deferFence opt.
            doFenceOnRevisionValue(client, primaryDeleteRev, context);
            return;
        }
        // mod_revision was 0 on the primary delete — fall back to key-based deletion fence.
        final String kind;
        switch (metadata.getKind()) {
            case MEASURE:
                kind = "measure";
                break;
            case STREAM:
                kind = "stream";
                break;
            case TRACE:
                kind = "trace";
                break;
            case PROPERTY:
                kind = "property";
                break;
            default:
                return;
        }
        final SchemaKey key = SchemaKey.newBuilder()
                .setKind(kind)
                .setGroup(metadata.getGroup())
                .setName(metadata.name())
                .build();
        final SchemaWatcher.Result result = client.getSchemaWatcher().awaitSchemaDeleted(key, FENCE_TIMEOUT);
        if (!result.isApplied()) {
            log.warn("BanyanDB schema-watch deletion fence did NOT confirm removal of {}:{} within {} ms ({}); "
                + "proceeding anyway. Laggards: {}", metadata.getGroup(), metadata.name(),
                FENCE_TIMEOUT.toMillis(), context, result.getLaggards());
        } else {
            log.debug("BanyanDB schema-watch confirmed removal of {}:{} ({})", metadata.getGroup(), metadata.name(), context);
        }
    }

    private void dropIndexRuleBindingsBestEffort(BanyanDBClient client, String group, String name,
                                                  StorageManipulationOpt opt) {
        // IndexRuleBindings are named after the resource; a best-effort delete covers both the common
        // binding-name pattern and leaves other objects untouched on NOT_FOUND.
        try {
            log.debug("BanyanDB DDL: drop index rule binding {}/{}", group, name);
            opt.recordModRevision(client.deleteIndexRuleBindingWithRevision(group, name));
            opt.recordOutcome("indexRuleBinding", group + "/" + name, StorageManipulationOpt.Outcome.DROPPED, null);
        } catch (BanyanDBException ex) {
            if (!Status.Code.NOT_FOUND.equals(ex.getStatus())) {
                log.warn("drop index rule binding {}:{} failed: {}", group, name, ex.getMessage());
            }
        }
    }

    /**
     * Check if the group settings need to be updated
     */
    private boolean checkGroup(MetadataRegistry.SchemaMetadata metadata, BanyanDBClient client) throws BanyanDBException {
        Group g = client.findGroup(metadata.getGroup());

        if (g.getResourceOpts().getShardNum() != metadata.getResource().getShardNum()
            || g.getResourceOpts().getSegmentInterval().getNum() != metadata.getResource().getSegmentInterval()
            || g.getResourceOpts().getTtl().getNum() != metadata.getResource().getTtl()) {
            return true;
        }

        if (g.getResourceOpts().getStagesCount() != metadata.getResource().getAdditionalLifecycleStages().size()) {
            return true;
        }
        for (int i = 0; i < g.getResourceOpts().getStagesCount(); i++) {
            BanyandbCommon.LifecycleStage stage = g.getResourceOpts().getStages(i);
            BanyanDBStorageConfig.Stage stageConfig = metadata.getResource().getAdditionalLifecycleStages().get(i);
            if (!stage.getName().equals(stageConfig.getName().name())
                || stage.getShardNum() != stageConfig.getShardNum()
                || stage.getSegmentInterval().getNum() != stageConfig.getSegmentInterval()
                || stage.getTtl().getNum() != stageConfig.getTtl()
                || !stage.getNodeSelector().equals(stageConfig.getNodeSelector())
                || stage.getClose() != stageConfig.isClose()) {
                return true;
            }
        }
        // A pipeline change (or removal) must also trigger a group update. Because gBuilder always
        // re-sends the desired pipeline, this comparison is also what keeps a full-replace update
        // from silently clearing an operator-configured trace pipeline.
        BanyandbCommon.TracePipelineConfig desiredPipeline = buildTracePipeline(metadata.getResource().getTracePipeline());
        BanyandbCommon.TracePipelineConfig existingPipeline = g.hasPipeline() ? g.getPipeline() : null;
        if (desiredPipeline == null) {
            return existingPipeline != null;
        }
        return !desiredPipeline.equals(existingPipeline);
    }

    /**
     * Build the BanyanDB {@code TracePipelineConfig} from the group's configured trace pipeline, or
     * {@code null} when the pipeline is absent or disabled (which leaves the group without a sampler).
     */
    private BanyandbCommon.TracePipelineConfig buildTracePipeline(BanyanDBStorageConfig.TracePipeline pipelineConfig) {
        if (pipelineConfig == null || !pipelineConfig.isEnabled()) {
            return null;
        }
        BanyandbCommon.TracePipelineConfig.Builder builder =
            BanyandbCommon.TracePipelineConfig.newBuilder().setEnabled(true);
        for (String event : pipelineConfig.getEnabledEvents()) {
            builder.addEnabledEvents(BanyandbCommon.PipelineEvent.valueOf(event));
        }
        if (pipelineConfig.getMergeGraceSeconds() > 0) {
            // Fully qualified: java.time.Duration is imported above, so the simple name is taken.
            builder.setMergeGrace(com.google.protobuf.Duration.newBuilder()
                                                              .setSeconds(pipelineConfig.getMergeGraceSeconds()));
        }
        if (pipelineConfig.getFinalizeGraceSeconds() > 0) {
            builder.setFinalizeGrace(com.google.protobuf.Duration.newBuilder()
                                                                 .setSeconds(pipelineConfig.getFinalizeGraceSeconds()));
        }
        for (BanyanDBStorageConfig.SamplerPluginConfig plugin : pipelineConfig.getPlugins()) {
            BanyandbCommon.SamplerPlugin.Builder samplerBuilder =
                BanyandbCommon.SamplerPlugin.newBuilder()
                                            .setPath(plugin.getPath())
                                            .setAbiVersion(plugin.getAbiVersion());
            if (plugin.getSymbol() != null) {
                samplerBuilder.setSymbol(plugin.getSymbol());
            }
            if (plugin.getConfig() != null && !plugin.getConfig().isEmpty()) {
                samplerBuilder.setConfig(buildStruct(plugin.getConfig()));
            }
            builder.addPlugins(BanyandbCommon.Plugin.newBuilder()
                                                    .setName(plugin.getName())
                                                    .setSampler(samplerBuilder));
        }
        return builder.build();
    }

    private Struct buildStruct(Map<?, ?> map) {
        Struct.Builder structBuilder = Struct.newBuilder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            structBuilder.putFields(String.valueOf(entry.getKey()), toStructValue(entry.getValue()));
        }
        return structBuilder.build();
    }

    // toStructValue mirrors the protobuf Struct JSON mapping so a plugin config of any shape
    // survives the round-trip to the sampler constructor: nested Maps become sub-Structs and
    // Lists become ListValues (e.g. a keepTagRules list of objects), rather than being
    // flattened to a single toString() field.
    private Value toStructValue(Object value) {
        Value.Builder valueBuilder = Value.newBuilder();
        if (value == null) {
            valueBuilder.setNullValue(NullValue.NULL_VALUE);
        } else if (value instanceof Boolean) {
            valueBuilder.setBoolValue((Boolean) value);
        } else if (value instanceof Number) {
            valueBuilder.setNumberValue(((Number) value).doubleValue());
        } else if (value instanceof Map) {
            valueBuilder.setStructValue(buildStruct((Map<?, ?>) value));
        } else if (value instanceof List) {
            ListValue.Builder listBuilder = ListValue.newBuilder();
            for (Object element : (List<?>) value) {
                listBuilder.addValues(toStructValue(element));
            }
            valueBuilder.setListValue(listBuilder);
        } else {
            valueBuilder.setStringValue(value.toString());
        }
        return valueBuilder.build();
    }

    private ResourceExist checkResourceExistence(MetadataRegistry.SchemaMetadata metadata,
                                           BanyanDBClient client,
                                           StorageManipulationOpt opt) throws BanyanDBException {
        ResourceExist resourceExist;
        Group.Builder gBuilder
            = Group.newBuilder()
                   .setMetadata(BanyandbCommon.Metadata.newBuilder().setName(metadata.getGroup()));
        BanyandbCommon.ResourceOpts.Builder optsBuilder = BanyandbCommon.ResourceOpts.newBuilder()
                                                                                     .setShardNum(metadata.getResource()
                                                                                                          .getShardNum())
                                                                                     .setReplicas(metadata.getResource()
                                                                                                          .getReplicas());

        switch (metadata.getKind()) {
            case STREAM:
                optsBuilder.setSegmentInterval(
                IntervalRule.newBuilder()
                    .setUnit(
                        IntervalRule.Unit.UNIT_DAY)
                    .setNum(
                        metadata.getResource().getSegmentInterval()))
                .setTtl(
                    IntervalRule.newBuilder()
                        .setUnit(
                            IntervalRule.Unit.UNIT_DAY)
                        .setNum(
                            metadata.getResource().getTtl()));
                resourceExist = client.existStream(metadata.getGroup(), metadata.name());
                gBuilder.setCatalog(BanyandbCommon.Catalog.CATALOG_STREAM).build();
                break;
            case MEASURE:
                optsBuilder.setSegmentInterval(
                        IntervalRule.newBuilder()
                            .setUnit(
                                IntervalRule.Unit.UNIT_DAY)
                            .setNum(
                                metadata.getResource().getSegmentInterval()))
                    .setTtl(
                        IntervalRule.newBuilder()
                            .setUnit(
                                IntervalRule.Unit.UNIT_DAY)
                            .setNum(
                                metadata.getResource().getTtl()));
                resourceExist = client.existMeasure(metadata.getGroup(), metadata.name());
                gBuilder.setCatalog(BanyandbCommon.Catalog.CATALOG_MEASURE).build();
                break;
            case PROPERTY:
                resourceExist = client.existProperty(metadata.getGroup(), metadata.name());
                gBuilder.setCatalog(BanyandbCommon.Catalog.CATALOG_PROPERTY).build();
                break;
            case TRACE:
                optsBuilder.setSegmentInterval(
                               IntervalRule.newBuilder()
                                           .setUnit(
                                               IntervalRule.Unit.UNIT_DAY)
                                           .setNum(
                                               metadata.getResource().getSegmentInterval()))
                           .setTtl(
                               IntervalRule.newBuilder()
                                           .setUnit(
                                               IntervalRule.Unit.UNIT_DAY)
                                           .setNum(
                                               metadata.getResource().getTtl()));
                resourceExist = client.existTrace(metadata.getGroup(), metadata.name());
                gBuilder.setCatalog(BanyandbCommon.Catalog.CATALOG_TRACE).build();
                BanyandbCommon.TracePipelineConfig tracePipeline = buildTracePipeline(metadata.getResource().getTracePipeline());
                if (tracePipeline != null) {
                    gBuilder.setPipeline(tracePipeline);
                }
                break;
            default:
                throw new IllegalStateException("unknown metadata kind: " + metadata.getKind());
        }
        if (CollectionUtils.isNotEmpty(metadata.getResource().getAdditionalLifecycleStages())) {
            for (BanyanDBStorageConfig.Stage stage : metadata.getResource().getAdditionalLifecycleStages()) {
                optsBuilder.addStages(
                    BanyandbCommon.LifecycleStage.newBuilder()
                                                 .setName(stage.getName().name())
                                                 .setShardNum(stage.getShardNum())
                                                 .setSegmentInterval(
                                                     IntervalRule.newBuilder().setUnit(IntervalRule.Unit.UNIT_DAY)
                                                                 .setNum(stage.getSegmentInterval()))
                                                 .setTtl(
                                                     IntervalRule.newBuilder()
                                                                 .setUnit(
                                                                     IntervalRule.Unit.UNIT_DAY)
                                                                 .setNum(
                                                                     stage.getTtl()))
                                                 .setReplicas(stage.getReplicas())
                                                 .setNodeSelector(stage.getNodeSelector())
                                                 .setClose(stage.isClose())
                );
            }
        }
        if (CollectionUtils.isNotEmpty(metadata.getResource().getDefaultQueryStages())) {
            optsBuilder.addAllDefaultStages(metadata.getResource().getDefaultQueryStages());
        }
        gBuilder.setResourceOpts(optsBuilder.build());
        // Group DDL follows the opt, not RunningMode: a runtime-rule runtimeApply
        // creates / updates the group on whatever node reaches here (peers short-circuit
        // earlier via inspectBackend=false), while the static boot path defers to the init
        // OAP on no-init. Create is gated on createMissing and update on !failOnShapeMismatch
        // so runtimePeerVerify stays read-only even though it is not deferred.
        if (!deferDDLToInitNode(opt) && !groupAligned.contains(metadata.getGroup())) {
            if (!resourceExist.isHasGroup()) {
                // create the group if not exist
                if (opt.getFlags().isCreateMissing()) {
                    try {
                        Group g = client.define(gBuilder.build());
                        if (g != null) {
                            log.debug("BanyanDB DDL: define group {}", g);
                            opt.recordOutcome("group", g.getMetadata().getName(), StorageManipulationOpt.Outcome.CREATED, null);
                        }
                    } catch (BanyanDBException ex) {
                        if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                            log.debug("BanyanDB group {} already created by another OAP node", metadata.getGroup());
                        } else {
                            throw ex;
                        }
                    }
                }
            } else {
                // update the group if necessary
                if (!opt.getFlags().isFailOnShapeMismatch()) {
                    if (this.checkGroup(metadata, client)) {
                        log.debug("BanyanDB DDL: update group {}", gBuilder.build());
                        opt.recordModRevision(client.update(gBuilder.build()));
                        opt.recordOutcome("group", metadata.getGroup(), StorageManipulationOpt.Outcome.UPDATED,
                            "shards, segment interval, TTL, lifecycle stages or trace pipeline changed");
                    } else {
                        opt.recordOutcome("group", metadata.getGroup(), StorageManipulationOpt.Outcome.MATCHED,
                            "shards, segment interval, TTL, lifecycle stages and trace pipeline match");
                    }
                }
            }
            // mark the group as aligned
            groupAligned.add(metadata.getGroup());
        }
        return resourceExist;
    }

    private void defineTopNAggregation(MetadataRegistry.Schema schema, BanyanDBClient client,
                                       StorageManipulationOpt opt) throws BanyanDBException {
        if (CollectionUtils.isEmpty(schema.getTopNSpecs())) {
            if (schema.getMetadata().getKind() == MetadataRegistry.Kind.MEASURE) {
                log.debug("skip null TopN Schema for [{}]", schema.getMetadata().name());
            }
            return;
        }
        for (TopNAggregation topNSpec : schema.getTopNSpecs().values()) {
            try {
                opt.recordModRevision(client.define(topNSpec));
                log.debug("BanyanDB DDL: define TopN aggregation {}", topNSpec);
                opt.recordOutcome("topN", nameOf(topNSpec.getMetadata()), StorageManipulationOpt.Outcome.CREATED, null);
            } catch (BanyanDBException ex) {
                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                    log.debug("BanyanDB TopN aggregation {} already created by another OAP node", nameOf(topNSpec.getMetadata()));
                } else {
                    throw ex;
                }
            }
        }
    }

    /**
     * Check if the index rule conflicts with the exist one.
     */
    private void checkIndexRuleConflicts(String modelName, IndexRule indexRule, IndexRule existRule) {
        if (!existRule.equals(indexRule)) {
            throw new IllegalStateException(
                "conflict index rule in model: " + modelName + ": " + indexRule + " vs exist rule: " + existRule);
        }
    }

    /**
     * Whether another model of the group already got this index rule onto the backend. A declaration that conflicts
     * with that model's throws.
     */
    private boolean isIndexRuleProcessed(String modelName, IndexRule indexRule) {
        IndexRule existRule = groupIndexRules.getOrDefault(indexRule.getMetadata().getGroup(), Collections.emptyMap())
                                             .get(indexRule.getMetadata().getName());
        if (existRule != null) {
            checkIndexRuleConflicts(modelName, indexRule, existRule);
            return true;
        }
        return false;
    }

    /**
     * Marks the index rule processed, once the backend holds it: a rule whose check or definition failed is checked
     * again for the next model that declares it.
     */
    private void markIndexRuleProcessed(IndexRule indexRule) {
        groupIndexRules.computeIfAbsent(indexRule.getMetadata().getGroup(), k -> new HashMap<>())
                       .put(indexRule.getMetadata().getName(), indexRule);
    }

    /**
     * Whether the primary resource check that started recording at outcome index {@code from} updated the resource.
     */
    private static boolean updatedSince(StorageManipulationOpt opt, int from) {
        final List<StorageManipulationOpt.ResourceOutcome> outcomes = opt.getOutcomes();
        for (int i = from; i < outcomes.size(); i++) {
            if (outcomes.get(i).getStatus() == StorageManipulationOpt.Outcome.UPDATED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Define the index rule if not exist and no conflict. Returns the etcd
     * mod_revision of the write, or 0 when the rule is already processed locally
     * or already exists on the server.
     */
    private long defineIndexRule(String modelName,
                                 IndexRule indexRule,
                                 BanyanDBClient client,
                                 StorageManipulationOpt opt) throws BanyanDBException {
        if (isIndexRuleProcessed(modelName, indexRule)) {
            return 0L;
        }
        try {
            log.debug("BanyanDB DDL: define index rule {}", indexRule);
            long rev = client.define(indexRule);
            markIndexRuleProcessed(indexRule);
            opt.recordOutcome("indexRule", nameOf(indexRule.getMetadata()), StorageManipulationOpt.Outcome.CREATED, null);
            return rev;
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                log.debug("BanyanDB index rule {} already created by another OAP node", nameOf(indexRule.getMetadata()));
                markIndexRuleProcessed(indexRule);
                return 0L;
            } else {
                throw ex;
            }
        }
    }

    private long defineIndexRuleBinding(List<IndexRule> indexRules,
                                        String group,
                                        String name,
                                        BanyandbCommon.Catalog catalog,
                                        BanyanDBClient client,
                                        StorageManipulationOpt opt) throws BanyanDBException {
        List<String> indexRuleNames = indexRules.stream().map(indexRule -> indexRule.getMetadata().getName()).collect(
            Collectors.toList());
        try {
            long rev = client.define(IndexRuleBinding.newBuilder()
                                          .setMetadata(BanyandbCommon.Metadata.newBuilder()
                                                                              .setGroup(group)
                                                                              .setName(name))
                                          .setSubject(BanyandbDatabase.Subject.newBuilder()
                                                                              .setName(name)
                                                                              .setCatalog(catalog))
                                          .addAllRules(indexRuleNames)
                                          .build());
            log.debug("BanyanDB DDL: define index rule binding {}/{} rules {}", group, name, indexRuleNames);
            opt.recordOutcome("indexRuleBinding", group + "/" + name, StorageManipulationOpt.Outcome.CREATED, null);
            return rev;
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                log.debug("BanyanDB index rule binding {}/{} already created by another OAP node", group, name);
                return 0L;
            } else {
                throw ex;
            }
        }
    }

    /**
     * Checks a present measure against its declared shape and brings it in line as far as {@code opt} allows
     * ({@link #reconcileShape}).
     *
     * @return {@code true} when the live measure now has the declared shape (it already had, or it was updated);
     *         {@code false} when a difference was left unapplied. Callers then skip the dependent index rules,
     *         binding and TopN, so a binding never points at tags the live measure doesn't have.
     */
    private boolean checkMeasure(Measure measure, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        Measure hisMeasure = client.findMeasure(measure.getMetadata().getGroup(), measure.getMetadata().getName());
        if (hisMeasure == null) {
            throw new IllegalStateException("Measure: " + measure.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        }
        final String name = nameOf(measure.getMetadata());
        final Measure live = hisMeasure.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build();
        if (live.equals(measure.toBuilder().clearMetadata().build())) {
            opt.recordOutcome("measure", name, StorageManipulationOpt.Outcome.MATCHED, null);
            return true;
        }
        final String diff = new ShapeDiff()
            .scalar("entity", hisMeasure.getEntity().getTagNamesList(), measure.getEntity().getTagNamesList())
            .scalar("interval", hisMeasure.getInterval(), measure.getInterval())
            .scalar("index mode", hisMeasure.getIndexMode(), measure.getIndexMode())
            .scalar("sharding key", hisMeasure.getShardingKey().getTagNamesList(), measure.getShardingKey().getTagNamesList())
            .named("tags", tagsOf(hisMeasure.getTagFamiliesList()), tagsOf(measure.getTagFamiliesList()))
            .named("fields", fieldsOf(hisMeasure.getFieldsList()), fieldsOf(measure.getFieldsList()))
            .toString();
        return reconcileShape(opt, "measure", name, isPurelyAdditiveMeasure(measure, hisMeasure), diff,
            () -> client.update(measure));
    }

    /**
     * Checks a present stream against its declared shape; see {@link #checkMeasure}.
     */
    private boolean checkStream(Stream stream, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        Stream hisStream = client.findStream(stream.getMetadata().getGroup(), stream.getMetadata().getName());
        if (hisStream == null) {
            throw new IllegalStateException("Stream: " + stream.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        }
        final String name = nameOf(stream.getMetadata());
        final Stream live = hisStream.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build();
        if (live.equals(stream.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build())) {
            opt.recordOutcome("stream", name, StorageManipulationOpt.Outcome.MATCHED, null);
            return true;
        }
        final String diff = new ShapeDiff()
            .scalar("entity", hisStream.getEntity().getTagNamesList(), stream.getEntity().getTagNamesList())
            .named("tags", tagsOf(hisStream.getTagFamiliesList()), tagsOf(stream.getTagFamiliesList()))
            .toString();
        return reconcileShape(opt, "stream", name, isPurelyAdditiveStream(stream, hisStream), diff,
            () -> client.update(stream));
    }

    /**
     * Checks a present trace against its declared shape; see {@link #checkMeasure}.
     */
    private boolean checkTrace(Trace trace, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        Trace hisTrace = client.findTrace(trace.getMetadata().getGroup(), trace.getMetadata().getName());
        if (hisTrace == null) {
            throw new IllegalStateException("Trace: " + trace.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        }
        final String name = nameOf(trace.getMetadata());
        final Trace live = hisTrace.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build();
        if (live.equals(trace.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build())) {
            opt.recordOutcome("trace", name, StorageManipulationOpt.Outcome.MATCHED, null);
            return true;
        }
        final String diff = new ShapeDiff()
            .scalar("trace id tag", hisTrace.getTraceIdTagName(), trace.getTraceIdTagName())
            .scalar("span id tag", hisTrace.getSpanIdTagName(), trace.getSpanIdTagName())
            .scalar("timestamp tag", hisTrace.getTimestampTagName(), trace.getTimestampTagName())
            .named("tags", traceTagsOf(hisTrace.getTagsList()), traceTagsOf(trace.getTagsList()))
            .toString();
        return reconcileShape(opt, "trace", name, isPurelyAdditiveTrace(trace, hisTrace), diff,
            () -> client.update(trace));
    }

    /**
     * Checks a present property definition against its declared shape. The property engine is schemaless, so
     * any difference counts as additive: boot updates it too.
     */
    private void checkProperty(Property property, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        Property hisProperty = client.findPropertyDefinition(property.getMetadata().getGroup(), property.getMetadata().getName());
        if (hisProperty == null) {
            throw new IllegalStateException("Property: " + property.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        }
        final String name = nameOf(property.getMetadata());
        final Property live = hisProperty.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build();
        if (live.equals(property.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build())) {
            opt.recordOutcome("property", name, StorageManipulationOpt.Outcome.MATCHED, null);
            return;
        }
        final String diff = new ShapeDiff()
            .named("tags", propertyTagsOf(hisProperty.getTagsList()), propertyTagsOf(property.getTagsList()))
            .toString();
        reconcileShape(opt, "property", name, true, diff, () -> client.update(property));
    }

    /**
     * A present resource differs from its declared shape. Applies the difference when {@code opt} allows it — any
     * difference on {@link StorageManipulationOpt.Mode#RUNTIME_APPLY}, an additive one on
     * {@link StorageManipulationOpt.Mode#BOOT_INSTALL} — and records {@code UPDATED}; otherwise records the outcome
     * for an unapplied difference ({@link StorageManipulationOpt#unappliedDifference()}).
     *
     * <p>When the difference is left unapplied, the caller leaves the resource's index rules and binding alone too:
     * reconciling them would point the binding at the declared tag list while the live resource still carries the
     * old one (a tag dropped from the model but kept on the backend loses its rule, and the orphan rule becomes
     * unqueryable). So either everything on the resource has the declared shape, or nothing on it is touched.
     *
     * @return {@code true} when the live resource now has the declared shape
     */
    private static boolean reconcileShape(final StorageManipulationOpt opt, final String kind, final String name,
                                          final boolean additive, final String diff,
                                          final SchemaUpdate update) throws BanyanDBException {
        final StorageManipulationOpt.Flags flags = opt.getFlags();
        if (flags.isUpdateOnMismatch() || additive && flags.isApplyAdditiveOnMismatch()) {
            log.debug("BanyanDB DDL: update {} {}: {}", kind, name, diff);
            opt.recordModRevision(update.apply());
            opt.recordOutcome(kind, name, StorageManipulationOpt.Outcome.UPDATED, diff);
            return true;
        }
        final StorageManipulationOpt.Outcome outcome = opt.unappliedDifference();
        String detail = "property".equals(kind) ? diff : diff + "; its index rules and binding are left as they are";
        if (outcome == StorageManipulationOpt.Outcome.BOOT_REFUSED_BREAKING_CHANGE) {
            detail += ". To store the declared shape, delete the " + kind + " with BanyanDB's own tools before the "
                + "next start (its data is lost)" + ("measure".equals(kind) ? ", or give the metric a new name" : "");
        }
        opt.recordOutcome(kind, name, outcome, detail);
        return false;
    }

    /** A BanyanDB schema update returning its {@code mod_revision}. */
    @FunctionalInterface
    private interface SchemaUpdate {
        long apply() throws BanyanDBException;
    }

    /**
     * Additive for a stream: declared may add tags or move a tag between families (a {@code storageOnly} toggle on
     * a {@code @Column} moves it between {@code storage-only} and {@code searchable}). The
     * {@link BanyandbDatabase.Entity entity} must match exactly, no tag may be dropped, and no tag type may change.
     */
    private static boolean isPurelyAdditiveStream(Stream declared, Stream live) {
        if (!declared.getEntity().equals(live.getEntity())) {
            return false;
        }
        return isPurelyAdditiveTagFamilies(
            declared.getTagFamiliesList(), live.getTagFamiliesList(), declared.getEntity().getTagNamesList());
    }

    /**
     * Additive for a measure: the tag rules of {@link #isPurelyAdditiveStream}, plus fields may be added but no
     * field's name, type, encoding or compression may change, and the entity, interval, index mode and sharding key
     * must match exactly.
     */
    private static boolean isPurelyAdditiveMeasure(Measure declared, Measure live) {
        if (!declared.getEntity().equals(live.getEntity())) {
            return false;
        }
        if (!declared.getInterval().equals(live.getInterval())) {
            return false;
        }
        if (declared.getIndexMode() != live.getIndexMode()) {
            return false;
        }
        if (!declared.getShardingKey().equals(live.getShardingKey())) {
            return false;
        }
        if (!isPurelyAdditiveTagFamilies(
            declared.getTagFamiliesList(), live.getTagFamiliesList(), declared.getEntity().getTagNamesList())) {
            return false;
        }
        final Map<String, BanyandbDatabase.FieldSpec> declaredFields = declared.getFieldsList().stream()
            .collect(Collectors.toMap(BanyandbDatabase.FieldSpec::getName, f -> f, (a, b) -> a));
        for (BanyandbDatabase.FieldSpec liveField : live.getFieldsList()) {
            if (!liveField.equals(declaredFields.get(liveField.getName()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Additive for a trace: the trace-id, span-id and timestamp tag names must match exactly; declared may add
     * tags, but no tag may be dropped and no tag type may change.
     */
    private static boolean isPurelyAdditiveTrace(Trace declared, Trace live) {
        if (!declared.getTraceIdTagName().equals(live.getTraceIdTagName())
            || !declared.getSpanIdTagName().equals(live.getSpanIdTagName())
            || !declared.getTimestampTagName().equals(live.getTimestampTagName())) {
            return false;
        }
        final Map<String, BanyandbDatabase.TagType> declaredTags = declared.getTagsList().stream()
            .collect(Collectors.toMap(TraceTagSpec::getName, TraceTagSpec::getType, (a, b) -> a));
        for (TraceTagSpec liveTag : live.getTagsList()) {
            if (declaredTags.get(liveTag.getName()) != liveTag.getType()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tag check shared by {@link #isPurelyAdditiveStream} and {@link #isPurelyAdditiveMeasure}, by name and type,
     * not by family: a tag may move between families, except an entity tag, which BanyanDB refuses to move (the
     * update would fail). A dropped tag or a changed type is not additive.
     *
     * <p><strong>Operator caveat:</strong> BanyanDB does NOT physically migrate existing
     * rows when a tag's family changes. Pre-existing data for that tag stays in the old
     * family's on-disk segment; new writes go to the declared family. Queries that route
     * through a new IndexRule on the relocated tag will only see post-reshape rows.
     * Operators should expect a backfill window after a storageOnly toggle.
     */
    private static boolean isPurelyAdditiveTagFamilies(List<BanyandbDatabase.TagFamilySpec> declared,
                                                       List<BanyandbDatabase.TagFamilySpec> live,
                                                       List<String> entityTags) {
        // Collapse declared tags across all families: (name -> TagSpec, and its family). A tag is
        // allowed to move between families, so a per-family lookup would falsely reject the move.
        final Map<String, BanyandbDatabase.TagSpec> declaredTagsByName = new HashMap<>();
        final Map<String, String> declaredFamilyByName = new HashMap<>();
        for (BanyandbDatabase.TagFamilySpec family : declared) {
            for (BanyandbDatabase.TagSpec tag : family.getTagsList()) {
                declaredTagsByName.putIfAbsent(tag.getName(), tag);
                declaredFamilyByName.putIfAbsent(tag.getName(), family.getName());
            }
        }
        for (BanyandbDatabase.TagFamilySpec liveFamily : live) {
            for (BanyandbDatabase.TagSpec liveTag : liveFamily.getTagsList()) {
                BanyandbDatabase.TagSpec declaredTag = declaredTagsByName.get(liveTag.getName());
                if (declaredTag == null || declaredTag.getType() != liveTag.getType()) {
                    return false;
                }
                if (entityTags.contains(liveTag.getName())
                    && !liveFamily.getName().equals(declaredFamilyByName.get(liveTag.getName()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String nameOf(final BanyandbCommon.Metadata metadata) {
        return metadata.getGroup() + "/" + metadata.getName();
    }

    /** Tag name → "family:type", so a family move shows as a change. */
    private static Map<String, String> tagsOf(final List<BanyandbDatabase.TagFamilySpec> families) {
        final Map<String, String> tags = new TreeMap<>();
        for (final BanyandbDatabase.TagFamilySpec family : families) {
            for (final BanyandbDatabase.TagSpec tag : family.getTagsList()) {
                tags.put(tag.getName(), family.getName() + ":" + tag.getType());
            }
        }
        return tags;
    }

    private static Map<String, String> traceTagsOf(final List<TraceTagSpec> traceTags) {
        final Map<String, String> tags = new TreeMap<>();
        for (final TraceTagSpec tag : traceTags) {
            tags.put(tag.getName(), String.valueOf(tag.getType()));
        }
        return tags;
    }

    private static Map<String, String> propertyTagsOf(final List<BanyandbDatabase.TagSpec> propertyTags) {
        final Map<String, String> tags = new TreeMap<>();
        for (final BanyandbDatabase.TagSpec tag : propertyTags) {
            tags.put(tag.getName(), String.valueOf(tag.getType()));
        }
        return tags;
    }

    private static Map<String, String> fieldsOf(final List<BanyandbDatabase.FieldSpec> fieldSpecs) {
        final Map<String, String> fields = new TreeMap<>();
        for (final BanyandbDatabase.FieldSpec field : fieldSpecs) {
            fields.put(field.getName(),
                field.getFieldType() + "/" + field.getEncodingMethod() + "/" + field.getCompressionMethod());
        }
        return fields;
    }

    /**
     * A one-line summary of how a declared BanyanDB schema differs from the live one, for the outcome log: what the
     * declared shape adds, removes or changes ("live → declared").
     */
    private static final class ShapeDiff {
        private final List<String> parts = new ArrayList<>();

        ShapeDiff scalar(final String what, final Object live, final Object declared) {
            if (!Objects.equals(live, declared)) {
                parts.add(what + " " + live + " → " + declared);
            }
            return this;
        }

        ShapeDiff named(final String what, final Map<String, String> live, final Map<String, String> declared) {
            final List<String> added = new ArrayList<>();
            final List<String> removed = new ArrayList<>();
            final List<String> changed = new ArrayList<>();
            declared.forEach((name, spec) -> {
                final String liveSpec = live.get(name);
                if (liveSpec == null) {
                    added.add(name + " " + spec);
                } else if (!liveSpec.equals(spec)) {
                    changed.add(name + " " + liveSpec + " → " + spec);
                }
            });
            live.keySet().stream().filter(name -> !declared.containsKey(name)).forEach(removed::add);
            if (!added.isEmpty()) {
                parts.add("adds " + what + " " + added);
            }
            if (!removed.isEmpty()) {
                parts.add("removes " + what + " " + removed);
            }
            if (!changed.isEmpty()) {
                parts.add("changes " + what + " " + changed);
            }
            return this;
        }

        @Override
        public String toString() {
            return parts.isEmpty() ? "differs in tag order or another attribute" : String.join("; ", parts);
        }
    }

    /**
     * Check if the index rules exist and update them if necessary. In
     * {@link StorageManipulationOpt#isRuntimePeerVerify() verify} mode the writes are
     * skipped and {@link StorageManipulationOpt.Outcome#RUNTIME_PEER_SCHEMA_ABSENT} /
     * {@link StorageManipulationOpt.Outcome#RUNTIME_PEER_SCHEMA_DIFFERENT} is recorded instead —
     * {@link ModelInstaller#whenCreating} turns that into a failed startup.
     */
    private void checkIndexRules(String modelName, List<IndexRule> indexRules, boolean primaryUpdated,
                                 BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        for (IndexRule indexRule : indexRules) {
            if (isIndexRuleProcessed(modelName, indexRule)) {
                // Another model of the group already checked this rule. An unchanged resource stops here, as it
                // always has; one this call just updated may declare new rules after the shared one.
                if (primaryUpdated) {
                    continue;
                }
                return;
            }
            IndexRule hisIndexRule = client.findIndexRule(
                indexRule.getMetadata().getGroup(), indexRule.getMetadata().getName());
            if (hisIndexRule == null) {
                if (!opt.getFlags().isCreateMissing()) {
                    opt.recordOutcome("indexRule", nameOf(indexRule.getMetadata()),
                        StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_ABSENT,
                        "IndexRule absent on backend; createMissing flag is off — refusing to define");
                    continue;
                }
                try {
                    log.debug("BanyanDB DDL: define index rule {}", indexRule);
                    opt.recordModRevision(client.define(indexRule));
                    opt.recordOutcome("indexRule", nameOf(indexRule.getMetadata()), StorageManipulationOpt.Outcome.CREATED, null);
                } catch (BanyanDBException ex) {
                    if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                        log.debug("BanyanDB index rule {} already created by another OAP node", nameOf(indexRule.getMetadata()));
                    } else {
                        throw ex;
                    }
                }
            } else {
                boolean equals = hisIndexRule.toBuilder()
                                             .clearUpdatedAt()
                                             .clearCreatedAt()
                                             .clearMetadata()
                                             .build()
                                             .equals(indexRule.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build());
                if (equals) {
                    opt.recordOutcome("indexRule", nameOf(indexRule.getMetadata()), StorageManipulationOpt.Outcome.MATCHED, null);
                } else {
                    if (opt.getFlags().isFailOnShapeMismatch()) {
                        opt.recordOutcome("indexRule", nameOf(indexRule.getMetadata()),
                            StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_DIFFERENT,
                            "IndexRule shape mismatch on backend; failOnShapeMismatch flag is on — refusing to update");
                        continue;
                    }
                    log.debug("BanyanDB DDL: update index rule {} from: {} to: {}", nameOf(indexRule.getMetadata()),
                        hisIndexRule, indexRule);
                    opt.recordModRevision(client.update(indexRule));
                    opt.recordOutcome("indexRule", nameOf(indexRule.getMetadata()), StorageManipulationOpt.Outcome.UPDATED,
                        "rule definition changed");
                }
            }
            markIndexRuleProcessed(indexRule);
        }
    }

    /**
     * Check if the index rule binding exists and update it if necessary. In
     * {@link StorageManipulationOpt#isRuntimePeerVerify() verify} mode skip the write and
     * record {@link StorageManipulationOpt.Outcome#RUNTIME_PEER_SCHEMA_ABSENT} /
     * {@link StorageManipulationOpt.Outcome#RUNTIME_PEER_SCHEMA_DIFFERENT}.
     */
    private void checkIndexRuleBinding(List<IndexRule> indexRules,
                                       String group,
                                       String name,
                                       BanyandbCommon.Catalog catalog,
                                       BanyanDBClient client,
                                       StorageManipulationOpt opt) throws BanyanDBException {
        if (indexRules.isEmpty()) {
            return;
        }
        List<String> indexRuleNames = indexRules.stream().map(indexRule -> indexRule.getMetadata().getName()).collect(
            Collectors.toList());

        IndexRuleBinding indexRuleBinding = IndexRuleBinding.newBuilder()
                                                            .setMetadata(BanyandbCommon.Metadata.newBuilder()
                                                                                                .setGroup(
                                                                                                    group)
                                                                                                .setName(name))
                                                            .setSubject(BanyandbDatabase.Subject.newBuilder()
                                                                                                .setName(name)
                                                                                                .setCatalog(
                                                                                                    catalog))
                                                            .addAllRules(indexRuleNames).build();
        IndexRuleBinding hisIndexRuleBinding = client.findIndexRuleBinding(group, name);
        if (hisIndexRuleBinding == null) {
            if (!opt.getFlags().isCreateMissing()) {
                opt.recordOutcome("indexRuleBinding", group + "/" + name,
                    StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_ABSENT,
                    "IndexRuleBinding absent on backend; createMissing flag is off — refusing to define");
                return;
            }
            try {
                opt.recordModRevision(client.define(indexRuleBinding));
                log.debug("BanyanDB DDL: define index rule binding {}", indexRuleBinding);
                opt.recordOutcome("indexRuleBinding", group + "/" + name, StorageManipulationOpt.Outcome.CREATED, null);
            } catch (BanyanDBException ex) {
                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                    log.debug("BanyanDB index rule binding {}/{} already created by another OAP node", group, name);
                } else {
                    throw ex;
                }
            }
        } else {
            boolean equals = hisIndexRuleBinding.toBuilder()
                                                .clearUpdatedAt()
                                                .clearCreatedAt()
                                                .clearMetadata()
                                                .clearBeginAt()
                                                .clearExpireAt()
                                                .build()
                                                .equals(indexRuleBinding.toBuilder().clearCreatedAt().clearMetadata().build());
            if (equals) {
                opt.recordOutcome("indexRuleBinding", group + "/" + name, StorageManipulationOpt.Outcome.MATCHED, null);
            } else {
                if (opt.getFlags().isFailOnShapeMismatch()) {
                    opt.recordOutcome("indexRuleBinding", group + "/" + name,
                        StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_DIFFERENT,
                        "IndexRuleBinding shape mismatch on backend; failOnShapeMismatch flag is on — refusing to update");
                    return;
                }
                // update binding and use the same begin expire time
                opt.recordModRevision(client.update(indexRuleBinding.toBuilder()
                                              .setBeginAt(hisIndexRuleBinding.getBeginAt())
                                              .setExpireAt(hisIndexRuleBinding.getExpireAt())
                                              .build()));
                log.debug("BanyanDB DDL: update index rule binding {}/{} from: {} to: {}", group, name,
                    hisIndexRuleBinding, indexRuleBinding);
                opt.recordOutcome("indexRuleBinding", group + "/" + name, StorageManipulationOpt.Outcome.UPDATED,
                    "rules " + hisIndexRuleBinding.getRulesList() + " → " + indexRuleNames);
            }
        }
    }

    /**
     * Check if the TopN aggregation exists and update it if necessary.
     * If the TopN rules are not used, will be checked and deleted after install, in the `BanyanDBStorageProvider.notifyAfterCompleted()`.
     * In {@link StorageManipulationOpt#isRuntimePeerVerify() verify} mode skip the write
     * and record {@link StorageManipulationOpt.Outcome#RUNTIME_PEER_SCHEMA_ABSENT} /
     * {@link StorageManipulationOpt.Outcome#RUNTIME_PEER_SCHEMA_DIFFERENT}.
     */
    private void checkTopNAggregation(Model model, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema.getTopNSpecs() == null) {
            return;
        }
        for (TopNAggregation topNAggregation : schema.getTopNSpecs().values()) {
            String topNName = topNAggregation.getMetadata().getName();
            TopNAggregation hisTopNAggregation = client.findTopNAggregation(schema.getMetadata().getGroup(), topNName);
            if (hisTopNAggregation == null) {
                if (!opt.getFlags().isCreateMissing()) {
                    opt.recordOutcome("topN", schema.getMetadata().getGroup() + "/" + topNName,
                        StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_ABSENT,
                        "TopNAggregation absent on backend; createMissing flag is off — refusing to define");
                    continue;
                }
                try {
                    opt.recordModRevision(client.define(topNAggregation));
                    log.debug("BanyanDB DDL: define TopN aggregation {}", topNAggregation);
                    opt.recordOutcome("topN", schema.getMetadata().getGroup() + "/" + topNName, StorageManipulationOpt.Outcome.CREATED, null);
                } catch (BanyanDBException ex) {
                    if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                        log.debug("BanyanDB TopN aggregation {} already created by another OAP node", topNName);
                    } else {
                        throw ex;
                    }
                }
            } else {
                boolean equals = hisTopNAggregation.toBuilder()
                                                   .clearUpdatedAt()
                                                   .clearCreatedAt()
                                                   .clearMetadata()
                                                   .build()
                                                   .equals(topNAggregation.toBuilder().clearCreatedAt().clearMetadata().build());
                if (equals) {
                    opt.recordOutcome("topN", schema.getMetadata().getGroup() + "/" + topNName, StorageManipulationOpt.Outcome.MATCHED, null);
                } else {
                    if (opt.getFlags().isFailOnShapeMismatch()) {
                        opt.recordOutcome("topN", schema.getMetadata().getGroup() + "/" + topNName,
                            StorageManipulationOpt.Outcome.RUNTIME_PEER_SCHEMA_DIFFERENT,
                            "TopNAggregation shape mismatch on backend; failOnShapeMismatch flag is on — refusing to update");
                        continue;
                    }
                    opt.recordModRevision(client.update(topNAggregation));
                    log.debug("BanyanDB DDL: update TopN aggregation {} from: {} to: {}", topNName,
                        hisTopNAggregation, topNAggregation);
                    opt.recordOutcome("topN", schema.getMetadata().getGroup() + "/" + topNName, StorageManipulationOpt.Outcome.UPDATED,
                        "aggregation changed");
                }
            }
        }
    }

    /**
     * Register the {@link Model} in {@link MetadataRegistry} by its kind, without touching
     * the BanyanDB server. Used on the peer-mode short-circuit above — populates the
     * schema cache the local DAOs read from so this node can translate Model ↔ BanyanDB
     * proto for sample ingest / queries.
     */
    @Override
    protected void populateLocalCacheOnly(final Model model, final StorageManipulationOpt opt) {
        // inspectBackend=false (peer / local-cache-only tick): the main owns the backend
        // resource; this node only (re)derives its local MetadataRegistry entry so its DAOs
        // can translate this model. RPC-free, and an overwrite via register*Model — keeps a
        // peer's cache in lockstep with a reshaped model that re-fires whenCreating, which the
        // read-side self-heal (fill-if-absent only) cannot do.
        final DownSamplingConfigService downSamplingConfigService = moduleManager.find(CoreModule.NAME)
                                                                                 .provider()
                                                                                 .getService(DownSamplingConfigService.class);
        registerLocallyByKind(model, downSamplingConfigService);
    }

    @Override
    protected void evictLocalCache(final Model model) {
        MetadataRegistry.INSTANCE.evict(model);
    }

    private void registerLocallyByKind(final Model model,
                                        final DownSamplingConfigService downSamplingConfigService) {
        if (model.isTimeSeries()) {
            if (model.isRecord()) {
                if (BanyanDB.TraceGroup.NONE != model.getBanyanDBModelExtension().getTraceGroup()) {
                    MetadataRegistry.INSTANCE.registerTraceModel(model, config);
                } else {
                    MetadataRegistry.INSTANCE.registerStreamModel(model, config);
                }
            } else {
                try {
                    MetadataRegistry.INSTANCE.registerMeasureModel(model, config, downSamplingConfigService);
                } catch (final StorageException ignored) {
                    // Peer-side registration is idempotent / best-effort; if the registry rejects
                    // the model (already registered, or config skew) the peer's local DAOs will
                    // use whatever's already cached. Main owns convergence.
                }
            }
        } else {
            MetadataRegistry.INSTANCE.registerPropertyModel(model, config);
        }
    }

    @Getter
    @Setter
    private static class InstallInfoBanyanDB extends InstallInfo {
        private DownSampling downSampling;
        private String tableName;
        private MetadataRegistry.Kind kind;
        private String group;
        private boolean groupExist;

        protected InstallInfoBanyanDB(Model model) {
            super(model);
        }

        @Override
        public String buildInstallInfoMsg() {
            return "InstallInfoBanyanDB{" +
                "modelName=" + getModelName() +
                ", modelType=" + getModelType() +
                ", timeSeries=" + isTimeSeries() +
                ", superDataset=" + isSuperDataset() +
                ", downSampling=" + downSampling.name() +
                ", tableName=" + tableName +
                ", kind=" + kind.name() +
                ", group=" + group +
                ", allResourcesExist=" + isAllExist() +
                " [groupExist=" + groupExist +
                ", tableExist=" + isTableExist() +
                "]}";
        }
    }
}
