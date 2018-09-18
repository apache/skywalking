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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.storage.IIndicatorDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataType;
import org.apache.skywalking.oap.server.library.client.jdbc.JDBCClientException;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLExecutor;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.TableMetaInfo;

/**
 * @author wusheng
 */
public class H2IndicatorDAO implements IIndicatorDAO<SQLExecutor, SQLExecutor> {
    private JDBCHikariCPClient h2Client;
    private StorageBuilder<Indicator> storageBuilder;

    public H2IndicatorDAO(JDBCHikariCPClient h2Client, StorageBuilder<Indicator> storageBuilder) {
        this.h2Client = h2Client;
        this.storageBuilder = storageBuilder;
    }

    @Override public Indicator get(String modelName, Indicator indicator) throws IOException {
        try (ResultSet rs = h2Client.executeQuery("SELECT * FROM " + modelName + " WHERE id = ?", new Object[] {indicator.id()})) {
            while (rs.next()) {
                Map data = new HashMap();
                List<ModelColumn> columns = TableMetaInfo.get(modelName).getColumns();
                for (ModelColumn column : columns) {
                    data.put(column.getColumnName(), rs.getObject(column.getColumnName().getName()));
                }
                return storageBuilder.map2Data(data);
            }
        } catch (SQLException e) {
            throw new IOException(e.getMessage(), e);
        } catch (JDBCClientException e) {
            throw new IOException(e.getMessage(), e);
        }
        return null;
    }

    @Override public SQLExecutor prepareBatchInsert(String modelName, Indicator indicator) throws IOException {
        Map<String, Object> objectMap = storageBuilder.data2Map(indicator);

        SQLBuilder sqlBuilder = new SQLBuilder("INSERT INTO " + modelName + " VALUES");
        List<ModelColumn> columns = TableMetaInfo.get(modelName).getColumns();
        List<Object> param = new ArrayList<>();
        sqlBuilder.append("(id=?,");
        param.add(indicator.id());
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            sqlBuilder.append("?");
            if (i != columns.size()) {
                sqlBuilder.append(",");
            }

            Object value = objectMap.get(column.getColumnName().getName());
            if (value instanceof StorageDataType) {
                param.add(((StorageDataType)value).toStorageData());
            } else {
                param.add(value);
            }
        }
        sqlBuilder.append(")");

        return new SQLExecutor(sqlBuilder.toString(), param);
    }

    @Override public SQLExecutor prepareBatchUpdate(String modelName, Indicator indicator) throws IOException {
        Map<String, Object> objectMap = storageBuilder.data2Map(indicator);

        SQLBuilder sqlBuilder = new SQLBuilder("UPDATE " + modelName + " SET ");
        List<ModelColumn> columns = TableMetaInfo.get(modelName).getColumns();
        List<Object> param = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            ModelColumn column = columns.get(i);
            sqlBuilder.append(column.getColumnName().getName() + "= ?");
            if (i != columns.size()) {
                sqlBuilder.append(",");
            }

            Object value = objectMap.get(column.getColumnName().getName());
            if (value instanceof StorageDataType) {
                param.add(((StorageDataType)value).toStorageData());
            } else {
                param.add(value);
            }
        }
        sqlBuilder.append(" WHERE id = ?");
        param.add(indicator.id());

        return new SQLExecutor(sqlBuilder.toString(), param);
    }

    @Override public void deleteHistory(String modelName, Long timeBucketBefore) {

    }
}
