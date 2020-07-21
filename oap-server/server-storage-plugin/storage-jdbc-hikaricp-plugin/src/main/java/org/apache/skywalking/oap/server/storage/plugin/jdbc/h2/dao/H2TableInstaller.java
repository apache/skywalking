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

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

/**
 * H2 table initialization. Create tables without Indexes. H2 is for the demonstration only, so, keep the logic as
 * simple as possible.
 */
@Slf4j
public class H2TableInstaller extends ModelInstaller {
    public static final String ID_COLUMN = "id";

    public H2TableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    protected boolean isExists(Model model) throws StorageException {
        TableMetaInfo.addModel(model);
        return false;
    }

    @Override
    protected void createTable(Model model) throws StorageException {
        JDBCHikariCPClient jdbcHikariCPClient = (JDBCHikariCPClient) client;
        try (Connection connection = jdbcHikariCPClient.getConnection()) {
            SQLBuilder tableCreateSQL = new SQLBuilder("CREATE TABLE IF NOT EXISTS " + model.getName() + " (");
            /**
             * 512 is also the ElasticSearch ID size.
             */
            tableCreateSQL.appendLine("id VARCHAR(512) PRIMARY KEY, ");
            for (int i = 0; i < model.getColumns().size(); i++) {
                ModelColumn column = model.getColumns().get(i);
                ColumnName name = column.getColumnName();
                tableCreateSQL.appendLine(
                    name.getStorageName() + " " + getColumnType(column) + (i != model
                        .getColumns()
                        .size() - 1 ? "," : ""));
            }
            tableCreateSQL.appendLine(")");

            if (log.isDebugEnabled()) {
                log.debug("creating table: " + tableCreateSQL.toStringInNewLine());
            }

            jdbcHikariCPClient.execute(connection, tableCreateSQL.toString());

            createTableIndexes(jdbcHikariCPClient, connection, model);
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    /**
     * Set up the data type mapping between Java type and H2 database type
     */
    protected String getColumnType(ModelColumn column) {
        final Class<?> type = column.getType();
        if (Integer.class.equals(type) || int.class.equals(type) || NodeType.class.equals(type)) {
            return "INT";
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return "BIGINT";
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return "DOUBLE";
        } else if (String.class.equals(type)) {
            return "VARCHAR(" + column.getLength() + ")";
        } else if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return "VARCHAR(20000)";
        } else if (byte[].class.equals(type)) {
            return "MEDIUMTEXT";
        } else if (JsonObject.class.equals(type)) {
            return "VARCHAR(" + column.getLength() + ")";
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }

    protected void createTableIndexes(JDBCHikariCPClient client,
                                      Connection connection,
                                      Model model) throws JDBCClientException {
    }

    protected void createIndex(JDBCHikariCPClient client, Connection connection, Model model,
                               SQLBuilder indexSQL) throws JDBCClientException {
        if (log.isDebugEnabled()) {
            log.debug("create index for table {}, sql: {} ", model.getName(), indexSQL.toStringInNewLine());
        }
        client.execute(connection, indexSQL.toString());
    }
}
