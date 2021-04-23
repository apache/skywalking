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

package org.apache.skywalking.oap.server.storage.plugin.influxdb;

import lombok.extern.slf4j.Slf4j;
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
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.BatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.HistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.InfluxStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.AggregationQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.AlarmQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.BrowserLogQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.EventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.LogQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.MetadataQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.MetricsQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.NetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.ProfileTaskLogQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.ProfileTaskQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.ProfileThreadSnapshotQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.TopNRecordsQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.TopologyQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.TraceQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.UITemplateManagementDAOImpl;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class InfluxStorageProvider extends ModuleProvider {
    private final InfluxStorageConfig config;
    private InfluxClient client;

    public InfluxStorageProvider() {
        config = new InfluxStorageConfig();
    }

    @Override
    public String name() {
        return "influxdb";
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

        client = new InfluxClient(config);

        this.registerServiceImplementation(IBatchDAO.class, new BatchDAO(client));
        this.registerServiceImplementation(StorageDAO.class, new InfluxStorageDAO(client));

        this.registerServiceImplementation(INetworkAddressAliasDAO.class, new NetworkAddressAliasDAO(client));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new MetadataQuery(client));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new TopologyQuery(client));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new MetricsQuery(client));
        this.registerServiceImplementation(ITraceQueryDAO.class, new TraceQuery(client));
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new BrowserLogQuery(client));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new AggregationQuery(client));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new AlarmQuery(client));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new TopNRecordsQuery(client));
        this.registerServiceImplementation(ILogQueryDAO.class, new LogQuery(client));

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new ProfileTaskQuery(client));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new ProfileThreadSnapshotQuery(client));
        this.registerServiceImplementation(
            IProfileTaskLogQueryDAO.class, new ProfileTaskLogQuery(client, config.getFetchTaskLogMaxSize()));

        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new HistoryDeleteDAO(client));
        this.registerServiceImplementation(UITemplateManagementDAO.class, new UITemplateManagementDAOImpl(client));

        this.registerServiceImplementation(IEventQueryDAO.class, new EventQueryDAO(client));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        MetricsCreator metricCreator = getManager().find(TelemetryModule.NAME)
                                                   .provider()
                                                   .getService(MetricsCreator.class);
        HealthCheckMetrics healthChecker = metricCreator.createHealthCheckerGauge(
            "storage_influxdb", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        client.registerChecker(healthChecker);
        try {
            client.connect();

            InfluxTableInstaller installer = new InfluxTableInstaller(client, getManager());
            getManager().find(CoreModule.NAME).provider().getService(ModelCreator.class).addModelListener(installer);
        } catch (StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
