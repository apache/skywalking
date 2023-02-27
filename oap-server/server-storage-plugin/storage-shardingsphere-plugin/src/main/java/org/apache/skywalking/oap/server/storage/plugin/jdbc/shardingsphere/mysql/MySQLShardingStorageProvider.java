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

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCStorageConfig;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLStorageProvider;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.DurationWithinTTL;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.ShardingRulesOperator;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.ShardingSphereTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingHistoryDeleteDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingLogQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingTopologyQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere.dao.ShardingZipkinQueryDAO;

import java.io.IOException;
import java.sql.SQLException;

public class MySQLShardingStorageProvider extends MySQLStorageProvider {
    @Override
    public String name() {
        return "mysql-sharding";
    }

    @Override
    protected ModelInstaller createModelInstaller() {
        return new ShardingSphereTableInstaller(
            jdbcClient,
            getManager(),
            ((MySQLShardingStorageConfig) config).getDataSources());
    }

    @Override
    public ConfigCreator<? extends JDBCStorageConfig> newConfigCreator() {
        return new ConfigCreator<MySQLShardingStorageConfig>() {
            @Override
            public Class<MySQLShardingStorageConfig> type() {
                return MySQLShardingStorageConfig.class;
            }

            @Override
            public void onInitialized(final MySQLShardingStorageConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        super.prepare();

        // Override service implementations.

        this.registerServiceImplementation(
            ITopologyQueryDAO.class,
            new ShardingTopologyQueryDAO(jdbcClient));
        this.registerServiceImplementation(
            IMetricsQueryDAO.class,
            new ShardingMetricsQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            ITraceQueryDAO.class,
            new ShardingTraceQueryDAO(getManager(), jdbcClient));
        this.registerServiceImplementation(
            IBrowserLogQueryDAO.class,
            new ShardingBrowserLogQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IAggregationQueryDAO.class,
            new ShardingAggregationQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            ILogQueryDAO.class,
            new ShardingLogQueryDAO(jdbcClient, getManager(), tableHelper));

        this.registerServiceImplementation(
            IHistoryDeleteDAO.class,
            new ShardingHistoryDeleteDAO(
                jdbcClient,
                ((MySQLShardingStorageConfig) config).getDataSources(),
                getManager(),
                modelInstaller,
                tableHelper));

        this.registerServiceImplementation(
            IZipkinQueryDAO.class,
            new ShardingZipkinQueryDAO(jdbcClient));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        try {
            super.start();

            ShardingRulesOperator.INSTANCE.start(jdbcClient);

            DurationWithinTTL.INSTANCE.setConfigService(
                getManager()
                    .find(CoreModule.NAME)
                    .provider()
                    .getService(ConfigService.class));
        } catch (StorageException | SQLException | IOException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }
}
