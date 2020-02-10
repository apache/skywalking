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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7;

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
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
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.HistoryDeleteEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.*;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ttl.ElasticsearchStorageTTL;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.cache.EndpointInventoryCacheEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.cache.NetworkAddressInventoryCacheEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.cache.ServiceInstanceInventoryCacheEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.cache.ServiceInventoryCacheEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.client.ElasticSearch7Client;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.dao.StorageEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.base.StorageEs7Installer;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.lock.RegisterLockEs77DAOImpl;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.lock.RegisterLockEs7Installer;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.*;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * @author peng-yongsheng, jian.tan
 * @author kezhenxu94
 */
public class StorageModuleElasticsearch7Provider extends ModuleProvider {

    protected final StorageModuleElasticsearch7Config config;
    protected ElasticSearch7Client elasticSearch7Client;

    public StorageModuleElasticsearch7Provider() {
        super();
        this.config = new StorageModuleElasticsearch7Config();
    }

    @Override
    public String name() {
        return "elasticsearch7";
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
        elasticSearch7Client = new ElasticSearch7Client(config.getClusterNodes(), config.getProtocol(), config.getTrustStorePath(), config.getTrustStorePass(), config.getNameSpace(), config.getUser(), config.getPassword());

        this.registerServiceImplementation(IBatchDAO.class, new BatchProcessEsDAO(elasticSearch7Client, config.getBulkActions(), config.getFlushInterval(), config.getConcurrentRequests()));
        this.registerServiceImplementation(StorageDAO.class, new StorageEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(IRegisterLockDAO.class, new RegisterLockEs77DAOImpl(elasticSearch7Client));
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new HistoryDeleteEsDAO(getManager(), elasticSearch7Client, new ElasticsearchStorageTTL()));

        this.registerServiceImplementation(IServiceInventoryCacheDAO.class, new ServiceInventoryCacheEs7DAO(elasticSearch7Client, config.getResultWindowMaxSize()));
        this.registerServiceImplementation(IServiceInstanceInventoryCacheDAO.class, new ServiceInstanceInventoryCacheEs7DAO(elasticSearch7Client, config.getResultWindowMaxSize()));
        this.registerServiceImplementation(IEndpointInventoryCacheDAO.class, new EndpointInventoryCacheEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(INetworkAddressInventoryCacheDAO.class, new NetworkAddressInventoryCacheEs7DAO(elasticSearch7Client, config.getResultWindowMaxSize()));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new TopologyQueryEsDAO(elasticSearch7Client));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new MetricsQueryEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(ITraceQueryDAO.class, new TraceQueryEs7DAO(elasticSearch7Client, config.getSegmentQueryMaxSize()));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new MetadataQueryEs7DAO(elasticSearch7Client, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new AggregationQueryEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new AlarmQueryEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new TopNRecordsQueryEsDAO(elasticSearch7Client));
        this.registerServiceImplementation(ILogQueryDAO.class, new LogQueryEs7DAO(elasticSearch7Client));

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new ProfileTaskQueryEsDAO(elasticSearch7Client, config.getProfileTaskQueryMaxSize()));
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new ProfileTaskLogEsDAO(elasticSearch7Client, config.getProfileTaskQueryMaxSize()));
        this.registerServiceImplementation(IProfileThreadSnapshotQueryDAO.class, new ProfileThreadSnapshotQueryEsDAO(elasticSearch7Client, config.getProfileTaskQueryMaxSize()));
    }

    @Override
    public void start() throws ModuleStartException {
        overrideCoreModuleTTLConfig();

        try {
            elasticSearch7Client.connect();

            StorageEs7Installer installer = new StorageEs7Installer(getManager(), config);
            installer.install(elasticSearch7Client);

            RegisterLockEs7Installer lockInstaller = new RegisterLockEs7Installer(elasticSearch7Client);
            lockInstaller.install();
        } catch (StorageException | IOException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException | CertificateException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() {
    }

    @Override
    public String[] requiredModules() {
        return new String[]{CoreModule.NAME};
    }

    private void overrideCoreModuleTTLConfig() {
        ConfigService configService = getManager().find(CoreModule.NAME).provider().getService(ConfigService.class);
        configService.getDataTTLConfig().setRecordDataTTL(config.getRecordDataTTL());
        configService.getDataTTLConfig().setMinuteMetricsDataTTL(config.getMinuteMetricsDataTTL());
        configService.getDataTTLConfig().setHourMetricsDataTTL(config.getHourMetricsDataTTL());
        configService.getDataTTLConfig().setDayMetricsDataTTL(config.getDayMetricsDataTTL());
        configService.getDataTTLConfig().setMonthMetricsDataTTL(config.getMonthMetricsDataTTL());
    }
}
