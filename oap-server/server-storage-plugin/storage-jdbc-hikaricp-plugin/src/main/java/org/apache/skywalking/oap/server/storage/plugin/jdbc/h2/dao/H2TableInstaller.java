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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.storage.StorageException;
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

    protected final int maxSizeOfArrayColumn;
    protected final int numOfSearchableValuesPerTag;

    public H2TableInstaller(Client client,
                            ModuleManager moduleManager,
                            int maxSizeOfArrayColumn,
                            int numOfSearchableValuesPerTag) {
        super(client, moduleManager);
        this.maxSizeOfArrayColumn = maxSizeOfArrayColumn;
        this.numOfSearchableValuesPerTag = numOfSearchableValuesPerTag;
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
                tableCreateSQL.appendLine(
                    getColumn(column) + (i != model.getColumns().size() - 1 ? "," : ""));
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
    protected String getColumn(ModelColumn column) {
        return transform(column, column.getType(), column.getGenericType());
    }

    protected String transform(ModelColumn column, Class<?> type, Type genericType) {
        final String storageName = column.getColumnName().getStorageName();
        if (Integer.class.equals(type) || int.class.equals(type) || NodeType.class.equals(type)) {
            return storageName + " INT";
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return storageName + " BIGINT";
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return storageName + " DOUBLE";
        } else if (String.class.equals(type)) {
            return storageName + " VARCHAR(" + column.getLength() + ")";
        } else if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return storageName + " VARCHAR(20000)";
        } else if (byte[].class.equals(type)) {
            return storageName + " MEDIUMTEXT";
        } else if (JsonObject.class.equals(type)) {
            return storageName + " VARCHAR(" + column.getLength() + ")";
        } else if (List.class.isAssignableFrom(type)) {
            final Type elementType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            String oneColumnType = transform(column, (Class<?>) elementType, elementType);
            // Remove the storageName as prefix
            oneColumnType = oneColumnType.substring(storageName.length());
            StringBuilder columns = new StringBuilder();
            for (int i = 0; i < maxSizeOfArrayColumn; i++) {
                columns.append(storageName).append("_").append(i).append(oneColumnType)
                       .append(i == maxSizeOfArrayColumn - 1 ? "" : ",");
            }
            return columns.toString();
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
