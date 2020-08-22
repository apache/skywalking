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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class H2NetworkAddressAliasDAO extends H2SQLExecutor implements INetworkAddressAliasDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(H2NetworkAddressAliasDAO.class);
    private JDBCHikariCPClient h2Client;

    public H2NetworkAddressAliasDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public List<NetworkAddressAlias> loadLastUpdate(long lastUpdateTime) {
        List<NetworkAddressAlias> networkAddressAliases = new ArrayList<>();

        try {
            StringBuilder sql = new StringBuilder("select * from ");
            sql.append(NetworkAddressAlias.INDEX_NAME);
            sql.append(" where ").append(NetworkAddressAlias.LAST_UPDATE_TIME_BUCKET).append(">?");

            try (Connection connection = h2Client.getConnection()) {
                try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), lastUpdateTime)) {
                    NetworkAddressAlias networkAddressAlias;
                    do {
                        networkAddressAlias = (NetworkAddressAlias) toStorageData(
                            resultSet, NetworkAddressAlias.INDEX_NAME, new NetworkAddressAlias.Builder());
                        if (networkAddressAlias != null) {
                            networkAddressAliases.add(networkAddressAlias);
                        }
                    }
                    while (networkAddressAlias != null);
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
        return networkAddressAliases;
    }
}
