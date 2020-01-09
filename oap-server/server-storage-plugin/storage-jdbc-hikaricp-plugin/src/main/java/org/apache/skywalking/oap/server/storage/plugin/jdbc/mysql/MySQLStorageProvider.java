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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.IRegisterLockDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.ttl.GeneralStorageTTL;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MySQL storage provider should be secondary choice for production usage as SkyWalking storage solution. It enhanced
 * and came from H2StorageProvider, but consider more in using in production.
 *
 * Because this module is not really related to MySQL, instead, it is based on MySQL SQL style with JDBC, so, by having
 * this storage implementation, we could also use this in MySQL-compatible projects, such as, Apache ShardingSphere,
 * TiDB
 *
 * @author wusheng, peng-yongsheng, panjuan
 */
public class MySQLStorageProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(MySQLStorageProvider.class);

    private MySQLStorageConfig config;
    private JDBCHikariCPClient mysqlClient;
    private H2RegisterLockDAO lockDAO;

    public MySQLStorageProvider() {
        config = new MySQLStorageConfig();
    }

    @Override public String name() {
        return "mysql";
    }

    @Override public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        mysqlClient = new JDBCHikariCPClient(config.getProperties());

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(mysqlClient));
        this.registerServiceImplementation(StorageDAO.class, new H2StorageDAO(mysqlClient));
        lockDAO = new H2RegisterLockDAO(mysqlClient);
        this.registerServiceImplementation(IRegisterLockDAO.class, lockDAO);

        this.registerServiceImplementation(IServiceInventoryCacheDAO.class, new H2ServiceInventoryCacheDAO(mysqlClient));
        this.registerServiceImplementation(IServiceInstanceInventoryCacheDAO.class, new H2ServiceInstanceInventoryCacheDAO(mysqlClient));
        this.registerServiceImplementation(IEndpointInventoryCacheDAO.class, new H2EndpointInventoryCacheDAO(mysqlClient));
        this.registerServiceImplementation(INetworkAddressInventoryCacheDAO.class, new H2NetworkAddressInventoryCacheDAO(mysqlClient));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new H2TopologyQueryDAO(mysqlClient));
        this.registerServiceImplementation(IMetricsQueryDAO.class, new H2MetricsQueryDAO(mysqlClient));
        this.registerServiceImplementation(ITraceQueryDAO.class, new MySQLTraceQueryDAO(mysqlClient));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new H2MetadataQueryDAO(mysqlClient, config.getMetadataQueryMaxSize()));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new MySQLAggregationQueryDAO(mysqlClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new MySQLAlarmQueryDAO(mysqlClient));
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new H2HistoryDeleteDAO(getManager(), mysqlClient, new GeneralStorageTTL()));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new H2TopNRecordsQueryDAO(mysqlClient));
        this.registerServiceImplementation(ILogQueryDAO.class, new MySQLLogQueryDAO(mysqlClient));

        this.registerServiceImplementation(IProfileTaskQueryDAO.class, new H2ProfileTaskQueryDAO(mysqlClient));
        this.registerServiceImplementation(IProfileTaskLogQueryDAO.class, new H2ProfileTaskLogQueryDAO(mysqlClient));
    }
    
    @Override public void start() throws ServiceNotProvidedException, ModuleStartException {
        try {
            mysqlClient.connect();

            MySQLTableInstaller installer = new MySQLTableInstaller(getManager());
            installer.install(mysqlClient);

            new H2RegisterLockInstaller().install(mysqlClient, lockDAO);
        } catch (StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
