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
import org.apache.skywalking.banyandb.v1.client.metadata.Group;
import org.apache.skywalking.banyandb.v1.client.metadata.Measure;
import org.apache.skywalking.banyandb.v1.client.metadata.Stream;
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
    }

    @Override
    protected boolean isExists(Model model) throws StorageException {
        final MetadataRegistry.SchemaMetadata metadata = MetadataRegistry.INSTANCE.parseMetadata(model, config);
        try {
            final BanyanDBClient c = ((BanyanDBStorageClient) this.client).client;
            // first check group
            Group g = metadata.getOrCreateGroup(c);
            if (g == null) {
                throw new StorageException("fail to create group " + metadata.getGroup());
            }
            log.info("group {} created", g.name());
            // then check entity schema
            if (metadata.findRemoteSchema(c).isPresent()) {
                MetadataRegistry.INSTANCE.registerModel(model, config);
                return true;
            }
            return false;
        } catch (BanyanDBException ex) {
            throw new StorageException("fail to check existence", ex);
        }
    }

    @Override
    protected void createTable(Model model) throws StorageException {
        try {
            if (model.isTimeSeries() && model.isRecord()) { // stream
                Stream stream = (Stream) MetadataRegistry.INSTANCE.registerModel(model, config);
                if (stream != null) {
                    log.info("install stream schema {}", model.getName());
                    ((BanyanDBStorageClient) client).define(stream);
                }
            } else if (model.isTimeSeries() && !model.isRecord()) { // measure
                Measure measure = (Measure) MetadataRegistry.INSTANCE.registerModel(model, config);
                if (measure != null) {
                    log.info("install measure schema {}", model.getName());
                    ((BanyanDBStorageClient) client).define(measure);
                }
            } else if (!model.isTimeSeries()) { // UITemplate
                log.info("skip property index {}", model.getName());
            }
        } catch (IOException ex) {
            throw new StorageException("fail to install schema", ex);
        }
    }
}
