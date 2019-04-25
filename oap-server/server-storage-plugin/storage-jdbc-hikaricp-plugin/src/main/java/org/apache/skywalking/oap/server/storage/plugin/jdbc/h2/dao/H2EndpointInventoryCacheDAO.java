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
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class H2EndpointInventoryCacheDAO extends H2SQLExecutor implements IEndpointInventoryCacheDAO {
    private static final Logger logger = LoggerFactory.getLogger(H2EndpointInventoryCacheDAO.class);
    private JDBCHikariCPClient h2Client;

    public H2EndpointInventoryCacheDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override public int getEndpointId(int serviceId, String endpointName, int detectPoint) {
        String id = EndpointInventory.buildId(serviceId, endpointName, detectPoint);
        return getEntityIDByID(h2Client, EndpointInventory.SEQUENCE, EndpointInventory.MODEL_NAME, id);
    }

    @Override public EndpointInventory get(int endpointId) {
        try {
            return (EndpointInventory)getByColumn(h2Client, EndpointInventory.MODEL_NAME, EndpointInventory.SEQUENCE, endpointId, new EndpointInventory.Builder());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }
}
