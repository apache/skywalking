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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.ExtraQueryIndex;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao.H2TableInstaller;

/**
 * Extend H2TableInstaller but match MySQL SQL syntax.
 */
@Slf4j
public class MySQLTableInstaller extends H2TableInstaller {
    public MySQLTableInstaller(Client client,
                               ModuleManager moduleManager,
                               int maxSizeOfArrayColumn,
                               int numOfSearchableValuesPerTag) {
        super(client, moduleManager, maxSizeOfArrayColumn, numOfSearchableValuesPerTag);
        /*
         * Override column because the default column names in core have syntax conflict with MySQL.
         */
        this.overrideColumnName("precision", "cal_precision");
        this.overrideColumnName("match", "match_num");
    }

    @Override
    protected boolean isExists(Model model) throws StorageException {
        TableMetaInfo.addModel(model);
        JDBCHikariCPClient h2Client = (JDBCHikariCPClient) client;
        try (Connection conn = h2Client.getConnection()) {
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
    protected void createTableIndexes(JDBCHikariCPClient client,
                                      Connection connection,
                                      Model model) throws JDBCClientException {
        int indexSeq = 0;
        for (final ModelColumn modelColumn : model.getColumns()) {
            if (!modelColumn.isStorageOnly() && modelColumn.getLength() < 256) {
                final Class<?> type = modelColumn.getType();
                if (List.class.isAssignableFrom(type)) {
                    for (int i = 0; i < maxSizeOfArrayColumn; i++) {
                        SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
                        tableIndexSQL.append(model.getName().toUpperCase())
                                     .append("_")
                                     .append(String.valueOf(indexSeq++))
                                     .append("_IDX ");
                        tableIndexSQL.append("ON ").append(model.getName()).append("(")
                                     .append(modelColumn.getColumnName().getStorageName() + "_" + i)
                                     .append(")");
                        createIndex(client, connection, model, tableIndexSQL);
                    }
                } else {
                    SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
                    tableIndexSQL.append(model.getName().toUpperCase())
                                 .append("_")
                                 .append(String.valueOf(indexSeq++))
                                 .append("_IDX ");
                    tableIndexSQL.append("ON ").append(model.getName()).append("(")
                                 .append(modelColumn.getColumnName().getStorageName())
                                 .append(")");
                    createIndex(client, connection, model, tableIndexSQL);
                }
            }
        }

        for (final ExtraQueryIndex extraQueryIndex : model.getExtraQueryIndices()) {
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(model.getName().toUpperCase())
                         .append("_")
                         .append(String.valueOf(indexSeq++))
                         .append("_IDX ");
            tableIndexSQL.append(" ON ").append(model.getName()).append("(");
            final String[] columns = extraQueryIndex.getColumns();
            for (int i = 0; i < columns.length; i++) {
                tableIndexSQL.append(columns[i]);
                if (i < columns.length - 1) {
                    tableIndexSQL.append(",");
                }
            }
            tableIndexSQL.append(")");
            createIndex(client, connection, model, tableIndexSQL);
        }
    }

    @Override
    protected String getColumn(final ModelColumn column) {
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
        }
        return super.getColumn(column);
    }
}
