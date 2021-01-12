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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.ArrayParamBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

@Slf4j
public class H2SQLExecutor {
    protected <T extends StorageData> List<StorageData> getByIDs(JDBCHikariCPClient h2Client,
                                                                 String modelName,
                                                                 String[] ids,
                                                                 StorageBuilder<T> storageBuilder) throws IOException {
        /*
         * Although H2 database or other database support createArrayOf and setArray operate,
         * Mysql 5.1.44 driver doesn't.
         */
        String param = ArrayParamBuilder.build(ids);

        try (Connection connection = h2Client.getConnection();
             ResultSet rs = h2Client.executeQuery(
                 connection, "SELECT * FROM " + modelName + " WHERE id in (" + param + ")")) {
            List<StorageData> storageDataList = new ArrayList<>();
            StorageData storageData;
            do {
                storageData = toStorageData(rs, modelName, storageBuilder);
                if (storageData != null) {
                    storageDataList.add(storageData);
                }
            }
            while (storageData != null);

            return storageDataList;
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
            return storageBuilder.map2Data(data);
        }
        return null;
    }

    protected <T extends StorageData> SQLExecutor getInsertExecutor(String modelName, T metrics,
                                                                    StorageBuilder<T> storageBuilder) throws IOException {
        return getInsertExecutor(modelName, metrics, storageBuilder, 1);
    }

    protected <T extends StorageData> SQLExecutor getInsertExecutor(String modelName, T metrics,
                                                                    StorageBuilder<T> storageBuilder,
                                                                    int maxSizeOfArrayColumn) throws IOException {
        Map<String, Object> objectMap = storageBuilder.data2Map(metrics);

        SQLBuilder sqlBuilder = new SQLBuilder("INSERT INTO " + modelName + " VALUES");
        List<ModelColumn> columns = TableMetaInfo.get(modelName).getColumns();
        List<Object> param = new ArrayList<>();
        sqlBuilder.append("(?,");
        param.add(metrics.id());
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            if (List.class.isAssignableFrom(column.getType())) {
                for (int physicalColumnIdx = 0; physicalColumnIdx < maxSizeOfArrayColumn; physicalColumnIdx++) {
                    sqlBuilder.append("?");
                    param.add(objectMap.get(column.getColumnName().getName() + "_" + physicalColumnIdx));
                    if (physicalColumnIdx != maxSizeOfArrayColumn - 1) {
                        sqlBuilder.append(",");
                    }
                }
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

        return new SQLExecutor(sqlBuilder.toString(), param);
    }

    protected <T extends StorageData> SQLExecutor getUpdateExecutor(String modelName, T metrics,
                                                                    StorageBuilder<T> storageBuilder) throws IOException {
        Map<String, Object> objectMap = storageBuilder.data2Map(metrics);

        SQLBuilder sqlBuilder = new SQLBuilder("UPDATE " + modelName + " SET ");
        List<ModelColumn> columns = TableMetaInfo.get(modelName).getColumns();
        List<Object> param = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            sqlBuilder.append(column.getColumnName().getStorageName() + "= ?");
            if (i != columns.size() - 1) {
                sqlBuilder.append(",");
            }

            Object value = objectMap.get(column.getColumnName().getName());
            if (value instanceof StorageDataComplexObject) {
                param.add(((StorageDataComplexObject) value).toStorageData());
            } else {
                param.add(value);
            }
        }
        sqlBuilder.append(" WHERE id = ?");
        param.add(metrics.id());

        return new SQLExecutor(sqlBuilder.toString(), param);
    }
}
