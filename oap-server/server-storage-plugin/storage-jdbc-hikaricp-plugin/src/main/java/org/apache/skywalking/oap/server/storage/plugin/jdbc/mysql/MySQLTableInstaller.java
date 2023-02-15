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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseExtension;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

/**
 * Extend H2TableInstaller but match MySQL SQL syntax.
 */
@Slf4j
public class MySQLTableInstaller extends H2TableInstaller {
    public MySQLTableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    public void start() {
        /*
         * Override column because the default column names in core have syntax conflict with MySQL.
         */
        this.overrideColumnName("precision", "cal_precision");
        this.overrideColumnName("match", "match_num");
    }

    @Override
    public void createTable(
        JDBCHikariCPClient client,
        Connection connection,
        String tableName,
        List<ModelColumn> columns,
        boolean additionalTable) throws JDBCClientException {
        SQLBuilder tableCreateSQL =
            new SQLBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (");
        tableCreateSQL.append(ID_COLUMN).append(" VARCHAR(512) ");
        if (!additionalTable) {
            tableCreateSQL.appendLine("PRIMARY KEY, ");
        } else {
            tableCreateSQL.appendLine(", ");
        }
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            tableCreateSQL.appendLine(
                getColumn(column) + (i != columns.size() ? "," : ""));
        }

        int indexSeq = 0;

        // Add indexes
        List<String> columnList =
            columns.stream().map(column -> column.getColumnName().getStorageName()).collect(
                Collectors.toList());
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            for (final SQLDatabaseExtension.MultiColumnsIndex index : column.getSqlDatabaseExtension()
                                                                            .getIndices()) {
                final String[] multiColumns = index.getColumns();
                // Create MultiColumnsIndex on the additional table only when it contains all need columns.
                if (additionalTable && !columnList.containsAll(Arrays.asList(multiColumns))) {
                    continue;
                }
                tableCreateSQL.append("KEY K")
                    .append(String.valueOf(indexSeq++));
                tableCreateSQL.append(" (");
                for (int j = 0; j < multiColumns.length; j++) {
                    tableCreateSQL.append(multiColumns[j]);
                    if (j < multiColumns.length - 1) {
                        tableCreateSQL.append(",");
                    }
                }
                tableCreateSQL.appendLine("),");
            }
            if (column.shouldIndex() && column.getLength() < 513) {
                tableCreateSQL
                    .append("KEY K")
                    .append(String.valueOf(indexSeq++))
                    .append(" (")
                    .append(column.getColumnName().getStorageName())
                    .append(")");
                tableCreateSQL.appendLine(i != columns.size() - 1 ? "," : "");
            }
        }
        tableCreateSQL.appendLine(")");

        if (log.isDebugEnabled()) {
            log.debug("creating table: " + tableCreateSQL.toStringInNewLine());
        }

        client.execute(connection, tableCreateSQL.toString());
    }

    @Override
    public void createTableIndexes(
        JDBCHikariCPClient client,
        Connection connection,
        String tableName, List<ModelColumn> columns,
        boolean additionalTable)
        throws JDBCClientException {
        // Do nothing, indexes have been created inside create tables.
    }

    @Override
    public String getColumn(final ModelColumn column) {
        final String storageName = column.getColumnName().getStorageName();
        final Class<?> type = column.getType();
        if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return storageName + " MEDIUMTEXT";
        } else if (String.class.equals(type)) {
            if (column.getLength() > 16383) {
                return storageName + " MEDIUMTEXT";
            } else {
                return storageName + " VARCHAR(" + column.getLength() + ")";
            }
        } else if (JsonObject.class.equals(type)) {
            if (column.getLength() > 16383) {
                return storageName + " MEDIUMTEXT";
            } else {
                return storageName + " VARCHAR(" + column.getLength() + ")";
            }
        }
        return super.getColumn(column);
    }
}
