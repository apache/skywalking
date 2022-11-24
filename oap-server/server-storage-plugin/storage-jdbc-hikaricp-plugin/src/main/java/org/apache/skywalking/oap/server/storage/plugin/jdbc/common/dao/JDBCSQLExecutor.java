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

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

@Slf4j
public class JDBCSQLExecutor {
    protected <T extends StorageData> List<StorageData> getByIDs(JDBCHikariCPClient h2Client,
                                                                 String modelName,
                                                                 String[] ids,
                                                                 StorageBuilder<T> storageBuilder) throws IOException {

        try (Connection connection = h2Client.getConnection()) {
            SQLBuilder sql = new SQLBuilder("SELECT * FROM " + modelName + " WHERE id in (");
            List<Object> parameters = new ArrayList<>(ids.length);
            for (int i = 0; i < ids.length; i++) {
                if (i == 0) {
                    sql.append("?");
                } else {
                    sql.append(",?");
                }
                parameters.add(ids[i]);
            }
            sql.append(")");
            try (ResultSet rs = h2Client.executeQuery(connection, sql.toString(), parameters.toArray(new Object[0]))) {
                StorageData storageData;
                List<StorageData> storageDataList = new ArrayList<>();
                do {
                    storageData = toStorageData(rs, modelName, storageBuilder);
                    if (storageData != null) {
                        storageDataList.add(storageData);
                    }
                }
                while (storageData != null);

                return storageDataList;
            }
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    protected <T extends StorageData> StorageData getByID(JDBCHikariCPClient h2Client, String modelName, String id,
                                                          StorageBuilder<T> storageBuilder) throws IOException {
        try (Connection connection = h2Client.getConnection();
             ResultSet rs = h2Client.executeQuery(connection, "SELECT * FROM " + modelName + " WHERE id = ?", id)) {
            return toStorageData(rs, modelName, storageBuilder);
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    protected StorageData getByColumn(JDBCHikariCPClient h2Client, String modelName, String columnName, Object value,
                                      StorageBuilder<? extends StorageData> storageBuilder) throws IOException {
        try (Connection connection = h2Client.getConnection();
             ResultSet rs = h2Client.executeQuery(
                 connection, "SELECT * FROM " + modelName + " WHERE " + columnName + " = ?", value)) {
            return toStorageData(rs, modelName, storageBuilder);
        } catch (SQLException | JDBCClientException e) {
            throw new IOException(e.getMessage(), e);
        }
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

    protected <T extends StorageData> SQLExecutor getInsertExecutor(String modelName, T metrics,
                                                                    StorageBuilder<T> storageBuilder,
                                                                    Convert2Storage<Map<String, Object>> converter,
                                                                    SessionCacheCallback callback) throws IOException {
        Model model = TableMetaInfo.get(modelName);
        storageBuilder.entity2Storage(metrics, converter);
        Map<String, Object> objectMap = converter.obtain();
        //build main table sql
        Map<String, Object> mainEntity = new HashMap<>();
        model.getColumns().forEach(column -> {
            mainEntity.put(column.getColumnName().getName(), objectMap.get(column.getColumnName().getName()));
        });
        SQLExecutor sqlExecutor = buildInsertExecutor(
            modelName, model.getColumns(), metrics, mainEntity, callback);
        //build additional table sql
        for (SQLDatabaseModelExtension.AdditionalTable additionalTable : model.getSqlDBModelExtension()
                                                                              .getAdditionalTables()
                                                                              .values()) {
            Map<String, Object> additionalEntity = new HashMap<>();
            additionalTable.getColumns().forEach(column -> {
                additionalEntity.put(column.getColumnName().getName(), objectMap.get(column.getColumnName().getName()));
            });

            List<SQLExecutor> additionalSQLExecutors = buildAdditionalInsertExecutor(
                additionalTable.getName(), additionalTable.getColumns(), metrics, additionalEntity, callback
            );
            sqlExecutor.appendAdditionalSQLs(additionalSQLExecutors);
        }
        return sqlExecutor;
    }

    private <T extends StorageData> SQLExecutor buildInsertExecutor(String tableName,
                                                                    List<ModelColumn> columns,
                                                                    T metrics,
                                                                    Map<String, Object> objectMap,
                                                                    SessionCacheCallback onCompleteCallback) throws IOException {
        SQLBuilder sqlBuilder = new SQLBuilder("INSERT INTO " + tableName + " VALUES");
        List<Object> param = new ArrayList<>();
        sqlBuilder.append("(?,");
        param.add(metrics.id());
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            sqlBuilder.append("?");

            Object value = objectMap.get(column.getColumnName().getName());
            if (value instanceof StorageDataComplexObject) {
                param.add(((StorageDataComplexObject) value).toStorageData());
            } else {
                param.add(value);
            }

            if (i != columns.size() - 1) {
                sqlBuilder.append(",");
            }
        }
        sqlBuilder.append(")");

        return new SQLExecutor(sqlBuilder.toString(), param, onCompleteCallback);
    }

    private <T extends StorageData> List<SQLExecutor> buildAdditionalInsertExecutor(String tableName,
                                                                                    List<ModelColumn> columns,
                                                                                    T metrics,
                                                                                    Map<String, Object> objectMap,
                                                                                    SessionCacheCallback callback) throws IOException {

        List<SQLExecutor> sqlExecutors = new ArrayList<>();
        SQLBuilder sqlBuilder = new SQLBuilder("INSERT INTO " + tableName + " VALUES");
        List<Object> param = new ArrayList<>();
        sqlBuilder.append("(?,");
        param.add(metrics.id());
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

    protected <T extends StorageData> SQLExecutor getUpdateExecutor(String modelName, T metrics,
                                                                    StorageBuilder<T> storageBuilder,
                                                                    SessionCacheCallback callback) throws IOException {
        final HashMapConverter.ToStorage toStorage = new HashMapConverter.ToStorage();
        storageBuilder.entity2Storage(metrics, toStorage);
        Map<String, Object> objectMap = toStorage.obtain();

        StringBuilder sqlBuilder = new StringBuilder("UPDATE " + modelName + " SET ");
        Model model = TableMetaInfo.get(modelName);
        List<ModelColumn> columns = model.getColumns();
        List<Object> param = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            String columnName = column.getColumnName().getName();
            if (model.getSqlDBModelExtension().isShardingTable()) {
                SQLDatabaseModelExtension.Sharding sharding = model.getSqlDBModelExtension().getSharding().orElseThrow(
                    () -> new UnexpectedException("Sharding should not be empty."));
                if (columnName.equals(sharding.getDataSourceShardingColumn()) || columnName.equals(
                    sharding.getTableShardingColumn())) {
                    continue;
                }
            }
            sqlBuilder.append(column.getColumnName().getStorageName()).append("= ?,");

            Object value = objectMap.get(columnName);
            if (value instanceof StorageDataComplexObject) {
                param.add(((StorageDataComplexObject) value).toStorageData());
            } else {
                param.add(value);
            }
        }
        sqlBuilder.replace(sqlBuilder.length() - 1, sqlBuilder.length(), "");
        sqlBuilder.append(" WHERE id = ?");
        param.add(metrics.id());

        return new SQLExecutor(sqlBuilder.toString(), param, callback);
    }
}
