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

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.v1.client.metadata.Measure;
import org.apache.skywalking.banyandb.v1.client.metadata.Stream;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import java.io.IOException;

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
        final ConfigService configService = moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class);
        final MetadataRegistry.SchemaMetadata metadata = MetadataRegistry.INSTANCE.parseMetadata(model, config, configService);
        try {
            final BanyanDBClient c = ((BanyanDBStorageClient) this.client).client;
            // first check resource existence and create group if necessary
            final boolean resourceExist = metadata.checkResourceExistence(c);
            if (!resourceExist) {
                return false;
            }

            // then check entity schema
            if (metadata.findRemoteSchema(c).isPresent()) {
                // register models only locally but not remotely
                if (model.isRecord()) { // stream
                    MetadataRegistry.INSTANCE.registerStreamModel(model, config, configService);
                } else { // measure
                    MetadataRegistry.INSTANCE.registerMeasureModel(model, config, configService);
                }
                return true;
            }

            throw new IllegalStateException("inconsistent state:" + metadata);
        } catch (BanyanDBException ex) {
            throw new StorageException("fail to check existence", ex);
        }
    }

    @Override
    public void createTable(Model model) throws StorageException {
        try {
            ConfigService configService = moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class);
            if (model.isRecord()) { // stream
                Stream stream = MetadataRegistry.INSTANCE.registerStreamModel(model, config, configService);
                if (stream != null) {
                    log.info("install stream schema {}", model.getName());
                    ((BanyanDBStorageClient) client).define(stream);
                }
            } else { // measure
                Measure measure = MetadataRegistry.INSTANCE.registerMeasureModel(model, config, configService);
                if (measure != null) {
                    log.info("install measure schema {}", measure.name());
                    ((BanyanDBStorageClient) client).define(measure);
                    final BanyanDBClient c = ((BanyanDBStorageClient) this.client).client;
                    MetadataRegistry.INSTANCE.findMetadata(model).installTopNAggregation(c);
                    log.info("installed TopN schema for measure {}", measure.name());
                }
            }
        } catch (IOException ex) {
            throw new StorageException("fail to install schema", ex);
        } catch (BanyanDBException ex) {
            throw new StorageException("fail to install TopN schema", ex);
        }
    }
}
