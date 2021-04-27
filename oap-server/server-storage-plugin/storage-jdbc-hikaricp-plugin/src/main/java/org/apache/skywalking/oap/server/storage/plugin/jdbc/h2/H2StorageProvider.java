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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2;

import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
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
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2AggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2AlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2BatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2BrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2EventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2HistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2LogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2NetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2StorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2UITemplateManagementDAO;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * H2 Storage provider is for demonstration and preview only. I will find that haven't implemented several interfaces,
 * because not necessary, and don't consider about performance very much.
 * <p>
 * If someone wants to implement SQL-style database as storage, please just refer the logic.
 */
@Slf4j
public class H2StorageProvider extends ModuleProvider {

    private H2StorageConfig config;
    private JDBCHikariCPClient h2Client;

    public H2StorageProvider() {
        config = new H2StorageConfig();
    }

    @Override
    public String name() {
        return "h2";
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
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        this.registerServiceImplementation(StorageBuilderFactory.class, new StorageBuilderFactory.Default());

        Properties settings = new Properties();
        settings.setProperty("dataSourceClassName", config.getDriver());
        settings.setProperty("dataSource.url", config.getUrl());
        settings.setProperty("dataSource.user", config.getUser());
        settings.setProperty("dataSource.password", config.getPassword());
        h2Client = new JDBCHikariCPClient(settings);

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(h2Client));
        this.registerServiceImplementation(
            StorageDAO.class,
            new H2StorageDAO(
                getManager(), h2Client, config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag())
        );

        this.registerServiceImplementation(
            INetworkAddressAliasDAO.class, new H2NetworkAddressAliasDAO(h2Client));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new H2TopologyQueryDAO(h2Client));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new H2MetricsQueryDAO(h2Client));
        this.registerServiceImplementation(
            ITraceQueryDAO.class, new H2TraceQueryDAO(
                getManager(),
                h2Client,
                config.getMaxSizeOfArrayColumn(),
                config.getNumOfSearchableValuesPerTag()
            ));
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new H2BrowserLogQueryDAO(h2Client));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class, new H2MetadataQueryDAO(h2Client, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new H2AggregationQueryDAO(h2Client));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new H2AlarmQueryDAO(
                h2Client,
                getManager(),
                config.getMaxSizeOfArrayColumn(),
                config.getNumOfSearchableValuesPerTag()
        ));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new H2HistoryDeleteDAO(h2Client));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new H2TopNRecordsQueryDAO(h2Client));
        this.registerServiceImplementation(
            ILogQueryDAO.class,
            new H2LogQueryDAO(
                h2Client,
                getManager(),
                config.getMaxSizeOfArrayColumn(),
                config.getNumOfSearchableValuesPerTag()
            )
        );

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new H2ProfileTaskQueryDAO(h2Client));
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new H2ProfileTaskLogQueryDAO(h2Client));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new H2ProfileThreadSnapshotQueryDAO(h2Client));
        this.registerServiceImplementation(UITemplateManagementDAO.class, new H2UITemplateManagementDAO(h2Client));

        this.registerServiceImplementation(IEventQueryDAO.class, new H2EventQueryDAO(h2Client));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        final ConfigService configService = getManager().find(CoreModule.NAME)
                                                        .provider()
                                                        .getService(ConfigService.class);
        final int numOfSearchableTracesTags = configService.getSearchableTracesTags().split(Const.COMMA).length;
        if (numOfSearchableTracesTags * config.getNumOfSearchableValuesPerTag() > config.getMaxSizeOfArrayColumn()) {
            throw new ModuleStartException("Size of searchableTracesTags[" + numOfSearchableTracesTags
                                               + "] * numOfSearchableValuesPerTag[" + config.getNumOfSearchableValuesPerTag()
                                               + "] > maxSizeOfArrayColumn[" + config.getMaxSizeOfArrayColumn()
                                               + "]. Potential out of bound in the runtime.");
        }
        final int numOfSearchableLogsTags = configService.getSearchableLogsTags().split(Const.COMMA).length;
        if (numOfSearchableLogsTags * config.getNumOfSearchableValuesPerTag() > config.getMaxSizeOfArrayColumn()) {
            throw new ModuleStartException("Size of searchableLogsTags[" + numOfSearchableLogsTags
                                               + "] * numOfSearchableValuesPerTag[" + config.getNumOfSearchableValuesPerTag()
                                               + "] > maxSizeOfArrayColumn[" + config.getMaxSizeOfArrayColumn()
                                               + "]. Potential out of bound in the runtime.");
        }

        MetricsCreator metricCreator = getManager().find(TelemetryModule.NAME)
                                                   .provider()
                                                   .getService(MetricsCreator.class);
        HealthCheckMetrics healthChecker = metricCreator.createHealthCheckerGauge(
            "storage_h2", MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
        h2Client.registerChecker(healthChecker);
        try {
            h2Client.connect();

            H2TableInstaller installer = new H2TableInstaller(
                h2Client, getManager(), config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag());
            getManager().find(CoreModule.NAME).provider().getService(ModelCreator.class).addModelListener(installer);
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
