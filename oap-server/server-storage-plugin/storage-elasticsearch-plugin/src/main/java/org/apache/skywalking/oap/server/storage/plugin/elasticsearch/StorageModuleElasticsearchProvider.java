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

import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.library.client.NameSpace;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class StorageModuleElasticsearchProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(StorageModuleElasticsearchProvider.class);

    private final StorageModuleElasticsearchConfig config;
    private final NameSpace nameSpace;
    private ElasticSearchClient elasticSearchClient;

    public StorageModuleElasticsearchProvider() {
        super();
        this.config = new StorageModuleElasticsearchConfig();
        this.nameSpace = new NameSpace();
    }

    @Override
    public String name() {
        return "elasticsearch";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        elasticSearchClient = new ElasticSearchClient(config.getClusterNodes(), nameSpace);

        this.registerServiceImplementation(IBatchDAO.class, new BatchProcessEsDAO(elasticSearchClient, config.getBulkActions(), config.getBulkSize(), config.getFlushInterval(), config.getConcurrentRequests()));
        this.registerServiceImplementation(IPersistenceDAO.class, new PersistenceEsDAO(elasticSearchClient, nameSpace));
    }

    @Override
    public void start() throws ModuleStartException {
        try {
            nameSpace.setNameSpace(config.getNameSpace());
            elasticSearchClient.initialize();

            StorageEsInstaller installer = new StorageEsInstaller(getManager(), config.getIndexShardsNumber(), config.getIndexReplicasNumber());
            installer.install(elasticSearchClient);
        } catch (StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[0];
    }
}
