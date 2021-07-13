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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
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
import org.apache.skywalking.oap.server.library.util.MultipleFilesChangeMonitor;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.HistoryDeleteEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.NetworkAddressAliasEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileTaskLogEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileTaskQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TopNRecordsQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TopologyQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.UITemplateManagementEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.base.StorageEs7Installer;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.client.ElasticSearch7Client;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.dao.StorageEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.AggregationQueryEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.AlarmQueryEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.BrowserLogQueryEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.ES7EventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.LogQueryEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.MetadataQueryEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.MetricsQueryEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.ProfileThreadSnapshotQueryEs7DAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query.TraceQueryEs7DAO;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

import static org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchProvider.indexNameConverters;

/**
 * The storage provider for ElasticSearch 7.
 */
@Slf4j
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
        this.registerServiceImplementation(StorageBuilderFactory.class, new StorageBuilderFactory.Default());

        if (StringUtil.isEmpty(config.getNameSpace())) {
            config.setNameSpace("sw");
        } else {
            config.setNameSpace(config.getNameSpace().toLowerCase());
        }
        if (config.getDayStep() > 1) {
            TimeSeriesUtils.setDAY_STEP(config.getDayStep());
            TimeSeriesUtils.setSUPER_DATASET_DAY_STEP(config.getDayStep());
        }
        if (config.getSuperDatasetDayStep() > 0) {
            TimeSeriesUtils.setSUPER_DATASET_DAY_STEP(config.getSuperDatasetDayStep());
        }
        if (!StringUtil.isEmpty(config.getSecretsManagementFile())) {
            MultipleFilesChangeMonitor monitor = new MultipleFilesChangeMonitor(
                10, readableContents -> {
                final byte[] secretsFileContent = readableContents.get(0);
                if (secretsFileContent == null) {
                    return;
                }
                Properties secrets = new Properties();
                secrets.load(new ByteArrayInputStream(secretsFileContent));
                config.setUser(secrets.getProperty("user", null));
                config.setPassword(secrets.getProperty("password", null));
                config.setTrustStorePass(secrets.getProperty("trustStorePass", null));

                if (elasticSearch7Client == null) {
                    //In the startup process, we just need to change the username/password
                } else {
                    // The client has connected, updates the config and connects again.
                    elasticSearch7Client.setUser(config.getUser());
                    elasticSearch7Client.setPassword(config.getPassword());
                    elasticSearch7Client.setTrustStorePass(config.getTrustStorePass());
                    elasticSearch7Client.connect();
                }
            }, config.getSecretsManagementFile(), config.getTrustStorePass());
            /**
             * By leveraging the sync update check feature when startup.
             */
            monitor.start();
        }

        elasticSearch7Client = new ElasticSearch7Client(
            config.getClusterNodes(), config.getProtocol(), config.getTrustStorePath(), config
            .getTrustStorePass(), config.getUser(), config.getPassword(),
            indexNameConverters(config.getNameSpace()), config.getConnectTimeout(), config.getSocketTimeout()
        );
        this.registerServiceImplementation(
            IBatchDAO.class,
            new BatchProcessEsDAO(
                elasticSearch7Client, config.getBulkActions(), config.getFlushInterval(), config.getConcurrentRequests()
            )
        );
        this.registerServiceImplementation(StorageDAO.class, new StorageEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new HistoryDeleteEsDAO(elasticSearch7Client));
        this.registerServiceImplementation(
            INetworkAddressAliasDAO.class, new NetworkAddressAliasEsDAO(
                elasticSearch7Client,
                config.getResultWindowMaxSize()
            ));
        this.registerServiceImplementation(ITopologyQueryDAO.class, new TopologyQueryEsDAO(elasticSearch7Client));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new MetricsQueryEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(
            ITraceQueryDAO.class, new TraceQueryEs7DAO(elasticSearch7Client, config.getSegmentQueryMaxSize()));
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new BrowserLogQueryEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class, new MetadataQueryEs7DAO(elasticSearch7Client, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(
            IAggregationQueryDAO.class, new AggregationQueryEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new AlarmQueryEs7DAO(elasticSearch7Client));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new TopNRecordsQueryEsDAO(elasticSearch7Client));
        this.registerServiceImplementation(ILogQueryDAO.class, new LogQueryEs7DAO(elasticSearch7Client));

        this.registerServiceImplementation(
            IProfileTaskQueryDAO.class, new ProfileTaskQueryEsDAO(
                elasticSearch7Client,
                config.getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(
            IProfileTaskLogQueryDAO.class, new ProfileTaskLogEsDAO(
                elasticSearch7Client,
                config.getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new ProfileThreadSnapshotQueryEs7DAO(
                elasticSearch7Client,
                config.getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(
            UITemplateManagementDAO.class, new UITemplateManagementEsDAO(elasticSearch7Client));

        this.registerServiceImplementation(IEventQueryDAO.class, new ES7EventQueryDAO(elasticSearch7Client));
    }

    @Override
    public void start() throws ModuleStartException {
        MetricsCreator metricCreator = getManager().find(TelemetryModule.NAME)
                                                   .provider()
                                                   .getService(MetricsCreator.class);
        HealthCheckMetrics healthChecker = metricCreator.createHealthCheckerGauge(
            "storage_elasticsearch", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        elasticSearch7Client.registerChecker(healthChecker);
        try {
            elasticSearch7Client.connect();

            StorageEs7Installer installer = new StorageEs7Installer(elasticSearch7Client, getManager(), config);
            getManager().find(CoreModule.NAME).provider().getService(ModelCreator.class).addModelListener(installer);
        } catch (StorageException | IOException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException | CertificateException e) {
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
