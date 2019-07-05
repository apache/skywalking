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
import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.slf4j.*;

/**
 * @author wusheng
 */
public class H2RegisterDAO extends H2SQLExecutor implements IRegisterDAO {

    private static final Logger logger = LoggerFactory.getLogger(H2RegisterDAO.class);

    private final JDBCHikariCPClient h2Client;
    private final StorageBuilder<RegisterSource> storageBuilder;

    public H2RegisterDAO(JDBCHikariCPClient h2Client, StorageBuilder<RegisterSource> storageBuilder) {
        this.h2Client = h2Client;
        this.storageBuilder = storageBuilder;
    }

    @Override public RegisterSource get(String modelName, String id) throws IOException {
        return (RegisterSource)getByID(h2Client, modelName, id, storageBuilder);
    }

    @Override
    public Map<String, RegisterSource> batchGet(String modelName, String... ids) throws IOException {
        Map<String, RegisterSource> map = new HashMap<>(ids.length);
        for (String id : ids) {
            RegisterSource registerSource = this.get(modelName, id);
            if (registerSource != null) {
                map.put(id, registerSource);
            }
        }
        return map;
    }

    @Override public void forceInsert(String modelName, RegisterSource source) throws IOException {
        try (Connection connection = h2Client.getConnection()) {
            getInsertExecutor(modelName, source, storageBuilder).invoke(connection);
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override public void forceUpdate(String modelName, RegisterSource source) throws IOException {
        try (Connection connection = h2Client.getConnection()) {
            getUpdateExecutor(modelName, source, storageBuilder).invoke(connection);
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
    @Override public SQLExecutor prepareBatchInsert(String modelName, RegisterSource source) throws IOException {
        return getInsertExecutor(modelName, source, storageBuilder);
    }

    @Override public SQLExecutor prepareBatchUpdate(String modelName, RegisterSource source) throws IOException {
        return getUpdateExecutor(modelName, source, storageBuilder);
    }
}
