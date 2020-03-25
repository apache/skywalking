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
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.storage.cache.IEndpointInventoryCacheDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class H2EndpointInventoryCacheDAO extends H2SQLExecutor implements IEndpointInventoryCacheDAO {
    private static final Logger logger = LoggerFactory.getLogger(H2EndpointInventoryCacheDAO.class);
    private JDBCHikariCPClient h2Client;

    public H2EndpointInventoryCacheDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override
    public int getEndpointId(int serviceId, String endpointName, int detectPoint) {
        String id = EndpointTraffic.buildId(serviceId, endpointName, detectPoint);
        return getEntityIDByID(h2Client, EndpointTraffic.SEQUENCE, EndpointTraffic.INDEX_NAME, id);
    }

    @Override
    public EndpointTraffic get(int endpointId) {
        try {
            return (EndpointTraffic) getByColumn(h2Client, EndpointTraffic.INDEX_NAME, EndpointTraffic.SEQUENCE, endpointId, new EndpointTraffic.Builder());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }
}
