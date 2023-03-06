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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JDBCNetworkAddressAliasDAO extends JDBCSQLExecutor implements INetworkAddressAliasDAO {
    private final JDBCClient jdbcClient;
    private final TableHelper tableHelper;

    public JDBCNetworkAddressAliasDAO(JDBCClient jdbcClient, ModuleManager moduleManager) {
        this.jdbcClient = jdbcClient;
        this.tableHelper = new TableHelper(moduleManager, jdbcClient);
    }

    @Override
    @SneakyThrows
    public List<NetworkAddressAlias> loadLastUpdate(long lastUpdateTime) {
        final var tables = tableHelper.getTablesForRead(NetworkAddressAlias.INDEX_NAME);
        final var results = new ArrayList<NetworkAddressAlias>();

        for (final var table : tables) {
            final var sql = new StringBuilder()
                .append("select * from ").append(table)
                .append(" where ")
                .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ")
                .append(" and ").append(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET).append(">?");


            results.addAll(
                jdbcClient.executeQuery(
                    sql.toString(),
                    resultSet -> {
                        List<NetworkAddressAlias> networkAddressAliases = new ArrayList<>();
                        NetworkAddressAlias networkAddressAlias;
                        do {
                            networkAddressAlias = (NetworkAddressAlias) toStorageData(
                                resultSet, NetworkAddressAlias.INDEX_NAME, new NetworkAddressAlias.Builder());
                            if (networkAddressAlias != null) {
                                networkAddressAliases.add(networkAddressAlias);
                            }
                        }
                        while (networkAddressAlias != null);
                        return networkAddressAliases;
                    },
                    NetworkAddressAlias.INDEX_NAME, lastUpdateTime)
            );
        }
        return results;
    }
}
