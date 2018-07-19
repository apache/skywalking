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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch;

import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class StorageModuleElasticsearchProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(StorageModuleElasticsearchProvider.class);

    private final StorageModuleElasticsearchConfig storageConfig;

    public StorageModuleElasticsearchProvider() {
        super();
        this.storageConfig = new StorageModuleElasticsearchConfig();
    }

    @Override
    public String name() {
        return "elasticsearch";
    }

    @Override
    public Class module() {
        return StorageModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return storageConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
    }

    @Override
    public void start() throws ModuleStartException {
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }
}
