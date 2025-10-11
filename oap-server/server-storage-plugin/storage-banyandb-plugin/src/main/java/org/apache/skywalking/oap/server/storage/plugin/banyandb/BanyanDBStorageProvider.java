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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.RunningMode;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UIMenuManagementDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IJFRDataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
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
import org.apache.skywalking.oap.server.core.storage.query.IHierarchyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.storage.ttl.StorageTTLStatusQuery;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBEBPFProfilingScheduleQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBHierarchyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBNetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBServiceLabelDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBTagAutocompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBHistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBJFRDataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBSpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBZipkinQueryDAO;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class BanyanDBStorageProvider extends ModuleProvider {
    private BanyanDBStorageConfig config;
    private BanyanDBStorageClient client;
    private ModelInstaller modelInstaller;

    @Override
    public String name() {
        return "banyandb";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<BanyanDBStorageConfig>() {
            @Override
            public Class type() {
                return BanyanDBStorageConfig.class;
            }

            @Override
            public void onInitialized(final BanyanDBStorageConfig initialized) {
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        // load banyandb config
        config = new BanyanDBConfigLoader(this).loadConfig();
        if (StringUtil.isBlank(config.getGlobal().getNamespace())) {
            config.getGlobal().setNamespace("sw");
        }
        if (config.getMetricsDay().getTtl() > config.getMetadata().getTtl()) {
            throw new ModuleStartException("metricsDay ttl must be less than or equal to metadata ttl");
        }
        if (config.getMetricsHour().getTtl() > config.getMetadata().getTtl()) {
            throw new ModuleStartException("metricsHour must be less than or equal to metadata ttl");
        }
        if (config.getMetricsMin().getTtl() > config.getMetadata().getTtl()) {
            throw new ModuleStartException("metricsMin must be less than or equal to metadata ttl");
        }
        this.registerServiceImplementation(StorageBuilderFactory.class, new StorageBuilderFactory.Default());

        this.client = new BanyanDBStorageClient(config);
        this.modelInstaller = new BanyanDBIndexInstaller(client, getManager(), this.config);

        // Stream
        this.registerServiceImplementation(
            IBatchDAO.class, new BanyanDBBatchDAO(client, config.getGlobal().getMaxBulkSize(), config.getGlobal().getFlushInterval(),
                                                  config.getGlobal().getConcurrentWriteThreads()
            ));
        this.registerServiceImplementation(StorageDAO.class, new BanyanDBStorageDAO(client));
        this.registerServiceImplementation(INetworkAddressAliasDAO.class, new BanyanDBNetworkAddressAliasDAO(client, this.config));
        this.registerServiceImplementation(ITraceQueryDAO.class, new BanyanDBTraceQueryDAO(client, this.config.getGlobal().getSegmentQueryMaxSize(), getManager()));
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new BanyanDBBrowserLogQueryDAO(client));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new BanyanDBMetadataQueryDAO(client, this.config));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new BanyanDBAlarmQueryDAO(client));
        this.registerServiceImplementation(ILogQueryDAO.class, new BanyanDBLogQueryDAO(client));
        this.registerServiceImplementation(
            IProfileTaskQueryDAO.class, new BanyanDBProfileTaskQueryDAO(client,
                                                                        this.config.getGlobal().getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(
            IProfileTaskLogQueryDAO.class, new BanyanDBProfileTaskLogQueryDAO(client,
                                                                              this.config.getGlobal().getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new BanyanDBProfileThreadSnapshotQueryDAO(client,
                                                                                            this.config.getGlobal().getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(UITemplateManagementDAO.class, new BanyanDBUITemplateManagementDAO(client));
        this.registerServiceImplementation(UIMenuManagementDAO.class, new BanyanDBUIMenuManagementDAO(client));
        this.registerServiceImplementation(IEventQueryDAO.class, new BanyanDBEventQueryDAO(client));
        this.registerServiceImplementation(ITopologyQueryDAO.class, new BanyanDBTopologyQueryDAO(client));
        this.registerServiceImplementation(IEBPFProfilingTaskDAO.class, new BanyanDBEBPFProfilingTaskDAO(client));
        this.registerServiceImplementation(IEBPFProfilingDataDAO.class, new BanyanDBEBPFProfilingDataDAO(client, this.config.getGlobal().getProfileDataQueryBatchSize()));
        this.registerServiceImplementation(
            IEBPFProfilingScheduleDAO.class, new BanyanDBEBPFProfilingScheduleQueryDAO(client));
        this.registerServiceImplementation(IContinuousProfilingPolicyDAO.class, new BanyanDBContinuousProfilingPolicyDAO(client));

        this.registerServiceImplementation(IServiceLabelDAO.class, new BanyanDBServiceLabelDAO(client, this.config));
        this.registerServiceImplementation(ITagAutoCompleteQueryDAO.class, new BanyanDBTagAutocompleteQueryDAO(client));
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new BanyanDBHistoryDeleteDAO());
        this.registerServiceImplementation(IMetricsQueryDAO.class, new BanyanDBMetricsQueryDAO(client));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new BanyanDBAggregationQueryDAO(client));
        this.registerServiceImplementation(IRecordsQueryDAO.class, new BanyanDBRecordsQueryDAO(client));
        this.registerServiceImplementation(IZipkinQueryDAO.class, new BanyanDBZipkinQueryDAO(client));
        this.registerServiceImplementation(ISpanAttachedEventQueryDAO.class, new BanyanDBSpanAttachedEventQueryDAO(client, this.config.getGlobal().getProfileDataQueryBatchSize()));
        this.registerServiceImplementation(IHierarchyQueryDAO.class, new BanyanDBHierarchyQueryDAO(client, this.config));
        this.registerServiceImplementation(
                IAsyncProfilerTaskQueryDAO.class, new BanyanDBAsyncProfilerTaskQueryDAO(client,
                        this.config.getGlobal().getAsyncProfilerTaskQueryMaxSize()
                ));
        this.registerServiceImplementation(
                IAsyncProfilerTaskLogQueryDAO.class, new BanyanDBAsyncProfilerTaskLogQueryDAO(client,
                        this.config.getGlobal().getAsyncProfilerTaskQueryMaxSize()
                ));
        this.registerServiceImplementation(IJFRDataQueryDAO.class, new BanyanDBJFRDataQueryDAO(client));
        this.registerServiceImplementation(
            StorageTTLStatusQuery.class,
            new BanyanDBTTLStatusQuery(config)
        );
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        MetricsCreator metricCreator = getManager().find(TelemetryModule.NAME)
                                                   .provider()
                                                   .getService(MetricsCreator.class);
        HealthCheckMetrics healthChecker = metricCreator.createHealthCheckerGauge(
            "storage_banyandb", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        this.client.registerChecker(healthChecker);
        try {
            this.client.connect();
            this.modelInstaller.start();

            getManager().find(CoreModule.NAME).provider().getService(ModelCreator.class).addModelListener(modelInstaller);
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
        if (!RunningMode.isNoInitMode()) {
            try {
                List<BanyandbCommon.Group> groups = this.client.client.findGroups();
                cleanupUnusedTopNRules(groups);
                //todo: can not delete indexRules now, because banyanDB server can not delete or update Tags.
            } catch (BanyanDBException e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }

    // Cleanup TopN rules in BanyanDB server that are not configured in the current config.
    private void cleanupUnusedTopNRules(List<BanyandbCommon.Group> groups) throws BanyanDBException {
        Set<String> topNNames = new HashSet<>();
        this.config.getTopNConfigs().values().forEach(topNConfig -> {
            topNNames.addAll(topNConfig.keySet());
        });
        for (BanyandbCommon.Group group : groups) {
            if (BanyandbCommon.Catalog.CATALOG_MEASURE.equals(group.getCatalog())) {
                String groupName = group.getMetadata().getName();
                List<BanyandbDatabase.TopNAggregation> topNAggregations = this.client.client.findTopNAggregations(
                    groupName);
                if (CollectionUtils.isNotEmpty(topNAggregations)) {
                    for (BanyandbDatabase.TopNAggregation topNAggregation : topNAggregations) {
                        String topNName = topNAggregation.getMetadata().getName();
                        if (!topNNames.contains(topNName)) {
                            if (this.config.getGlobal().isCleanupUnusedTopNRules()) {
                                this.client.client.deleteTopNAggregation(groupName, topNName);
                                log.info(
                                    "Deleted unused topN rule from BanyanDB server: {}, group: {}. Please check bydb-topn.yml. " +
                                        "If you don't want to cleanup unused rules from server, please set cleanupUnusedTopNRules=false in bydb.yml",
                                    topNName, groupName
                                );
                            } else {
                                // Log the unused TopN aggregation.
                                log.warn(
                                    "Unused topN rule in BanyanDB server: {}, group: {}. Please check bydb-topn.yml. " +
                                        "If you want to cleanup unused rules from server, please set cleanupUnusedTopNRules=true in bydb.yml",
                                    topNName, groupName
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}
