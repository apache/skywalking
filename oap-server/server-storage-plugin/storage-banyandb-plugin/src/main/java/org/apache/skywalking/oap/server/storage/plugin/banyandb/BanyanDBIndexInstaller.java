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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Group;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon.IntervalRule;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRule;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.IndexRuleBinding;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.TopNAggregation;
import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.v1.client.metadata.MetadataCache;
import org.apache.skywalking.banyandb.v1.client.metadata.ResourceExist;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.config.DownSamplingConfigService;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@Slf4j
public class BanyanDBIndexInstaller extends ModelInstaller {
    // BanyanDB group setting aligned with the OAP settings
    private static final Set<String/*group name*/> GROUP_ALIGNED = new HashSet<>();
    private static final Map<String/*group name*/, Map<String/*rule name*/, IndexRule>> GROUP_INDEX_RULES = new HashMap<>();
    private final BanyanDBStorageConfig config;

    public BanyanDBIndexInstaller(Client client, ModuleManager moduleManager, BanyanDBStorageConfig config) {
        super(client, moduleManager);
        this.config = config;
    }

    @Override
    public boolean isExists(Model model) throws StorageException {
        if (!model.isTimeSeries()) {
            return true;
        }
        final DownSamplingConfigService downSamplingConfigService = moduleManager.find(CoreModule.NAME)
                                                         .provider()
                                                         .getService(DownSamplingConfigService.class);
        final MetadataRegistry.SchemaMetadata metadata = MetadataRegistry.INSTANCE.parseMetadata(
            model, config, downSamplingConfigService);
        try {
            final BanyanDBClient c = ((BanyanDBStorageClient) this.client).client;
            // first check resource existence and create group if necessary
            final boolean resourceExist = checkResourceExistence(metadata, c);
            if (!resourceExist) {
                return false;
            } else {
                // register models only locally(Schema cache) but not remotely
                if (model.isRecord()) { // stream
                    StreamModel streamModel = MetadataRegistry.INSTANCE.registerStreamModel(
                        model, config, downSamplingConfigService);
                    if (!RunningMode.isNoInitMode()) {
                        checkStream(streamModel.getStream(), c);
                        checkIndexRules(model.getName(), streamModel.getIndexRules(), c);
                        checkIndexRuleBinding(
                            streamModel.getIndexRules(), metadata.getGroup(), metadata.name(),
                            BanyandbCommon.Catalog.CATALOG_STREAM, c
                        );
                        // Stream not support server side TopN pre-aggregation
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
                return true;
            }
        } catch (BanyanDBException ex) {
            throw new StorageException("fail to check existence", ex);
        }
    }

    @Override
    public void createTable(Model model) throws StorageException {
        try {
            DownSamplingConfigService configService = moduleManager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(DownSamplingConfigService.class);
            if (model.isRecord()) { // stream
                StreamModel streamModel = MetadataRegistry.INSTANCE.registerStreamModel(model, config, configService);
                Stream stream = streamModel.getStream();
                if (stream != null) {
                    log.info("install stream schema {}", model.getName());
                    final BanyanDBClient client = ((BanyanDBStorageClient) this.client).client;
                    try {
                        client.define(stream);
                        if (CollectionUtils.isNotEmpty(streamModel.getIndexRules())) {
                            for (IndexRule indexRule : streamModel.getIndexRules()) {
                                defineIndexRule(model.getName(), indexRule, client);
                            }
                            defineIndexRuleBinding(
                                streamModel.getIndexRules(), stream.getMetadata().getGroup(), stream.getMetadata().getName(),
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
            } else { // measure
                MeasureModel measureModel = MetadataRegistry.INSTANCE.registerMeasureModel(model, config, configService);
                Measure measure = measureModel.getMeasure();
                if (measure != null) {
                    log.info("install measure schema {}", model.getName());
                    final BanyanDBClient client = ((BanyanDBStorageClient) this.client).client;
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
                    try {
                        defineTopNAggregation(schema, client);
                    } catch (BanyanDBException ex) {
                        if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                            log.info("Measure schema {}_{} TopN({}) already created by another OAP node",
                                     model.getName(),
                                     model.getDownsampling(),
                                     schema.getTopNSpec());
                        } else {
                            throw ex;
                        }
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
        return g.getResourceOpts().getShardNum() != metadata.getShard()
            || g.getResourceOpts().getSegmentInterval().getNum() != metadata.getSegmentIntervalDays()
            || g.getResourceOpts().getTtl().getNum() != metadata.getTtlDays();
    }

    private boolean checkResourceExistence(MetadataRegistry.SchemaMetadata metadata,
                                           BanyanDBClient client) throws BanyanDBException {
        ResourceExist resourceExist;
        Group.Builder gBuilder
            = Group.newBuilder()
                   .setMetadata(BanyandbCommon.Metadata.newBuilder().setName(metadata.getGroup()))
                   .setResourceOpts(BanyandbCommon.ResourceOpts.newBuilder()
                                                               .setShardNum(metadata.getShard())
                                                               .setSegmentInterval(
                                                                   IntervalRule.newBuilder()
                                                                               .setUnit(
                                                                                   IntervalRule.Unit.UNIT_DAY)
                                                                               .setNum(
                                                                                   metadata.getSegmentIntervalDays()))
                                                               .setTtl(
                                                                   IntervalRule.newBuilder()
                                                                               .setUnit(
                                                                                   IntervalRule.Unit.UNIT_DAY)
                                                                               .setNum(
                                                                                   metadata.getTtlDays())));
        switch (metadata.getKind()) {
            case STREAM:
                resourceExist = client.existStream(metadata.getGroup(), metadata.name());
                gBuilder.setCatalog(BanyandbCommon.Catalog.CATALOG_STREAM).build();
                break;
            case MEASURE:
                resourceExist = client.existMeasure(metadata.getGroup(), metadata.name());
                gBuilder.setCatalog(BanyandbCommon.Catalog.CATALOG_MEASURE).build();
                break;
            default:
                throw new IllegalStateException("unknown metadata kind: " + metadata.getKind());
        }
        if (!RunningMode.isNoInitMode()) {
            if (!GROUP_ALIGNED.contains(metadata.getGroup())) {
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
                GROUP_ALIGNED.add(metadata.getGroup());
            }
        }
        return resourceExist.hasResource();
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
            default:
                throw new IllegalStateException("unknown metadata kind: " + metadata.getKind());
        }
    }

    private void defineTopNAggregation(MetadataRegistry.Schema schema, BanyanDBClient client) throws BanyanDBException {
        if (schema.getTopNSpec() == null) {
            if (schema.getMetadata().getKind() == MetadataRegistry.Kind.MEASURE) {
                log.debug("skip null TopN Schema for [{}]", schema.getMetadata().name());
            }
            return;
        }
        try {
            client.define(schema.getTopNSpec());
            log.info("installed TopN schema for measure {}", schema.getMetadata().name());
        } catch (BanyanDBException ex) {
            if (ex.getStatus().equals(Status.Code.ALREADY_EXISTS)) {
                log.info("TopNAggregation {} already created by another OAP node", schema.getTopNSpec());
            } else {
                throw ex;
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
        Map<String, IndexRule> rules = GROUP_INDEX_RULES.computeIfAbsent(
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
     * If the old index rule is not in the index rule binding, delete it.
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
     * If the old TopN aggregation is not in the schema, delete it.
     */
    private void checkTopNAggregation(Model model, BanyanDBClient client) throws BanyanDBException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(model);
        String topNName = MetadataRegistry.Schema.formatTopNName(schema.getMetadata().name());
        TopNAggregation hisTopNAggregation = client.findTopNAggregation(schema.getMetadata().getGroup(), topNName);

        if (schema.getTopNSpec() != null) {
            TopNAggregation topNAggregation = schema.getTopNSpec();
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
        } else {
            if (hisTopNAggregation != null) {
                client.deleteTopNAggregation(schema.getMetadata().getGroup(), topNName);
                log.info(
                    "delete deprecated TopNAggregation: {} from group: {}", hisTopNAggregation.getMetadata().getName(),
                    schema.getMetadata().getGroup()
                );
            }
        }
    }
}
