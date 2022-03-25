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

package org.apache.skywalking.oap.server.storage.plugin.iotdb;

import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
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
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
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
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBBatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBHistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.base.IoTDBStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.cache.IoTDBNetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.management.IoTDBUITemplateManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.profile.IoTDBProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.profile.IoTDBProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.profile.IoTDBProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBEventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBMetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBTopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.query.IoTDBTraceQueryDAO;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class IoTDBStorageProvider extends ModuleProvider {
    private final IoTDBStorageConfig config;
    private IoTDBClient client;

    public IoTDBStorageProvider() {
        config = new IoTDBStorageConfig();
    }

    @Override
    public String name() {
        return "iotdb";
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

        client = new IoTDBClient(config);

        this.registerServiceImplementation(IBatchDAO.class, new IoTDBBatchDAO(client));
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new IoTDBHistoryDeleteDAO(client));
        this.registerServiceImplementation(StorageDAO.class, new IoTDBStorageDAO(client));

        this.registerServiceImplementation(INetworkAddressAliasDAO.class, new IoTDBNetworkAddressAliasDAO(client));

        this.registerServiceImplementation(UITemplateManagementDAO.class, new IoTDBUITemplateManagementDAO(client));

        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class,
                new IoTDBProfileTaskLogQueryDAO(client, config.getFetchTaskLogMaxSize()));
        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new IoTDBProfileTaskQueryDAO(client));
        this.registerServiceImplementation(IProfileThreadSnapshotQueryDAO.class,
                new IoTDBProfileThreadSnapshotQueryDAO(client));

        this.registerServiceImplementation(IAggregationQueryDAO.class, new IoTDBAggregationQueryDAO(client));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new IoTDBAlarmQueryDAO(client));
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new IoTDBBrowserLogQueryDAO(client));
        this.registerServiceImplementation(IEventQueryDAO.class, new IoTDBEventQueryDAO(client));
        this.registerServiceImplementation(ILogQueryDAO.class, new IoTDBLogQueryDAO(client));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new IoTDBMetadataQueryDAO(client));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new IoTDBMetricsQueryDAO(client));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new IoTDBTopNRecordsQueryDAO(client));
        this.registerServiceImplementation(ITopologyQueryDAO.class, new IoTDBTopologyQueryDAO(client));
        this.registerServiceImplementation(ITraceQueryDAO.class, new IoTDBTraceQueryDAO(client));

        this.registerServiceImplementation(IEBPFProfilingTaskDAO.class, new IoTDBEBPFProfilingTaskDAO(client));
        this.registerServiceImplementation(IEBPFProfilingScheduleDAO.class, new IoTDBEBPFProfilingScheduleDAO(client));
        this.registerServiceImplementation(IEBPFProfilingDataDAO.class, new IoTDBEBPFProfilingDataDAO(client));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        MetricsCreator metricCreator = getManager().find(TelemetryModule.NAME)
                .provider()
                .getService(MetricsCreator.class);
        HealthCheckMetrics healthChecker = metricCreator.createHealthCheckerGauge(
                "storage_iotdb", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        client.registerChecker(healthChecker);
        try {
            client.connect();

            IoTDBTableInstaller installer = new IoTDBTableInstaller(client, getManager());
            getManager().find(CoreModule.NAME).provider().getService(ModelCreator.class).addModelListener(installer);
        } catch (StorageException | IoTDBConnectionException | StatementExecutionException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override
    public String[] requiredModules() {
        return new String[]{CoreModule.NAME};
    }
}
