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

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.HistoryDeleteEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsInstaller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.EndpointInventoryCacheEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.NetworkAddressInventoryCacheEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.ServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.ServiceInventoryCacheEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock.RegisterLockDAOImpl;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.lock.RegisterLockInstaller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AggregationQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AlarmQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.MetadataQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.MetricQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TopNRecordsQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TopologyQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TraceQueryEsDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class StorageModuleElasticsearchProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(StorageModuleElasticsearchProvider.class);

    private final StorageModuleElasticsearchConfig config;
    private ElasticSearchClient elasticSearchClient;

    public StorageModuleElasticsearchProvider() {
        super();
        this.config = new StorageModuleElasticsearchConfig();
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
        if (!StringUtil.isEmpty(config.getNameSpace())) {
            config.setNameSpace(config.getNameSpace().toLowerCase());
        }
        elasticSearchClient = new ElasticSearchClient(config.getClusterNodes(), config.getNameSpace(), config.getUser(), config.getPassword());

        this.registerServiceImplementation(IBatchDAO.class, new BatchProcessEsDAO(elasticSearchClient, config.getBulkActions(), config.getBulkSize(), config.getFlushInterval(), config.getConcurrentRequests()));
        this.registerServiceImplementation(StorageDAO.class, new StorageEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IRegisterLockDAO.class, new RegisterLockDAOImpl(elasticSearchClient));
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new HistoryDeleteEsDAO(elasticSearchClient));

        this.registerServiceImplementation(IServiceInventoryCacheDAO.class, new ServiceInventoryCacheEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceInstanceInventoryCacheDAO.class, new ServiceInstanceInventoryCacheDAO(elasticSearchClient));
        this.registerServiceImplementation(IEndpointInventoryCacheDAO.class, new EndpointInventoryCacheEsDAO(elasticSearchClient));
        this.registerServiceImplementation(INetworkAddressInventoryCacheDAO.class, new NetworkAddressInventoryCacheEsDAO(elasticSearchClient));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new TopologyQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IMetricQueryDAO.class, new MetricQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(ITraceQueryDAO.class, new TraceQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new MetadataQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new AggregationQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new AlarmQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new TopNRecordsQueryEsDAO(elasticSearchClient));
    }

    @Override
    public void start() throws ModuleStartException {
        try {
            elasticSearchClient.connect();

            StorageEsInstaller installer = new StorageEsInstaller(getManager(), config.getIndexShardsNumber(), config.getIndexReplicasNumber());
            installer.install(elasticSearchClient);

            RegisterLockInstaller lockInstaller = new RegisterLockInstaller(elasticSearchClient);
            lockInstaller.install();
        } catch (StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
