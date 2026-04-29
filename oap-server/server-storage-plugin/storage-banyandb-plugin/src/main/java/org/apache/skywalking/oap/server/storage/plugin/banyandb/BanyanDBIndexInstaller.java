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

import io.grpc.Status;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import java.time.Duration;
import org.apache.skywalking.library.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.library.banyandb.v1.client.SchemaWatcher;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.ResourceExist;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBTrace;
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
 *   <li><b>isExists</b> — read-only inspection. Compares the declared shape against what
 *       BanyanDB currently holds; records
 *       {@link StorageManipulationOpt.Outcome#SKIPPED_SHAPE_MISMATCH} on
 *       {@link StorageManipulationOpt} when the on-disk shape diverges, so the boot loop
 *       can skip the affected resource and log an ERROR diff instead of silently dropping
 *       samples.</li>
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
 *   <li><b>Peer-mode shortcut</b> — when {@code opt.flags.inspectBackend == false}
 *       (non-main OAP in cluster mode), {@code isExists} skips every server RPC and
 *       just populates the local {@link MetadataRegistry} so this peer's DAOs can
 *       translate the model for sample read / write. The cluster main is the only
 *       node that actually drives DDL.</li>
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
            opt.recordOutcome(metadata.getKind().name().toLowerCase(), metadata.name(),
                StorageManipulationOpt.Outcome.EXISTING_MATCHED,
                "peer-mode local cache refresh — no server RPC");
            return installInfo;
        }

        try {
            final BanyanDBClient c = ((BanyanDBStorageClient) this.client).client;
            // first check resource existence and create group if necessary
            final ResourceExist resourceExist = checkResourceExistence(metadata, c, opt);
            installInfo.setGroupExist(resourceExist.isHasGroup());
            installInfo.setTableExist(resourceExist.isHasResource());
            if (!resourceExist.isHasResource() && !BanyanDBTrace.MergeTable.class.isAssignableFrom(model.getStreamClass())) {
                installInfo.setAllExist(false);
                return installInfo;
            } else {
                // Run shape-compat checks unless we're in the legacy no-init poll loop
                // path. failOnAbsence implies the caller wants strict verification even
                // in non-init mode (LOCAL_CACHE_VERIFY), so honour that instead of just
                // gating on RunningMode.
                final boolean runShapeChecks = !RunningMode.isNoInitMode() || opt.getFlags().isFailOnAbsence();
                if (model.isTimeSeries()) {
                    // register models only locally(Schema cache) but not remotely
                    if (model.isRecord()) {
                        if (BanyanDB.TraceGroup.NONE != model.getBanyanDBModelExtension().getTraceGroup()) {
                            // trace
                            TraceModel traceModel = MetadataRegistry.INSTANCE.registerTraceModel(model, config);
                            if (BanyanDBTrace.MergeTable.class.isAssignableFrom(model.getStreamClass())) {
                                installInfo.setAllExist(true);
                                return installInfo;
                            }
                            if (runShapeChecks) {
                                checkTrace(traceModel.getTrace(), c, opt);
                                checkIndexRules(model.getName(), traceModel.getIndexRules(), c, opt);
                                checkIndexRuleBinding(
                                    traceModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                    BanyandbCommon.Catalog.CATALOG_TRACE, c, opt
                                );
                            }
                        } else {
                            // stream
                            StreamModel streamModel = MetadataRegistry.INSTANCE.registerStreamModel(
                                model, config);
                            if (runShapeChecks) {
                                checkStream(streamModel.getStream(), c, opt);
                                checkIndexRules(model.getName(), streamModel.getIndexRules(), c, opt);
                                checkIndexRuleBinding(
                                    streamModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                    BanyandbCommon.Catalog.CATALOG_STREAM, c, opt
                                );
                                // Stream not support server side TopN pre-aggregation
                            }
                        }
                    } else { // measure
                        MeasureModel measureModel = MetadataRegistry.INSTANCE.registerMeasureModel(model, config, downSamplingConfigService);
                        if (runShapeChecks) {
                            checkMeasure(measureModel.getMeasure(), c, opt);
                            checkIndexRules(model.getName(), measureModel.getIndexRules(), c, opt);
                            checkIndexRuleBinding(
                                measureModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                BanyandbCommon.Catalog.CATALOG_MEASURE, c, opt
                            );
                            checkTopNAggregation(model, c, opt);
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
        final long rev = opt.getMaxModRevision();
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
        createTable(model, StorageManipulationOpt.fullInstall());
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
                        if (BanyanDBTrace.MergeTable.class.isAssignableFrom(model.getStreamClass())) {
                            return;
                        }
                        // trace
                        Trace trace = traceModel.getTrace();
                        if (trace != null) {
                            log.info("install trace schema {}", model.getName());
                            try {
                                opt.recordModRevision(client.define(trace));
                                if (CollectionUtils.isNotEmpty(traceModel.getIndexRules())) {
                                    for (IndexRule indexRule : traceModel.getIndexRules()) {
                                        opt.recordModRevision(defineIndexRule(model.getName(), indexRule, client));
                                    }
                                    opt.recordModRevision(defineIndexRuleBinding(
                                        traceModel.getIndexRules(), trace.getMetadata().getGroup(), trace.getMetadata().getName(),
                                        BanyandbCommon.Catalog.CATALOG_TRACE, client
                                    ));
                                }
                            } catch (BanyanDBException ex) {
                                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                                    log.info("Trace schema {} already created by another OAP node", model.getName());
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
                            log.info("install stream schema {}", model.getName());
                            try {
                                opt.recordModRevision(client.define(stream));
                                if (CollectionUtils.isNotEmpty(streamModel.getIndexRules())) {
                                    for (IndexRule indexRule : streamModel.getIndexRules()) {
                                        opt.recordModRevision(defineIndexRule(model.getName(), indexRule, client));
                                    }
                                    opt.recordModRevision(defineIndexRuleBinding(
                                        streamModel.getIndexRules(), stream.getMetadata().getGroup(),
                                        stream.getMetadata().getName(),
                                        BanyandbCommon.Catalog.CATALOG_STREAM, client
                                    ));
                                }
                            } catch (BanyanDBException ex) {
                                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                                    log.info(
                                        "Stream schema {}_{} already created by another OAP node",
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
                        log.info("install measure schema {}", model.getName());
                        try {
                            opt.recordModRevision(client.define(measure));
                            if (CollectionUtils.isNotEmpty(measureModel.getIndexRules())) {
                                for (IndexRule indexRule : measureModel.getIndexRules()) {
                                    opt.recordModRevision(defineIndexRule(model.getName(), indexRule, client));
                                }
                                opt.recordModRevision(defineIndexRuleBinding(
                                    measureModel.getIndexRules(), measure.getMetadata().getGroup(), measure.getMetadata().getName(),
                                    BanyandbCommon.Catalog.CATALOG_MEASURE, client
                                ));
                            }
                        } catch (BanyanDBException ex) {
                            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                                log.info("Measure schema {}_{} already created by another OAP node",
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
                log.info("install property schema {}", model.getName());
                try {
                    opt.recordModRevision(client.define(property));
                } catch (BanyanDBException ex) {
                    if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                        log.info("Property schema {} already created by another OAP node", model.getName());
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
        dropTable(model, StorageManipulationOpt.fullInstall());
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
            log.info("drop BanyanDB schema kind={} {}:{}", metadata.getKind(), group, name);
            switch (metadata.getKind()) {
                case MEASURE:
                    // Drop the TopN aggregations first (if any), then index rule bindings, index rules, then the measure.
                    try {
                        opt.recordModRevision(client.deleteTopNAggregationWithRevision(group, name));
                    } catch (BanyanDBException ex) {
                        if (!Status.Code.NOT_FOUND.equals(ex.getStatus())) {
                            log.warn("drop TopN aggregation {}:{} failed: {}", group, name, ex.getMessage());
                        }
                    }
                    dropIndexRuleBindingsBestEffort(client, group, name, opt);
                    opt.recordModRevision(client.deleteMeasureWithRevision(group, name));
                    break;
                case STREAM:
                    dropIndexRuleBindingsBestEffort(client, group, name, opt);
                    opt.recordModRevision(client.deleteStreamWithRevision(group, name));
                    break;
                case TRACE:
                    dropIndexRuleBindingsBestEffort(client, group, name, opt);
                    client.deleteTrace(group, name);
                    break;
                case PROPERTY:
                    client.deletePropertyDefinition(group, name);
                    break;
                default:
                    throw new StorageException(
                        "dropTable unsupported kind=" + metadata.getKind() + " for model " + model.getName());
            }
            // Fence: prefer the revision-based wait when the server recorded a tombstone
            // mod_revision; otherwise fall back to AwaitSchemaDeleted keyed on the
            // primary resource so callers get a hard "removed everywhere" signal.
            fenceOnRevisionOrDeletion(client, opt, metadata, "dropTable:" + model.getName());
        } catch (BanyanDBException ex) {
            if (Status.Code.NOT_FOUND.equals(ex.getStatus())) {
                log.info("BanyanDB schema {} already absent on drop (idempotent)", model.getName());
                return;
            }
            throw new StorageException("fail to drop schema " + model.getName(), ex);
        }
    }

    /**
     * Prefer {@code AwaitRevisionApplied(maxRev)} when the registry returned a
     * non-zero tombstone revision; otherwise fall back to
     * {@code AwaitSchemaDeleted(key)} keyed on the primary resource. The fallback
     * exists because {@code mod_revision == 0} on a delete response means the server
     * did not record a tombstone — the revision-based fence cannot observe a
     * deletion that didn't get one.
     */
    private void fenceOnRevisionOrDeletion(final BanyanDBClient client, final StorageManipulationOpt opt,
                                           final MetadataRegistry.SchemaMetadata metadata,
                                           final String context) throws BanyanDBException {
        final long rev = opt.getMaxModRevision();
        if (rev > 0L) {
            fenceOnRevision(client, opt, context);
            return;
        }
        // mod_revision was 0 on every delete — fall back to key-based deletion fence.
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
            opt.recordModRevision(client.deleteIndexRuleBindingWithRevision(group, name));
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
        return false;
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
        if (!RunningMode.isNoInitMode()) {
            if (!groupAligned.contains(metadata.getGroup())) {
                // create the group if not exist
                if (!resourceExist.isHasGroup()) {
                    try {
                        Group g = client.define(gBuilder.build());
                        if (g != null) {
                            log.info("group {} created", g.getMetadata().getName());
                        }
                    } catch (BanyanDBException ex) {
                        if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                            log.info("group {} already created by another OAP node", metadata.getGroup());
                        } else {
                            throw ex;
                        }
                    }
                } else {
                    // update the group if necessary
                    if (this.checkGroup(metadata, client)) {
                        opt.recordModRevision(client.update(gBuilder.build()));
                        log.info("group {} updated", metadata.getGroup());
                    }
                }
                // mark the group as aligned
                groupAligned.add(metadata.getGroup());
            }
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
                log.info("installed TopN schema for measure {}", schema.getMetadata().name());
            } catch (BanyanDBException ex) {
                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                    log.info("TopNAggregation {} already created by another OAP node", topNSpec);
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
     * Check if the index rule has been processed.
     * If the index rule has been processed, return true.
     * Otherwise, return false and mark the index rule as processed.
     */
    private boolean checkIndexRuleProcessed(String modelName, IndexRule indexRule) {
        Map<String, IndexRule> rules = groupIndexRules.computeIfAbsent(
            indexRule.getMetadata().getGroup(), k -> new HashMap<>());
        IndexRule existRule = rules.get(indexRule.getMetadata().getName());
        if (existRule != null) {
            checkIndexRuleConflicts(modelName, indexRule, existRule);
            return true;
        } else {
            rules.put(indexRule.getMetadata().getName(), indexRule);
            return false;
        }
    }

    /**
     * Define the index rule if not exist and no conflict. Returns the etcd
     * mod_revision of the write, or 0 when the rule is already processed locally
     * or already exists on the server.
     */
    private long defineIndexRule(String modelName,
                                 IndexRule indexRule,
                                 BanyanDBClient client) throws BanyanDBException {
        if (checkIndexRuleProcessed(modelName, indexRule)) {
            return 0L;
        }
        try {
            long rev = client.define(indexRule);
            log.info("new IndexRule created: {}", indexRule.getMetadata().getName());
            return rev;
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                log.info("IndexRule {} already created by another OAP node", indexRule.getMetadata().getName());
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
                                        BanyanDBClient client) throws BanyanDBException {
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
            log.info("new IndexRuleBinding created: {}", name);
            return rev;
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                log.info("IndexRuleBinding {} already created by another OAP node", name);
                return 0L;
            } else {
                throw ex;
            }
        }
    }

    /**
     * Check if the measure exists and, when the live shape differs from the intended shape,
     * either update it (on-demand operator workflow — {@link StorageManipulationOpt#isFullInstall()})
     * or skip the update and record {@link StorageManipulationOpt.Outcome#SKIPPED_SHAPE_MISMATCH}
     * (static boot workflow — {@link StorageManipulationOpt#isCreateIfAbsent()}). Boot MUST
     * NOT reshape the backend — reshape is an explicit operator action only.
     */
    private void checkMeasure(Measure measure, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        Measure hisMeasure = client.findMeasure(measure.getMetadata().getGroup(), measure.getMetadata().getName());
        if (hisMeasure == null) {
            throw new IllegalStateException("Measure: " + measure.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        } else {
            boolean equals = hisMeasure.toBuilder()
                                       .clearUpdatedAt()
                                       .clearCreatedAt()
                                       .clearMetadata()
                                       .build()
                                       .equals(measure.toBuilder().clearMetadata().build());
            if (!equals) {
                if (!opt.getFlags().isUpdateOnMismatch()) {
                    log.error("BanyanDB measure {} shape mismatch at boot — backend holds a "
                        + "different shape than the declared rule. SKIPPING metric; operator "
                        + "must reshape via POST /runtime/rule/addOrUpdate or align the rule "
                        + "shape with the backend. backend={}, declared={}",
                        hisMeasure.getMetadata().getName(), hisMeasure, measure);
                    opt.recordOutcome("measure", hisMeasure.getMetadata().getName(),
                        StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                        "backend shape differs from declared shape; use /runtime/rule/addOrUpdate to reshape");
                    return;
                }
                // banyanDB server can not delete or update Tags.
                opt.recordModRevision(client.update(measure));
                log.info("update Measure: {} from: {} to: {}", hisMeasure.getMetadata().getName(), hisMeasure, measure);
            }
        }
    }

    /**
     * Check if the stream exists and update (or record shape mismatch) per mode.
     * See {@link #checkMeasure} for the create-if-absent vs full-install contract.
     */
    private void checkStream(Stream stream, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        Stream hisStream = client.findStream(stream.getMetadata().getGroup(), stream.getMetadata().getName());
        if (hisStream == null) {
            throw new IllegalStateException("Stream: " + stream.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        } else {
            boolean equals = hisStream.toBuilder()
                                      .clearUpdatedAt()
                                      .clearCreatedAt()
                                      .clearMetadata()
                                      .build()
                                      .equals(stream.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build());
            if (!equals) {
                if (!opt.getFlags().isUpdateOnMismatch()) {
                    log.error("BanyanDB stream {} shape mismatch at boot — backend holds a "
                        + "different shape than the declared rule. SKIPPING; operator must "
                        + "reshape via POST /runtime/rule/addOrUpdate. backend={}, declared={}",
                        hisStream.getMetadata().getName(), hisStream, stream);
                    opt.recordOutcome("stream", hisStream.getMetadata().getName(),
                        StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                        "backend shape differs from declared shape; use /runtime/rule/addOrUpdate to reshape");
                    return;
                }
                opt.recordModRevision(client.update(stream));
                log.info("update Stream: {} from: {} to: {}", hisStream.getMetadata().getName(), hisStream, stream);
            }
        }
    }

    private void checkTrace(Trace trace, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        Trace hisTrace = client.findTrace(trace.getMetadata().getGroup(), trace.getMetadata().getName());
        if (hisTrace == null) {
            throw new IllegalStateException("Trace: " + trace.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        } else {
            boolean equals = hisTrace.toBuilder()
                                      .clearUpdatedAt()
                                      .clearCreatedAt()
                                      .clearMetadata()
                                      .build()
                                      .equals(trace.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build());
            if (!equals) {
                if (!opt.getFlags().isUpdateOnMismatch()) {
                    log.error("BanyanDB trace {} shape mismatch at boot — backend holds a "
                        + "different shape than the declared rule. SKIPPING; operator must "
                        + "reshape via POST /runtime/rule/addOrUpdate. backend={}, declared={}",
                        hisTrace.getMetadata().getName(), hisTrace, trace);
                    opt.recordOutcome("trace", hisTrace.getMetadata().getName(),
                        StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                        "backend shape differs from declared shape; use /runtime/rule/addOrUpdate to reshape");
                    return;
                }
                opt.recordModRevision(client.update(trace));
                log.info("update Trace: {} from: {} to: {}", hisTrace.getMetadata().getName(), hisTrace, trace);
            }
        }
    }

    /**
     * Check if the property exists and update (or record shape mismatch) per mode.
     * See {@link #checkMeasure} for the create-if-absent vs full-install contract.
     */
    private void checkProperty(Property property, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        Property hisProperty = client.findPropertyDefinition(property.getMetadata().getGroup(), property.getMetadata().getName());
        if (hisProperty == null) {
            throw new IllegalStateException("Property: " + property.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        } else {
            boolean equals = hisProperty.toBuilder()
                                        .clearUpdatedAt()
                                        .clearCreatedAt()
                                        .clearMetadata()
                                        .build()
                                        .equals(property.toBuilder().clearUpdatedAt().clearCreatedAt().clearMetadata().build());
            if (!equals) {
                if (!opt.getFlags().isUpdateOnMismatch()) {
                    log.error("BanyanDB property {} shape mismatch at boot — backend holds a "
                        + "different shape than the declared rule. SKIPPING; operator must "
                        + "reshape via POST /runtime/rule/addOrUpdate. backend={}, declared={}",
                        hisProperty.getMetadata().getName(), hisProperty, property);
                    opt.recordOutcome("property", hisProperty.getMetadata().getName(),
                        StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                        "backend shape differs from declared shape; use /runtime/rule/addOrUpdate to reshape");
                    return;
                }
                opt.recordModRevision(client.update(property));
                log.info("update Property: {} from: {} to: {}", hisProperty.getMetadata().getName(), hisProperty, property);
            }
        }
    }

    /**
     * Check if the index rules exist and update them if necessary. In
     * {@link StorageManipulationOpt#isLocalCacheVerify() verify} mode the writes are
     * skipped and a {@link StorageManipulationOpt.Outcome#SKIPPED_SHAPE_MISMATCH} is
     * recorded instead — the orchestrator promotes that to a fatal boot error.
     */
    private void checkIndexRules(String modelName, List<IndexRule> indexRules, BanyanDBClient client, StorageManipulationOpt opt) throws BanyanDBException {
        for (IndexRule indexRule : indexRules) {
            if (checkIndexRuleProcessed(modelName, indexRule)) {
                return;
            }
            IndexRule hisIndexRule = client.findIndexRule(
                indexRule.getMetadata().getGroup(), indexRule.getMetadata().getName());
            if (hisIndexRule == null) {
                if (!opt.getFlags().isCreateMissing()) {
                    opt.recordOutcome("indexRule", indexRule.getMetadata().getName(),
                        StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                        "IndexRule absent on backend; createMissing flag is off — refusing to define");
                    continue;
                }
                try {
                    opt.recordModRevision(client.define(indexRule));
                    log.info("new IndexRule created: {}", indexRule);
                } catch (BanyanDBException ex) {
                    if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                        log.info("IndexRule {} already created by another OAP node", indexRule);
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
                if (!equals) {
                    if (opt.getFlags().isFailOnShapeMismatch()) {
                        opt.recordOutcome("indexRule", indexRule.getMetadata().getName(),
                            StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                            "IndexRule shape mismatch on backend; failOnShapeMismatch flag is on — refusing to update");
                        continue;
                    }
                    opt.recordModRevision(client.update(indexRule));
                    log.info(
                        "update IndexRule: {} from: {} to: {}", hisIndexRule.getMetadata().getName(), hisIndexRule,
                        indexRule
                    );
                }
            }
        }
    }

    /**
     * Check if the index rule binding exists and update it if necessary. In
     * {@link StorageManipulationOpt#isLocalCacheVerify() verify} mode skip the write and
     * record {@link StorageManipulationOpt.Outcome#SKIPPED_SHAPE_MISMATCH}.
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
                opt.recordOutcome("indexRuleBinding", name,
                    StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                    "IndexRuleBinding absent on backend; createMissing flag is off — refusing to define");
                return;
            }
            try {
                opt.recordModRevision(client.define(indexRuleBinding));
                log.info("new IndexRuleBinding created: {}", indexRuleBinding);
            } catch (BanyanDBException ex) {
                if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                    log.info("IndexRuleBinding {} already created by another OAP node", indexRuleBinding);
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
            if (!equals) {
                if (opt.getFlags().isFailOnShapeMismatch()) {
                    opt.recordOutcome("indexRuleBinding", name,
                        StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                        "IndexRuleBinding shape mismatch on backend; failOnShapeMismatch flag is on — refusing to update");
                    return;
                }
                // update binding and use the same begin expire time
                opt.recordModRevision(client.update(indexRuleBinding.toBuilder()
                                              .setBeginAt(hisIndexRuleBinding.getBeginAt())
                                              .setExpireAt(hisIndexRuleBinding.getExpireAt())
                                              .build()));
                log.info(
                    "update IndexRuleBinding: {} from: {} to: {}", hisIndexRuleBinding.getMetadata().getName(),
                    hisIndexRuleBinding, indexRuleBinding
                );
            }
        }
    }

    /**
     * Check if the TopN aggregation exists and update it if necessary.
     * If the TopN rules are not used, will be checked and deleted after install, in the `BanyanDBStorageProvider.notifyAfterCompleted()`.
     * In {@link StorageManipulationOpt#isLocalCacheVerify() verify} mode skip the write
     * and record {@link StorageManipulationOpt.Outcome#SKIPPED_SHAPE_MISMATCH}.
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
                    opt.recordOutcome("topN", topNName,
                        StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                        "TopNAggregation absent on backend; createMissing flag is off — refusing to define");
                    continue;
                }
                try {
                    opt.recordModRevision(client.define(topNAggregation));
                    log.info("new TopNAggregation created: {}", topNAggregation);
                } catch (BanyanDBException ex) {
                    if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                        log.info("TopNAggregation {} already created by another OAP node", topNAggregation);
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
                if (!equals) {
                    if (opt.getFlags().isFailOnShapeMismatch()) {
                        opt.recordOutcome("topN", topNName,
                            StorageManipulationOpt.Outcome.SKIPPED_SHAPE_MISMATCH,
                            "TopNAggregation shape mismatch on backend; failOnShapeMismatch flag is on — refusing to update");
                        continue;
                    }
                    opt.recordModRevision(client.update(topNAggregation));
                    log.info(
                        "update TopNAggregation: {} from: {} to: {}", hisTopNAggregation.getMetadata().getName(),
                        hisTopNAggregation, topNAggregation
                    );
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
        private boolean tableExist;
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
                ", tableExist=" + tableExist +
                "]}";
        }
    }
}
