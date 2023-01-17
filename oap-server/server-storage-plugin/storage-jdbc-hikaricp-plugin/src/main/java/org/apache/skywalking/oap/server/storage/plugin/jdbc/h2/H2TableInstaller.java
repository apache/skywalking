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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2;

import com.google.gson.JsonObject;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
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
    public static final String ID_COLUMN = Metrics.ID;

    public H2TableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    public boolean isExists(Model model) throws StorageException {
        TableMetaInfo.addModel(model);
        JDBCHikariCPClient jdbcClient = (JDBCHikariCPClient) client;
        try (Connection conn = jdbcClient.getConnection()) {
            try (ResultSet rset = conn.getMetaData().getTables(conn.getCatalog(), null, model.getName(), null)) {
                if (rset.next()) {
                    return true;
                }
            }
        } catch (SQLException | JDBCClientException e) {
            throw new StorageException(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void createTable(Model model) throws StorageException {
        JDBCHikariCPClient jdbcHikariCPClient = (JDBCHikariCPClient) client;
        try (Connection connection = jdbcHikariCPClient.getConnection()) {
            //Consider there additional table columns need to remove from model columns.
            model = TableMetaInfo.get(model.getName());
            createTable(jdbcHikariCPClient, connection, model.getName(), model.getColumns(), false);
            createTableIndexes(jdbcHikariCPClient, connection, model.getName(), model.getColumns(), false);
            createAdditionalTable(jdbcHikariCPClient, connection, model);
        } catch (JDBCClientException | SQLException e) {
            throw new StorageException(e.getMessage(), e);
        }
    }

    @Override
    public void start() {
        overrideColumnName("value", "value_");
    }

    /**
     * Set up the data type mapping between Java type and H2 database type
     */
    public String getColumn(ModelColumn column) {
        return transform(column, column.getType(), column.getGenericType());
    }

    protected String transform(ModelColumn column, Class<?> type, Type genericType) {
        final String storageName = column.getColumnName().getStorageName();
        if (Integer.class.equals(type) || int.class.equals(type) || Layer.class.equals(type)) {
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
            return transform(column, (Class<?>) elementType, elementType);
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }

    public void createTableIndexes(JDBCHikariCPClient client,
                                      Connection connection,
                                      String tableName, List<ModelColumn> columns, boolean additionalTable) throws JDBCClientException {
    }

    public void createIndex(JDBCHikariCPClient client, Connection connection, String tableName,
                            SQLBuilder indexSQL) throws JDBCClientException {
        if (log.isDebugEnabled()) {
            log.debug("create index for table {}, sql: {} ", tableName, indexSQL.toStringInNewLine());
        }
        client.execute(connection, indexSQL.toString());
    }

    private void createAdditionalTable(JDBCHikariCPClient client,
                                       Connection connection,
                                       Model model) throws JDBCClientException {
        Map<String, SQLDatabaseModelExtension.AdditionalTable> additionalTables = model.getSqlDBModelExtension()
                                                                                       .getAdditionalTables();
        for (SQLDatabaseModelExtension.AdditionalTable table : additionalTables.values()) {
            createTable(client, connection, table.getName(), table.getColumns(), true);
            createTableIndexes(client, connection, table.getName(), table.getColumns(), true);
        }
    }

    public void createTable(JDBCHikariCPClient client,
                            Connection connection,
                            String tableName, List<ModelColumn> columns, boolean additionalTable) throws JDBCClientException {
        SQLBuilder tableCreateSQL = new SQLBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (");
        tableCreateSQL.appendLine(ID_COLUMN).appendLine(" VARCHAR(512) ");
        if (!additionalTable) {
            /**
             * 512 is also the ElasticSearch ID size.
             */

            tableCreateSQL.appendLine("PRIMARY KEY, ");
        } else {
            tableCreateSQL.appendLine(", ");
        }
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            tableCreateSQL.appendLine(
                getColumn(column) + (i != columns.size() - 1 ? "," : ""));
        }
        tableCreateSQL.appendLine(")");

        if (log.isDebugEnabled()) {
            log.debug("creating table: " + tableCreateSQL.toStringInNewLine());
        }

        client.execute(connection, tableCreateSQL.toString());
    }
}
