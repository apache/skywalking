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

import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

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
    public void createOrUpdateTableIndexes(
        String tableName,
        List<ModelColumn> columns,
        boolean isAdditionalTable) throws JDBCClientException {
        // Additional table's id follow the main table can not be primary key
        if (isAdditionalTable) {
            SQLBuilder tableIndexSQL = new SQLBuilder("CREATE INDEX IF NOT EXISTS ");
            tableIndexSQL.append(tableName.toUpperCase()).append("_id_IDX");
            tableIndexSQL.append(" ON ").append(tableName).append("(").append(ID_COLUMN).append(")");
            executeSQL(tableIndexSQL);
        }

        executeSQL(
            new SQLBuilder("CREATE INDEX IF NOT EXISTS ")
                .append(tableName.toUpperCase())
                .append("_")
                .append(H2TableInstaller.TABLE_COLUMN)
                .append(" ON ")
                .append(tableName)
                .append("(")
                .append(H2TableInstaller.TABLE_COLUMN)
                .append(")")
        );

        final var c =
            columns
                .stream()
                .filter(ModelColumn::shouldIndex)
                .filter(it -> it.getLength() < 256)
                .map(ModelColumn::getColumnName)
                .map(ColumnName::getStorageName)
                .collect(toList());
        for (var column : c) {
            final var sql = new SQLBuilder("CREATE INDEX IF NOT EXISTS ");
            sql.append(tableName.toUpperCase())
               .append("_")
               .append(column)
               .append(" ON ").append(tableName).append("(")
               .append(column)
               .append(")");
            executeSQL(sql);
        }

        final var columnList = columns.stream().map(ModelColumn::getColumnName).map(ColumnName::getStorageName).collect(toSet());
        for (final var modelColumn : columns) {
            for (final var index : modelColumn.getSqlDatabaseExtension().getIndices()) {
                final var multiColumns = Arrays.asList(index.getColumns());
                // Don't create composite index on the additional table if it doesn't contain all needed columns.
                if (isAdditionalTable && !columnList.containsAll(multiColumns)) {
                    continue;
                }
                final var sql = new SQLBuilder("CREATE INDEX IF NOT EXISTS ");
                sql.append(tableName.toUpperCase())
                   .append("_")
                   .append(String.join("_", multiColumns))
                   .append(" ON ")
                   .append(tableName);
                sql.append(multiColumns.stream().collect(Collectors.joining(", ", " (", ")")));
                executeSQL(sql);
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
    public String getColumnDefinition(final ModelColumn column) {
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
        return super.getColumnDefinition(column);
    }
}
