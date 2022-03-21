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
import org.apache.skywalking.banyandb.v1.client.metadata.Catalog;
import org.apache.skywalking.banyandb.v1.client.metadata.Duration;
import org.apache.skywalking.banyandb.v1.client.metadata.Group;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
public class BanyanDBIndexInstaller extends ModelInstaller {
    private final ConfigService configService;

    public BanyanDBIndexInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
        this.configService = moduleManager.find(CoreModule.NAME)
                .provider()
                .getService(ConfigService.class);
    }

    @Override
    protected boolean isExists(Model model) throws StorageException {
        // TODO: get from BanyanDB and make a diff?
        return false;
    }

    @Override
    protected void createTable(Model model) throws StorageException {
        if (model.isTimeSeries() && model.isRecord()) { // stream
            StreamMetadata metaInfo = MetadataRegistry.INSTANCE.registerModel(model, this.configService);
            if (metaInfo != null) {
                log.info("install index {}", model.getName());
                ((BanyanDBStorageClient) client).define(
                        new Group(metaInfo.getGroup(), Catalog.STREAM, 2, 10, Duration.ofDays(7))
                );
                ((BanyanDBStorageClient) client).define(metaInfo);
            }
        } else if (model.isTimeSeries() && !model.isRecord()) { // measure
            log.info("skip measure index {}", model.getName());
        } else if (!model.isTimeSeries()) { // UITemplate
            log.info("skip property index {}", model.getName());
        }
    }
}
