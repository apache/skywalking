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
import java.sql.*;
import java.util.*;
import org.apache.skywalking.oap.server.core.register.*;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class H2NetworkAddressInventoryCacheDAO extends H2SQLExecutor implements INetworkAddressInventoryCacheDAO {
    private static final Logger logger = LoggerFactory.getLogger(H2NetworkAddressInventoryCacheDAO.class);
    private JDBCHikariCPClient h2Client;

    public H2NetworkAddressInventoryCacheDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override public int getAddressId(String networkAddress) {
        String id = NetworkAddressInventory.buildId(networkAddress);
        return getEntityIDByID(h2Client, NetworkAddressInventory.SEQUENCE, NetworkAddressInventory.INDEX_NAME, id);
    }

    @Override public NetworkAddressInventory get(int addressId) {
        try {
            return (NetworkAddressInventory)getByColumn(h2Client, NetworkAddressInventory.INDEX_NAME, NetworkAddressInventory.SEQUENCE, addressId, new NetworkAddressInventory.Builder());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    @Override public List<NetworkAddressInventory> loadLastUpdate(long lastUpdateTime) {
        List<NetworkAddressInventory> addressInventories = new ArrayList<>();

        try {
            StringBuilder sql = new StringBuilder("select * from ");
            sql.append(NetworkAddressInventory.INDEX_NAME);
            sql.append(" where ").append(NetworkAddressInventory.LAST_UPDATE_TIME).append(">?");

            try (Connection connection = h2Client.getConnection()) {
                try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), lastUpdateTime)) {
                    NetworkAddressInventory addressInventory;
                    do {
                        addressInventory = (NetworkAddressInventory)toStorageData(resultSet, NetworkAddressInventory.INDEX_NAME, new ServiceInventory.Builder());
                        if (addressInventory != null) {
                            addressInventories.add(addressInventory);
                        }
                    }
                    while (addressInventory != null);
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        return addressInventories;
    }
}
