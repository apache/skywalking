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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common;

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
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
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
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCBatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCHistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCMetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCNetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCServiceLabelQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCSpanAttachedEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCTagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCUITemplateManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao.JDBCZipkinQueryDAO;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

public abstract class JDBCStorageProvider extends ModuleProvider {
    protected JDBCStorageConfig config;
    protected JDBCClient jdbcClient;
    protected JDBCTableInstaller modelInstaller;
    protected TableHelper tableHelper;

    /**
     * Different storage implementations have different ways to create the tables/indices,
     * the specific implementations should provide a model installer for that storage.
     */
    protected abstract ModelInstaller createModelInstaller();

    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override
    public ConfigCreator<? extends JDBCStorageConfig> newConfigCreator() {
        return new ConfigCreator<JDBCStorageConfig>() {
            @Override
            public Class<JDBCStorageConfig> type() {
                return JDBCStorageConfig.class;
            }

            @Override
            public void onInitialized(final JDBCStorageConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        jdbcClient = new JDBCClient(config.getProperties());
        modelInstaller = (JDBCTableInstaller) createModelInstaller();
        tableHelper = new TableHelper(getManager(), jdbcClient);

        this.registerServiceImplementation(
            StorageBuilderFactory.class,
            new StorageBuilderFactory.Default());

        this.registerServiceImplementation(
            IBatchDAO.class,
            new JDBCBatchDAO(
                jdbcClient,
                config.getMaxSizeOfBatchSql(),
                config.getAsyncBatchPersistentPoolSize()));
        this.registerServiceImplementation(
            StorageDAO.class,
            new JDBCStorageDAO(jdbcClient));

        this.registerServiceImplementation(
            INetworkAddressAliasDAO.class,
            new JDBCNetworkAddressAliasDAO(jdbcClient, getManager()));

        this.registerServiceImplementation(
            ITopologyQueryDAO.class,
            new JDBCTopologyQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IMetricsQueryDAO.class,
            new JDBCMetricsQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            ITraceQueryDAO.class,
            new JDBCTraceQueryDAO(getManager(), jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IBrowserLogQueryDAO.class,
            new JDBCBrowserLogQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class,
            new JDBCMetadataQueryDAO(jdbcClient, config.getMetadataQueryMaxSize(), getManager()));
        this.registerServiceImplementation(
            IAggregationQueryDAO.class,
            new JDBCAggregationQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IAlarmQueryDAO.class,
            new JDBCAlarmQueryDAO(jdbcClient, getManager(), tableHelper));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class,
            new JDBCHistoryDeleteDAO(jdbcClient, tableHelper, modelInstaller));
        this.registerServiceImplementation(
            IRecordsQueryDAO.class,
            new JDBCRecordsQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            ILogQueryDAO.class,
            new JDBCLogQueryDAO(jdbcClient, getManager(), tableHelper));

        this.registerServiceImplementation(
            IProfileTaskQueryDAO.class,
            new JDBCProfileTaskQueryDAO(jdbcClient, getManager()));
        this.registerServiceImplementation(
            IProfileTaskLogQueryDAO.class,
            new JDBCProfileTaskLogQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class,
            new JDBCProfileThreadSnapshotQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            UITemplateManagementDAO.class,
            new JDBCUITemplateManagementDAO(jdbcClient, tableHelper));

        this.registerServiceImplementation(
            IEventQueryDAO.class,
            new JDBCEventQueryDAO(jdbcClient, tableHelper));

        this.registerServiceImplementation(
            IEBPFProfilingTaskDAO.class,
            new JDBCEBPFProfilingTaskDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IEBPFProfilingScheduleDAO.class,
            new JDBCEBPFProfilingScheduleDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IEBPFProfilingDataDAO.class,
            new JDBCEBPFProfilingDataDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IServiceLabelDAO.class,
            new JDBCServiceLabelQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            ITagAutoCompleteQueryDAO.class,
            new JDBCTagAutoCompleteQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IZipkinQueryDAO.class,
            new JDBCZipkinQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            ISpanAttachedEventQueryDAO.class,
            new JDBCSpanAttachedEventQueryDAO(jdbcClient, tableHelper));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        MetricsCreator metricCreator =
            getManager()
                .find(TelemetryModule.NAME)
                .provider()
                .getService(MetricsCreator.class);
        HealthCheckMetrics healthChecker =
            metricCreator.createHealthCheckerGauge(
                "storage_" + name(),
                MetricsTag.EMPTY_KEY,
                MetricsTag.EMPTY_VALUE);
        jdbcClient.registerChecker(healthChecker);
        try {
            jdbcClient.connect();
            modelInstaller.start();

            getManager()
                .find(CoreModule.NAME)
                .provider()
                .getService(ModelCreator.class)
                .addModelListener(modelInstaller);
        } catch (StorageException e) {
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
