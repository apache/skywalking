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

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import lombok.RequiredArgsConstructor;

/**
 * Synchronize storage H2 implements
 */
@RequiredArgsConstructor
public class JDBCManagementDAO extends JDBCSQLExecutor implements IManagementDAO {
    private final JDBCHikariCPClient jdbcClient;
    private final StorageBuilder<ManagementData> storageBuilder;

    @Override
    public void insert(Model model, ManagementData storageData) throws IOException {
        try (Connection connection = jdbcClient.getConnection()) {
            final StorageData data = getByID(jdbcClient, model.getName(), storageData.id().build(), storageBuilder);
            if (data != null) {
                return;
            }

            SQLExecutor insertExecutor = getInsertExecutor(model.getName(), storageData, storageBuilder,
                                                           new HashMapConverter.ToStorage(), null);
            insertExecutor.invoke(connection);
        } catch (IOException | SQLException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
