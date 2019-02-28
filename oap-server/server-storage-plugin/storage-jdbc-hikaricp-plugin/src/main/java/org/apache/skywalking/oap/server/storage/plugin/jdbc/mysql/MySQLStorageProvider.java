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

import java.io.IOException;
import java.util.Properties;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.cache.*;
import org.apache.skywalking.oap.server.core.storage.query.*;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.*;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.*;
import org.slf4j.*;

/**
 * MySQL storage provider should be secondary choice for production usage as SkyWalking storage solution. It enhanced
 * and came from H2StorageProvider, but consider more in using in production.
 *
 * Because this module is not really related to MySQL, instead, it is based on MySQL SQL style with JDBC, so, by having
 * this storage implementation, we could also use this in MySQL-compatible projects, such as, Apache ShardingSphere,
 * TiDB
 *
 * @author wusheng, peng-yongsheng
 */
public class MySQLStorageProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(H2StorageProvider.class);

    private H2StorageConfig config;
    private JDBCHikariCPClient mysqlClient;
    private H2RegisterLockDAO lockDAO;

    public MySQLStorageProvider() {
        config = new H2StorageConfig();
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

    @Override public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        Properties settings = new Properties();
        try {
            settings.load(ResourceUtils.read("datasource-settings.properties"));
        } catch (IOException e) {
            throw new ModuleStartException("load datasource setting file failure.", e);
        }

        mysqlClient = new JDBCHikariCPClient(settings);

        this.registerServiceImplementation(IBatchDAO.class, new H2BatchDAO(mysqlClient));
        this.registerServiceImplementation(StorageDAO.class, new H2StorageDAO(mysqlClient));
        lockDAO = new H2RegisterLockDAO(mysqlClient);
        this.registerServiceImplementation(IRegisterLockDAO.class, lockDAO);

        this.registerServiceImplementation(IServiceInventoryCacheDAO.class, new H2ServiceInventoryCacheDAO(mysqlClient));
        this.registerServiceImplementation(IServiceInstanceInventoryCacheDAO.class, new H2ServiceInstanceInventoryCacheDAO(mysqlClient));
        this.registerServiceImplementation(IEndpointInventoryCacheDAO.class, new H2EndpointInventoryCacheDAO(mysqlClient));
        this.registerServiceImplementation(INetworkAddressInventoryCacheDAO.class, new H2NetworkAddressInventoryCacheDAO(mysqlClient));

        this.registerServiceImplementation(ITopologyQueryDAO.class, new H2TopologyQueryDAO(mysqlClient));
        this.registerServiceImplementation(IMetricQueryDAO.class, new H2MetricQueryDAO(mysqlClient));
        this.registerServiceImplementation(ITraceQueryDAO.class, new MySQLTraceQueryDAO(mysqlClient));
        this.registerServiceImplementation(IMetadataQueryDAO.class, new H2MetadataQueryDAO(mysqlClient));
        this.registerServiceImplementation(IAggregationQueryDAO.class, new MySQLAggregationQueryDAO(mysqlClient));
        this.registerServiceImplementation(IAlarmQueryDAO.class, new MySQLAlarmQueryDAO(mysqlClient));
        this.registerServiceImplementation(IHistoryDeleteDAO.class, new H2HistoryDeleteDAO(mysqlClient));
        this.registerServiceImplementation(ITopNRecordsQueryDAO.class, new H2TopNRecordsQueryDAO(mysqlClient));
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

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
