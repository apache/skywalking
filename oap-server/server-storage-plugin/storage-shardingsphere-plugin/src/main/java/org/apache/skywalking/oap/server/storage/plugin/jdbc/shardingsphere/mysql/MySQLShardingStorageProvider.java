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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.mysql;

import java.io.IOException;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
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
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2BatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2EBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2EBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2EBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2EventQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2HistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2MetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2NetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ServiceLabelQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2ProfileThreadSnapshotQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2StorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2UITemplateManagementDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.DurationWithinTTL;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingZipkinQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.mysql.dao.MySQLShardingLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.mysql.dao.MySQLShardingTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.mysql.dao.MysqlShardingBrowserLogQueryDAO;

@Slf4j
public class MySQLShardingStorageProvider extends ModuleProvider {

    private MySQLShardingStorageConfig config;
    private JDBCHikariCPClient mysqlClient;

    public MySQLShardingStorageProvider() {
        config = new MySQLShardingStorageConfig();
    }

    @Override
    public String name() {
        return "mysql-sharding";
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
        
        mysqlClient = new JDBCHikariCPClient(config.getProperties());

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(mysqlClient, config.getMaxSizeOfBatchSql(), config.getAsyncBatchPersistentPoolSize()));
        this.registerServiceImplementation(
            StorageDAO.class,
            new H2StorageDAO(mysqlClient));
        this.registerServiceImplementation(
            INetworkAddressAliasDAO.class, new H2NetworkAddressAliasDAO(mysqlClient));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new ShardingTopologyQueryDAO(mysqlClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new ShardingMetricsQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            ITraceQueryDAO.class,
            new MySQLShardingTraceQueryDAO(getManager(), mysqlClient)
        );
        this.registerServiceImplementation(IBrowserLogQueryDAO.class, new MysqlShardingBrowserLogQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            IMetadataQueryDAO.class, new H2MetadataQueryDAO(mysqlClient, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new ShardingAggregationQueryDAO(mysqlClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new MySQLAlarmQueryDAO(mysqlClient, getManager()));
        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new H2HistoryDeleteDAO(mysqlClient));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new H2TopNRecordsQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            ILogQueryDAO.class,
            new MySQLShardingLogQueryDAO(mysqlClient, getManager()));

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new H2ProfileTaskQueryDAO(mysqlClient));
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new H2ProfileTaskLogQueryDAO(mysqlClient));
        this.registerServiceImplementation(
            IProfileThreadSnapshotQueryDAO.class, new H2ProfileThreadSnapshotQueryDAO(mysqlClient));
        this.registerServiceImplementation(UITemplateManagementDAO.class, new H2UITemplateManagementDAO(mysqlClient));

        this.registerServiceImplementation(
            IHistoryDeleteDAO.class, new MySQLShardingHistoryDeleteDAO(mysqlClient, config, getManager()));

        this.registerServiceImplementation(IEventQueryDAO.class, new H2EventQueryDAO(mysqlClient));

        this.registerServiceImplementation(IEBPFProfilingTaskDAO.class, new H2EBPFProfilingTaskDAO(mysqlClient));
        this.registerServiceImplementation(IEBPFProfilingScheduleDAO.class, new H2EBPFProfilingScheduleDAO(mysqlClient));
        this.registerServiceImplementation(IEBPFProfilingDataDAO.class, new H2EBPFProfilingDataDAO(mysqlClient));
        this.registerServiceImplementation(IServiceLabelDAO.class, new H2ServiceLabelQueryDAO(mysqlClient));
        this.registerServiceImplementation(ITagAutoCompleteQueryDAO.class, new H2TagAutoCompleteQueryDAO(mysqlClient));
        this.registerServiceImplementation(IZipkinQueryDAO.class, new ShardingZipkinQueryDAO(mysqlClient));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        try {
            mysqlClient.connect();
            MySQLShardingTableInstaller installer = new MySQLShardingTableInstaller(mysqlClient, getManager(), config);
            ShardingRulesOperator.INSTANCE.start(mysqlClient);
            getManager().find(CoreModule.NAME).provider().getService(ModelCreator.class).addModelListener(installer);
            DurationWithinTTL.INSTANCE.setConfigService(getManager().find(CoreModule.NAME)
                                                                    .provider()
                                                                    .getService(ConfigService.class));
        } catch (StorageException | SQLException | IOException e) {
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
