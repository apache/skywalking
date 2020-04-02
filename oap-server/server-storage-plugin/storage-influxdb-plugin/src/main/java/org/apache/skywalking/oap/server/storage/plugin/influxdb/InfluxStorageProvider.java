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
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
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
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.BatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.HistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.InfluxStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.AggregationQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.AlarmQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.InfluxMetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.InfluxNetworkAddressAlias;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.LogQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.MetricsQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.ProfileTaskLogQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.ProfileTaskQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.ProfileThreadSnapshotQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.TopNRecordsQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.TopologyQuery;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.query.TraceQuery;

@Slf4j
public class InfluxStorageProvider extends ModuleProvider {
    private InfluxStorageConfig config;
    private InfluxClient influxClient;

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
        influxClient = new InfluxClient(config);

        this.registerServiceImplementation(IBatchDAO.class, new BatchDAO(influxClient));
        this.registerServiceImplementation(StorageDAO.class, new InfluxStorageDAO(influxClient));

        this.registerServiceImplementation(INetworkAddressAliasDAO.class, new InfluxNetworkAddressAlias(influxClient));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new InfluxMetadataQueryDAO(influxClient));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new TopologyQuery(influxClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new MetricsQuery(influxClient));
        this.registerServiceImplementation(ITraceQueryDAO.class, new TraceQuery(influxClient));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new AggregationQuery(influxClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new AlarmQuery(influxClient));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new TopNRecordsQuery(influxClient));
        this.registerServiceImplementation(ILogQueryDAO.class, new LogQuery(influxClient));

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new ProfileTaskQuery(influxClient));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new ProfileThreadSnapshotQuery(influxClient));
        this.registerServiceImplementation(
            IProfileTaskLogQueryDAO.class, new ProfileTaskLogQuery(influxClient, config.getFetchTaskLogMaxSize()));

        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new HistoryDeleteDAO(influxClient));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        influxClient.connect();
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
