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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller;

import java.sql.ResultSet;
import java.util.List;
import java.util.Set;

/**
 * ShardingSphere table installer delegates all the works to
 * the table installer (e.g. H2TableInstaller, MySQLTableInstaller)
 * of the backed storage (e.g. H2, MySQL), and then add sharding rules.
 */
public class ShardingSphereTableInstaller extends H2TableInstaller {
    private final H2TableInstaller delegatee;
    private final Set<String> dataSources;
    private final ModuleManager moduleManager;

    public ShardingSphereTableInstaller(Client client,
                                        ModuleManager moduleManager,
                                        Set<String> dataSources,
                                        H2TableInstaller delegatee) {
        super(client, moduleManager);
        this.dataSources = dataSources;
        this.delegatee = delegatee;
        this.moduleManager = moduleManager;
    }

    @Override
    public boolean isExists(Model model) throws StorageException {
        boolean isRuleExecuted = false;
        boolean isTableExist = isTableExists(model);
        JDBCHikariCPClient jdbcClient = (JDBCHikariCPClient) client;
        ConfigService configService = moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class);
        int ttl = model.isRecord() ? configService.getRecordDataTTL() : configService.getMetricsDataTTL();
        if (model.getSqlDBModelExtension().isShardingTable()) {
            isRuleExecuted = ShardingRulesOperator.INSTANCE.createOrUpdateShardingRule(jdbcClient, model, this.dataSources, ttl);
        }
        return isTableExist && !isRuleExecuted;
    }

    @SneakyThrows
    private boolean isTableExists(Model model) {
        TableMetaInfo.addModel(model);
        final var jdbcClient = (JDBCHikariCPClient) client;
        return jdbcClient.executeQuery(String.format("SHOW LOGICAL TABLES LIKE '%s'", model.getName()), ResultSet::next);
    }

    @Override
    public void start() {
        delegatee.start();
    }

    @Override
    public void createOrUpdateTable(
        String table,
        List<ModelColumn> columns,
        boolean isAdditionalTable) {
        delegatee.createOrUpdateTable(table, columns, isAdditionalTable);
    }

    @Override
    public void createOrUpdateTableIndexes(
        String tableName,
        List<ModelColumn> columns,
        boolean isAdditionalTable) throws JDBCClientException {
        delegatee.createOrUpdateTableIndexes(tableName, columns, isAdditionalTable);
    }

    @Override
    public String getColumnDefinition(ModelColumn column) {
        return delegatee.getColumnDefinition(column);
    }
}
