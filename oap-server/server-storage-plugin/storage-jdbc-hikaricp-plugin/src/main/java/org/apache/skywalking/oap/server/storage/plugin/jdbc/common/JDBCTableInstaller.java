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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common;

import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * JDBC table installer, uses standard SQL to create tables, indices, and views.
 */
@Slf4j
public class JDBCTableInstaller extends ModelInstaller {
    public static final String ID_COLUMN = Metrics.ID;
    public static final String TABLE_COLUMN = "table_name";

    public JDBCTableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    @SneakyThrows
    public boolean isExists(Model model) {
        TableMetaInfo.addModel(model);

        final var table = TableHelper.getLatestTableForWrite(model);

        final var jdbcClient = (JDBCClient) client;
        if (!jdbcClient.tableExists(table)) {
            return false;
        }

        final var databaseColumns = getDatabaseColumns(table);
        final var isAnyColumnNotCreated =
            model
                .getColumns().stream()
                .map(ModelColumn::getColumnName)
                .map(ColumnName::getStorageName)
                .anyMatch(not(databaseColumns::contains));

        return !isAnyColumnNotCreated;
    }

    @Override
    @SneakyThrows
    public void createTable(Model model) {
        final var dayTimeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
        createTable(model, dayTimeBucket);
    }

    @SneakyThrows
    public void createTable(Model model, long timeBucket) {
        final var table = TableHelper.getTable(model, timeBucket);
        createOrUpdateTable(table, model.getColumns(), false);
        createOrUpdateTableIndexes(table, model.getColumns(), false);
        createAdditionalTable(model, timeBucket);
    }

    public String getColumnDefinition(ModelColumn column) {
        return getColumnDefinition(column, column.getType(), column.getGenericType());
    }

    protected String getColumnDefinition(ModelColumn column, Class<?> type, Type genericType) {
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
            return getColumnDefinition(column, (Class<?>) elementType, elementType);
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type.getName());
        }
    }

    public void createOrUpdateTableIndexes(String table, List<ModelColumn> columns,
                                           boolean isAdditionalTable) throws SQLException {
        final var jdbcClient = (JDBCClient) client;

        // Additional table's id is a many-to-one relation to the main table's id,
        // and thus can not be primary key, but a simple index.
        if (isAdditionalTable) {
            final var index = "idx_" + Math.abs((table + "_" + JDBCTableInstaller.ID_COLUMN).hashCode());
            if (!jdbcClient.indexExists(table, index)) {
                executeSQL(
                    new SQLBuilder("CREATE INDEX ")
                        .append(index)
                        .append(" ON ").append(table)
                        .append("(")
                        .append(ID_COLUMN)
                        .append(")")
                );
            }
        }

        if (!isAdditionalTable) {
            final var index = "idx_" + Math.abs((table + "_" + JDBCTableInstaller.TABLE_COLUMN).hashCode());
            if (!jdbcClient.indexExists(table, index)) {
                executeSQL(
                    new SQLBuilder("CREATE INDEX ")
                        .append(index)
                        .append(" ON ")
                        .append(table)
                        .append("(")
                        .append(JDBCTableInstaller.TABLE_COLUMN)
                        .append(")")
                );
            }
        }

        final var columnsMissingIndex =
            columns
                .stream()
                .filter(ModelColumn::shouldIndex)
                .filter(it -> it.getLength() < 256)
                .map(ModelColumn::getColumnName)
                .map(ColumnName::getStorageName)
                .collect(toList());
        for (var column : columnsMissingIndex) {
            final var index = "idx_" + Math.abs((table + "_" + column).hashCode());
            if (!jdbcClient.indexExists(table, index)) {
                executeSQL(
                    new SQLBuilder("CREATE INDEX ")
                        .append(index)
                        .append(" ON ").append(table).append("(")
                        .append(column)
                        .append(")")
                );
            }
        }

        final var columnNames =
            columns
                .stream()
                .map(ModelColumn::getColumnName)
                .map(ColumnName::getStorageName)
                .collect(toSet());
        for (final var modelColumn : columns) {
            for (final var compositeIndex : modelColumn.getSqlDatabaseExtension().getIndices()) {
                final var multiColumns = Arrays.asList(compositeIndex.getColumns());
                // Don't create composite index on the additional table if it doesn't contain all needed columns.
                if (isAdditionalTable && !columnNames.containsAll(multiColumns)) {
                    continue;
                }
                final var index = "idx_" + Math.abs((table + "_" + String.join("_", multiColumns)).hashCode());
                if (jdbcClient.indexExists(table, index)) {
                    continue;
                }
                executeSQL(
                    new SQLBuilder("CREATE INDEX ")
                        .append(index)
                        .append(" ON ")
                        .append(table)
                        .append(multiColumns.stream().collect(joining(", ", " (", ")")))
                );
            }
        }
    }

    public void executeSQL(SQLBuilder sql) throws SQLException {
        final var c = (JDBCClient) client;
        c.execute(sql.toString());
    }

    public void createAdditionalTable(Model model, long timeBucket) throws SQLException {
        final var additionalTables = model.getSqlDBModelExtension().getAdditionalTables();
        for (final var table : additionalTables.values()) {
            final var tableName = TableHelper.getTable(table.getName(), timeBucket);
            createOrUpdateTable(tableName, table.getColumns(), true);
            createOrUpdateTableIndexes(tableName, table.getColumns(), true);
        }
    }

    @SneakyThrows
    public void createOrUpdateTable(String table, List<ModelColumn> columns, boolean isAdditionalTable) {
        // Some SQL implementations don't have the syntax "alter table <table> add column if not exists",
        // we have to query the columns and filter out the existing ones.
        final var columnsToBeAdded = new ArrayList<>(columns);
        final var existingColumns = getDatabaseColumns(table);
        columnsToBeAdded.removeIf(it -> existingColumns.contains(it.getColumnName().getStorageName()));

        final var jdbcClient = (JDBCClient) client;
        if (!jdbcClient.tableExists(table)) {
            createTable(table, columnsToBeAdded, isAdditionalTable);
        } else {
            updateTable(table, columnsToBeAdded);
        }
    }

    protected Set<String> getDatabaseColumns(String table) throws SQLException {
        final var jdbcClient = (JDBCClient) client;
        return jdbcClient.getTableColumns(table);
    }

    private void updateTable(String table, List<ModelColumn> columns) throws SQLException {
        final var alterSqls =
            columns
                .stream()
                .map(this::getColumnDefinition)
                .map(definition -> "ALTER TABLE " + table + " ADD COLUMN " + definition + "; ")
                .collect(toList());

        for (String alterSql : alterSqls) {
            executeSQL(new SQLBuilder(alterSql));
        }
    }

    private void createTable(String table, List<ModelColumn> columns, boolean isAdditionalTable) throws SQLException {
        final var columnDefinitions = new ArrayList<String>();
        columnDefinitions.add(ID_COLUMN + " VARCHAR(512)" + (!isAdditionalTable ? " PRIMARY KEY" : ""));
        if (!isAdditionalTable) {
            columnDefinitions.add(TABLE_COLUMN + " VARCHAR(512)");
        }
        columns
            .stream()
            .map(this::getColumnDefinition)
            .collect(toCollection(() -> columnDefinitions));

        final var sql =
            new SQLBuilder("CREATE TABLE IF NOT EXISTS " + table)
                .append(columnDefinitions.stream().collect(joining(", ", " (", ");")));

        executeSQL(sql);
    }
}
