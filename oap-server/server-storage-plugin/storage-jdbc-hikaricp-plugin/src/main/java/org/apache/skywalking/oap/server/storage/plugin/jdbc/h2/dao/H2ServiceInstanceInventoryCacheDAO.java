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
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.storage.cache.IServiceInstanceInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wusheng
 */
public class H2ServiceInstanceInventoryCacheDAO extends H2SQLExecutor implements IServiceInstanceInventoryCacheDAO {
    private static final Logger logger = LoggerFactory.getLogger(H2ServiceInstanceInventoryCacheDAO.class);
    private JDBCHikariCPClient h2Client;

    public H2ServiceInstanceInventoryCacheDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override public ServiceInstanceInventory get(int serviceInstanceId) {
        try {
            return (ServiceInstanceInventory)getByColumn(h2Client, ServiceInstanceInventory.MODEL_NAME, ServiceInstanceInventory.SEQUENCE, serviceInstanceId, new ServiceInstanceInventory.Builder());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    @Override public int getServiceInstanceId(int serviceId, String uuid) {
        String id = ServiceInstanceInventory.buildId(serviceId, uuid);
        return getByID(id);
    }

    @Override public int getServiceInstanceId(int serviceId, int addressId) {
        String id = ServiceInstanceInventory.buildId(serviceId, addressId);
        return getByID(id);
    }

    private int getByID(String id) {
        return getEntityIDByID(h2Client, ServiceInstanceInventory.SEQUENCE, ServiceInstanceInventory.MODEL_NAME, id);
    }
}
