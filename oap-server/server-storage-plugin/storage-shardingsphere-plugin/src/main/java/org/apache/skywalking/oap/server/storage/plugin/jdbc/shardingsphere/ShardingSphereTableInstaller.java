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
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql.MySQLTableInstaller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ShardingSphere table installer delegates all the works to
 * the table installer (e.g. H2TableInstaller, MySQLTableInstaller)
 * of the backed storage (e.g. H2, MySQL), and then add sharding rules.
 */
public class ShardingSphereTableInstaller extends MySQLTableInstaller {
    private final Set<String> dataSources;
    private final ModuleManager moduleManager;

    public ShardingSphereTableInstaller(Client client,
                                        ModuleManager moduleManager,
                                        Set<String> dataSources) {
        super(client, moduleManager);
        this.dataSources = dataSources;
        this.moduleManager = moduleManager;
    }

    @Override
    @SneakyThrows
    public boolean isExists(Model model) {
        TableMetaInfo.addModel(model);

        boolean isRuleExecuted = false;
        boolean isTableExist = super.isExists(model) && this.isTableExists(model);
        JDBCClient jdbcClient = (JDBCClient) client;
        ConfigService configService = moduleManager.find(CoreModule.NAME).provider().getService(ConfigService.class);
        int ttl = model.isRecord() ? configService.getRecordDataTTL() : configService.getMetricsDataTTL();
        final var tableName = TableHelper.getTableForWrite(model);
        if (model.getSqlDBModelExtension().isShardingTable()) {
            isRuleExecuted = ShardingRulesOperator.INSTANCE.createOrUpdateShardingRule(jdbcClient, model, tableName, this.dataSources, ttl);
        }
        return isTableExist && !isRuleExecuted;
    }

    @SneakyThrows
    private boolean isTableExists(Model model) {
        final var jdbcClient = (JDBCClient) client;
        return jdbcClient.executeQuery(String.format("SHOW LOGICAL TABLES LIKE '%s'", model.getName()), ResultSet::next);
    }

    @Override
    protected Set<String> getDatabaseColumns(String table) throws SQLException {
        final var jdbcClient = (JDBCClient) client;
        if (!isTableExisted(table)) {
            return Collections.emptySet();
        }
        return jdbcClient.executeQuery("show columns from " + table, resultSet -> {
            final var columns = new HashSet<String>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("Field"));
            }
            return columns;
        });
    }

    @Override
    protected boolean isTableExisted(String table) throws SQLException {
        final var jdbcClient = (JDBCClient) client;
        return jdbcClient.executeQuery("show tables", resultSet -> {
            while (resultSet.next()) {
                if (table.equals(resultSet.getString(1))) {
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    protected boolean isIndexExisted(String table, String index) throws SQLException {
        final var jdbcClient = (JDBCClient) client;
        return jdbcClient.executeQuery("show index from " + table, resultSet -> {
            while (resultSet.next()) {
                if (index.equals(resultSet.getString("Key_name"))) {
                    return true;
                }
            }
            return false;
        });
    }
}
