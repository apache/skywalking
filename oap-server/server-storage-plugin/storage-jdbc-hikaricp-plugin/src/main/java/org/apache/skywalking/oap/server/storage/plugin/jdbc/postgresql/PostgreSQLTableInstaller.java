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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.Layer;
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

public class PostgreSQLTableInstaller extends H2TableInstaller {
    public PostgreSQLTableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    public void start() {
        /*
         * Override column because the default column names in core are reserved in PostgreSQL.
         */
        this.overrideColumnName("precision", "cal_precision");
        this.overrideColumnName("match", "match_num");
    }

    @Override
    public void createTableIndexes(
        JDBCHikariCPClient client,
        Connection connection,
        String tableName,
        List<ModelColumn> columns,
        boolean additionalTable) throws JDBCClientException {
        // Additional table's id follow the main table can not be primary key
        if (additionalTable) {
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
            tableIndexSQL.append(tableName.toUpperCase()).append("_id_IDX");
            tableIndexSQL.append(" ON ").append(tableName).append("(").append(ID_COLUMN).append(")");
            createIndex(client, connection, tableName, tableIndexSQL);
        }

        int indexSeq = 0;
        for (final ModelColumn modelColumn : columns) {
            if (modelColumn.shouldIndex() && modelColumn.getLength() < 256) {
                    SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
                    tableIndexSQL.append(tableName.toUpperCase())
                                 .append("_")
                                 .append(String.valueOf(indexSeq++))
                                 .append("_IDX ");
                    tableIndexSQL.append("ON ").append(tableName).append("(")
                                 .append(modelColumn.getColumnName().getStorageName())
                                 .append(")");
                    createIndex(client, connection, tableName, tableIndexSQL);
            }
        }

        List<String> columnList = columns.stream().map(column -> column.getColumnName().getStorageName()).collect(
            Collectors.toList());
        for (final ModelColumn modelColumn : columns) {
            for (final SQLDatabaseExtension.MultiColumnsIndex index : modelColumn.getSqlDatabaseExtension()
                                                                                 .getIndices()) {
                final String[] multiColumns = index.getColumns();
                //Create MultiColumnsIndex on the additional table only when it contains all need columns.
                if (additionalTable && !columnList.containsAll(Arrays.asList(multiColumns))) {
                    continue;
                }
                SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX ");
                tableIndexSQL.append(tableName.toUpperCase())
                             .append("_")
                             .append(String.valueOf(indexSeq++))
                             .append("_IDX ");
                tableIndexSQL.append(" ON ").append(tableName).append("(");
                for (int i = 0; i < multiColumns.length; i++) {
                    tableIndexSQL.append(multiColumns[i]);
                    if (i < multiColumns.length - 1) {
                        tableIndexSQL.append(",");
                    }
                }
                tableIndexSQL.append(")");
                createIndex(client, connection, tableName, tableIndexSQL);
            }
        }
    }

    @Override
    protected String transform(ModelColumn column, Class<?> type, Type genericType) {
        final String storageName = column.getColumnName().getStorageName();
        if (Integer.class.equals(type) || int.class.equals(type) || Layer.class.equals(type)) {
            return storageName + " INT";
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            return storageName + " BIGINT";
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            return storageName + " DOUBLE PRECISION";
        } else if (String.class.equals(type)) {
            return storageName + " VARCHAR(" + column.getLength() + ")";
        } else if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return storageName + " VARCHAR(20000)";
        } else if (byte[].class.equals(type)) {
            return storageName + " TEXT";
        } else if (JsonObject.class.equals(type)) {
            if (column.getLength() > 16383) {
                return storageName + " TEXT";
            } else {
                return storageName + " VARCHAR(" + column.getLength() + ")";
            }
        } else if (List.class.isAssignableFrom(type)) {
            final Type elementType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            return transform(column, (Class<?>) elementType, elementType);
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }

    @Override
    public String getColumn(final ModelColumn column) {
        final String storageName = column.getColumnName().getStorageName();
        final Class<?> type = column.getType();
        if (StorageDataComplexObject.class.isAssignableFrom(type)) {
            return storageName + " TEXT";
        } else if (String.class.equals(type)) {
            if (column.getLength() > 16383) {
                return storageName + " TEXT";
            } else {
                return storageName + " VARCHAR(" + column.getLength() + ")";
            }
        } else if (JsonObject.class.equals(type)) {
            if (column.getLength() > 16383) {
                return storageName + " TEXT";
            } else {
                return storageName + " VARCHAR(" + column.getLength() + ")";
            }
        }
        return super.getColumn(column);
    }
}
