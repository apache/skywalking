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
import java.sql.SQLException;
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;

/**
 * Synchronize storage H2 implements
 */
public class H2ManagementDAO extends H2SQLExecutor implements IManagementDAO {

    private JDBCHikariCPClient h2Client;
    private StorageBuilder<ManagementData> storageBuilder;

    public H2ManagementDAO(JDBCHikariCPClient h2Client, StorageBuilder<ManagementData> storageBuilder) {
        this.h2Client = h2Client;
        this.storageBuilder = storageBuilder;
    }

    @Override
    public void insert(Model model, ManagementData storageData) throws IOException {
        try (Connection connection = h2Client.getConnection()) {
            final StorageData data = getByID(h2Client, model.getName(), storageData.id(), storageBuilder);
            if (data != null) {
                return;
            }

            SQLExecutor insertExecutor = getInsertExecutor(model.getName(), storageData, storageBuilder);
            insertExecutor.invoke(connection);
        } catch (IOException | SQLException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
