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
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class H2ServiceInventoryCacheDAO extends H2SQLExecutor implements IServiceInventoryCacheDAO {
    private static final Logger logger = LoggerFactory.getLogger(H2ServiceInventoryCacheDAO.class);
    private JDBCHikariCPClient h2Client;

    public H2ServiceInventoryCacheDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override public int getServiceId(String serviceName) {
        String id = ServiceInventory.buildId(serviceName);
        return getEntityIDByID(h2Client, ServiceInventory.SEQUENCE, ServiceInventory.INDEX_NAME, id);
    }

    @Override public int getServiceId(int addressId) {
        String id = ServiceInventory.buildId(addressId);
        return getEntityIDByID(h2Client, ServiceInventory.SEQUENCE, ServiceInventory.INDEX_NAME, id);
    }

    @Override public ServiceInventory get(int serviceId) {
        try {
            return (ServiceInventory)getByColumn(h2Client, ServiceInventory.INDEX_NAME, ServiceInventory.SEQUENCE, serviceId, new ServiceInventory.Builder());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    @Override public List<ServiceInventory> loadLastUpdate(long lastUpdateTime) {
        List<ServiceInventory> serviceInventories = new ArrayList<>();

        try {
            StringBuilder sql = new StringBuilder("select * from ");
            sql.append(ServiceInventory.INDEX_NAME);
            sql.append(" where ").append(ServiceInventory.IS_ADDRESS).append("=? ");
            sql.append(" and ").append(ServiceInventory.LAST_UPDATE_TIME).append(">?");

            try (Connection connection = h2Client.getConnection()) {
                try (ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), BooleanUtils.TRUE, lastUpdateTime)) {
                    ServiceInventory serviceInventory;
                    do {
                        serviceInventory = (ServiceInventory)toStorageData(resultSet, ServiceInventory.INDEX_NAME, new ServiceInventory.Builder());
                        if (serviceInventory != null) {
                            serviceInventories.add(serviceInventory);
                        }
                    }
                    while (serviceInventory != null);
                }
            } catch (SQLException e) {
                throw new IOException(e);
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        return serviceInventories;
    }
}
