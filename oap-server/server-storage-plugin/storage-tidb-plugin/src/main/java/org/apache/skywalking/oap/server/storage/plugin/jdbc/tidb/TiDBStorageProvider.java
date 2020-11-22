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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.tidb;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
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
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2HistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2NetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2StorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2UITemplateManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MysqlBrowserLogQueryDAO;

/**
 * TiDB storage enhanced and came from MySQLStorageProvider to support TiDB.
 *
 * caution: need add "useAffectedRows=true" to jdbc url.
 */
@Slf4j
public class TiDBStorageProvider extends ModuleProvider {

    private TiDBStorageConfig config;
    private JDBCHikariCPClient mysqlClient;

    public TiDBStorageProvider() {
        config = new TiDBStorageConfig();
    }

    @Override
    public String name() {
        return "tidb";
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
        mysqlClient = new JDBCHikariCPClient(config.getProperties());

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(mysqlClient));
        this.registerServiceImplementation(
            StorageDAO.class,
            new H2StorageDAO(
                getManager(), mysqlClient, config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag())
        );
        this.registerServiceImplementation(
            INetworkAddressAliasDAO.class, new H2NetworkAddressAliasDAO(mysqlClient));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new H2TopologyQueryDAO(mysqlClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new H2MetricsQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            ITraceQueryDAO.class,
            new MySQLTraceQueryDAO(
                getManager(),
                mysqlClient,
                config.getMaxSizeOfArrayColumn(),
                config.getNumOfSearchableValuesPerTag()
            )
        );
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new MysqlBrowserLogQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class, new H2MetadataQueryDAO(mysqlClient, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new MySQLAggregationQueryDAO(mysqlClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new MySQLAlarmQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new H2HistoryDeleteDAO(mysqlClient));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new H2TopNRecordsQueryDAO(mysqlClient));
        this.registerServiceImplementation(ILogQueryDAO.class, new MySQLLogQueryDAO(mysqlClient));

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new H2ProfileTaskQueryDAO(mysqlClient));
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new H2ProfileTaskLogQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new H2ProfileThreadSnapshotQueryDAO(mysqlClient));
        this.registerServiceImplementation(UITemplateManagementDAO.class, new H2UITemplateManagementDAO(mysqlClient));

        this.registerServiceImplementation(IHistoryDeleteDAO.class, new TiDBHistoryDeleteDAO(mysqlClient));
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

        try {
            mysqlClient.connect();

            MySQLTableInstaller installer = new MySQLTableInstaller(
                mysqlClient, getManager(), config.getMaxSizeOfArrayColumn(), config.getNumOfSearchableValuesPerTag()
            );
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
