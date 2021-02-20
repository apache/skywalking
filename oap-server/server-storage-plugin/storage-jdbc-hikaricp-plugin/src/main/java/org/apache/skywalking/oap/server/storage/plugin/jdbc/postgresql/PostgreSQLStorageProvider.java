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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql;

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
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2BatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2EventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2HistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2NetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2StorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2UITemplateManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLTraceQueryDAO;

/**
 * PostgreSQL storage enhanced and came from MySQLStorageProvider to support PostgreSQL.
 */
@Slf4j
public class PostgreSQLStorageProvider extends ModuleProvider {
    private PostgreSQLStorageConfig config;
    private JDBCHikariCPClient postgresqlClient;

    public PostgreSQLStorageProvider() {
        config = new PostgreSQLStorageConfig();
    }

    @Override
    public String name() {
        return "postgresql";
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

        postgresqlClient = new JDBCHikariCPClient(config.getProperties());

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(postgresqlClient));
        this.registerServiceImplementation(
                StorageDAO.class,
                new H2StorageDAO(
                        getManager(), postgresqlClient, config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag())
        );
        this.registerServiceImplementation(
                INetworkAddressAliasDAO.class, new H2NetworkAddressAliasDAO(postgresqlClient));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new H2TopologyQueryDAO(postgresqlClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new PostgreSQLMetricsQueryDAO(postgresqlClient));
        this.registerServiceImplementation(
                ITraceQueryDAO.class,
                new PostgreSQLTraceQueryDAO(
                        getManager(),
                        postgresqlClient,
                        config.getMaxSizeOfArrayColumn(),
                        config.getNumOfSearchableValuesPerTag()
                )
        );
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new PostgreSQLBrowserLogQueryDAO(postgresqlClient));
        this.registerServiceImplementation(
                IMetadataQueryDAO.class, new H2MetadataQueryDAO(postgresqlClient, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new PostgreSQLAggregationQueryDAO(postgresqlClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new PostgreSQLAlarmQueryDAO(postgresqlClient));
        this.registerServiceImplementation(
                IHistoryDeleteDAO.class, new H2HistoryDeleteDAO(postgresqlClient));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new H2TopNRecordsQueryDAO(postgresqlClient));
        this.registerServiceImplementation(
                ILogQueryDAO.class,
                new PostgreSQLLogQueryDAO(
                        postgresqlClient,
                        getManager(),
                        config.getMaxSizeOfArrayColumn(),
                        config.getNumOfSearchableValuesPerTag()
                )
        );

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new H2ProfileTaskQueryDAO(postgresqlClient));
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new H2ProfileTaskLogQueryDAO(postgresqlClient));
        this.registerServiceImplementation(
                IProfileThreadSnapshotQueryDAO.class, new H2ProfileThreadSnapshotQueryDAO(postgresqlClient));
        this.registerServiceImplementation(UITemplateManagementDAO.class, new H2UITemplateManagementDAO(postgresqlClient));
        this.registerServiceImplementation(IEventQueryDAO.class, new H2EventQueryDAO(postgresqlClient));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        final ConfigService configService = getManager().find(CoreModule.NAME)
                .provider()
                .getService(ConfigService.class);
        final int numOfSearchableTags = configService.getSearchableTracesTags().split(Const.COMMA).length;
        if (numOfSearchableTags * config.getNumOfSearchableValuesPerTag() > config.getMaxSizeOfArrayColumn()) {
            throw new ModuleStartException("Size of searchableTracesTags[" + numOfSearchableTags
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

        try {
            postgresqlClient.connect();

            MySQLTableInstaller installer = new PostgreSQLTableInstaller(
                    postgresqlClient, getManager(), config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag()
            );
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
