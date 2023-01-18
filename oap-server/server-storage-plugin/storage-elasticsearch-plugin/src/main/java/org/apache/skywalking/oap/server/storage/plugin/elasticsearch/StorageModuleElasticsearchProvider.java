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

import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.MultipleFilesChangeMonitor;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.BatchProcessEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.HistoryDeleteEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.StorageEsInstaller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.TimeSeriesUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache.NetworkAddressAliasEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AggregationQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AlarmQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.BrowserLogQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.EBPFProfilingDataEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.EBPFProfilingScheduleEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.EBPFProfilingTaskEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ESEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.LogQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.MetadataQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.MetricsQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileTaskLogEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileTaskQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ProfileThreadSnapshotQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.RecordsQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.ServiceLabelEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.SpanAttachedEventEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TopologyQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.TraceQueryEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.UITemplateManagementEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.zipkin.ZipkinQueryEsDAO;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * The storage provider for ElasticSearch 6/7 and OpenSearch 1.x.
 */
@Slf4j
public class StorageModuleElasticsearchProvider extends ModuleProvider {

    protected StorageModuleElasticsearchConfig config;
    protected ElasticSearchClient elasticSearchClient;
    protected StorageEsInstaller modelInstaller;

    @Override
    public String name() {
        return "elasticsearch";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<StorageModuleElasticsearchConfig>() {
            @Override
            public Class type() {
                return StorageModuleElasticsearchConfig.class;
            }

            @Override
            public void onInitialized(final StorageModuleElasticsearchConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        this.registerServiceImplementation(StorageBuilderFactory.class, new StorageBuilderFactory.Default());

        if (StringUtil.isEmpty(config.getNamespace())) {
            config.setNamespace("sw");
        } else {
            config.setNamespace(config.getNamespace().toLowerCase());
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

                if (elasticSearchClient == null) {
                    // In the startup process, we just need to change the username/password
                } else {
                    // The client has connected, updates the config and connects again.
                    elasticSearchClient.setUser(config.getUser());
                    elasticSearchClient.setPassword(config.getPassword());
                    elasticSearchClient.setTrustStorePass(config.getTrustStorePass());
                    elasticSearchClient.connect();
                }
            }, config.getSecretsManagementFile(), config.getTrustStorePath());
            /*
             * By leveraging the sync update check feature when startup.
             */
            monitor.start();
        }

        elasticSearchClient = new ElasticSearchClient(
            config.getClusterNodes(), config.getProtocol(), config.getTrustStorePath(), config
            .getTrustStorePass(), config.getUser(), config.getPassword(),
            indexNameConverter(config.getNamespace()), config.getConnectTimeout(),
            config.getSocketTimeout(), config.getResponseTimeout(),
            config.getNumHttpClientThread()
        );
        modelInstaller = new StorageEsInstaller(elasticSearchClient, getManager(), config);

        this.registerServiceImplementation(
            IBatchDAO.class,
            new BatchProcessEsDAO(elasticSearchClient, config.getBulkActions(), config
                .getFlushInterval(), config.getConcurrentRequests())
        );
        this.registerServiceImplementation(StorageDAO.class, new StorageEsDAO(elasticSearchClient));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new HistoryDeleteEsDAO(elasticSearchClient));
        this.registerServiceImplementation(
            INetworkAddressAliasDAO.class, new NetworkAddressAliasEsDAO(elasticSearchClient, config));
        this.registerServiceImplementation(ITopologyQueryDAO.class, new TopologyQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new MetricsQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(
            ITraceQueryDAO.class, new TraceQueryEsDAO(elasticSearchClient, config.getSegmentQueryMaxSize()));
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new BrowserLogQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class, new MetadataQueryEsDAO(elasticSearchClient, config));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new AggregationQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new AlarmQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IRecordsQueryDAO.class, new RecordsQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(ILogQueryDAO.class, new LogQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(
            IProfileTaskQueryDAO.class, new ProfileTaskQueryEsDAO(elasticSearchClient, config
                .getProfileTaskQueryMaxSize()));
        this.registerServiceImplementation(
            IProfileTaskLogQueryDAO.class, new ProfileTaskLogEsDAO(elasticSearchClient, config
                .getProfileTaskQueryMaxSize()));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new ProfileThreadSnapshotQueryEsDAO(elasticSearchClient, config
                .getProfileTaskQueryMaxSize()));
        this.registerServiceImplementation(
            UITemplateManagementDAO.class, new UITemplateManagementEsDAO(elasticSearchClient));

        this.registerServiceImplementation(IEventQueryDAO.class, new ESEventQueryDAO(elasticSearchClient));

        this.registerServiceImplementation(
            IEBPFProfilingTaskDAO.class,
            new EBPFProfilingTaskEsDAO(elasticSearchClient, config)
        );
        this.registerServiceImplementation(
            IEBPFProfilingScheduleDAO.class,
            new EBPFProfilingScheduleEsDAO(elasticSearchClient, config)
        );
        this.registerServiceImplementation(
            IEBPFProfilingDataDAO.class,
            new EBPFProfilingDataEsDAO(elasticSearchClient, config)
        );
        this.registerServiceImplementation(
            IServiceLabelDAO.class,
            new ServiceLabelEsDAO(elasticSearchClient, config)
        );
        this.registerServiceImplementation(
            ITagAutoCompleteQueryDAO.class, new TagAutoCompleteQueryDAO(elasticSearchClient));
        this.registerServiceImplementation(
            IZipkinQueryDAO.class, new ZipkinQueryEsDAO(elasticSearchClient));
        this.registerServiceImplementation(
            ISpanAttachedEventQueryDAO.class, new SpanAttachedEventEsDAO(elasticSearchClient, config));
        IndexController.INSTANCE.setLogicSharding(config.isLogicSharding());
        IndexController.INSTANCE.setEnableCustomRouting(config.isEnableCustomRouting());
    }

    @Override
    public void start() throws ModuleStartException {
        MetricsCreator metricCreator = getManager().find(TelemetryModule.NAME)
                                                   .provider()
                                                   .getService(MetricsCreator.class);
        HealthCheckMetrics healthChecker = metricCreator.createHealthCheckerGauge(
            "storage_elasticsearch", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        elasticSearchClient.registerChecker(healthChecker);
        try {
            elasticSearchClient.connect();

            final ConfigService service = getManager().find(CoreModule.NAME).provider().getService(ConfigService.class);
            // Add 5s to make sure OAP has at least done persistent once.
            // By default, the persistent period is 25 seconds and ElasticSearch refreshes in every 30 seconds.
            modelInstaller.setIndexRefreshInterval(service.getPersistentPeriod() + 5);
            modelInstaller.start();

            getManager().find(CoreModule.NAME)
                        .provider()
                        .getService(ModelCreator.class)
                        .addModelListener(modelInstaller);
        } catch (Exception e) {
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

    public static Function<String, String> indexNameConverter(String namespace) {
        return indexName -> {
            if (StringUtil.isNotEmpty(namespace)) {
                return namespace + "_" + indexName;
            }
            return indexName;
        };
    }
}
