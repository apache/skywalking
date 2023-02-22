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

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.StorageException;
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
    public static final String TABLE_COLUMN = "TABLE_NAME";

    public JDBCTableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    @SneakyThrows
    public boolean isExists(Model model) throws StorageException {
        TableMetaInfo.addModel(model);

        final var tableName = tableNameOf(model);

        if (!isTableExisted(tableName)) {
            return false;
        }

        final var databaseColumns = getDatabaseColumns(tableName);
        final var isAnyColumnNotCreated =
            model
                .getColumns().stream()
                .map(ModelColumn::getColumnName)
                .map(ColumnName::getStorageName)
                .anyMatch(not(databaseColumns::contains));
        if (isAnyColumnNotCreated) {
            return false;
        }

        return true;
    }

    @Override
    @SneakyThrows
    public void createTable(Model model) {
        final var tableName = tableNameOf(model);

        createOrUpdateTable(tableName, model.getColumns(), false);
        createOrUpdateTableIndexes(tableName, model.getColumns(), false);
        createAdditionalTable(model);
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

    public void createOrUpdateTableIndexes(String tableName, List<ModelColumn> columns,
                                           boolean isAdditionalTable) throws SQLException {
        final var jdbcClient = (JDBCClient) client;

        // Additional table's id follow the main table can not be primary key
        if (isAdditionalTable) {
            final var indexName = tableName + "_id";
            if (!isIndexExisted(tableName, indexName)) {
                final var tableIndexSQL = new SQLBuilder("CREATE INDEX ")
                    .append(indexName)
                    .append(" ON ").append(tableName)
                    .append("(").append(ID_COLUMN).append(")");
                executeSQL(tableIndexSQL);
            }
        }

        if (!isAdditionalTable) {
            final var indexName = tableName + "_" + JDBCTableInstaller.TABLE_COLUMN;
            if (!isIndexExisted(tableName, indexName)) {
                executeSQL(
                    new SQLBuilder("CREATE INDEX ")
                        .append(indexName)
                        .append(" ON ")
                        .append(tableName)
                        .append("(")
                        .append(JDBCTableInstaller.TABLE_COLUMN)
                        .append(")")
                );
            }
        }

        final var c =
            columns
                .stream()
                .filter(ModelColumn::shouldIndex)
                .filter(it -> it.getLength() < 256)
                .map(ModelColumn::getColumnName)
                .map(ColumnName::getStorageName)
                .collect(toList());
        for (var column : c) {
            final var indexName = tableName + "_" + Math.abs(column.hashCode());
            if (!isIndexExisted(tableName, indexName)) {
                final var sql = new SQLBuilder("CREATE INDEX ")
                    .append(indexName)
                    .append(" ON ").append(tableName).append("(")
                    .append(column)
                    .append(")");
                executeSQL(sql);
            }
        }

        final var columnList = columns.stream().map(ModelColumn::getColumnName).map(ColumnName::getStorageName).collect(toSet());
        for (final var modelColumn : columns) {
            for (final var index : modelColumn.getSqlDatabaseExtension().getIndices()) {
                final var multiColumns = Arrays.asList(index.getColumns());
                // Don't create composite index on the additional table if it doesn't contain all needed columns.
                if (isAdditionalTable && !columnList.containsAll(multiColumns)) {
                    continue;
                }
                final var indexName = tableName + "_" + Math.abs(String.join("_", multiColumns).hashCode());
                if (isIndexExisted(tableName, indexName)) {
                    continue;
                }
                final var sql = new SQLBuilder("CREATE INDEX ")
                    .append(indexName)
                    .append(" ON ")
                    .append(tableName)
                    .append(multiColumns.stream().collect(joining(", ", " (", ")")));
                executeSQL(sql);
            }
        }
    }

    public void executeSQL(SQLBuilder sql) throws SQLException {
        final var c = (JDBCClient) client;
        c.execute(sql.toString());
    }

    private void createAdditionalTable(Model model) throws SQLException {
        final var additionalTables = model.getSqlDBModelExtension().getAdditionalTables();
        for (final var table : additionalTables.values()) {
            createOrUpdateTable(table.getName(), table.getColumns(), true);
            createOrUpdateTableIndexes(table.getName(), table.getColumns(), true);
        }
    }

    @SneakyThrows
    public void createOrUpdateTable(String table, List<ModelColumn> columns, boolean isAdditionalTable) {
        // Some SQL implementations don't have the syntax "alter table <table> add column if not exists",
        // we have to query the columns and filter out the existing ones.
        final var columnsToBeAdded = new ArrayList<>(columns);
        final var existingColumns = getDatabaseColumns(table);
        columnsToBeAdded.removeIf(it -> existingColumns.contains(it.getColumnName().getStorageName()));

        if (!isTableExisted(table)) {
            createTable(table, columnsToBeAdded, isAdditionalTable);
        } else {
            updateTable(table, columnsToBeAdded);
        }
    }

    protected Set<String> getDatabaseColumns(String table) throws SQLException {
        final var jdbcClient = (JDBCClient) client;
        return jdbcClient.getTableColumns(table);
    }

    protected boolean isTableExisted(String table) throws SQLException {
        final var jdbcClient = (JDBCClient) client;
        return jdbcClient.isTableExisted(table);
    }

    protected boolean isIndexExisted(String table, String index) throws SQLException {
        final var jdbcClient = (JDBCClient) client;
        return jdbcClient.isIndexExisted(table, index);
    }

    private void updateTable(String table, List<ModelColumn> columns) throws SQLException {
        final var alterSql =
            columns
                .stream()
                .map(this::getColumnDefinition)
                .map(definition -> "ALTER TABLE " + table + " ADD COLUMN " + definition + "; ")
                .collect(joining());

        if (Strings.isNullOrEmpty(alterSql.trim())) {
            return;
        }

        executeSQL(new SQLBuilder(alterSql));
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

    private String tableNameOf(Model model) {
        if (!model.isTimeSeries()) {
            return model.getName();
        }

        var tableName =
            model.isMetric() ? "metrics_all" :
                model.isRecord() && !model.isSuperDataset() ? "records_all"
                    : model.getName();
        if (model.getDownsampling() != DownSampling.None) {
            final var dayTimeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
            tableName = tableName + Const.UNDERSCORE + dayTimeBucket;
        }

        return tableName;
    }
}
