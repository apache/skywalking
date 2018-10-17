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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wusheng
 */
public class H2BatchDAO implements IBatchDAO {
    private static final Logger logger = LoggerFactory.getLogger(H2BatchDAO.class);

    private JDBCHikariCPClient h2Client;

    public H2BatchDAO(JDBCHikariCPClient h2Client) {
        this.h2Client = h2Client;
    }

    @Override public void batchPersistence(List<?> batchCollection) {
        if (batchCollection.size() == 0) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("batch sql statements execute, data size: {}", batchCollection.size());
        }

        try (Connection connection = h2Client.getConnection()) {
            for (Object exe : batchCollection) {
                SQLExecutor sqlExecutor = (SQLExecutor)exe;
                sqlExecutor.invoke(connection);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        } catch (JDBCClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
