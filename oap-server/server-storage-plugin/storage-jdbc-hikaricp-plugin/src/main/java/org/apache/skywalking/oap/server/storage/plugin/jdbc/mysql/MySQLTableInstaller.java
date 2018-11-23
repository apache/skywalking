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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import org.apache.skywalking.oap.server.core.analysis.indicator.IntKeyLongValueArray;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TableInstaller;

/**
 * Extend H2TableInstaller but match MySQL SQL syntax.
 *
 * @author wusheng
 */
public class MySQLTableInstaller extends H2TableInstaller {
    public MySQLTableInstaller(ModuleManager moduleManager) {
        super(moduleManager);
        /**
         * Override column because the default column names in core have syntax conflict with MySQL.
         */
        this.overrideColumnName("precision", "cal_precision");
        this.overrideColumnName("match", "match_num");
    }

    @Override protected void deleteTable(Client client, Model model) throws StorageException {
        JDBCHikariCPClient jdbcClient = (JDBCHikariCPClient)client;
        try (Connection connection = jdbcClient.getConnection()) {
            jdbcClient.execute(connection, "drop table " + model.getName());
        } catch (SQLException | JDBCClientException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    @Override
    protected String getColumnType(Class<?> type) {
        if (Integer.class.equals(type) || int.class.equals(type)) {
            return "INT";
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return "BIGINT";
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return "DOUBLE";
        } else if (String.class.equals(type)) {
            return "VARCHAR(2000)";
        } else if (IntKeyLongValueArray.class.equals(type)) {
            return "MEDIUMTEXT";
        } else if (byte[].class.equals(type)) {
            return "MEDIUMTEXT";
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }
}
