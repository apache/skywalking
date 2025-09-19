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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.v1.client.metadata.MetadataCache;
import org.apache.skywalking.banyandb.v1.client.metadata.ResourceExist;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.storage.model.BanyanDBTrace;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

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
    public InstallInfo isExists(Model model) throws StorageException {
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
        try {
            final BanyanDBClient c = ((BanyanDBStorageClient) this.client).client;
            // first check resource existence and create group if necessary
            final ResourceExist resourceExist = checkResourceExistence(metadata, c);
            installInfo.setGroupExist(resourceExist.hasGroup());
            installInfo.setTableExist(resourceExist.hasResource());
            if (!resourceExist.hasResource() && !BanyanDBTrace.MergeTable.class.isAssignableFrom(model.getStreamClass())) {
                installInfo.setAllExist(false);
                return installInfo;
            } else {
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
                            if (!RunningMode.isNoInitMode()) {
                                checkTrace(traceModel.getTrace(), c);
                                checkIndexRules(model.getName(), traceModel.getIndexRules(), c);
                                checkIndexRuleBinding(
                                    traceModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                    BanyandbCommon.Catalog.CATALOG_TRACE, c
                                );
                            }
                        } else {
                            // stream
                            StreamModel streamModel = MetadataRegistry.INSTANCE.registerStreamModel(
                                model, config);
                            if (!RunningMode.isNoInitMode()) {
                                checkStream(streamModel.getStream(), c);
                                checkIndexRules(model.getName(), streamModel.getIndexRules(), c);
                                checkIndexRuleBinding(
                                    streamModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                    BanyandbCommon.Catalog.CATALOG_STREAM, c
                                );
                                // Stream not support server side TopN pre-aggregation
                            }
                        }
                    } else { // measure
                        MeasureModel measureModel = MetadataRegistry.INSTANCE.registerMeasureModel(model, config, downSamplingConfigService);
                        if (!RunningMode.isNoInitMode()) {
                            checkMeasure(measureModel.getMeasure(), c);
                            checkIndexRules(model.getName(), measureModel.getIndexRules(), c);
                            checkIndexRuleBinding(
                                measureModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                                BanyandbCommon.Catalog.CATALOG_MEASURE, c
                            );
                            checkTopNAggregation(model, c);
                        }
                    }
                    // pre-load remote schema for java client
                    MetadataCache.EntityMetadata remoteMeta = updateSchemaFromServer(metadata, c);
                    if (remoteMeta == null) {
                        throw new IllegalStateException("inconsistent state: metadata:" + metadata + ", remoteMeta: null");
                    }
                } else {
                    PropertyModel propertyModel = MetadataRegistry.INSTANCE.registerPropertyModel(model, config);
                    if (!RunningMode.isNoInitMode()) {
                        checkProperty(propertyModel.getProperty(), c);
                    }
                }
                installInfo.setAllExist(true);
                return installInfo;
            }
        } catch (BanyanDBException ex) {
            throw new StorageException("fail to check existence", ex);
        }
    }

    @Override
    public void createTable(Model model) throws StorageException {
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
                                client.define(trace);
                                if (CollectionUtils.isNotEmpty(traceModel.getIndexRules())) {
                                    for (IndexRule indexRule : traceModel.getIndexRules()) {
                                        defineIndexRule(model.getName(), indexRule, client);
                                    }
                                    defineIndexRuleBinding(
                                        traceModel.getIndexRules(), trace.getMetadata().getGroup(), trace.getMetadata().getName(),
                                        BanyandbCommon.Catalog.CATALOG_TRACE, client
                                    );
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
                                client.define(stream);
                                if (CollectionUtils.isNotEmpty(streamModel.getIndexRules())) {
                                    for (IndexRule indexRule : streamModel.getIndexRules()) {
                                        defineIndexRule(model.getName(), indexRule, client);
                                    }
                                    defineIndexRuleBinding(
                                        streamModel.getIndexRules(), stream.getMetadata().getGroup(),
                                        stream.getMetadata().getName(),
                                        BanyandbCommon.Catalog.CATALOG_STREAM, client
                                    );
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
                            client.define(measure);
                            if (CollectionUtils.isNotEmpty(measureModel.getIndexRules())) {
                                for (IndexRule indexRule : measureModel.getIndexRules()) {
                                    defineIndexRule(model.getName(), indexRule, client);
                                }
                                defineIndexRuleBinding(
                                    measureModel.getIndexRules(), measure.getMetadata().getGroup(), measure.getMetadata().getName(),
                                    BanyandbCommon.Catalog.CATALOG_MEASURE, client
                                );
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
                        defineTopNAggregation(schema, client);
                    }
                }
            } else {
                PropertyModel propertyModel = MetadataRegistry.INSTANCE.registerPropertyModel(model, config);
                Property property = propertyModel.getProperty();
                log.info("install property schema {}", model.getName());
                try {
                    client.define(property);
                } catch (BanyanDBException ex) {
                    if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                        log.info("Property schema {} already created by another OAP node", model.getName());
                    } else {
                        throw ex;
                    }
                }
            }
        } catch (BanyanDBException ex) {
            throw new StorageException("fail to create schema " + model.getName(), ex);
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
                                           BanyanDBClient client) throws BanyanDBException {
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
                if (!resourceExist.hasGroup()) {
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
                        client.update(gBuilder.build());
                        log.info("group {} updated", metadata.getGroup());
                    }
                }
                // mark the group as aligned
                groupAligned.add(metadata.getGroup());
            }
        }
        return resourceExist;
    }

    /**
     * Update the schema from the banyanDB server side for the java client cache
     */
    private MetadataCache.EntityMetadata updateSchemaFromServer(MetadataRegistry.SchemaMetadata metadata, BanyanDBClient client) throws BanyanDBException {
        switch (metadata.getKind()) {
            case STREAM:
                return client.updateStreamMetadataCacheFromSever(metadata.getGroup(), metadata.name());
            case MEASURE:
                return client.updateMeasureMetadataCacheFromSever(metadata.getGroup(), metadata.name());
            case TRACE:
                return client.updateTraceMetadataCacheFromServer(metadata.getGroup(), metadata.name());
            default:
                throw new IllegalStateException("unknown metadata kind: " + metadata.getKind());
        }
    }

    private void defineTopNAggregation(MetadataRegistry.Schema schema, BanyanDBClient client) throws BanyanDBException {
        if (CollectionUtils.isEmpty(schema.getTopNSpecs())) {
            if (schema.getMetadata().getKind() == MetadataRegistry.Kind.MEASURE) {
                log.debug("skip null TopN Schema for [{}]", schema.getMetadata().name());
            }
            return;
        }
        for (TopNAggregation topNSpec : schema.getTopNSpecs().values()) {
            try {
                client.define(topNSpec);
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
     * Define the index rule if not exist and no conflict.
     */
    private void defineIndexRule(String modelName,
                                 IndexRule indexRule,
                                 BanyanDBClient client) throws BanyanDBException {
        if (checkIndexRuleProcessed(modelName, indexRule)) {
            return;
        }
        try {
            client.define(indexRule);
            log.info("new IndexRule created: {}", indexRule.getMetadata().getName());
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                log.info("IndexRule {} already created by another OAP node", indexRule.getMetadata().getName());
            } else {
                throw ex;
            }
        }
    }

    private void defineIndexRuleBinding(List<IndexRule> indexRules,
                                        String group,
                                        String name,
                                        BanyandbCommon.Catalog catalog,
                                        BanyanDBClient client) throws BanyanDBException {
        List<String> indexRuleNames = indexRules.stream().map(indexRule -> indexRule.getMetadata().getName()).collect(
            Collectors.toList());
        try {
            client.define(IndexRuleBinding.newBuilder()
                                          .setMetadata(BanyandbCommon.Metadata.newBuilder()
                                                                              .setGroup(group)
                                                                              .setName(name))
                                          .setSubject(BanyandbDatabase.Subject.newBuilder()
                                                                              .setName(name)
                                                                              .setCatalog(catalog))
                                          .addAllRules(indexRuleNames)
                                          .build());
            log.info("new IndexRuleBinding created: {}", name);
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                log.info("IndexRuleBinding {} already created by another OAP node", name);
            } else {
                throw ex;
            }
        }
    }

    /**
     * Check if the measure exists and update it if necessary
     */
    private void checkMeasure(Measure measure, BanyanDBClient client) throws BanyanDBException {
        Measure hisMeasure = client.findMeasure(measure.getMetadata().getGroup(), measure.getMetadata().getName());
        if (hisMeasure == null) {
            throw new IllegalStateException("Measure: " + measure.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        } else {
            boolean equals = hisMeasure.toBuilder()
                                       .clearUpdatedAt()
                                       .clearMetadata()
                                       .build()
                                       .equals(measure.toBuilder().clearMetadata().build());
            if (!equals) {
                // banyanDB server can not delete or update Tags.
                client.update(measure);
                log.info("update Measure: {} from: {} to: {}", hisMeasure.getMetadata().getName(), hisMeasure, measure);
            }
        }
    }

    /**
     * Check if the stream exists and update it if necessary
     */
    private void checkStream(Stream stream, BanyanDBClient client) throws BanyanDBException {
        Stream hisStream = client.findStream(stream.getMetadata().getGroup(), stream.getMetadata().getName());
        if (hisStream == null) {
            throw new IllegalStateException("Stream: " + stream.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        } else {
            boolean equals = hisStream.toBuilder()
                                      .clearUpdatedAt()
                                      .clearMetadata()
                                      .build()
                                      .equals(stream.toBuilder().clearUpdatedAt().clearMetadata().build());
            if (!equals) {
                client.update(stream);
                log.info("update Stream: {} from: {} to: {}", hisStream.getMetadata().getName(), hisStream, stream);
            }
        }
    }

    private void checkTrace(Trace trace, BanyanDBClient client) throws BanyanDBException {
        Trace hisTrace = client.findTrace(trace.getMetadata().getGroup(), trace.getMetadata().getName());
        if (hisTrace == null) {
            throw new IllegalStateException("Trace: " + trace.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        } else {
            boolean equals = hisTrace.toBuilder()
                                      .clearUpdatedAt()
                                      .clearMetadata()
                                      .build()
                                      .equals(trace.toBuilder().clearUpdatedAt().clearMetadata().build());
            if (!equals) {
                client.update(trace);
                log.info("update Trace: {} from: {} to: {}", hisTrace.getMetadata().getName(), hisTrace, trace);
            }
        }
    }

    /**
     * Check if the property exists and update it if necessary
     */
    private void checkProperty(Property property, BanyanDBClient client) throws BanyanDBException {
        Property hisProperty = client.findPropertyDefinition(property.getMetadata().getGroup(), property.getMetadata().getName());
        if (hisProperty == null) {
            throw new IllegalStateException("Property: " + property.getMetadata().getName() + " exist but can't find it from BanyanDB server");
        } else {
            boolean equals = hisProperty.toBuilder()
                                        .clearUpdatedAt()
                                        .clearMetadata()
                                        .build()
                                        .equals(property.toBuilder().clearUpdatedAt().clearMetadata().build());
            if (!equals) {
                client.update(property);
                log.info("update Property: {} from: {} to: {}", hisProperty.getMetadata().getName(), hisProperty, property);
            }
        }
    }

    /**
     * Check if the index rules exist and update them if necessary
     */
    private void checkIndexRules(String modelName, List<IndexRule> indexRules, BanyanDBClient client) throws BanyanDBException {
        for (IndexRule indexRule : indexRules) {
            if (checkIndexRuleProcessed(modelName, indexRule)) {
                return;
            }
            IndexRule hisIndexRule = client.findIndexRule(
                indexRule.getMetadata().getGroup(), indexRule.getMetadata().getName());
            if (hisIndexRule == null) {
                try {
                    client.define(indexRule);
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
                                             .clearMetadata()
                                             .build()
                                             .equals(indexRule.toBuilder().clearUpdatedAt().clearMetadata().build());
                if (!equals) {
                    client.update(indexRule);
                    log.info(
                        "update IndexRule: {} from: {} to: {}", hisIndexRule.getMetadata().getName(), hisIndexRule,
                        indexRule
                    );
                }
            }
        }
    }

    /**
     * Check if the index rule binding exists and update it if necessary.
     */
    private void checkIndexRuleBinding(List<IndexRule> indexRules,
                                       String group,
                                       String name,
                                       BanyandbCommon.Catalog catalog,
                                       BanyanDBClient client) throws BanyanDBException {
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
            try {
                client.define(indexRuleBinding);
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
                                                .clearMetadata()
                                                .clearBeginAt()
                                                .clearExpireAt()
                                                .build()
                                                .equals(indexRuleBinding.toBuilder().clearMetadata().build());
            if (!equals) {
                // update binding and use the same begin expire time
                client.update(indexRuleBinding.toBuilder()
                                              .setBeginAt(hisIndexRuleBinding.getBeginAt())
                                              .setExpireAt(hisIndexRuleBinding.getExpireAt())
                                              .build());
                log.info(
                    "update IndexRuleBinding: {} from: {} to: {}", hisIndexRuleBinding.getMetadata().getName(),
                    hisIndexRuleBinding, indexRuleBinding
                );
            }
        }
    }

    /**
     * Check if the TopN aggregation exists and update it if necessary.
     * If the TopN rules are not used, will be checked and deleted after install, in the `BanyanDBStorageProvider.notifyAfterCompleted()`
     */
    private void checkTopNAggregation(Model model, BanyanDBClient client) throws BanyanDBException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        if (schema.getTopNSpecs() == null) {
            return;
        }
        for (TopNAggregation topNAggregation : schema.getTopNSpecs().values()) {
            String topNName = topNAggregation.getMetadata().getName();
            TopNAggregation hisTopNAggregation = client.findTopNAggregation(schema.getMetadata().getGroup(), topNName);
            if (hisTopNAggregation == null) {
                try {
                    client.define(topNAggregation);
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
                                                   .clearMetadata()
                                                   .build()
                                                   .equals(topNAggregation.toBuilder().clearMetadata().build());
                if (!equals) {
                    client.update(topNAggregation);
                    log.info(
                        "update TopNAggregation: {} from: {} to: {}", hisTopNAggregation.getMetadata().getName(),
                        hisTopNAggregation, topNAggregation
                    );
                }
            }
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
