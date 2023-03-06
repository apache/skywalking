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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.ColumnName;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class JDBCSQLExecutor {
    protected <T extends StorageData> List<StorageData> getByIDs(JDBCClient h2Client,
                                                                 String modelName,
                                                                 List<String> ids,
                                                                 StorageBuilder<T> storageBuilder) throws Exception {
        final var tables = getModelTables(h2Client, modelName);
        final var storageDataList = new ArrayList<StorageData>();

        for (var table : tables) {
            final var sql = new SQLBuilder("SELECT * FROM " + table + " WHERE id in ")
                .append(ids.stream().map(it -> "?").collect(Collectors.joining(",", "(", ")")));
            h2Client.executeQuery(sql.toString(), resultSet -> {
                StorageData storageData;
                while ((storageData = toStorageData(resultSet, modelName, storageBuilder)) != null) {
                    storageDataList.add(storageData);
                }

                return null;
            }, ids.stream().map(it -> TableHelper.generateId(modelName, it)).toArray());
        }
        return storageDataList;
    }

    @SneakyThrows
    protected <T extends StorageData> StorageData getByID(JDBCClient h2Client, String modelName, String id,
                                                          StorageBuilder<T> storageBuilder) {
        final var tables = getModelTables(h2Client, modelName);
        for (var table : tables) {
            final var result = h2Client.executeQuery(
                "SELECT * FROM " + table + " WHERE id = ?",
                resultSet -> toStorageData(resultSet, modelName, storageBuilder),
                TableHelper.generateId(modelName, id)
            );
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    protected StorageData toStorageData(ResultSet rs, String modelName,
                                        StorageBuilder<? extends StorageData> storageBuilder) throws SQLException {
        if (rs.next()) {
            Map<String, Object> data = new HashMap<>();
            List<ModelColumn> columns = TableMetaInfo.get(modelName).getColumns();
            for (ModelColumn column : columns) {
                data.put(column.getColumnName().getName(), rs.getObject(column.getColumnName().getStorageName()));
            }
            return storageBuilder.storage2Entity(new HashMapConverter.ToEntity(data));
        }
        return null;
    }

    protected <T extends StorageData> SQLExecutor getInsertExecutor(Model model, T metrics,
                                                                    long timeBucket,
                                                                    StorageBuilder<T> storageBuilder,
                                                                    Convert2Storage<Map<String, Object>> converter,
                                                                    SessionCacheCallback callback) throws IOException {
        storageBuilder.entity2Storage(metrics, converter);
        Map<String, Object> objectMap = converter.obtain();
        //build main table sql
        Map<String, Object> mainEntity = new HashMap<>();
        model.getColumns().forEach(column -> {
            mainEntity.put(column.getColumnName().getName(), objectMap.get(column.getColumnName().getName()));
        });
        SQLExecutor sqlExecutor = buildInsertExecutor(
            model, metrics, timeBucket, mainEntity, callback);
        //build additional table sql
        for (final var additionalTable : model.getSqlDBModelExtension().getAdditionalTables().values()) {
            Map<String, Object> additionalEntity = new HashMap<>();
            additionalTable.getColumns().forEach(column -> {
                additionalEntity.put(column.getColumnName().getName(), objectMap.get(column.getColumnName().getName()));
            });

            List<SQLExecutor> additionalSQLExecutors = buildAdditionalInsertExecutor(
                model, additionalTable.getName(), additionalTable.getColumns(), metrics, additionalEntity, callback
            );
            sqlExecutor.appendAdditionalSQLs(additionalSQLExecutors);
        }
        return sqlExecutor;
    }

    private <T extends StorageData> SQLExecutor buildInsertExecutor(Model model,
                                                                    T metrics,
                                                                    long timeBucket,
                                                                    Map<String, Object> objectMap,
                                                                    SessionCacheCallback onCompleteCallback) {
        final var table = TableHelper.getTable(model.getName(), timeBucket);
        final var sqlBuilder = new SQLBuilder("INSERT INTO " + table);
        final var columns = model.getColumns();
        final var columnNames =
            Stream.concat(
                      Stream.of(H2TableInstaller.ID_COLUMN, JDBCTableInstaller.TABLE_COLUMN),
                      columns
                          .stream()
                          .map(ModelColumn::getColumnName)
                          .map(ColumnName::getStorageName))
                  .collect(Collectors.toList());
        sqlBuilder.append(columnNames.stream().collect(Collectors.joining(",", "(", ")")));
        sqlBuilder.append(" VALUES ");
        sqlBuilder.append(columnNames.stream().map(it -> "?").collect(Collectors.joining(",", "(", ")")));

        final var param =
            Stream.concat(
                      Stream.of(TableHelper.generateId(model, metrics.id().build()), model.getName()),
                      columns
                          .stream()
                          .map(ModelColumn::getColumnName)
                          .map(ColumnName::getName)
                          .map(objectMap::get)
                          .map(it -> {
                              if (it instanceof StorageDataComplexObject) {
                                  return ((StorageDataComplexObject) it).toStorageData();
                              }
                              return it;
                          }))
                  .collect(Collectors.toList());

        return new SQLExecutor(sqlBuilder.toString(), param, onCompleteCallback);
    }

    private <T extends StorageData> List<SQLExecutor> buildAdditionalInsertExecutor(Model model, String tableName,
                                                                                    List<ModelColumn> columns,
                                                                                    T metrics,
                                                                                    Map<String, Object> objectMap,
                                                                                    SessionCacheCallback callback) {

        List<SQLExecutor> sqlExecutors = new ArrayList<>();
        SQLBuilder sqlBuilder = new SQLBuilder("INSERT INTO " + tableName + " VALUES");
        List<Object> param = new ArrayList<>();
        sqlBuilder.append("(?,");
        param.add(TableHelper.generateId(model, metrics.id().build()));
        int position = 0;
        List valueList = new ArrayList();
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            if (List.class.isAssignableFrom(column.getType())) {
                valueList = (List) objectMap.get(column.getColumnName().getName());
                sqlBuilder.append("?");
                param.add(null);
                position = i + 1;
            } else {
                sqlBuilder.append("?");
                Object value = objectMap.get(column.getColumnName().getName());
                if (value instanceof StorageDataComplexObject) {
                    param.add(((StorageDataComplexObject) value).toStorageData());
                } else {
                    param.add(value);
                }
            }

            if (i != columns.size() - 1) {
                sqlBuilder.append(",");
            }
        }
        sqlBuilder.append(")");
        String sql = sqlBuilder.toString();
        if (!CollectionUtils.isEmpty(valueList)) {
            for (Object object : valueList) {
                List<Object> paramCopy = new ArrayList<>(param);
                paramCopy.set(position, object);
                sqlExecutors.add(new SQLExecutor(sql, paramCopy, callback));
            }
        } else {
            sqlExecutors.add(new SQLExecutor(sql, param, callback));
        }

        return sqlExecutors;
    }

    protected <T extends StorageData> SQLExecutor getUpdateExecutor(Model model, T metrics,
                                                                    long timeBucket,
                                                                    StorageBuilder<T> storageBuilder,
                                                                    SessionCacheCallback callback) {
        final var toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(metrics, toStorage);
        final var objectMap = toStorage.obtain();
        final var table = TableHelper.getTable(model.getName(), timeBucket);
        final var sqlBuilder = new StringBuilder("UPDATE " + table + " SET ");
        final var columns = model.getColumns();
        final var queries = new ArrayList<String>();
        final var param = new ArrayList<>();
        for (final var column : columns) {
            final var columnName = column.getColumnName().getName();
            if (model.getSqlDBModelExtension().isShardingTable()) {
                SQLDatabaseModelExtension.Sharding sharding = model.getSqlDBModelExtension().getSharding().orElseThrow(
                    () -> new UnexpectedException("Sharding should not be empty."));
                if (columnName.equals(sharding.getDataSourceShardingColumn()) || columnName.equals(
                    sharding.getTableShardingColumn())) {
                    continue;
                }
            }
            queries.add(column.getColumnName().getStorageName() + " = ?");

            final var value = objectMap.get(columnName);
            if (value instanceof StorageDataComplexObject) {
                param.add(((StorageDataComplexObject) value).toStorageData());
            } else {
                param.add(value);
            }
        }
        sqlBuilder.append(queries.stream().collect(Collectors.joining(", ")));
        sqlBuilder.append(" WHERE id = ?");
        param.add(metrics.id().build());

        return new SQLExecutor(sqlBuilder.toString(), param, callback);
    }

    private static ArrayList<String> getModelTables(JDBCClient h2Client, String modelName) throws Exception {
        final var model = TableMetaInfo.get(modelName);
        final var tableNamePattern = (
            model.isMetric() ? "metrics_all" :
                model.isRecord() && !model.isSuperDataset() ? "records_all"
                    : model.getName()
        ) + "%";
        final var tables = new ArrayList<String>();
        try (final var connection = h2Client.getConnection();
             final var resultSet = connection.getMetaData().getTables(connection.getCatalog(), null, tableNamePattern, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }
}
