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
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.base;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexStructures;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsInstaller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.StorageModuleElasticsearch7Config;

@Slf4j
public class StorageEs7Installer extends StorageEsInstaller {
    public StorageEs7Installer(final Client client,
                               final ModuleManager moduleManager,
                               final StorageModuleElasticsearch7Config config) throws StorageException {
        super(client, moduleManager, config);
    }

    @Override
    protected IndexStructures getStructures() {
        return new IndexEs7Structures();
    }
}
