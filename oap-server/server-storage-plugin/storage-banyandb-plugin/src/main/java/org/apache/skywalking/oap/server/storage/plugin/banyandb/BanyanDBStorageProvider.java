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

import org.apache.skywalking.banyandb.v1.client.metadata.Group;
import org.apache.skywalking.banyandb.v1.client.metadata.IntervalRule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
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
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ISpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBEBPFProfilingScheduleQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBNetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBServiceLabelDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBTagAutocompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBHistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBSpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBTraceQueryDAO;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

public class BanyanDBStorageProvider extends ModuleProvider {
    private BanyanDBStorageConfig config;
    private BanyanDBStorageClient client;
    private ModelInstaller modelInstaller;

    private IntervalRule bypass = IntervalRule.create(IntervalRule.Unit.UNSPECIFIED, 0);

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
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        this.registerServiceImplementation(StorageBuilderFactory.class, new StorageBuilderFactory.Default());

        this.client = new BanyanDBStorageClient(config.getHost(), config.getPort());
        this.modelInstaller = new BanyanDBIndexInstaller(client, getManager(), this.config);

        // Stream
        this.registerServiceImplementation(
            IBatchDAO.class, new BanyanDBBatchDAO(client, config.getMaxBulkSize(), config.getFlushInterval(),
                                                  config.getConcurrentWriteThreads()
            ));
        this.registerServiceImplementation(StorageDAO.class, new BanyanDBStorageDAO(client));
        this.registerServiceImplementation(INetworkAddressAliasDAO.class, new BanyanDBNetworkAddressAliasDAO(client));
        this.registerServiceImplementation(ITraceQueryDAO.class, new BanyanDBTraceQueryDAO(client));
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new BanyanDBBrowserLogQueryDAO(client));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new BanyanDBMetadataQueryDAO(client));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new BanyanDBAlarmQueryDAO(client));
        this.registerServiceImplementation(ILogQueryDAO.class, new BanyanDBLogQueryDAO(client));
        this.registerServiceImplementation(
            IProfileTaskQueryDAO.class, new BanyanDBProfileTaskQueryDAO(client,
                                                                        this.config.getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(
            IProfileTaskLogQueryDAO.class, new BanyanDBProfileTaskLogQueryDAO(client,
                                                                              this.config.getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new BanyanDBProfileThreadSnapshotQueryDAO(client,
                                                                                            this.config.getProfileTaskQueryMaxSize()
            ));
        this.registerServiceImplementation(UITemplateManagementDAO.class, new BanyanDBUITemplateManagementDAO(client));
        this.registerServiceImplementation(IEventQueryDAO.class, new BanyanDBEventQueryDAO(client));
        this.registerServiceImplementation(ITopologyQueryDAO.class, new BanyanDBTopologyQueryDAO(client));
        this.registerServiceImplementation(IEBPFProfilingTaskDAO.class, new BanyanDBEBPFProfilingTaskDAO(client));
        this.registerServiceImplementation(IEBPFProfilingDataDAO.class, new BanyanDBEBPFProfilingDataDAO(client));
        this.registerServiceImplementation(
            IEBPFProfilingScheduleDAO.class, new BanyanDBEBPFProfilingScheduleQueryDAO(client));
        this.registerServiceImplementation(IContinuousProfilingPolicyDAO.class, new BanyanDBContinuousProfilingPolicyDAO(client));

        this.registerServiceImplementation(IServiceLabelDAO.class, new BanyanDBServiceLabelDAO(client));
        this.registerServiceImplementation(ITagAutoCompleteQueryDAO.class, new BanyanDBTagAutocompleteQueryDAO(client));
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new BanyanDBHistoryDeleteDAO());
        this.registerServiceImplementation(IMetricsQueryDAO.class, new BanyanDBMetricsQueryDAO(client));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new BanyanDBAggregationQueryDAO(client));
        this.registerServiceImplementation(IRecordsQueryDAO.class, new BanyanDBRecordsQueryDAO(client));
        this.registerServiceImplementation(IZipkinQueryDAO.class, new BanyanDBZipkinQueryDAO(client));
        this.registerServiceImplementation(ISpanAttachedEventQueryDAO.class, new BanyanDBSpanAttachedEventQueryDAO(client));
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
            this.client.defineIfEmpty(Group.create(BanyanDBUITemplateManagementDAO.GROUP));
            this.modelInstaller.start();

            getManager().find(CoreModule.NAME).provider().getService(ModelCreator.class).addModelListener(modelInstaller);
        } catch (Exception e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
