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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Measure;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase.Stream;
import org.apache.skywalking.banyandb.v1.client.metadata.MetadataCache;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@Slf4j
public class BanyanDBIndexInstaller extends ModelInstaller {
    private final BanyanDBStorageConfig config;

    public BanyanDBIndexInstaller(Client client, ModuleManager moduleManager, BanyanDBStorageConfig config) {
        super(client, moduleManager);
        this.config = config;
        MetadataRegistry.INSTANCE.initializeIntervals(config.getSpecificGroupSettings());
    }

    @Override
    public boolean isExists(Model model) throws StorageException {
        if (!model.isTimeSeries()) {
            return true;
        }
        final ConfigService configService = moduleManager.find(CoreModule.NAME)
                                                         .provider()
                                                         .getService(ConfigService.class);
        final MetadataRegistry.SchemaMetadata metadata = MetadataRegistry.INSTANCE.parseMetadata(
            model, config, configService);
        try {
            final BanyanDBClient c = ((BanyanDBStorageClient) this.client).client;
            // first check resource existence and create group if necessary
            final boolean resourceExist = metadata.checkResourceExistence(c);
            if (!resourceExist) {
                return false;
            } else {
                // register models only locally(Schema cache) but not remotely
                if (model.isRecord()) { // stream
                    MetadataRegistry.INSTANCE.registerStreamModel(model, config, configService);
                } else { // measure
                    MetadataRegistry.INSTANCE.registerMeasureModel(model, config, configService);
                }
                // pre-load remote schema for java client
                MetadataCache.EntityMetadata remoteMeta = metadata.updateRemoteSchema(c);
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
            ConfigService configService = moduleManager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(ConfigService.class);
            if (model.isRecord()) { // stream
                StreamModel streamModel = MetadataRegistry.INSTANCE.registerStreamModel(model, config, configService);
                Stream stream = streamModel.getStream();
                if (stream != null) {
                    log.info("install stream schema {}", model.getName());
                    final BanyanDBClient client = ((BanyanDBStorageClient) this.client).client;
                    try {
                        if (CollectionUtils.isNotEmpty(streamModel.getIndexRules())) {
                            client.define(stream, streamModel.getIndexRules());
                        } else {
                            client.define(stream);
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
                        if (CollectionUtils.isNotEmpty(measureModel.getIndexRules())) {
                            client.define(measure, measureModel.getIndexRules());
                        } else {
                            client.define(measure);
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
                        schema.installTopNAggregation(client);
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
}
