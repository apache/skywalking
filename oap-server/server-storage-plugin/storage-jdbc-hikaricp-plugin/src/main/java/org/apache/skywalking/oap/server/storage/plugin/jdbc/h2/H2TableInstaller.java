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
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

/**
 * H2 table initialization. Create tables without Indexes. H2 is for the demonstration only, so, keep the logic as
 * simple as possible.
 */
@Slf4j
public class H2TableInstaller extends ModelInstaller {
    public static final String ID_COLUMN = Metrics.ID;
    public static final String TABLE_COLUMN = "TABLE_NAME";

    public H2TableInstaller(Client client, ModuleManager moduleManager) {
        super(client, moduleManager);
    }

    @Override
    public boolean isExists(Model model) throws StorageException {
        TableMetaInfo.addModel(model);

        return false;
//        final var jdbcClient = (JDBCHikariCPClient) client;
//
//        var tableName = (
//            model.isMetric() ? "metrics_all" :
//                model.isRecord() && !model.isSuperDataset() ? "records_all"
//                    : model.getName()
//        );
//        if (model.getDownsampling() != DownSampling.None) {
//            final var dayTimeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
//            tableName = tableName + Const.UNDERSCORE + dayTimeBucket;
//        }
//        if (!isTableCreated(tableName)) {
//            return false;
//        }
//
//        try (var conn = jdbcClient.getConnection();
//             var columns = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, null)) {
//            final var databaseColumns = new HashSet<String>();
//            while (columns.next()) {
//                databaseColumns.add(columns.getString("COLUMN_NAME"));
//            }
//            return model
//                .getColumns()
//                .stream()
//                .map(ModelColumn::getColumnName)
//                .map(ColumnName::getStorageName)
//                .allMatch(databaseColumns::contains);
//        } catch (SQLException | JDBCClientException e) {
//            throw new StorageException(e.getMessage(), e);
//        }
    }

    @Override
    @SneakyThrows
    public void createTable(Model model) {
        var tableName = model.getName();

        if (model.isTimeSeries()) {
            tableName =
                model.isMetric() ? "metrics_all" :
                    model.isRecord() && !model.isSuperDataset() ? "records_all"
                        : model.getName();
            if (model.getDownsampling() != DownSampling.None) {
                final var dayTimeBucket = TimeBucket.getTimeBucket(System.currentTimeMillis(), DownSampling.Day);
                tableName = tableName + Const.UNDERSCORE + dayTimeBucket;
            }
        }

        createOrUpdateTable(tableName, model.getColumns(), false);
        createOrUpdateTableIndexes(tableName, model.getColumns(), false);
        createAdditionalTable(model);

        if (model.isTimeSeries()) {
            final var viewSql = new SQLBuilder();
            viewSql.append("CREATE OR REPLACE VIEW ")
                   .append(model.getName())
                   .append(" AS SELECT ")
                   .append(
                       Stream.concat(
                                 Stream.of(ID_COLUMN),
                                 model
                                     .getColumns()
                                     .stream()
                                     .map(ModelColumn::getColumnName)
                                     .map(ColumnName::getStorageName))
                             .collect(joining(", ")))
                   .append(" FROM ").append(tableName)
                   .append(" WHERE ").append(TABLE_COLUMN).append(" = '").append(model.getName()).append("'");
            executeSQL(viewSql);
        }
    }

    @Override
    public void start() {
        overrideColumnName("value", "value_");
    }

    public String getColumnDefinition(ModelColumn column) {
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

    public void createOrUpdateTableIndexes(String tableName, List<ModelColumn> columns,
                                           boolean isAdditionalTable) throws JDBCClientException {
    }

    public void executeSQL(SQLBuilder sql) throws JDBCClientException {
        final var c = (JDBCHikariCPClient) client;
        c.execute(sql.toString());
    }

    private void createAdditionalTable(Model model) throws JDBCClientException {
        final var additionalTables = model.getSqlDBModelExtension().getAdditionalTables();
        for (final var table : additionalTables.values()) {
            createOrUpdateTable(table.getName(), table.getColumns(), true);
            createOrUpdateTableIndexes(table.getName(), table.getColumns(), true);
        }
    }

    @SneakyThrows
    public void createOrUpdateTable(String table,
                                    List<ModelColumn> columns,
                                    boolean isAdditionalTable) {
        final var sql = new SQLBuilder();

        if (!isTableCreated(table)) {
            final var columnDefinitions = new ArrayList<String>();
            columnDefinitions.add(ID_COLUMN + " VARCHAR(512)" + (!isAdditionalTable ? " PRIMARY KEY" : ""));
            columnDefinitions.add(TABLE_COLUMN + " VARCHAR(512)");
            columns
                .stream()
                .map(this::getColumnDefinition)
                .collect(toCollection(() -> columnDefinitions));

            sql.append("CREATE TABLE IF NOT EXISTS " + table)
               .append(columnDefinitions.stream().collect(joining(", ", " (", ");")));
        } else {
            final var columnDefinition =
                columns
                    .stream()
                    .map(this::getColumnDefinition)
                    .map(it -> "ADD COLUMN IF NOT EXISTS " + it)
                    .collect(joining(", ", " ", ""));
            if (Strings.isNullOrEmpty(columnDefinition.trim())) {
                return;
            }
            sql.append("ALTER TABLE " + table)
               .append(columnDefinition);
        }

        executeSQL(sql);
    }

    private boolean isTableCreated(final String table) throws StorageException {
        final var jdbcClient = (JDBCHikariCPClient) client;
        try (var conn = jdbcClient.getConnection();
             var result = conn.getMetaData().getTables(conn.getCatalog(), null, table, new String[]{"TABLE"})) {
            return result.next();
        } catch (Exception e) {
            throw new StorageException(e.getMessage(), e);
        }
    }
}
